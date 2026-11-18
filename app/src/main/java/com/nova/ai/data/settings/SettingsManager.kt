package com.nova.ai.data.settings
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nova_settings")
object SettingsKeys {
    val MODEL_PATH = stringPreferencesKey("model_path")
    val TEMPERATURE = floatPreferencesKey("temperature")
    val MAX_TOKENS = intPreferencesKey("max_tokens")
    val CONTEXT_LENGTH = intPreferencesKey("context_length")
    val THREADS = intPreferencesKey("threads")
}
class SettingsManager(private val context: Context) {
    private val dataStore = context.dataStore
    val modelPath: Flow<String> = dataStore.data.map { it[SettingsKeys.MODEL_PATH] ?: "" }
    val temperature: Flow<Float> = dataStore.data.map { it[SettingsKeys.TEMPERATURE] ?: 0.7f }
    val maxTokens: Flow<Int> = dataStore.data.map { it[SettingsKeys.MAX_TOKENS] ?: 2048 }
    val contextLength: Flow<Int> = dataStore.data.map { it[SettingsKeys.CONTEXT_LENGTH] ?: 2048 }
    val threads: Flow<Int> = dataStore.data.map { it[SettingsKeys.THREADS] ?: 4 }
    suspend fun setModelPath(path: String) { dataStore.edit { it[SettingsKeys.MODEL_PATH] = path } }
    suspend fun setTemperature(temp: Float) { dataStore.edit { it[SettingsKeys.TEMPERATURE] = temp } }
    suspend fun setMaxTokens(tokens: Int) { dataStore.edit { it[SettingsKeys.MAX_TOKENS] = tokens } }
    suspend fun setContextLength(length: Int) { dataStore.edit { it[SettingsKeys.CONTEXT_LENGTH] = length } }
    suspend fun setThreads(t: Int) { dataStore.edit { it[SettingsKeys.THREADS] = t } }
}
