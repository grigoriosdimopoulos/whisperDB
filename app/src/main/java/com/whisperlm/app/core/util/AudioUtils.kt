package com.whisperlm.app.core.util

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioUtils {

    /**
     * Convert a PCM16 byte array (16kHz mono) to FloatArray
     * suitable for Whisper / diarization models.
     */
    fun pcm16ToFloat(pcmBytes: ByteArray): FloatArray {
        val shorts = pcmBytes.size / 2
        val result = FloatArray(shorts)
        val buf = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until shorts) {
            result[i] = buf.short / 32768f
        }
        return result
    }

    /**
     * Convert FloatArray back to PCM16 ByteArray.
     */
    fun floatToPcm16(floats: FloatArray): ByteArray {
        val buf = ByteBuffer.allocate(floats.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (f in floats) {
            val clamped = f.coerceIn(-1f, 1f)
            buf.putShort((clamped * 32767).toInt().toShort())
        }
        return buf.array()
    }

    /**
     * Write a minimal WAV header + PCM16 data to a File.
     * sampleRate = 16000, channels = 1 (mono), bitsPerSample = 16
     */
    fun writePcm16ToWav(pcmData: ByteArray, outFile: File, sampleRate: Int = 16000) {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcmData.size
        val headerSize = 44

        RandomAccessFile(outFile, "rw").use { raf ->
            raf.setLength(0)
            val header = ByteBuffer.allocate(headerSize).order(ByteOrder.LITTLE_ENDIAN)
            // RIFF chunk
            header.put("RIFF".toByteArray())
            header.putInt(dataSize + headerSize - 8)
            header.put("WAVE".toByteArray())
            // fmt sub-chunk
            header.put("fmt ".toByteArray())
            header.putInt(16)
            header.putShort(1)               // PCM = 1
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(blockAlign.toShort())
            header.putShort(bitsPerSample.toShort())
            // data sub-chunk
            header.put("data".toByteArray())
            header.putInt(dataSize)
            raf.write(header.array())
            raf.write(pcmData)
        }
    }

    /** Extract total duration in ms from a WAV file header. */
    fun getWavDurationMs(file: File): Long {
        if (!file.exists() || file.length() < 44) return 0L
        return try {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(24)
                val buf = ByteArray(4)
                raf.read(buf)
                val sampleRate = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).int
                raf.seek(40)
                raf.read(buf)
                val dataSize = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).int
                val channels = 1
                val bitsPerSample = 16
                val totalSamples = dataSize / (channels * bitsPerSample / 8)
                (totalSamples * 1000L) / sampleRate
            }
        } catch (_: Exception) {
            0L
        }
    }
}
