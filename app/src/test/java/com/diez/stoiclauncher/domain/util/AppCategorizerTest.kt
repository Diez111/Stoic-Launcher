package com.diez.stoiclauncher.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCategorizerTest {

    @Test
    fun `heuristicCategory returns Social for whatsapp`() {
        val result = invokeHeuristic("com.whatsapp")
        assertEquals("Social", result)
    }

    @Test
    fun `heuristic category returns Social for instagram`() {
        val result = invokeHeuristic("com.instagram.android")
        assertEquals("Social", result)
    }

    @Test
    fun `heuristic category returns Finanzas for banking app`() {
        val result = invokeHeuristic("com.bank.app")
        assertEquals("Finanzas", result)
    }

    @Test
    fun `heuristic category returns Finanzas for mercadopago`() {
        val result = invokeHeuristic("com.mercadopago.wallet")
        assertEquals("Finanzas", result)
    }

    @Test
    fun `heuristic category returns Entretenimiento for spotify`() {
        val result = invokeHeuristic("com.spotify.music")
        assertEquals("Entretenimiento", result)
    }

    @Test
    fun `heuristic category returns Entretenimiento for netflix`() {
        val result = invokeHeuristic("com.netflix.mediaclient")
        assertEquals("Entretenimiento", result)
    }

    @Test
    fun `heuristic category returns Trabajo for chrome`() {
        val result = invokeHeuristic("com.android.chrome")
        assertEquals("Trabajo", result)
    }

    @Test
    fun `heuristic category returns Trabajo for github`() {
        val result = invokeHeuristic("com.github.android")
        assertEquals("Trabajo", result)
    }

    @Test
    fun `heuristic category returns Sistema for settings`() {
        val result = invokeHeuristic("com.android.settings")
        assertEquals("Sistema", result)
    }

    @Test
    fun `heuristic category returns Otros for unknown app`() {
        val result = invokeHeuristic("com.unknown.randomapp123")
        assertEquals("Otros", result)
    }

    @Test
    fun `social category takes priority over finances`() {
        val result = invokeHeuristic("com.social.bank")
        assertEquals("Social", result)
    }

    private fun invokeHeuristic(packageName: String): String {
        val method = AppCategorizer::class.java.getDeclaredMethod("heuristicCategory", String::class.java)
        method.isAccessible = true
        return method.invoke(AppCategorizer, packageName) as String
    }
}
