package com.nova.ai.service
import android.content.Context
import com.nova.ai.NativeBridge
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File

class LLMService(private val context: Context) {
    private var modelLoaded = false
    suspend fun loadModel(modelPath: String, threads: Int, contextLength: Int): Boolean {
        return try {
            val file = File(modelPath)
            if (!file.exists() || !file.canRead()) return false
            val nCtx = (contextLength + 512).coerceAtMost(4096)
            modelLoaded = (NativeBridge.loadModel(modelPath, threads, nCtx) == 0)
            modelLoaded
        } catch (e: Exception) { false }
    }
    fun generateResponse(prompt: String, temperature: Float, maxTokens: Int): Flow<String> = callbackFlow {
        if (!modelLoaded) { trySend("⚠️ No model loaded. Use Settings → Import model."); close(); return@callbackFlow }
        try {
            NativeBridge.generateStream(prompt, temperature, maxTokens) { token -> trySend(token) }
            awaitClose { NativeBridge.stopGeneration() }
        } catch (e: Exception) { trySend("❌ Generation error: ${e.message}"); close() }
    }
    fun stopGeneration() { NativeBridge.stopGeneration() }
    fun isModelLoaded(): Boolean = modelLoaded
}
