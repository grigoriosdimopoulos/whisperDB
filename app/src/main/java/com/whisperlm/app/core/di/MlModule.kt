package com.whisperlm.app.core.di

import android.content.Context
import com.whisperlm.app.ml.diarization.DiarizationEngine
import com.whisperlm.app.ml.llm.LlmEngine
import com.whisperlm.app.ml.whisper.WhisperEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MlModule {

    @Provides
    @Singleton
    fun provideWhisperEngine(@ApplicationContext context: Context): WhisperEngine =
        WhisperEngine(context)

    @Provides
    @Singleton
    fun provideDiarizationEngine(@ApplicationContext context: Context): DiarizationEngine =
        DiarizationEngine(context)

    @Provides
    @Singleton
    fun provideLlmEngine(@ApplicationContext context: Context): LlmEngine =
        LlmEngine(context)
}
