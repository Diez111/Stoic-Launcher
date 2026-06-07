package com.diez.stoiclauncher.presentation.manager

import android.app.Activity
import android.graphics.Bitmap
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
                    decodeSampledBitmap(uri, MAX_WALLPAPER_WIDTH, MAX_WALLPAPER_HEIGHT)
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

    private fun decodeSampledBitmap(uri: android.net.Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        activity.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        return activity.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun applySystemWallpaper(window: android.view.Window, onLight: (Boolean) -> Unit) {
        val imageView = activity.findViewById<android.widget.ImageView>(com.diez.stoiclauncher.R.id.iv_wallpaper)
        imageView.visibility = View.GONE
        onLight(false)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.setBackgroundDrawable(null)
        window.decorView.background = null
    }

    companion object {
        private const val TAG = "WallpaperManager"
        private const val MAX_WALLPAPER_WIDTH = 1080
        private const val MAX_WALLPAPER_HEIGHT = 1920
    }

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
