package com.diez.stoiclauncher.presentation.util

import android.app.Dialog
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.Window
import androidx.core.view.WindowCompat
import com.google.android.material.bottomsheet.BottomSheetDialog

object UiHelper {

    fun setupBottomSheetColor(dialog: Dialog, view: View, accentColor: Int) {
        // 1. Tint Background Logic (Rounded Corners)
        // Access the BottomSheet's root view (usually a FrameLayout in simpler implementations, 
        // but here 'view' is our custom layout with the background drawable).
        
        // Ensure background drawable exists and tint it
        val bgDrawable = androidx.core.content.ContextCompat.getDrawable(view.context, com.diez.stoiclauncher.R.drawable.shape_bottom_sheet)?.mutate()
        if (bgDrawable != null) {
            androidx.core.graphics.drawable.DrawableCompat.setTint(bgDrawable, accentColor)
            view.background = bgDrawable
        }

        // 2. Navigation Bar Integration (The "Continuous" Look)
        val window = dialog.window
        if (window != null) {
            // Modern API: Use WindowInsetsController for navigation bar styling
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(
                    if (ColorHelper.isLightColor(accentColor)) 
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS 
                    else 0,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            }
            // Set color using WindowCompat (works on all API levels)
            @Suppress("DEPRECATION")
            window.navigationBarColor = accentColor
            
            // Handle Icon Contrast using WindowCompat (more compatible)
            val isLight = ColorHelper.isLightColor(accentColor)
            val wic = WindowCompat.getInsetsController(window, window.decorView)
            wic.isAppearanceLightNavigationBars = isLight
        }
    }
}
