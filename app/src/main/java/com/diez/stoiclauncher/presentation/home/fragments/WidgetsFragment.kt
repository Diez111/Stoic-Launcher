package com.diez.stoiclauncher.presentation.home.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.diez.stoiclauncher.R
import com.diez.stoiclauncher.presentation.home.HomeViewModel
import com.diez.stoiclauncher.presentation.home.fragments.WidgetContainerProvider

class WidgetsFragment : Fragment() {

    private val viewModel: HomeViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_widgets, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val tcClock = view.findViewById<View>(R.id.tc_clock)
        val tcDate = view.findViewById<View>(R.id.tc_date)
        val widgetContainer = view.findViewById<ViewGroup>(R.id.widget_container)
        val emptyMessage = view.findViewById<TextView>(R.id.tv_empty_widgets)
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
        
        // Widgets: This requires communication with Activity's WidgetManager
        val containerProvider = requireActivity() as? WidgetContainerProvider
        containerProvider?.attachWidgetContainer(widgetContainer)
        
        // Restore Widgets on Load (and observe changes if possible, but manual restore is safer for now)
        // Access ViewModel to get configs? Or direct from WidgetManager via Activity?
        // WidgetManager in Activity handles persistence.
        // Ideally we should observe flows. 
        // Let's use the provided widgetManager from Activity directly if accessible or trigger a restore.
        
        // Tricky: WidgetManager is in MainActivity.
        val mainActivity = requireActivity() as? com.diez.stoiclauncher.presentation.MainActivity
        if (mainActivity != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                 val appHelper = (requireActivity().application as com.diez.stoiclauncher.StoicApplication).container
                 
                 // Initial Restore
                 val configs = appHelper.settingsRepository.getAllWidgetConfigs()
                 mainActivity.widgetController.restoreWidgets(configs)
                 
                 // Observe Updates
                 appHelper.settingsRepository.widgetConfigs.collect { updatedConfigs ->
                     if (widgetContainer.childCount != updatedConfigs.filter { it.page == 0 || it.page == 1 }.size) {
                         mainActivity.widgetController.restoreWidgets(updatedConfigs)
                     }
                 }
            }
        }
        
        // Color Sync Flow - usar mapeo EXPLÍCITO de accentColor
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.accentColor.collect { color ->
                 val contentColor = com.diez.stoiclauncher.presentation.util.ColorHelper.getTextColorForAccent(color)
                 val secondaryColor = com.diez.stoiclauncher.presentation.util.ColorHelper.getSecondaryTextColorForAccent(color)
                 
                 (tcClock as? TextView)?.setTextColor(contentColor)
                 (tcDate as? TextView)?.setTextColor(secondaryColor)
                 emptyMessage.setTextColor(secondaryColor)

                 // Refresh Widget Themes
                 val mainActivity = requireActivity() as? com.diez.stoiclauncher.presentation.MainActivity
                 // Determine if the accent color implies a light background for widgets
                 val isLightBackground = com.diez.stoiclauncher.presentation.util.ColorHelper.isLightColor(color)
                 mainActivity?.widgetController?.refreshThemes(isLightBackground)
            }
        }
        
        // Hide empty message if widgets exist
        widgetContainer.viewTreeObserver.addOnGlobalLayoutListener {
            emptyMessage.visibility = if (widgetContainer.childCount > 1) View.GONE else View.VISIBLE
        }
        
        val widgetScroll = view.findViewById<View>(R.id.widget_scroll)
        
        // Background Long Press - attach to ALL views
        val longPressListener = View.OnLongClickListener {
            showWidgetOptions()
            true
        }
        
        view.setOnLongClickListener(longPressListener)
        widgetScroll.setOnLongClickListener(longPressListener)
        widgetContainer.setOnLongClickListener(longPressListener)
        emptyMessage.setOnLongClickListener(longPressListener)
    }
    
    private fun showWidgetOptions() {
        val options = listOf(
            com.diez.stoiclauncher.presentation.common.MenuOption(getString(R.string.add_widget)),
            com.diez.stoiclauncher.presentation.common.MenuOption(getString(R.string.settings))
        )
        
        val bottomSheet = com.diez.stoiclauncher.presentation.common.BottomSheetMenu(
            "Widgets", 
            options,
            viewModel.accentColor.value
        ) { index ->
            when (index) {
                0 -> (requireActivity() as? WidgetContainerProvider)?.requestAddWidget()
                1 -> startActivity(android.content.Intent(requireContext(), com.diez.stoiclauncher.presentation.settings.SettingsActivity::class.java))
            }
        }
        bottomSheet.show(parentFragmentManager, "widget_options")
    }
}
