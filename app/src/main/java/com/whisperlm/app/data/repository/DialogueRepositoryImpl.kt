package com.whisperlm.app.data.repository

import com.whisperlm.app.core.database.dao.DialogueDao
import com.whisperlm.app.core.database.dao.DialogueParticipantDao
import com.whisperlm.app.core.database.dao.PersonDao
import com.whisperlm.app.core.database.dao.TranscriptionSegmentDao
import com.whisperlm.app.core.database.entity.DialogueEntity
import com.whisperlm.app.core.database.entity.DialogueParticipantEntity
import com.whisperlm.app.core.database.entity.TranscriptionSegmentEntity
import com.whisperlm.app.domain.model.Dialogue
import com.whisperlm.app.domain.model.Person
import com.whisperlm.app.domain.model.TranscriptionSegment
import com.whisperlm.app.domain.repository.DialogueRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DialogueRepositoryImpl @Inject constructor(
    private val dialogueDao: DialogueDao,
    private val participantDao: DialogueParticipantDao,
    private val segmentDao: TranscriptionSegmentDao,
    private val personDao: PersonDao
) : DialogueRepository {

    override fun getAllDialogues(newestFirst: Boolean): Flow<List<Dialogue>> {
        val flow = if (newestFirst) dialogueDao.getAllDialoguesNewestFirst()
        else dialogueDao.getAllDialoguesOldestFirst()
        return flow.map { entities ->
            entities.map { entity ->
                val participantIds = participantDao.getPersonIdsForDialogue(entity.id)
                val participants = participantIds.mapNotNull { personDao.getPersonById(it)?.toDomain() }
                entity.toDomain(participants, emptyList())
            }
        }
    }

    override suspend fun getDialogueById(id: Long): Dialogue? {
        val entity = dialogueDao.getDialogueById(id) ?: return null
        val participantIds = participantDao.getPersonIdsForDialogue(id)
        val participants = participantIds.mapNotNull { personDao.getPersonById(it)?.toDomain() }
        val personMap = participants.associateBy { it.id }
        val segments = segmentDao.getSegmentsForDialogueSnapshot(id).map { it.toDomain(personMap) }
        return entity.toDomain(participants, segments)
    }

    override fun getDialoguesForPerson(personId: Long): Flow<List<Dialogue>> =
        dialogueDao.getDialoguesForPerson(personId).map { entities ->
            entities.map { it.toDomain(emptyList(), emptyList()) }
        }

    override suspend fun getAllDialoguesForLlmContext(): List<Dialogue> {
        val entities = dialogueDao.getAllDialoguesSnapshot()
        return entities.map { entity ->
            val participantIds = participantDao.getPersonIdsForDialogue(entity.id)
            val participants = participantIds.mapNotNull { personDao.getPersonById(it)?.toDomain() }
            val personMap = participants.associateBy { it.id }
            val segments = segmentDao.getSegmentsForDialogueSnapshot(entity.id).map { it.toDomain(personMap) }
            entity.toDomain(participants, segments)
        }
    }

    override suspend fun saveDialogue(dialogue: Dialogue): Long {
        val dialogueId = dialogueDao.insertDialogue(dialogue.toEntity())
        saveParticipantsAndSegments(dialogueId, dialogue)
        return dialogueId
    }

    override suspend fun updateDialogue(dialogue: Dialogue) {
        dialogueDao.updateDialogue(dialogue.toEntity())
        participantDao.deleteParticipantsForDialogue(dialogue.id)
        segmentDao.deleteSegmentsForDialogue(dialogue.id)
        saveParticipantsAndSegments(dialogue.id, dialogue)
    }

    private suspend fun saveParticipantsAndSegments(dialogueId: Long, dialogue: Dialogue) {
        val participants = dialogue.participants.map { person ->
            val speakerLabel = dialogue.segments
                .firstOrNull { it.personId == person.id }?.speakerLabel ?: ""
            DialogueParticipantEntity(dialogueId, person.id, speakerLabel)
        }
        if (participants.isNotEmpty()) participantDao.insertParticipants(participants)

        val segments = dialogue.segments.map { seg ->
            TranscriptionSegmentEntity(
                id = seg.id,
                dialogueId = dialogueId,
                personId = seg.personId,
                speakerLabel = seg.speakerLabel,
                text = seg.text,
                timestampStartMs = seg.timestampStartMs,
                timestampEndMs = seg.timestampEndMs
            )
        }
        if (segments.isNotEmpty()) segmentDao.insertSegments(segments)
    }

    override suspend fun deleteDialogue(id: Long) = dialogueDao.deleteDialogueById(id)

    override fun getSegmentsForDialogue(dialogueId: Long): Flow<List<TranscriptionSegment>> =
        segmentDao.getSegmentsForDialogue(dialogueId).map { segs -> segs.map { it.toDomain(emptyMap()) } }

    override suspend fun getSegmentsSnapshot(dialogueId: Long): List<TranscriptionSegment> =
        segmentDao.getSegmentsForDialogueSnapshot(dialogueId).map { it.toDomain(emptyMap()) }

    override suspend fun saveSegments(segments: List<TranscriptionSegment>) {
        segmentDao.insertSegments(segments.map { seg ->
            TranscriptionSegmentEntity(
                id = seg.id, dialogueId = seg.dialogueId,
                personId = seg.personId, speakerLabel = seg.speakerLabel,
                text = seg.text, timestampStartMs = seg.timestampStartMs,
                timestampEndMs = seg.timestampEndMs
            )
        })
    }

    override suspend fun updateSegment(segment: TranscriptionSegment) {
        segmentDao.updateSegment(
            TranscriptionSegmentEntity(
                id = segment.id, dialogueId = segment.dialogueId,
                personId = segment.personId, speakerLabel = segment.speakerLabel,
                text = segment.text, timestampStartMs = segment.timestampStartMs,
                timestampEndMs = segment.timestampEndMs
            )
        )
    }

    override suspend fun assignPersonToSpeaker(dialogueId: Long, speakerLabel: String, personId: Long) {
        segmentDao.assignPersonToSpeakerLabel(dialogueId, speakerLabel, personId)
    }

    private fun DialogueEntity.toDomain(participants: List<Person>, segments: List<TranscriptionSegment>) =
        Dialogue(
            id = id, subject = subject, audioFilePath = audioFilePath,
            recordedAt = recordedAt, createdAt = createdAt, durationMs = durationMs,
            participants = participants, segments = segments
        )

    private fun Dialogue.toEntity() = DialogueEntity(
        id = id, subject = subject, audioFilePath = audioFilePath,
        recordedAt = recordedAt, createdAt = createdAt, durationMs = durationMs
    )

    private fun com.whisperlm.app.core.database.entity.PersonEntity.toDomain() = Person(
        id = id, name = name, age = age,
        relationToUser = relationToUser, notes = notes,
        profilePhotoPath = profilePhotoPath, isUserSelf = isUserSelf,
        createdAt = createdAt
    )

    private fun TranscriptionSegmentEntity.toDomain(personMap: Map<Long, Person>) = TranscriptionSegment(
        id = id, dialogueId = dialogueId, personId = personId,
        speakerLabel = speakerLabel, text = text,
        timestampStartMs = timestampStartMs, timestampEndMs = timestampEndMs,
        personName = personId?.let { personMap[it]?.name }
    )
}
