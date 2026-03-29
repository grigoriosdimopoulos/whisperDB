package com.whisperlm.app.ui.screens.persons

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whisperlm.app.domain.model.Person
import com.whisperlm.app.domain.repository.PersonRepository
import com.whisperlm.app.domain.usecase.person.GetPersonsUseCase
import com.whisperlm.app.ui.components.ConfirmDeleteDialog
import com.whisperlm.app.ui.components.PersonAvatar
import com.whisperlm.app.ui.theme.PurplePrimary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class PersonsViewModel @Inject constructor(
    private val getPersonsUseCase: GetPersonsUseCase,
    private val personRepository: PersonRepository
) : ViewModel() {

    val persons: StateFlow<List<Person>> = getPersonsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun deletePerson(id: Long) {
        viewModelScope.launch {
            personRepository.deletePerson(id)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonsScreen(
    onPersonClick: (Long) -> Unit,
    viewModel: PersonsViewModel = hiltViewModel()
) {
    val persons by viewModel.persons.collectAsState()

    // State for delete confirmation dialog
    var personPendingDelete by remember { mutableStateOf<Person?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Persons",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onPersonClick(-2L) },
                containerColor = PurplePrimary,
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add person"
                )
            }
        }
    ) { innerPadding ->

        if (persons.isEmpty()) {
            PersonsEmptyState(modifier = Modifier.padding(innerPadding))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 88.dp // FAB clearance
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = persons,
                    key = { it.id }
                ) { person ->
                    PersonCard(
                        person = person,
                        onClick = { onPersonClick(person.id) },
                        onLongClick = {
                            // Self person cannot be deleted from this screen
                            if (!person.isUserSelf) {
                                personPendingDelete = person
                            }
                        }
                    )
                }
            }
        }

        // Delete confirmation dialog
        personPendingDelete?.let { person ->
            ConfirmDeleteDialog(
                title = "Delete Person",
                message = "Remove \"${person.name}\" and all associated data? This cannot be undone.",
                onConfirm = {
                    viewModel.deletePerson(person.id)
                    personPendingDelete = null
                },
                onDismiss = { personPendingDelete = null }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PersonCard
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PersonCard(
    person: Person,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 12.dp)
        ) {
            PersonAvatar(
                name = person.name,
                photoPath = person.profilePhotoPath,
                size = 64.dp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = person.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            RelationBadge(relation = person.relationToUser)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RelationBadge
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RelationBadge(
    relation: String,
    modifier: Modifier = Modifier
) {
    val containerColor = when (relation.lowercase()) {
        "self"      -> MaterialTheme.colorScheme.primary
        "family"    -> MaterialTheme.colorScheme.secondaryContainer
        "friend"    -> MaterialTheme.colorScheme.tertiaryContainer
        "colleague" -> MaterialTheme.colorScheme.surfaceVariant
        else        -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (relation.lowercase()) {
        "self"      -> MaterialTheme.colorScheme.onPrimary
        "family"    -> MaterialTheme.colorScheme.onSecondaryContainer
        "friend"    -> MaterialTheme.colorScheme.onTertiaryContainer
        "colleague" -> MaterialTheme.colorScheme.onSurfaceVariant
        else        -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        modifier = modifier
    ) {
        Text(
            text = relation,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PersonsEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.People,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No persons yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap + to add a person and start tracking conversations.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
