package com.diez.stoiclauncher.presentation.home.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
import com.diez.stoiclauncher.R
import com.diez.stoiclauncher.presentation.home.HomeViewModel
import com.diez.stoiclauncher.presentation.home.AppAdapter
import com.diez.stoiclauncher.presentation.home.AppListAdapter
import com.diez.stoiclauncher.domain.model.AppModel
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import com.diez.stoiclauncher.presentation.widget.WidgetManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetHost
import android.content.ComponentName
import com.diez.stoiclauncher.StoicApplication

class HomeFragment : Fragment() {

    private val viewModel: HomeViewModel by activityViewModels() // Share ViewModel with Activity
    private lateinit var adapter: AppAdapter
    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var appWidgetHost: AppWidgetHost
    private lateinit var settingsRepository: com.diez.stoiclauncher.domain.repository.SettingsRepository
    private var widgetContainer: ViewGroup? = null
    
    companion object {
        private const val APPWIDGET_HOST_ID = 1025
    }
    
    // We need to manage widgets here or in Activity?
    // Widgets are tricky in Fragments because AppWidgetHost needs Activity context and lifecycle.
    // Ideally, keep WidgetHost in Activity and pass container to Fragment? Or accessible via interface.
    // For simplicity, let's keep Widget logic simple or pass it down.
    // Actually, WidgetHostView is a View, so we can add it here.
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize widget components
        val appContainer = (requireActivity().application as StoicApplication).container
        settingsRepository = appContainer.settingsRepository
        
        val tcClock = view.findViewById<View>(R.id.tc_clock)
        val tcDate = view.findViewById<View>(R.id.tc_date)
        val rvFavorites = view.findViewById<RecyclerView>(R.id.rv_favorites)
        widgetContainer = view.findViewById<ViewGroup>(R.id.widget_container)
        val btnPhone = view.findViewById<View>(R.id.btn_phone)
        val btnSettings = view.findViewById<View>(R.id.btn_settings)

        // Launch Clock
        tcClock.setOnClickListener {
             try {
                val intent = android.content.Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
                startActivity(intent)
             } catch (e: Exception) {
                 // Fallback or ignore
             }
        }
        
        // Launch Calendar
        tcDate.setOnClickListener {
             try {
                val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
                intent.addCategory(android.content.Intent.CATEGORY_APP_CALENDAR)
                startActivity(intent)
             } catch (e: Exception) {
                 // Try generic calendar Intent
                 val calIntent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                 calIntent.data = android.net.Uri.parse("content://com.android.calendar/time/")
                 try { startActivity(calIntent) } catch (e2: Exception) {}
             }
        }
        
        // Shortcuts
        btnPhone.setOnClickListener {
             try {
                 val intent = android.content.Intent(android.content.Intent.ACTION_DIAL)
                 startActivity(intent)
             } catch (e: Exception) {}
        }
        
        btnSettings.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), com.diez.stoiclauncher.presentation.settings.SettingsActivity::class.java))
        }

        // Adapter for Favorites 
        adapter = AppAdapter(
            onAppClick = { app ->
                 if (app.isGroup) {
                     showGroupDialog(app)
                 } else {
                     com.diez.stoiclauncher.presentation.util.AppLaunchHelper.launchApp(requireContext(), app)
                 }
            },
            onAppLongClick = { app ->
                 (requireActivity() as? AppActionListener)?.onAppLongClick(app, "FAVORITES") ?: false
            },
            hideLabelsForSingleApps = true
        )
        
        rvFavorites.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 4)
        rvFavorites.adapter = adapter
        rvFavorites.setHasFixedSize(true) // Performance: size doesn't change
        rvFavorites.setItemViewCacheSize(20) // Performance: cache more items
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.favoritesState.collectLatest { apps ->
                adapter.submitList(apps)
            }
        }
        
        // Restore widgets from persistence via WidgetManager (Centralized)
        viewLifecycleOwner.lifecycleScope.launch {
            settingsRepository.widgetConfigs.collectLatest { configs ->
                widgetContainer?.let { container ->
                    (requireActivity() as? com.diez.stoiclauncher.presentation.MainActivity)?.widgetController?.restoreWidgets(configs)
                }
            }
        }
        
        // Widgets: This requires communication with Activity's WidgetManager
        (requireActivity() as? WidgetContainerProvider)?.attachWidgetContainer(widgetContainer!!)
        
        // Background Long Press for Options (Add Widget, etc)
        view.setOnLongClickListener {
            showHomeOptions()
            true
        }
    }
    
    private fun showHomeOptions() {
        val options = listOf(
            com.diez.stoiclauncher.presentation.common.MenuOption(getString(R.string.add_widget)),
            com.diez.stoiclauncher.presentation.common.MenuOption(getString(R.string.settings))
        )
        
        val bottomSheet = com.diez.stoiclauncher.presentation.common.BottomSheetMenu(
            "Home Options", 
            options,
            viewModel.accentColor.value
        ) { index ->
            when (index) {
                0 -> (requireActivity() as? WidgetContainerProvider)?.requestAddWidget()
                1 -> startActivity(android.content.Intent(requireContext(), com.diez.stoiclauncher.presentation.settings.SettingsActivity::class.java))
            }
        }
        bottomSheet.show(parentFragmentManager, "HomeMenu")
    }
    
    private fun showGroupDialog(groupApp: AppModel) {
        val groupId = groupApp.groupId ?: return
        val apps = viewModel.getAppsInGroup(groupId)
        
        // Full Screen Overlay Dialog (Minimalist)
        val dialog = android.app.Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar)
        dialog.setContentView(R.layout.dialog_folder_overlay)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        
        val tvTitle = dialog.findViewById<TextView>(R.id.tv_folder_title)
        val rvApps = dialog.findViewById<RecyclerView>(R.id.rv_folder_apps)
        val btnClose = dialog.findViewById<View>(R.id.btn_close_folder)
        
        tvTitle.text = groupId
        
        // Force Grid Layout (Minimalist)
        rvApps.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 4)
        val gridAdapter = AppAdapter(
            onAppClick = { app ->
                com.diez.stoiclauncher.presentation.util.AppLaunchHelper.launchApp(requireContext(), app)
                dialog.dismiss()
            },
            onAppLongClick = { app ->
                (requireActivity() as? AppActionListener)?.onAppLongClick(app, "GROUP") ?: false
            },
            hideLabelsForSingleApps = false
        )
        gridAdapter.submitList(apps)
        rvApps.adapter = gridAdapter
        
        btnClose.setOnClickListener { dialog.dismiss() }
        
        dialog.findViewById<View>(android.R.id.content).setOnClickListener { 
             // dialog.dismiss() 
        }
        
        dialog.show()
    }
}

interface AppActionListener {
    fun onAppLongClick(app: AppModel, source: String = "DRAWER"): Boolean
}

interface WidgetContainerProvider {
    fun attachWidgetContainer(container: ViewGroup)
    fun requestAddWidget()
}
