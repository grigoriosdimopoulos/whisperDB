package com.whisperlm.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.whisperlm.app.core.database.converter.Converters
import com.whisperlm.app.core.database.dao.DialogueDao
import com.whisperlm.app.core.database.dao.DialogueParticipantDao
import com.whisperlm.app.core.database.dao.PersonDao
import com.whisperlm.app.core.database.dao.TranscriptionSegmentDao
import com.whisperlm.app.core.database.dao.VoiceEmbeddingDao
import com.whisperlm.app.core.database.entity.DialogueEntity
import com.whisperlm.app.core.database.entity.DialogueParticipantEntity
import com.whisperlm.app.core.database.entity.PersonEntity
import com.whisperlm.app.core.database.entity.TranscriptionSegmentEntity
import com.whisperlm.app.core.database.entity.VoiceEmbeddingEntity

@Database(
    entities = [
        PersonEntity::class,
        VoiceEmbeddingEntity::class,
        DialogueEntity::class,
        DialogueParticipantEntity::class,
        TranscriptionSegmentEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun voiceEmbeddingDao(): VoiceEmbeddingDao
    abstract fun dialogueDao(): DialogueDao
    abstract fun dialogueParticipantDao(): DialogueParticipantDao
    abstract fun transcriptionSegmentDao(): TranscriptionSegmentDao

    companion object {
        const val DATABASE_NAME = "whisperlm.db"
    }
}
