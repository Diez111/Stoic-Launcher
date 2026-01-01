package com.diez.stoiclauncher.presentation.util

import android.graphics.Color
import androidx.core.graphics.ColorUtils

object ColorHelper {
    /**
     * Threshold basado en WCAG 2.0 Level AA (ratio 4.5:1)
     * Colores con luminancia > 0.179 requieren texto oscuro
     * Colores con luminancia < 0.179 requieren texto claro
     */
    private const val LUMINANCE_THRESHOLD = 0.179
    
    /**
     * Mapeo EXPLÍCITO de cada color de acento a su color de texto correcto
     * Usando Map para O(1) lookup en lugar de when expression
     */
    private val ACCENT_TO_TEXT_COLOR_MAP = mapOf(
        // Temas OSCUROS → Texto BLANCO
        0xFF000000.toInt() to Color.WHITE,  // Ónix (#000000)
        0xFF1565C0.toInt() to Color.WHITE,  // Océano (#1565C0)
        
        // Temas CLAROS → Texto NEGRO
        0xFFEFEBE9.toInt() to Color.BLACK,  // Abedul (#EFEBE9)
        0xFFB0BEC5.toInt() to Color.BLACK,  // Ceniza (#B0BEC5)
        0xFFECC15F.toInt() to Color.BLACK,  // Ronchi (#ECC15F)
        0xFF4DB6AC.toInt() to Color.BLACK,  // Galápagos (#4DB6AC)
        0xFF9575CD.toInt() to Color.BLACK,  // Lavanda (#9575CD)
        0xFFFFB7C5.toInt() to Color.BLACK,  // Sakura (#FFB7C5)
        0xFF81A1C1.toInt() to Color.BLACK,  // Nórdico (#81A1C1)
        0xFFA5D6A7.toInt() to Color.BLACK,  // Matcha (#A5D6A7)
        0xFFFFCA28.toInt() to Color.BLACK   // Ámbar (#FFCA28)
    )
    
    fun getTextColorForAccent(accentColor: Int): Int {
        // O(1) lookup en Map, fallback a cálculo de luminancia
        return ACCENT_TO_TEXT_COLOR_MAP[accentColor]
            ?: if (isLightColor(accentColor)) Color.BLACK else Color.WHITE
    }
    
    /**
     * Versión semi-transparente del color de contraste para textos secundarios
     */
    fun getSecondaryTextColorForAccent(accentColor: Int): Int {
        val primaryTextColor = getTextColorForAccent(accentColor)
        return if (primaryTextColor == Color.BLACK) {
            0x99000000.toInt()  // Negro semi-transparente
        } else {
            0x99FFFFFF.toInt()  // Blanco semi-transparente
        }
    }
    
    fun getContrastColor(backgroundColor: Int): Int {
        val luminance = ColorUtils.calculateLuminance(backgroundColor)
        // Si el fondo es claro (> threshold), usar texto Negro. Si es oscuro, usar Blanco.
        return if (luminance > LUMINANCE_THRESHOLD) Color.BLACK else Color.WHITE
    }
    
    fun getSecondaryContrastColor(backgroundColor: Int): Int {
         val luminance = ColorUtils.calculateLuminance(backgroundColor)
         // Versión semi-transparente del color de contraste
         return if (luminance > LUMINANCE_THRESHOLD) 0x99000000.toInt() else 0x99FFFFFF.toInt()
    }
    
    fun isLightColor(color: Int): Boolean {
        return ColorUtils.calculateLuminance(color) > LUMINANCE_THRESHOLD
    }

    suspend fun isBitmapLight(bitmap: android.graphics.Bitmap): Boolean {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
             val palette = androidx.palette.graphics.Palette.from(bitmap).generate()
             // Check dominant or average color
             val dominantColor = palette.getDominantColor(android.graphics.Color.BLACK)
             isLightColor(dominantColor)
        }
    }
}
