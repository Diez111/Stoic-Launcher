package com.diez.stoiclauncher.presentation

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.diez.stoiclauncher.databinding.ActivityMainBinding

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.diez.stoiclauncher.StoicApplication
import com.diez.stoiclauncher.presentation.home.HomeViewModel
import com.diez.stoiclauncher.presentation.util.viewModelFactory
import com.diez.stoiclauncher.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.os.Handler
import android.os.Looper

import androidx.recyclerview.widget.GridLayoutManager
import com.diez.stoiclauncher.presentation.home.AppAdapter
import com.diez.stoiclauncher.presentation.widget.WidgetManager
import android.content.Intent
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.pm.PackageManager

import com.diez.stoiclauncher.presentation.home.fragments.WidgetContainerProvider
import com.diez.stoiclauncher.R
import com.diez.stoiclauncher.presentation.common.BottomSheetMenu
import com.diez.stoiclauncher.presentation.common.MenuOption

import com.diez.stoiclauncher.presentation.home.fragments.AppActionListener
import com.diez.stoiclauncher.domain.model.AppModel

class MainActivity : AppCompatActivity(), WidgetContainerProvider, AppActionListener {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: HomeViewModel
    
    // MICROOPTIMIZATION: Use Controller (Microservice) pattern
    public lateinit var widgetController: com.diez.stoiclauncher.presentation.controller.WidgetController
    private lateinit var wallpaperSettingsManager: com.diez.stoiclauncher.presentation.manager.WallpaperSettingsManager
    
    // WidgetController manages the container ref internally now
    // private var currentWidgetContainer: ViewGroup? = null 
    // private var pendingWidgetId: Int? = null
    
    override fun onAppLongClick(app: AppModel, source: String): Boolean {
        if (app.isGroup) {
            // Group Options
            val menuOptions = listOf(
                MenuOption("Favorito"), // Toggle favorite for group
                MenuOption("Renombrar"),
                MenuOption("Eliminar Grupo")
            )
            
            val bottomSheet = BottomSheetMenu("${app.label}", menuOptions) { index ->
                when (index) {
                    0 -> viewModel.toggleAppFavorite(app)
                    1 -> showRenameGroupDialog(app)
                    2 -> showDeleteGroupConfirmation(app)
                }
            }
            bottomSheet.show(supportFragmentManager, "GroupMenu")
            return true
        }
        
        // App Options
        // Build dynamic options based on context
        val menuOptions = mutableListOf<MenuOption>()
        
        // Context specific actions
        if (source == "FAVORITES") {
             menuOptions.add(MenuOption("Quitar de Favoritos"))
        } else if (source == "GROUP") {
             menuOptions.add(MenuOption("Quitar del Grupo"))
        } else {
             // Drawer
             val isFavorite = viewModel.favoritesState.value.any { it.uniqueId == app.uniqueId }
             val favLabel = if (isFavorite) "Quitar de Favoritos" else "Añadir a Favoritos"
             menuOptions.add(MenuOption(favLabel))
        }
        
        menuOptions.add(MenuOption("Grupo")) // Provide capability to move/add to group always? Or restricted?
        // Add to Group / Move to Group
        
        menuOptions.add(MenuOption("Ocultar"))
        menuOptions.add(MenuOption("Desinstalar"))
        menuOptions.add(MenuOption("Info de App"))
        menuOptions.add(MenuOption("Límite de Uso"))
        
        val bottomSheet = BottomSheetMenu("${app.label}", menuOptions) { index ->
            // Map index to action. Since list is dynamic, logic needs to be dynamic or sequential?
            // "Quitar de Fav" is always 0 if preset, "Grupo" is 1, etc.
            
            // Simplest way: Switch on index, but the first item changes meaning? 
            // Better: use exact index mapping or list of actions.
            // Let's assume standard order: 0=ContextAction, 1=Group, 2=Hide, 3=Uninstall, 4=Info, 5=Limit
            
            when (index) {
                0 -> {
                    if (source == "FAVORITES") {
                        viewModel.toggleAppFavorite(app) // It will remove it
                    } else if (source == "GROUP") {
                         // Remove from Group (set group to null)
                         viewModel.setAppGroup(app, null) // Assuming passing null removes group
                    } else {
                         viewModel.toggleAppFavorite(app)
                    }
                }
                1 -> showGroupDialog(app)
                2 -> showHideConfirmation(app)
                3 -> {
                    try {
                        val intent = Intent(android.content.Intent.ACTION_DELETE)
                        intent.data = android.net.Uri.parse("package:${app.packageName}")
                        startActivity(intent)
                    } catch (e: Exception) {}
                }
                4 -> {
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = android.net.Uri.parse("package:${app.packageName}")
                        startActivity(intent)
                    } catch (e: Exception) {}
                }
                5 -> {
                   // Per-App Limit Dialog
                   val usageManager = (application as com.diez.stoiclauncher.StoicApplication).container.appUsageManager
                   if (!usageManager.hasPermission()) {
                        // Prompt Permission
                       androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Permiso Necesario")
                            .setMessage("Para limitar el uso de ${app.label}, necesitas dar permiso de acceso a estadísticas de uso.")
                            .setPositiveButton("Otorgar") { _, _ ->
                                usageManager.promptPermission()
                            }
                            .setNegativeButton("Cancelar", null)
                            .show()
                   } else {
                       showUsageLimitDialog(app)
                   }
                }
            }
        }
        bottomSheet.show(supportFragmentManager, "AppMenu")
        return true
    }
    
    private fun showUsageLimitDialog(app: AppModel) {
        val options = arrayOf("Sin Límite", "15 minutos", "30 minutos", "45 minutos", "1 hora", "1 hora 30 min", "2 horas", "3 horas")
        val values = intArrayOf(0, 15, 30, 45, 60, 90, 120, 180)
        
        // Use Coroutine to get current limit for pre-selection (highlighting)? 
        // For simple dialog, just show list.
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Límite para ${app.label}")
            .setItems(options) { _, which ->
                viewModel.setAppUsageLimit(app, values[which])
                android.widget.Toast.makeText(this, "Límite actualizado", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // AGGRESSIVE GHOSTING FIX: Ensure window is translucent
        window.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle Window Insets (Navigation Bar / Status Bar)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            
            // Apply bottom padding to the Dock (Bottom Bar) so it sits ABOVE the nav bar
            binding.bottomBar.setPadding(
                binding.bottomBar.paddingLeft,
                binding.bottomBar.paddingTop,
                binding.bottomBar.paddingRight,
                insets.bottom + 24 // +24dp original padding
            )
            
            // Apply top padding to ViewPager (for status bar) and bottom padding (for nav bar)
            // Note: We might want the wallpaper to be full screen, so we pad the internal views, not the root.
            // But here, apply to ViewPager to ensure content isn't hidden.
            binding.viewPager.setPadding(
                0, 
                insets.top, 
                0, 
                insets.bottom // Ensure lists don't go behind nav bar
            )
            
            androidx.core.view.WindowInsetsCompat.CONSUMED
        }
        
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Always return to widgets screen (page 1 - center)
                if (binding.viewPager.currentItem != 1) {
                    binding.viewPager.currentItem = 1
                }
                // If already on widgets (1), do nothing (launcher behavior)
            }
        })
        
        // Force Spanish if no locale is set
        if (androidx.appcompat.app.AppCompatDelegate.getApplicationLocales().isEmpty) {
            val localeList = androidx.core.os.LocaleListCompat.forLanguageTags("es")
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(localeList)
        }
        
        // Initialize Microservices
        wallpaperSettingsManager = com.diez.stoiclauncher.presentation.manager.WallpaperSettingsManager(this)
        widgetController = com.diez.stoiclauncher.presentation.controller.WidgetController(this)
        // appWidgetManager and widgetManager removed from Activity level config
        
        val appContainer = (application as StoicApplication).container
        viewModel = ViewModelProvider(
            this,
            viewModelFactory { 
                HomeViewModel(
                    appContainer.getInstalledAppsUseCase,
                    appContainer.refreshAppsUseCase,
                    appContainer.filterAppsUseCase,
                    appContainer.toggleAppFavoriteUseCase,
                    appContainer.manageAppGroupsUseCase,
                    appContainer.hideAppUseCase,
                    appContainer.renameAppUseCase,
                    appContainer.settingsRepository
                ) 
            }
        )[HomeViewModel::class.java]

        setupPager()
        
        lifecycleScope.launch {
            val settingsRepo = (application as StoicApplication).container.settingsRepository
            
            kotlinx.coroutines.flow.combine(
                settingsRepo.isWallpaperEnabled,
                settingsRepo.accentColor,
                settingsRepo.wallpaperUri
            ) { isWp, col, uri -> Triple(isWp, col, uri) }
            .collectLatest { (isWallpaper, accentColor, wallpaperUri) ->
                // Delegate to Microservice
                wallpaperSettingsManager.applyWallpaperSettings(isWallpaper, accentColor, wallpaperUri)
            }
        }
        
        // Listen for One-Shot User Events (Toasts)
        lifecycleScope.launch {
            viewModel.userEvents.collectLatest { message ->
                android.widget.Toast.makeText(this@MainActivity, message, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        
        // Handle Search Intent from Accessibility Service
        if (intent.getBooleanExtra("open_search", false)) {
            binding.viewPager.post {
                binding.viewPager.currentItem = 2
            }
        }
        
        setupGestures()
        setupDock()
    }

    private fun setupDock() {
        val btnPhone = findViewById<android.view.View>(R.id.btn_phone)
        val btnSettings = findViewById<android.view.View>(R.id.btn_settings)

        // Launch Clock & Calendar are fragment specific, but Dock is global.
        
        lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(
                viewModel.shortcutsState,
                viewModel.uiState, // Use unfiltered state to ensure shortcuts persist during search
                viewModel.accentColor
            ) { shortcuts, apps, color -> Triple(shortcuts, apps, color) }
            .collectLatest { (shortcuts, apps, color) ->
                 // Color
                 val contentColor = com.diez.stoiclauncher.presentation.util.ColorHelper.getTextColorForAccent(color)
                 (btnPhone as? android.widget.TextView)?.setTextColor(contentColor)
                 (btnSettings as? android.widget.TextView)?.setTextColor(contentColor)

                 // LEFT SHORTCUT (Phone)
                 val leftPackage = shortcuts["bottom_left"]
                 val leftApp = if (leftPackage != null) apps.find { it.packageName == leftPackage } else null
                 
                 if (leftPackage != null && leftApp != null) {
                      (btnPhone as? android.widget.TextView)?.text = leftApp.label
                      btnPhone.setOnClickListener { onAppClickHelper(leftApp) }
                 } else {
                      (btnPhone as? android.widget.TextView)?.text = getString(R.string.phone)
                      btnPhone.setOnClickListener { defaultPhoneAction() }
                 }

                 // RIGHT SHORTCUT (Settings)
                 val rightPackage = shortcuts["bottom_right"]
                 val rightApp = if (rightPackage != null) apps.find { it.packageName == rightPackage } else null
                 
                 if (rightPackage != null && rightApp != null) {
                      (btnSettings as? android.widget.TextView)?.text = rightApp.label
                      btnSettings.setOnClickListener { onAppClickHelper(rightApp) }
                 } else {
                     (btnSettings as? android.widget.TextView)?.text = getString(R.string.settings)
                     btnSettings.setOnClickListener { defaultSettingsAction() }
                 }
            }
        }
        
        // Long Press to Change
        btnPhone.setOnLongClickListener {
            showAppSelectionDialog("bottom_left")
            true
        }
        
        btnSettings.setOnLongClickListener {
            showAppSelectionDialog("bottom_right")
            true
        }
    }

    private fun onAppClickHelper(app: AppModel) {
         if (app.isGroup) {
             showGroupDialog(app)
         } else {
             com.diez.stoiclauncher.presentation.util.AppLaunchHelper.launchApp(this, app)
         }
    }

    private fun defaultPhoneAction() {
         try {
             val intent = Intent(android.content.Intent.ACTION_DIAL)
             startActivity(intent)
         } catch (e: Exception) {}
    }

    private fun defaultSettingsAction() {
        startActivity(Intent(this, com.diez.stoiclauncher.presentation.settings.SettingsActivity::class.java))
    }

    private fun showAppSelectionDialog(position: String) {
        // Use Coroutine to get the RAW app list synchronously (bypassing search/groups filters)
        lifecycleScope.launch {
             val apps = viewModel.getAppsForPicker().filter { !it.isGroup }.sortedBy { it.label.lowercase() }
             val items = apps.map { it.label }.toTypedArray()
             
             val options = arrayOf("Restaurar por defecto") + items
             
             // Convert to MenuOptions for BottomSheetMenu
             val menuOptions = options.map { com.diez.stoiclauncher.presentation.common.MenuOption(it) }
             
             val bottomSheet = com.diez.stoiclauncher.presentation.common.BottomSheetMenu(
                 "Seleccionar App", 
                 menuOptions,
                 viewModel.accentColor.value
             ) { index ->
                 if (index == 0) {
                     // Reset
                     viewModel.setAppShortcut(position, null)
                 } else {
                     // Index - 1 because of "Restore" option
                     val selectedApp = apps[index - 1]
                     viewModel.setAppShortcut(position, selectedApp.packageName)
                 }
             }
             bottomSheet.show(supportFragmentManager, "app_picker")
        }
    }

    private fun setupGestures() {
        val gestureDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                if (binding.viewPager.currentItem == 1) { // Only on Home
                    executeGestureAction("DOUBLE_TAP")
                    return true
                }
                return false
            }

            override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (binding.viewPager.currentItem == 1 && e1 != null) { // Only on Home
                    val diffY = e2.y - e1.y
                    val diffX = e2.x - e1.x
                    if (Math.abs(diffY) > Math.abs(diffX) && Math.abs(diffY) > 100 && velocityY > 100) {
                        // Swipe Down
                        executeGestureAction("SWIPE_DOWN")
                        return true
                    }
                }
                return false
            }
        })

        binding.viewPager.getChildAt(0).setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false // Don't consume so ViewPager still works
        }
    }

    private fun executeGestureAction(trigger: String) {
        val appContainer = (application as StoicApplication).container
        lifecycleScope.launch {
            val mappings = appContainer.settingsRepository.gestureMappingsFlow.first()
            val action = mappings[trigger]
            
            // Execute on Main UI
            launch(kotlinx.coroutines.Dispatchers.Main) {
                when (action) {
                    "SEARCH" -> {
                         // Search is handled by opening Drawer/Search? 
                         // Or just trigger search in DrawerFragment if we had a way.
                         // For now, let's just go to page 2 (Drawer)
                         binding.viewPager.currentItem = 2
                    }
                    "NOTIFICATIONS" -> {
                        // Note: Expanding notification panel from Activity
                        // Using reflection trick for compatibility
                        try {
                            val statusBarService = getSystemService("statusbar")
                            val statusBarManager = Class.forName("android.app.StatusBarManager")
                            val expandMethod = statusBarManager.getMethod("expandNotificationsPanel")
                            expandMethod.invoke(statusBarService)
                        } catch (e: Exception) {
                            // Fallback or ignore
                        }
                    }
                    "FLASHLIGHT" -> {
                        // Copy flashlight logic from service or move to a helper
                        toggleFlashlightInternal()
                    }
                }
            }
        }
    }

    private var isFlashlightOnInternal = false
    private fun toggleFlashlightInternal() {
        try {
            val cameraManager = getSystemService(android.content.Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            isFlashlightOnInternal = !isFlashlightOnInternal
            cameraManager.setTorchMode(cameraId, isFlashlightOnInternal)
        } catch (e: Exception) {}
    }

    private fun setupPager() {
        val adapter = MainPagerAdapter(this)
        binding.viewPager.adapter = adapter
        binding.viewPager.orientation = androidx.viewpager2.widget.ViewPager2.ORIENTATION_HORIZONTAL
        binding.viewPager.offscreenPageLimit = 2  // Mantener todos los fragmentos (3 páginas) en memoria
        binding.viewPager.currentItem = 1 // Start on Widgets (center)

        // Dynamic Dock Hiding (Fade out when moving to Drawer/Page 2)
        binding.viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                // Transitioning from Home (1) to Drawer (2)
                if (position == 1) {
                    // offset goes 0 -> 1 as we swipe right to Drawer
                    val alpha = 1f - positionOffset
                    binding.bottomBar.alpha = alpha
                    binding.bottomBar.visibility = if (alpha <= 0.05f) View.GONE else View.VISIBLE
                    
                    // Also fade out if going to Widgets (0)? Usually users like dock there. 
                    // But if position == 0, we are at Widgets. 
                    // Let's only target Drawer (Page 2).
                } else if (position == 2) {
                    // Fully in Drawer, or swiping back
                    binding.bottomBar.alpha = 0f
                    binding.bottomBar.visibility = View.GONE
                } else if (position == 0) {
                     // Widgets
                     binding.bottomBar.alpha = 1f
                     binding.bottomBar.visibility = View.VISIBLE
                }
            }
            
            override fun onPageSelected(position: Int) {
                 if (position == 2) {
                     binding.bottomBar.visibility = View.GONE
                     // Keyboard is now handled by DrawerFragment.onResume() - more reliable!
                 } else {
                     binding.bottomBar.visibility = View.VISIBLE
                     binding.bottomBar.alpha = 1f
                     // Keyboard hiding is now handled by DrawerFragment.onPause() - more reliable!
                 }
            }
        })
        
        // Set navigation bar to black (suppress deprecation - still needed for older APIs)
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.BLACK
        
        // Back Press Handler
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (widgetController.handleBackPress()) {
                    return
                }
                if (binding.viewPager.currentItem != 1) {
                    binding.viewPager.currentItem = 1  // Always return to Favorites (Home)
                }
            }
        })
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent) // Update intent
        if (intent?.categories?.contains(Intent.CATEGORY_HOME) == true && intent.action == Intent.ACTION_MAIN) {
            binding.viewPager.currentItem = 1 // Navigate to Favorites (Home)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isDefaultLauncher()) {
            promptSetDefaultLauncher()
        }
    }
    
    override fun onStart() {
        super.onStart()
        widgetController.onStart()
    }
    
    override fun onStop() {
        super.onStop()
        widgetController.onStop()
    }
    
    override fun onDestroy() {
        super.onDestroy()
    }

    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == packageName
    }

    private fun promptSetDefaultLauncher() {
        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.set_default_title))
            .setMessage(getString(R.string.set_default_message))
            .setPositiveButton(getString(R.string.go_to_settings)) { _, _ ->
                 val intent = Intent(android.provider.Settings.ACTION_HOME_SETTINGS)
                 try {
                     startActivity(intent)
                 } catch (e: Exception) {
                     val intent2 = Intent(android.provider.Settings.ACTION_SETTINGS)
                     startActivity(intent2)
                 }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // ...

    // Widget Provider Implementation
    override fun attachWidgetContainer(container: ViewGroup) {
        widgetController.attachContainer(container)
    }
    
    private fun showGroupDialog(app: com.diez.stoiclauncher.domain.model.AppModel) {
        // Collect existing groups from raw state (all installed apps) to avoid search filters
        val currentGroups = viewModel.uiState.value
            .mapNotNull { it.groupId }
            .distinct()
            .sorted()
        
        
        val options = mutableListOf<String>()
        options.add(getString(R.string.new_group_action)) // "Create New Group"
        options.addAll(currentGroups)
        
        // Use BottomSheetMenu for elegant group selection
        val menuOptions = options.map { MenuOption(it) }
        val bottomSheet = BottomSheetMenu(getString(R.string.add_to_group), menuOptions) { index ->
            if (index == 0) {
                // New Group -> Show Input Dialog
                showNewGroupInputDialog(app)
            } else {
                // Selected existing group
                val selectedGroup = options[index]
                viewModel.setAppGroup(app, selectedGroup)
            }
        }
        bottomSheet.show(supportFragmentManager, "AddToGroupSheet")
    }
    

    
    private fun showRenameDialog(app: com.diez.stoiclauncher.domain.model.AppModel) {
       showBottomSheetInput(
           title = getString(R.string.rename_title),
           prefill = app.label,
           hint = getString(R.string.rename_title)
       ) { newName ->
           viewModel.renameApp(app, newName)
       }
    }

    private fun showRenameGroupDialog(group: com.diez.stoiclauncher.domain.model.AppModel) {
        showBottomSheetInput(
            title = getString(R.string.rename_title),
            prefill = group.groupId ?: "",
            hint = getString(R.string.group_name_hint)
        ) { newName ->
            if (newName.isNotEmpty() && group.groupId != null) {
                viewModel.renameGroup(group.groupId, newName)
            }
        }
    }
    
    private fun showNewGroupInputDialog(app: com.diez.stoiclauncher.domain.model.AppModel) {
        showBottomSheetInput(
            title = getString(R.string.new_group_title),
            prefill = "",
            hint = getString(R.string.group_name_hint)
        ) { name ->
             if (name.isNotEmpty()) {
                viewModel.setAppGroup(app, name)
            }
        }
    }
    
    private fun showBottomSheetInput(title: String, prefill: String, hint: String, onConfirm: (String) -> Unit) {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_bottom_sheet_input, null)
        bottomSheetDialog.setContentView(view)
        
        // Theme Logic
        val accentColor = viewModel.accentColor.value
        val contentColor = com.diez.stoiclauncher.presentation.util.ColorHelper.getTextColorForAccent(accentColor)
        
        // Use Helper for Background & Nav Bar
        com.diez.stoiclauncher.presentation.util.UiHelper.setupBottomSheetColor(bottomSheetDialog, view, accentColor)
        
        val tvTitle = view.findViewById<android.widget.TextView>(R.id.tv_title)
        val etInput = view.findViewById<android.widget.EditText>(R.id.et_input)
        val btnConfirm = view.findViewById<android.widget.TextView>(R.id.btn_confirm)
        
        tvTitle.text = title
        tvTitle.setTextColor(contentColor)
        
        // Input Field Styling (Fixed White Background -> Fixed Black Text)
        etInput.setText(prefill)
        etInput.hint = hint
        etInput.setTextColor(android.graphics.Color.BLACK) 
        etInput.setHintTextColor(android.graphics.Color.GRAY)
        // Note: Background is set to @drawable/shape_input_field (white rounded) in XML
        // Do NOT tint the background via code or it will override white.
        etInput.selectAll()
        
        btnConfirm.setTextColor(contentColor)
        btnConfirm.setOnClickListener {
            val text = etInput.text.toString().trim()
            onConfirm(text)
            bottomSheetDialog.dismiss()
        }
        
        bottomSheetDialog.show()
        
        // Focus and Keyboard
        etInput.requestFocus()
    }
    
    private fun showDeleteGroupConfirmation(group: com.diez.stoiclauncher.domain.model.AppModel) {
        // Minimalist confirmation via BottomSheet
        val options = listOf(
            MenuOption(getString(R.string.delete_action))
        )
        
        val bottomSheet = BottomSheetMenu(
            getString(R.string.delete_group_message, group.label), 
            options,
            viewModel.accentColor.value
        ) { index ->
            if (index == 0) {
                group.groupId?.let { groupId ->
                    viewModel.deleteGroup(groupId)
                }
            }
        }
        bottomSheet.show(supportFragmentManager, "DeleteGroup")
    }

    private fun showHideConfirmation(app: com.diez.stoiclauncher.domain.model.AppModel) {
         android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.hide_title))
            .setMessage(getString(R.string.hide_message, app.label))
            .setPositiveButton(getString(R.string.hide)) { _, _ ->
                viewModel.hideApp(app)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    // Widget Result Handling
    // Widget Result Handling
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        widgetController.handleActivityResult(requestCode, resultCode, data)
    }
    
    // Removed addWidgetToContainer - moved to Controller
    
    override fun requestAddWidget() {
        widgetController.requestAddWidget()
    }
    
    private inner class MainPagerAdapter(fa: androidx.fragment.app.FragmentActivity) : androidx.viewpager2.adapter.FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = 3
        override fun createFragment(position: Int): androidx.fragment.app.Fragment {
            return when (position) {
                0 -> com.diez.stoiclauncher.presentation.home.fragments.WidgetsFragment()    // Left
                1 -> com.diez.stoiclauncher.presentation.home.fragments.FavoritesFragment()  // Center/Home
                2 -> com.diez.stoiclauncher.presentation.home.fragments.DrawerFragment()     // Right
                else -> throw IllegalArgumentException("Invalid position $position")
            }
        }
    }
}