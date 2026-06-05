package com.diez.stoiclauncher.domain.util

import android.content.pm.ApplicationInfo
import android.os.Build
import com.diez.stoiclauncher.domain.model.AppModel

object AppCategorizer {

    fun getCategory(appInfo: ApplicationInfo): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (appInfo.category) {
                ApplicationInfo.CATEGORY_AUDIO -> return "Entretenimiento"
                ApplicationInfo.CATEGORY_GAME -> return "Entretenimiento"
                ApplicationInfo.CATEGORY_IMAGE -> return "Entretenimiento"
                ApplicationInfo.CATEGORY_MAPS -> return "Finanzas"
                ApplicationInfo.CATEGORY_NEWS -> return "Trabajo"
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> return "Trabajo"
                ApplicationInfo.CATEGORY_SOCIAL -> return "Social"
                ApplicationInfo.CATEGORY_VIDEO -> return "Entretenimiento"
            }
        }
        return heuristicCategory(appInfo.packageName)
    }

    private fun heuristicCategory(pkg: String): String {
        val p = pkg.lowercase()
        return when {
            // SOCIAL
            p.contains("whatsapp") || p.contains("instagram") || p.contains("facebook") ||
            p.contains("twitter") || p.contains("telegram") || p.contains("discord") ||
            p.contains("reddit") || p.contains("pinterest") || p.contains("linkedin") ||
            p.contains("tiktok") || p.contains("snapchat") || p.contains("zhiliaoapp") ||
            p.contains("messenger") || p.contains("badoo") || p.contains("plato") ||
            p.contains("crunchyroll") -> "Social"

            // FINANZAS (Banking, Investment, Shopping, Payments, Transport)
            p.contains("bank") || p.contains("wallet") || p.contains("finance") ||
            p.contains("money") || p.contains("pay") || p.contains("banc") ||
            p.contains("uala") || p.contains("cocos") || p.contains("nexo") ||
            p.contains("iol.") || p.contains("naranja") || p.contains("paretopago") ||
            p.contains("rebanking") || p.contains("mercado") || p.contains("buepp") ||
            p.contains("sube") || p.contains("ypf") || p.contains("tradingview") ||
            p.contains("binance") || p.contains("bingbon") || p.contains("payoneer") ||
            p.contains("galeno") || p.contains("tarjeta") || p.contains("shop") ||
            p.contains("amazon") || p.contains("aliexpress") || p.contains("supermercado") ||
            p.contains("dia.") || p.contains("map") || p.contains("waze") ||
            p.contains("uber") || p.contains("pedidosya") || p.contains("rappi") ||
            p.contains("temu") || p.contains("carrefour") -> "Finanzas"

            // ENTRETENIMIENTO (Audio, Video, Photos, Games)
            p.contains("spotify") || p.contains("music") || p.contains("shazam") ||
            p.contains("youtube") || p.contains("newpipe") || p.contains("sound") ||
            p.contains("volume") || p.contains("boost") || p.contains("camera") ||
            p.contains("gallery") || p.contains("photo") || p.contains("scan") ||
            p.contains("media") || p.contains("fashion") || p.contains("disney") ||
            p.contains("netflix") || p.contains("prime") || p.contains("hbo") ||
            p.contains("twitch") || p.contains("game") || p.contains("chess") ||
            p.contains("odyssey") || p.contains("civilizations") || p.contains("play.games") -> "Entretenimiento"

            // TRABAJO (Productivity, Education, AI, Browsers)
            p.contains("calendar") || p.contains("keep") || p.contains("tasks") ||
            p.contains("doc") || p.contains("sheet") || p.contains("slide") ||
            p.contains("office") || p.contains("wps") || p.contains("onlyoffice") ||
            p.contains("note") || p.contains("authenticator") || p.contains("protonmail") ||
            p.contains("compute") || p.contains("drive") || p.contains("files") ||
            p.contains("contact") || p.contains("azure") || p.contains("mail") ||
            p.contains("geogebra") || p.contains("mathway") || p.contains("math") ||
            p.contains("forest") || p.contains("duolingo") || p.contains("course") ||
            p.contains("learn") || p.contains("interest_calc") || p.contains("chatgpt") ||
            p.contains("claude") || p.contains("bard") || p.contains("gemini") ||
            p.contains("copilot") || p.contains("github") || p.contains("localsend") ||
            p.contains("apkinstaller") || p.contains("wallbit") || p.contains("tailwind") ||
            p.contains("safetycore") || p.contains("microsoft") || p.contains("chrome") ||
            p.contains("firefox") || p.contains("browser") || p.contains("brave") ||
            p.contains("opera") || p.contains("news") -> "Trabajo"

            // SISTEMA (Settings, Utilities, Health, System)
            p.contains("settings") || p.contains("systemui") || p.contains("launcher") ||
            p.contains("tool") || p.contains("ruler") || p.contains("smartlife") ||
            p.contains("chromecast") || p.contains("tuya") || p.contains("screenrecorder") ||
            p.contains("calculator") || p.contains("health") || p.contains("fitness") ||
            p.contains("wellbeing") || p.contains("step") || p.contains("run") ||
            p.contains("packageinstaller") || p.contains("permissioncontroller") ||
            p.contains("cellbroadcast") || p.contains("emergency") || p.contains("webview") ||
            p.contains("speech") || p.contains("inputmethod") || p.contains("companion") ||
            p.contains("traceur") || p.contains("documentsui") || p.contains("contacts") ||
            p.contains("phone") || p.contains("dialer") || p.contains("messaging") ||
            p.contains("sms") || p.contains("clock") || p.contains("calendar") ||
            p.contains("camera") || p.contains("gallery") || p.contains("file") ||
            p.contains("music") || p.contains("video") || p.contains("browser") ||
            p.contains("email") || p.contains("calculator") || p.contains("sound") ||
            p.contains("display") || p.contains("storage") || p.contains("battery") ||
            p.contains("wifi") || p.contains("bluetooth") || p.contains("location") ||
            p.contains("security") || p.contains("backup") || p.contains("update") ||
            p.contains("feedback") || p.contains("setup") || p.contains("account") ||
            p.contains("sync") || p.contains("nfc") || p.contains("print") ||
            p.contains("vpn") || p.contains("developer") || p.contains("accessibility") ||
            p.contains("recovery") || p.contains("framework") || p.contains("shell") ||
            p.contains("smn") || p.contains("weather") || p.contains("clima") ||
            p.contains("android") || p.contains("google") || p.contains("com.miui") ||
            p.contains("com.xiaomi") || p.contains("com.android") || p.contains("com.google") -> "Sistema"

            else -> "Otros"
        }
    }

    fun groupByCategory(apps: List<AppModel>): List<com.diez.stoiclauncher.presentation.home.fragments.CategoryGroup> {
        val grouped = linkedMapOf<String, MutableList<AppModel>>()
        for (app in apps) {
            val cat = app.category ?: "Otros"
            grouped.getOrPut(cat) { mutableListOf() }.add(app)
        }
        return grouped.map { (cat, list) ->
            com.diez.stoiclauncher.presentation.home.fragments.CategoryGroup(cat, list.sortedBy { it.label.lowercase() })
        }.sortedByDescending { it.apps.size }
    }
}
