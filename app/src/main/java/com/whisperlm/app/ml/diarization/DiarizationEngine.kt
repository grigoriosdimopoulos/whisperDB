package com.whisperlm.app.ml.diarization

import android.content.Context
import android.util.Log
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OnnxTensor
import com.whisperlm.app.core.util.EmbeddingUtils
import com.whisperlm.app.core.util.FileUtils
import com.whisperlm.app.domain.model.SpeakerCluster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Speaker diarization engine using ONNX Runtime.
 *
 * Model: wespeaker_resnet34.onnx or compatible speaker embedding model.
 * Place the model file in getExternalFilesDir("models") as "diarization.onnx".
 *
 * Pipeline:
 * 1. Sliding window over the audio (1.5s windows, 0.75s hop)
 * 2. Each window → 256-dim speaker embedding via ONNX model
 * 3. Agglomerative clustering of embeddings (cosine distance threshold ~0.35)
 * 4. Each cluster = one identified speaker
 */
class DiarizationEngine @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "DiarizationEngine"
        private const val SAMPLE_RATE = 16000
        private const val WINDOW_SAMPLES = SAMPLE_RATE * 3 / 2    // 1.5 seconds
        private const val HOP_SAMPLES    = SAMPLE_RATE * 3 / 4    // 0.75 seconds hop
        private const val CLUSTER_THRESHOLD = 0.35f               // cosine distance threshold
        private const val EMBEDDING_DIM = 256
        private const val DIARIZATION_MODEL_NAME = "diarization.onnx"
    }

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var modelLoaded = false

    suspend fun loadModel(): Boolean = withContext(Dispatchers.IO) {
        val modelFile = FileUtils.getModelsDir(context)
            .listFiles()
            ?.firstOrNull { it.name == DIARIZATION_MODEL_NAME }
        if (modelFile == null) {
            Log.w(TAG, "Diarization model not found ($DIARIZATION_MODEL_NAME)")
            return@withContext false
        }
        try {
            ortEnv = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(4)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            ortSession = ortEnv!!.createSession(modelFile.absolutePath, opts)
            modelLoaded = true
            Log.i(TAG, "Diarization model loaded")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load diarization model: ${e.message}")
            false
        }
    }

    fun isLoaded(): Boolean = modelLoaded && ortSession != null

    /**
     * Run speaker diarization on a PCM float array.
     * Returns a list of SpeakerCluster objects with their average embeddings.
     */
    suspend fun diarize(
        pcmFloats: FloatArray,
        segmentStartMs: List<Long> = emptyList(),
        segmentEndMs: List<Long> = emptyList()
    ): List<SpeakerCluster> = withContext(Dispatchers.Default) {
        if (!isLoaded()) {
            Log.w(TAG, "Model not loaded, using simple single-speaker fallback")
            return@withContext listOf(
                SpeakerCluster(
                    label = "SPEAKER_0",
                    embedding = FloatArray(EMBEDDING_DIM),
                    segmentCount = 1,
                    totalDurationMs = (pcmFloats.size.toLong() * 1000) / SAMPLE_RATE
                )
            )
        }

        val embeddings = extractWindowEmbeddings(pcmFloats)
        if (embeddings.isEmpty()) return@withContext emptyList()

        clusterEmbeddings(embeddings, pcmFloats.size)
    }

    private suspend fun extractWindowEmbeddings(pcmFloats: FloatArray): List<Pair<Int, FloatArray>> {
        val result = mutableListOf<Pair<Int, FloatArray>>()
        var start = 0

        while (start + WINDOW_SAMPLES <= pcmFloats.size) {
            val window = pcmFloats.copyOfRange(start, min(start + WINDOW_SAMPLES, pcmFloats.size))
            val emb = runEmbeddingModel(window)
            if (emb != null) result.add(Pair(start, emb))
            start += HOP_SAMPLES
        }

        return result
    }

    private fun runEmbeddingModel(window: FloatArray): FloatArray? {
        val session = ortSession ?: return null
        val env = ortEnv ?: return null

        return try {
            val shape = longArrayOf(1, window.size.toLong())
            val floatBuf = FloatBuffer.wrap(window)
            val tensor = OnnxTensor.createTensor(env, floatBuf, shape)

            val inputName = session.inputNames.iterator().next()
            val results = session.run(mapOf(inputName to tensor))
            val outputTensor = results.get(0) as OnnxTensor
            val outputData = outputTensor.floatBuffer
            val emb = FloatArray(EMBEDDING_DIM)
            outputData.get(emb)

            tensor.close()
            outputTensor.close()
            results.close()

            EmbeddingUtils.normalize(emb)
        } catch (e: Exception) {
            Log.e(TAG, "Embedding inference error: ${e.message}")
            null
        }
    }

    private fun clusterEmbeddings(
        windowEmbeddings: List<Pair<Int, FloatArray>>,
        totalSamples: Int
    ): List<SpeakerCluster> {
        // Simple agglomerative clustering by cosine distance
        val labels = IntArray(windowEmbeddings.size) { -1 }
        var nextLabel = 0
        val clusterEmbeddings = mutableMapOf<Int, MutableList<FloatArray>>()

        for (i in windowEmbeddings.indices) {
            val (_, emb) = windowEmbeddings[i]
            var bestCluster = -1
            var bestScore = -1f

            for ((cLabel, cEmbs) in clusterEmbeddings) {
                val centroid = EmbeddingUtils.averageEmbeddings(cEmbs)
                val sim = EmbeddingUtils.cosineSimilarity(emb, centroid)
                val dist = 1f - sim
                if (dist < CLUSTER_THRESHOLD && sim > bestScore) {
                    bestScore = sim
                    bestCluster = cLabel
                }
            }

            if (bestCluster == -1) {
                bestCluster = nextLabel++
                clusterEmbeddings[bestCluster] = mutableListOf()
            }

            labels[i] = bestCluster
            clusterEmbeddings[bestCluster]!!.add(emb)
        }

        // Build SpeakerCluster objects
        return clusterEmbeddings.entries
            .sortedBy { it.key }
            .map { (label, embs) ->
                val count = embs.size
                val durationMs = (count * HOP_SAMPLES.toLong() * 1000) / SAMPLE_RATE
                SpeakerCluster(
                    label = "SPEAKER_$label",
                    embedding = EmbeddingUtils.averageEmbeddings(embs),
                    segmentCount = count,
                    totalDurationMs = durationMs
                )
            }
    }

    /**
     * Merge Whisper transcription segments with diarization speaker assignments.
     * Each transcription segment gets the speaker label of the diarization window
     * that overlaps most with its time range.
     */
    fun assignSpeakersToSegments(
        segmentStartMs: List<Long>,
        segmentEndMs: List<Long>,
        windowEmbeddings: List<Pair<Int, FloatArray>>,
        windowLabels: List<String>
    ): List<String> {
        if (windowLabels.isEmpty()) return List(segmentStartMs.size) { "SPEAKER_0" }

        return segmentStartMs.indices.map { i ->
            val midMs = (segmentStartMs[i] + segmentEndMs[i]) / 2
            val midSample = (midMs * SAMPLE_RATE / 1000).toInt()

            // Find closest window
            var bestWindow = 0
            var bestDist = Int.MAX_VALUE
            for ((wi, wStart) in windowEmbeddings.withIndex()) {
                val dist = Math.abs(wStart.first - midSample)
                if (dist < bestDist) { bestDist = dist; bestWindow = wi }
            }

            if (bestWindow < windowLabels.size) windowLabels[bestWindow] else "SPEAKER_0"
        }
    }

    fun release() {
        ortSession?.close()
        ortSession = null
        ortEnv?.close()
        ortEnv = null
        modelLoaded = false
    }
}
