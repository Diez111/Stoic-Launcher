package com.diez.stoiclauncher.presentation.widget

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.diez.stoiclauncher.StoicApplication
import com.diez.stoiclauncher.domain.model.WidgetConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetManager(
    private val activity: AppCompatActivity,
    private val hostId: Int = 1024
) {
    var isLightMode: Boolean = false

    // Use Activity Context for proper widget lifecycle and updates
    // Widgets need the Activity context to receive proper updates and callbacks
    private val appWidgetManager = AppWidgetManager.getInstance(activity)
    private val appWidgetHost = AppWidgetHost(activity, hostId)
    private val settingsRepository = (activity.application as StoicApplication).container.settingsRepository
    
    // Callback to exit edit mode (Resize/Move) from Back Press
    private var exitEditModeCallback: (() -> Unit)? = null
    
    fun handleBackPress(): Boolean {
        exitEditModeCallback?.let {
            it.invoke()
            return true
        }
        return false
    }
    
    private fun disableViewPagerSwipe() {
        // Find ViewPager and disable user input
        val viewPager = activity.findViewById<ViewPager2>(com.diez.stoiclauncher.R.id.view_pager)
        viewPager?.isUserInputEnabled = false
    }
    
    private fun enableViewPagerSwipe() {
        // Re-enable ViewPager
        val viewPager = activity.findViewById<ViewPager2>(com.diez.stoiclauncher.R.id.view_pager)
        viewPager?.isUserInputEnabled = true
    }

    fun startListening() {
        appWidgetHost.startListening()
    }

    fun stopListening() {
        appWidgetHost.stopListening()
    }
    
    fun deleteWidgetId(appWidgetId: Int) {
        appWidgetHost.deleteAppWidgetId(appWidgetId)
        // Also delete from persistence
        CoroutineScope(Dispatchers.IO).launch {
            try {
                settingsRepository.deleteWidgetConfig(appWidgetId)
                android.util.Log.d("WidgetManager", "Widget config deleted from persistence: $appWidgetId")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting widget config", e)
            }
        }
    }
    
    fun removeAllWidgets(container: ViewGroup? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            val configs = settingsRepository.getAllWidgetConfigs()
            configs.forEach { config ->
                appWidgetHost.deleteAppWidgetId(config.widgetId)
            }
            settingsRepository.clearAllWidgetConfigs()
            
            CoroutineScope(Dispatchers.Main).launch {
                container?.removeAllViews()
                android.widget.Toast.makeText(activity, "Todos los widgets eliminados", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun refreshWidgetThemes(isLight: Boolean, container: ViewGroup) {
        android.util.Log.d("WidgetManager", "refreshWidgetThemes called. isLight=$isLight, previous isLightMode=$isLightMode")
        
        // Si el modo cambió, necesitamos re-renderizar TODOS los widgets
        if (this.isLightMode != isLight) {
            android.util.Log.d("WidgetManager", "Mode changed! Re-rendering all widgets...")
            this.isLightMode = isLight
            
            // Forzar re-renderización completa de todos los widgets
            CoroutineScope(Dispatchers.IO).launch {
                val configs = settingsRepository.getAllWidgetConfigs()
                withContext(Dispatchers.Main) {
                    restoreWidgets(container, configs)
                }
            }
        } else {
            // Solo actualizar colores del wrapper si el modo no cambió
            android.util.Log.d("WidgetManager", "Mode unchanged. Only updating wrapper colors.")
            val density = activity.resources.displayMetrics.density
            val wrapperBgColor = if (isLightMode) 0x1A000000 else 0x1AFFFFFF
            
            for (i in 0 until container.childCount) {
                 val wrapper = container.getChildAt(i) as? android.widget.FrameLayout ?: continue
                 wrapper.background = android.graphics.drawable.GradientDrawable().apply {
                     setColor(wrapperBgColor)
                     cornerRadius = 16 * density
                 }
            }
        }
    }

    fun allocateWidgetId(): Int {
        return appWidgetHost.allocateAppWidgetId()
    }

    fun createWidgetPickIntent(widgetId: Int): Intent {
        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
        pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        
        // Optional: Filter for specific categories if needed, but default is fine here
        val allowedConfig = ArrayList<android.os.Bundle>()
        pickIntent.putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_INFO, allowedConfig)
        
        return pickIntent
    }
    
    
    fun showCustomWidgetPicker(onWidgetSelected: (AppWidgetProviderInfo, Int) -> Unit) {
        android.util.Log.d("WidgetManager", "========== showCustomWidgetPicker CALLED ==========")
        val widgetId = allocateWidgetId()
        android.util.Log.d("WidgetManager", "Allocated widget ID: $widgetId")
        
        // Get ALL installed widgets - don't filter by className to avoid missing widgets
        val installedWidgets = appWidgetManager.installedProviders
        
        android.util.Log.d("WidgetManager", "Total widgets found: ${installedWidgets.size}")
        
        // Sort alphabetically by label for better UX
        val sortedWidgets = installedWidgets.sortedBy { 
            it.loadLabel(activity.packageManager)?.toString() ?: ""
        }
        
        android.util.Log.d("WidgetManager", "Sorted widgets: ${sortedWidgets.size}")
        android.util.Log.d("WidgetManager", "========== WIDGET LIST START ==========")
        sortedWidgets.forEachIndexed { index, widget ->
            val label = widget.loadLabel(activity.packageManager)?.toString() ?: "NO LABEL"
            val provider = widget.provider
            android.util.Log.d("WidgetManager", "[$index] $label - $provider")
        }
        android.util.Log.d("WidgetManager", "========== WIDGET LIST END ==========")
        
        // Create bottom sheet dialog
        android.util.Log.d("WidgetManager", "Creating bottom sheet dialog...")
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(activity)
        val view = activity.layoutInflater.inflate(com.diez.stoiclauncher.R.layout.dialog_widget_picker, null)
        dialog.setContentView(view)
        
        // Make background transparent for rounded corners
        (view.parent as? android.view.View)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        
        // Adjust dialog window for keyboard - don't resize, use pan mode
        dialog.window?.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        )
        
        // Set max height to 80% of screen to leave room for keyboard
        val displayMetrics = activity.resources.displayMetrics
        val maxHeight = (displayMetrics.heightPixels * 0.8).toInt()
        view.layoutParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            this.height = maxHeight
        }
        
        
        val searchView = view.findViewById<androidx.appcompat.widget.SearchView>(com.diez.stoiclauncher.R.id.search_widgets)
        val recyclerView = view.findViewById<androidx.recyclerview.widget.RecyclerView>(com.diez.stoiclauncher.R.id.rv_widgets)
        
        android.util.Log.d("WidgetManager", "Setting up RecyclerView...")
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(activity)
        recyclerView.setHasFixedSize(true)
        
        android.util.Log.d("WidgetManager", "Creating adapter with ${sortedWidgets.size} widgets...")
        val adapter = com.diez.stoiclauncher.presentation.widget.WidgetPickerAdapter(activity, sortedWidgets.toMutableList()) { selectedWidget ->
            android.util.Log.d("WidgetManager", "Widget selected: ${selectedWidget.loadLabel(activity.packageManager)} - ${selectedWidget.provider}")
            dialog.dismiss()
            android.util.Log.d("WidgetManager", "Calling onWidgetSelected callback...")
            onWidgetSelected(selectedWidget, widgetId)
            android.util.Log.d("WidgetManager", "onWidgetSelected callback completed")
        }
        recyclerView.adapter = adapter
        android.util.Log.d("WidgetManager", "Adapter set on RecyclerView")
        
        // SearchView filter
        searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                // Dismiss keyboard on submit
                searchView.clearFocus()
                return true
            }
            
            override fun onQueryTextChange(newText: String?): Boolean {
                try {
                    adapter.filter(newText ?: "")
                } catch (e: Exception) {
                    Log.e(TAG, "Error filtering widgets", e)
                }
                return true
            }
        })
        
        // Clean up listener when dialog is dismissed
        dialog.setOnDismissListener {
            searchView.setOnQueryTextListener(null)
        }
        
        dialog.show()
    }
    
    fun hasBindPermission(appWidgetId: Int, provider: android.content.ComponentName): Boolean {
        return appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider)
    }
    
    fun addWidgetToHost(widgetId: Int, container: ViewGroup) {
        val appWidgetInfo = appWidgetManager.getAppWidgetInfo(widgetId)
        appWidgetInfo?.let { info ->
            try {
                val baseContext = activity.applicationContext
                val config = android.content.res.Configuration(baseContext.resources.configuration)
                config.uiMode = if (isLightMode) {
                    (config.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or android.content.res.Configuration.UI_MODE_NIGHT_NO
                } else {
                    (config.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or android.content.res.Configuration.UI_MODE_NIGHT_YES
                }
                val themedContext = baseContext.createConfigurationContext(config)
                val widgetContext = android.view.ContextThemeWrapper(
                    themedContext,
                    if (isLightMode) android.R.style.Theme_DeviceDefault_Light else android.R.style.Theme_DeviceDefault
                )
                
                val hostView = appWidgetHost.createView(widgetContext, widgetId, info)
                hostView.setAppWidget(widgetId, info)
                hostView.updateAppWidget(null)
                
                createWidgetWrapper(container, widgetId, info, hostView, null)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error adding widget", e)
                android.widget.Toast.makeText(activity, "Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        } ?: run {
             android.widget.Toast.makeText(activity, "No se pudo obtener información del widget", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun restoreWidgets(container: ViewGroup, configs: List<WidgetConfig>) {
        android.util.Log.d("WidgetManager", "Restoring ${configs.size} widgets...")
        container.removeAllViews()
        
        configs.forEach { config ->
            try {
                // Support both new Page 0 and legacy Page 1 for widgets
                if (config.page != 0 && config.page != 1) return@forEach
                
                val info = appWidgetManager.getAppWidgetInfo(config.widgetId)
                if (info == null) {
                    android.util.Log.e("WidgetManager", "Widget ${config.widgetId} info missing. Skipping restore (not deleting).")
                    return@forEach
                }
                
                val baseContext = activity.applicationContext
                val uiConfig = android.content.res.Configuration(baseContext.resources.configuration)
                uiConfig.uiMode = if (isLightMode) {
                    (uiConfig.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or android.content.res.Configuration.UI_MODE_NIGHT_NO
                } else {
                    (uiConfig.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or android.content.res.Configuration.UI_MODE_NIGHT_YES
                }
                val themedContext = baseContext.createConfigurationContext(uiConfig)
                val widgetContext = android.view.ContextThemeWrapper(
                    themedContext, 
                    if (isLightMode) android.R.style.Theme_DeviceDefault_Light else android.R.style.Theme_DeviceDefault
                )
                val hostView = appWidgetHost.createView(widgetContext, config.widgetId, info)
                hostView.setAppWidget(config.widgetId, info)
                
                createWidgetWrapper(container, config.widgetId, info, hostView, config)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring widget ${config.widgetId}", e)
            }
        }
    }

    private fun createWidgetWrapper(container: ViewGroup, widgetId: Int, info: AppWidgetProviderInfo, hostView: android.view.View, config: WidgetConfig?) {
        val displayMetrics = activity.resources.displayMetrics
        val density = displayMetrics.density
        
        // Dimensions
        val defaultHeightPx = (300 * density).toInt()
        val minHeightPx = (150 * density).toInt()
        
        val finalHeight = config?.height ?: run {
             val widgetMinHeightDp = info.minHeight / density.toInt()
             if (widgetMinHeightDp in 150..600) (widgetMinHeightDp * density).toInt() else defaultHeightPx
        }
        val finalWidth = config?.width ?: ViewGroup.LayoutParams.MATCH_PARENT
        
        // Circular reference handler
        var showMenuAction: (() -> Unit)? = null
        
        // Overlay
        val handleSize = (24 * density).toInt()
        val blockingOverlay = android.view.View(activity).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = true
            visibility = android.view.View.GONE
            setBackgroundColor(0x00000000)
            
            val overlayLongPressRunnable = Runnable {
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                showMenuAction?.invoke()
            }
            val overlayHandler = android.os.Handler(android.os.Looper.getMainLooper())
            var overlayInitialX = 0f
            var overlayInitialY = 0f
            
            setOnTouchListener { v, event ->
               when (event.action) {
                   android.view.MotionEvent.ACTION_DOWN -> {
                       overlayInitialX = event.rawX
                       overlayInitialY = event.rawY
                       overlayHandler.postDelayed(overlayLongPressRunnable, 500)
                       true
                   }
                   android.view.MotionEvent.ACTION_MOVE -> {
                       val dx = kotlin.math.abs(event.rawX - overlayInitialX)
                       val dy = kotlin.math.abs(event.rawY - overlayInitialY)
                       if (dx > 20 || dy > 20) {
                           overlayHandler.removeCallbacks(overlayLongPressRunnable)
                       }
                       true
                   }
                   android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                       overlayHandler.removeCallbacks(overlayLongPressRunnable)
                       true
                   }
                   else -> false
               }
            }
        }

        val cornerTopLeft = android.view.View(activity)
        val cornerTopRight = android.view.View(activity)
        val cornerBottomLeft = android.view.View(activity)
        val cornerBottomRight = android.view.View(activity)
        val handles = listOf(cornerTopLeft, cornerTopRight, cornerBottomLeft, cornerBottomRight)
        
        handles.forEach { 
            it.layoutParams = android.widget.FrameLayout.LayoutParams(handleSize, handleSize)
            it.visibility = android.view.View.GONE
            it.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0xFFFFFFFF.toInt())
                setStroke((2 * density).toInt(), 0xFF4CAF50.toInt())
            }
            it.elevation = 6 * density
        }

        // Wrapper Logic
        val wrapper = object : android.widget.FrameLayout(activity) {
            
            // State
            var longPressDetected = false
            val MODE_NONE = 0
            val MODE_MOVE = 1
            val MODE_RESIZE = 2
            var currentMode = MODE_NONE
            
            val longPressHandler = android.os.Handler(android.os.Looper.getMainLooper())
            var initialX = 0f
            var initialY = 0f
            var dX = 0f
            var dY = 0f
            
            // Revert state
            var initialXOnDown = 0f
            var initialYOnDown = 0f
            
            val longPressRunnable = Runnable {
                longPressDetected = true
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                showEditMenu()
            }
            
            init {
                showMenuAction = { showEditMenu() }
            }
            
            override fun onInterceptTouchEvent(ev: android.view.MotionEvent): Boolean {
                when (ev.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        if (currentMode == MODE_NONE) {
                            initialX = ev.rawX
                            initialY = ev.rawY
                            longPressDetected = false
                            longPressHandler.postDelayed(longPressRunnable, 500)
                        } else if (currentMode == MODE_MOVE) return true
                        else if (currentMode == MODE_RESIZE) return false
                        return false
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        if (currentMode == MODE_MOVE) return true
                        if (currentMode == MODE_RESIZE) return false
                        val deltaX = kotlin.math.abs(ev.rawX - initialX)
                        val deltaY = kotlin.math.abs(ev.rawY - initialY)
                        if (deltaX > 20 || deltaY > 20) longPressHandler.removeCallbacks(longPressRunnable)
                        return false
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        longPressHandler.removeCallbacks(longPressRunnable)
                        return false
                    }
                }
                return false
            }
            
            override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
                if (currentMode == MODE_MOVE) {
                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            dX = x - event.rawX
                            dY = y - event.rawY
                            initialX = event.rawX
                            initialY = event.rawY
                            
                            val params = layoutParams as android.widget.FrameLayout.LayoutParams
                            initialXOnDown = params.leftMargin.toFloat()
                            initialYOnDown = params.topMargin.toFloat()
                            
                            longPressDetected = false
                            longPressHandler.postDelayed(longPressRunnable, 500)
                            return true
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                             val deltaMoveX = kotlin.math.abs(event.rawX - initialX)
                             val deltaMoveY = kotlin.math.abs(event.rawY - initialY)
                             if (deltaMoveX > 20 || deltaMoveY > 20) longPressHandler.removeCallbacks(longPressRunnable)

                             val parentView = parent as? ViewGroup ?: return true
                             val pWidth = parentView.width
                             val pHeight = parentView.height
                             
                             // Calculate new margins
                             val rawDiffX = (event.rawX - initialX)
                             val rawDiffY = (event.rawY - initialY)
                             
                             var newLeft = (initialXOnDown + rawDiffX).toInt()
                             var newTop = (initialYOnDown + rawDiffY).toInt()
                             
                             // Boundary Check (Clamp)
                             newLeft = newLeft.coerceIn(0, pWidth - width)
                             newTop = newTop.coerceIn(0, pHeight - height)
                             
                             // Update LayoutParams
                             val params = layoutParams as android.widget.FrameLayout.LayoutParams
                             params.leftMargin = newLeft
                             params.topMargin = newTop
                             layoutParams = params
                             return true
                        }
                        android.view.MotionEvent.ACTION_UP -> {
                             // Collision Check
                             if (checkCollision(this)) {
                                 // Revert
                                 val params = layoutParams as android.widget.FrameLayout.LayoutParams
                                 params.leftMargin = initialXOnDown.toInt()
                                 params.topMargin = initialYOnDown.toInt()
                                 layoutParams = params
                                 android.widget.Toast.makeText(activity, "No hay espacio aquí", android.widget.Toast.LENGTH_SHORT).show()
                             } else {
                                 saveWidgetConfig(widgetId, info, this)
                             }
                             return true
                        }
                    }
                }
                return super.onTouchEvent(event)
            }
            
            fun checkCollision(currentWidget: android.view.View): Boolean {
                 val parentView = currentWidget.parent as? ViewGroup ?: return false
                 val currentRect = android.graphics.Rect()
                 currentWidget.getHitRect(currentRect)
                 
                 for (i in 0 until parentView.childCount) {
                     val other = parentView.getChildAt(i)
                     if (other == currentWidget) continue
                     if (other.visibility != android.view.View.VISIBLE) continue
                     if (other !is android.widget.FrameLayout) continue // Only check against other wrappers
                     
                     val otherRect = android.graphics.Rect()
                     other.getHitRect(otherRect)
                     
                     if (android.graphics.Rect.intersects(currentRect, otherRect)) {
                         return true
                     }
                 }
                 return false
            }
            
            fun showEditMenu() {
                val moveStr = "Mover"
                val resizeStr = "Redimensionar"
                val deleteStr = "Eliminar"
                val doneStr = "Terminar edición"
                
                val options = if (currentMode == MODE_NONE) {
                    listOf(
                        com.diez.stoiclauncher.presentation.common.MenuOption(moveStr),
                        com.diez.stoiclauncher.presentation.common.MenuOption(resizeStr),
                        com.diez.stoiclauncher.presentation.common.MenuOption(deleteStr)
                    )
                } else {
                    listOf(
                        com.diez.stoiclauncher.presentation.common.MenuOption(moveStr),
                        com.diez.stoiclauncher.presentation.common.MenuOption(resizeStr),
                        com.diez.stoiclauncher.presentation.common.MenuOption(doneStr),
                        com.diez.stoiclauncher.presentation.common.MenuOption(deleteStr)
                    )
                }
                
                val bottomSheet = com.diez.stoiclauncher.presentation.common.BottomSheetMenu(
                    if (currentMode == MODE_NONE) "Opciones" else "Modo Edición",
                    options
                ) { index ->
                     val isDone = (currentMode != MODE_NONE && index == 2)
                     val isDelete = (currentMode == MODE_NONE && index == 2) || (currentMode != MODE_NONE && index == 3)
                     
                     if (isDone) exitEditMode()
                     else if (isDelete) deleteWidget()
                     else if (index == 0) enterMoveMode()
                     else if (index == 1) enterResizeMode()
                }
                if (activity is androidx.fragment.app.FragmentActivity) {
                    bottomSheet.show(activity.supportFragmentManager, "widget_options")
                }
            }
            
            fun enterMoveMode() {
                currentMode = MODE_MOVE
                exitEditModeCallback = { exitEditMode() }
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0x30FFFFFF)
                    cornerRadius = 16 * density
                    setStroke((2 * density).toInt(), 0xFF4CAF50.toInt())
                }
                disableViewPagerSwipe()
                blockingOverlay.visibility = android.view.View.GONE
                handles.forEach { it.visibility = android.view.View.GONE }
                android.widget.Toast.makeText(activity, "Arrastra para mover", android.widget.Toast.LENGTH_SHORT).show()
            }
            
            fun enterResizeMode() {
                currentMode = MODE_RESIZE
                exitEditModeCallback = { exitEditMode() }
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0x30FFFFFF)
                    cornerRadius = 16 * density
                    setStroke((2 * density).toInt(), 0xFF2196F3.toInt())
                }
                disableViewPagerSwipe()
                blockingOverlay.visibility = android.view.View.VISIBLE
                handles.forEach { it.visibility = android.view.View.VISIBLE }
                android.widget.Toast.makeText(activity, "Arrastra esquinas", android.widget.Toast.LENGTH_SHORT).show()
            }
            
            fun exitEditMode() {
                currentMode = MODE_NONE
                exitEditModeCallback = null
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0x10FFFFFF)
                    cornerRadius = 16 * density
                }
                enableViewPagerSwipe()
                blockingOverlay.visibility = android.view.View.GONE
                handles.forEach { it.visibility = android.view.View.GONE }
                saveWidgetConfig(widgetId, info, this)
            }
            
            fun deleteWidget() {
                (parent as? ViewGroup)?.removeView(this)
                deleteWidgetId(widgetId)
            }
        }
        
        val wrapperBgColor = if (isLightMode) 0x1A000000 else 0x1AFFFFFF
        
        wrapper.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(wrapperBgColor)
            cornerRadius = 16 * density
        }
        wrapper.elevation = 2 * density
        wrapper.clipToOutline = true
        
        // Add Views to Wrapper
        val widgetParams = android.widget.FrameLayout.LayoutParams(
             ViewGroup.LayoutParams.MATCH_PARENT, 
             ViewGroup.LayoutParams.MATCH_PARENT
        )
        wrapper.addView(hostView, widgetParams)
        wrapper.addView(blockingOverlay)
        
        // Add Handles
        (cornerTopLeft.layoutParams as android.widget.FrameLayout.LayoutParams).gravity = android.view.Gravity.TOP or android.view.Gravity.LEFT
        wrapper.addView(cornerTopLeft)
        (cornerTopRight.layoutParams as android.widget.FrameLayout.LayoutParams).gravity = android.view.Gravity.TOP or android.view.Gravity.RIGHT
        wrapper.addView(cornerTopRight)
        (cornerBottomLeft.layoutParams as android.widget.FrameLayout.LayoutParams).gravity = android.view.Gravity.BOTTOM or android.view.Gravity.LEFT
        wrapper.addView(cornerBottomLeft)
        (cornerBottomRight.layoutParams as android.widget.FrameLayout.LayoutParams).gravity = android.view.Gravity.BOTTOM or android.view.Gravity.RIGHT
        wrapper.addView(cornerBottomRight)
        
        // Resize Listeners
        val setupResize = { handle: android.view.View, isLeft: Boolean, isTop: Boolean ->
            handle.setOnTouchListener(object : android.view.View.OnTouchListener {
                    var initialRawX = 0f
                    var initialRawY = 0f
                    var initialWidth = 0
                    var initialHeight = 0
                    var initialLeft = 0
                    var initialTop = 0
                    
                    override fun onTouch(v: android.view.View, event: android.view.MotionEvent): Boolean {
                        when (event.action) {
                            android.view.MotionEvent.ACTION_DOWN -> {
                                initialRawX = event.rawX
                                initialRawY = event.rawY
                                initialWidth = wrapper.width
                                initialHeight = wrapper.height
                                val params = wrapper.layoutParams as android.widget.FrameLayout.LayoutParams
                                initialLeft = params.leftMargin
                                initialTop = params.topMargin
                                return true
                            }
                            android.view.MotionEvent.ACTION_MOVE -> {
                                var dx = event.rawX - initialRawX
                                var dy = event.rawY - initialRawY
                                if (isLeft) dx = -dx
                                if (isTop) dy = -dy
                                
                                var newWidth = (initialWidth + dx).toInt().coerceAtLeast(minHeightPx)
                                var newHeight = (initialHeight + dy).toInt().coerceAtLeast(minHeightPx)
                                
                                val parentView = wrapper.parent as? ViewGroup ?: return true
                                val pWidth = parentView.width
                                val pHeight = parentView.height
                                
                                // Boundary Check for Resize
                                if (isLeft) {
                                     var newLeftMargin = initialLeft - (newWidth - initialWidth)
                                     newLeftMargin = newLeftMargin.coerceAtLeast(0)
                                     newWidth = initialWidth + (initialLeft - newLeftMargin)
                                     
                                     val params = wrapper.layoutParams as android.widget.FrameLayout.LayoutParams
                                     params.leftMargin = newLeftMargin
                                } else {
                                     val currentLeft = (wrapper.layoutParams as android.widget.FrameLayout.LayoutParams).leftMargin
                                     newWidth = newWidth.coerceAtMost(pWidth - currentLeft)
                                }
                                
                                if (isTop) {
                                     var newTopMargin = initialTop - (newHeight - initialHeight)
                                     newTopMargin = newTopMargin.coerceAtLeast(0)
                                     newHeight = initialHeight + (initialTop - newTopMargin)
                                     
                                     val params = wrapper.layoutParams as android.widget.FrameLayout.LayoutParams
                                     params.topMargin = newTopMargin
                                } else {
                                     val currentTop = (wrapper.layoutParams as android.widget.FrameLayout.LayoutParams).topMargin
                                     newHeight = newHeight.coerceAtMost(pHeight - currentTop)
                                }

                                wrapper.layoutParams.width = newWidth
                                wrapper.layoutParams.height = newHeight
                                wrapper.requestLayout()
                                return true
                            }
                            android.view.MotionEvent.ACTION_UP -> {
                                if (wrapper.checkCollision(wrapper)) {
                                     // Revert Resize
                                     val params = wrapper.layoutParams as android.widget.FrameLayout.LayoutParams
                                     params.width = initialWidth
                                     params.height = initialHeight
                                     params.leftMargin = initialLeft
                                     params.topMargin = initialTop
                                     wrapper.layoutParams = params
                                      android.widget.Toast.makeText(activity, "No hay espacio", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                     saveWidgetConfig(widgetId, info, wrapper)
                                }
                                return true
                            }
                        }
                        return false
                    }
             })
        }
        setupResize(cornerTopLeft, true, true)
        setupResize(cornerTopRight, false, true)
        setupResize(cornerBottomLeft, true, false)
        setupResize(cornerBottomRight, false, false)
        
        // Add to Container
        if (config != null) {
            val params = android.widget.FrameLayout.LayoutParams(config.width, config.height)
            params.leftMargin = config.x.toInt()
            params.topMargin = config.y.toInt()
            container.addView(wrapper, params)
        } else {
             // New Widget Auto-Placement
             val spacing = (16 * density).toInt() 
             val slot = findPlacementSlot(container, finalHeight, minHeightPx, spacing)
             
             if (slot != null) {
                 val (y, h) = slot
                 val params = android.widget.FrameLayout.LayoutParams(
                     android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
                     h
                 )
                 params.topMargin = y
                 container.addView(wrapper, params)
                 
                 // Update height in wrapper params immediately for view measure
                 wrapper.layoutParams.height = h
                 
                 // Post to save actual position after layout
                 wrapper.post {
                     saveWidgetConfig(widgetId, info, wrapper)
                 }
             } else {
                 android.widget.Toast.makeText(activity, "No hay más lugar en el espacio de widgets", android.widget.Toast.LENGTH_LONG).show()
                 deleteWidgetId(widgetId)
             }
        }
    }
    
    // Returns Pair(Y, Height)
    private fun findPlacementSlot(container: ViewGroup, desiredHeight: Int, minHeight: Int, spacing: Int): Pair<Int, Int>? {
        val displayMetrics = activity.resources.displayMetrics
        val rawHeight = container.height
        // Scan limit: Current visual height (prevent infinite loop 0..infinity)
        val scanLimit = if (rawHeight > 0) rawHeight else displayMetrics.heightPixels
        
        val reqWidth = container.width.coerceAtLeast(displayMetrics.widthPixels)
        
        var currentY = spacing
        val step = 10 
        
        var maxWidgetBottom = 0
        
        // 1. Scan for Gaps (Perfect Fit)
        while (currentY < scanLimit) {
             val desiredRect = android.graphics.Rect(0, currentY, reqWidth, currentY + desiredHeight)
             var collisionFound = false
             var nextSkipY = currentY + step
             
             for (i in 0 until container.childCount) {
                 val child = container.getChildAt(i)
                 if (child.visibility != android.view.View.VISIBLE) continue
                 if (child !is android.widget.FrameLayout) continue 
                 
                 val childRect = android.graphics.Rect()
                 child.getHitRect(childRect)
                 maxWidgetBottom = kotlin.math.max(maxWidgetBottom, childRect.bottom)
                 
                 if (android.graphics.Rect.intersects(desiredRect, childRect)) {
                     collisionFound = true
                     nextSkipY = kotlin.math.max(nextSkipY, childRect.bottom + spacing)
                 }
             }
             
             if (!collisionFound) {
                 // Perfect fit found in a gap!
                 // Ensure we don't return a slot that is visually "weird" if it's way below everything but inside scanLimit? 
                 // No, gap finding is good.
                 // But wait, if maxWidgetBottom is small (e.g. 100), and we find a gap at 1000? 
                 // We should only fill gaps "between" widgets or at the immediate end.
                 // "Fill Gaps" usually implies checking 0..maxWidgetBottom.
                 // If currentY > maxWidgetBottom, we are effectively appending.
                 
                 // So, if we find a spot, return it.
                 return Pair(currentY, desiredHeight)
             }
             
             currentY = nextSkipY
        }
        
        // 2. Scan for Max Bottom again just in case loop didn't visit everything (unlikely)
        // Actually loop logic ensures we visited intersecting, but maybe not side ones?
        // Let's safe-check maxWidgetBottom by iterating one last time if needed, OR trust the loop.
        // Trust loop? Loop iterates Y. If widgets are disjoint, we might miss some?
        // No, `findNextYPosition` iterates children inside loop. Correct.
        // Wait! The loop condition `currentY < scanLimit`.
        // If there is a widget AT scanLimit, we see it.
        // But to be SAFE, let's just calc maxBottom explicitly once.
        
        var absoluteMaxBottom = 0
        for (i in 0 until container.childCount) {
             val child = container.getChildAt(i)
             if (child.visibility != android.view.View.VISIBLE || child !is android.widget.FrameLayout) continue
             val r = android.graphics.Rect()
             child.getHitRect(r)
             absoluteMaxBottom = kotlin.math.max(absoluteMaxBottom, r.bottom)
        }
        
        // 3. Append (Fallback)
        // Since we are in a ScrollView, we can always extend.
        // Just place it below the lowest widget.
        val appendY = kotlin.math.max(spacing, absoluteMaxBottom + spacing)
        
        return Pair(appendY, desiredHeight)
    }
    
    private fun saveWidgetConfig(widgetId: Int, info: AppWidgetProviderInfo, wrapper: android.widget.FrameLayout) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val config = WidgetConfig(
                    widgetId = widgetId,
                    provider = info.provider.flattenToString(),
                    x = wrapper.x,
                    y = wrapper.y,
                    width = wrapper.width,
                    height = wrapper.height,
                    page = 0 // Updated to reflect correct screen index (Screen 0)
                )
                settingsRepository.saveWidgetConfig(config)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving widget config", e)
            }
        }
    }

    companion object {
        private const val TAG = "WidgetManager"
        const val REQUEST_PICK_APPWIDGET = 9
        const val REQUEST_CREATE_APPWIDGET = 10
        const val REQUEST_BIND_APPWIDGET = 11
        const val APPWIDGET_HOST_ID = 1024
    }
}
