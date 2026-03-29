package com.whisperlm.app.core.util

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

object EmbeddingUtils {

    /**
     * Compute cosine similarity between two float vectors.
     * Returns value in [-1, 1]; higher = more similar.
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Embedding size mismatch: ${a.size} vs ${b.size}" }
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom == 0.0) 0f else (dot / denom).toFloat()
    }

    /**
     * Average multiple embeddings into one representative embedding.
     */
    fun averageEmbeddings(embeddings: List<FloatArray>): FloatArray {
        if (embeddings.isEmpty()) return FloatArray(256)
        val size = embeddings[0].size
        val result = FloatArray(size)
        for (emb in embeddings) {
            for (i in emb.indices) result[i] += emb[i]
        }
        val n = embeddings.size.toFloat()
        for (i in result.indices) result[i] /= n
        return normalize(result)
    }

    /** L2-normalize a float vector. */
    fun normalize(v: FloatArray): FloatArray {
        var norm = 0.0
        for (x in v) norm += x * x
        norm = sqrt(norm)
        if (norm == 0.0) return v
        return FloatArray(v.size) { v[it] / norm.toFloat() }
    }

    /** Serialize FloatArray to ByteArray (little-endian). */
    fun floatArrayToBytes(floats: FloatArray): ByteArray {
        val buf = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (f in floats) buf.putFloat(f)
        return buf.array()
    }

    /** Deserialize ByteArray to FloatArray (little-endian). */
    fun bytesToFloatArray(bytes: ByteArray): FloatArray {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val floats = FloatArray(bytes.size / 4)
        for (i in floats.indices) floats[i] = buf.float
        return floats
    }
}
