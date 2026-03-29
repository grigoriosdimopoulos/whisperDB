package com.whisperlm.app.data.repository

import com.whisperlm.app.core.database.dao.PersonDao
import com.whisperlm.app.core.database.dao.VoiceEmbeddingDao
import com.whisperlm.app.core.database.entity.PersonEntity
import com.whisperlm.app.core.database.entity.VoiceEmbeddingEntity
import com.whisperlm.app.core.util.EmbeddingUtils
import com.whisperlm.app.domain.model.Person
import com.whisperlm.app.domain.model.VoiceEmbedding
import com.whisperlm.app.domain.repository.PersonRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PersonRepositoryImpl @Inject constructor(
    private val personDao: PersonDao,
    private val voiceEmbeddingDao: VoiceEmbeddingDao
) : PersonRepository {

    override fun getAllPersons(): Flow<List<Person>> =
        personDao.getAllPersons().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getPersonById(id: Long): Person? =
        personDao.getPersonById(id)?.toDomain()

    override suspend fun getSelfPerson(): Person? =
        personDao.getSelfPerson()?.toDomain()

    override fun getSelfPersonFlow(): Flow<Person?> =
        personDao.getSelfPersonFlow().map { it?.toDomain() }

    override suspend fun savePerson(person: Person): Long =
        personDao.insertPerson(person.toEntity())

    override suspend fun deletePerson(id: Long) =
        personDao.deletePersonById(id)

    override suspend fun saveVoiceEmbedding(embedding: VoiceEmbedding): Long =
        voiceEmbeddingDao.insertEmbedding(embedding.toEntity())

    override suspend fun getEmbeddingsForPerson(personId: Long): List<VoiceEmbedding> =
        voiceEmbeddingDao.getEmbeddingsForPerson(personId).map { it.toDomain() }

    override suspend fun getAllVoiceEmbeddings(): List<VoiceEmbedding> =
        voiceEmbeddingDao.getAllEmbeddings().map { it.toDomain() }

    override suspend fun getVoiceSampleCount(personId: Long): Int =
        voiceEmbeddingDao.getEmbeddingCountForPerson(personId)

    private fun PersonEntity.toDomain() = Person(
        id = id, name = name, age = age,
        relationToUser = relationToUser, notes = notes,
        profilePhotoPath = profilePhotoPath, isUserSelf = isUserSelf,
        createdAt = createdAt
    )

    private fun Person.toEntity() = PersonEntity(
        id = id, name = name, age = age,
        relationToUser = relationToUser, notes = notes,
        profilePhotoPath = profilePhotoPath, isUserSelf = isUserSelf,
        createdAt = createdAt
    )

    private fun VoiceEmbeddingEntity.toDomain() = VoiceEmbedding(
        id = id, personId = personId,
        embedding = EmbeddingUtils.bytesToFloatArray(embeddingBlob),
        createdAt = createdAt
    )

    private fun VoiceEmbedding.toEntity() = VoiceEmbeddingEntity(
        id = id, personId = personId,
        embeddingBlob = EmbeddingUtils.floatArrayToBytes(embedding),
        createdAt = createdAt
    )
}
