package com.diez.stoiclauncher.presentation.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.diez.stoiclauncher.R
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class GesturesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gestures)

        val appContainer = (application as com.diez.stoiclauncher.StoicApplication).container
        val settingsRepository = appContainer.settingsRepository

        // Triggers
        val rowDoubleTap = findViewById<android.view.View>(R.id.row_double_tap)
        val rowSwipeDown = findViewById<android.view.View>(R.id.row_swipe_down)
        val rowVolUp = findViewById<android.view.View>(R.id.row_vol_up)
        val rowVolDown = findViewById<android.view.View>(R.id.row_vol_down)
        val btnAccessibility = findViewById<android.view.View>(R.id.btn_enable_accessibility)

        val tvActionDoubleTap = findViewById<TextView>(R.id.tv_action_double_tap)
        val tvActionSwipeDown = findViewById<TextView>(R.id.tv_action_swipe_down)
        val tvActionVolUp = findViewById<TextView>(R.id.tv_action_vol_up)
        val tvActionVolDown = findViewById<TextView>(R.id.tv_action_vol_down)
        val tvAccessibilityStatus = findViewById<TextView>(R.id.tv_accessibility_status)

        // Observe Mappings
        kotlinx.coroutines.MainScope().launch {
            settingsRepository.gestureMappingsFlow.collect { mappings: Map<String, String> ->
                tvActionDoubleTap.text = getActionLabel(mappings["DOUBLE_TAP"])
                tvActionSwipeDown.text = getActionLabel(mappings["SWIPE_DOWN"])
                tvActionVolUp.text = getActionLabel(mappings["VOL_UP_LONG"])
                tvActionVolDown.text = getActionLabel(mappings["VOL_DOWN_LONG"])
            }
        }

        // Click Listeners
        rowDoubleTap.setOnClickListener { showActionPicker("DOUBLE_TAP") }
        rowSwipeDown.setOnClickListener { showActionPicker("SWIPE_DOWN") }
        rowVolUp.setOnClickListener { showActionPicker("VOL_UP_LONG") }
        rowVolDown.setOnClickListener { showActionPicker("VOL_DOWN_LONG") }

        btnAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        // Theme Observation (Mirror SettingsActivity logic)
        kotlinx.coroutines.MainScope().launch {
            kotlinx.coroutines.flow.combine(
                settingsRepository.isWallpaperEnabled,
                settingsRepository.accentColor,
                settingsRepository.wallpaperUri
            ) { wp, col, uri -> Triple(wp, col, uri) }
            .collect { (isWallpaper, accentColor, wallpaperUri) ->
                var isLight = false
                if (isWallpaper && wallpaperUri != null) {
                    try {
                        val bitmap = android.graphics.BitmapFactory.decodeStream(contentResolver.openInputStream(android.net.Uri.parse(wallpaperUri)))
                        if (bitmap != null) {
                            window.setBackgroundDrawable(android.graphics.drawable.BitmapDrawable(resources, bitmap))
                            isLight = com.diez.stoiclauncher.presentation.util.ColorHelper.isBitmapLight(bitmap)
                        } else {
                            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error loading wallpaper bitmap", e)
                        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
                    }
                } else if (isWallpaper) {
                    window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
                } else {
                    window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(accentColor))
                    isLight = com.diez.stoiclauncher.presentation.util.ColorHelper.isLightColor(accentColor)
                }

                val contrastColor = if (isLight) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                val secondaryColor = if (isLight) 0x99000000.toInt() else 0x99FFFFFF.toInt()

                findViewById<TextView>(R.id.tv_gestures_title).setTextColor(contrastColor)
                findViewById<TextView>(R.id.tv_header_accessibility).setTextColor(secondaryColor)
                findViewById<TextView>(R.id.tv_header_triggers).setTextColor(secondaryColor)
                findViewById<TextView>(R.id.tv_label_accessibility).setTextColor(contrastColor)
                
                // Color all trigger labels (finding by direct child or specific IDs if I had added them, but I'll use a loop or specific IDs for clarity)
                listOf(rowDoubleTap, rowSwipeDown, rowVolUp, rowVolDown).forEach { row ->
                    (row as android.view.ViewGroup).getChildAt(0).let { if (it is TextView) it.setTextColor(contrastColor) }
                }
                
                tvAccessibilityStatus.setTextColor(secondaryColor)
                tvActionDoubleTap.setTextColor(secondaryColor)
                tvActionSwipeDown.setTextColor(secondaryColor)
                tvActionVolUp.setTextColor(secondaryColor)
                tvActionVolDown.setTextColor(secondaryColor)
                
                 window.navigationBarColor = if (isWallpaper && wallpaperUri != null) android.graphics.Color.TRANSPARENT else if (isWallpaper) android.graphics.Color.BLACK else accentColor
                 window.statusBarColor = if (isWallpaper && wallpaperUri != null) android.graphics.Color.TRANSPARENT else if (isWallpaper) android.graphics.Color.BLACK else accentColor
                 
                 androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = isLight
                    isAppearanceLightNavigationBars = isLight
                 }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
    }

    private fun updateAccessibilityStatus() {
        val enabled = isAccessibilityServiceEnabled()
        findViewById<TextView>(R.id.tv_accessibility_status).text = if (enabled) "Activado" else "Desactivado (Toca para activar)"
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "${packageName}/com.diez.stoiclauncher.presentation.services.StoicAccessibilityService"
        val enabled = Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        if (enabled == 1) {
            val settingValue = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            return settingValue?.contains(service) == true
        }
        return false
    }

    companion object {
        private const val TAG = "GesturesActivity"
    }

    private fun getActionLabel(action: String?): String {
        return when (action) {
            "FLASHLIGHT" -> getString(R.string.action_flashlight)
            "SEARCH" -> getString(R.string.action_search)
            "NOTIFICATIONS" -> getString(R.string.action_notifications)
            else -> getString(R.string.action_none)
        }
    }

    private fun showActionPicker(trigger: String) {
        val options = listOf(
            com.diez.stoiclauncher.presentation.common.MenuOption(getString(R.string.action_none)),
            com.diez.stoiclauncher.presentation.common.MenuOption(getString(R.string.action_flashlight)),
            com.diez.stoiclauncher.presentation.common.MenuOption(getString(R.string.action_search)),
            com.diez.stoiclauncher.presentation.common.MenuOption(getString(R.string.action_notifications))
        )

        val bottomSheet = com.diez.stoiclauncher.presentation.common.BottomSheetMenu(getString(R.string.gestures_title), options) { index ->
            val action = when (index) {
                1 -> "FLASHLIGHT"
                2 -> "SEARCH"
                3 -> "NOTIFICATIONS"
                else -> "NONE"
            }
            val appContainer = (application as com.diez.stoiclauncher.StoicApplication).container
            kotlinx.coroutines.MainScope().launch {
                appContainer.settingsRepository.setGestureMapping(trigger, action)
            }
        }
        bottomSheet.show(supportFragmentManager, "action_picker")
    }
}
