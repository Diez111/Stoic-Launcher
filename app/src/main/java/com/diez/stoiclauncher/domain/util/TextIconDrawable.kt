package com.diez.stoiclauncher.domain.util

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

class TextIconDrawable(private val letter: String) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        // Use a thin/light typeface if possible
        typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        // Scale text size relative to bounds
        paint.textSize = bounds.height() * 0.6f
        
        val x = bounds.width() / 2f
        val y = bounds.height() / 2f - (paint.descent() + paint.ascent()) / 2f
        
        canvas.drawText(letter.uppercase(), x, y, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
