package com.whisperlm.app.domain.usecase.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.whisperlm.app.core.util.AudioUtils
import com.whisperlm.app.core.util.FileUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.io.File
import javax.inject.Inject

data class RecordingChunk(
    val pcmFloats: FloatArray,
    val elapsedMs: Long
)

class RecordAudioUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        // Buffer for ~100ms of audio
        private val BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            .coerceAtLeast(3200)  // at least 100ms worth
    }

    private var audioRecord: AudioRecord? = null
    private val allPcmBytes = mutableListOf<ByteArray>()
    private var startTimeMs = 0L

    @SuppressLint("MissingPermission")
    fun startRecording(): Flow<RecordingChunk> = flow {
        allPcmBytes.clear()
        startTimeMs = System.currentTimeMillis()

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            BUFFER_SIZE * 8
        )
        audioRecord = recorder
        recorder.startRecording()

        val buffer = ByteArray(BUFFER_SIZE)
        while (currentCoroutineContext().isActive) {
            val read = recorder.read(buffer, 0, buffer.size)
            if (read > 0) {
                val chunk = buffer.copyOf(read)
                allPcmBytes.add(chunk)
                val elapsed = System.currentTimeMillis() - startTimeMs
                emit(RecordingChunk(AudioUtils.pcm16ToFloat(chunk), elapsed))
            }
        }
    }.flowOn(Dispatchers.IO)

    fun stopAndSave(): File? {
        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null

        if (allPcmBytes.isEmpty()) return null

        val totalSize = allPcmBytes.sumOf { it.size }
        val allBytes = ByteArray(totalSize)
        var offset = 0
        for (chunk in allPcmBytes) {
            chunk.copyInto(allBytes, offset)
            offset += chunk.size
        }

        val outFile = FileUtils.newRecordingFile(context)
        AudioUtils.writePcm16ToWav(allBytes, outFile, SAMPLE_RATE)
        allPcmBytes.clear()
        return outFile
    }

    fun cancel() {
        audioRecord?.apply { stop(); release() }
        audioRecord = null
        allPcmBytes.clear()
    }
}
