package com.diez.stoiclauncher.presentation.home.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.diez.stoiclauncher.R
import com.diez.stoiclauncher.presentation.home.HomeViewModel
import com.diez.stoiclauncher.presentation.home.AppListAdapter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.text.TextWatcher
import android.text.Editable

class DrawerFragment : Fragment() {

    private val viewModel: HomeViewModel by activityViewModels()
    private lateinit var adapter: AppListAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_drawer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val etSearch = view.findViewById<EditText>(R.id.et_search)
        etSearch.hint = getString(R.string.search_hint)
        val btnSettings = view.findViewById<android.widget.ImageButton>(R.id.btn_settings)
        btnSettings.setOnClickListener {
             startActivity(android.content.Intent(requireContext(), com.diez.stoiclauncher.presentation.settings.SettingsActivity::class.java))
        }
        
        val rvAllApps = view.findViewById<RecyclerView>(R.id.rv_all_apps)
        
        adapter = AppListAdapter(
            onAppClick = { app ->
                 if (app.isGroup) {
                     showGroupDialog(app)
                 } else {
                      lifecycleScope.launch { com.diez.stoiclauncher.presentation.util.AppLaunchHelper.launchApp(requireContext(), app) }
                 }
            },
            onAppLongClick = { app ->
                 (requireActivity() as? AppActionListener)?.onAppLongClick(app) ?: false
            }
        )
        
        rvAllApps.layoutManager = LinearLayoutManager(requireContext())
        rvAllApps.adapter = adapter
        rvAllApps.setHasFixedSize(true)
        rvAllApps.setItemViewCacheSize(10)
        rvAllApps.itemAnimator = null
        
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.onSearchQueryChanged(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allAppsState.collectLatest { apps ->
                 adapter.submitList(apps)
            }
        }
        
        // Theme Colors
        viewLifecycleOwner.lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(
                viewModel.accentColor, viewModel.isWallpaperEnabled
            ) { color, wallpaper -> color to wallpaper }
            .collectLatest { (color, isWallpaper) ->
                 val contentColor = com.diez.stoiclauncher.presentation.util.ColorHelper.getTextColorForAccent(color, isWallpaper)
                 val secondaryColor = com.diez.stoiclauncher.presentation.util.ColorHelper.getSecondaryTextColorForAccent(color, isWallpaper)
                 
                 
                 val searchBgColor = if (contentColor == android.graphics.Color.WHITE) {
                     0x33FFFFFF.toInt() 
                 } else {
                     0x1A000000.toInt() 
                 }
                 
                  // Actualizar color manualmente iterando vistas visibles para evitar duplicación
                  // NO usar notifyDataSetChanged()
                  adapter.updateTextColor(contentColor, rvAllApps)
                 
                 etSearch.setTextColor(contentColor)
                 etSearch.setHintTextColor(secondaryColor)
                 etSearch.background?.setTint(searchBgColor)
                 
                 // Tint the settings icon
                 btnSettings.setColorFilter(contentColor)
            }
    } // End launch
     } // End onViewCreated

    override fun onResume() {
        super.onResume()
        // ALWAYS show keyboard when this fragment becomes visible
        // This is the most reliable lifecycle method - guaranteed to fire when user sees this screen
        showKeyboard()
    }
    
    override fun onPause() {
        super.onPause()
        // ALWAYS hide keyboard when leaving this fragment
        hideKeyboard()
    }
    
    private fun showKeyboard() {
        // Guard: Only show keyboard if fragment is properly attached
        if (!isAdded || view == null) return
        
        val etSearch = view?.findViewById<EditText>(R.id.et_search) ?: return
        
        // Clear previous search for fresh start
        etSearch.setText("")
        etSearch.requestFocus()
        
        // Post to ensure view is fully laid out and fragment still attached
        etSearch.post {
            // Double-check fragment is still attached (post happens async)
            if (!isAdded || context == null) return@post
            
            etSearch.requestFocus()
            val imm = context?.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            // SHOW_IMPLICIT is more polite than deprecated SHOW_FORCED
            imm?.showSoftInput(etSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }
    
    private fun hideKeyboard() {
        // Guard: Only hide keyboard if fragment is properly attached
        if (!isAdded || view == null) return
        
        val etSearch = view?.findViewById<EditText>(R.id.et_search) ?: return
        
        etSearch.clearFocus()
        val imm = context?.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(etSearch.windowToken, 0)
    }
    
    private fun showGroupDialog(groupApp: com.diez.stoiclauncher.domain.model.AppModel) {
        val groupId = groupApp.groupId ?: return
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
        
        // Force Grid Layout
        rvApps.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 4)
        val gridAdapter = com.diez.stoiclauncher.presentation.home.AppAdapter(
            onAppClick = { app ->
                lifecycleScope.launch { com.diez.stoiclauncher.presentation.util.AppLaunchHelper.launchApp(requireContext(), app) }
                dialog.dismiss()
            },
            onAppLongClick = { false },
            hideLabelsForSingleApps = false
        )
        gridAdapter.submitList(apps)
        rvApps.adapter = gridAdapter
        
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
