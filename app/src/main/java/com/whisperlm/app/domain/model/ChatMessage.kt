package com.whisperlm.app.domain.model

enum class MessageRole { USER, ASSISTANT }

data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val role: MessageRole,
    val content: String,
    val isStreaming: Boolean = false
)
