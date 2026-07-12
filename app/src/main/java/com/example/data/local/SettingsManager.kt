package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "verse_settings")

class SettingsManager(private val context: Context) {

    companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode") // "system", "light", "dark"
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val DEFAULT_LYRICS_TYPE = stringPreferencesKey("default_lyrics_type") // "synced", "plain"
        val FALLBACK_BROADER_SEARCH = booleanPreferencesKey("fallback_broader_search")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val NOTIFY_FAILURE = booleanPreferencesKey("notify_failure")
        val MARK_INSTRUMENTAL = booleanPreferencesKey("mark_instrumental")
        val STORAGE_DESTINATION = stringPreferencesKey("storage_destination") // "cache", "lrc", "embed"
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val MATCHING_THRESHOLD = intPreferencesKey("matching_threshold")
        val ACTIVE_PRESET = stringPreferencesKey("active_preset") // theme preset name
        val IMMERSIVE_FONT_FAMILY = stringPreferencesKey("immersive_font_family") // "default", "serif", "monospace", "cursive"
        val IMMERSIVE_FONT_SIZE = floatPreferencesKey("immersive_font_size") // in sp, default 24f
        val IMMERSIVE_ALIGNMENT = stringPreferencesKey("immersive_alignment") // "left", "center"
        val IMMERSIVE_TEXT_SHADOW = booleanPreferencesKey("immersive_text_shadow")
        val USER_PRESETS = stringPreferencesKey("user_presets")
        val FLOATING_LYRICS_ENABLED = booleanPreferencesKey("floating_lyrics_enabled")
    }

    val themeModeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE] ?: "system"
    }

    val dynamicColorFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DYNAMIC_COLOR] ?: true
    }

    val defaultLyricsTypeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DEFAULT_LYRICS_TYPE] ?: "synced"
    }

    val fallbackBroaderSearchFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[FALLBACK_BROADER_SEARCH] ?: true
    }

    val wifiOnlyFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[WIFI_ONLY] ?: false
    }

    val notifyFailureFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[NOTIFY_FAILURE] ?: false
    }

    val markInstrumentalFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[MARK_INSTRUMENTAL] ?: true
    }

    val storageDestinationFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[STORAGE_DESTINATION] ?: "cache"
    }

    val geminiApiKeyFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[GEMINI_API_KEY] ?: ""
    }

    val matchingThresholdFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[MATCHING_THRESHOLD] ?: 70
    }

    val activePresetFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[ACTIVE_PRESET] ?: "Album Art Glow"
    }

    val immersiveFontFamilyFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[IMMERSIVE_FONT_FAMILY] ?: "default"
    }

    val immersiveFontSizeFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[IMMERSIVE_FONT_SIZE] ?: 24f
    }

    val immersiveAlignmentFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[IMMERSIVE_ALIGNMENT] ?: "center"
    }

    val immersiveTextShadowFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IMMERSIVE_TEXT_SHADOW] ?: true
    }

    val userPresetsFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[USER_PRESETS] ?: ""
    }

    val floatingLyricsEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[FLOATING_LYRICS_ENABLED] ?: false
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DYNAMIC_COLOR] = enabled
        }
    }

    suspend fun setDefaultLyricsType(type: String) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_LYRICS_TYPE] = type
        }
    }

    suspend fun setFallbackBroaderSearch(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[FALLBACK_BROADER_SEARCH] = enabled
        }
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[WIFI_ONLY] = enabled
        }
    }

    suspend fun setNotifyFailure(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[NOTIFY_FAILURE] = enabled
        }
    }

    suspend fun setMarkInstrumental(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[MARK_INSTRUMENTAL] = enabled
        }
    }

    suspend fun setStorageDestination(dest: String) {
        context.dataStore.edit { preferences ->
            preferences[STORAGE_DESTINATION] = dest
        }
    }

    suspend fun setGeminiApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[GEMINI_API_KEY] = key
        }
    }

    suspend fun setMatchingThreshold(threshold: Int) {
        context.dataStore.edit { preferences ->
            preferences[MATCHING_THRESHOLD] = threshold
        }
    }

    suspend fun setActivePreset(preset: String) {
        context.dataStore.edit { preferences ->
            preferences[ACTIVE_PRESET] = preset
        }
    }

    suspend fun setImmersiveFontFamily(family: String) {
        context.dataStore.edit { preferences ->
            preferences[IMMERSIVE_FONT_FAMILY] = family
        }
    }

    suspend fun setImmersiveFontSize(size: Float) {
        context.dataStore.edit { preferences ->
            preferences[IMMERSIVE_FONT_SIZE] = size
        }
    }

    suspend fun setImmersiveAlignment(alignment: String) {
        context.dataStore.edit { preferences ->
            preferences[IMMERSIVE_ALIGNMENT] = alignment
        }
    }

    suspend fun setImmersiveTextShadow(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IMMERSIVE_TEXT_SHADOW] = enabled
        }
    }

    suspend fun setUserPresets(presets: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_PRESETS] = presets
        }
    }

    suspend fun setFloatingLyricsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[FLOATING_LYRICS_ENABLED] = enabled
        }
    }
}
