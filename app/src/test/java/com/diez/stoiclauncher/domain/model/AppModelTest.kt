package com.diez.stoiclauncher.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppModelTest {

    @Test
    fun `uniqueId returns packageName`() {
        val app = AppModel(
            label = "Test App",
            packageName = "com.test.app",
            icon = null
        )
        assertEquals("com.test.app", app.uniqueId)
    }

    @Test
    fun `uniqueId is same for apps with different labels but same package`() {
        val app1 = AppModel(label = "App v1", packageName = "com.test.app", icon = null)
        val app2 = AppModel(label = "App v2", packageName = "com.test.app", icon = null)
        assertEquals(app1.uniqueId, app2.uniqueId)
    }

    @Test
    fun `default values are correct`() {
        val app = AppModel(label = "Test", packageName = "com.test", icon = null)
        assertNull(app.user)
        assertFalse(app.isSystemApp)
        assertNull(app.groupId)
        assertFalse(app.isGroup)
        assertNull(app.category)
    }

    @Test
    fun `group app has correct properties`() {
        val group = AppModel(
            label = "[ Work ]",
            packageName = "group:work",
            icon = null,
            isGroup = true,
            groupId = "work"
        )
        assertTrue(group.isGroup)
        assertEquals("work", group.groupId)
    }

    @Test
    fun `copy preserves uniqueId when packageName unchanged`() {
        val original = AppModel(label = "Original", packageName = "com.test", icon = null)
        val copy = original.copy(label = "Modified")
        assertEquals(original.uniqueId, copy.uniqueId)
    }

    @Test
    fun `copy changes uniqueId when packageName changed`() {
        val original = AppModel(label = "App", packageName = "com.test1", icon = null)
        val copy = original.copy(packageName = "com.test2")
        assertEquals("com.test2", copy.uniqueId)
    }
}
