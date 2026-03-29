package com.whisperlm.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.whisperlm.app.core.database.entity.DialogueParticipantEntity

@Dao
interface DialogueParticipantDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParticipant(participant: DialogueParticipantEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParticipants(participants: List<DialogueParticipantEntity>)

    @Query("SELECT * FROM dialogue_participants WHERE dialogue_id = :dialogueId")
    suspend fun getParticipantsForDialogue(dialogueId: Long): List<DialogueParticipantEntity>

    @Query("DELETE FROM dialogue_participants WHERE dialogue_id = :dialogueId")
    suspend fun deleteParticipantsForDialogue(dialogueId: Long)

    @Query("SELECT person_id FROM dialogue_participants WHERE dialogue_id = :dialogueId")
    suspend fun getPersonIdsForDialogue(dialogueId: Long): List<Long>
}
