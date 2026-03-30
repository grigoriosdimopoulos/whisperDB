package com.whisperlm.app.ml.llm

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.whisperlm.app.core.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
        private const val LLAMA_CONTEXT_SIZE = 4096

        init {
            try {
                System.loadLibrary("whisperlm_jni")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Native library not loaded: ${e.message}")
            }
        }
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
     * Generate a response to the given prompt + context.
     * Returns a Flow of token strings for streaming display.
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
        // Gemma / Llama instruction format
        return "<start_of_turn>user\n$systemContext\n\nQuestion: $userQuery<end_of_turn>\n<start_of_turn>model\n"
    }

    private fun generateMediaPipe(prompt: String): Flow<String> = flow {
        val inference = mediaPipeInference ?: run {
            emit("[Model not available]")
            return@flow
        }
        try {
            // generateResponse is synchronous; wrap in IO dispatcher
            val response = withContext(Dispatchers.Default) {
                inference.generateResponse(prompt)
            }
            emit(response)
        } catch (e: Exception) {
            Log.e(TAG, "MediaPipe generation error: ${e.message}")
            emit("[Generation failed: ${e.message}]")
        }
    }

    private fun generateLlama(prompt: String): Flow<String> = flow {
        emit(nativeGenerate(prompt, 512))
    }.flowOn(Dispatchers.Default)

    fun release() {
        mediaPipeInference?.close()
        mediaPipeInference = null
        if (activeBackend == LlmBackend.LLAMA_CPP) nativeFreeModel()
        activeBackend = LlmBackend.NONE
    }

    // JNI methods for llama.cpp
    private external fun nativeLoadModel(modelPath: String, nCtx: Int): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int): String
    private external fun nativeFreeModel()
}
