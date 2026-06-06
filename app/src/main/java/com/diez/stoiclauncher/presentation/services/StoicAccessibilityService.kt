package com.diez.stoiclauncher.presentation.services

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.diez.stoiclauncher.StoicApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class StoicAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isFlashlightOn = false
    private val handler = Handler(Looper.getMainLooper())

    private var lastKeyDownTime = 0L
    private val longPressThreshold = 500L
    private var lastForegroundPackage: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            if (pkg != lastForegroundPackage) {
                lastForegroundPackage = pkg
                enforceLimit(pkg)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (event.repeatCount == 0) {
                    lastKeyDownTime = System.currentTimeMillis()
                } else if (System.currentTimeMillis() - lastKeyDownTime >= longPressThreshold) {
                    handleLongPress(keyCode)
                    return true
                }
            }
        }
        return super.onKeyEvent(event)
    }

    private fun enforceLimit(packageName: String) {
        val appContainer = (application as StoicApplication).container
        val usageManager = appContainer.appUsageManager
        val settingsRepo = appContainer.settingsRepository

        scope.launch {
            if (!usageManager.hasPermission()) return@launch
            val limitMinutes = settingsRepo.getAppUsageLimit(packageName).first()
            if (limitMinutes <= 0) return@launch

            val usedMinutes = usageManager.getUsedMinutesToday(packageName)
            if (usedMinutes >= limitMinutes) {
                handler.post {
                    showLimitReachedDialog(packageName, limitMinutes, usedMinutes)
                }
            }
        }
    }

    private fun showLimitReachedDialog(pkg: String, limitMin: Int, usedMin: Int) {
        try {
            val label = packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0)
            ).toString()

            val overlay = android.app.AlertDialog.Builder(this@StoicAccessibilityService)
                .setTitle("Límite alcanzado")
                .setMessage("$label\n\nHas usado $usedMin min de $limitMin min disponibles hoy. Volvé mañana o ajustá el límite en Stoic Launcher.")
                .setPositiveButton("Ajustar límite") { _, _ ->
                    val intent = packageManager.getLaunchIntentForPackage(packageName)
                    intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                }
                .setNegativeButton("Cerrar") { _, _ ->
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
                .setCancelable(false)
                .create()

            overlay.window?.setType(
                android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            )
            overlay.show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing limit dialog", e)
        }
    }

    private fun handleLongPress(keyCode: Int) {
        val trigger = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) "VOL_UP_LONG" else "VOL_DOWN_LONG"

        val settingsRepository = (application as StoicApplication).container.settingsRepository
        scope.launch {
            val mappings = settingsRepository.gestureMappingsFlow.first()
            val action = mappings[trigger]

            Handler(Looper.getMainLooper()).post {
                executeAction(action)
            }
        }
    }

    private fun executeAction(action: String?) {
        when (action) {
            "FLASHLIGHT" -> toggleFlashlight()
            "NOTIFICATIONS" -> expandNotifications()
            "SEARCH" -> launchSearch()
        }
    }

    private fun toggleFlashlight() {
        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            isFlashlightOn = !isFlashlightOn
            cameraManager.setTorchMode(cameraId, isFlashlightOn)
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling flashlight", e)
        }
    }

    private fun expandNotifications() {
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    private fun launchSearch() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        intent?.putExtra("open_search", true)
        startActivity(intent)
    }

    companion object {
        private const val TAG = "StoicAccessService"
    }
}
