package com.diez.stoiclauncher.data.repository

import android.content.Context
import android.content.pm.PackageManager
import com.diez.stoiclauncher.domain.model.AppModel
import com.diez.stoiclauncher.domain.repository.AppRepository
import com.diez.stoiclauncher.domain.util.AppCategorizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

class AppRepositoryImpl(
    private val context: Context,
    private val preferencesRepository: AppPreferencesRepository,
    private val iconPackManager: com.diez.stoiclauncher.domain.util.IconPackManager
) : AppRepository {

    companion object {
        private val ACCENT_STRIP = Regex("\\p{M}")
        private val iconCache = android.util.LruCache<String, android.graphics.drawable.Drawable>(80)
        private val normalizedLabelCache = java.util.concurrent.ConcurrentHashMap<String, String>()
        private const val MAX_LABEL_CACHE_SIZE = 500
    }

    private val _installedApps = MutableStateFlow<List<AppModel>>(emptyList())
    private val packageManager: PackageManager = context.packageManager
    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps
    private val callbackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _forceRefresh = MutableStateFlow(0L)

    private val callback = object : android.content.pm.LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: android.os.UserHandle) {
             callbackScope.launch { incrementalRefresh(packageName) }
        }
        override fun onPackageRemoved(packageName: String, user: android.os.UserHandle) {
             callbackScope.launch { incrementalRemove(packageName) }
        }
        override fun onPackageChanged(packageName: String, user: android.os.UserHandle) {
             callbackScope.launch { incrementalRefresh(packageName) }
        }
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: android.os.UserHandle, replacing: Boolean) {
             callbackScope.launch { refreshApps() }
        }
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: android.os.UserHandle, replacing: Boolean) {
             callbackScope.launch { refreshApps() }
        }
    }

    init {
        launcherApps.registerCallback(callback)
        callbackScope.launch {
            preferencesRepository.iconPackPackageFlow.collect { pack ->
                iconPackManager.setIconPack(pack)
                iconCache.evictAll()
                refreshApps()
            }
        }
    }

    override fun destroy() {
        launcherApps.unregisterCallback(callback)
        callbackScope.cancel()
    }

    // Combine installed apps with preferences
    override fun getAllApps(includeHidden: Boolean): Flow<List<AppModel>> = kotlinx.coroutines.flow.combine(
        _installedApps,
        _forceRefresh,
        preferencesRepository.hiddenAppsFlow,
        preferencesRepository.appAliasesFlow,
        preferencesRepository.appGroupsFlow
    ) { apps, _, hidden, aliases, groups ->
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
            _installedApps.emit(loadAllApps())
        }
    }

    private fun loadAllApps(): List<AppModel> {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps
        val userManager = context.getSystemService(Context.USER_SERVICE) as android.os.UserManager
        val profiles = userManager.userProfiles
        val appModels = mutableListOf<AppModel>()

        for (userHandle in profiles) {
            val activities = launcherApps.getActivityList(null, userHandle)
            for (activity in activities) {
                appModels.add(createAppModel(activity, userHandle))
            }
        }
        return appModels
    }

    private fun createAppModel(activity: android.content.pm.LauncherActivityInfo, user: android.os.UserHandle): AppModel {
        val pkg = activity.applicationInfo.packageName
        var icon = iconCache.get(pkg)
        if (icon == null) {
            if (iconPackManager.isStoicMinimal) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    val originalIcon = activity.getIcon(0)
                    if (originalIcon is android.graphics.drawable.AdaptiveIconDrawable && originalIcon.monochrome != null) {
                        icon = originalIcon.monochrome?.constantState?.newDrawable()?.mutate()?.apply {
                            setTint(android.graphics.Color.WHITE)
                        }
                    }
                }
                if (icon == null) icon = activity.getIcon(0)
            } else {
                val component = activity.componentName
                icon = if (component != null) iconPackManager.getIcon(component) else activity.getIcon(0)
            }
            if (icon == null) icon = activity.getIcon(0)
            iconCache.put(pkg, icon)
        }

        return AppModel(label = activity.label.toString(), packageName = pkg, icon = icon, user = user,
            category = AppCategorizer.getCategory(activity.applicationInfo))
    }

    private suspend fun incrementalRefresh(packageName: String) = withContext(Dispatchers.IO) {
        val current = _installedApps.value.toMutableList()
        current.removeAll { it.packageName == packageName }
        val userManager = context.getSystemService(Context.USER_SERVICE) as android.os.UserManager
        for (user in userManager.userProfiles) {
            val activities = launcherApps.getActivityList(packageName, user)
            for (activity in activities) {
                current.add(createAppModel(activity, user))
            }
        }
        _installedApps.emit(current)
    }

    private suspend fun incrementalRemove(packageName: String) = withContext(Dispatchers.IO) {
        val current = _installedApps.value.toMutableList()
        current.removeAll { it.packageName == packageName }
        _installedApps.emit(current)
    }
    
    override fun filterApps(apps: List<AppModel>, query: String): List<AppModel> {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isEmpty()) return apps

        val queryNoAccent = normalizeString(normalizedQuery)

        return apps.filter { app ->
            val label = app.label.lowercase()
            val labelNoAccent = getNormalizedLabel(label)

            if (labelNoAccent.contains(queryNoAccent)) return@filter true
            if (label.contains(normalizedQuery)) return@filter true

            if (queryNoAccent.length >= 1) {
                if (isSubsequence(queryNoAccent, labelNoAccent)) return@filter true
            }

            if (queryNoAccent.length in 2..3) {
                val labelStart = labelNoAccent.take(normalizedQuery.length + 1)
                val dist = levenshtein(labelStart, queryNoAccent)
                if (dist <= 1) return@filter true
            }

            false
        }
    }

    private fun normalizeString(input: String): String {
        return java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
            .replace(ACCENT_STRIP, "")
    }

    private fun getNormalizedLabel(label: String): String {
        if (normalizedLabelCache.size > MAX_LABEL_CACHE_SIZE) {
            normalizedLabelCache.clear()
        }
        return normalizedLabelCache.getOrPut(label) { normalizeString(label) }
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
        _forceRefresh.value = System.currentTimeMillis()
    }

    override suspend fun deleteGroup(groupId: String) {
        preferencesRepository.deleteGroup(groupId)
        _forceRefresh.value = System.currentTimeMillis()
    }
}
