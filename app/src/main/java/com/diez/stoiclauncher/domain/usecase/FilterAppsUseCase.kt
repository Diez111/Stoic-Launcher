package com.diez.stoiclauncher.domain.usecase

import com.diez.stoiclauncher.domain.model.AppModel
import com.diez.stoiclauncher.domain.repository.AppRepository

class FilterAppsUseCase(
    private val appRepository: AppRepository
) {
    operator fun invoke(apps: List<AppModel>, query: String): List<AppModel> {
        return appRepository.filterApps(apps, query)
    }
}
