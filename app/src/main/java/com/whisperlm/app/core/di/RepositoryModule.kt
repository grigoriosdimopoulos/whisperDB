package com.whisperlm.app.core.di

import com.whisperlm.app.data.repository.DialogueRepositoryImpl
import com.whisperlm.app.data.repository.PersonRepositoryImpl
import com.whisperlm.app.domain.repository.DialogueRepository
import com.whisperlm.app.domain.repository.PersonRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPersonRepository(impl: PersonRepositoryImpl): PersonRepository

    @Binds
    @Singleton
    abstract fun bindDialogueRepository(impl: DialogueRepositoryImpl): DialogueRepository
}
