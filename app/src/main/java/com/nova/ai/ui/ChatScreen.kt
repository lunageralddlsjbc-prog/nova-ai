package com.nova.ai.ui
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nova.ai.data.local.AppDatabase
import com.nova.ai.data.local.entity.ChatMessage
import com.nova.ai.data.settings.SettingsManager
import com.nova.ai.service.LLMService
import com.nova.ai.ui.components.MarkdownText
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

    // Qwen-like Dark Background
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F13))) {
        Column(modifier = modifier.fillMaxSize()) {
            if (status.isNotEmpty()) {
                Text(status, style = MaterialTheme.typography.bodySmall, color = Color(0xFF8B5CF6), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages) { msg -> MessageBubble(message = msg) }
                if (isGenerating && currentResponse.isNotEmpty()) {
                    item { MessageBubble(message = ChatMessage(content = currentResponse, isUser = false), isStreaming = true) }
                }
            }

            // Input Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1A1A20),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message Nova AI...", color = Color.Gray) },
                        enabled = !isGenerating,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF8B5CF6),
                            unfocusedBorderColor = Color(0xFF333340),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (inputText.isNotBlank() && !isGenerating) {
                                scope.launch {
                                    if (!llmService.isModelLoaded()) {
                                        if (modelPath.isBlank()) { status = "Please import a model in Settings"; return@launch }
                                        status = "Loading model... (This may take 10-30s)"
                                        val loaded = withContext(Dispatchers.IO) { llmService.loadModel(modelPath, threads, contextLength) }
                                        if (!loaded) { status = "Failed to load model. Check path."; return@launch }
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
                        },
                        enabled = inputText.isNotBlank() && !isGenerating,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (isGenerating) "..." else "Send", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage, isStreaming: Boolean = false) {
    val isUser = message.isUser
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) Color(0xFF8B5CF6) else Color(0xFF1E1E28),
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (!isUser) {
                    Text("Nova AI", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8B5CF6), modifier = Modifier.padding(bottom = 6.dp))
                }
                // Use MarkdownText for AI, regular Text for user
                if (isUser) {
                    Text(text = message.content, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                } else {
                    MarkdownText(
                        markdown = message.content + if (isStreaming) " ▊" else "",
                        isUser = false
                    )
                }
            }
        }
    }
}

private fun buildPrompt(messages: List<ChatMessage>): String {
    val sb = StringBuilder()
    // System prompt to make it act like Qwen
    sb.append("You are Nova AI, a helpful, harmless, and honest AI assistant. You provide clear, structured responses using markdown formatting (bold, code blocks, lists) when appropriate. Be concise but thorough.\n\n")
    
    // Universal Alpaca-style prompt (works on almost all models)
    messages.takeLast(10).forEach { msg ->
        if (msg.isUser) {
            sb.append("### Instruction:\n${msg.content}\n\n")
        } else {
            sb.append("### Response:\n${msg.content}\n\n")
        }
    }
    sb.append("### Response:\n")
    return sb.toString()
}
