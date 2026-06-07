package com.diez.stoiclauncher.presentation.util

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.util.Log
import android.widget.Toast
import com.diez.stoiclauncher.domain.model.AppModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppLaunchHelper {

    private const val TAG = "AppLaunchHelper"

    suspend fun launchApp(context: Context, app: AppModel) = withContext(Dispatchers.IO) {
        try {
            val appContext = context.applicationContext
            if (appContext is com.diez.stoiclauncher.StoicApplication) {
                val usageManager = appContext.container.appUsageManager
                if (!usageManager.isAppRunAllowed(app)) {
                    val remaining = usageManager.getRemainingTime(app)
                    if (remaining != "∞") {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Tiempo límite alcanzado. Descansa un poco.", Toast.LENGTH_LONG).show()
                        }
                        return@withContext
                    }
                }
            }

            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val user = app.user ?: Process.myUserHandle()
            val activities = launcherApps.getActivityList(app.packageName, user)

            withContext(Dispatchers.Main) {
                if (activities.isNotEmpty()) {
                    launcherApps.startMainActivity(activities[0].componentName, user, null, null)
                } else {
                    Toast.makeText(context, "No se puede abrir la app", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching app: ${app.packageName}", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error al abrir app", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
