package com.whisperlm.app.core.di

import android.content.Context
import androidx.room.Room
import com.whisperlm.app.core.database.AppDatabase
import com.whisperlm.app.core.database.dao.DialogueDao
import com.whisperlm.app.core.database.dao.DialogueParticipantDao
import com.whisperlm.app.core.database.dao.PersonDao
import com.whisperlm.app.core.database.dao.TranscriptionSegmentDao
import com.whisperlm.app.core.database.dao.VoiceEmbeddingDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .build()

    @Provides
    fun providePersonDao(db: AppDatabase): PersonDao = db.personDao()

    @Provides
    fun provideVoiceEmbeddingDao(db: AppDatabase): VoiceEmbeddingDao = db.voiceEmbeddingDao()

    @Provides
    fun provideDialogueDao(db: AppDatabase): DialogueDao = db.dialogueDao()

    @Provides
    fun provideDialogueParticipantDao(db: AppDatabase): DialogueParticipantDao = db.dialogueParticipantDao()

    @Provides
    fun provideTranscriptionSegmentDao(db: AppDatabase): TranscriptionSegmentDao = db.transcriptionSegmentDao()
}
