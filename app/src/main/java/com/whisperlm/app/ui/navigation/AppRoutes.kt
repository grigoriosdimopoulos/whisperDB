package com.whisperlm.app.ui.navigation

object AppRoutes {
    const val SETUP = "setup"
    const val CHAT = "chat"
    const val ADD_RECORDING = "add_recording"
    const val DIALOGUES = "dialogues"
    const val DIALOGUE_DETAIL = "dialogue_detail/{dialogueId}"
    const val PERSONS = "persons"
    const val PERSON_PROFILE = "person_profile/{personId}"
    const val MY_PROFILE = "my_profile"

    fun dialogueDetail(dialogueId: Long) = "dialogue_detail/$dialogueId"
    fun personProfile(personId: Long) = "person_profile/$personId"
}
