package com.diez.stoiclauncher.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents the persisted configuration of a widget
 */
@Serializable
data class WidgetConfig(
    val widgetId: Int,
    val provider: String,  // ComponentName as string (e.g., "com.google.android.calendar/...")
    val x: Float,          // Position X in pixels
    val y: Float,          // Position Y in pixels
    val width: Int,        // Width in pixels
    val height: Int,       // Height in pixels
    val page: Int = 1      // Which page: 0=apps, 1=widgets, 2=settings
)
