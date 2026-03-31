package com.whisperlm.app.ui.screens.addrecording

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whisperlm.app.core.util.FileUtils
import com.whisperlm.app.core.util.TimeUtils
import com.whisperlm.app.domain.model.*
import com.whisperlm.app.domain.repository.PersonRepository
import com.whisperlm.app.domain.usecase.audio.RecordAudioUseCase
import com.whisperlm.app.domain.usecase.diarization.MatchVoiceUseCase
import com.whisperlm.app.domain.usecase.dialogue.SaveDialogueUseCase
import com.whisperlm.app.ml.diarization.DiarizationEngine
import com.whisperlm.app.ml.whisper.WhisperEngine
import com.whisperlm.app.ui.components.*
import com.whisperlm.app.ui.theme.PurplePrimary
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

// ─── State ───────────────────────────────────────────────────────────────────

enum class AddRecordingState {
    IDLE, RECORDING, PROCESSING_STT, PROCESSING_DIARIZATION, ASSIGNING_SPEAKERS, SAVING, DONE
}

// ─── ViewModel ───────────────────────────────────────────────────────────────

@HiltViewModel
class AddRecordingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recordAudioUseCase: RecordAudioUseCase,
    private val whisperEngine: WhisperEngine,
    private val diarizationEngine: DiarizationEngine,
    private val matchVoiceUseCase: MatchVoiceUseCase,
    private val saveDialogueUseCase: SaveDialogueUseCase,
    private val personRepository: PersonRepository
) : ViewModel() {

    val screenState = MutableStateFlow(AddRecordingState.IDLE)
    val waveformAmplitudes = MutableStateFlow<List<Float>>(emptyList())
    val elapsedRecordingMs = MutableStateFlow(0L)
    val transcriptSegments = MutableStateFlow<List<TranscriptionSegment>>(emptyList())
    val speakerClusters = MutableStateFlow<List<SpeakerCluster>>(emptyList())
    val speakerNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val speakerSuggestions = MutableStateFlow<Map<String, Pair<Person, Float>?>>(emptyMap())
    val subject = MutableStateFlow("")
    val recordedAt = MutableStateFlow(System.currentTimeMillis())
    val error = MutableStateFlow<String?>(null)
    val savedDialogueId = MutableStateFlow<Long?>(null)

    private var recordingJob: Job? = null
    private var audioFile: File? = null

    fun startRecording() {
        screenState.value = AddRecordingState.RECORDING
        waveformAmplitudes.value = emptyList()
        elapsedRecordingMs.value = 0L
        recordedAt.value = System.currentTimeMillis()

        recordingJob = viewModelScope.launch {
            try {
                recordAudioUseCase.startRecording().collect { chunk ->
                    elapsedRecordingMs.value = chunk.elapsedMs
                    val amplitude = chunk.pcmFloats.maxOrNull()?.let { Math.abs(it) } ?: 0f
                    waveformAmplitudes.value = (waveformAmplitudes.value + amplitude).takeLast(80)
                }
            } catch (e: Throwable) {
                error.value = "Recording failed: ${e.message}"
                screenState.value = AddRecordingState.IDLE
            }
        }
    }

    fun stopRecording() {
        recordingJob?.cancel()
        val file = recordAudioUseCase.stopAndSave()
        if (file != null) {
            audioFile = file
            processAudio(file)
        } else {
            screenState.value = AddRecordingState.IDLE
            error.value = "Recording failed. Please try again."
        }
    }

    fun cancelRecording() {
        recordingJob?.cancel()
        recordAudioUseCase.cancel()
        screenState.value = AddRecordingState.IDLE
        waveformAmplitudes.value = emptyList()
    }

    fun importFile(uri: Uri) {
        viewModelScope.launch {
            try {
                val fileName = FileUtils.getFileNameFromUri(context, uri)
                val ext = fileName.substringAfterLast('.', "wav")
                val destName = "imported_${System.currentTimeMillis()}.$ext"
                val copiedFile = FileUtils.copyUriToRecordingsDir(context, uri, destName)
                audioFile = copiedFile
                // Try to get recorded date from file metadata; use current time as fallback
                recordedAt.value = copiedFile.lastModified().takeIf { it > 0L }
                    ?: System.currentTimeMillis()
                processAudio(copiedFile)
            } catch (e: Exception) {
                error.value = "Failed to import file: ${e.message}"
            }
        }
    }

    private fun processAudio(file: File) {
        viewModelScope.launch {
            // Step 1: Load Whisper model if needed
            if (!whisperEngine.isLoaded()) {
                val loaded = whisperEngine.loadModel()
                if (!loaded) {
                    error.value = "Whisper model not found. Please add a whisper-*.bin file in the setup."
                    screenState.value = AddRecordingState.IDLE
                    return@launch
                }
            }

            // Step 2: Transcription
            screenState.value = AddRecordingState.PROCESSING_STT
            val segments = whisperEngine.transcribeFile(file, dialogueId = 0L)
            transcriptSegments.value = segments

            // Step 3: Diarization
            screenState.value = AddRecordingState.PROCESSING_DIARIZATION
            val pcmFloats = loadWavAsPcmFloat(file)
            val clusters = diarizationEngine.diarize(pcmFloats)

            // Assign speaker labels to segments based on timing
            val labeledSegments = assignLabelsToSegments(segments, clusters, pcmFloats.size)
            transcriptSegments.value = labeledSegments
            speakerClusters.value = clusters

            // Step 4: Voice matching suggestions
            val suggestions = mutableMapOf<String, Pair<Person, Float>?>()
            for (cluster in clusters) {
                val match = matchVoiceUseCase(cluster.embedding)
                suggestions[cluster.label] = match
            }
            speakerSuggestions.value = suggestions

            // Initialize speaker names with suggestions
            val names = mutableMapOf<String, String>()
            for (cluster in clusters) {
                names[cluster.label] = suggestions[cluster.label]?.first?.name ?: ""
            }
            speakerNames.value = names

            screenState.value = AddRecordingState.ASSIGNING_SPEAKERS
        }
    }

    private fun assignLabelsToSegments(
        segments: List<TranscriptionSegment>,
        clusters: List<SpeakerCluster>,
        totalSamples: Int
    ): List<TranscriptionSegment> {
        if (clusters.isEmpty()) return segments
        if (clusters.size == 1) {
            return segments.map { it.copy(speakerLabel = clusters[0].label) }
        }
        // Simple: divide audio into equal-ish speaker segments based on cluster ordering
        val msPerCluster = (totalSamples.toLong() * 1000L / 16000L) / clusters.size
        return segments.map { seg ->
            val clusterIdx = (seg.timestampStartMs / msPerCluster.coerceAtLeast(1L))
                .toInt()
                .coerceIn(0, clusters.size - 1)
            seg.copy(speakerLabel = clusters[clusterIdx].label)
        }
    }

    fun assignSpeakerName(speakerLabel: String, name: String) {
        speakerNames.value = speakerNames.value.toMutableMap().also { it[speakerLabel] = name }
    }

    fun confirmAndSave() {
        viewModelScope.launch {
            screenState.value = AddRecordingState.SAVING
            try {
                val names = speakerNames.value
                val clusters = speakerClusters.value

                // Create or find persons for each speaker
                val labelToPersonId = mutableMapOf<String, Long>()
                for (cluster in clusters) {
                    val name = names[cluster.label]?.trim() ?: continue
                    if (name.isBlank()) continue
                    val suggestion = speakerSuggestions.value[cluster.label]
                    val personId = if (suggestion != null && suggestion.first.name == name) {
                        // Use existing person
                        suggestion.first.id
                    } else {
                        // Create new person
                        personRepository.savePerson(Person(name = name))
                    }
                    labelToPersonId[cluster.label] = personId

                    // Save voice embedding for this speaker
                    personRepository.saveVoiceEmbedding(
                        VoiceEmbedding(personId = personId, embedding = cluster.embedding)
                    )
                }

                // Build final segments with personIds
                val finalSegments = transcriptSegments.value.map { seg ->
                    seg.copy(personId = labelToPersonId[seg.speakerLabel])
                }

                // Build participants list
                val participants = labelToPersonId.values.distinct().mapNotNull { pid ->
                    personRepository.getPersonById(pid)
                }

                val dialogue = Dialogue(
                    subject = subject.value.trim().ifBlank { "Conversation" },
                    audioFilePath = audioFile?.absolutePath,
                    recordedAt = recordedAt.value,
                    durationMs = if (finalSegments.isNotEmpty())
                        finalSegments.last().timestampEndMs else 0L,
                    participants = participants,
                    segments = finalSegments
                )

                val id = saveDialogueUseCase(dialogue)
                savedDialogueId.value = id
                screenState.value = AddRecordingState.DONE
            } catch (e: Exception) {
                error.value = "Failed to save: ${e.message}"
                screenState.value = AddRecordingState.ASSIGNING_SPEAKERS
            }
        }
    }

    private fun loadWavAsPcmFloat(file: File): FloatArray {
        val bytes = file.readBytes()
        if (bytes.size <= 44) return FloatArray(0)
        val pcmBytes = bytes.copyOfRange(44, bytes.size)
        val samples = pcmBytes.size / 2
        val floats = FloatArray(samples)
        val buf = java.nio.ByteBuffer.wrap(pcmBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until samples) floats[i] = buf.short.toFloat() / 32768f
        return floats
    }

    override fun onCleared() {
        super.onCleared()
        recordingJob?.cancel()
    }
}

// ─── Screen ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecordingScreen(
    onDialogueSaved: (Long) -> Unit,
    viewModel: AddRecordingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val screenState by viewModel.screenState.collectAsState()
    val waveform by viewModel.waveformAmplitudes.collectAsState()
    val elapsed by viewModel.elapsedRecordingMs.collectAsState()
    val segments by viewModel.transcriptSegments.collectAsState()
    val clusters by viewModel.speakerClusters.collectAsState()
    val speakerNames by viewModel.speakerNames.collectAsState()
    val suggestions by viewModel.speakerSuggestions.collectAsState()
    val subject by viewModel.subject.collectAsState()
    val error by viewModel.error.collectAsState()
    val savedId by viewModel.savedDialogueId.collectAsState()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.importFile(it) } }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startRecording()
        else viewModel.error.value = "Microphone permission is required to record audio."
    }

    // Navigate when saved
    LaunchedEffect(savedId) {
        savedId?.let { onDialogueSaved(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Add Recording") })
        }
    ) { padding ->
        when (screenState) {
            AddRecordingState.IDLE -> IdleContent(
                modifier = Modifier.padding(padding),
                onRecord = {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPermission) viewModel.startRecording()
                    else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                onImport = { importLauncher.launch("audio/*") }
            )

            AddRecordingState.RECORDING -> RecordingContent(
                modifier = Modifier.padding(padding),
                elapsed = elapsed,
                waveform = waveform,
                onStop = { viewModel.stopRecording() },
                onCancel = { viewModel.cancelRecording() }
            )

            AddRecordingState.PROCESSING_STT,
            AddRecordingState.PROCESSING_DIARIZATION -> ProcessingContent(
                modifier = Modifier.padding(padding),
                state = screenState,
                segments = segments
            )

            AddRecordingState.ASSIGNING_SPEAKERS -> AssignSpeakersContent(
                modifier = Modifier.padding(padding),
                subject = subject,
                clusters = clusters,
                speakerNames = speakerNames,
                suggestions = suggestions,
                segments = segments,
                onSubjectChange = { viewModel.subject.value = it },
                onAssignName = { label, name -> viewModel.assignSpeakerName(label, name) },
                onSave = { viewModel.confirmAndSave() }
            )

            AddRecordingState.SAVING -> LoadingIndicator(
                message = "Saving…",
                modifier = Modifier.padding(padding)
            )

            AddRecordingState.DONE -> Unit // navigation handled above
        }

        error?.let { msg ->
            LaunchedEffect(msg) {
                // Show error — reset after a moment
            }
            AlertDialog(
                onDismissRequest = { viewModel.error.value = null },
                title = { Text("Error") },
                text = { Text(msg) },
                confirmButton = {
                    TextButton(onClick = { viewModel.error.value = null }) { Text("OK") }
                }
            )
        }
    }
}

@Composable
private fun IdleContent(
    onRecord: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Add a Conversation",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Record a new conversation or import an existing audio file.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onRecord,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.Mic, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text("Record New Conversation", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text("Import Audio File", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Supports: MP3, WAV, M4A, OGG",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecordingContent(
    elapsed: Long,
    waveform: List<Float>,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            tween(600, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(32.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(80.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
            ) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = "Recording",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                TimeUtils.formatDuration(elapsed),
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                "Recording…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(32.dp))
            WaveformVisualizer(amplitudes = waveform)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Stop, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Stop Recording")
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onCancel) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProcessingContent(
    state: AddRecordingState,
    segments: List<TranscriptionSegment>,
    modifier: Modifier = Modifier
) {
    val statusText = when (state) {
        AddRecordingState.PROCESSING_STT -> "Transcribing speech…"
        AddRecordingState.PROCESSING_DIARIZATION -> "Identifying speakers…"
        else -> "Processing…"
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text(statusText, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))

        if (segments.isNotEmpty()) {
            Text("Transcript so far:", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(segments) { seg ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TimestampChip(seg.timestampStartMs)
                        Text(
                            seg.text,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssignSpeakersContent(
    subject: String,
    clusters: List<SpeakerCluster>,
    speakerNames: Map<String, String>,
    suggestions: Map<String, Pair<Person, Float>?>,
    segments: List<TranscriptionSegment>,
    onSubjectChange: (String) -> Unit,
    onAssignName: (String, String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canSave = subject.isNotBlank() && speakerNames.values.all { it.isNotBlank() }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("Assign Speaker Names", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Name each voice detected in the recording.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            OutlinedTextField(
                value = subject,
                onValueChange = onSubjectChange,
                label = { Text("Subject / Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Title, null) }
            )
        }

        items(clusters) { cluster ->
            SpeakerCard(
                cluster = cluster,
                currentName = speakerNames[cluster.label] ?: "",
                suggestion = suggestions[cluster.label],
                onNameChange = { onAssignName(cluster.label, it) },
                segments = segments.filter { it.speakerLabel == cluster.label }
            )
        }

        item {
            Button(
                onClick = onSave,
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Save, null)
                Spacer(Modifier.width(8.dp))
                Text("Save Dialogue")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SpeakerCard(
    cluster: SpeakerCluster,
    currentName: String,
    suggestion: Pair<Person, Float>?,
    onNameChange: (String) -> Unit,
    segments: List<TranscriptionSegment>
) {
    val speakerNum = cluster.label.removePrefix("SPEAKER_").toIntOrNull()?.plus(1) ?: 1

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(PurplePrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$speakerNum",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Speaker $speakerNum",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "${segments.size} segments · ${TimeUtils.formatDuration(cluster.totalDurationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            suggestion?.let { (person, score) ->
                val pct = (score * 100).toInt()
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.RecordVoiceOver, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Suggested: ${person.name} ($pct% match)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { onNameChange(person.name) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Use", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = currentName,
                onValueChange = onNameChange,
                label = { Text("Name this speaker") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Person, null) }
            )

            if (segments.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "\"${segments.first().text.take(80)}…\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
