package com.whisperlm.app.ui.screens.personprofile

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whisperlm.app.core.util.FileUtils
import com.whisperlm.app.core.util.TimeUtils
import com.whisperlm.app.domain.model.Dialogue
import com.whisperlm.app.domain.model.Person
import com.whisperlm.app.domain.repository.PersonRepository
import com.whisperlm.app.domain.usecase.dialogue.GetDialoguesUseCase
import com.whisperlm.app.domain.usecase.person.GetPersonsUseCase
import com.whisperlm.app.domain.usecase.person.SavePersonUseCase
import com.whisperlm.app.ui.components.ConfirmDeleteDialog
import com.whisperlm.app.ui.components.PersonAvatar
import com.whisperlm.app.ui.components.SectionHeader
import com.whisperlm.app.ui.theme.PurplePrimary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class PersonProfileViewModel @Inject constructor(
    private val getPersonsUseCase: GetPersonsUseCase,
    private val savePersonUseCase: SavePersonUseCase,
    private val getDialoguesUseCase: GetDialoguesUseCase,
    private val personRepository: PersonRepository
) : ViewModel() {

    // Holds the resolved person ID once loadPerson() is called.
    // -1L  → self person
    // -2L  → new person (nothing to load)
    // >0   → specific person by ID
    private val _resolvedPersonId = MutableStateFlow<Long?>(null)

    private val _person = MutableStateFlow<Person?>(null)
    val person: StateFlow<Person?> = _person.asStateFlow()

    private val _voiceSampleCount = MutableStateFlow(0)
    val voiceSampleCount: StateFlow<Int> = _voiceSampleCount.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val dialogues: StateFlow<List<Dialogue>> = _resolvedPersonId
        .flatMapLatest { resolvedId ->
            when {
                resolvedId == null || resolvedId == -2L -> flowOf(emptyList())
                else -> getDialoguesUseCase.forPerson(resolvedId)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    // Exposed so the UI knows which mode we are in without re-deriving it
    var isSelf: Boolean = false
        private set

    fun loadPerson(personId: Long) {
        viewModelScope.launch {
            when {
                personId == -1L -> {
                    isSelf = true
                    val selfPerson = personRepository.getSelfPerson()
                    _person.value = selfPerson
                    selfPerson?.let { p ->
                        _resolvedPersonId.value = p.id
                        _voiceSampleCount.value = personRepository.getVoiceSampleCount(p.id)
                    }
                }
                personId == -2L -> {
                    isSelf = false
                    _person.value = null
                    _resolvedPersonId.value = -2L
                }
                else -> {
                    isSelf = false
                    val loaded = personRepository.getPersonById(personId)
                    _person.value = loaded
                    _resolvedPersonId.value = personId
                    _voiceSampleCount.value = personRepository.getVoiceSampleCount(personId)
                }
            }
        }
    }

    fun savePerson(name: String, age: Int?, relation: String, notes: String) {
        viewModelScope.launch {
            val existing = _person.value
            val updated = if (existing != null) {
                existing.copy(
                    name = name.trim(),
                    age = age,
                    relationToUser = relation,
                    notes = notes.trim()
                )
            } else {
                Person(
                    name = name.trim(),
                    age = age,
                    relationToUser = relation,
                    notes = notes.trim(),
                    isUserSelf = isSelf
                )
            }
            val savedId = savePersonUseCase(updated)
            // Reload after save so ID is populated for new persons
            val reloaded = personRepository.getPersonById(savedId)
            _person.value = reloaded
            _resolvedPersonId.value = savedId
        }
    }

    fun deletePerson() {
        if (isSelf) return
        viewModelScope.launch {
            _person.value?.let { personRepository.deletePerson(it.id) }
        }
    }

    fun updatePhoto(context: Context, uri: Uri) {
        viewModelScope.launch {
            val current = _person.value ?: return@launch
            val newPath = withContext(Dispatchers.IO) {
                FileUtils.copyUriToPhotosDir(context, uri, current.id).absolutePath
            }
            val updated = current.copy(profilePhotoPath = newPath)
            savePersonUseCase(updated)
            _person.value = updated
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Relation options
// ─────────────────────────────────────────────────────────────────────────────

private val RELATION_OPTIONS = listOf("Self", "Family", "Friend", "Colleague", "Other")

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonProfileScreen(
    personId: Long,
    onDialogueClick: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: PersonProfileViewModel = hiltViewModel()
) {
    // Load person on first composition or when personId changes
    LaunchedEffect(personId) {
        viewModel.loadPerson(personId)
    }

    val person by viewModel.person.collectAsState()
    val dialogues by viewModel.dialogues.collectAsState()
    val voiceSampleCount by viewModel.voiceSampleCount.collectAsState()
    val context = LocalContext.current

    // Derive profile mode from the incoming personId
    val isMyProfile  = personId == -1L
    val isNewPerson  = personId == -2L

    val screenTitle = when {
        isMyProfile -> "My Profile"
        isNewPerson -> "New Person"
        else        -> "Edit Profile"
    }

    // Local editable state — re-initialised whenever the loaded person changes
    var nameText  by rememberSaveable(person) { mutableStateOf(person?.name ?: "") }
    var ageText   by rememberSaveable(person) { mutableStateOf(person?.age?.toString() ?: "") }
    var relation  by rememberSaveable(person) { mutableStateOf(person?.relationToUser ?: if (isMyProfile) "Self" else "Other") }
    var notesText by rememberSaveable(person) { mutableStateOf(person?.notes ?: "") }
    var nameError by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Photo picker launcher
    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.updatePhoto(context, it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = screenTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {

            // ── Profile photo ──────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .padding(top = 24.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                PersonAvatar(
                    name = nameText.ifBlank { "?" },
                    photoPath = person?.profilePhotoPath,
                    size = 96.dp
                )
                // Camera overlay — only shown when a person record exists to attach the photo to
                if (!isNewPerson || person != null) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(PurplePrimary)
                            .clickable { photoLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Change photo",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Editable fields ────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                // Name
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it; nameError = false },
                    label = { Text("Name") },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text("Name is required") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Age
                OutlinedTextField(
                    value = ageText,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.all { it.isDigit() }) {
                            ageText = input
                        }
                    },
                    label = { Text("Age") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Relation dropdown
                RelationDropdown(
                    selected = relation,
                    options = RELATION_OPTIONS,
                    onSelect = { relation = it },
                    modifier = Modifier.fillMaxWidth()
                )

                // Notes
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Notes") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Voice samples count ────────────────────────────────────────
            if (!isNewPerson) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Voice samples: $voiceSampleCount",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Save button ────────────────────────────────────────────────
            Button(
                onClick = {
                    if (nameText.isBlank()) {
                        nameError = true
                    } else {
                        val age = ageText.trim().toIntOrNull()
                        viewModel.savePerson(nameText, age, relation, notesText)
                        if (isNewPerson) onBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
            ) {
                Text("Save")
            }

            // ── Delete button (non-self, non-new persons only) ─────────────
            if (!isMyProfile && !isNewPerson) {
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Delete Person",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // ── Dialogues section (not shown for a brand-new unsaved person) ─
            if (!isNewPerson) {
                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                SectionHeader(title = "Dialogues")

                if (dialogues.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No dialogues recorded yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // Rendered as a plain Column inside the parent verticalScroll to avoid
                    // nested-scroll conflicts with LazyColumn.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        dialogues.forEach { dialogue ->
                            DialogueCard(
                                dialogue = dialogue,
                                onClick = { onDialogueClick(dialogue.id) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // ── Delete confirmation dialog ─────────────────────────────────────
        if (showDeleteDialog) {
            ConfirmDeleteDialog(
                title = "Delete Person",
                message = "Delete \"${person?.name ?: "this person"}\" and all associated data? This cannot be undone.",
                onConfirm = {
                    viewModel.deletePerson()
                    showDeleteDialog = false
                    onBack()
                },
                onDismiss = { showDeleteDialog = false }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RelationDropdown
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RelationDropdown(
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("Relation") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DialogueCard (profile-scoped, lighter style than the standalone Dialogues tab)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DialogueCard(
    dialogue: Dialogue,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Chat,
                contentDescription = null,
                tint = PurplePrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dialogue.subject.ifBlank { "Untitled dialogue" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = TimeUtils.formatDateTime(dialogue.recordedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
