package com.diez.stoiclauncher.presentation.home

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.diez.stoiclauncher.databinding.ItemAppBinding
import com.diez.stoiclauncher.domain.model.AppModel

class AppAdapter(
    private val onAppClick: (AppModel) -> Unit,
    private val onAppLongClick: (AppModel) -> Boolean,
    private val hideLabelsForSingleApps: Boolean = false
) : ListAdapter<AppModel, AppAdapter.AppViewHolder>(AppDiffCallback()) {

    companion object {
        private val MONO_FILTER = ColorMatrixColorFilter(
            ColorMatrix().apply { setSaturation(0f) }
        )
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private var _textColor: Int = android.graphics.Color.WHITE
    
    var textColor: Int
        get() = _textColor
        set(value) {
            _textColor = value
            // notifyDataSetChanged() - NO USAR, causa glitches visuales
        }
    
    // Actualizar color sin re-renderizar toda la lista
    fun updateTextColor(color: Int, recyclerView: RecyclerView) {
        _textColor = color
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            val holder = recyclerView.getChildViewHolder(child)
            if (holder is AppViewHolder) {
                holder.setTextColor(color)
            }
        }
    }

    inner class AppViewHolder(private val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onAppClick(getItem(position))
                }
            }
            binding.root.setOnLongClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onAppLongClick(getItem(position))
                } else {
                    false
                }
            }
        }

        fun bind(app: AppModel) {
            // Hide label if configured and not a group
            if (hideLabelsForSingleApps && !app.isGroup) {
                binding.tvLabel.visibility = android.view.View.GONE
            } else {
                binding.tvLabel.visibility = android.view.View.VISIBLE
                binding.tvLabel.text = app.label
                binding.tvLabel.setTextColor(textColor)
            }
            
            // Icon & Monochrome Filter
            if (app.icon != null) {
                binding.ivIcon.setImageDrawable(app.icon)
                binding.ivIcon.visibility = android.view.View.VISIBLE
                binding.ivIcon.colorFilter = MONO_FILTER
            } else {
                binding.ivIcon.visibility = android.view.View.GONE
            }
        }
        
        fun setTextColor(color: Int) {
            binding.tvLabel.setTextColor(color)
        }
    }

    class AppDiffCallback : DiffUtil.ItemCallback<AppModel>() {
        override fun areItemsTheSame(oldItem: AppModel, newItem: AppModel): Boolean {
            return oldItem.uniqueId == newItem.uniqueId
        }

        override fun areContentsTheSame(oldItem: AppModel, newItem: AppModel): Boolean {
            return oldItem == newItem
        }
    }
}
