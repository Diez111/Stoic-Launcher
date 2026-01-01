package com.diez.stoiclauncher.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.diez.stoiclauncher.domain.repository.SettingsRepository
import com.diez.stoiclauncher.domain.model.WidgetConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class SettingsRepositoryImpl(context: Context) : SettingsRepository {

    private val appPreferencesRepository = AppPreferencesRepository(context)
    private val prefs: SharedPreferences = context.getSharedPreferences("stoic_prefs", Context.MODE_PRIVATE)
    private val _favoritesFlow = MutableStateFlow<Set<String>>(getFavoritesFromPrefs())

    override val favoritePackages: Flow<Set<String>> = _favoritesFlow

    override suspend fun toggleAppFavorite(packageName: String) {
        val current = getFavoritesFromPrefs().toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
        _favoritesFlow.value = current
    }

    override suspend fun isAppFavorite(packageName: String): Boolean {
        return getFavoritesFromPrefs().contains(packageName)
    }

    private fun getFavoritesFromPrefs(): Set<String> {
        val raw = prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
        // MIGRATION FIX: Strip "|0", "|hash", etc. to ensure matches with simplified AppModel.uniqueId
        return raw.map { 
             if (it.startsWith("group:")) it 
             else it.split("|")[0] 
        }.toSet()
    }

    private val _wallpaperFlow = MutableStateFlow<Boolean>(prefs.getBoolean(KEY_WALLPAPER_ENABLED, true))
    override val isWallpaperEnabled: Flow<Boolean> = _wallpaperFlow

    override suspend fun setWallpaperEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WALLPAPER_ENABLED, enabled).apply()
        _wallpaperFlow.value = enabled
    }

    private val _accentFlow = MutableStateFlow<Int>(prefs.getInt(KEY_ACCENT, android.graphics.Color.WHITE)) // Default White
    override val accentColor: Flow<Int> = _accentFlow

    override suspend fun setAccentColor(color: Int) {
        prefs.edit().putInt(KEY_ACCENT, color).apply()
        _accentFlow.value = color
    }
    
    private val _wallpaperUriFlow = MutableStateFlow<String?>(prefs.getString(KEY_WALLPAPER_URI, null))
    override val wallpaperUri: Flow<String?> = _wallpaperUriFlow

    override suspend fun setWallpaperUri(uri: String?) {
        prefs.edit().putString(KEY_WALLPAPER_URI, uri).apply()
        _wallpaperUriFlow.value = uri
    }
    
    // Widget persistence
    private val _widgetConfigsFlow = MutableStateFlow<List<WidgetConfig>>(getWidgetConfigsFromPrefs())
    override val widgetConfigs: Flow<List<WidgetConfig>> = _widgetConfigsFlow
    
    override suspend fun saveWidgetConfig(config: WidgetConfig) {
        val configs = getWidgetConfigsFromPrefs().toMutableList()
        // Remove existing config for this widgetId
        configs.removeIf { it.widgetId == config.widgetId }
        configs.add(config)
        
        val jsonString = Json.encodeToString(configs)
        prefs.edit().putString(KEY_WIDGET_CONFIGS, jsonString).apply()
        _widgetConfigsFlow.value = configs
    }
    
    override suspend fun getAllWidgetConfigs(): List<WidgetConfig> {
        return getWidgetConfigsFromPrefs()
    }
    
    override suspend fun deleteWidgetConfig(widgetId: Int) {
        val configs = getWidgetConfigsFromPrefs().toMutableList()
        configs.removeIf { it.widgetId == widgetId }
        
        val jsonString = Json.encodeToString(configs)
        prefs.edit().putString(KEY_WIDGET_CONFIGS, jsonString).apply()
        _widgetConfigsFlow.value = configs
    }
    
    override suspend fun clearAllWidgetConfigs() {
        val empty = emptyList<WidgetConfig>()
        val jsonString = Json.encodeToString(empty)
        prefs.edit().putString(KEY_WIDGET_CONFIGS, jsonString).apply()
        _widgetConfigsFlow.value = empty
    }

    private val _gestureMappingsFlow = MutableStateFlow<Map<String, String>>(getGestureMappingsFromPrefs())
    override val gestureMappingsFlow: Flow<Map<String, String>> = _gestureMappingsFlow

    override suspend fun setGestureMapping(trigger: String, action: String) {
        val mappings = getGestureMappingsFromPrefs().toMutableMap()
        if (action == "NONE" || action.isBlank()) {
            mappings.remove(trigger)
        } else {
            mappings[trigger] = action
        }
        
        val jsonString = Json.encodeToString(mappings)
        prefs.edit().putString(KEY_GESTURE_MAPPINGS, jsonString).apply()
        _gestureMappingsFlow.value = mappings
    }

    private fun getGestureMappingsFromPrefs(): Map<String, String> {
        return try {
            val jsonString = prefs.getString(KEY_GESTURE_MAPPINGS, null)
            if (jsonString != null) {
                Json.decodeFromString<Map<String, String>>(jsonString)
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    private fun getWidgetConfigsFromPrefs(): List<WidgetConfig> {
        return try {
            val jsonString = prefs.getString(KEY_WIDGET_CONFIGS, null)
            if (jsonString != null) {
                Json.decodeFromString<List<WidgetConfig>>(jsonString)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("SettingsRepo", "Error loading widget configs", e)
            emptyList()
        }
    }

    // Gestures override removed (already implemented above)

    override val appShortcutsFlow: Flow<Map<String, String>> = appPreferencesRepository.appShortcutsFlow

    override suspend fun setAppShortcut(position: String, packageName: String?) {
        appPreferencesRepository.setAppShortcut(position, packageName)
    }

    // Per-App Limits Logic
    private val _allLimitsFlow = MutableStateFlow<Map<String, Int>>(getAllLimitsFromPrefs())
    override val allAppUsageLimits: Flow<Map<String, Int>> = _allLimitsFlow
    
    override fun getAppUsageLimit(packageName: String): Flow<Int> {
        return _allLimitsFlow.map { limits -> limits[packageName] ?: 0 }
    }
    
    override suspend fun setAppUsageLimit(packageName: String, minutes: Int) {
        val current = _allLimitsFlow.value.toMutableMap()
        if (minutes <= 0) {
            current.remove(packageName)
        } else {
            current[packageName] = minutes
        }
        
        val jsonString = Json.encodeToString(current)
        prefs.edit().putString(KEY_ALL_USAGE_LIMITS, jsonString).apply()
        _allLimitsFlow.value = current
    }

    private fun getAllLimitsFromPrefs(): Map<String, Int> {
        return try {
            val jsonString = prefs.getString(KEY_ALL_USAGE_LIMITS, null)
            if (jsonString != null) {
                Json.decodeFromString<Map<String, Int>>(jsonString)
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // Deprecated Global
    private val _appUsageLimitFlow = MutableStateFlow<Int>(prefs.getInt(KEY_USAGE_LIMIT, 0))
    override val appUsageLimitMinutes: Flow<Int> = _appUsageLimitFlow
    
    override suspend fun setAppUsageLimitMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_USAGE_LIMIT, minutes).apply()
        _appUsageLimitFlow.value = minutes
    }

    private val _groupGridModeFlow = MutableStateFlow<Boolean>(prefs.getBoolean(KEY_GROUP_GRID_MODE, true))
    override val isGroupGridMode: Flow<Boolean> = _groupGridModeFlow
    
    override suspend fun setGroupGridMode(isGrid: Boolean) {
        prefs.edit().putBoolean(KEY_GROUP_GRID_MODE, isGrid).apply()
        _groupGridModeFlow.value = isGrid
    }

    companion object {
        private const val KEY_FAVORITES = "favorite_packages"
        private const val KEY_WALLPAPER_ENABLED = "wallpaper_enabled"
        private const val KEY_GRID_ENABLED = "grid_enabled"
        private const val KEY_ACCENT = "accent_color"
        private const val KEY_WIDGET_CONFIGS = "widget_configs"
        private const val KEY_WALLPAPER_URI = "wallpaper_uri"
        private const val KEY_GESTURE_MAPPINGS = "gesture_mappings"
        private const val KEY_USAGE_LIMIT = "app_usage_limit_minutes"
        private const val KEY_ALL_USAGE_LIMITS = "all_app_usage_limits"
        private const val KEY_GROUP_GRID_MODE = "group_grid_mode"
    }
}
