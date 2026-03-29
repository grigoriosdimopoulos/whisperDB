package com.whisperlm.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dialogues")
data class DialogueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subject: String = "",
    @ColumnInfo(name = "audio_file_path")
    val audioFilePath: String? = null,
    // Timestamp of the actual recording (from file metadata or user input)
    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long = System.currentTimeMillis(),
    // Timestamp when the entry was added to the DB
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long = 0L
)
