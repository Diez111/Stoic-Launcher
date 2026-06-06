package com.diez.stoiclauncher.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diez.stoiclauncher.domain.model.AppModel
import com.diez.stoiclauncher.domain.repository.AppRepository
import com.diez.stoiclauncher.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class HomeViewModel(
    private val getInstalledAppsUseCase: com.diez.stoiclauncher.domain.usecase.GetInstalledAppsUseCase,
    private val refreshAppsUseCase: com.diez.stoiclauncher.domain.usecase.RefreshAppsUseCase,
    private val filterAppsUseCase: com.diez.stoiclauncher.domain.usecase.FilterAppsUseCase,
    private val toggleAppFavoriteUseCase: com.diez.stoiclauncher.domain.usecase.ToggleAppFavoriteUseCase,
    private val manageAppGroupsUseCase: com.diez.stoiclauncher.domain.usecase.ManageAppGroupsUseCase,
    private val hideAppUseCase: com.diez.stoiclauncher.domain.usecase.HideAppUseCase,
    private val renameAppUseCase: com.diez.stoiclauncher.domain.usecase.RenameAppUseCase,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<List<AppModel>>(emptyList())
    val uiState: StateFlow<List<AppModel>> = _uiState.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    // Derived state for Search/All Apps (Drawer)
    private val groupedAppsState: StateFlow<List<AppModel>> = _uiState.map { apps ->
         // Grouping Logic
         val groups = apps.mapNotNull { it.groupId }.distinct()
         val groupItems = groups.map { groupId ->
              AppModel(label = "[ $groupId ]", packageName = "group:$groupId", icon = null, isGroup = true, groupId = groupId)
         }
         val nonGroupedApps = apps.filter { it.groupId == null }
         
         (groupItems + nonGroupedApps).sortedBy { it.label.lowercase() }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(1000), emptyList())

    val allAppsState: StateFlow<List<AppModel>> = combine(_uiState, _searchQuery) { rawApps, query ->
        if (query.isNotEmpty()) {
             filterAppsUseCase(rawApps, query)
        } else {
             rawApps.sortedBy { it.label.lowercase() }
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(1000), emptyList())

    val accentColor = settingsRepository.accentColor
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, android.graphics.Color.BLACK)
        
    val isWallpaperEnabled = settingsRepository.isWallpaperEnabled
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    val wallpaperUri = settingsRepository.wallpaperUri
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)

    private val _manualIsLight = MutableStateFlow<Boolean?>(null)
    
    val isLightBackground: StateFlow<Boolean> = kotlinx.coroutines.flow.combine(
        settingsRepository.accentColor,
        settingsRepository.isWallpaperEnabled,
        _manualIsLight
    ) { color, wallpaperEnabled, manual ->
        if (!wallpaperEnabled) {
            val textColor = com.diez.stoiclauncher.presentation.util.ColorHelper.getTextColorForAccent(color)
            textColor == android.graphics.Color.BLACK
        } else {
            manual ?: false
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    fun setLightBackground(isLight: Boolean) {
        _manualIsLight.value = isLight
    }

    val favoritesState: StateFlow<List<AppModel>> = combine(_uiState, settingsRepository.favoritePackages) { allApps, favorites ->
        val groups = allApps.mapNotNull { it.groupId }.distinct().sorted()
        val groupItems = groups.map { groupId ->
             AppModel(label = "[ $groupId ]", packageName = "group:$groupId", icon = null, isGroup = true, groupId = groupId)
        }
        val favoriteApps = allApps.filter { favorites.contains(it.uniqueId) }
        
        (groupItems + favoriteApps).distinctBy { it.uniqueId }.sortedBy { 
             if (it.isGroup) "0${it.label.lowercase()}" else "1${it.label.lowercase()}"
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(1000), emptyList())

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshAppsUseCase()
        }
        viewModelScope.launch {
            getInstalledAppsUseCase(includeHidden = false).collect { apps ->
                 _uiState.value = apps
            }
        }
    }
    
    /**
     * Retorna una lista con TODAS las apps (incluyendo ocultas) para el selector.
     * Soluciona el problema de apps faltantes.
     */
    suspend fun getAppsForPicker(): List<AppModel> {
        // Direct synchronous fetch to avoid Flow/Filtering issues
        return getInstalledAppsUseCase.invokeSync()
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }
    
    fun toggleAppFavorite(app: AppModel) {
        viewModelScope.launch {
            toggleAppFavoriteUseCase(app.uniqueId)
        }
    }
    
    fun hideApp(app: AppModel) {
        viewModelScope.launch {
            hideAppUseCase(app.uniqueId, true)
        }
    }
    
    fun renameApp(app: AppModel, newName: String) {
        viewModelScope.launch {
             renameAppUseCase(app.uniqueId, newName)
        }
    }
    
    // One-shot events for UI (Toasts, Navigation)
    // replay=0: no replay for new collectors (events are truly one-shot)
    // extraBufferCapacity=1: buffer 1 event if no collectors yet
    // onBufferOverflow=DROP_OLDEST: drop old events if buffer full
    private val _userEvents = kotlinx.coroutines.flow.MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val userEvents = _userEvents.asSharedFlow()

    fun setAppGroup(app: AppModel, groupId: String?) {
        // Prevent adding to same group (Duplicate Logic Guard)
        // Check current state to ensure Freshness
        val currentApp = _uiState.value.find { it.uniqueId == app.uniqueId } ?: app
        
        if (groupId != null && currentApp.groupId == groupId) {
            viewModelScope.launch {
                _userEvents.emit("La aplicación ya está en este grupo.")
            }
            return
        }
        
        viewModelScope.launch {
            manageAppGroupsUseCase.setAppGroup(app.uniqueId, groupId)
            if (groupId != null) {
                // Optional: Confirm success? 
                // _userEvents.emit("Añadida a $groupId")
            }
        }
    }
    
    
    fun renameGroup(oldGroupId: String, newGroupId: String) {
        viewModelScope.launch {
            manageAppGroupsUseCase.renameGroup(oldGroupId, newGroupId)
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            manageAppGroupsUseCase.deleteGroup(groupId)
        }
    }
    
    fun getAppsInGroup(groupId: String): List<AppModel> {
        return _uiState.value.filter { it.groupId == groupId }.sortedBy { it.label.lowercase() }
    }

    val shortcutsState: StateFlow<Map<String, String>> = settingsRepository.appShortcutsFlow
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(1000), emptyMap())

    fun setAppShortcut(position: String, packageName: String?) {
        viewModelScope.launch {
            settingsRepository.setAppShortcut(position, packageName)
        }
    }
    
    fun setAppUsageLimit(app: AppModel, minutes: Int) {
        viewModelScope.launch {
            settingsRepository.setAppUsageLimit(app.packageName, minutes)
        }
    }
    
    val isGroupGridMode = settingsRepository.isGroupGridMode
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, true)
        
    fun setGroupGridMode(isGrid: Boolean) {
        viewModelScope.launch {
            settingsRepository.setGroupGridMode(isGrid)
        }
    }
}
