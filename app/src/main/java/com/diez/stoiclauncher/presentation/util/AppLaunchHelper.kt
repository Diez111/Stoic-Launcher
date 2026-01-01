package com.diez.stoiclauncher.presentation.util

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.widget.Toast
import com.diez.stoiclauncher.domain.model.AppModel

object AppLaunchHelper {
    fun launchApp(context: Context, app: AppModel) {
        try {
            // Check App Usage Limits
            val appContext = context.applicationContext
            if (appContext is com.diez.stoiclauncher.StoicApplication) {
                val usageManager = appContext.container.appUsageManager
                
                if (!usageManager.isAppRunAllowed(app)) {
                    val remaining = usageManager.getRemainingTime(app)
                    if (remaining != "∞") {
                         Toast.makeText(context, "Tiempo límite alcanzado. Descansa un poco.", Toast.LENGTH_LONG).show()
                         return
                    }
                }
            }

            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val user = app.user ?: Process.myUserHandle()
            val activities = launcherApps.getActivityList(app.packageName, user)
            
            if (activities.isNotEmpty()) {
                launcherApps.startMainActivity(activities[0].componentName, user, null, null)
            } else {
                Toast.makeText(context, "No se puede abrir la app", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error al abrir app: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
