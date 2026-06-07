package com.diez.stoiclauncher.domain.repository

import com.diez.stoiclauncher.domain.model.AppModel
import kotlinx.coroutines.flow.Flow

interface AppRepository {
    fun getAllApps(includeHidden: Boolean = false): Flow<List<AppModel>>
    suspend fun getRawApps(): List<AppModel>
    suspend fun refreshApps()
    fun filterApps(apps: List<AppModel>, query: String): List<AppModel>
    
    suspend fun setAppHidden(packageName: String, isHidden: Boolean)
    val hiddenAppsFlow: Flow<Set<String>>
    suspend fun setAppAlias(packageName: String, alias: String)
    suspend fun setAppGroup(packageName: String, groupId: String?)
    suspend fun renameGroup(oldGroupId: String, newGroupId: String)
    suspend fun deleteGroup(groupId: String)
    fun destroy()
}
