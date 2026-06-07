package com.diez.stoiclauncher.domain.repository

import com.diez.stoiclauncher.domain.model.WidgetConfig
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val favoritePackages: Flow<Set<String>>
    suspend fun toggleAppFavorite(packageName: String)
    suspend fun isAppFavorite(packageName: String): Boolean
    
    val isWallpaperEnabled: Flow<Boolean>
    suspend fun setWallpaperEnabled(enabled: Boolean)

    val wallpaperUri: Flow<String?>
    suspend fun setWallpaperUri(uri: String?)

    // Removed Grid Settings
    
    val accentColor: Flow<Int>
    suspend fun setAccentColor(color: Int)
    
    // Widget persistence
    suspend fun saveWidgetConfig(config: WidgetConfig)
    suspend fun getAllWidgetConfigs(): List<WidgetConfig>
    suspend fun deleteWidgetConfig(widgetId: Int)
    suspend fun clearAllWidgetConfigs()
    val widgetConfigs: Flow<List<WidgetConfig>>
    
    val gestureMappingsFlow: Flow<Map<String, String>>
    suspend fun setGestureMapping(trigger: String, action: String)
    
    val appShortcutsFlow: Flow<Map<String, String>>
    suspend fun setAppShortcut(position: String, packageName: String?)
    
    // Per-App Usage Limits
    fun getAppUsageLimit(packageName: String): Flow<Int>
    suspend fun setAppUsageLimit(packageName: String, minutes: Int)
    val allAppUsageLimits: Flow<Map<String, Int>>
    
    // Global limit (Deprecated/Unused)
    val appUsageLimitMinutes: Flow<Int> 
    suspend fun setAppUsageLimitMinutes(minutes: Int)
    
    // Group View Mode
    val isGroupGridMode: Flow<Boolean>
    suspend fun setGroupGridMode(isGrid: Boolean)
    
    // Category Management (hide/rename)
    val hiddenCategories: Flow<Set<String>>
    suspend fun toggleHiddenCategory(categoryName: String)
    
    val customCategoryNames: Flow<Map<String, String>>
    suspend fun setCustomCategoryName(originalName: String, customName: String)
    suspend fun removeCustomCategoryName(originalName: String)

    val volumeBoostEnabled: Flow<Boolean>
    val volumeBoostLevel: Flow<Int>
    suspend fun setVolumeBoostEnabled(enabled: Boolean)
    suspend fun setVolumeBoostLevel(level: Int)
}
