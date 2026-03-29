package com.whisperlm.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "dialogue_participants",
    primaryKeys = ["dialogue_id", "person_id"],
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
data class DialogueParticipantEntity(
    @ColumnInfo(name = "dialogue_id")
    val dialogueId: Long,
    @ColumnInfo(name = "person_id")
    val personId: Long,
    // The diarization label that maps to this person in this dialogue
    @ColumnInfo(name = "speaker_label")
    val speakerLabel: String = ""
)
