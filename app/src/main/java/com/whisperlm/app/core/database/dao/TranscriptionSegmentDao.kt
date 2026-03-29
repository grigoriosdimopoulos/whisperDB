package com.whisperlm.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.whisperlm.app.core.database.entity.TranscriptionSegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptionSegmentDao {

    @Query("SELECT * FROM transcription_segments WHERE dialogue_id = :dialogueId ORDER BY timestamp_start_ms ASC")
    fun getSegmentsForDialogue(dialogueId: Long): Flow<List<TranscriptionSegmentEntity>>

    @Query("SELECT * FROM transcription_segments WHERE dialogue_id = :dialogueId ORDER BY timestamp_start_ms ASC")
    suspend fun getSegmentsForDialogueSnapshot(dialogueId: Long): List<TranscriptionSegmentEntity>

    @Query("SELECT * FROM transcription_segments ORDER BY dialogue_id, timestamp_start_ms ASC")
    suspend fun getAllSegmentsSnapshot(): List<TranscriptionSegmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegment(segment: TranscriptionSegmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<TranscriptionSegmentEntity>)

    @Update
    suspend fun updateSegment(segment: TranscriptionSegmentEntity)

    @Query("UPDATE transcription_segments SET person_id = :personId WHERE speaker_label = :speakerLabel AND dialogue_id = :dialogueId")
    suspend fun assignPersonToSpeakerLabel(dialogueId: Long, speakerLabel: String, personId: Long)

    @Query("DELETE FROM transcription_segments WHERE dialogue_id = :dialogueId")
    suspend fun deleteSegmentsForDialogue(dialogueId: Long)

    @Query("DELETE FROM transcription_segments WHERE id = :id")
    suspend fun deleteSegmentById(id: Long)
}
