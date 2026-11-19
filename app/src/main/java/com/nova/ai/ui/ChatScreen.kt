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
    var maxTokens by remember { mutableIntStateOf(512) }
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

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F13))) {
        Column(modifier = modifier.fillMaxSize()) {
            if (status.isNotEmpty()) {
                Text(status, style = MaterialTheme.typography.bodySmall, color = Color(0xFF8B5CF6), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(messages) { msg -> MessageBubble(message = msg) }
                if (isGenerating && currentResponse.isNotEmpty()) {
                    item { MessageBubble(message = ChatMessage(content = currentResponse, isUser = false), isStreaming = true) }
                }
            }
            Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF1A1A20), shadowElevation = 8.dp) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = inputText, onValueChange = { inputText = it }, modifier = Modifier.weight(1f),
                        placeholder = { Text("Message Nova AI...", color = Color.Gray) }, enabled = !isGenerating,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF8B5CF6), unfocusedBorderColor = Color(0xFF333340), focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (inputText.isNotBlank() && !isGenerating) {
                                scope.launch {
                                    if (!llmService.isModelLoaded()) {
                                        if (modelPath.isBlank()) { status = "Please import a model in Settings"; return@launch }
                                        status = "Loading model... (10-30s)"
                                        val loaded = withContext(Dispatchers.IO) { llmService.loadModel(modelPath, threads, contextLength) }
                                        if (!loaded) { status = "Failed to load model."; return@launch }
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
                    ) { Text(if (isGenerating) "..." else "Send", color = Color.White) }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage, isStreaming: Boolean = false) {
    val isUser = message.isUser
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (isUser) Color(0xFF8B5CF6) else Color(0xFF1E1E28),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (isUser) 16.dp else 4.dp, bottomEnd = if (isUser) 4.dp else 16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (!isUser) Text("Nova AI", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8B5CF6), modifier = Modifier.padding(bottom = 6.dp))
                if (isUser) {
                    Text(text = message.content, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                } else {
                    MarkdownText(markdown = message.content + if (isStreaming) " ▊" else "", isUser = false)
                }
            }
        }
    }
}

private fun buildPrompt(messages: List<ChatMessage>): String {
    val sb = StringBuilder()
    sb.append("

cd ~/NovaAI
cat > app/src/main/cpp/native.cpp << 'CPPEOF'
#include <jni.h>
#include <string>
#include <vector>
#include <thread>
#include <atomic>
#include "llama.h"

static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;
static std::thread g_gen_thread;
static std::atomic<bool> g_stop_flag{false};
static JavaVM* g_jvm = nullptr;
static jobject g_callback = nullptr;

extern "C" {
JNIEXPORT jint JNICALL Java_com_nova_ai_NativeBridge_loadModel(JNIEnv* env, jobject thiz, jstring modelPath, jint threads, jint ctxSize) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    llama_backend_init();
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    g_model = llama_load_model_from_file(path, mp);
    env->ReleaseStringUTFChars(modelPath, path);
    if (!g_model) return -1;
    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = ctxSize; cp.n_threads = threads; cp.n_threads_batch = threads;
    g_ctx = llama_new_context_with_model(g_model, cp);
    if (!g_ctx) { llama_free_model(g_model); g_model = nullptr; return -2; }
    env->GetJavaVM(&g_jvm);
    return 0;
}

JNIEXPORT void JNICALL Java_com_nova_ai_NativeBridge_generateStream(JNIEnv* env, jobject thiz, jstring prompt, jfloat temperature, jint maxTokens, jobject callback) {
    const char* p = env->GetStringUTFChars(prompt, nullptr);
    std::string prompt_std(p);
    env->ReleaseStringUTFChars(prompt, p);
    g_callback = env->NewGlobalRef(callback);
    g_stop_flag = false;

    g_gen_thread = std::thread([prompt_std, maxTokens]() {
        JNIEnv* env;
        g_jvm->AttachCurrentThread(&env, nullptr);
        jclass cls = env->GetObjectClass(g_callback);
        jmethodID mid = env->GetMethodID(cls, "invoke", "(Ljava/lang/Object;)Ljava/lang/Object;");

        int need = llama_tokenize(g_model, prompt_std.c_str(), (int)prompt_std.length(), nullptr, 0, true, true);
        if (need < 0) need = -need;
        std::vector<llama_token> tokens(need);
        int n_tokens = llama_tokenize(g_model, prompt_std.c_str(), (int)prompt_std.length(), tokens.data(), (int)tokens.size(), true, true);
        if (n_tokens <= 0) {
            jstring err = env->NewStringUTF("Tokenization failed");
            env->CallObjectMethod(g_callback, mid, err);
            env->DeleteGlobalRef(g_callback); g_callback = nullptr;
            g_jvm->DetachCurrentThread(); return;
        }

        int n_past = 0;
        const int B = 512;
        for (int i = 0; i < n_tokens; i += B) {
            int cur = std::min(B, n_tokens - i);
            llama_batch batch = llama_batch_init(cur, 0, 1);
            for (int j = 0; j < cur; j++) {
                batch.token[j] = tokens[i + j];
                batch.pos[j] = n_past + j;
                batch.n_seq_id[j] = 1;
                batch.seq_id[j][0] = 0;
                batch.logits[j] = (i + j == n_tokens - 1);
            }
            batch.n_tokens = cur;
            if (llama_decode(g_ctx, batch) != 0) { llama_batch_free(batch); break; }
            llama_batch_free(batch);
            n_past += cur;
        }

        const int n_vocab = llama_n_vocab(g_model);
        const llama_token eos = llama_token_eos(g_model);

        for (int i = 0; i < maxTokens && !g_stop_flag; i++) {
            const float* logits = llama_get_logits(g_ctx);
            if (!logits) break;
            int best = 0; float bestv = logits[0];
            for (int v = 1; v < n_vocab; v++) { if (logits[v] > bestv) { bestv = logits[v]; best = v; } }
            llama_token new_token = (llama_token)best;
            if (new_token == eos) break;

            char buf[64];
            int len = llama_token_to_piece(g_model, new_token, buf, sizeof(buf) - 1, 0, true);
            if (len < 0) len = 0;
            buf[len] = '\0';
            jstring jtoken = env->NewStringUTF(buf);
            env->CallObjectMethod(g_callback, mid, jtoken);
            env->DeleteLocalRef(jtoken);

            llama_batch batch = llama_batch_init(1, 0, 1);
            batch.token[0] = new_token;
            batch.pos[0] = n_past;
            batch.n_seq_id[0] = 1;
            batch.seq_id[0][0] = 0;
            batch.logits[0] = true;
            batch.n_tokens = 1;
            if (llama_decode(g_ctx, batch) != 0) { llama_batch_free(batch); break; }
            llama_batch_free(batch);
            n_past++;
        }

        env->DeleteGlobalRef(g_callback);
        g_callback = nullptr;
        g_jvm->DetachCurrentThread();
    });
    g_gen_thread.detach();
}

JNIEXPORT void JNICALL Java_com_nova_ai_NativeBridge_stopGeneration(JNIEnv* env, jobject thiz) { g_stop_flag = true; }
JNIEXPORT void JNICALL Java_com_nova_ai_NativeBridge_unloadModel(JNIEnv* env, jobject thiz) {
    g_stop_flag = true;
    if (g_gen_thread.joinable()) g_gen_thread.join();
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_free_model(g_model); g_model = nullptr; }
    llama_backend_free();
}
}
CPPEOF
echo "✅ Step 1 done: native.cpp updated"
cat > app/src/main/java/com/nova/ai/ui/ChatScreen.kt << 'KTEOF'
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
    var maxTokens by remember { mutableIntStateOf(512) }
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

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F13))) {
        Column(modifier = modifier.fillMaxSize()) {
            if (status.isNotEmpty()) {
                Text(status, style = MaterialTheme.typography.bodySmall, color = Color(0xFF8B5CF6), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(messages) { msg -> MessageBubble(message = msg) }
                if (isGenerating && currentResponse.isNotEmpty()) {
                    item { MessageBubble(message = ChatMessage(content = currentResponse, isUser = false), isStreaming = true) }
                }
            }
            Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF1A1A20), shadowElevation = 8.dp) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = inputText, onValueChange = { inputText = it }, modifier = Modifier.weight(1f),
                        placeholder = { Text("Message Nova AI...", color = Color.Gray) }, enabled = !isGenerating,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF8B5CF6), unfocusedBorderColor = Color(0xFF333340), focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (inputText.isNotBlank() && !isGenerating) {
                                scope.launch {
                                    if (!llmService.isModelLoaded()) {
                                        if (modelPath.isBlank()) { status = "Please import a model in Settings"; return@launch }
                                        status = "Loading model... (10-30s)"
                                        val loaded = withContext(Dispatchers.IO) { llmService.loadModel(modelPath, threads, contextLength) }
                                        if (!loaded) { status = "Failed to load model."; return@launch }
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
                    ) { Text(if (isGenerating) "..." else "Send", color = Color.White) }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage, isStreaming: Boolean = false) {
    val isUser = message.isUser
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (isUser) Color(0xFF8B5CF6) else Color(0xFF1E1E28),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (isUser) 16.dp else 4.dp, bottomEnd = if (isUser) 4.dp else 16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (!isUser) Text("Nova AI", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8B5CF6), modifier = Modifier.padding(bottom = 6.dp))
                if (isUser) {
                    Text(text = message.content, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                } else {
                    MarkdownText(markdown = message.content + if (isStreaming) " ▊" else "", isUser = false)
                }
            }
        }
    }
}

private fun buildPrompt(messages: List<ChatMessage>): String {
    val sb = StringBuilder()
    sb.append("<|im_start|>system\nYou are Nova AI, a helpful, harmless, and honest AI assistant. You provide clear, structured responses using markdown formatting when appropriate.\n<|im_end|>\n")
    messages.takeLast(10).forEach { msg ->
        val role = if (msg.isUser) "user" else "assistant"
        sb.append("<|im_start|>$role\n${msg.content}<|im_end|>\n")
    }
    sb.append("<|im_start|>assistant\n")
    return sb.toString()
}
