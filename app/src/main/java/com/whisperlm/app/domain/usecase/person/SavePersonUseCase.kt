package com.whisperlm.app.domain.usecase.person

import com.whisperlm.app.domain.model.Person
import com.whisperlm.app.domain.repository.PersonRepository
import javax.inject.Inject

class SavePersonUseCase @Inject constructor(
    private val personRepository: PersonRepository
) {
    suspend operator fun invoke(person: Person): Long =
        personRepository.savePerson(person)
}
