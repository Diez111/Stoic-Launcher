package com.diez.stoiclauncher.presentation.home

import android.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diez.stoiclauncher.domain.model.AppModel
import com.diez.stoiclauncher.domain.repository.SettingsRepository
import com.diez.stoiclauncher.domain.usecase.FilterAppsUseCase
import com.diez.stoiclauncher.domain.usecase.GetInstalledAppsUseCase
import com.diez.stoiclauncher.domain.usecase.HideAppUseCase
import com.diez.stoiclauncher.domain.usecase.ManageAppGroupsUseCase
import com.diez.stoiclauncher.domain.usecase.RefreshAppsUseCase
import com.diez.stoiclauncher.domain.usecase.RenameAppUseCase
import com.diez.stoiclauncher.domain.usecase.ToggleAppFavoriteUseCase
import com.diez.stoiclauncher.presentation.util.ColorHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    private val refreshAppsUseCase: RefreshAppsUseCase,
    private val filterAppsUseCase: FilterAppsUseCase,
    private val toggleAppFavoriteUseCase: ToggleAppFavoriteUseCase,
    private val manageAppGroupsUseCase: ManageAppGroupsUseCase,
    private val hideAppUseCase: HideAppUseCase,
    private val renameAppUseCase: RenameAppUseCase,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<List<AppModel>>(emptyList())
    val uiState: StateFlow<List<AppModel>> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val allAppsState: StateFlow<List<AppModel>> = combine(_uiState, _searchQuery) { rawApps, query ->
        if (query.isNotEmpty()) {
            filterAppsUseCase(rawApps, query)
        } else {
            rawApps.sortedBy { it.label.lowercase() }
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), emptyList())

    val accentColor = settingsRepository.accentColor
        .stateIn(viewModelScope, SharingStarted.Eagerly, Color.BLACK)

    val isWallpaperEnabled = settingsRepository.isWallpaperEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val wallpaperUri = settingsRepository.wallpaperUri
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _manualIsLight = MutableStateFlow<Boolean?>(null)

    val isLightBackground: StateFlow<Boolean> = combine(
        settingsRepository.accentColor,
        settingsRepository.isWallpaperEnabled,
        _manualIsLight
    ) { color, wallpaperEnabled, manual ->
        if (!wallpaperEnabled) {
            val textColor = ColorHelper.getTextColorForAccent(color)
            textColor == Color.BLACK
        } else {
            manual ?: false
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, false)

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
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), emptyList())

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

    suspend fun getAppsForPicker(): List<AppModel> {
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

    private val _userEvents = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val userEvents: SharedFlow<String> = _userEvents.asSharedFlow()

    fun setAppGroup(app: AppModel, groupId: String?) {
        val currentApp = _uiState.value.find { it.uniqueId == app.uniqueId } ?: app
        if (groupId != null && currentApp.groupId == groupId) {
            viewModelScope.launch {
                _userEvents.emit("La aplicación ya está en este grupo.")
            }
            return
        }
        viewModelScope.launch {
            manageAppGroupsUseCase.setAppGroup(app.uniqueId, groupId)
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), emptyMap())

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
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    fun setGroupGridMode(isGrid: Boolean) {
        viewModelScope.launch {
            settingsRepository.setGroupGridMode(isGrid)
        }
    }
}
