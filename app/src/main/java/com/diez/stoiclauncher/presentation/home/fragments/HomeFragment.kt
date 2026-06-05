package com.diez.stoiclauncher.presentation.home.fragments

import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.diez.stoiclauncher.R
import com.diez.stoiclauncher.StoicApplication
import com.diez.stoiclauncher.domain.model.AppModel
import com.diez.stoiclauncher.presentation.home.HomeViewModel
import com.diez.stoiclauncher.presentation.util.AppLaunchHelper
import com.diez.stoiclauncher.presentation.util.ColorHelper
import com.diez.stoiclauncher.presentation.util.LaunchHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private val viewModel: HomeViewModel by activityViewModels()
    private lateinit var rvBubbles: RecyclerView
    private lateinit var bubbleAdapter: BubbleAdapter
    private val matrix = ColorMatrix().apply { setSaturation(0f) }
    private val monoFilter = ColorMatrixColorFilter(matrix)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        rvBubbles = view.findViewById(R.id.rv_bubbles)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tcClock = view.findViewById<View>(R.id.tc_clock)
        val tcDate = view.findViewById<View>(R.id.tc_date)

        tcClock.setOnClickListener { LaunchHelper.openClock(requireContext()) }
        tcDate.setOnClickListener { LaunchHelper.openCalendar(requireContext()) }

        val settingsRepo = (requireActivity().application as StoicApplication).container.settingsRepository

        bubbleAdapter = BubbleAdapter(
            monoFilter = monoFilter,
            onBubbleClick = { category -> showCategoryDialog(category) },
            onBubbleLongClick = { category -> showBubbleOptions(category); true }
        )

        rvBubbles.layoutManager = GridLayoutManager(requireContext(), 2)
        rvBubbles.adapter = bubbleAdapter
        rvBubbles.setHasFixedSize(true)
        rvBubbles.isNestedScrollingEnabled = false
        rvBubbles.overScrollMode = View.OVER_SCROLL_NEVER

        // Combine all data sources for bubbles
        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                viewModel.uiState,
                settingsRepo.hiddenCategories,
                settingsRepo.customCategoryNames
            ) { apps, hidden, customNames -> Triple(apps, hidden, customNames) }
                .collectLatest { (apps, hidden, customNames) ->
                    if (apps.isNotEmpty()) {
                        val allCategories = com.diez.stoiclauncher.domain.util.AppCategorizer.groupByCategory(apps)
                        val visibleCategories = allCategories.filter { !hidden.contains(it.name) }
                            .map { cat ->
                                val displayName = customNames[cat.name] ?: cat.name
                                CategoryGroup(displayName, cat.apps, cat.name)
                            }
                        bubbleAdapter.submitCategories(visibleCategories)
                    }
                }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                viewModel.accentColor, viewModel.isWallpaperEnabled
            ) { color, wallpaper -> color to wallpaper }
                .collectLatest { (color, isWallpaper) ->
                    val contentColor = ColorHelper.getTextColorForAccent(color, isWallpaper)
                    val secondaryColor = ColorHelper.getSecondaryTextColorForAccent(color, isWallpaper)

                    (tcClock as? TextView)?.setTextColor(contentColor)
                    (tcDate as? TextView)?.setTextColor(secondaryColor)
                    bubbleAdapter.updateColors(contentColor, secondaryColor)
                }
        }

        rvBubbles.viewTreeObserver.addOnGlobalLayoutListener {
            bubbleAdapter.updateHeight(rvBubbles)
        }

        view.setOnLongClickListener {
            val options = listOf(
                com.diez.stoiclauncher.presentation.common.MenuOption(getString(R.string.settings))
            )
            com.diez.stoiclauncher.presentation.common.BottomSheetMenu(
                "", options, viewModel.accentColor.value
            ) { index ->
                when (index) {
                    0 -> startActivity(android.content.Intent(requireContext(),
                        com.diez.stoiclauncher.presentation.settings.SettingsActivity::class.java))
                }
            }.show(parentFragmentManager, "home_options")
            true
        }
    }

    private fun showCategoryDialog(category: CategoryGroup) {
        val dialog = android.app.Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar)
        dialog.setContentView(R.layout.dialog_folder_overlay)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        val tvTitle = dialog.findViewById<TextView>(R.id.tv_folder_title)
        val rvApps = dialog.findViewById<RecyclerView>(R.id.rv_folder_apps)
        val btnClose = dialog.findViewById<View>(R.id.btn_close_folder)

        tvTitle.text = category.name

        rvApps.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 4)
        val gridAdapter = com.diez.stoiclauncher.presentation.home.AppAdapter(
            onAppClick = { app ->
                AppLaunchHelper.launchApp(requireContext(), app)
                dialog.dismiss()
            },
            onAppLongClick = { app ->
                (requireActivity() as? AppActionListener)?.onAppLongClick(app, "CATEGORY") ?: false
            },
            hideLabelsForSingleApps = false
        )
        gridAdapter.submitList(category.apps)
        rvApps.adapter = gridAdapter

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showBubbleOptions(category: CategoryGroup) {
        val settingsRepo = (requireActivity().application as StoicApplication).container.settingsRepository
        val options = listOf(
            com.diez.stoiclauncher.presentation.common.MenuOption("Renombrar"),
            com.diez.stoiclauncher.presentation.common.MenuOption("Ocultar categoría"),
            com.diez.stoiclauncher.presentation.common.MenuOption("Editar apps")
        )
        com.diez.stoiclauncher.presentation.common.BottomSheetMenu(
            category.name, options, viewModel.accentColor.value
        ) { index ->
            when (index) {
                0 -> showRenameCategoryDialog(category, settingsRepo)
                1 -> lifecycleScope.launch { settingsRepo.toggleHiddenCategory(category.originalName) }
                2 -> showEditAppsDialog(category)
            }
        }.show(parentFragmentManager, "bubble_options")
    }

    private fun showRenameCategoryDialog(category: CategoryGroup, settingsRepo: com.diez.stoiclauncher.domain.repository.SettingsRepository) {
        val dlg = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.layout_bottom_sheet_input, null)
        dlg.setContentView(view)
        val accentColor = viewModel.accentColor.value
        val contentColor = ColorHelper.getTextColorForAccent(accentColor, viewModel.isWallpaperEnabled.value)
        com.diez.stoiclauncher.presentation.util.UiHelper.setupBottomSheetColor(dlg, view, accentColor)
        val tvTitle = view.findViewById<android.widget.TextView>(R.id.tv_title)
        val etInput = view.findViewById<android.widget.EditText>(R.id.et_input)
        val btnConfirm = view.findViewById<android.widget.TextView>(R.id.btn_confirm)
        tvTitle.text = "Renombrar categoría"
        tvTitle.setTextColor(contentColor)
        etInput.setText(category.name)
        etInput.hint = "Nuevo nombre"
        etInput.setTextColor(android.graphics.Color.BLACK)
        etInput.setHintTextColor(android.graphics.Color.GRAY)
        etInput.selectAll()
        btnConfirm.setTextColor(contentColor)
        btnConfirm.setOnClickListener {
            val newName = etInput.text.toString().trim()
            if (newName.isNotEmpty()) {
                lifecycleScope.launch {
                    settingsRepo.setCustomCategoryName(category.originalName, newName)
                }
            }
            dlg.dismiss()
        }
        dlg.show()
        etInput.requestFocus()
    }

    private fun showEditAppsDialog(category: CategoryGroup) {
        // Show all apps in this category with ability to move them to other categories
        val dialog = android.app.Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar)
        dialog.setContentView(R.layout.dialog_folder_overlay)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        val tvTitle = dialog.findViewById<TextView>(R.id.tv_folder_title)
        val rvApps = dialog.findViewById<RecyclerView>(R.id.rv_folder_apps)
        val btnClose = dialog.findViewById<View>(R.id.btn_close_folder)

        tvTitle.text = "Editar: ${category.name}"

        rvApps.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        val editAdapter = CategoryEditAdapter(
            apps = category.apps,
            monoFilter = monoFilter,
            onMoveApp = { app -> showMoveAppDialog(app, dialog) }
        )
        rvApps.adapter = editAdapter

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showMoveAppDialog(app: AppModel, parentDialog: android.app.Dialog) {
        val categories = listOf("Social", "Trabajo", "Entretenimiento", "Finanzas", "Sistema", "Otros")
        val options = categories.map { com.diez.stoiclauncher.presentation.common.MenuOption(it) }
        com.diez.stoiclauncher.presentation.common.BottomSheetMenu(
            "Mover ${app.label} a:", options, viewModel.accentColor.value
        ) { index ->
            // Note: This is a visual-only move for the current session.
            // Real app categorization requires changing the package category or creating a custom mapping.
            // For now, we just show a toast.
            android.widget.Toast.makeText(requireContext(), "Movido a ${categories[index]}", android.widget.Toast.LENGTH_SHORT).show()
        }.show(parentFragmentManager, "move_app")
    }
}

data class CategoryGroup(
    val name: String,
    val apps: List<AppModel>,
    val originalName: String = name
)

class BubbleAdapter(
    private val monoFilter: ColorMatrixColorFilter,
    private val onBubbleClick: (CategoryGroup) -> Unit,
    private val onBubbleLongClick: (CategoryGroup) -> Boolean
) : RecyclerView.Adapter<BubbleAdapter.VH>() {

    private var categories: List<CategoryGroup> = emptyList()
    private var textColor = Color.WHITE
    private var secondaryColor = 0xB3FFFFFF.toInt()
    private var itemHeight: Int? = null

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tv_bubble_title)
        val count: TextView = view.findViewById(R.id.tv_bubble_count)
        val icon1: ImageView = view.findViewById(R.id.iv_icon_1)
        val icon2: ImageView = view.findViewById(R.id.iv_icon_2)
        val icon3: ImageView = view.findViewById(R.id.iv_icon_3)
        val icon4: ImageView = view.findViewById(R.id.iv_icon_4)
    }

    fun submitCategories(list: List<CategoryGroup>) {
        categories = list
        notifyDataSetChanged()
    }

    fun updateColors(text: Int, secondary: Int) {
        textColor = text
        secondaryColor = secondary
        notifyDataSetChanged()
    }

    fun updateHeight(rv: RecyclerView) {
        val rows = kotlin.math.max(1, Math.ceil(categories.size / 2.0).toInt())
        val availableHeight = rv.measuredHeight - rv.paddingTop - rv.paddingBottom
        if (availableHeight > 0) {
            val margins = 16 * rv.context.resources.displayMetrics.density
            val newHeight = (availableHeight / rows) - margins.toInt()
            if (itemHeight != newHeight) {
                itemHeight = newHeight
                notifyDataSetChanged()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_bubble, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val category = categories[position]
        holder.title.text = category.name
        holder.title.setTextColor(textColor)
        holder.count.text = "${category.apps.size} apps"
        holder.count.setTextColor(secondaryColor)

        val icons = listOf(holder.icon1, holder.icon2, holder.icon3, holder.icon4)
        icons.forEach { it.visibility = View.INVISIBLE }

        category.apps.take(4).forEachIndexed { index, app ->
            if (index < icons.size) {
                val iv = icons[index]
                if (app.icon != null) {
                    iv.setImageDrawable(app.icon)
                    iv.colorFilter = monoFilter
                    iv.visibility = View.VISIBLE
                }
            }
        }

        itemHeight?.let {
            holder.itemView.layoutParams.height = it
        }

        holder.itemView.setOnClickListener { onBubbleClick(category) }
        holder.itemView.setOnLongClickListener { onBubbleLongClick(category) }
    }

    override fun getItemCount() = categories.size
}

class CategoryEditAdapter(
    private val apps: List<AppModel>,
    private val monoFilter: ColorMatrixColorFilter,
    private val onMoveApp: (AppModel) -> Unit
) : RecyclerView.Adapter<CategoryEditAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iv_icon)
        val label: TextView = view.findViewById(R.id.tv_label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_list, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = apps[position]
        holder.label.text = app.label
        if (app.icon != null) {
            holder.icon.setImageDrawable(app.icon)
            holder.icon.colorFilter = monoFilter
        }
        holder.itemView.setOnClickListener { onMoveApp(app) }
    }

    override fun getItemCount() = apps.size
}
