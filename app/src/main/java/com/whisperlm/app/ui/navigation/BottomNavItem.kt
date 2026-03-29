package com.whisperlm.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    object Chat : BottomNavItem(AppRoutes.CHAT, "Chat", Icons.Filled.Chat)
    object AddRecording : BottomNavItem(AppRoutes.ADD_RECORDING, "Add", Icons.Filled.FiberManualRecord)
    object Dialogues : BottomNavItem(AppRoutes.DIALOGUES, "Dialogues", Icons.Filled.FormatListBulleted)
    object Persons : BottomNavItem(AppRoutes.PERSONS, "Persons", Icons.Filled.People)
    object MyProfile : BottomNavItem(AppRoutes.MY_PROFILE, "Profile", Icons.Filled.Person)

    companion object {
        val items = listOf(Chat, AddRecording, Dialogues, Persons, MyProfile)
    }
}
