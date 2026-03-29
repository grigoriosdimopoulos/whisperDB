package com.whisperlm.app.domain.usecase.dialogue

import com.whisperlm.app.domain.model.Dialogue
import com.whisperlm.app.domain.repository.DialogueRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetDialoguesUseCase @Inject constructor(
    private val dialogueRepository: DialogueRepository
) {
    operator fun invoke(newestFirst: Boolean = true): Flow<List<Dialogue>> =
        dialogueRepository.getAllDialogues(newestFirst)

    fun forPerson(personId: Long): Flow<List<Dialogue>> =
        dialogueRepository.getDialoguesForPerson(personId)
}
