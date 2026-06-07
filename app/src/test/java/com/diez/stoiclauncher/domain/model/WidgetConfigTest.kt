package com.diez.stoiclauncher.domain.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetConfigTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `default page is 0`() {
        val config = WidgetConfig(
            widgetId = 1,
            provider = "com.test/com.test.Widget",
            x = 0f,
            y = 0f,
            width = 100,
            height = 100
        )
        assertEquals(0, config.page)
    }

    @Test
    fun `serialization roundtrip preserves data`() {
        val original = WidgetConfig(
            widgetId = 42,
            provider = "com.google.android.calendar/com.android.calendar widget",
            x = 10.5f,
            y = 20.3f,
            width = 300,
            height = 400,
            page = 0
        )
        val jsonString = json.encodeToString(original)
        val deserialized = json.decodeFromString<WidgetConfig>(jsonString)
        assertEquals(original, deserialized)
    }

    @Test
    fun `serialization with custom page`() {
        val config = WidgetConfig(
            widgetId = 1,
            provider = "test",
            x = 0f,
            y = 0f,
            width = 100,
            height = 100,
            page = 1
        )
        val jsonString = json.encodeToString(config)
        val deserialized = json.decodeFromString<WidgetConfig>(jsonString)
        assertEquals(1, deserialized.page)
    }

    @Test
    fun `negative coordinates are preserved`() {
        val config = WidgetConfig(
            widgetId = 1,
            provider = "test",
            x = -10.5f,
            y = -20.3f,
            width = 100,
            height = 100
        )
        val jsonString = json.encodeToString(config)
        val deserialized = json.decodeFromString<WidgetConfig>(jsonString)
        assertEquals(-10.5f, deserialized.x, 0.01f)
        assertEquals(-20.3f, deserialized.y, 0.01f)
    }
}
