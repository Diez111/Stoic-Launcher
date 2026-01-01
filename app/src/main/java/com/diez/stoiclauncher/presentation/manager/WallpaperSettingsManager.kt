package com.diez.stoiclauncher.presentation.manager

import android.app.Activity
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.View
import android.view.WindowManager
import androidx.core.view.WindowCompat
import com.diez.stoiclauncher.presentation.util.ColorHelper

class WallpaperSettingsManager(private val activity: Activity) {

    suspend fun applyWallpaperSettings(isWallpaperEnabled: Boolean, accentColor: Int, wallpaperUri: String?) {
        val window = activity.window
        
        // Ensure Window is Translucent for proper wallpaper rendering
        window.setFormat(PixelFormat.TRANSLUCENT)
        window.navigationBarColor = Color.TRANSPARENT
        window.statusBarColor = Color.TRANSPARENT

        var isLightKey = false

        if (isWallpaperEnabled) {
            handleWallpaperMode(window, wallpaperUri) { isLight ->
                isLightKey = isLight
            }
        } else {
            handleSolidColorMode(window, accentColor) { isLight ->
                isLightKey = isLight
            }
        }

        // Apply Light/Dark status bar icons
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = isLightKey
            isAppearanceLightNavigationBars = isLightKey
        }
    }

    private suspend fun handleWallpaperMode(window: android.view.Window, wallpaperUri: String?, onLightCalculated: (Boolean) -> Unit) {
        if (wallpaperUri != null) {
            try {
                val uri = android.net.Uri.parse(wallpaperUri)
                val inputStream = activity.contentResolver.openInputStream(uri)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                
                if (bitmap != null) {
                    // Custom Wallpaper found
                    val imageView = activity.findViewById<android.widget.ImageView>(com.diez.stoiclauncher.R.id.iv_wallpaper)
                    imageView.visibility = View.VISIBLE
                    imageView.setImageBitmap(bitmap)
                    
                    val isLight = ColorHelper.isBitmapLight(bitmap)
                    onLightCalculated(isLight)

                    // Opaque window for custom image (performance optimization & prevents ghosting)
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                    window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.BLACK))
                } else {
                    applySystemWallpaper(window, onLightCalculated)
                }
            } catch (e: Exception) {
                // Formatting error, fallback to system
                applySystemWallpaper(window, onLightCalculated)
            }
        } else {
            applySystemWallpaper(window, onLightCalculated)
        }
    }

    private fun applySystemWallpaper(window: android.view.Window, onLightCalculated: (Boolean) -> Unit) {
        val imageView = activity.findViewById<android.widget.ImageView>(com.diez.stoiclauncher.R.id.iv_wallpaper)
        imageView.visibility = View.GONE
        
        onLightCalculated(false) // Assume dark for system wallpaper to ensure white text visibility

        // System Wallpaper Flag
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        
        // AGGRESSIVE CLEARING
        window.setBackgroundDrawable(null)
        window.decorView.background = null
    }

    private suspend fun handleSolidColorMode(window: android.view.Window, accentColor: Int, onLightCalculated: (Boolean) -> Unit) {
        val imageView = activity.findViewById<android.widget.ImageView>(com.diez.stoiclauncher.R.id.iv_wallpaper)
        imageView.visibility = View.GONE

        val isLight = ColorHelper.isLightColor(accentColor)
        onLightCalculated(isLight)

        window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(accentColor))
        window.navigationBarColor = accentColor
        window.statusBarColor = accentColor
    }
}
