package com.diez.stoiclauncher.domain.usecase

import com.diez.stoiclauncher.domain.repository.AppRepository

class RefreshAppsUseCase(
    private val appRepository: AppRepository
) {
    suspend operator fun invoke() {
        appRepository.refreshApps()
    }
}
