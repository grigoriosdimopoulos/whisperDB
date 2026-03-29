package com.whisperlm.app.ui.screens.dialoguedetail

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whisperlm.app.core.util.TimeUtils
import com.whisperlm.app.domain.model.*
import com.whisperlm.app.domain.repository.DialogueRepository
import com.whisperlm.app.domain.repository.PersonRepository
import com.whisperlm.app.domain.usecase.audio.PlaybackUseCase
import com.whisperlm.app.domain.usecase.audio.PlaybackState
import com.whisperlm.app.domain.usecase.dialogue.DeleteDialogueUseCase
import com.whisperlm.app.domain.usecase.dialogue.SaveDialogueUseCase
import com.whisperlm.app.ui.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── ViewModel ───────────────────────────────────────────────────────────────

@HiltViewModel
class DialogueDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val dialogueRepository: DialogueRepository,
    private val personRepository: PersonRepository,
    private val saveDialogueUseCase: SaveDialogueUseCase,
    private val deleteDialogueUseCase: DeleteDialogueUseCase,
    val playbackUseCase: PlaybackUseCase
) : ViewModel() {

    private val dialogueId: Long = savedStateHandle["dialogueId"] ?: 0L

    private val _dialogue = MutableStateFlow<Dialogue?>(null)
    val dialogue: StateFlow<Dialogue?> = _dialogue.asStateFlow()

    private val _segments = MutableStateFlow<List<TranscriptionSegment>>(emptyList())
    val segments: StateFlow<List<TranscriptionSegment>> = _segments.asStateFlow()

    private val _allPersons = MutableStateFlow<List<Person>>(emptyList())
    val allPersons: StateFlow<List<Person>> = _allPersons.asStateFlow()

    val playbackState: StateFlow<PlaybackState> = playbackUseCase.state

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    init {
        viewModelScope.launch {
            val d = dialogueRepository.getDialogueById(dialogueId)
            _dialogue.value = d
            _segments.value = d?.segments ?: emptyList()
        }
        viewModelScope.launch {
            personRepository.getAllPersons().collect { _allPersons.value = it }
        }
    }

    fun updateSubject(text: String) {
        _dialogue.value = _dialogue.value?.copy(subject = text)
        _isDirty.value = true
    }

    fun updateSegmentText(segmentId: Long, text: String) {
        _segments.value = _segments.value.map {
            if (it.id == segmentId) it.copy(text = text) else it
        }
        _isDirty.value = true
    }

    fun updateSegmentSpeaker(segmentId: Long, personId: Long) {
        val person = _allPersons.value.firstOrNull { it.id == personId }
        _segments.value = _segments.value.map {
            if (it.id == segmentId)
                it.copy(personId = personId, personName = person?.name)
            else it
        }
        _isDirty.value = true
    }

    fun togglePlayback() {
        val state = playbackState.value
        val audioPath = _dialogue.value?.audioFilePath ?: return
        when {
            !state.isPlaying && state.filePath != audioPath -> playbackUseCase.play(audioPath)
            state.isPlaying -> playbackUseCase.pause()
            else -> playbackUseCase.resume()
        }
    }

    fun seekTo(ms: Long) = playbackUseCase.seekTo(ms)

    fun saveChanges() {
        viewModelScope.launch {
            val d = _dialogue.value ?: return@launch
            val updated = d.copy(segments = _segments.value)
            saveDialogueUseCase(updated)
            _isDirty.value = false
        }
    }

    fun deleteDialogue() {
        viewModelScope.launch {
            deleteDialogueUseCase(dialogueId)
            playbackUseCase.stop()
            _deleted.value = true
        }
    }

    override fun onCleared() {
        super.onCleared()
        playbackUseCase.stop()
    }
}

// ─── Screen ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogueDetailScreen(
    dialogueId: Long,
    onPersonClick: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: DialogueDetailViewModel = hiltViewModel()
) {
    val dialogue by viewModel.dialogue.collectAsState()
    val segments by viewModel.segments.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val isDirty by viewModel.isDirty.collectAsState()
    val deleted by viewModel.deleted.collectAsState()
    val allPersons by viewModel.allPersons.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(deleted) { if (deleted) onBack() }

    if (dialogue == null) {
        LoadingIndicator()
        return
    }

    val d = dialogue!!

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        d.subject.ifBlank { "Dialogue" },
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (isDirty) {
                        IconButton(onClick = { viewModel.saveChanges() }) {
                            Icon(Icons.Filled.Save, "Save")
                        }
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Filled.Delete, "Delete",
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        },
        floatingActionButton = {
            if (isDirty) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.saveChanges() },
                    icon = { Icon(Icons.Filled.Save, null) },
                    text = { Text("Save Changes") }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Subject editor
            item {
                var subjectText by remember(d.subject) { mutableStateOf(d.subject) }
                OutlinedTextField(
                    value = subjectText,
                    onValueChange = { subjectText = it; viewModel.updateSubject(it) },
                    label = { Text("Subject") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )
            }

            // Meta row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CalendarToday, null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        TimeUtils.formatDateTime(d.recordedAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Filled.Timer, null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        TimeUtils.formatDuration(d.durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Participants row
            if (d.participants.isNotEmpty()) {
                item {
                    SectionHeader("Participants")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        items(d.participants) { person ->
                            FilterChip(
                                selected = false,
                                onClick = { onPersonClick(person.id) },
                                label = { Text(person.name) },
                                leadingIcon = {
                                    PersonAvatar(name = person.name, size = 24.dp)
                                }
                            )
                        }
                    }
                }
            }

            // Audio player
            if (d.audioFilePath != null) {
                item {
                    AudioPlayerBar(
                        playbackState = playbackState,
                        durationMs = d.durationMs,
                        onPlayPause = { viewModel.togglePlayback() },
                        onSeek = { viewModel.seekTo(it) },
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // Divider before transcript
            item {
                SectionHeader("Transcript")
            }

            // Transcript segments
            items(segments, key = { it.id }) { seg ->
                TranscriptSegmentRow(
                    segment = seg,
                    allPersons = allPersons,
                    onTextChange = { viewModel.updateSegmentText(seg.id, it) },
                    onSpeakerChange = { viewModel.updateSegmentSpeaker(seg.id, it) },
                    onPersonClick = { seg.personId?.let { pid -> onPersonClick(pid) } }
                )
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showDeleteDialog) {
        ConfirmDeleteDialog(
            title = "Delete Dialogue?",
            message = "This will permanently delete the dialogue and its transcript.",
            onConfirm = { viewModel.deleteDialogue() },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

// ─── Audio Player Bar ────────────────────────────────────────────────────────

@Composable
private fun AudioPlayerBar(
    playbackState: PlaybackState,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPlayPause) {
                Icon(
                    if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                val posMs = playbackState.positionMs
                val durMs = durationMs.takeIf { it > 0L } ?: 1L
                Slider(
                    value = posMs.toFloat() / durMs.toFloat(),
                    onValueChange = { onSeek((it * durMs).toLong()) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(TimeUtils.formatDuration(posMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(TimeUtils.formatDuration(durMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ─── Transcript Segment Row ───────────────────────────────────────────────────

@Composable
private fun TranscriptSegmentRow(
    segment: TranscriptionSegment,
    allPersons: List<Person>,
    onTextChange: (String) -> Unit,
    onSpeakerChange: (Long) -> Unit,
    onPersonClick: () -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var editText by remember(segment.text) { mutableStateOf(segment.text) }
    var showSpeakerMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { if (!editing) editing = true })
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TimestampChip(segment.timestampStartMs)

            Box {
                SuggestionChip(
                    onClick = { showSpeakerMenu = true },
                    label = {
                        Text(
                            segment.personName ?: segment.speakerLabel,
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    icon = {
                        Icon(Icons.Filled.Person, null,
                            modifier = Modifier.size(14.dp))
                    }
                )
                DropdownMenu(
                    expanded = showSpeakerMenu,
                    onDismissRequest = { showSpeakerMenu = false }
                ) {
                    allPersons.forEach { person ->
                        DropdownMenuItem(
                            text = { Text(person.name) },
                            onClick = {
                                onSpeakerChange(person.id)
                                showSpeakerMenu = false
                            },
                            leadingIcon = {
                                PersonAvatar(name = person.name, size = 24.dp)
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        if (editing) {
            OutlinedTextField(
                value = editText,
                onValueChange = { editText = it },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                ),
                trailingIcon = {
                    Row {
                        IconButton(onClick = {
                            onTextChange(editText)
                            editing = false
                        }) {
                            Icon(Icons.Filled.Check, "Save",
                                tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { editing = false; editText = segment.text }) {
                            Icon(Icons.Filled.Close, "Cancel")
                        }
                    }
                }
            )
        } else {
            Text(
                text = segment.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth()
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
