package com.diez.stoiclauncher.domain.util

import android.content.Context
import android.hardware.camera2.CameraManager
import android.util.Log

class FlashlightManager(private val context: Context) {

    @Volatile
    var isOn: Boolean = false
        private set

    fun toggle(): Boolean {
        try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val ids = cm.cameraIdList
            if (ids.isEmpty()) {
                Log.w(TAG, "No cameras available")
                return false
            }
            val id = ids[0]
            isOn = !isOn
            cm.setTorchMode(id, isOn)
            return isOn
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling flashlight", e)
            isOn = false
            return false
        }
    }

    companion object {
        private const val TAG = "FlashlightManager"
    }
}
