package com.diez.stoiclauncher.domain.usage

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.diez.stoiclauncher.domain.model.AppModel
import com.diez.stoiclauncher.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Calendar

class AppUsageManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    // Whitelisted groups that are always allowed
    private val exemptGroups = setOf("Trabajo", "Estudio", "Inversión", "Productividad", "Finance", "Work", "Study")
    
    // Check if permission is granted
    fun hasPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    fun promptPermission() {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Checks if the app is allowed to start based on daily limits.
     * Returns TRUE if allowed, FALSE if blocked.
     */
    fun isAppRunAllowed(app: AppModel): Boolean {
        // 1. Check exemptions (Work/Study/Investment)
        if (isExempt(app)) return true
        
        // 2. Check permission
        if (!hasPermission()) return true // Fail safe: if no permission, don't block
        
        // 3. Get limit from settings (Specific to Package)
        val limitMinutes = runBlocking { 
            settingsRepository.getAppUsageLimit(app.packageName).first() 
        }
        if (limitMinutes <= 0) return true // No limit set for this app
        
        // 4. Check actual usage
        val usageMillis = getDailyUsage(app.packageName)
        val limitMillis = limitMinutes * 60 * 1000L
        
        return usageMillis < limitMillis
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
    
    fun getRemainingTime(app: AppModel): String {
        if (isExempt(app)) return "∞"
        
        val limitMinutes = runBlocking { settingsRepository.getAppUsageLimit(app.packageName).first() }
        if (limitMinutes <= 0) return "∞"
        
        val usageMillis = getDailyUsage(app.packageName)
        val limitMillis = limitMinutes * 60 * 1000L
        val remainingMillis = (limitMillis - usageMillis).coerceAtLeast(0)
        
        val minutes = (remainingMillis / 1000 / 60).toInt()
        return "${minutes}m"
    }
}
