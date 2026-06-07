package com.diez.stoiclauncher.presentation.widget

import android.appwidget.AppWidgetProviderInfo
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.diez.stoiclauncher.databinding.ItemWidgetOptionBinding

class WidgetPickerAdapter(
    private val context: android.content.Context,
    private val allWidgets: List<AppWidgetProviderInfo>,
    private val onWidgetSelected: (AppWidgetProviderInfo) -> Unit
) : RecyclerView.Adapter<WidgetPickerAdapter.WidgetViewHolder>() {

    private var filteredWidgets: List<AppWidgetProviderInfo> = allWidgets

    fun filter(query: String) {
        filteredWidgets = if (query.isEmpty()) {
            allWidgets
        } else {
            allWidgets.filter { widget ->
                try {
                    val label = widget.loadLabel(context.packageManager).toString()
                    val packageName = widget.provider.packageName
                    val className = widget.provider.className
                    
                    // Search in label, package name, and class name
                    label.contains(query, ignoreCase = true) ||
                    packageName.contains(query, ignoreCase = true) ||
                    className.contains(query, ignoreCase = true)
                } catch (e: Exception) {
                    false // If can't load label, exclude from filter
                }
            }
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WidgetViewHolder {
        val binding = ItemWidgetOptionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return WidgetViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WidgetViewHolder, position: Int) {
        holder.bind(filteredWidgets[position])
    }

    override fun getItemCount() = filteredWidgets.size

    inner class WidgetViewHolder(
        private val binding: ItemWidgetOptionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onWidgetSelected(filteredWidgets[position])
                }
            }
        }

        fun bind(widgetInfo: AppWidgetProviderInfo) {
            binding.tvWidgetName.text = widgetInfo.loadLabel(binding.root.context.packageManager)
        }
    }
}
