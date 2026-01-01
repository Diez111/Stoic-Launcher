package com.diez.stoiclauncher.domain.usecase

import com.diez.stoiclauncher.domain.repository.AppRepository

class HideAppUseCase(
    private val appRepository: AppRepository
) {
    suspend operator fun invoke(appId: String, isHidden: Boolean) {
        appRepository.setAppHidden(appId, isHidden)
    }
}
