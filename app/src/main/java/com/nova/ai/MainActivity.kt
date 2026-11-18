package com.nova.ai
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.nova.ai.ui.ChatScreen
import com.nova.ai.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Force Dark Theme like Qwen
            val darkColorScheme = darkColorScheme(
                primary = Color(0xFF8B5CF6),
                background = Color(0xFF0F0F13),
                surface = Color(0xFF1A1A20),
                surfaceVariant = Color(0xFF1E1E28)
            )
            MaterialTheme(colorScheme = darkColorScheme) {
                Surface(modifier = Modifier.fillMaxSize()) { NovaApp() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovaApp() {
    var showSettings by remember { mutableStateOf(false) }
    Scaffold(topBar = { 
        TopAppBar(
            title = { Text("Nova AI", color = Color.White) }, 
            actions = { 
                IconButton(onClick = { showSettings = !showSettings }) { 
                    Icon(androidx.compose.material.icons.Icons.Default.Settings, "Settings", tint = Color.White) 
                } 
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F0F13))
        ) 
    }) { padding ->
        if (showSettings) SettingsScreen(modifier = Modifier.padding(padding), onBack = { showSettings = false }) else ChatScreen(modifier = Modifier.padding(padding))
    }
}
