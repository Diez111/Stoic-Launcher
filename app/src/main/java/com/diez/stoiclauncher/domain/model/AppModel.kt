package com.diez.stoiclauncher.domain.model

import android.graphics.drawable.Drawable

data class AppModel(
    val label: String,
    val packageName: String,
    val icon: Drawable?,
    val user: android.os.UserHandle? = null,
    val isSystemApp: Boolean = false,
    val groupId: String? = null,
    val isGroup: Boolean = false,
    val category: String? = null
) {
    // Unique ID for persistence (e.g. "package|userId")
    // SIMPLIFIED: Just use packageName to fix migration issues. 
    // We can add multi-user support later if needed by appending user ID, NOT hashCode.
    val uniqueId: String
        get() = packageName // if (user != null) "$packageName|${user.hashCode()}" else packageName
}
