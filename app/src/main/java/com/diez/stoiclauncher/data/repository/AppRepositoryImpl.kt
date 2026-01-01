package com.diez.stoiclauncher.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.diez.stoiclauncher.domain.model.AppModel
import com.diez.stoiclauncher.domain.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

class AppRepositoryImpl(
    private val context: Context,
    private val preferencesRepository: AppPreferencesRepository
) : AppRepository {

    private val _installedApps = MutableStateFlow<List<AppModel>>(emptyList())
    private val packageManager: PackageManager = context.packageManager
    private val searchIndex = com.diez.stoiclauncher.data.search.Trie()
    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps

    private val callback = object : android.content.pm.LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: android.os.UserHandle) {
             kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { refreshApps() }
        }
        override fun onPackageRemoved(packageName: String, user: android.os.UserHandle) {
             kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { refreshApps() }
        }
        override fun onPackageChanged(packageName: String, user: android.os.UserHandle) {
             kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { refreshApps() }
        }
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: android.os.UserHandle, replacing: Boolean) {
             kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { refreshApps() }
        }
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: android.os.UserHandle, replacing: Boolean) {
             kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { refreshApps() }
        }
    }

    init {
        launcherApps.registerCallback(callback)
    }

    // Combine installed apps with preferences
    override fun getAllApps(includeHidden: Boolean): Flow<List<AppModel>> = kotlinx.coroutines.flow.combine(
        _installedApps,
        preferencesRepository.hiddenAppsFlow,
        preferencesRepository.appAliasesFlow,
        preferencesRepository.appGroupsFlow
    ) { apps, hidden, aliases, groups ->
        // Return ALL apps effectively if includeHidden is true, otherwise filter
        val visibleApps = if (includeHidden) apps else apps.filter { !hidden.contains(it.uniqueId) }
        
        visibleApps.map { app ->
            val alias = aliases[app.uniqueId]
            val groupId = groups[app.uniqueId]
            val finalApp = if (alias != null) app.copy(label = alias) else app
            if (groupId != null) finalApp.copy(groupId = groupId) else finalApp
        }.sortedBy { it.label.lowercase() }
    }

    override suspend fun getRawApps(): List<AppModel> {
        refreshApps()
        return _installedApps.value.sortedBy { it.label.lowercase() }
    }

    override suspend fun refreshApps() {
        withContext(Dispatchers.IO) {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps
            val userManager = context.getSystemService(Context.USER_SERVICE) as android.os.UserManager
            
            val profiles = userManager.userProfiles
            val appModels = mutableListOf<AppModel>()
            
            for (userHandle in profiles) {
                val activities = launcherApps.getActivityList(null, userHandle)
                for (activity in activities) {
                    appModels.add(
                        AppModel(
                            label = activity.label.toString(),
                            packageName = activity.applicationInfo.packageName,
                            icon = activity.getIcon(0), // 0 density = default
                            user = userHandle,
                            category = com.diez.stoiclauncher.domain.util.AppCategorizer.getCategory(activity.applicationInfo)
                        )
                    )
                }
            }
            _installedApps.emit(appModels)
        }
    }
    
    override fun filterApps(apps: List<AppModel>, query: String): List<AppModel> {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isEmpty()) return apps
        
        return apps.filter { app ->
            val label = app.label.lowercase()
            // 1. Contains
            if (label.contains(normalizedQuery)) return@filter true
            
            // 2. Acronym? (Start letters of words) - TODO if needed
            
            // 3. Fuzzy / Typos (Levenshtein)
            // Allow 1 error for every 4 chars?
            val threshold = (label.length / 4).coerceAtLeast(1)
            // Or simplified: if query is short, exact or contains. If longer, allow typo.
            if (normalizedQuery.length > 2) {
                 val distance = levenshtein(label, normalizedQuery)
                 // If distance is small enough considering query length
                 // E.g. "wats" (4) vs "whatsapp" (8). Distance is 4. mismatch.
                 // "wats" vs "whats" (5). Distance 1. Match?
                 // We want to match "wats" to "whatsapp". "wats" matches "whats" (prefix of whatsapp).
                 // So check if query is close to ANY substring of label? That's complex.
                 // Let's settle for: Label starts with or Contains is handled.
                 // Levenshtein between Query and Label? No, Label is usually longer.
                 // Levenshtein between Query and START of Label?
                 // Or Query and any word in Label?
                 
                 // For "wats" -> "WhatsApp":
                 // "wats" distance to "what" is 1.
                 // Let's implement a simple "Fuzzy Contains":
                 // Can we transform query "wats" to regex "w.*a.*t.*s"? (Subsequence)
                 if (isSubsequence(normalizedQuery, label)) return@filter true
            }
            false
        }
    }
    
    private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLength = lhs.length
        val rhsLength = rhs.length
        var cost = Array(lhsLength + 1) { it }
        var newCost = Array(lhsLength + 1) { 0 }
        for (i in 1..rhsLength) {
            newCost[0] = i
            for (j in 1..lhsLength) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = minOf(costInsert, costDelete, costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLength]
    }
    
    private fun isSubsequence(query: String, target: String): Boolean {
        var i = 0
        var j = 0
        while (i < query.length && j < target.length) {
            if (query[i] == target[j]) {
                i++
            }
            j++
        }
        return i == query.length
    }

    // Note: The 'packageName' parameter in interface is now interpreted as 'uniqueId' (package|user)
    // We should rename parameters in interface ideally, but for now treating string as ID.
    
    // Management
    override val hiddenAppsFlow: Flow<Set<String>> = preferencesRepository.hiddenAppsFlow

    override suspend fun setAppHidden(packageName: String, isHidden: Boolean) {
        preferencesRepository.setAppHidden(packageName, isHidden)
    }

    override suspend fun setAppAlias(packageName: String, alias: String) {
        preferencesRepository.setAppAlias(packageName, alias)
    }

    override suspend fun setAppGroup(packageName: String, groupId: String?) {
        preferencesRepository.setAppGroup(packageName, groupId)
    }

    override suspend fun renameGroup(oldGroupId: String, newGroupId: String) {
        preferencesRepository.renameGroup(oldGroupId, newGroupId)
        _installedApps.value = _installedApps.value
    }

    override suspend fun deleteGroup(groupId: String) {
        preferencesRepository.deleteGroup(groupId)
        _installedApps.value = _installedApps.value
    }
}
