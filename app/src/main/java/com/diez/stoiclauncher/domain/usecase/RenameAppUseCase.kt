package com.diez.stoiclauncher.domain.usecase

import com.diez.stoiclauncher.domain.repository.AppRepository

class RenameAppUseCase(
    private val appRepository: AppRepository
) {
    suspend operator fun invoke(appId: String, newName: String) {
        appRepository.setAppAlias(appId, newName)
    }
}
