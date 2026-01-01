package com.diez.stoiclauncher.presentation.services

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.diez.stoiclauncher.StoicApplication
import com.diez.stoiclauncher.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class StoicAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var isFlashlightOn = false
    private val handler = Handler(Looper.getMainLooper())
    
    // For long press detection
    private var lastKeyDownTime = 0L
    private val longPressThreshold = 500L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (event.repeatCount == 0) {
                    lastKeyDownTime = System.currentTimeMillis()
                } else if (System.currentTimeMillis() - lastKeyDownTime >= longPressThreshold) {
                    // Long Press Detected
                    handleLongPress(keyCode)
                    // We consume it to avoid volume beep/change
                    return true
                }
            }
        }
        return super.onKeyEvent(event)
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
            e.printStackTrace()
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
}
