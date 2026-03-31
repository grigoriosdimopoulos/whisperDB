package com.whisperlm.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.whisperlm.app.ui.navigation.AppNavGraph
import com.whisperlm.app.ui.navigation.AppRoutes
import com.whisperlm.app.ui.navigation.BottomNavItem
import com.whisperlm.app.ui.theme.WhisperLMTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WhisperLMTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent() {
    val mainViewModel: MainViewModel = hiltViewModel()
    val isSetupComplete by mainViewModel.isSetupComplete.collectAsState()

    // Paint the theme background immediately; show nothing until DataStore is ready
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        // isSetupComplete is null only while DataStore hasn't emitted yet;
        // once it emits, ?: false means null key → false (first run) or true (done)
        if (isSetupComplete != null) {
            // Capture startDestination once — changing it later would rebuild the nav graph and crash
            val startDestination = remember { if (isSetupComplete == true) AppRoutes.CHAT else AppRoutes.SETUP }
            val navController = rememberNavController()

            val bottomNavRoutes = BottomNavItem.items.map { it.route }

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    if (isSetupComplete == true) {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = navBackStackEntry?.destination?.route
                        val isTopLevel = bottomNavRoutes.any { currentRoute?.startsWith(it) == true }
                        if (isTopLevel) {
                            WhisperBottomNav(
                                currentRoute = currentRoute,
                                onItemSelected = { item ->
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                }
            ) { _ ->
                AppNavGraph(
                    navController = navController,
                    startDestination = startDestination
                )
            }
        }
    }  // end Surface
}  // end AppContent

@Composable
fun WhisperBottomNav(
    currentRoute: String?,
    onItemSelected: (BottomNavItem) -> Unit
) {
    NavigationBar {
        BottomNavItem.items.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = { onItemSelected(item) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) }
            )
        }
    }
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    companion object {
        val KEY_SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
    }

    val isSetupComplete = dataStore.data
        .map { prefs -> prefs[KEY_SETUP_COMPLETE] ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
