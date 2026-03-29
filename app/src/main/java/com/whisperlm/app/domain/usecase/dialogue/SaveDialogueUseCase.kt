package com.whisperlm.app.domain.usecase.dialogue

import com.whisperlm.app.domain.model.Dialogue
import com.whisperlm.app.domain.repository.DialogueRepository
import javax.inject.Inject

class SaveDialogueUseCase @Inject constructor(
    private val dialogueRepository: DialogueRepository
) {
    suspend operator fun invoke(dialogue: Dialogue): Long {
        return if (dialogue.id == 0L) {
            dialogueRepository.saveDialogue(dialogue)
        } else {
            dialogueRepository.updateDialogue(dialogue)
            dialogue.id
        }
    }
}
