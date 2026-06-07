package com.diez.stoiclauncher.presentation.manager

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.core.view.WindowCompat
import com.diez.stoiclauncher.presentation.util.ColorHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WallpaperSettingsManager(private val activity: Activity) {

    suspend fun applyWallpaperSettings(isWallpaperEnabled: Boolean, accentColor: Int, wallpaperUri: String?) {
        val window = activity.window
        window.setFormat(PixelFormat.TRANSLUCENT)
        window.navigationBarColor = Color.TRANSPARENT
        window.statusBarColor = Color.TRANSPARENT

        var isLightKey = false
        if (isWallpaperEnabled) handleWallpaperMode(window, wallpaperUri) { isLightKey = it }
        else handleSolidColorMode(window, accentColor) { isLightKey = it }

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = isLightKey
            isAppearanceLightNavigationBars = isLightKey
        }
    }

    private suspend fun handleWallpaperMode(window: android.view.Window, wallpaperUri: String?, onLight: (Boolean) -> Unit) {
        if (wallpaperUri != null) {
            try {
                val uri = android.net.Uri.parse(wallpaperUri)
                val bitmap = withContext(Dispatchers.IO) {
                    activity.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                }
                if (bitmap != null) {
                    val imageView = activity.findViewById<android.widget.ImageView>(com.diez.stoiclauncher.R.id.iv_wallpaper)
                    imageView.visibility = View.VISIBLE
                    imageView.setImageBitmap(bitmap)
                    val isLight = ColorHelper.isBitmapLight(bitmap)
                    onLight(isLight)
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                    window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.BLACK))
                } else applySystemWallpaper(window, onLight)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading custom wallpaper", e)
                applySystemWallpaper(window, onLight)
            }
        } else applySystemWallpaper(window, onLight)
    }

    private fun applySystemWallpaper(window: android.view.Window, onLight: (Boolean) -> Unit) {
        val imageView = activity.findViewById<android.widget.ImageView>(com.diez.stoiclauncher.R.id.iv_wallpaper)
        imageView.visibility = View.GONE
        onLight(false)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.setBackgroundDrawable(null)
        window.decorView.background = null
    }

    companion object { private const val TAG = "WallpaperManager" }

    private suspend fun handleSolidColorMode(window: android.view.Window, accentColor: Int, onLight: (Boolean) -> Unit) {
        activity.findViewById<android.widget.ImageView>(com.diez.stoiclauncher.R.id.iv_wallpaper).visibility = View.GONE
        val isLight = ColorHelper.isLightColor(accentColor)
        onLight(isLight)
        window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(accentColor))
        window.navigationBarColor = accentColor
        window.statusBarColor = accentColor
    }
}
