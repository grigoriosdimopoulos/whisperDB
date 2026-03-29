package com.whisperlm.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.whisperlm.app.ui.screens.addrecording.AddRecordingScreen
import com.whisperlm.app.ui.screens.chat.ChatScreen
import com.whisperlm.app.ui.screens.dialoguedetail.DialogueDetailScreen
import com.whisperlm.app.ui.screens.dialogues.DialoguesScreen
import com.whisperlm.app.ui.screens.personprofile.PersonProfileScreen
import com.whisperlm.app.ui.screens.persons.PersonsScreen
import com.whisperlm.app.ui.screens.setup.SetupScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable(AppRoutes.SETUP) {
            SetupScreen(onSetupComplete = {
                navController.navigate(AppRoutes.CHAT) {
                    popUpTo(AppRoutes.SETUP) { inclusive = true }
                }
            })
        }

        composable(AppRoutes.CHAT) {
            ChatScreen()
        }

        composable(AppRoutes.ADD_RECORDING) {
            AddRecordingScreen(
                onDialogueSaved = { dialogueId ->
                    navController.navigate(AppRoutes.dialogueDetail(dialogueId)) {
                        popUpTo(AppRoutes.ADD_RECORDING)
                    }
                }
            )
        }

        composable(AppRoutes.DIALOGUES) {
            DialoguesScreen(
                onDialogueClick = { dialogueId ->
                    navController.navigate(AppRoutes.dialogueDetail(dialogueId))
                }
            )
        }

        composable(
            route = AppRoutes.DIALOGUE_DETAIL,
            arguments = listOf(navArgument("dialogueId") { type = NavType.LongType })
        ) { backStackEntry ->
            val dialogueId = backStackEntry.arguments?.getLong("dialogueId") ?: return@composable
            DialogueDetailScreen(
                dialogueId = dialogueId,
                onPersonClick = { personId ->
                    navController.navigate(AppRoutes.personProfile(personId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(AppRoutes.PERSONS) {
            PersonsScreen(
                onPersonClick = { personId ->
                    navController.navigate(AppRoutes.personProfile(personId))
                }
            )
        }

        composable(
            route = AppRoutes.PERSON_PROFILE,
            arguments = listOf(navArgument("personId") { type = NavType.LongType })
        ) { backStackEntry ->
            val personId = backStackEntry.arguments?.getLong("personId") ?: return@composable
            PersonProfileScreen(
                personId = personId,
                onDialogueClick = { dialogueId ->
                    navController.navigate(AppRoutes.dialogueDetail(dialogueId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(AppRoutes.MY_PROFILE) {
            PersonProfileScreen(
                personId = -1L, // -1 signals "show self profile"
                onDialogueClick = { dialogueId ->
                    navController.navigate(AppRoutes.dialogueDetail(dialogueId))
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
