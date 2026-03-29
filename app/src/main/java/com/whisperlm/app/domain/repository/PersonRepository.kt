package com.whisperlm.app.domain.repository

import com.whisperlm.app.domain.model.Person
import com.whisperlm.app.domain.model.VoiceEmbedding
import kotlinx.coroutines.flow.Flow

interface PersonRepository {
    fun getAllPersons(): Flow<List<Person>>
    suspend fun getPersonById(id: Long): Person?
    suspend fun getSelfPerson(): Person?
    fun getSelfPersonFlow(): Flow<Person?>
    suspend fun savePerson(person: Person): Long
    suspend fun deletePerson(id: Long)

    suspend fun saveVoiceEmbedding(embedding: VoiceEmbedding): Long
    suspend fun getEmbeddingsForPerson(personId: Long): List<VoiceEmbedding>
    suspend fun getAllVoiceEmbeddings(): List<VoiceEmbedding>
    suspend fun getVoiceSampleCount(personId: Long): Int
}
