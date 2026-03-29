package com.whisperlm.app.ml.whisper

import android.content.Context
import android.util.Log
import com.whisperlm.app.core.util.FileUtils
import com.whisperlm.app.domain.model.TranscriptionSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject

/**
 * Wraps whisper.cpp via JNI to provide offline speech-to-text.
 *
 * Supports any language that whisper-small/medium can handle (Greek, English,
 * bilingual recordings via auto language detection).
 *
 * Setup: the user must copy a whisper-*.bin model file to
 * getExternalFilesDir("models") during the setup wizard.
 */
class WhisperEngine @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "WhisperEngine"
        private const val SAMPLE_RATE = 16000

        init {
            try {
                System.loadLibrary("whisperlm_jni")
                Log.i(TAG, "Native library loaded")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Native library not available - STT will be stubbed: ${e.message}")
            }
        }
    }

    private var modelLoaded = false

    /** Load the model from the models directory. Returns true on success. */
    suspend fun loadModel(): Boolean = withContext(Dispatchers.IO) {
        val modelFile = FileUtils.findWhisperModel(context)
        if (modelFile == null) {
            Log.w(TAG, "No whisper model file found in models dir")
            return@withContext false
        }
        Log.i(TAG, "Loading whisper model: ${modelFile.name} (${modelFile.length() / 1_000_000}MB)")
        val success = nativeLoadModel(modelFile.absolutePath)
        modelLoaded = success
        if (!success) Log.e(TAG, "Failed to load whisper model")
        success
    }

    fun isLoaded(): Boolean = modelLoaded && nativeIsLoaded()

    /**
     * Transcribe a WAV/PCM float array.
     * Returns list of TranscriptionSegment with timestamps.
     * Language: "" for auto-detect, "el" for Greek, "en" for English.
     */
    suspend fun transcribe(
        pcmFloats: FloatArray,
        dialogueId: Long = 0L,
        language: String = ""
    ): List<TranscriptionSegment> = withContext(Dispatchers.Default) {
        if (!isLoaded()) {
            Log.w(TAG, "Model not loaded, returning empty transcription")
            return@withContext emptyList()
        }

        val rawSegments = nativeTranscribe(pcmFloats, SAMPLE_RATE, language, false)
            ?: return@withContext emptyList()

        rawSegments.mapIndexedNotNull { index, entry ->
            parseSegmentEntry(entry, dialogueId, index)
        }
    }

    /** Transcribe a WAV file directly. */
    suspend fun transcribeFile(
        file: File,
        dialogueId: Long = 0L,
        language: String = ""
    ): List<TranscriptionSegment> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        val pcmFloats = loadWavAsPcmFloat(file)
        transcribe(pcmFloats, dialogueId, language)
    }

    private fun parseSegmentEntry(entry: String, dialogueId: Long, index: Int): TranscriptionSegment? {
        // Format: "startMs|endMs|text"
        val parts = entry.split("|", limit = 3)
        if (parts.size < 3) return null
        return try {
            TranscriptionSegment(
                id = 0L,
                dialogueId = dialogueId,
                speakerLabel = "SPEAKER_0",  // diarization assigns labels later
                text = parts[2].trim(),
                timestampStartMs = parts[0].toLong(),
                timestampEndMs = parts[1].toLong()
            )
        } catch (_: NumberFormatException) {
            null
        }
    }

    /** Load a WAV file and convert to float array for whisper. */
    private fun loadWavAsPcmFloat(file: File): FloatArray {
        val bytes = file.readBytes()
        // Skip 44-byte WAV header
        if (bytes.size <= 44) return FloatArray(0)
        val pcmBytes = bytes.copyOfRange(44, bytes.size)
        val buf = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        val samples = pcmBytes.size / 2
        val floats = FloatArray(samples)
        for (i in 0 until samples) {
            floats[i] = buf.short.toFloat() / 32768f
        }
        return floats
    }

    fun release() {
        nativeFreeModel()
        modelLoaded = false
    }

    // JNI native methods
    private external fun nativeLoadModel(modelPath: String): Boolean
    private external fun nativeTranscribe(
        pcmData: FloatArray, sampleRate: Int,
        language: String, translate: Boolean
    ): Array<String>?
    private external fun nativeFreeModel()
    private external fun nativeIsLoaded(): Boolean
}
