package com.diez.stoiclauncher.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

class AppPreferencesRepository(private val context: Context) {

    private val gson = Gson()
    
    // We will store aliases as a JSON map: "package_name" -> "alias"
    // We will store hidden apps as a JSON list: ["package_name1", "package_name2"]
    
    // Simplification: Using simple keys for now. 
    // Ideally we'd use separate tables or a proto, but JSON in string preference is fast enough for this list size.
    
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
        val json = preferences[HIDDEN_APPS_KEY] ?: "[]"
        val type = object : TypeToken<Set<String>>() {}.type
        gson.fromJson(json, type) ?: emptySet()
    }

    val appAliasesFlow: Flow<Map<String, String>> = context.dataStore.data.map { preferences ->
        val json = preferences[APP_ALIASES_KEY] ?: "{}"
        val type = object : TypeToken<Map<String, String>>() {}.type
        gson.fromJson(json, type) ?: emptyMap()
    }

    val appShortcutsFlow: Flow<Map<String, String>> = context.dataStore.data.map { preferences ->
        val json = preferences[APP_SHORTCUTS_KEY] ?: "{}"
        val type = object : TypeToken<Map<String, String>>() {}.type
        gson.fromJson(json, type) ?: emptyMap()
    }

    val iconPackPackageFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[ICON_PACK_KEY] ?: "stoic_builtin"
    }
    
    suspend fun setAppHidden(packageName: String, isHidden: Boolean) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[HIDDEN_APPS_KEY] ?: "[]"
            val type = object : TypeToken<MutableSet<String>>() {}.type
            val currentSet: MutableSet<String> = gson.fromJson(currentJson, type) ?: mutableSetOf()
            
            if (isHidden) {
                currentSet.add(packageName)
            } else {
                currentSet.remove(packageName)
            }
            
            preferences[HIDDEN_APPS_KEY] = gson.toJson(currentSet)
        }
    }

    suspend fun setAppAlias(packageName: String, alias: String) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[APP_ALIASES_KEY] ?: "{}"
            val type = object : TypeToken<MutableMap<String, String>>() {}.type
            val currentMap: MutableMap<String, String> = gson.fromJson(currentJson, type) ?: mutableMapOf()
            
            if (alias.isBlank()) {
                currentMap.remove(packageName)
            } else {
                currentMap[packageName] = alias
            }
            
            preferences[APP_ALIASES_KEY] = gson.toJson(currentMap)
        }
    }
    val appGroupsFlow: Flow<Map<String, String>> = context.dataStore.data.map { preferences ->
        val json = preferences[APP_GROUPS_KEY] ?: "{}"
        val type = object : TypeToken<Map<String, String>>() {}.type
        gson.fromJson(json, type) ?: emptyMap()
    }
    
    suspend fun setAppGroup(packageName: String, groupId: String?) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[APP_GROUPS_KEY] ?: "{}"
            val type = object : TypeToken<MutableMap<String, String>>() {}.type
            val currentMap: MutableMap<String, String> = gson.fromJson(currentJson, type) ?: mutableMapOf()
            
            if (groupId.isNullOrBlank()) {
                currentMap.remove(packageName)
            } else {
                currentMap[packageName] = groupId
            }
            
            preferences[APP_GROUPS_KEY] = gson.toJson(currentMap)
        }
    }

    suspend fun renameGroup(oldGroupId: String, newGroupId: String) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[APP_GROUPS_KEY] ?: "{}"
            val type = object : TypeToken<MutableMap<String, String>>() {}.type
            val currentMap: MutableMap<String, String> = gson.fromJson(currentJson, type) ?: mutableMapOf()
            
            // Find keys where value is oldGroupId and update them
            val keysToUpdate = currentMap.filterValues { it == oldGroupId }.keys
            keysToUpdate.forEach { key ->
                currentMap[key] = newGroupId
            }
            
            preferences[APP_GROUPS_KEY] = gson.toJson(currentMap)
        }
    }

    suspend fun deleteGroup(groupId: String) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[APP_GROUPS_KEY] ?: "{}"
            val type = object : TypeToken<MutableMap<String, String>>() {}.type
            val currentMap: MutableMap<String, String> = gson.fromJson(currentJson, type) ?: mutableMapOf()
            
            // Remove entries belonging to this group
             val keysToRemove = currentMap.filterValues { it == groupId }.keys
             keysToRemove.forEach { key ->
                 currentMap.remove(key)
             }
            
            preferences[APP_GROUPS_KEY] = gson.toJson(currentMap)
        }
    }

    suspend fun setAppShortcut(position: String, packageName: String?) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[APP_SHORTCUTS_KEY] ?: "{}"
            val type = object : TypeToken<MutableMap<String, String>>() {}.type
            val currentMap: MutableMap<String, String> = gson.fromJson(currentJson, type) ?: mutableMapOf()
            
            if (packageName == null) {
                currentMap.remove(position)
            } else {
                currentMap[position] = packageName
            }
            
            val newJson = gson.toJson(currentMap)
            preferences[APP_SHORTCUTS_KEY] = newJson
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
        val json = preferences[USER_GROUPS_LIST_KEY] ?: "[]"
        val type = object : TypeToken<List<String>>() {}.type
        gson.fromJson(json, type) ?: emptyList()
    }

    suspend fun addUserGroup(name: String) {
        context.dataStore.edit { preferences ->
            val json = preferences[USER_GROUPS_LIST_KEY] ?: "[]"
            val type = object : TypeToken<MutableList<String>>() {}.type
            val list: MutableList<String> = gson.fromJson(json, type) ?: mutableListOf()
            if (!list.contains(name)) {
                list.add(name)
                preferences[USER_GROUPS_LIST_KEY] = gson.toJson(list)
            }
        }
    }

    suspend fun removeUserGroup(name: String) {
        context.dataStore.edit { preferences ->
            val json = preferences[USER_GROUPS_LIST_KEY] ?: "[]"
            val type = object : TypeToken<MutableList<String>>() {}.type
            val list: MutableList<String> = gson.fromJson(json, type) ?: mutableListOf()
            list.remove(name)
            preferences[USER_GROUPS_LIST_KEY] = gson.toJson(list)
        }
    }

    suspend fun reorderUserGroups(ordered: List<String>) {
        context.dataStore.edit { preferences ->
            preferences[USER_GROUPS_LIST_KEY] = gson.toJson(ordered)
        }
    }

    val dockAppsFlow: Flow<List<String>> = context.dataStore.data.map { preferences ->
        val json = preferences[DOCK_APPS_KEY] ?: "[]"
        val type = object : TypeToken<List<String>>() {}.type
        gson.fromJson(json, type) ?: emptyList()
    }

    suspend fun addDockApp(packageName: String): Boolean {
        var added = false
        context.dataStore.edit { preferences ->
            val json = preferences[DOCK_APPS_KEY] ?: "[]"
            val type = object : TypeToken<MutableList<String>>() {}.type
            val list: MutableList<String> = gson.fromJson(json, type) ?: mutableListOf()
            if (!list.contains(packageName) && list.size < 6) {
                list.add(packageName)
                preferences[DOCK_APPS_KEY] = gson.toJson(list)
                added = true
            }
        }
        return added
    }

    suspend fun removeDockApp(packageName: String) {
        context.dataStore.edit { preferences ->
            val json = preferences[DOCK_APPS_KEY] ?: "[]"
            val type = object : TypeToken<MutableList<String>>() {}.type
            val list: MutableList<String> = gson.fromJson(json, type) ?: mutableListOf()
            list.remove(packageName)
            preferences[DOCK_APPS_KEY] = gson.toJson(list)
        }
    }

    suspend fun reorderDockApps(ordered: List<String>) {
        context.dataStore.edit { preferences ->
            preferences[DOCK_APPS_KEY] = gson.toJson(ordered)
        }
    }
}
