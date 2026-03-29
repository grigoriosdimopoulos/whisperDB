package com.whisperlm.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transcription_segments",
    foreignKeys = [
        ForeignKey(
            entity = DialogueEntity::class,
            parentColumns = ["id"],
            childColumns = ["dialogue_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["person_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("dialogue_id"), Index("person_id")]
)
data class TranscriptionSegmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "dialogue_id")
    val dialogueId: Long,
    // Null until the user assigns a name to this speaker
    @ColumnInfo(name = "person_id")
    val personId: Long? = null,
    // Raw diarization label e.g. "SPEAKER_0"
    @ColumnInfo(name = "speaker_label")
    val speakerLabel: String,
    val text: String,
    @ColumnInfo(name = "timestamp_start_ms")
    val timestampStartMs: Long,
    @ColumnInfo(name = "timestamp_end_ms")
    val timestampEndMs: Long
)
