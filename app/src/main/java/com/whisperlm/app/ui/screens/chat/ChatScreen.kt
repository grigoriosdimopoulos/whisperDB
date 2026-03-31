package com.whisperlm.app.ui.screens.chat

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whisperlm.app.domain.model.ChatMessage
import com.whisperlm.app.domain.model.MessageRole
import com.whisperlm.app.domain.usecase.llm.BuildContextUseCase
import com.whisperlm.app.ml.llm.LlmBackend
import com.whisperlm.app.ml.llm.LlmEngine
import com.whisperlm.app.ui.theme.BubbleAssistant
import com.whisperlm.app.ui.theme.BubbleUser
import com.whisperlm.app.ui.theme.PurplePrimary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val llmEngine: LlmEngine,
    private val buildContextUseCase: BuildContextUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
        private const val STATUS_READY = "Ready"
        private const val STATUS_LOADING = "Loading model…"
        private const val STATUS_NO_MODEL =
            "No model found. Add a .task or .gguf file to use chat."
    }

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _llmStatus = MutableStateFlow(STATUS_LOADING)
    val llmStatus: StateFlow<String> = _llmStatus.asStateFlow()

    // Pre-built conversation context injected at inference time
    private var builtContext: String = ""

    init {
        viewModelScope.launch {
            // Load the LLM model and build the conversation context in parallel
            val backendDeferred = launch {
                try {
                    val backend = llmEngine.loadModel()
                    _llmStatus.value = when (backend) {
                        LlmBackend.NONE -> STATUS_NO_MODEL
                        else -> STATUS_READY
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Model load error: ${e.message}")
                    _llmStatus.value = STATUS_NO_MODEL
                }
            }

            val contextDeferred = launch {
                try {
                    builtContext = buildContextUseCase()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to build context: ${e.message}")
                    builtContext = ""
                }
            }

            backendDeferred.join()
            contextDeferred.join()
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || _isLoading.value) return

        // Append the user message immediately
        val userMsg = ChatMessage(role = MessageRole.USER, content = trimmed)
        _messages.update { it + userMsg }

        viewModelScope.launch {
            _isLoading.value = true

            // Add a streaming placeholder for the assistant
            val streamingId = System.currentTimeMillis() + 1
            val placeholder = ChatMessage(
                id = streamingId,
                role = MessageRole.ASSISTANT,
                content = "",
                isStreaming = true
            )
            _messages.update { it + placeholder }

            try {
                val accumulated = StringBuilder()
                llmEngine.generate(systemContext = builtContext, userQuery = trimmed)
                    .collect { token ->
                        accumulated.append(token)
                        // Replace the streaming placeholder with the current accumulated text
                        _messages.update { list ->
                            list.map { msg ->
                                if (msg.id == streamingId) {
                                    msg.copy(content = accumulated.toString(), isStreaming = true)
                                } else {
                                    msg
                                }
                            }
                        }
                    }

                // Finalize: mark streaming as done
                _messages.update { list ->
                    list.map { msg ->
                        if (msg.id == streamingId) {
                            msg.copy(
                                content = accumulated.toString().ifBlank { "[No response]" },
                                isStreaming = false
                            )
                        } else {
                            msg
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Generation error: ${e.message}")
                _messages.update { list ->
                    list.map { msg ->
                        if (msg.id == streamingId) {
                            msg.copy(
                                content = "Error: ${e.message ?: "Unknown error"}",
                                isStreaming = false
                            )
                        } else {
                            msg
                        }
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        llmEngine.release()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val llmStatus by viewModel.llmStatus.collectAsState()

    val hasModel = llmStatus == "Ready" || isLoading
    val listState = rememberLazyListState()

    // Scroll to bottom whenever the message list grows
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    // Also scroll when the last message's content changes (streaming tokens)
    val lastContent = messages.lastOrNull()?.content
    LaunchedEffect(lastContent) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(PurplePrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = "whisperLM logo",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "whisperLM",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = llmStatus,
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    llmStatus == "Ready" -> MaterialTheme.colorScheme.primary
                                    llmStatus.startsWith("No model") ->
                                        MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
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
        ) {
            // Message list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        ChatEmptyState(hasModel = hasModel)
                    }
                }

                items(messages, key = { it.id }) { message ->
                    ChatBubble(message = message)
                }

                // Inline loading indicator while generating
                if (isLoading && messages.lastOrNull()?.isStreaming == false) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = PurplePrimary
                            )
                        }
                    }
                }
            }

            // No-model banner replaces input
            if (llmStatus.startsWith("No model")) {
                NoModelBanner()
            } else {
                ChatInput(
                    isLoading = isLoading,
                    onSend = { viewModel.sendMessage(it) }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Chat bubble
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == MessageRole.USER
    val bubbleColor = if (isUser) BubbleUser else BubbleAssistant
    val textColor = Color.White
    val senderName = if (isUser) "You" else "whisperLM"
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bubbleShape = if (isUser) {
        RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 4.dp,
            bottomStart = 16.dp,
            bottomEnd = 16.dp
        )
    } else {
        RoundedCornerShape(
            topStart = 4.dp,
            topEnd = 16.dp,
            bottomStart = 16.dp,
            bottomEnd = 16.dp
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Text(
            text = senderName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )
        Surface(
            shape = bubbleShape,
            color = bubbleColor,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
                if (message.isStreaming) {
                    Spacer(modifier = Modifier.height(4.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(10.dp),
                        strokeWidth = 1.5.dp,
                        color = textColor.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Chat input row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChatInput(
    isLoading: Boolean,
    onSend: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }

    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Ask about your conversations…") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (inputText.isNotBlank() && !isLoading) {
                            onSend(inputText)
                            inputText = ""
                        }
                    }
                ),
                shape = RoundedCornerShape(24.dp)
            )
            IconButton(
                onClick = {
                    if (inputText.isNotBlank() && !isLoading) {
                        onSend(inputText)
                        inputText = ""
                    }
                },
                enabled = inputText.isNotBlank() && !isLoading,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (inputText.isNotBlank() && !isLoading)
                            PurplePrimary
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = if (inputText.isNotBlank()) Color.White
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// No-model info banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NoModelBanner() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "No LLM model found. Go to Settings and add a .task or .gguf file to enable chat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChatEmptyState(hasModel: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = PurplePrimary,
                    modifier = Modifier.size(36.dp)
                )
            }
            Text(
                text = "whisperLM Chat",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (hasModel)
                    "Ask anything about your recorded conversations. The AI has access to all your stored dialogues."
                else
                    "Add a .task or .gguf model file to start chatting with your conversations.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
