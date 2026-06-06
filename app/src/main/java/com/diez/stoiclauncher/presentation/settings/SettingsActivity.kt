package com.diez.stoiclauncher.presentation.settings

import android.os.Bundle
import android.widget.CompoundButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.diez.stoiclauncher.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        // Ensure System Bars are Black / Transparent
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.BLACK
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.BLACK
        
        val btnBack = findViewById<android.view.View>(R.id.btn_back)
        val switchWallpaper = findViewById<SwitchCompat>(R.id.switch_wallpaper)
        val rvThemes = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_themes)
        
        btnBack.setOnClickListener { finish() }
        
        val appContainer = (application as com.diez.stoiclauncher.StoicApplication).container
        val settingsRepository = appContainer.settingsRepository
        
        // Initial State
        kotlinx.coroutines.MainScope().launch {
             settingsRepository.isWallpaperEnabled.collect { enabled ->
                 if (switchWallpaper.isChecked != enabled) {
                     switchWallpaper.isChecked = enabled
                 }
             }
        }
        
        switchWallpaper.setOnCheckedChangeListener { _, isChecked ->
             kotlinx.coroutines.MainScope().launch {
                 settingsRepository.setWallpaperEnabled(isChecked)
             }
        }
        
        // Setup Themes
        val themes = listOf(
            ThemeOption("Ónix", android.graphics.Color.parseColor("#000000")), 
            ThemeOption("Abedul", android.graphics.Color.parseColor("#EFEBE9")), 
            ThemeOption("Ceniza", android.graphics.Color.parseColor("#B0BEC5")),
            ThemeOption("Ronchi", android.graphics.Color.parseColor("#ECC15F")),
            ThemeOption("Galápagos", android.graphics.Color.parseColor("#4DB6AC")),
            ThemeOption("Lavanda", android.graphics.Color.parseColor("#9575CD")),
            ThemeOption("Sakura", android.graphics.Color.parseColor("#FFB7C5")), // Hello Kitty Pink
            ThemeOption("Nórdico", android.graphics.Color.parseColor("#81A1C1")),
            ThemeOption("Matcha", android.graphics.Color.parseColor("#A5D6A7")),
            ThemeOption("Ámbar", android.graphics.Color.parseColor("#FFCA28")),
            ThemeOption("Océano", android.graphics.Color.parseColor("#1565C0"))
        )
        // User asked for "Black which would be the default".
        // If current accent is missing or default, it should be Onyx.
        
        rvThemes.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
        rvThemes.setHasFixedSize(true)
        
        // Settings UI Theme Observer
        kotlinx.coroutines.MainScope().launch {
             kotlinx.coroutines.flow.combine(
                settingsRepository.isWallpaperEnabled,
                settingsRepository.accentColor,
                settingsRepository.wallpaperUri
            ) { wp, col, uri -> Triple(wp, col, uri) }
            .collect { (isWallpaper, accentColor, wallpaperUri) ->
                 
                 var isLight: Boolean
                 
                 if (isWallpaper && wallpaperUri != null) {
                     // Try to analyze wallpaper luminance
                     try {
                         val uri = android.net.Uri.parse(wallpaperUri)
                         val inputStream = contentResolver.openInputStream(uri)
                         val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                         if (bitmap != null) {
                             window.setBackgroundDrawable(android.graphics.drawable.BitmapDrawable(resources, bitmap))
                             isLight = com.diez.stoiclauncher.presentation.util.ColorHelper.isBitmapLight(bitmap)
                         } else {
                             window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
                             isLight = false
                         }
                     } catch (e: Exception) {
                         window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
                         isLight = false
                     }
                 } else if (isWallpaper) {
                     // System wallpaper (fallback to black background in settings)
                     window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
                     isLight = false
                 } else {
                     // Theme Mode
                     window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(accentColor))
                     isLight = com.diez.stoiclauncher.presentation.util.ColorHelper.isLightColor(accentColor)
                 }

                 // Apply system bar appearance (suppress deprecation - still needed for older APIs)
                 @Suppress("DEPRECATION")
                 window.navigationBarColor = if (isWallpaper && wallpaperUri != null) android.graphics.Color.TRANSPARENT else if (isWallpaper) android.graphics.Color.BLACK else accentColor
                 @Suppress("DEPRECATION")
                 window.statusBarColor = if (isWallpaper && wallpaperUri != null) android.graphics.Color.TRANSPARENT else if (isWallpaper) android.graphics.Color.BLACK else accentColor
                 
                 val contrastColor = if (isLight) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                 val secondaryColor = if (isLight) 0x99000000.toInt() else 0x99FFFFFF.toInt()
                 val inactiveColor = if (isLight) 0x66000000.toInt() else 0x66FFFFFF.toInt()

                 findViewById<android.widget.TextView>(R.id.tv_settings_title).setTextColor(contrastColor)
                 (btnBack as? android.widget.TextView)?.setTextColor(contrastColor)
                 findViewById<android.widget.TextView>(R.id.tv_header_general).setTextColor(secondaryColor)
                 findViewById<android.widget.TextView>(R.id.tv_header_widgets).setTextColor(secondaryColor)
                 findViewById<android.widget.TextView>(R.id.tv_header_wellbeing).setTextColor(secondaryColor)
                 findViewById<android.widget.TextView>(R.id.tv_header_style).setTextColor(secondaryColor)
                 
                 findViewById<android.widget.TextView>(R.id.tv_label_language).setTextColor(contrastColor)
                 findViewById<android.widget.TextView>(R.id.tv_label_wallpaper).setTextColor(contrastColor)
                 findViewById<android.widget.TextView>(R.id.tv_label_select_wallpaper).setTextColor(contrastColor)
                 findViewById<android.widget.TextView>(R.id.tv_label_hidden_apps).setTextColor(contrastColor)
                 findViewById<android.widget.TextView>(R.id.tv_label_usage_limit).setTextColor(contrastColor)
                 findViewById<android.widget.TextView>(R.id.tv_label_icon_pack).setTextColor(contrastColor)

                 findViewById<android.widget.TextView>(R.id.tv_label_accent_color).setTextColor(secondaryColor)
                 
                 // Update Adapter
                 (rvThemes.adapter as? ThemeAdapter)?.textColor = contrastColor

                 // Language Buttons
                 val btnEn = findViewById<android.widget.TextView>(R.id.btn_lang_en)
                 val btnEs = findViewById<android.widget.TextView>(R.id.btn_lang_es)
                 val currentLocales = androidx.core.os.LocaleListCompat.getAdjustedDefault()
                 val currentLang = if (!currentLocales.isEmpty) currentLocales.get(0)?.language else "en"

                 if (currentLang == "es") {
                     btnEs.setTextColor(contrastColor)
                     btnEn.setTextColor(inactiveColor)
                 } else {
                     btnEn.setTextColor(contrastColor)
                     btnEs.setTextColor(inactiveColor)
                 }

                 // Note: Reset widgets stays red for warning visibility
                 
                 androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = isLight
                    isAppearanceLightNavigationBars = isLight
                 }
            }
        }
        
        kotlinx.coroutines.MainScope().launch {
             settingsRepository.accentColor.collect { currentAccent ->
                 rvThemes.adapter = ThemeAdapter(themes, currentAccent) { selectedColor ->
                     kotlinx.coroutines.MainScope().launch {
                         settingsRepository.setAccentColor(selectedColor)
                         settingsRepository.setWallpaperEnabled(false) // Auto-disable wallpaper
                     }
                 }
             }
        }
        
        val btnEn = findViewById<android.widget.TextView>(R.id.btn_lang_en)
        val btnEs = findViewById<android.widget.TextView>(R.id.btn_lang_es)
        
        btnEn.setOnClickListener {
            val appLocale = androidx.core.os.LocaleListCompat.forLanguageTags("en")
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(appLocale)
        }
        
        btnEs.setOnClickListener {
            val appLocale = androidx.core.os.LocaleListCompat.forLanguageTags("es")
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(appLocale)
        }
        
        // Wallpaper Picker
        val pickImageLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                // Persist permission for the URI
                try {
                    val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(it, flags)
                } catch (e: Exception) {
                    android.util.Log.w("SettingsActivity", "Could not persist URI permission", e)
                }
                
                kotlinx.coroutines.MainScope().launch {
                    settingsRepository.setWallpaperUri(it.toString())
                    settingsRepository.setWallpaperEnabled(true) // Enable it
                }
            }
        }
        
        val btnSelectWallpaper = findViewById<android.view.View>(R.id.btn_select_wallpaper)
        btnSelectWallpaper.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
        
        val btnHiddenApps = findViewById<android.view.View>(R.id.btn_hidden_apps) // Now a layout
        btnHiddenApps.setOnClickListener {
             showHiddenAppsManager()
        }
        
        val btnResetWidgets = findViewById<android.view.View>(R.id.btn_reset_widgets)
        btnResetWidgets.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Limpiar Widgets")
                .setMessage("¿Estás seguro? Se eliminarán todos los widgets de la pantalla de inicio.")
                .setPositiveButton("Eliminar") { _, _ ->
                     val wm = com.diez.stoiclauncher.presentation.widget.WidgetManager(this)
                     wm.removeAllWidgets(null)
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
        
        // Usage Limits Logic
        val btnManageLimits = findViewById<android.view.View>(R.id.btn_manage_usage_limits)
        val tvUsageSubtitle = findViewById<android.widget.TextView>(R.id.tv_usage_status_subtitle)
        val appUsageManager = appContainer.appUsageManager
        
        fun updateUsageSubtitle(count: Int) {
            if (!appUsageManager.hasPermission()) {
                tvUsageSubtitle.text = "Requiere permiso"
                tvUsageSubtitle.setTextColor(android.graphics.Color.parseColor("#FF5252"))
            } else {
                 tvUsageSubtitle.setTextColor(android.graphics.Color.parseColor("#99FFFFFF")) // Reset color
                 if (count == 0) {
                     tvUsageSubtitle.text = "Ninguna app restringida"
                 } else {
                     tvUsageSubtitle.text = "$count apps restringidas"
                 }
            }
        }
        
        // Initial check
        updateUsageSubtitle(0) // Will update with flow
        
        kotlinx.coroutines.MainScope().launch {
            settingsRepository.allAppUsageLimits.collect { limits ->
                val count = limits.filter { it.value > 0 }.size
                updateUsageSubtitle(count)
            }
        }
        
        btnManageLimits.setOnClickListener {
             if (!appUsageManager.hasPermission()) {
                 androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Permiso Necesario")
                    .setMessage("Para gestionar límites, Stoic Launcher necesita acceso a estadísticas de uso.")
                    .setPositiveButton("Conceder") { _, _ -> appUsageManager.promptPermission() }
                    .setNegativeButton("Cancelar", null)
                    .show()
             } else {
                 showUsageLimitsManager()
             }
        }
        
        // Icon Pack Logic
        val btnIconPack = findViewById<android.view.View>(R.id.btn_icon_pack)
        val tvIconPackSubtitle = findViewById<android.widget.TextView>(R.id.tv_icon_pack_subtitle)
        
        kotlinx.coroutines.MainScope().launch {
            val appPreferences = com.diez.stoiclauncher.data.repository.AppPreferencesRepository(this@SettingsActivity)
            appPreferences.iconPackPackageFlow.collect { pack ->
                if (pack == null) {
                    tvIconPackSubtitle.text = "Predeterminado"
                } else if (pack == "stoic_builtin") {
                    tvIconPackSubtitle.text = "Stoic Pack"
                } else if (pack == "stoic_minimal") {
                    tvIconPackSubtitle.text = "Stoic Minimal"
                } else {
                    val label = try {
                        val info = packageManager.getApplicationInfo(pack, 0)
                        packageManager.getApplicationLabel(info)
                    } catch (e: Exception) {
                        "Desconocido"
                    }
                    tvIconPackSubtitle.text = label
                }
            }
        }
        
        btnIconPack.setOnClickListener {
            val intent = android.content.Intent("com.novalauncher.THEME")
            val resolveInfos = packageManager.queryIntentActivities(intent, 0)
            val options = mutableListOf<Pair<String?, String>>()
            options.add("stoic_builtin" to "Stoic Pack")
            options.add("stoic_minimal" to "Stoic Minimal")
            options.add(null to "Predeterminado")
            resolveInfos.forEach {
                val pkg = it.activityInfo.packageName
                val label = it.loadLabel(packageManager).toString()
                if (options.none { opt -> opt.first == pkg }) {
                    options.add(pkg to label)
                }
            }
            
            val labels = options.map { it.second }.toTypedArray()
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Seleccionar Pack de Iconos")
                .setItems(labels) { _, which ->
                    val selectedPack = options[which].first
                    kotlinx.coroutines.MainScope().launch {
                        val appPreferences = com.diez.stoiclauncher.data.repository.AppPreferencesRepository(this@SettingsActivity)
                        appPreferences.setIconPackPackage(selectedPack)
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh UI or check states if needed
        // For Usage Limit, the flow collects automatically?
        // Yes, but let's ensure we are robust.
        
        // Update "Desactivado" text to indicate if Permission is missing?
        val usageManager = com.diez.stoiclauncher.domain.usage.AppUsageManager(this, (application as com.diez.stoiclauncher.StoicApplication).container.settingsRepository)

    }
    
    private fun showHiddenAppsManager() {
        // Use full screen minimal dialog
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_folder_overlay)
        
        val tvTitle = dialog.findViewById<android.widget.TextView>(R.id.tv_folder_title)
        val rvApps = dialog.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_folder_apps)
        val btnClose = dialog.findViewById<android.view.View>(R.id.btn_close_folder)
        
        tvTitle.text = "Apps Ocultas"
        rvApps.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 4)
        
        val adapter = com.diez.stoiclauncher.presentation.home.AppAdapter(
            onAppClick = { app ->
                // Unhide
                kotlinx.coroutines.MainScope().launch {
                    val appRepository = (application as com.diez.stoiclauncher.StoicApplication).container.appRepository
                    appRepository.setAppHidden(app.uniqueId, false)
                    android.widget.Toast.makeText(this@SettingsActivity, "${app.label} visible", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onAppLongClick = { false },
            hideLabelsForSingleApps = false
        )
        // Fix for AppAdapter expecting ListItems if we reused code... 
        // Wait, AppAdapter in Settings was direct AppModel? 
        // Let's check imports. It refers to presentation.home.AppAdapter which takes ListItems usually IF updated.
        // But in previous view_file it was passed AppModels directly? 
        // Actually AppAdapter.kt takes List<AppModel> in submitList if it extends ListAdapter<AppModel, ...>
        // Let's check AppAdapter definition briefly. 
        // It consumes AppModel.
        
        rvApps.adapter = adapter
        
        btnClose.setOnClickListener { dialog.dismiss() }
        
        val job = kotlinx.coroutines.MainScope().launch {
            val appRepository = (application as com.diez.stoiclauncher.StoicApplication).container.appRepository
            appRepository.hiddenAppsFlow.collect { hiddenSet ->
                 val launcherApps = getSystemService(android.content.Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps
                 val userManager = getSystemService(android.content.Context.USER_SERVICE) as android.os.UserManager
                 
                 val resolvedApps = mutableListOf<com.diez.stoiclauncher.domain.model.AppModel>()
                 // ... resolution logic same as before ...
                 for (user in userManager.userProfiles) {
                     hiddenSet.forEach { packageName ->
                         // Note: hiddenSet stores uniqueId. 
                         // But logic splits package|user? Or just package?
                         // Current repo uses uniqueId.
                         // For legacy simplicity assuming packageName split or just loop.
                         // Let's just try to find by package.
                         val pkg = packageName.substringBefore("|")
                         try {
                             val activityList = launcherApps.getActivityList(pkg, user)
                             if (activityList.isNotEmpty()) {
                                 val info = activityList[0]
                                 resolvedApps.add(
                                     com.diez.stoiclauncher.domain.model.AppModel(
                                         label = info.label.toString(),
                                         packageName = pkg,
                                         icon = info.getIcon(0),
                                         user = user,
                                         category = null
                                     )
                                 )
                             }
                          } catch (e: Exception) {
                            android.util.Log.w("SettingsActivity", "Could not resolve hidden app: $packageName", e)
                          }
                      }
                  }
                 
                 adapter.submitList(resolvedApps)
                 if (resolvedApps.isEmpty()) {
                     tvTitle.text = "No hay apps ocultas"
                 } else {
                     tvTitle.text = "Toca para mostrar"
                 }
            }
        }
        
        dialog.setOnDismissListener { job.cancel() }
        dialog.show()
    }


    private fun showUsageLimitsManager() {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_folder_overlay)

        val tvTitle = dialog.findViewById<android.widget.TextView>(R.id.tv_folder_title)
        val rvApps = dialog.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_folder_apps)
        val btnClose = dialog.findViewById<android.view.View>(R.id.btn_close_folder)

        tvTitle.text = "Límites de Uso"

        rvApps.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

        btnClose.setOnClickListener { dialog.dismiss() }

        val usageManager = (application as com.diez.stoiclauncher.StoicApplication).container.appUsageManager
        val appContainer = (application as com.diez.stoiclauncher.StoicApplication).container

        val job = kotlinx.coroutines.MainScope().launch {
            appContainer.settingsRepository.allAppUsageLimits.collect { limits ->
                val restrictedPackages = limits.filter { it.value > 0 }

                val resolvedApps = mutableListOf<Triple<com.diez.stoiclauncher.domain.model.AppModel, Int, Int>>()
                val launcherApps = getSystemService(android.content.Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps
                val userManager = getSystemService(android.content.Context.USER_SERVICE) as android.os.UserManager

                restrictedPackages.forEach { (pkg, minutes) ->
                    try {
                        for (user in userManager.userProfiles) {
                            val list = launcherApps.getActivityList(pkg, user)
                            if (list.isNotEmpty()) {
                                val info = list[0]
                                val model = com.diez.stoiclauncher.domain.model.AppModel(
                                    label = info.label.toString(),
                                    packageName = pkg,
                                    icon = info.getIcon(0),
                                    user = user,
                                    category = null
                                )
                                val used = usageManager.getUsedMinutesToday(pkg)
                                resolvedApps.add(Triple(model, minutes, used))
                                break
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("SettingsActivity", "Could not resolve usage limit app: $pkg", e)
                    }
                }

                rvApps.adapter = UsageLimitAdapter(resolvedApps) { app, limitMin ->
                    showEditLimitSheet(app, limitMin, appContainer)
                }

                if (resolvedApps.isEmpty()) {
                    tvTitle.text = "Sin restricciones"
                } else {
                    tvTitle.text = "Apps Limitadas"
                }
            }
        }

        dialog.setOnDismissListener { job.cancel() }
        dialog.show()
    }

    private fun showEditLimitSheet(
        app: com.diez.stoiclauncher.domain.model.AppModel,
        currentLimit: Int,
        appContainer: com.diez.stoiclauncher.core.di.AppContainer
    ) {
        kotlinx.coroutines.MainScope().launch {
            val accentColor = appContainer.settingsRepository.accentColor.first()
            val options = listOf(
                "Sin límite" to 0, "5 minutos" to 5, "15 minutos" to 15,
                "30 minutos" to 30, "45 minutos" to 45, "1 hora" to 60,
                "1h 30m" to 90, "2 horas" to 120, "3 horas" to 180
            )
            val labels = options.map { (label, mins) ->
                if (mins == currentLimit) "$label ✓" else label
            }
            val vals = options.map { it.second }

            val bottomSheet = com.diez.stoiclauncher.presentation.common.BottomSheetMenu(
                app.label, labels.map { com.diez.stoiclauncher.presentation.common.MenuOption(it) },
                accentColor
            ) { index ->
                kotlinx.coroutines.MainScope().launch {
                    appContainer.settingsRepository.setAppUsageLimit(app.packageName, vals[index])
                }
            }
            bottomSheet.show(supportFragmentManager, "EditLimit")
        }
    }


    inner class UsageLimitAdapter(
        private val items: List<Triple<com.diez.stoiclauncher.domain.model.AppModel, Int, Int>>,
        private val onItemClick: (com.diez.stoiclauncher.domain.model.AppModel, Int) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<UsageLimitAdapter.ViewHolder>() {

        inner class ViewHolder(view: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
            val ivIcon: android.widget.ImageView = view.findViewById(R.id.iv_icon)
            val tvLabel: android.widget.TextView = view.findViewById(R.id.tv_label)
            val tvUsage: android.widget.TextView = view.findViewById(R.id.tv_usage)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_usage_limit, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val (app, limit, used) = items[position]
            holder.ivIcon.setImageDrawable(app.icon)
            holder.tvLabel.text = "${app.label} — $limit min/día"
            holder.tvUsage.text = "$used min usados hoy"

            val matrix = android.graphics.ColorMatrix()
            matrix.setSaturation(0f)
            holder.ivIcon.colorFilter = android.graphics.ColorMatrixColorFilter(matrix)

            holder.itemView.setOnClickListener { onItemClick(app, limit) }
        }

        override fun getItemCount() = items.size
    }
}
