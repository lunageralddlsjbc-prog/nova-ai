package com.nova.ai
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.nova.ai.ui.ChatScreen
import com.nova.ai.ui.SettingsScreen
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) { NovaApp() }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovaApp() {
    var showSettings by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("Nova AI") }, actions = { IconButton(onClick = { showSettings = !showSettings }) { Icon(Icons.Default.Settings, "Settings") } }) }) { padding ->
        if (showSettings) SettingsScreen(modifier = Modifier.padding(padding), onBack = { showSettings = false }) else ChatScreen(modifier = Modifier.padding(padding))
    }
}
