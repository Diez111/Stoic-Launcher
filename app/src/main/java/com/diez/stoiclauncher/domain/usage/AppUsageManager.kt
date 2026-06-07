package com.diez.stoiclauncher.domain.usage

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.diez.stoiclauncher.domain.model.AppModel
import com.diez.stoiclauncher.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar

class AppUsageManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    // Whitelisted groups that are always allowed
    private val exemptGroups = setOf("Trabajo", "Estudio", "Inversión", "Productividad", "Finance", "Work", "Study")
    
    // Check if permission is granted
    @Suppress("DEPRECATION")
    fun hasPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    fun promptPermission() {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("AppUsageManager", "Error opening usage access settings", e)
        }
    }

    /**
     * Checks if the app is allowed to start based on daily limits.
     * Suspending version — use from coroutine to avoid main-thread blocking.
     */
    suspend fun isAppRunAllowed(app: AppModel): Boolean = withContext(Dispatchers.IO) {
        if (isExempt(app)) return@withContext true
        if (!hasPermission()) return@withContext true
        
        val limitMinutes = settingsRepository.getAppUsageLimit(app.packageName).first()
        if (limitMinutes <= 0) return@withContext true
        
        val usageMillis = getDailyUsage(app.packageName)
        val limitMillis = limitMinutes * 60 * 1000L
        
        usageMillis < limitMillis
    }
    
    private fun isExempt(app: AppModel): Boolean {
        // Exempt System Settings, Phone, Camera, Launcher itself
        if (app.packageName == context.packageName) return true
        if (app.packageName == "com.android.settings") return true
        if (app.packageName == "com.android.dialer") return true
        // Allow user-defined groups
        if (app.groupId != null && exemptGroups.any { it.equals(app.groupId, ignoreCase = true) }) return true
        return false
    }
    
    private fun getDailyUsage(packageName: String): Long {
        if (usageStatsManager == null) return 0
        
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()
        
        val statsMap = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
        val stats = statsMap[packageName]
        
        return stats?.totalTimeInForeground ?: 0L
    }
    
    fun getUsedMinutesToday(packageName: String): Int {
        val millis = getDailyUsage(packageName)
        return kotlin.math.ceil(millis / 1000.0 / 60.0).toInt()
    }

    suspend fun getRemainingTime(app: AppModel): String = withContext(Dispatchers.IO) {
        if (isExempt(app)) return@withContext "∞"
        val limitMinutes = settingsRepository.getAppUsageLimit(app.packageName).first()
        if (limitMinutes <= 0) return@withContext "∞"
        val remaining = limitMinutes - getUsedMinutesToday(app.packageName)
        "${remaining.coerceAtLeast(0)}m"
    }

    suspend fun getRemainingMinutes(app: AppModel): Int = withContext(Dispatchers.IO) {
        if (isExempt(app)) return@withContext Int.MAX_VALUE
        val limitMinutes = settingsRepository.getAppUsageLimit(app.packageName).first()
        if (limitMinutes <= 0) return@withContext Int.MAX_VALUE
        (limitMinutes - getUsedMinutesToday(app.packageName)).coerceAtLeast(0)
    }

    companion object {
        private const val TAG = "AppUsageManager"
    }
}
