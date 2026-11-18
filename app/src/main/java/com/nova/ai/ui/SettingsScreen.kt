package com.nova.ai.ui
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                importStatus = "Copying model... please wait"
                try {
                    val dir = File(context.filesDir, "models").apply { mkdirs() }
                    val dest = File(dir, "nova_model.gguf")
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { out -> input.copyTo(out) } } ?: error("Cannot open file")
                    }
                    settingsManager.setModelPath(dest.absolutePath)
                    importStatus = "✅ Imported: ${dest.absolutePath}"
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

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("AI Model", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Import model (.gguf)") }
                if (importStatus.isNotEmpty()) Text(importStatus, style = MaterialTheme.typography.bodySmall, color = if (importStatus.startsWith("✅")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                OutlinedTextField(value = modelPath, onValueChange = { modelPath = it; scope.launch { settingsManager.setModelPath(it) } }, label = { Text("Model Path (.gguf)") }, modifier = Modifier.fillMaxWidth())
                Text("Temperature: $temperature", style = MaterialTheme.typography.bodyMedium)
                Slider(value = temperature, onValueChange = { temperature = it; scope.launch { settingsManager.setTemperature(it) } }, valueRange = 0f..2f, steps = 20)
                Text("Max Tokens: $maxTokens", style = MaterialTheme.typography.bodyMedium)
                Slider(value = maxTokens.toFloat(), onValueChange = { maxTokens = it.toInt(); scope.launch { settingsManager.setMaxTokens(it.toInt()) } }, valueRange = 256f..4096f, steps = 38)
                Text("Context Length: $contextLength", style = MaterialTheme.typography.bodyMedium)
                Slider(value = contextLength.toFloat(), onValueChange = { contextLength = it.toInt(); scope.launch { settingsManager.setContextLength(it.toInt()) } }, valueRange = 512f..4096f, steps = 35)
                Text("CPU Threads: $threads", style = MaterialTheme.typography.bodyMedium)
                Slider(value = threads.toFloat(), onValueChange = { threads = it.toInt(); scope.launch { settingsManager.setThreads(it.toInt()) } }, valueRange = 1f..8f, steps = 7)
            }
        }
    }
}
