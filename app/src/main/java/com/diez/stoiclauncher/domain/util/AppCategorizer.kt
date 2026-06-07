package com.diez.stoiclauncher.domain.util

import android.content.pm.ApplicationInfo
import android.os.Build
import com.diez.stoiclauncher.domain.model.AppModel
import com.diez.stoiclauncher.domain.model.CategoryGroup

object AppCategorizer {

    private const val CAT_SOCIAL = "Social"
    private const val CAT_FINANZAS = "Finanzas"
    private const val CAT_ENTRETENIMIENTO = "Entretenimiento"
    private const val CAT_TRABAJO = "Trabajo"
    private const val CAT_SISTEMA = "Sistema"
    private const val CAT_OTROS = "Otros"

    private val CATEGORY_PATTERNS: List<Pair<String, List<String>>> = listOf(
        CAT_SOCIAL to listOf(
            "whatsapp", "instagram", "facebook", "twitter", "telegram", "discord",
            "reddit", "pinterest", "linkedin", "tiktok", "snapchat", "zhiliaoapp",
            "messenger", "badoo", "plato", "crunchyroll"
        ),
        CAT_FINANZAS to listOf(
            "bank", "wallet", "finance", "money", "pay", "banc",
            "uala", "cocos", "nexo", "iol.", "naranja", "paretopago",
            "rebanking", "mercado", "buepp", "sube", "ypf", "tradingview",
            "binance", "bingbon", "payoneer", "galeno", "tarjeta",
            "amazon", "aliexpress", "supermercado", "dia.", "map", "waze",
            "uber", "pedidosya", "rappi", "temu", "carrefour"
        ),
        CAT_ENTRETENIMIENTO to listOf(
            "spotify", "music", "shazam", "youtube", "newpipe", "sound",
            "volume", "boost", "camera", "gallery", "photo", "scan",
            "media", "fashion", "disney", "netflix", "prime", "hbo",
            "twitch", "game", "chess", "odyssey", "civilizations", "play.games"
        ),
        CAT_TRABAJO to listOf(
            "calendar", "keep", "tasks", "doc", "sheet", "slide",
            "office", "wps", "onlyoffice", "note", "authenticator", "protonmail",
            "compute", "drive", "files", "contact", "azure", "mail",
            "geogebra", "mathway", "math", "forest", "duolingo", "course",
            "learn", "interest_calc", "chatgpt", "claude", "bard", "gemini",
            "copilot", "github", "localsend", "apkinstaller", "wallbit", "tailwind",
            "safetycore", "microsoft", "chrome", "firefox", "browser", "brave",
            "opera", "news"
        ),
        CAT_SISTEMA to listOf(
            "settings", "systemui", "launcher", "tool", "ruler", "smartlife",
            "chromecast", "tuya", "screenrecorder", "calculator", "health", "fitness",
            "wellbeing", "step", "run", "packageinstaller", "permissioncontroller",
            "cellbroadcast", "emergency", "webview", "speech", "inputmethod", "companion",
            "traceur", "documentsui", "phone", "dialer", "messaging", "sms", "clock",
            "com.miui", "com.xiaomi", "com.android", "com.google"
        )
    )

    fun getCategory(appInfo: ApplicationInfo): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (appInfo.category) {
                ApplicationInfo.CATEGORY_AUDIO -> return CAT_ENTRETENIMIENTO
                ApplicationInfo.CATEGORY_GAME -> return CAT_ENTRETENIMIENTO
                ApplicationInfo.CATEGORY_IMAGE -> return CAT_ENTRETENIMIENTO
                ApplicationInfo.CATEGORY_MAPS -> return CAT_FINANZAS
                ApplicationInfo.CATEGORY_NEWS -> return CAT_TRABAJO
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> return CAT_TRABAJO
                ApplicationInfo.CATEGORY_SOCIAL -> return CAT_SOCIAL
                ApplicationInfo.CATEGORY_VIDEO -> return CAT_ENTRETENIMIENTO
            }
        }
        return heuristicCategory(appInfo.packageName)
    }

    private fun heuristicCategory(pkg: String): String {
        val p = pkg.lowercase()
        for ((category, patterns) in CATEGORY_PATTERNS) {
            for (pattern in patterns) {
                if (p.contains(pattern)) return category
            }
        }
        return CAT_OTROS
    }

    fun groupByCategory(apps: List<AppModel>): List<CategoryGroup> {
        val grouped = linkedMapOf<String, MutableList<AppModel>>()
        for (app in apps) {
            val cat = app.category ?: CAT_OTROS
            grouped.getOrPut(cat) { mutableListOf() }.add(app)
        }
        return grouped.map { (cat, list) ->
            CategoryGroup(cat, list.sortedBy { it.label.lowercase() })
        }.sortedByDescending { it.apps.size }
    }
}
