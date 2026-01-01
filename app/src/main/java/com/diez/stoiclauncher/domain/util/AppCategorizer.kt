package com.diez.stoiclauncher.domain.util

import android.content.pm.ApplicationInfo
import android.os.Build

object AppCategorizer {
    
    fun getCategory(appInfo: ApplicationInfo): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (appInfo.category) {
                ApplicationInfo.CATEGORY_AUDIO -> return "Audio & Music"
                ApplicationInfo.CATEGORY_GAME -> return "Games"
                ApplicationInfo.CATEGORY_IMAGE -> return "Photos & Video"
                ApplicationInfo.CATEGORY_MAPS -> return "Maps & Navigation"
                ApplicationInfo.CATEGORY_NEWS -> return "News & Magazines"
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> return "Productivity"
                ApplicationInfo.CATEGORY_SOCIAL -> return "Social"
                ApplicationInfo.CATEGORY_VIDEO -> return "Photos & Video"
                ApplicationInfo.CATEGORY_ACCESSIBILITY -> return "Utilities" 
                // UNDEFINED or others
            }
        }
        
        // Fallback Heuristics for common apps if undefined or old Android
        val pkg = appInfo.packageName.lowercase()
        return when {
            pkg.contains("calendar") || pkg.contains("note") || pkg.contains("doc") || pkg.contains("sheet") -> "Productivity"
            pkg.contains("facebook") || pkg.contains("twitter") || pkg.contains("instagram") || pkg.contains("discord") || pkg.contains("whatsapp") || pkg.contains("telegram") -> "Social"
            pkg.contains("finance") || pkg.contains("bank") || pkg.contains("wallet") || pkg.contains("money") -> "Finance"
            pkg.contains("camera") || pkg.contains("gallery") || pkg.contains("photo") -> "Photos & Video"
            pkg.contains("music") || pkg.contains("spotify") || pkg.contains("audio") -> "Audio & Music"
            pkg.contains("map") || pkg.contains("waze") || pkg.contains("uber") -> "Maps & Navigation"
            pkg.contains("game") -> "Games"
            pkg.contains("chrome") || pkg.contains("firefox") || pkg.contains("browser") -> "Web"
            pkg.contains("settings") || pkg.contains("tool") -> "Utilities"
            else -> "General"
        }
    }
}
