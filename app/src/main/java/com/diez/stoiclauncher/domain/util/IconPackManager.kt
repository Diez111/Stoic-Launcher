package com.diez.stoiclauncher.domain.util

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import org.xmlpull.v1.XmlPullParser

class IconPackManager(private val context: Context) {

    companion object {
        private const val TAG = "IconPackManager"
        const val PACK_STOIC_BUILTIN = "stoic_builtin"
        const val PACK_STOIC_MINIMAL = "stoic_minimal"
    }

    private var currentIconPack: String? = null
    private var externalPackResources: android.content.res.Resources? = null
    private val externalIconMap = mutableMapOf<String, String>()
    private val builtinIconMap = mutableMapOf<String, String>()
    private var builtinReady = false

    val isStoicMinimal: Boolean get() = currentIconPack == PACK_STOIC_MINIMAL
    val isStoicBuiltin: Boolean get() = currentIconPack == PACK_STOIC_BUILTIN

    init {
        loadBuiltinPackSync()
    }

    private fun loadBuiltinPackSync() {
        try {
            val res = context.resources
            val parser = res.getXml(com.diez.stoiclauncher.R.xml.appfilter)
            var count = 0
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                    val pkg = parser.getAttributeValue(null, "package")
                    val drawable = parser.getAttributeValue(null, "drawable")
                    if (pkg != null && drawable != null) {
                        builtinIconMap[pkg] = drawable
                        count++
                    }
                }
                eventType = parser.next()
            }
            builtinReady = true
            Log.d(TAG, "Built-in pack loaded: $count icons mapped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load built-in pack", e)
            builtinReady = false
        }
    }

    fun setIconPack(packageName: String?) {
        Log.d(TAG, "setIconPack: $packageName (current=$currentIconPack, builtinReady=$builtinReady)")
        if (currentIconPack == packageName) return
        currentIconPack = packageName
        externalIconMap.clear()
        externalPackResources = null

        if (packageName.isNullOrEmpty() || packageName == PACK_STOIC_MINIMAL || packageName == PACK_STOIC_BUILTIN) return

        try {
            val pm = context.packageManager
            externalPackResources = pm.getResourcesForApplication(packageName)
            val resId = externalPackResources?.getIdentifier("appfilter", "xml", packageName) ?: 0
            if (resId != 0) {
                parseExternalAppFilter(externalPackResources!!.getXml(resId))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set icon pack: $packageName", e)
            currentIconPack = null
            externalPackResources = null
        }
    }

    private fun parseExternalAppFilter(parser: XmlPullParser) {
        try {
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                    val component = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")
                    if (component != null && drawable != null && component.startsWith("ComponentInfo{") && component.endsWith("}")) {
                        val cmp = component.substring(14, component.length - 1)
                        externalIconMap[cmp] = drawable
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse appfilter", e)
        }
    }

    fun getIcon(componentName: ComponentName): Drawable? {
        val pkg = componentName.packageName
        
        // Built-in pack — only when stoic_builtin is selected
        if (isStoicBuiltin && builtinReady && builtinIconMap.isNotEmpty()) {
            val drawableName = builtinIconMap[pkg]
            if (drawableName != null) {
                val resId = context.resources.getIdentifier(drawableName, "drawable", context.packageName)
                if (resId != 0) {
                    try {
                        Log.d(TAG, "Builtin match: $pkg -> $drawableName")
                        return context.resources.getDrawable(resId, null)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to get drawable $drawableName for $pkg", e)
                    }
                }
            }
            // Not in builtin map → return null so AppRepositoryImpl falls back to system icon
            Log.d(TAG, "No builtin match for $pkg")
            return null
        }

        // External icon pack
        val packRes = externalPackResources ?: return null
        val packPkg = currentIconPack ?: return null
        val cmpString = "${componentName.packageName}/${componentName.className}"
        var drawableName = externalIconMap[cmpString]
        if (drawableName == null) {
            drawableName = externalIconMap.entries.firstOrNull { it.key.startsWith("${componentName.packageName}/") }?.value
        }
        if (drawableName != null) {
            val resId = packRes.getIdentifier(drawableName, "drawable", packPkg)
            if (resId != 0) {
                try {
                    return packRes.getDrawable(resId, null)
                } catch (e: Exception) {}
            }
        }
        return null
    }
}
