package com.diez.stoiclauncher.domain.usecase

import com.diez.stoiclauncher.domain.repository.AppRepository

class ManageAppGroupsUseCase(
    private val appRepository: AppRepository
) {
    suspend fun setAppGroup(appId: String, groupId: String?) {
        appRepository.setAppGroup(appId, groupId)
    }

    suspend fun renameGroup(oldGroupId: String, newGroupId: String) {
        appRepository.renameGroup(oldGroupId, newGroupId)
    }

    suspend fun deleteGroup(groupId: String) {
        appRepository.deleteGroup(groupId)
    }
}
