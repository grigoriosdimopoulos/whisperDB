package com.whisperlm.app.domain.usecase.dialogue

import com.whisperlm.app.domain.repository.DialogueRepository
import javax.inject.Inject

class DeleteDialogueUseCase @Inject constructor(
    private val dialogueRepository: DialogueRepository
) {
    suspend operator fun invoke(dialogueId: Long) {
        dialogueRepository.deleteDialogue(dialogueId)
    }
}
