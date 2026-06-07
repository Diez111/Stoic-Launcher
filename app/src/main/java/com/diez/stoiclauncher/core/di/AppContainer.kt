package com.diez.stoiclauncher.core.di

import android.content.Context
import com.diez.stoiclauncher.data.repository.AppPreferencesRepository
import com.diez.stoiclauncher.data.repository.AppRepositoryImpl
import com.diez.stoiclauncher.data.repository.SettingsRepositoryImpl
import com.diez.stoiclauncher.domain.repository.AppRepository
import com.diez.stoiclauncher.domain.repository.SettingsRepository
import com.diez.stoiclauncher.domain.usage.AppUsageManager
import com.diez.stoiclauncher.domain.usecase.FilterAppsUseCase
import com.diez.stoiclauncher.domain.usecase.GetInstalledAppsUseCase
import com.diez.stoiclauncher.domain.usecase.HideAppUseCase
import com.diez.stoiclauncher.domain.usecase.ManageAppGroupsUseCase
import com.diez.stoiclauncher.domain.usecase.RefreshAppsUseCase
import com.diez.stoiclauncher.domain.usecase.RenameAppUseCase
import com.diez.stoiclauncher.domain.usecase.ToggleAppFavoriteUseCase
import com.diez.stoiclauncher.domain.util.FlashlightManager
import com.diez.stoiclauncher.domain.util.IconPackManager

class AppContainer(private val context: Context) {

    val appPreferencesRepository by lazy {
        AppPreferencesRepository(context)
    }

    val iconPackManager by lazy {
        IconPackManager(context)
    }

    val appRepository: AppRepository by lazy {
        AppRepositoryImpl(context, appPreferencesRepository, iconPackManager)
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepositoryImpl(context, appPreferencesRepository)
    }

    val getInstalledAppsUseCase by lazy {
        GetInstalledAppsUseCase(appRepository)
    }

    val toggleAppFavoriteUseCase by lazy {
        ToggleAppFavoriteUseCase(settingsRepository)
    }

    val manageAppGroupsUseCase by lazy {
        ManageAppGroupsUseCase(appRepository)
    }

    val filterAppsUseCase by lazy {
        FilterAppsUseCase(appRepository)
    }

    val refreshAppsUseCase by lazy {
        RefreshAppsUseCase(appRepository)
    }

    val hideAppUseCase by lazy {
        HideAppUseCase(appRepository)
    }

    val renameAppUseCase by lazy {
        RenameAppUseCase(appRepository)
    }

    val appUsageManager by lazy {
        AppUsageManager(context, settingsRepository)
    }

    val flashlightManager by lazy {
        FlashlightManager(context)
    }
}
