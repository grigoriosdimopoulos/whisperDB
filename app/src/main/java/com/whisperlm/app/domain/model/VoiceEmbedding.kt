package com.whisperlm.app.domain.model

data class VoiceEmbedding(
    val id: Long = 0,
    val personId: Long,
    val embedding: FloatArray,
    val createdAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VoiceEmbedding) return false
        return id == other.id && personId == other.personId
    }

    override fun hashCode(): Int = 31 * id.hashCode() + personId.hashCode()
}
