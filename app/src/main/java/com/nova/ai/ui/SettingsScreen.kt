package com.nova.ai.ui
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nova.ai.data.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val scope = rememberCoroutineScope()
    var modelPath by remember { mutableStateOf("") }
    var temperature by remember { mutableFloatStateOf(0.7f) }
    var maxTokens by remember { mutableIntStateOf(2048) }
    var contextLength by remember { mutableIntStateOf(2048) }
    var threads by remember { mutableIntStateOf(4) }
    var importStatus by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                importStatus = "Copying model... (This may take a minute)"
                try {
                    val dir = File(context.filesDir, "models").apply { mkdirs() }
                    val dest = File(dir, "nova_model.gguf")
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { out -> input.copyTo(out) } } ?: error("Cannot open file")
                    }
                    settingsManager.setModelPath(dest.absolutePath)
                    importStatus = "✅ Model imported successfully!"
                } catch (e: Exception) { importStatus = "❌ Import failed: ${e.message}" }
            }
        }
    }

    LaunchedEffect(Unit) {
        launch { settingsManager.modelPath.collect { modelPath = it } }
        launch { settingsManager.temperature.collect { temperature = it } }
        launch { settingsManager.maxTokens.collect { maxTokens = it } }
        launch { settingsManager.contextLength.collect { contextLength = it } }
        launch { settingsManager.threads.collect { threads = it } }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F13))) {
        Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                Text("Settings", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            }
            
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E28))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("AI Model", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Button(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))) { 
                        Text("Import model (.gguf)", color = Color.White) 
                    }
                    if (importStatus.isNotEmpty()) Text(importStatus, style = MaterialTheme.typography.bodySmall, color = if (importStatus.startsWith("✅")) Color(0xFF10B981) else Color(0xFFEF4444))
                    OutlinedTextField(value = modelPath, onValueChange = { modelPath = it; scope.launch { settingsManager.setModelPath(it) } }, label = { Text("Model Path") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF8B5CF6), unfocusedBorderColor = Color(0xFF333340), focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                    
                    Text("Temperature: $temperature", color = Color.White)
                    Slider(value = temperature, onValueChange = { temperature = it; scope.launch { settingsManager.setTemperature(it) } }, valueRange = 0f..2f, steps = 20, colors = SliderDefaults.colors(thumbColor = Color(0xFF8B5CF6), activeTrackColor = Color(0xFF8B5CF6)))
                    
                    Text("Max Tokens: $maxTokens", color = Color.White)
                    Slider(value = maxTokens.toFloat(), onValueChange = { maxTokens = it.toInt(); scope.launch { settingsManager.setMaxTokens(it.toInt()) } }, valueRange = 256f..4096f, steps = 38, colors = SliderDefaults.colors(thumbColor = Color(0xFF8B5CF6), activeTrackColor = Color(0xFF8B5CF6)))
                    
                    Text("Context Length: $contextLength", color = Color.White)
                    Slider(value = contextLength.toFloat(), onValueChange = { contextLength = it.toInt(); scope.launch { settingsManager.setContextLength(it.toInt()) } }, valueRange = 512f..4096f, steps = 35, colors = SliderDefaults.colors(thumbColor = Color(0xFF8B5CF6), activeTrackColor = Color(0xFF8B5CF6)))
                    
                    Text("CPU Threads: $threads", color = Color.White)
                    Slider(value = threads.toFloat(), onValueChange = { threads = it.toInt(); scope.launch { settingsManager.setThreads(it.toInt()) } }, valueRange = 1f..8f, steps = 7, colors = SliderDefaults.colors(thumbColor = Color(0xFF8B5CF6), activeTrackColor = Color(0xFF8B5CF6)))
                }
            }
        }
    }
}
