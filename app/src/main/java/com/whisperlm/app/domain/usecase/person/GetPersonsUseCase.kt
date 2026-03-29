package com.whisperlm.app.domain.usecase.person

import com.whisperlm.app.domain.model.Person
import com.whisperlm.app.domain.repository.PersonRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetPersonsUseCase @Inject constructor(
    private val personRepository: PersonRepository
) {
    operator fun invoke(): Flow<List<Person>> = personRepository.getAllPersons()

    suspend fun byId(id: Long): Person? = personRepository.getPersonById(id)

    fun selfPersonFlow(): Flow<Person?> = personRepository.getSelfPersonFlow()
}
