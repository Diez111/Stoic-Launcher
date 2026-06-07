package com.diez.stoiclauncher.presentation.util

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorHelperTest {

    @Test
    fun `isLightColor returns true for white`() {
        assertTrue(ColorHelper.isLightColor(Color.WHITE))
    }

    @Test
    fun `isLightColor returns false for black`() {
        assertFalse(ColorHelper.isLightColor(Color.BLACK))
    }

    @Test
    fun `isLightColor returns true for light gray`() {
        assertTrue(ColorHelper.isLightColor(Color.LTGRAY))
    }

    @Test
    fun `isLightColor returns false for dark gray`() {
        assertFalse(ColorHelper.isLightColor(Color.DKGRAY))
    }

    @Test
    fun `isLightColor returns true for yellow`() {
        assertTrue(ColorHelper.isLightColor(Color.YELLOW))
    }

    @Test
    fun `isLightColor returns false for blue`() {
        assertFalse(ColorHelper.isLightColor(Color.BLUE))
    }

    @Test
    fun `getTextColorForAccent returns black for light background`() {
        assertEquals(Color.BLACK, ColorHelper.getTextColorForAccent(Color.WHITE))
    }

    @Test
    fun `getTextColorForAccent returns white for dark background`() {
        assertEquals(Color.WHITE, ColorHelper.getTextColorForAccent(Color.BLACK))
    }

    @Test
    fun `getTextColorForAccent returns white when wallpaper enabled`() {
        assertEquals(Color.WHITE, ColorHelper.getTextColorForAccent(Color.WHITE, isWallpaperEnabled = true))
    }

    @Test
    fun `getSecondaryTextColorForAccent returns darker shade for light background`() {
        val secondary = ColorHelper.getSecondaryTextColorForAccent(Color.WHITE)
        assertEquals(0x99000000.toInt(), secondary)
    }

    @Test
    fun `getSecondaryTextColorForAccent returns lighter shade for dark background`() {
        val secondary = ColorHelper.getSecondaryTextColorForAccent(Color.BLACK)
        assertEquals(0xB3FFFFFF.toInt(), secondary)
    }

    @Test
    fun `getContrastColor returns black for light background`() {
        assertEquals(Color.BLACK, ColorHelper.getContrastColor(Color.WHITE))
    }

    @Test
    fun `getContrastColor returns white for dark background`() {
        assertEquals(Color.WHITE, ColorHelper.getContrastColor(Color.BLACK))
    }
}
