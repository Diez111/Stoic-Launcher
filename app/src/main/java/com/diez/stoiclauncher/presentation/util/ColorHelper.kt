package com.diez.stoiclauncher.presentation.util

import android.graphics.Color
import androidx.core.graphics.ColorUtils

object ColorHelper {

    private const val LUMINANCE_THRESHOLD = 0.179

    fun getTextColorForAccent(accentColor: Int, isWallpaperEnabled: Boolean = false): Int {
        if (isWallpaperEnabled) return Color.WHITE
        return if (isLightColor(accentColor)) Color.BLACK else Color.WHITE
    }

    fun getSecondaryTextColorForAccent(accentColor: Int, isWallpaperEnabled: Boolean = false): Int {
        if (isWallpaperEnabled) return 0xB3FFFFFF.toInt()
        val primaryTextColor = getTextColorForAccent(accentColor, false)
        return if (primaryTextColor == Color.BLACK) 0x99000000.toInt() else 0xB3FFFFFF.toInt()
    }

    fun getContrastColor(backgroundColor: Int): Int {
        val luminance = ColorUtils.calculateLuminance(backgroundColor)
        return if (luminance > LUMINANCE_THRESHOLD) Color.BLACK else Color.WHITE
    }

    fun getSecondaryContrastColor(backgroundColor: Int): Int {
        val luminance = ColorUtils.calculateLuminance(backgroundColor)
        return if (luminance > LUMINANCE_THRESHOLD) 0x99000000.toInt() else 0xB3FFFFFF.toInt()
    }

    fun isLightColor(color: Int): Boolean {
        return ColorUtils.calculateLuminance(color) > LUMINANCE_THRESHOLD
    }

    suspend fun isBitmapLight(bitmap: android.graphics.Bitmap): Boolean {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val palette = androidx.palette.graphics.Palette.from(bitmap).generate()
            val dominantColor = palette.getDominantColor(Color.BLACK)
            isLightColor(dominantColor)
        }
    }
}
