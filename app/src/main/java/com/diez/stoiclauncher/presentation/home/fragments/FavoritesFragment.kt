package com.diez.stoiclauncher.presentation.home.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
import com.diez.stoiclauncher.R
import com.diez.stoiclauncher.presentation.home.HomeViewModel
import com.diez.stoiclauncher.presentation.home.AppAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import com.diez.stoiclauncher.presentation.home.AppListAdapter

class FavoritesFragment : Fragment() {

    private val viewModel: HomeViewModel by activityViewModels()
    private lateinit var adapter: AppAdapter
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_favorites, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val tcClock = view.findViewById<View>(R.id.tc_clock)
        val tcDate = view.findViewById<View>(R.id.tc_date)
        val rvFavorites = view.findViewById<RecyclerView>(R.id.rv_favorites)

        // Launch Clock
        tcClock.setOnClickListener {
             com.diez.stoiclauncher.presentation.util.LaunchHelper.openClock(requireContext())
        }
        
        // Launch Calendar
        tcDate.setOnClickListener {
             com.diez.stoiclauncher.presentation.util.LaunchHelper.openCalendar(requireContext())
        }
        
        
        // Background Long Press
        view.setOnLongClickListener {
            showHomeOptions()
            true
        }
        
        // Setup favorites list
        adapter = AppAdapter(
            onAppClick = { app -> onAppClick(app) },
            onAppLongClick = { app ->
                 (requireActivity() as? AppActionListener)?.onAppLongClick(app) ?: false
            }
        )
        
        rvFavorites.layoutManager = LinearLayoutManager(requireContext())
        rvFavorites.adapter = adapter
        rvFavorites.setHasFixedSize(true)
        rvFavorites.setItemViewCacheSize(20)
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.favoritesState.collectLatest { apps ->
                adapter.submitList(apps)
                updateEmptyState(apps.isEmpty())
            }
        }
        
        // Background Long Press
        view.setOnLongClickListener {
            showHomeOptions()
            true
        }
        
        // Color Sync Flow - usar mapeo EXPLÍCITO de accentColor
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.accentColor.collectLatest { color ->
                 val contentColor = com.diez.stoiclauncher.presentation.util.ColorHelper.getTextColorForAccent(color)
                 val secondaryColor = com.diez.stoiclauncher.presentation.util.ColorHelper.getSecondaryTextColorForAccent(color)
                 
                 // Actualizar color manualmente iterando vistas visibles para evitar duplicación
                 val recyclerView = rvFavorites
                 if (recyclerView != null) {
                     adapter.updateTextColor(contentColor, recyclerView)
                 } else {
                      adapter.textColor = contentColor
                 }
                 
                 // UI Elements
                 (tcClock as? android.widget.TextView)?.setTextColor(contentColor)
                 (tcDate as? android.widget.TextView)?.setTextColor(secondaryColor)
                 view.findViewById<android.widget.TextView>(R.id.tv_empty_favorites)?.setTextColor(secondaryColor)
            }
        }
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        val emptyView = view?.findViewById<View>(R.id.tv_empty_favorites)
        val rv = view?.findViewById<View>(R.id.rv_favorites)
        
        if (isEmpty) {
            emptyView?.visibility = View.VISIBLE
            rv?.visibility = View.GONE
        } else {
            emptyView?.visibility = View.GONE
            rv?.visibility = View.VISIBLE
        }
    }
    
    private fun showGroupDialog(groupApp: com.diez.stoiclauncher.domain.model.AppModel) {
        val groupId = groupApp.groupId ?: return
        // Fix: Use getAppsInGroup to ensure we get ALL apps, not just favorites
        val apps = viewModel.getAppsInGroup(groupId)
        
        // Consistent minimal full screen folder
        val dialog = android.app.Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar)
        dialog.setContentView(R.layout.dialog_folder_overlay)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        
        val tvTitle = dialog.findViewById<android.widget.TextView>(R.id.tv_folder_title)
        val rvApps = dialog.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_folder_apps)
        val btnClose = dialog.findViewById<android.view.View>(R.id.btn_close_folder)
        
        tvTitle.text = groupId
        rvApps.layoutManager = LinearLayoutManager(requireContext())
        
        val dialogAdapter = AppListAdapter(
            onAppClick = { app ->
                com.diez.stoiclauncher.presentation.util.AppLaunchHelper.launchApp(requireContext(), app)
                dialog.dismiss()
            },
            onAppLongClick = { app ->
                // "Remove from Group" option
                val options = listOf(
                    com.diez.stoiclauncher.presentation.common.MenuOption(getString(R.string.remove_from_group)) // Ensure string exists or use hardcoded for now? 
                    // Let's use hardcoded "Quitar del Grupo" standard
                )
                val removeStr = "Quitar del Grupo"
                val menuOptions = listOf(com.diez.stoiclauncher.presentation.common.MenuOption(removeStr))
                
                val bottomSheet = com.diez.stoiclauncher.presentation.common.BottomSheetMenu(app.label, menuOptions) { index ->
                     if (index == 0) {
                         // Remove app from group
                         viewModel.setAppGroup(app, null)
                         dialog.dismiss() // Dismiss to refresh state or observe flow inside dialog?
                         // Dialog won't auto-refresh unless we observe flow here. 
                         // Simplest is to dismiss.
                     }
                }
                bottomSheet.show(parentFragmentManager, "remove_group_option")
                true
            }
        )
        
        // Wrap for AppListAdapter
        dialogAdapter.submitList(apps)
        rvApps.adapter = dialogAdapter
        
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
    
    private fun showHomeOptions() {
        val options = listOf(
            com.diez.stoiclauncher.presentation.common.MenuOption(getString(R.string.settings))
        )
        
        val bottomSheet = com.diez.stoiclauncher.presentation.common.BottomSheetMenu(
            "Favoritos", 
            options,
            viewModel.accentColor.value
        ) { index ->
            when (index) {
                0 -> startActivity(android.content.Intent(requireContext(), com.diez.stoiclauncher.presentation.settings.SettingsActivity::class.java))
            }
        }
        bottomSheet.show(parentFragmentManager, "home__options")
    }

    private fun onAppClick(app: com.diez.stoiclauncher.domain.model.AppModel) {
         if (app.isGroup) {
             showGroupDialog(app)
         } else {
             com.diez.stoiclauncher.presentation.util.AppLaunchHelper.launchApp(requireContext(), app)
         }
    }
}

