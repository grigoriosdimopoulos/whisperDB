package com.whisperlm.app.domain.model

data class TranscriptionSegment(
    val id: Long = 0,
    val dialogueId: Long,
    val personId: Long? = null,
    val speakerLabel: String,
    val text: String,
    val timestampStartMs: Long,
    val timestampEndMs: Long,
    // Resolved at display time from personId
    val personName: String? = null
)
