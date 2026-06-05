package com.diez.stoiclauncher.presentation

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.diez.stoiclauncher.R
import com.diez.stoiclauncher.StoicApplication
import com.diez.stoiclauncher.databinding.ActivityMainBinding
import com.diez.stoiclauncher.domain.model.AppModel
import com.diez.stoiclauncher.presentation.common.BottomSheetMenu
import com.diez.stoiclauncher.presentation.common.MenuOption
import com.diez.stoiclauncher.presentation.home.HomeViewModel
import com.diez.stoiclauncher.presentation.home.fragments.AppActionListener
import com.diez.stoiclauncher.presentation.home.fragments.WidgetContainerProvider
import com.diez.stoiclauncher.presentation.util.ColorHelper
import com.diez.stoiclauncher.presentation.util.LaunchHelper
import com.diez.stoiclauncher.presentation.util.viewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), WidgetContainerProvider, AppActionListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: HomeViewModel
    lateinit var widgetController: com.diez.stoiclauncher.presentation.controller.WidgetController
    private lateinit var wallpaperSettingsManager: com.diez.stoiclauncher.presentation.manager.WallpaperSettingsManager

    override fun onAppLongClick(app: AppModel, source: String): Boolean {
        if (app.isGroup) {
            val menuOptions = listOf(
                MenuOption("Favorito"),
                MenuOption("Renombrar"),
                MenuOption("Eliminar Grupo")
            )
            val bottomSheet = BottomSheetMenu(app.label, menuOptions) { index ->
                when (index) {
                    0 -> viewModel.toggleAppFavorite(app)
                    1 -> showRenameGroupDialog(app)
                    2 -> showDeleteGroupConfirmation(app)
                }
            }
            bottomSheet.show(supportFragmentManager, "GroupMenu")
            return true
        }

        val menuOptions = mutableListOf<MenuOption>()
        if (source == "FAVORITES") {
            menuOptions.add(MenuOption("Quitar de Favoritos"))
        } else if (source == "GROUP") {
            menuOptions.add(MenuOption("Quitar del Grupo"))
        } else {
            val isFavorite = viewModel.favoritesState.value.any { it.uniqueId == app.uniqueId }
            val favLabel = if (isFavorite) "Quitar de Favoritos" else "Añadir a Favoritos"
            menuOptions.add(MenuOption(favLabel))
        }
        menuOptions.add(MenuOption("Grupo"))
        menuOptions.add(MenuOption("Ocultar"))
        menuOptions.add(MenuOption("Desinstalar"))
        menuOptions.add(MenuOption("Info de App"))
        menuOptions.add(MenuOption("Límite de Uso"))

        val bottomSheet = BottomSheetMenu(app.label, menuOptions) { index ->
            when (index) {
                0 -> {
                    if (source == "FAVORITES") viewModel.toggleAppFavorite(app)
                    else if (source == "GROUP") viewModel.setAppGroup(app, null)
                    else viewModel.toggleAppFavorite(app)
                }
                1 -> showGroupDialog(app)
                2 -> showHideConfirmation(app)
                3 -> {
                    try {
                        val intent = Intent(Intent.ACTION_DELETE)
                        intent.data = android.net.Uri.parse("package:${app.packageName}")
                        startActivity(intent)
                    } catch (e: Exception) {
                        android.util.Log.w("MainActivity", "Could not uninstall ${app.packageName}", e)
                    }
                }
                4 -> {
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = android.net.Uri.parse("package:${app.packageName}")
                        startActivity(intent)
                    } catch (e: Exception) {
                        android.util.Log.w("MainActivity", "Could not open app info", e)
                    }
                }
                5 -> {
                    val usageManager = (application as StoicApplication).container.appUsageManager
                    if (!usageManager.hasPermission()) {
                        androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Permiso Necesario")
                            .setMessage("Para limitar el uso de ${app.label}, necesitas dar permiso de acceso a estadísticas de uso.")
                            .setPositiveButton("Otorgar") { _, _ -> usageManager.promptPermission() }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            binding.bottomBar.setPadding(
                binding.bottomBar.paddingLeft, binding.bottomBar.paddingTop,
                binding.bottomBar.paddingRight, insets.bottom + 16
            )
            binding.viewPager.setPadding(0, insets.top, 0, insets.bottom)
            androidx.core.view.WindowInsetsCompat.CONSUMED
        }

        if (androidx.appcompat.app.AppCompatDelegate.getApplicationLocales().isEmpty) {
            val localeList = androidx.core.os.LocaleListCompat.forLanguageTags("es")
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(localeList)
        }

        wallpaperSettingsManager = com.diez.stoiclauncher.presentation.manager.WallpaperSettingsManager(this)
        widgetController = com.diez.stoiclauncher.presentation.controller.WidgetController(this)

        val appContainer = (application as StoicApplication).container
        viewModel = ViewModelProvider(this, viewModelFactory {
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
        })[HomeViewModel::class.java]

        setupPager()
        setupDock()

        lifecycleScope.launch {
            val settingsRepo = (application as StoicApplication).container.settingsRepository
            kotlinx.coroutines.flow.combine(
                settingsRepo.isWallpaperEnabled, settingsRepo.accentColor, settingsRepo.wallpaperUri
            ) { wp, col, uri -> Triple(wp, col, uri) }
                .collectLatest { (isWallpaper, accentColor, wallpaperUri) ->
                    wallpaperSettingsManager.applyWallpaperSettings(isWallpaper, accentColor, wallpaperUri)
                }
        }

        lifecycleScope.launch {
            viewModel.userEvents.collectLatest { message ->
                android.widget.Toast.makeText(this@MainActivity, message, android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        if (intent.getBooleanExtra("open_search", false)) {
            binding.viewPager.post { binding.viewPager.currentItem = 2 }
        }

        setupGestures()
    }

    private fun setupDock() {
        val btnWhatsapp = findViewById<View>(R.id.btn_whatsapp)
        val btnMaps = findViewById<View>(R.id.btn_maps)
        val btnSpotify = findViewById<View>(R.id.btn_spotify)
        val btnMessages = findViewById<View>(R.id.btn_messages)

        lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(
                viewModel.accentColor, viewModel.isWallpaperEnabled
            ) { color, isWallpaper -> color to isWallpaper }
                .collectLatest { (color, isWallpaper) ->
                    val contentColor = ColorHelper.getTextColorForAccent(color as Int, isWallpaper as Boolean)
                    val tint = android.content.res.ColorStateList.valueOf(contentColor)
                    
                    (btnWhatsapp as? android.widget.ImageView)?.imageTintList = tint
                    (btnMaps as? android.widget.ImageView)?.imageTintList = tint
                    (btnSpotify as? android.widget.ImageView)?.imageTintList = tint
                    (btnMessages as? android.widget.ImageView)?.imageTintList = tint
                }
        }

        val launchApp = { pkg: String ->
            val intent = packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                startActivity(intent)
            } else {
                android.widget.Toast.makeText(this, "App no instalada", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        btnWhatsapp.setOnClickListener { launchApp("com.whatsapp") }
        btnMaps.setOnClickListener { launchApp("com.google.android.apps.maps") }
        btnSpotify.setOnClickListener { launchApp("com.spotify.music") }
        btnMessages.setOnClickListener { launchApp("com.google.android.apps.messaging") }
    }

    private fun setupPager() {
        val adapter = MainPagerAdapter(this)
        binding.viewPager.adapter = adapter
        binding.viewPager.orientation = androidx.viewpager2.widget.ViewPager2.ORIENTATION_HORIZONTAL
        binding.viewPager.offscreenPageLimit = 2
        binding.viewPager.currentItem = 1

        binding.viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                if (position == 1) {
                    val alpha = 1f - positionOffset
                    binding.bottomBar.alpha = alpha
                    binding.bottomBar.visibility = if (alpha <= 0.05f) View.GONE else View.VISIBLE
                } else if (position == 2) {
                    binding.bottomBar.alpha = 0f
                    binding.bottomBar.visibility = View.GONE
                } else if (position == 0) {
                    binding.bottomBar.alpha = 1f
                    binding.bottomBar.visibility = View.VISIBLE
                }
            }

            override fun onPageSelected(position: Int) {
                if (position == 2) {
                    binding.bottomBar.visibility = View.GONE
                } else {
                    binding.bottomBar.visibility = View.VISIBLE
                    binding.bottomBar.alpha = 1f
                }
            }
        })

        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.BLACK

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (widgetController.handleBackPress()) return
                if (binding.viewPager.currentItem != 1) binding.viewPager.currentItem = 1
            }
        })
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent?.categories?.contains(Intent.CATEGORY_HOME) == true && intent.action == Intent.ACTION_MAIN) {
            binding.viewPager.currentItem = 1
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isDefaultLauncher()) promptSetDefaultLauncher()
    }

    override fun onStart() { super.onStart(); widgetController.onStart() }
    override fun onStop() { super.onStop(); widgetController.onStop() }

    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == packageName
    }

    private fun promptSetDefaultLauncher() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.set_default_title))
            .setMessage(getString(R.string.set_default_message))
            .setPositiveButton(getString(R.string.go_to_settings)) { _, _ ->
                try { startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS)) }
                catch (e: Exception) { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun attachWidgetContainer(container: ViewGroup) { widgetController.attachContainer(container) }
    override fun requestAddWidget() { widgetController.requestAddWidget() }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        widgetController.handleActivityResult(requestCode, resultCode, data)
    }

    private fun setupGestures() {
        val gestureDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                if (binding.viewPager.currentItem == 1) { executeGestureAction("DOUBLE_TAP"); return true }
                return false
            }
            override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (binding.viewPager.currentItem == 1 && e1 != null) {
                    val diffY = e2.y - e1.y
                    if (Math.abs(diffY) > Math.abs(e2.x - e1.x) && Math.abs(diffY) > 100 && velocityY > 100) {
                        executeGestureAction("SWIPE_DOWN"); return true
                    }
                }
                return false
            }
        })
        binding.viewPager.getChildAt(0).setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event); false }
    }

    private fun executeGestureAction(trigger: String) {
        lifecycleScope.launch {
            val mappings = (application as StoicApplication).container.settingsRepository.gestureMappingsFlow.first()
            val action = mappings[trigger]
            launch(kotlinx.coroutines.Dispatchers.Main) {
                when (action) {
                    "SEARCH" -> binding.viewPager.currentItem = 2
                    "NOTIFICATIONS" -> {
                        try {
                            val svc = getSystemService("statusbar")
                            val cls = Class.forName("android.app.StatusBarManager")
                            cls.getMethod("expandNotificationsPanel").invoke(svc)
                        } catch (e: Exception) {}
                    }
                    "FLASHLIGHT" -> toggleFlashlight()
                }
            }
        }
    }

    private var isFlashlightOn = false
    private fun toggleFlashlight() {
        try {
            val cm = getSystemService(android.content.Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val id = cm.cameraIdList[0]
            isFlashlightOn = !isFlashlightOn
            cm.setTorchMode(id, isFlashlightOn)
        } catch (e: Exception) {}
    }

    private fun showGroupDialog(app: AppModel) {
        val currentGroups = viewModel.uiState.value.mapNotNull { it.groupId }.distinct().sorted()
        val options = mutableListOf(getString(R.string.new_group_action))
        options.addAll(currentGroups)
        val menuOptions = options.map { MenuOption(it) }
        val bottomSheet = BottomSheetMenu(getString(R.string.add_to_group), menuOptions) { index ->
            if (index == 0) showNewGroupInputDialog(app)
            else viewModel.setAppGroup(app, options[index])
        }
        bottomSheet.show(supportFragmentManager, "AddToGroupSheet")
    }

    private fun showNewGroupInputDialog(app: AppModel) {
        showBottomSheetInput(getString(R.string.new_group_title), "", getString(R.string.group_name_hint)) { name ->
            if (name.isNotEmpty()) viewModel.setAppGroup(app, name)
        }
    }

    private fun showRenameGroupDialog(group: AppModel) {
        showBottomSheetInput(getString(R.string.rename_title), group.groupId ?: "", getString(R.string.group_name_hint)) { name ->
            if (name.isNotEmpty() && group.groupId != null) viewModel.renameGroup(group.groupId, name)
        }
    }

    private fun showBottomSheetInput(title: String, prefill: String, hint: String, onConfirm: (String) -> Unit) {
        val dlg = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_bottom_sheet_input, null)
        dlg.setContentView(view)
        val accentColor = viewModel.accentColor.value
        val contentColor = ColorHelper.getTextColorForAccent(accentColor, viewModel.isWallpaperEnabled.value)
        com.diez.stoiclauncher.presentation.util.UiHelper.setupBottomSheetColor(dlg, view, accentColor)
        val tvTitle = view.findViewById<android.widget.TextView>(R.id.tv_title)
        val etInput = view.findViewById<android.widget.EditText>(R.id.et_input)
        val btnConfirm = view.findViewById<android.widget.TextView>(R.id.btn_confirm)
        tvTitle.text = title; tvTitle.setTextColor(contentColor)
        etInput.setText(prefill); etInput.hint = hint
        etInput.setTextColor(android.graphics.Color.BLACK); etInput.setHintTextColor(android.graphics.Color.GRAY)
        etInput.selectAll()
        btnConfirm.setTextColor(contentColor)
        btnConfirm.setOnClickListener { onConfirm(etInput.text.toString().trim()); dlg.dismiss() }
        dlg.show(); etInput.requestFocus()
    }

    private fun showDeleteGroupConfirmation(group: AppModel) {
        val options = listOf(MenuOption(getString(R.string.delete_action)))
        val bottomSheet = BottomSheetMenu(
            getString(R.string.delete_group_message, group.label), options, viewModel.accentColor.value
        ) { index ->
            if (index == 0) group.groupId?.let { viewModel.deleteGroup(it) }
        }
        bottomSheet.show(supportFragmentManager, "DeleteGroup")
    }

    private fun showHideConfirmation(app: AppModel) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.hide_title))
            .setMessage(getString(R.string.hide_message, app.label))
            .setPositiveButton(getString(R.string.hide)) { _, _ -> viewModel.hideApp(app) }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showUsageLimitDialog(app: AppModel) {
        val options = arrayOf("Sin Límite", "15 minutos", "30 minutos", "45 minutos", "1 hora", "1h 30m", "2 horas", "3 horas")
        val values = intArrayOf(0, 15, 30, 45, 60, 90, 120, 180)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Límite para ${app.label}")
            .setItems(options) { _, which -> viewModel.setAppUsageLimit(app, values[which]) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showAppSelectionDialog(position: String) {
        lifecycleScope.launch {
            val apps = viewModel.getAppsForPicker().filter { !it.isGroup }.sortedBy { it.label.lowercase() }
            val menuOptions = listOf(MenuOption("Restaurar por defecto")) + apps.map { MenuOption(it.label) }
            val bottomSheet = BottomSheetMenu("Seleccionar App", menuOptions, viewModel.accentColor.value) { index ->
                if (index == 0) viewModel.setAppShortcut(position, null)
                else viewModel.setAppShortcut(position, apps[index - 1].packageName)
            }
            bottomSheet.show(supportFragmentManager, "app_picker")
        }
    }

    private inner class MainPagerAdapter(fa: androidx.fragment.app.FragmentActivity) : androidx.viewpager2.adapter.FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = 3
        override fun createFragment(position: Int): androidx.fragment.app.Fragment = when (position) {
            0 -> com.diez.stoiclauncher.presentation.home.fragments.WidgetsFragment()
            1 -> com.diez.stoiclauncher.presentation.home.fragments.HomeFragment()
            2 -> com.diez.stoiclauncher.presentation.home.fragments.DrawerFragment()
            else -> throw IllegalArgumentException("Invalid position $position")
        }
    }
}
