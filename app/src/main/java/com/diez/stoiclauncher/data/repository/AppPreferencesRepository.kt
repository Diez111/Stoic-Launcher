package com.diez.stoiclauncher.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

class AppPreferencesRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        val HIDDEN_APPS_KEY = stringPreferencesKey("hidden_apps")
        val APP_ALIASES_KEY = stringPreferencesKey("app_aliases")
        val APP_GROUPS_KEY = stringPreferencesKey("app_groups")
        val APP_SHORTCUTS_KEY = stringPreferencesKey("app_shortcuts")
        val ICON_PACK_KEY = stringPreferencesKey("icon_pack")
        val USER_GROUPS_LIST_KEY = stringPreferencesKey("user_groups_list")
        val DOCK_APPS_KEY = stringPreferencesKey("dock_apps")
    }

    val hiddenAppsFlow: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        val jsonString = preferences[HIDDEN_APPS_KEY] ?: "[]"
        try {
            json.decodeFromString<List<String>>(jsonString).toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    val appAliasesFlow: Flow<Map<String, String>> = context.dataStore.data.map { preferences ->
        val jsonString = preferences[APP_ALIASES_KEY] ?: "{}"
        try {
            json.decodeFromString<Map<String, String>>(jsonString)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    val appShortcutsFlow: Flow<Map<String, String>> = context.dataStore.data.map { preferences ->
        val jsonString = preferences[APP_SHORTCUTS_KEY] ?: "{}"
        try {
            json.decodeFromString<Map<String, String>>(jsonString)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    val iconPackPackageFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[ICON_PACK_KEY] ?: "stoic_builtin"
    }

    suspend fun setAppHidden(packageName: String, isHidden: Boolean) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[HIDDEN_APPS_KEY] ?: "[]"
            val currentSet = try {
                json.decodeFromString<List<String>>(currentJson).toMutableSet()
            } catch (e: Exception) {
                mutableSetOf()
            }

            if (isHidden) {
                currentSet.add(packageName)
            } else {
                currentSet.remove(packageName)
            }

            preferences[HIDDEN_APPS_KEY] = json.encodeToString(currentSet.toList())
        }
    }

    suspend fun setAppAlias(packageName: String, alias: String) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[APP_ALIASES_KEY] ?: "{}"
            val currentMap = try {
                json.decodeFromString<Map<String, String>>(currentJson).toMutableMap()
            } catch (e: Exception) {
                mutableMapOf()
            }

            if (alias.isBlank()) {
                currentMap.remove(packageName)
            } else {
                currentMap[packageName] = alias
            }

            preferences[APP_ALIASES_KEY] = json.encodeToString(currentMap)
        }
    }

    val appGroupsFlow: Flow<Map<String, String>> = context.dataStore.data.map { preferences ->
        val jsonString = preferences[APP_GROUPS_KEY] ?: "{}"
        try {
            json.decodeFromString<Map<String, String>>(jsonString)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun setAppGroup(packageName: String, groupId: String?) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[APP_GROUPS_KEY] ?: "{}"
            val currentMap = try {
                json.decodeFromString<Map<String, String>>(currentJson).toMutableMap()
            } catch (e: Exception) {
                mutableMapOf()
            }

            if (groupId.isNullOrBlank()) {
                currentMap.remove(packageName)
            } else {
                currentMap[packageName] = groupId
            }

            preferences[APP_GROUPS_KEY] = json.encodeToString(currentMap)
        }
    }

    suspend fun renameGroup(oldGroupId: String, newGroupId: String) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[APP_GROUPS_KEY] ?: "{}"
            val currentMap = try {
                json.decodeFromString<Map<String, String>>(currentJson).toMutableMap()
            } catch (e: Exception) {
                mutableMapOf()
            }

            val keysToUpdate = currentMap.filterValues { it == oldGroupId }.keys
            keysToUpdate.forEach { key ->
                currentMap[key] = newGroupId
            }

            preferences[APP_GROUPS_KEY] = json.encodeToString(currentMap)
        }
    }

    suspend fun deleteGroup(groupId: String) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[APP_GROUPS_KEY] ?: "{}"
            val currentMap = try {
                json.decodeFromString<Map<String, String>>(currentJson).toMutableMap()
            } catch (e: Exception) {
                mutableMapOf()
            }

            val keysToRemove = currentMap.filterValues { it == groupId }.keys
            keysToRemove.forEach { key ->
                currentMap.remove(key)
            }

            preferences[APP_GROUPS_KEY] = json.encodeToString(currentMap)
        }
    }

    suspend fun setAppShortcut(position: String, packageName: String?) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[APP_SHORTCUTS_KEY] ?: "{}"
            val currentMap = try {
                json.decodeFromString<Map<String, String>>(currentJson).toMutableMap()
            } catch (e: Exception) {
                mutableMapOf()
            }

            if (packageName == null) {
                currentMap.remove(position)
            } else {
                currentMap[position] = packageName
            }

            preferences[APP_SHORTCUTS_KEY] = json.encodeToString(currentMap)
        }
    }

    suspend fun setIconPackPackage(packageName: String?) {
        context.dataStore.edit { preferences ->
            if (packageName == null) {
                preferences.remove(ICON_PACK_KEY)
            } else {
                preferences[ICON_PACK_KEY] = packageName
            }
        }
    }

    val userGroupsListFlow: Flow<List<String>> = context.dataStore.data.map { preferences ->
        val jsonString = preferences[USER_GROUPS_LIST_KEY] ?: "[]"
        try {
            json.decodeFromString<List<String>>(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addUserGroup(name: String) {
        context.dataStore.edit { preferences ->
            val jsonString = preferences[USER_GROUPS_LIST_KEY] ?: "[]"
            val list = try {
                json.decodeFromString<List<String>>(jsonString).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
            if (!list.contains(name)) {
                list.add(name)
                preferences[USER_GROUPS_LIST_KEY] = json.encodeToString(list)
            }
        }
    }

    suspend fun removeUserGroup(name: String) {
        context.dataStore.edit { preferences ->
            val jsonString = preferences[USER_GROUPS_LIST_KEY] ?: "[]"
            val list = try {
                json.decodeFromString<List<String>>(jsonString).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
            list.remove(name)
            preferences[USER_GROUPS_LIST_KEY] = json.encodeToString(list)
        }
    }

    suspend fun reorderUserGroups(ordered: List<String>) {
        context.dataStore.edit { preferences ->
            preferences[USER_GROUPS_LIST_KEY] = json.encodeToString(ordered)
        }
    }

    val dockAppsFlow: Flow<List<String>> = context.dataStore.data.map { preferences ->
        val jsonString = preferences[DOCK_APPS_KEY] ?: "[]"
        try {
            json.decodeFromString<List<String>>(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addDockApp(packageName: String): Boolean {
        var added = false
        context.dataStore.edit { preferences ->
            val jsonString = preferences[DOCK_APPS_KEY] ?: "[]"
            val list = try {
                json.decodeFromString<List<String>>(jsonString).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
            if (!list.contains(packageName) && list.size < 6) {
                list.add(packageName)
                preferences[DOCK_APPS_KEY] = json.encodeToString(list)
                added = true
            }
        }
        return added
    }

    suspend fun removeDockApp(packageName: String) {
        context.dataStore.edit { preferences ->
            val jsonString = preferences[DOCK_APPS_KEY] ?: "[]"
            val list = try {
                json.decodeFromString<List<String>>(jsonString).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
            list.remove(packageName)
            preferences[DOCK_APPS_KEY] = json.encodeToString(list)
        }
    }

    suspend fun reorderDockApps(ordered: List<String>) {
        context.dataStore.edit { preferences ->
            preferences[DOCK_APPS_KEY] = json.encodeToString(ordered)
        }
    }
}
