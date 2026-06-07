package com.diez.stoiclauncher.presentation.settings

import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
import android.widget.CompoundButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.diez.stoiclauncher.R
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.roundToInt

class SettingsActivity : AppCompatActivity() {

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var boostActive = false
    private var lastAppliedLevel = -1
    private var cachedMaxVolumes: IntArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
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
        
        lifecycleScope.launch {
             settingsRepository.isWallpaperEnabled.collect { enabled ->
                 if (switchWallpaper.isChecked != enabled) switchWallpaper.isChecked = enabled
             }
        }
        
        switchWallpaper.setOnCheckedChangeListener { _, isChecked ->
             lifecycleScope.launch { settingsRepository.setWallpaperEnabled(isChecked) }
        }

        // === OPTIMIZED VOLUME BOOST ===
        val switchVolumeBoost = findViewById<SwitchCompat>(R.id.switch_volume_boost)
        val seekBarVolume = findViewById<android.widget.SeekBar>(R.id.seekbar_volume_boost)
        val tvVolumeLevel = findViewById<android.widget.TextView>(R.id.tv_volume_level_text)
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        lifecycleScope.launch {
            settingsRepository.volumeBoostEnabled.collect { enabled ->
                if (switchVolumeBoost.isChecked != enabled) switchVolumeBoost.isChecked = enabled
                seekBarVolume.isEnabled = enabled
                tvVolumeLevel.isEnabled = enabled
                boostActive = enabled
            }
        }

        lifecycleScope.launch {
            settingsRepository.volumeBoostLevel.collect { level ->
                if (seekBarVolume.progress != level) seekBarVolume.progress = level
                tvVolumeLevel.text = "${level}%"
                if (boostActive && level != lastAppliedLevel) {
                    updateGainOnly(audioManager, level)
                }
            }
        }

        switchVolumeBoost.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                settingsRepository.setVolumeBoostEnabled(isChecked)
                boostActive = isChecked
                if (isChecked) {
                    val level = settingsRepository.volumeBoostLevel.first()
                    applyBoost(audioManager, level, true)
                } else {
                    releaseBoost(audioManager)
                }
            }
        }

        seekBarVolume.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                tvVolumeLevel.text = "${p}%"
                if (!fromUser) return
                lifecycleScope.launch {
                    settingsRepository.setVolumeBoostLevel(p)
                    if (boostActive) updateGainOnly(audioManager, p)
                }
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })

        // Setup Themes
        val themes = listOf(
            ThemeOption("Ónix", android.graphics.Color.parseColor("#000000")), 
            ThemeOption("Abedul", android.graphics.Color.parseColor("#EFEBE9")), 
            ThemeOption("Ceniza", android.graphics.Color.parseColor("#B0BEC5")),
            ThemeOption("Ronchi", android.graphics.Color.parseColor("#ECC15F")),
            ThemeOption("Galápagos", android.graphics.Color.parseColor("#4DB6AC")),
            ThemeOption("Lavanda", android.graphics.Color.parseColor("#9575CD")),
            ThemeOption("Sakura", android.graphics.Color.parseColor("#FFB7C5")),
            ThemeOption("Nórdico", android.graphics.Color.parseColor("#81A1C1")),
            ThemeOption("Matcha", android.graphics.Color.parseColor("#A5D6A7")),
            ThemeOption("Ámbar", android.graphics.Color.parseColor("#FFCA28")),
            ThemeOption("Océano", android.graphics.Color.parseColor("#1565C0"))
        )
        
        rvThemes.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
        rvThemes.setHasFixedSize(true)
        
        lifecycleScope.launch {
             kotlinx.coroutines.flow.combine(
                settingsRepository.isWallpaperEnabled,
                settingsRepository.accentColor,
                settingsRepository.wallpaperUri
            ) { wp, col, uri -> Triple(wp, col, uri) }
            .collect { (isWallpaper, accentColor, wallpaperUri) ->
                 
                 var isLight: Boolean
                 
                 if (isWallpaper && wallpaperUri != null) {
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
                     window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
                     isLight = false
                 } else {
                     window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(accentColor))
                     isLight = com.diez.stoiclauncher.presentation.util.ColorHelper.isLightColor(accentColor)
                 }

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
                 findViewById<android.widget.TextView>(R.id.tv_header_volume).setTextColor(secondaryColor)
                 findViewById<android.widget.TextView>(R.id.tv_label_volume_boost).setTextColor(contrastColor)
                 findViewById<android.widget.TextView>(R.id.tv_volume_level_label).setTextColor(contrastColor)
                 findViewById<android.widget.TextView>(R.id.tv_volume_level_text).setTextColor(contrastColor)
                 
                 (rvThemes.adapter as? ThemeAdapter)?.textColor = contrastColor

                 val btnEn = findViewById<android.widget.TextView>(R.id.btn_lang_en)
                 val btnEs = findViewById<android.widget.TextView>(R.id.btn_lang_es)
                 val currentLocales = androidx.core.os.LocaleListCompat.getAdjustedDefault()
                 val currentLang = if (!currentLocales.isEmpty) currentLocales.get(0)?.language else "en"

                 if (currentLang == "es") {
                     btnEs.setTextColor(contrastColor); btnEn.setTextColor(inactiveColor)
                 } else {
                     btnEn.setTextColor(contrastColor); btnEs.setTextColor(inactiveColor)
                 }
                 
                 androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = isLight
                    isAppearanceLightNavigationBars = isLight
                 }
            }
        }
        
        lifecycleScope.launch {
             settingsRepository.accentColor.collect { currentAccent ->
                 rvThemes.adapter = ThemeAdapter(themes, currentAccent) { selectedColor ->
                     lifecycleScope.launch {
                         settingsRepository.setAccentColor(selectedColor)
                         settingsRepository.setWallpaperEnabled(false)
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
        
        val pickImageLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                try {
                    val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(it, flags)
                } catch (e: Exception) {}
                lifecycleScope.launch {
                    settingsRepository.setWallpaperUri(it.toString())
                    settingsRepository.setWallpaperEnabled(true)
                }
            }
        }
        val btnSelectWallpaper = findViewById<android.view.View>(R.id.btn_select_wallpaper)
        btnSelectWallpaper.setOnClickListener { pickImageLauncher.launch("image/*") }
        
        val btnHiddenApps = findViewById<android.view.View>(R.id.btn_hidden_apps)
        btnHiddenApps.setOnClickListener { showHiddenAppsManager() }
        
         val btnResetWidgets = findViewById<android.view.View>(R.id.btn_reset_widgets)
         btnResetWidgets.setOnClickListener {
             androidx.appcompat.app.AlertDialog.Builder(this)
                 .setTitle("Limpiar Widgets")
                 .setMessage("¿Estás seguro? Se eliminarán todos los widgets de la pantalla de inicio.")
                 .setPositiveButton("Eliminar") { _, _ ->
                      lifecycleScope.launch {
                          settingsRepository.clearAllWidgetConfigs()
                      }
                 }
                 .setNegativeButton("Cancelar", null).show()
         }
        
        val btnManageLimits = findViewById<android.view.View>(R.id.btn_manage_usage_limits)
        val tvUsageSubtitle = findViewById<android.widget.TextView>(R.id.tv_usage_status_subtitle)
        val appUsageManager = appContainer.appUsageManager
        
        fun updateUsageSubtitle(count: Int) {
            if (!appUsageManager.hasPermission()) {
                tvUsageSubtitle.text = "Requiere permiso"
                tvUsageSubtitle.setTextColor(android.graphics.Color.parseColor("#FF5252"))
            } else {
                 tvUsageSubtitle.setTextColor(android.graphics.Color.parseColor("#99FFFFFF"))
                 tvUsageSubtitle.text = if (count == 0) "Ninguna app restringida" else "$count apps restringidas"
            }
        }
        updateUsageSubtitle(0)
        
        lifecycleScope.launch {
            settingsRepository.allAppUsageLimits.collect { limits ->
                updateUsageSubtitle(limits.filter { it.value > 0 }.size)
            }
        }
        btnManageLimits.setOnClickListener {
             if (!appUsageManager.hasPermission()) {
                 androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Permiso Necesario")
                    .setMessage("Para gestionar límites, Stoic Launcher necesita acceso a estadísticas de uso.")
                    .setPositiveButton("Conceder") { _, _ -> appUsageManager.promptPermission() }
                    .setNegativeButton("Cancelar", null).show()
             } else showUsageLimitsManager()
        }
        
        val btnIconPack = findViewById<android.view.View>(R.id.btn_icon_pack)
        val tvIconPackSubtitle = findViewById<android.widget.TextView>(R.id.tv_icon_pack_subtitle)
         val appPreferences = appContainer.appPreferencesRepository
         lifecycleScope.launch {
             appPreferences.iconPackPackageFlow.collect { pack ->
                 tvIconPackSubtitle.text = when (pack) {
                     null -> "Predeterminado"
                     "stoic_builtin" -> "Stoic Pack"
                     "stoic_minimal" -> "Stoic Minimal"
                     else -> try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pack, 0)) } catch (e: Exception) { "Desconocido" }
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
                 val pkg = it.activityInfo.packageName; val label = it.loadLabel(packageManager).toString()
                 if (options.none { opt -> opt.first == pkg }) options.add(pkg to label)
             }
             val labels = options.map { it.second }.toTypedArray()
             androidx.appcompat.app.AlertDialog.Builder(this)
                 .setTitle("Seleccionar Pack de Iconos")
                 .setItems(labels) { _, which ->
                     lifecycleScope.launch {
                         appPreferences.setIconPackPackage(options[which].first)
                     }
                 }.setNegativeButton("Cancelar", null).show()
         }
    }
    
    override fun onResume() { super.onResume() }
    override fun onDestroy() { super.onDestroy(); releaseBoostSilent() }

    // ===== OPTIMIZED VOLUME BOOST ENGINE =====

    private suspend fun applyBoost(mgr: AudioManager, levelPercent: Int, showToast: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                val enhancer = ensureEnhancer()
                val gainMb = percentToGain(levelPercent)
                enhancer.setTargetGain(gainMb)
                enhancer.enabled = true
                maxAllStreams(mgr)
                lastAppliedLevel = levelPercent
                if (showToast) toastOnMain("Boost: +${gainMb/10}%")
            } catch (e: Exception) {
                android.util.Log.w("VolumeBoost", "LoudnessEnhancer failed, using raw volume", e)
                maxAllStreams(mgr)
                if (showToast) toastOnMain("Boost vía sistema (sin DSP)")
            }
        }
    }

    private suspend fun updateGainOnly(mgr: AudioManager, levelPercent: Int) {
        withContext(Dispatchers.IO) {
            try {
                ensureEnhancer().setTargetGain(percentToGain(levelPercent))
                lastAppliedLevel = levelPercent
            } catch (_: Exception) { }
        }
    }

    private fun releaseBoost(mgr: AudioManager) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                loudnessEnhancer?.enabled = false
                loudnessEnhancer?.release()
                loudnessEnhancer = null
                lastAppliedLevel = -1
            } catch (_: Exception) {}
            maxAllStreams(mgr)
            toastOnMain("Boost desactivado")
        }
    }

    private fun releaseBoostSilent() {
        lifecycleScope.launch(Dispatchers.IO) {
            try { loudnessEnhancer?.release() } catch (_: Exception) {}
            loudnessEnhancer = null
        }
    }

    private fun ensureEnhancer(): LoudnessEnhancer {
        return loudnessEnhancer ?: LoudnessEnhancer(0).also {
            loudnessEnhancer = it
        }
    }

    private fun maxAllStreams(mgr: AudioManager) {
        if (cachedMaxVolumes == null) {
            cachedMaxVolumes = intArrayOf(
                mgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                mgr.getStreamMaxVolume(AudioManager.STREAM_RING),
                mgr.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION),
                mgr.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            )
        }
        val streams = intArrayOf(
            AudioManager.STREAM_MUSIC, AudioManager.STREAM_RING,
            AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_ALARM
        )
        cachedMaxVolumes!!.forEachIndexed { i, max ->
            mgr.setStreamVolume(streams[i], (max * 0.9f).roundToInt().coerceIn(0, max), 0)
        }
    }

    private fun percentToGain(percent: Int): Int {
        if (percent <= 0) return 0
        val logVal = log10(percent.toDouble() / 100.0 * 9.0 + 1.0)
        return (logVal * 2000.0).roundToInt().coerceIn(0, 2000)
    }

    private fun toastOnMain(msg: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(this@SettingsActivity, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // ===== EXISTING METHODS (UNCHANGED) =====

    private fun showHiddenAppsManager() {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_folder_overlay)
        val tvTitle = dialog.findViewById<android.widget.TextView>(R.id.tv_folder_title)
        val rvApps = dialog.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_folder_apps)
        val btnClose = dialog.findViewById<android.view.View>(R.id.btn_close_folder)
        tvTitle.text = "Apps Ocultas"
        rvApps.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 4)
        val adapter = com.diez.stoiclauncher.presentation.home.AppAdapter(
            onAppClick = { app ->
                lifecycleScope.launch {
                    (application as com.diez.stoiclauncher.StoicApplication).container.appRepository
                        .setAppHidden(app.uniqueId, false)
                    android.widget.Toast.makeText(this@SettingsActivity, "${app.label} visible", android.widget.Toast.LENGTH_SHORT).show()
                }
            }, onAppLongClick = { false }, hideLabelsForSingleApps = false
        )
        rvApps.adapter = adapter; btnClose.setOnClickListener { dialog.dismiss() }
        val job = lifecycleScope.launch {
            val appRepository = (application as com.diez.stoiclauncher.StoicApplication).container.appRepository
            appRepository.hiddenAppsFlow.collect { hiddenSet ->
                 val launcherApps = getSystemService(android.content.Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps
                 val userManager = getSystemService(android.content.Context.USER_SERVICE) as android.os.UserManager
                 val resolvedApps = mutableListOf<com.diez.stoiclauncher.domain.model.AppModel>()
                 for (user in userManager.userProfiles) {
                     hiddenSet.forEach { packageName ->
                         val pkg = packageName.substringBefore("|")
                         try {
                             val activityList = launcherApps.getActivityList(pkg, user)
                             if (activityList.isNotEmpty()) {
                                 val info = activityList[0]
                                 resolvedApps.add(com.diez.stoiclauncher.domain.model.AppModel(
                                     label = info.label.toString(), packageName = pkg,
                                     icon = info.getIcon(0), user = user, category = null))
                             }
                          } catch (e: Exception) {}
                      }
                  }
                 adapter.submitList(resolvedApps)
                 tvTitle.text = if (resolvedApps.isEmpty()) "No hay apps ocultas" else "Toca para mostrar"
            }
        }
        dialog.setOnDismissListener { job.cancel() }; dialog.show()
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
        val job = lifecycleScope.launch {
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
                                val model = com.diez.stoiclauncher.domain.model.AppModel(label = info.label.toString(), packageName = pkg, icon = info.getIcon(0), user = user, category = null)
                                val used = usageManager.getUsedMinutesToday(pkg)
                                resolvedApps.add(Triple(model, minutes, used)); break
                            }
                        }
                    } catch (e: Exception) {}
                }
                rvApps.adapter = UsageLimitAdapter(resolvedApps) { app, limitMin -> showEditLimitSheet(app, limitMin, appContainer) }
                tvTitle.text = if (resolvedApps.isEmpty()) "Sin restricciones" else "Apps Limitadas"
            }
        }
        dialog.setOnDismissListener { job.cancel() }; dialog.show()
    }

    private fun showEditLimitSheet(app: com.diez.stoiclauncher.domain.model.AppModel, currentLimit: Int, appContainer: com.diez.stoiclauncher.core.di.AppContainer) {
        lifecycleScope.launch {
            val accentColor = appContainer.settingsRepository.accentColor.first()
            val options = listOf("Sin límite" to 0, "5 minutos" to 5, "15 minutos" to 15, "30 minutos" to 30, "45 minutos" to 45, "1 hora" to 60, "1h 30m" to 90, "2 horas" to 120, "3 horas" to 180)
            val labels = options.map { (label, mins) -> if (mins == currentLimit) "$label ✓" else label }
            val vals = options.map { it.second }
            com.diez.stoiclauncher.presentation.common.BottomSheetMenu(app.label, labels.map { com.diez.stoiclauncher.presentation.common.MenuOption(it) }, accentColor) { index ->
                lifecycleScope.launch { appContainer.settingsRepository.setAppUsageLimit(app.packageName, vals[index]) }
            }.show(supportFragmentManager, "EditLimit")
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
            return ViewHolder(android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_usage_limit, parent, false))
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val (app, limit, used) = items[position]
            holder.ivIcon.setImageDrawable(app.icon)
            holder.tvLabel.text = "${app.label} — $limit min/día"
            holder.tvUsage.text = "$used min usados hoy"
            val m = android.graphics.ColorMatrix(); m.setSaturation(0f)
            holder.ivIcon.colorFilter = android.graphics.ColorMatrixColorFilter(m)
            holder.itemView.setOnClickListener { onItemClick(app, limit) }
        }
        override fun getItemCount() = items.size
    }
}