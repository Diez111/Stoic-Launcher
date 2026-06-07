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

    // Exclude icon from equals/hashCode to avoid issues with Drawables
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AppModel) return false
        return label == other.label && packageName == other.packageName &&
               user == other.user && isSystemApp == other.isSystemApp &&
               groupId == other.groupId && isGroup == other.isGroup &&
               category == other.category
    }

    override fun hashCode(): Int {
        var result = label.hashCode()
        result = 31 * result + packageName.hashCode()
        result = 31 * result + (user?.hashCode() ?: 0)
        result = 31 * result + isSystemApp.hashCode()
        result = 31 * result + (groupId?.hashCode() ?: 0)
        result = 31 * result + isGroup.hashCode()
        result = 31 * result + (category?.hashCode() ?: 0)
        return result
    }
}
