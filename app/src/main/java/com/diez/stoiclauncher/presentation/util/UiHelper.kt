package com.diez.stoiclauncher.presentation.util

import android.app.Dialog
import android.graphics.Color
import android.os.Build
import android.view.View
import androidx.core.view.WindowCompat
import com.google.android.material.bottomsheet.BottomSheetDialog

object UiHelper {

    fun setupBottomSheetColor(dialog: Dialog, view: View, accentColor: Int) {
        val bgDrawable = androidx.core.content.ContextCompat.getDrawable(
            view.context, com.diez.stoiclauncher.R.drawable.shape_bottom_sheet
        )?.mutate() ?: return

        val isLight = ColorHelper.isLightColor(accentColor)
        val sheetColor = if (isLight) Color.parseColor("#1A1A1A") else accentColor

        androidx.core.graphics.drawable.DrawableCompat.setTint(bgDrawable, sheetColor)
        view.background = bgDrawable

        val window = dialog.window ?: return

        @Suppress("DEPRECATION")
        window.navigationBarColor = sheetColor

        val wic = WindowCompat.getInsetsController(window, window.decorView)
        wic.isAppearanceLightNavigationBars = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(0, 0)
        }
    }
}
