package com.diez.stoiclauncher.domain.usecase

import com.diez.stoiclauncher.domain.repository.SettingsRepository

class ToggleAppFavoriteUseCase(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(appId: String) {
        settingsRepository.toggleAppFavorite(appId)
    }
}
