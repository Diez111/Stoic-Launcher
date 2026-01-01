package com.diez.stoiclauncher.core.di

import android.content.Context
import com.diez.stoiclauncher.data.repository.AppRepositoryImpl
import com.diez.stoiclauncher.data.repository.AppPreferencesRepository
import com.diez.stoiclauncher.domain.repository.AppRepository
import com.diez.stoiclauncher.data.repository.SettingsRepositoryImpl
import com.diez.stoiclauncher.domain.repository.SettingsRepository

class AppContainer(private val context: Context) {

    val appRepository: AppRepository by lazy {
        AppRepositoryImpl(context, AppPreferencesRepository(context))
    }
    
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepositoryImpl(context)
    }

    val getInstalledAppsUseCase: com.diez.stoiclauncher.domain.usecase.GetInstalledAppsUseCase by lazy {
        com.diez.stoiclauncher.domain.usecase.GetInstalledAppsUseCase(appRepository)
    }

    val toggleAppFavoriteUseCase: com.diez.stoiclauncher.domain.usecase.ToggleAppFavoriteUseCase by lazy {
        com.diez.stoiclauncher.domain.usecase.ToggleAppFavoriteUseCase(settingsRepository)
    }

    val manageAppGroupsUseCase: com.diez.stoiclauncher.domain.usecase.ManageAppGroupsUseCase by lazy {
        com.diez.stoiclauncher.domain.usecase.ManageAppGroupsUseCase(appRepository)
    }

    val filterAppsUseCase: com.diez.stoiclauncher.domain.usecase.FilterAppsUseCase by lazy {
        com.diez.stoiclauncher.domain.usecase.FilterAppsUseCase(appRepository)
    }

    val refreshAppsUseCase: com.diez.stoiclauncher.domain.usecase.RefreshAppsUseCase by lazy {
        com.diez.stoiclauncher.domain.usecase.RefreshAppsUseCase(appRepository)
    }

    val hideAppUseCase: com.diez.stoiclauncher.domain.usecase.HideAppUseCase by lazy {
        com.diez.stoiclauncher.domain.usecase.HideAppUseCase(appRepository)
    }

    val renameAppUseCase: com.diez.stoiclauncher.domain.usecase.RenameAppUseCase by lazy {
        com.diez.stoiclauncher.domain.usecase.RenameAppUseCase(appRepository)
    }
    
    val appUsageManager: com.diez.stoiclauncher.domain.usage.AppUsageManager by lazy {
        com.diez.stoiclauncher.domain.usage.AppUsageManager(context, settingsRepository)
    }
}
