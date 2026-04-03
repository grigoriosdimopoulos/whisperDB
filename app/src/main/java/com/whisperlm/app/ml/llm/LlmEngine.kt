package com.whisperlm.app.ml.llm

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.whisperlm.app.core.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class LlmBackend { NONE, MEDIAPIPE, LLAMA_CPP }

/**
 * On-device LLM engine with auto-detect strategy:
 * 1. If a .task file exists → use MediaPipe Gemma
 * 2. If a .gguf file exists → use llama.cpp JNI
 * 3. Neither → show "no model" state
 */
class LlmEngine @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "LlmEngine"
        private const val LLAMA_CONTEXT_SIZE = 2048

        // Cap the system context fed into the prompt so it fits within the model's
        // context window. TinyLlama / small models have 2048 tokens total;
        // 3600 chars ≈ 900 tokens leaving ~900 tokens for the response.
        private const val MAX_CONTEXT_CHARS = 3600

        init {
            try {
                System.loadLibrary("whisperlm_jni")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Native library not loaded: ${e.message}")
            }
        }
    }

    /** Callback interface invoked from C++ for each generated token piece. */
    fun interface StreamCallback {
        fun onToken(piece: String)
    }

    var activeBackend: LlmBackend = LlmBackend.NONE
        private set

    private var mediaPipeInference: LlmInference? = null

    /** Detect and load whichever LLM model is present. Returns the backend chosen. */
    suspend fun loadModel(): LlmBackend = withContext(Dispatchers.IO) {
        val modelFile = FileUtils.findLlmModel(context)

        if (modelFile == null) {
            Log.w(TAG, "No LLM model file found")
            activeBackend = LlmBackend.NONE
            return@withContext LlmBackend.NONE
        }

        when {
            FileUtils.isMediaPipeModel(modelFile) -> {
                try {
                    val options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelFile.absolutePath)
                        .setMaxTokens(1024)
                        .setTopK(40)
                        .setTemperature(0.7f)
                        .setRandomSeed(42)
                        .build()
                    mediaPipeInference = LlmInference.createFromOptions(context, options)
                    activeBackend = LlmBackend.MEDIAPIPE
                    Log.i(TAG, "MediaPipe LLM loaded: ${modelFile.name}")
                } catch (e: Exception) {
                    Log.e(TAG, "MediaPipe load failed: ${e.message}")
                    activeBackend = LlmBackend.NONE
                }
            }

            FileUtils.isLlamaModel(modelFile) -> {
                val loaded = nativeLoadModel(modelFile.absolutePath, LLAMA_CONTEXT_SIZE)
                activeBackend = if (loaded) {
                    Log.i(TAG, "llama.cpp model loaded: ${modelFile.name}")
                    LlmBackend.LLAMA_CPP
                } else {
                    Log.e(TAG, "llama.cpp load failed")
                    LlmBackend.NONE
                }
            }

            else -> {
                Log.w(TAG, "Unknown model format: ${modelFile.extension}")
                activeBackend = LlmBackend.NONE
            }
        }

        activeBackend
    }

    fun isReady(): Boolean = activeBackend != LlmBackend.NONE

    /**
     * Generate a response. Returns a Flow of token strings for streaming display.
     */
    fun generate(systemContext: String, userQuery: String): Flow<String> {
        val prompt = buildPrompt(systemContext, userQuery)

        return when (activeBackend) {
            LlmBackend.MEDIAPIPE -> generateMediaPipe(prompt)
            LlmBackend.LLAMA_CPP -> generateLlama(prompt)
            LlmBackend.NONE -> flowOf("[No LLM model loaded. Add a .task or .gguf file to the models folder.]")
        }
    }

    private fun buildPrompt(systemContext: String, userQuery: String): String {
        // Truncate context to fit within the model's token window.
        // Small models (TinyLlama, Qwen-1.5B) have 2048 token limits;
        // we keep the most recent portion of the context (tail) as it
        // is more relevant than older dialogues.
        val ctx = if (systemContext.length > MAX_CONTEXT_CHARS) {
            "...\n" + systemContext.takeLast(MAX_CONTEXT_CHARS)
        } else {
            systemContext
        }
        // ChatML format — compatible with Qwen, Mistral, Phi, TinyLlama, and most GGUF models
        return "<|im_start|>system\n$ctx<|im_end|>\n<|im_start|>user\n$userQuery<|im_end|>\n<|im_start|>assistant\n"
    }

    private fun generateMediaPipe(prompt: String): Flow<String> = flow {
        val inference = mediaPipeInference ?: run {
            emit("[Model not available]")
            return@flow
        }
        try {
            val response = withContext(Dispatchers.Default) {
                inference.generateResponse(prompt)
            }
            emit(response)
        } catch (e: Exception) {
            Log.e(TAG, "MediaPipe generation error: ${e.message}")
            emit("[Generation failed: ${e.message}]")
        }
    }

    // Streams tokens in real-time via JNI callback → callbackFlow.
    // Each token piece is emitted as soon as llama.cpp produces it,
    // so the user sees the response building word-by-word.
    private fun generateLlama(prompt: String): Flow<String> = callbackFlow {
        nativeGenerateStreaming(prompt, 200) { piece ->
            trySend(piece)
        }
        close()
        awaitClose()
    }.flowOn(Dispatchers.Default)

    fun release() {
        mediaPipeInference?.close()
        mediaPipeInference = null
        if (activeBackend == LlmBackend.LLAMA_CPP) nativeFreeModel()
        activeBackend = LlmBackend.NONE
    }

    // JNI methods
    private external fun nativeLoadModel(modelPath: String, nCtx: Int): Boolean
    private external fun nativeGenerateStreaming(prompt: String, maxTokens: Int, callback: StreamCallback)
    private external fun nativeGenerate(prompt: String, maxTokens: Int): String  // fallback
    private external fun nativeFreeModel()
}
