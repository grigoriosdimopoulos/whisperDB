package com.whisperlm.app.domain.usecase.llm

import com.whisperlm.app.domain.model.Dialogue
import com.whisperlm.app.domain.repository.DialogueRepository
import com.whisperlm.app.domain.repository.PersonRepository
import com.whisperlm.app.core.util.TimeUtils
import javax.inject.Inject

/**
 * Builds a plain-text context from all stored dialogues that is injected
 * into the LLM prompt. Truncated to ~4000 tokens (approx 16000 chars).
 */
class BuildContextUseCase @Inject constructor(
    private val dialogueRepository: DialogueRepository,
    private val personRepository: PersonRepository
) {
    companion object {
        private const val MAX_CONTEXT_CHARS = 16_000
    }

    suspend operator fun invoke(): String {
        val dialogues = dialogueRepository.getAllDialoguesForLlmContext()
        if (dialogues.isEmpty()) return ""

        val sb = StringBuilder()
        sb.append("You are an AI assistant with access to a personal conversation database. ")
        sb.append("Below are all recorded dialogues. Answer questions based ONLY on this data.\n\n")

        for (dialogue in dialogues) {
            if (sb.length > MAX_CONTEXT_CHARS) {
                sb.append("\n[... more dialogues truncated ...]")
                break
            }

            sb.append("=== Dialogue: ${dialogue.subject.ifBlank { "Untitled" }} ===\n")
            sb.append("Date: ${TimeUtils.formatDateTime(dialogue.recordedAt)}\n")
            if (dialogue.participants.isNotEmpty()) {
                val names = dialogue.participants.joinToString(", ") { it.name }
                sb.append("Participants: $names\n")
            }
            sb.append("\n")

            for (seg in dialogue.segments) {
                val speaker = seg.personName ?: seg.speakerLabel
                val time = TimeUtils.formatTimestamp(seg.timestampStartMs)
                sb.append("[$time] $speaker: ${seg.text}\n")
            }
            sb.append("\n")
        }

        return sb.toString()
    }
}
