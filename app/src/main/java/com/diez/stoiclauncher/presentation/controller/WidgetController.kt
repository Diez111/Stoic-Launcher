package com.diez.stoiclauncher.presentation.controller

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.diez.stoiclauncher.StoicApplication
import com.diez.stoiclauncher.domain.model.WidgetConfig
import com.diez.stoiclauncher.presentation.widget.WidgetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Controller responsible for all Widget-related interactions.
 * Acts as a Microservice for the UI layer, decoupling Widget logic from MainActivity.
 */
class WidgetController(
    private val activity: AppCompatActivity,
    private val hostId: Int = 1024
) {

    // Internal Manager handling low-level AppWidgetHost references
    private val widgetManager = WidgetManager(activity, hostId)
    private var pendingWidgetId: Int? = null
    
    // UI State
    private var currentContainer: ViewGroup? = null
    
    fun onStart() {
        widgetManager.startListening()
    }
    
    fun onStop() {
        widgetManager.stopListening()
    }
    
    fun handleBackPress(): Boolean {
        return widgetManager.handleBackPress()
    }
    
    fun attachContainer(container: ViewGroup) {
        this.currentContainer = container
    }
    
    fun requestAddWidget() {
        try {
            widgetManager.showCustomWidgetPicker { widget, widgetId ->
                handleWidgetSelection(widget, widgetId)
            }
        } catch (e: Exception) {
            android.util.Log.e("WidgetController", "Error requesting add widget", e)
        }
    }
    
    private fun handleWidgetSelection(widget: AppWidgetProviderInfo, widgetId: Int) {
        if (widgetManager.hasBindPermission(widgetId, widget.provider)) {
            configureOrAddWidget(widget, widgetId)
        } else {
            // Request Permission
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, widget.provider)
            pendingWidgetId = widgetId
            activity.startActivityForResult(intent, REQUEST_BIND_APPWIDGET)
        }
    }
    
    private fun configureOrAddWidget(widget: AppWidgetProviderInfo, widgetId: Int) {
         if (widget.configure != null) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            intent.component = widget.configure
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            pendingWidgetId = widgetId
            activity.startActivityForResult(intent, REQUEST_CREATE_APPWIDGET)
         } else {
             addWidgetToContainer(widgetId)
         }
    }
    
    private fun addWidgetToContainer(widgetId: Int) {
        currentContainer?.let { container ->
            widgetManager.addWidgetToHost(widgetId, container)
        } ?: run {
             // If container is null, maybe the fragment isn't ready?
             android.util.Log.e("WidgetController", "Container is null! Cannot add widget $widgetId")
        }
    }
    
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
             when (requestCode) {
                 REQUEST_BIND_APPWIDGET -> {
                     val widgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: pendingWidgetId ?: -1
                     if (widgetId != -1) {
                         val info = AppWidgetManager.getInstance(activity).getAppWidgetInfo(widgetId)
                         configureOrAddWidget(info, widgetId)
                     }
                 }
                 REQUEST_CREATE_APPWIDGET -> {
                     val widgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: pendingWidgetId ?: -1
                     if (widgetId != -1) {
                         addWidgetToContainer(widgetId)
                     }
                 }
             }
        } else if (resultCode == Activity.RESULT_CANCELED) {
             // Cleanup
             val widgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: pendingWidgetId ?: -1
             if (widgetId != -1) {
                 widgetManager.deleteWidgetId(widgetId)
             }
        }
        pendingWidgetId = null
    }
    
    fun restoreWidgets(configs: List<WidgetConfig>) {
        currentContainer?.let { container ->
            widgetManager.restoreWidgets(container, configs)
        }
    }
    
    fun refreshThemes(isLight: Boolean) {
        currentContainer?.let { container ->
            widgetManager.refreshWidgetThemes(isLight, container)
        }
    }

    companion object {
        const val REQUEST_BIND_APPWIDGET = 100
        const val REQUEST_PICK_APPWIDGET = 101 // Unused internal picker
        const val REQUEST_CREATE_APPWIDGET = 102
    }
}
