package com.nova.ai.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nova.ai.data.local.AppDatabase
import com.nova.ai.data.local.entity.ChatMessage
import com.nova.ai.data.settings.SettingsManager
import com.nova.ai.service.LLMService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val db = remember { AppDatabase.getDatabase(context) }
    val settingsManager = remember { SettingsManager(context) }
    val llmService = remember { LLMService(context) }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var currentResponse by remember { mutableStateOf("") }
    var modelPath by remember { mutableStateOf("") }
    var temperature by remember { mutableFloatStateOf(0.7f) }
    var maxTokens by remember { mutableIntStateOf(2048) }
    var contextLength by remember { mutableIntStateOf(2048) }
    var threads by remember { mutableIntStateOf(4) }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        launch { db.chatDao().getMessages("default").collectLatest { messages = it } }
        launch { settingsManager.modelPath.collect { modelPath = it } }
        launch { settingsManager.temperature.collect { temperature = it } }
        launch { settingsManager.maxTokens.collect { maxTokens = it } }
        launch { settingsManager.contextLength.collect { contextLength = it } }
        launch { settingsManager.threads.collect { threads = it } }
    }
    LaunchedEffect(messages.size, currentResponse) {
        if (listState.canScrollForward) listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { msg -> MessageBubble(message = msg) }
            if (isGenerating && currentResponse.isNotEmpty()) item { MessageBubble(message = ChatMessage(content = currentResponse, isUser = false), isStreaming = true) }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = inputText, onValueChange = { inputText = it }, modifier = Modifier.weight(1f), placeholder = { Text("Message Nova...") }, enabled = !isGenerating)
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                if (inputText.isNotBlank() && !isGenerating) {
                    scope.launch {
                        if (!llmService.isModelLoaded()) {
                            if (modelPath.isBlank()) { status = "No model path set. Import in Settings."; return@launch }
                            status = "Loading model (1.1 GB)... please wait ~10s"
                            val loaded = withContext(Dispatchers.IO) { llmService.loadModel(modelPath, threads, contextLength) }
                            if (!loaded) { status = "Model file not readable. Re-import it."; return@launch }
                            status = ""
                        }
                        db.chatDao().insertMessage(ChatMessage(content = inputText, isUser = true))
                        val prompt = buildPrompt(messages + ChatMessage(content = inputText, isUser = true))
                        isGenerating = true; currentResponse = ""
                        llmService.generateResponse(prompt, temperature, maxTokens).collectLatest { token -> currentResponse += token }
                        if (currentResponse.isNotEmpty()) db.chatDao().insertMessage(ChatMessage(content = currentResponse, isUser = false))
                        isGenerating = false; currentResponse = ""; inputText = ""
                    }
                }
            }, enabled = inputText.isNotBlank() && !isGenerating) { Text(if (isGenerating) "..." else "Send") }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage, isStreaming: Boolean = false) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (message.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = message.content + if (isStreaming) " ▊" else "", style = MaterialTheme.typography.bodyMedium, color = if (message.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            if (!message.isUser) Text("Nova AI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.padding(top = 4.dp))
        }
    }
}

private fun buildPrompt(messages: List<ChatMessage>): String {
    val sb = StringBuilder()
    sb.append("<|im_start|>system\nYou are Nova AI, a helpful assistant.<|im_end|>\n")
    messages.takeLast(10).forEach { msg ->
        val role = if (msg.isUser) "user" else "assistant"
        sb.append("<|im_start|>$role\n${msg.content}<|im_end|>\n")
    }
    sb.append("<|im_start|>assistant\n")
    return sb.toString()
}
