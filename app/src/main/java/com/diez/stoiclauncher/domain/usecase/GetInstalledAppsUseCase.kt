package com.diez.stoiclauncher.domain.usecase

import com.diez.stoiclauncher.domain.model.AppModel
import com.diez.stoiclauncher.domain.repository.AppRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetInstalledAppsUseCase(
    private val appRepository: AppRepository
) {
    /**
     * Retrieves all installed apps.
     * @param includeHidden If true, returns all apps including those marked as hidden. 
     *                      If false (default), returns only visible apps.
     */
    operator fun invoke(includeHidden: Boolean = false): Flow<List<AppModel>> {
        // AppRepository.getAllApps() currently returns visible apps only (filtered by hidden flow in Impl)
        // We need to modify AppRepository to support retrieving ALL apps or move the hiding logic here.
        // For now, let's assume we need to refactor Repository or access raw list.
        // Looking at AppRepositoryImpl, getAllApps() combines _installedApps with hiddenAppsFlow and filters.
        
        // To do this cleanly, we should expose the raw list from Repository and the hidden set separately,
        // OR add a parameter to getAllApps.
        // Let's rely on the repository's filtered list for now (default behavior) 
        // BUT we need to fix the Repository interface to support "includeHidden".
        
        return appRepository.getAllApps(includeHidden)
    }

    suspend fun invokeSync(): List<AppModel> {
        return appRepository.getRawApps()
    }
}
