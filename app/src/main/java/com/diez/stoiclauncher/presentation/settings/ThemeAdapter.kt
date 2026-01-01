package com.diez.stoiclauncher.presentation.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.diez.stoiclauncher.R

data class ThemeOption(val name: String, val color: Int)

class ThemeAdapter(
    private val themes: List<ThemeOption>,
    private val currentAccent: Int,
    private val onThemeSelected: (Int) -> Unit
) : RecyclerView.Adapter<ThemeAdapter.ThemeViewHolder>() {

    var textColor: Int = android.graphics.Color.WHITE
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_theme_option, parent, false)
        return ThemeViewHolder(view)
    }

    override fun onBindViewHolder(holder: ThemeViewHolder, position: Int) {
        holder.bind(themes[position])
    }

    override fun getItemCount(): Int = themes.size

    inner class ThemeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val colorView: View = itemView.findViewById(R.id.view_color)
        private val nameView: android.widget.TextView = itemView.findViewById(R.id.tv_theme_name)
        private val selectionView: View = itemView.findViewById(R.id.view_selection_indicator)

        fun bind(theme: ThemeOption) {
            nameView.text = theme.name
            nameView.setTextColor(textColor)
            colorView.setBackgroundColor(theme.color)
            
            if (theme.color == currentAccent) {
                selectionView.visibility = View.VISIBLE
            } else {
                selectionView.visibility = View.GONE
            }

            itemView.setOnClickListener {
                onThemeSelected(theme.color)
            }
        }
    }
}
