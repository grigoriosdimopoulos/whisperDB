package com.whisperlm.app.ui.screens.setup

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whisperlm.app.core.util.FileUtils
import com.whisperlm.app.domain.model.Person
import com.whisperlm.app.domain.repository.PersonRepository
import com.whisperlm.app.ui.theme.PurpleLight
import com.whisperlm.app.ui.theme.PurplePrimary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val personRepository: PersonRepository
) : ViewModel() {

    companion object {
        val KEY_SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
    }

    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    private val _whisperModelCopied = MutableStateFlow(false)
    val whisperModelCopied: StateFlow<Boolean> = _whisperModelCopied.asStateFlow()

    private val _llmModelCopied = MutableStateFlow(false)
    val llmModelCopied: StateFlow<Boolean> = _llmModelCopied.asStateFlow()

    private val _isCopying = MutableStateFlow(false)
    val isCopying: StateFlow<Boolean> = _isCopying.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Saved profile for use in finishSetup
    private var pendingName: String = ""
    private var pendingAge: Int? = null

    fun copyWhisperModel(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isCopying.value = true
            _errorMessage.value = null
            try {
                val fileName = FileUtils.getFileNameFromUri(context, uri)
                    .let { if (it == "unknown") "whisper_model.bin" else it }
                withContext(Dispatchers.IO) {
                    FileUtils.copyUriToModelsDir(context, uri, fileName)
                }
                _whisperModelCopied.value = true
            } catch (e: Exception) {
                _errorMessage.value = "Failed to copy model: ${e.message}"
            } finally {
                _isCopying.value = false
            }
        }
    }

    fun copyLlmModel(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isCopying.value = true
            _errorMessage.value = null
            try {
                val fileName = FileUtils.getFileNameFromUri(context, uri)
                    .let { if (it == "unknown") "llm_model.gguf" else it }
                withContext(Dispatchers.IO) {
                    FileUtils.copyUriToModelsDir(context, uri, fileName)
                }
                _llmModelCopied.value = true
            } catch (e: Exception) {
                _errorMessage.value = "Failed to copy model: ${e.message}"
            } finally {
                _isCopying.value = false
            }
        }
    }

    fun skipLlm() {
        _currentStep.value = 2
    }

    fun advanceFromStep(step: Int) {
        if (_currentStep.value == step) {
            _currentStep.value = step + 1
        }
    }

    fun saveProfile(name: String, age: Int?) {
        pendingName = name.trim()
        pendingAge = age
    }

    fun finishSetup(onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                // Save the self profile
                if (pendingName.isNotBlank()) {
                    val existing = personRepository.getSelfPerson()
                    val person = (existing ?: Person(
                        name = pendingName,
                        age = pendingAge,
                        isUserSelf = true
                    )).copy(
                        name = pendingName,
                        age = pendingAge,
                        isUserSelf = true
                    )
                    personRepository.savePerson(person)
                }
                // Mark setup as complete in DataStore
                dataStore.edit { prefs ->
                    prefs[KEY_SETUP_COMPLETE] = true
                }
                onComplete()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to save profile: ${e.message}"
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel()
) {
    val currentStep by viewModel.currentStep.collectAsState()
    val isCopying by viewModel.isCopying.collectAsState()
    val whisperModelCopied by viewModel.whisperModelCopied.collectAsState()
    val llmModelCopied by viewModel.llmModelCopied.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "whisperLM",
                style = MaterialTheme.typography.headlineLarge,
                color = PurplePrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Setup",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))

            // Step indicator
            SetupStepIndicator(currentStep = currentStep, totalSteps = 3)

            Spacer(modifier = Modifier.height(40.dp))

            // Step content
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it } + fadeOut())
                },
                label = "setup_step_content"
            ) { step ->
                when (step) {
                    0 -> StepWhisperModel(
                        isCopying = isCopying,
                        modelCopied = whisperModelCopied,
                        errorMessage = errorMessage,
                        onFilePicked = { uri, context ->
                            viewModel.copyWhisperModel(context, uri)
                        },
                        onNext = { viewModel.advanceFromStep(0) }
                    )
                    1 -> StepLlmModel(
                        isCopying = isCopying,
                        modelCopied = llmModelCopied,
                        errorMessage = errorMessage,
                        onFilePicked = { uri, context ->
                            viewModel.copyLlmModel(context, uri)
                        },
                        onNext = { viewModel.advanceFromStep(1) },
                        onSkip = { viewModel.skipLlm() }
                    )
                    2 -> StepProfile(
                        errorMessage = errorMessage,
                        onFinish = { name, age ->
                            viewModel.saveProfile(name, age)
                            viewModel.finishSetup(onComplete = onSetupComplete)
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step indicator
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SetupStepIndicator(currentStep: Int, totalSteps: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { index ->
            val isCompleted = index < currentStep
            val isCurrent = index == currentStep
            Box(
                modifier = Modifier
                    .size(if (isCurrent) 12.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isCompleted -> PurplePrimary
                            isCurrent -> PurpleLight
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
            )
            if (index < totalSteps - 1) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(2.dp)
                        .background(
                            if (isCompleted) PurplePrimary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 0: Add Whisper Model
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepWhisperModel(
    isCopying: Boolean,
    modelCopied: Boolean,
    errorMessage: String?,
    onFilePicked: (Uri, Context) -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onFilePicked(it, context) }
    }

    StepCard(
        stepNumber = 1,
        title = "Add Whisper Model",
        subtitle = "Select a Whisper .bin model file to enable speech-to-text transcription."
    ) {
        if (isCopying) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = PurplePrimary
                )
                Text(
                    text = "Copying model…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (modelCopied) {
            StatusBanner(
                text = "Model copied successfully",
                isSuccess = true
            )
        } else {
            FilledTonalButton(
                onClick = { launcher.launch("*/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Browse for .bin file")
            }
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onNext,
            enabled = modelCopied,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
        ) {
            Text("Next")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 1: Add LLM Model (optional)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepLlmModel(
    isCopying: Boolean,
    modelCopied: Boolean,
    errorMessage: String?,
    onFilePicked: (Uri, Context) -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onFilePicked(it, context) }
    }

    StepCard(
        stepNumber = 2,
        title = "Add LLM Model",
        subtitle = "Optional: select a .task (MediaPipe) or .gguf (llama.cpp) file to enable " +
                "AI chat about your recorded conversations."
    ) {
        if (isCopying) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = PurplePrimary
                )
                Text(
                    text = "Copying model…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (modelCopied) {
            StatusBanner(text = "Model copied successfully", isSuccess = true)
        } else {
            FilledTonalButton(
                onClick = { launcher.launch("*/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Browse for .task or .gguf file")
            }
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(
                onClick = onSkip,
                modifier = Modifier.weight(1f)
            ) {
                Text("Skip for now")
            }
            Button(
                onClick = onNext,
                enabled = modelCopied,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
            ) {
                Text("Next")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 2: Your Profile
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepProfile(
    errorMessage: String?,
    onFinish: (name: String, age: Int?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var ageText by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }

    StepCard(
        stepNumber = 3,
        title = "Your Profile",
        subtitle = "Tell whisperLM who you are so it can identify your voice in recordings."
    ) {
        // Name field
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                nameError = false
            },
            label = { Text("Your name *") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null
                )
            },
            isError = nameError,
            supportingText = if (nameError) {
                { Text("Name is required") }
            } else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Age field (optional)
        OutlinedTextField(
            value = ageText,
            onValueChange = { input ->
                // Only allow numeric input
                if (input.isEmpty() || input.all { it.isDigit() }) {
                    ageText = input
                }
            },
            label = { Text("Age (optional)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (name.isBlank()) {
                    nameError = true
                } else {
                    val age = ageText.trim().toIntOrNull()
                    onFinish(name.trim(), age)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
        ) {
            Text("Get Started")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared sub-components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepCard(
    stepNumber: Int,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "Step $stepNumber",
                style = MaterialTheme.typography.labelSmall,
                color = PurplePrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start
            )
            Spacer(modifier = Modifier.height(24.dp))
            content()
        }
    }
}

@Composable
private fun StatusBanner(text: String, isSuccess: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSuccess)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = if (isSuccess)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSuccess)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}
