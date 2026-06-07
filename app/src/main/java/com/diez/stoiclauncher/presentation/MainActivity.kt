package com.diez.stoiclauncher.presentation

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
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
        menuOptions.add(MenuOption("Agregar al dock"))
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
                2 -> addToDockFromApp(app)
                3 -> showHideConfirmation(app)
                4 -> {
                    try {
                        val intent = Intent(Intent.ACTION_DELETE)
                        intent.data = android.net.Uri.parse("package:${app.packageName}")
                        startActivity(intent)
                    } catch (e: Exception) {
                        android.util.Log.w("MainActivity", "Could not uninstall ${app.packageName}", e)
                    }
                }
                5 -> {
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = android.net.Uri.parse("package:${app.packageName}")
                        startActivity(intent)
                    } catch (e: Exception) {
                        android.util.Log.w("MainActivity", "Could not open app info", e)
                    }
                }
                6 -> {
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
        val rvDock = findViewById<RecyclerView>(R.id.rv_dock)
        val btnAdd = findViewById<ImageView>(R.id.btn_dock_add)
        val divider = findViewById<View>(R.id.dock_divider)
        val appPrefs = (application as StoicApplication).container.appPreferencesRepository
        val iconPackManager = (application as StoicApplication).container.iconPackManager

        val monoFilter = android.graphics.ColorMatrixColorFilter(
            android.graphics.ColorMatrix().apply { setSaturation(0f) }
        )

        val dockAdapter = DockAdapter(
            monoFilter = monoFilter,
            viewModel = viewModel,
            iconPackManager = iconPackManager,
            onAppClick = { pkg -> launchDockApp(pkg) },
            onAppDoubleTap = { pkg -> showDockAppMenu(pkg) }
        )

        rvDock.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
            this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false
        )
        rvDock.adapter = dockAdapter
        rvDock.setHasFixedSize(true)
        rvDock.itemAnimator = null

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT or ItemTouchHelper.START or ItemTouchHelper.END, 0
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                dockAdapter.moveItem(vh.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {}

            override fun onSelectedChanged(vh: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(vh, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    vh?.itemView?.apply {
                        scaleX = 1.2f; scaleY = 1.2f
                        alpha = 0.85f
                        translationZ = 16f
                    }
                }
            }

            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                vh.itemView.apply {
                    scaleX = 1f; scaleY = 1f
                    alpha = 1f; translationZ = 0f
                }
                lifecycleScope.launch { appPrefs.reorderDockApps(dockAdapter.getItems()) }
            }

            override fun onChildDraw(c: android.graphics.Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                dx: Float, dy: Float, actionState: Int, isActive: Boolean) {
                if (isActive) vh.itemView.elevation = 16f
                super.onChildDraw(c, rv, vh, dx, dy, actionState, isActive)
            }
        })
        itemTouchHelper.attachToRecyclerView(rvDock)

        lifecycleScope.launch {
            appPrefs.dockAppsFlow.collectLatest { apps ->
                dockAdapter.submitList(apps)
                val showDivider = apps.isNotEmpty()
                divider.visibility = if (showDivider) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(
                viewModel.accentColor, viewModel.isWallpaperEnabled
            ) { color, isWallpaper -> color to isWallpaper }
                .collectLatest { (color, isWallpaper) ->
                    val contentColor = ColorHelper.getTextColorForAccent(color as Int, isWallpaper as Boolean)
                    val tint = android.content.res.ColorStateList.valueOf(contentColor)
                    btnAdd.imageTintList = tint
                }
        }

        btnAdd.setOnClickListener { showDockAppPicker() }
        btnAdd.setOnLongClickListener {
            showDockAppPicker()
            true
        }
    }

    private fun launchDockApp(pkg: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) startActivity(intent)
        else android.widget.Toast.makeText(this, "App no instalada", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun showDockAppMenu(pkg: String) {
        val options = listOf(
            MenuOption("Quitar del dock"),
            MenuOption("Reemplazar")
        )
        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) { pkg }
        BottomSheetMenu(label, options, viewModel.accentColor.value) { index ->
            when (index) {
                0 -> {
                    val appPrefs = (application as StoicApplication).container.appPreferencesRepository
                    lifecycleScope.launch { appPrefs.removeDockApp(pkg) }
                }
                1 -> showDockReplaceDialog(pkg)
            }
        }.show(supportFragmentManager, "DockAppMenu")
    }

    private fun showDockReplaceDialog(currentPkg: String) {
        lifecycleScope.launch {
            val allApps = viewModel.getAppsForPicker().filter { !it.isGroup }
                .sortedBy { it.label.lowercase() }
            showAppPickerWithSearch("Reemplazar", allApps) { app ->
                val appPrefs = (application as StoicApplication).container.appPreferencesRepository
                lifecycleScope.launch {
                    appPrefs.removeDockApp(currentPkg)
                    appPrefs.addDockApp(app.packageName)
                }
            }
        }
    }

    private fun showDockAppPicker() {
        val appPrefs = (application as StoicApplication).container.appPreferencesRepository
        lifecycleScope.launch {
            val currentDock = appPrefs.dockAppsFlow.first()
            if (currentDock.size >= 6) {
                android.widget.Toast.makeText(this@MainActivity, "Máximo 6 apps en el dock", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            val apps = viewModel.getAppsForPicker().filter { app ->
                !app.isGroup && !currentDock.contains(app.packageName)
            }.sortedBy { it.label.lowercase() }

            if (apps.isEmpty()) {
                android.widget.Toast.makeText(this@MainActivity, "Todas las apps ya están en el dock", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }

            showAppPickerWithSearch("Agregar al dock", apps) { app ->
                lifecycleScope.launch { appPrefs.addDockApp(app.packageName) }
            }
        }
    }

    private fun showAppPickerWithSearch(title: String, apps: List<AppModel>, onSelected: (AppModel) -> Unit) {
        val dlg = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_app_picker, null)
        dlg.setContentView(view)

        val accentColor = viewModel.accentColor.value
        val isWallpaper = viewModel.isWallpaperEnabled.value
        val contentColor = ColorHelper.getTextColorForAccent(accentColor, isWallpaper)
        val secondaryColor = ColorHelper.getSecondaryTextColorForAccent(accentColor, isWallpaper)
        com.diez.stoiclauncher.presentation.util.UiHelper.setupBottomSheetColor(dlg, view, accentColor)

        val tvTitle = view.findViewById<android.widget.TextView>(R.id.tv_picker_title)
        val etSearch = view.findViewById<android.widget.EditText>(R.id.et_picker_search)
        val rvApps = view.findViewById<RecyclerView>(R.id.rv_picker_apps)

        tvTitle.text = title
        tvTitle.setTextColor(contentColor)
        etSearch.hint = "Buscar app..."
        etSearch.setTextColor(contentColor)
        etSearch.setHintTextColor(secondaryColor)

        val monoFilter = android.graphics.ColorMatrixColorFilter(
            android.graphics.ColorMatrix().apply { setSaturation(0f) }
        )

        val pickerAdapter = AppPickerAdapter(apps, monoFilter, contentColor) { app ->
            onSelected(app)
            dlg.dismiss()
        }

        rvApps.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        rvApps.adapter = pickerAdapter

        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s.toString().trim().lowercase()
                if (query.isEmpty()) {
                    pickerAdapter.resetFilter()
                } else {
                    val filtered = apps.filter { app ->
                        val label = java.text.Normalizer.normalize(app.label.lowercase(), java.text.Normalizer.Form.NFD)
                            .replace(Regex("\\p{M}"), "")
                        val q = java.text.Normalizer.normalize(query, java.text.Normalizer.Form.NFD)
                            .replace(Regex("\\p{M}"), "")
                        label.contains(q) || isSubsequenceLocal(q, label)
                    }
                    pickerAdapter.updateList(filtered)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        dlg.show()
        etSearch.requestFocus()
    }

    private fun addToDockFromApp(app: AppModel) {
        val appPrefs = (application as StoicApplication).container.appPreferencesRepository
        lifecycleScope.launch {
            val success = appPrefs.addDockApp(app.packageName)
            if (success) {
                android.widget.Toast.makeText(this@MainActivity, "${app.label} agregado al dock", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(this@MainActivity, "No se pudo agregar (lleno o ya existe)", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupPager() {
        val adapter = MainPagerAdapter(this)
        binding.viewPager.adapter = adapter
        binding.viewPager.orientation = androidx.viewpager2.widget.ViewPager2.ORIENTATION_HORIZONTAL
        binding.viewPager.offscreenPageLimit = 1
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
        val appPrefs = (application as StoicApplication).container.appPreferencesRepository
        val settingsRepo = (application as StoicApplication).container.settingsRepository
        lifecycleScope.launch {
            val groupList = appPrefs.userGroupsListFlow.first()
            val customNames = settingsRepo.customCategoryNames.first()

            if (groupList.isEmpty()) {
                androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("Sin grupos")
                    .setMessage("No hay grupos creados. Mantené presionado en la pantalla de inicio para crear uno.")
                    .setPositiveButton("Crear grupo") { _, _ ->
                        showNewGroupInputDialog(app)
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
                return@launch
            }

            val options = mutableListOf(getString(R.string.new_group_action))
            val labels = mutableListOf(getString(R.string.new_group_action))
            groupList.forEach { groupId ->
                val displayName = customNames[groupId] ?: groupId
                options.add(groupId)
                labels.add(displayName)
            }
            val menuOptions = labels.map { MenuOption(it) }
            val bottomSheet = BottomSheetMenu(getString(R.string.add_to_group), menuOptions) { index ->
                if (index == 0) showNewGroupInputDialog(app)
                else viewModel.setAppGroup(app, options[index])
            }
            bottomSheet.show(supportFragmentManager, "AddToGroupSheet")
        }
    }

    private fun showNewGroupInputDialog(app: AppModel) {
        val appPrefs = (application as StoicApplication).container.appPreferencesRepository
        showBottomSheetInput(getString(R.string.new_group_title), "", getString(R.string.group_name_hint)) { name ->
            if (name.isNotEmpty()) {
                lifecycleScope.launch {
                    appPrefs.addUserGroup(name)
                    viewModel.setAppGroup(app, name)
                }
            }
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
        val isWallpaper = viewModel.isWallpaperEnabled.value
        val contentColor = ColorHelper.getTextColorForAccent(accentColor, isWallpaper)
        val secondaryColor = ColorHelper.getSecondaryTextColorForAccent(accentColor, isWallpaper)
        com.diez.stoiclauncher.presentation.util.UiHelper.setupBottomSheetColor(dlg, view, accentColor)
        val tvTitle = view.findViewById<android.widget.TextView>(R.id.tv_title)
        val etInput = view.findViewById<android.widget.EditText>(R.id.et_input)
        val btnConfirm = view.findViewById<android.widget.TextView>(R.id.btn_confirm)
        tvTitle.text = title; tvTitle.setTextColor(contentColor)
        etInput.setText(prefill); etInput.hint = hint
        etInput.setTextColor(contentColor); etInput.setHintTextColor(secondaryColor)
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
        val usageManager = (application as StoicApplication).container.appUsageManager
        val dlg = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_usage_limit, null)
        dlg.setContentView(view)

        val accentColor = viewModel.accentColor.value
        val isWallpaper = viewModel.isWallpaperEnabled.value
        val contentColor = ColorHelper.getTextColorForAccent(accentColor, isWallpaper)
        val secondaryColor = ColorHelper.getSecondaryTextColorForAccent(accentColor, isWallpaper)
        com.diez.stoiclauncher.presentation.util.UiHelper.setupBottomSheetColor(dlg, view, accentColor)

        val tvTitle = view.findViewById<android.widget.TextView>(R.id.tv_usage_title)!!
        val tvUsed = view.findViewById<android.widget.TextView>(R.id.tv_usage_used)!!
        val tvLimit = view.findViewById<android.widget.TextView>(R.id.tv_usage_limit)!!
        val seekBar = view.findViewById<android.widget.SeekBar>(R.id.seekbar_limit)!!
        val btnSave = view.findViewById<android.widget.TextView>(R.id.btn_usage_save)!!

        tvTitle.text = "Límite: ${app.label}"
        tvTitle.setTextColor(contentColor)

        val usedMin = usageManager.getUsedMinutesToday(app.packageName)
        tvUsed.text = "Usado hoy: $usedMin min"
        tvUsed.setTextColor(secondaryColor)

        val limitOptions = intArrayOf(0, 5, 15, 30, 45, 60, 90, 120, 180)
        val limitLabels = arrayOf("Sin límite", "5 min", "15 min", "30 min", "45 min", "1 h", "1h 30m", "2 h", "3 h")

        lifecycleScope.launch {
            val currentLimit = (application as StoicApplication).container.settingsRepository.getAppUsageLimit(app.packageName).first()
            val currentIdx = limitOptions.indexOfFirst { it == currentLimit }.coerceAtLeast(0)
            seekBar.max = limitOptions.size - 1
            seekBar.progress = currentIdx

            tvLimit.text = limitLabels[currentIdx]
            tvLimit.setTextColor(contentColor)

            seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                    tvLimit.text = limitLabels[p]
                }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
            })
        }

        btnSave.text = "Guardar"
        btnSave.setTextColor(contentColor)
        btnSave.setOnClickListener {
            val minutes = limitOptions[seekBar.progress]
            viewModel.setAppUsageLimit(app, minutes)
            dlg.dismiss()
        }

        val btnRemove = view.findViewById<android.widget.TextView>(R.id.btn_usage_remove)!!
        btnRemove.text = "Sin límite"
        btnRemove.setTextColor(secondaryColor)
        btnRemove.setOnClickListener {
            viewModel.setAppUsageLimit(app, 0)
            dlg.dismiss()
        }

        dlg.show()
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

    private inner class DockAdapter(
        private val monoFilter: android.graphics.ColorMatrixColorFilter,
        private val viewModel: HomeViewModel,
        private val iconPackManager: com.diez.stoiclauncher.domain.util.IconPackManager,
        private val onAppClick: (String) -> Unit,
        private val onAppDoubleTap: (String) -> Unit
    ) : RecyclerView.Adapter<DockAdapter.VH>() {

        private var packages: List<String> = emptyList()

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.iv_dock_icon)
        }

        fun submitList(list: List<String>) {
            packages = list
            notifyDataSetChanged()
        }

        fun moveItem(fromPos: Int, toPos: Int) {
            val mutable = packages.toMutableList()
            val moved = mutable.removeAt(fromPos)
            mutable.add(toPos, moved)
            packages = mutable
            notifyItemMoved(fromPos, toPos)
        }

        fun getItems(): List<String> = packages

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_dock_app, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val pkg = packages[position]
            val appModel = viewModel.uiState.value.find { it.packageName == pkg }
            if (appModel?.icon != null) {
                holder.icon.setImageDrawable(appModel.icon)
                holder.icon.colorFilter = monoFilter
            } else {
                try {
                    val intent = packageManager.getLaunchIntentForPackage(pkg)
                    if (intent != null) {
                        val icon = iconPackManager.getIcon(intent.component!!)
                            ?: packageManager.getApplicationIcon(pkg)
                        holder.icon.setImageDrawable(icon)
                        holder.icon.colorFilter = monoFilter
                    }
                } catch (e: Exception) {
                    holder.icon.setImageResource(android.R.drawable.sym_def_app_icon)
                }
            }
            var lastTap = 0L
            holder.itemView.setOnClickListener {
                val now = System.currentTimeMillis()
                if (now - lastTap < 350) onAppDoubleTap(pkg)
                else onAppClick(pkg)
                lastTap = now
            }
        }

        override fun getItemCount() = packages.size
    }

    private inner class AppPickerAdapter(
        private val allApps: List<AppModel>,
        private val monoFilter: android.graphics.ColorMatrixColorFilter,
        private val textColor: Int,
        private val onAppClick: (AppModel) -> Unit
    ) : RecyclerView.Adapter<AppPickerAdapter.VH>() {

        private var filtered: List<AppModel> = allApps

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.iv_icon)
            val label: TextView = view.findViewById(R.id.tv_label)
        }

        fun resetFilter() { filtered = allApps; notifyDataSetChanged() }

        fun updateList(newList: List<AppModel>) { filtered = newList; notifyDataSetChanged() }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app_list, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val app = filtered[position]
            holder.label.text = app.label
            holder.label.setTextColor(textColor)
            if (app.icon != null) {
                holder.icon.setImageDrawable(app.icon)
                holder.icon.colorFilter = monoFilter
            }
            holder.itemView.setOnClickListener { onAppClick(app) }
        }

        override fun getItemCount() = filtered.size
    }

    private fun isSubsequenceLocal(query: String, target: String): Boolean {
        var i = 0; var j = 0
        while (i < query.length && j < target.length) { if (query[i] == target[j]) i++; j++ }
        return i == query.length
    }
}
