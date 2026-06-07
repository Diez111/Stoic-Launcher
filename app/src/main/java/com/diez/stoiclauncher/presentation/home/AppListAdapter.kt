package com.diez.stoiclauncher.presentation.home

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.diez.stoiclauncher.R
import com.diez.stoiclauncher.domain.model.AppModel
import android.widget.ImageView
import android.widget.TextView

class AppListAdapter(
    private val onAppClick: (AppModel) -> Unit,
    private val onAppLongClick: (AppModel) -> Boolean
) : ListAdapter<AppModel, AppListAdapter.AppViewHolder>(AppDiffCallback()) {

    companion object {
        private val MONO_FILTER = ColorMatrixColorFilter(
            ColorMatrix().apply { setSaturation(0f) }
        )
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_list, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private var _textColor: Int = android.graphics.Color.WHITE
    
    var textColor: Int
        get() = _textColor
        set(value) {
            _textColor = value
        }
    
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

    inner class AppViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.iv_icon)
        private val tvLabel: TextView = itemView.findViewById(R.id.tv_label)

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onAppClick(getItem(position))
                }
            }
            itemView.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onAppLongClick(getItem(position))
                } else {
                    false
                }
            }
        }

        fun bind(app: AppModel) {
            tvLabel.text = app.label
            tvLabel.setTextColor(textColor)
            
            if (app.icon != null) {
                ivIcon.setImageDrawable(app.icon)
                ivIcon.visibility = android.view.View.VISIBLE
                ivIcon.colorFilter = MONO_FILTER
            } else {
                ivIcon.visibility = android.view.View.GONE
            }
        }
        
        fun setTextColor(color: Int) {
            tvLabel.setTextColor(color)
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
