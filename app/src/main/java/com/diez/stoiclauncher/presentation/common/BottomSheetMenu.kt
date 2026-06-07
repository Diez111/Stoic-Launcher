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

        val color = accentColor ?: getThemeAccentColor()
        val isLight = com.diez.stoiclauncher.presentation.util.ColorHelper.isLightColor(color)
        val sheetColor = if (isLight) android.graphics.Color.parseColor("#1A1A1A") else color
        val contentColor = com.diez.stoiclauncher.presentation.util.ColorHelper.getTextColorForAccent(sheetColor)

        val dialog = dialog
        if (dialog != null) {
            val bgContainer = view.findViewById<android.view.View>(R.id.bottom_sheet_root)
            com.diez.stoiclauncher.presentation.util.UiHelper.setupBottomSheetColor(dialog, bgContainer, color)
        }

        val tvTitle = view.findViewById<TextView>(R.id.tv_title)
        tvTitle.text = title
        tvTitle.setTextColor(contentColor)

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
            view.setOnClickListener { onClick(bindingAdapterPosition) }
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
