package com.diez.stoiclauncher.presentation.common

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.diez.stoiclauncher.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class BottomSheetMenu(
    private val title: String,
    private val options: List<MenuOption>,
    private val accentColor: Int? = null, // Optional accent color override
    private val onOptionSelected: (Int) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.layout_bottom_sheet_menu, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Resolve Color
        val color = accentColor ?: getThemeAccentColor()
        
        // Adapt Colors based on background luminance
        val contentColor = com.diez.stoiclauncher.presentation.util.ColorHelper.getTextColorForAccent(color)
        val secondaryColor = com.diez.stoiclauncher.presentation.util.ColorHelper.getSecondaryTextColorForAccent(color)
        
        // 1. Tint Background & Setup System Bars (using new Helper)
        // Access dialog to set window properties
        val dialog = dialog
        if (dialog != null) {
            val bgContainer = view.findViewById<android.view.View>(R.id.bottom_sheet_root)
            com.diez.stoiclauncher.presentation.util.UiHelper.setupBottomSheetColor(dialog, bgContainer, color)
        } else {
             // Fallback if dialog is null? Should not happen in onViewCreated usually if shown.
             // But just in case, manual tint
             val bgContainer = view.findViewById<android.view.View>(R.id.bottom_sheet_root)
             val bgDrawable = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.shape_bottom_sheet)?.mutate()
             androidx.core.graphics.drawable.DrawableCompat.setTint(bgDrawable!!, color)
             bgContainer?.background = bgDrawable
        }
        
        // 2. Tint Title
        val tvTitle = view.findViewById<TextView>(R.id.tv_title)
        tvTitle.text = title
        tvTitle.setTextColor(contentColor)
        
        // 3. Setup List with Colors
        val rvOptions = view.findViewById<RecyclerView>(R.id.rv_options)
        rvOptions.layoutManager = LinearLayoutManager(requireContext())
        rvOptions.adapter = MenuAdapter(options, contentColor) { index ->
            onOptionSelected(index)
            dismiss()
        }
    }
    
    private fun getThemeAccentColor(): Int {
        // Fallback or fetch from VM if possible? 
        // Better to pass it in constructor to keep View dumb.
        // Assuming black for fallback
        return android.graphics.Color.BLACK 
    }
}

data class MenuOption(val label: String, val iconRes: Int? = null)

private class MenuAdapter(
    private val options: List<MenuOption>,
    private val textColor: Int,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<MenuAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvLabel: TextView = view.findViewById(R.id.tv_option_label)
        
        init {
            view.setOnClickListener { onClick(adapterPosition) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_menu_option, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.tvLabel.text = options[position].label
        holder.tvLabel.setTextColor(textColor)
    }

    override fun getItemCount() = options.size
}
