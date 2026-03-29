package com.whisperlm.app.domain.model

/**
 * Represents one identified speaker from the diarization pipeline,
 * before the user has assigned a name.
 */
data class SpeakerCluster(
    val label: String,               // e.g. "SPEAKER_0"
    val embedding: FloatArray,       // averaged 256-dim embedding for this cluster
    val segmentCount: Int,
    val totalDurationMs: Long,
    // Best matching person from existing voice embeddings (null if no match)
    val suggestedPerson: Person? = null,
    val suggestionScore: Float = 0f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SpeakerCluster) return false
        return label == other.label
    }

    override fun hashCode(): Int = label.hashCode()
}
