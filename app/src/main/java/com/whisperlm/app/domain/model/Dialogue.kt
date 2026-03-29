package com.whisperlm.app.domain.model

data class Dialogue(
    val id: Long = 0,
    val subject: String = "",
    val audioFilePath: String? = null,
    val recordedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L,
    val participants: List<Person> = emptyList(),
    val segments: List<TranscriptionSegment> = emptyList()
)
