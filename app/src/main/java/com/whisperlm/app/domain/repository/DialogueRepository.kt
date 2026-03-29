package com.whisperlm.app.domain.repository

import com.whisperlm.app.domain.model.Dialogue
import com.whisperlm.app.domain.model.TranscriptionSegment
import kotlinx.coroutines.flow.Flow

interface DialogueRepository {
    fun getAllDialogues(newestFirst: Boolean = true): Flow<List<Dialogue>>
    suspend fun getDialogueById(id: Long): Dialogue?
    fun getDialoguesForPerson(personId: Long): Flow<List<Dialogue>>
    suspend fun getAllDialoguesForLlmContext(): List<Dialogue>

    suspend fun saveDialogue(dialogue: Dialogue): Long
    suspend fun updateDialogue(dialogue: Dialogue)
    suspend fun deleteDialogue(id: Long)

    fun getSegmentsForDialogue(dialogueId: Long): Flow<List<TranscriptionSegment>>
    suspend fun getSegmentsSnapshot(dialogueId: Long): List<TranscriptionSegment>
    suspend fun saveSegments(segments: List<TranscriptionSegment>)
    suspend fun updateSegment(segment: TranscriptionSegment)
    suspend fun assignPersonToSpeaker(dialogueId: Long, speakerLabel: String, personId: Long)
}
