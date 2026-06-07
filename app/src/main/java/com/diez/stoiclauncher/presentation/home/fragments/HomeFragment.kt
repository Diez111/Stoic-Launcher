package com.diez.stoiclauncher.presentation.home.fragments

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.diez.stoiclauncher.R
import com.diez.stoiclauncher.StoicApplication
import com.diez.stoiclauncher.domain.model.AppModel
import com.diez.stoiclauncher.domain.model.CategoryGroup
import com.diez.stoiclauncher.presentation.home.HomeViewModel
import com.diez.stoiclauncher.presentation.util.AppLaunchHelper
import com.diez.stoiclauncher.presentation.util.ColorHelper
import com.diez.stoiclauncher.presentation.util.LaunchHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private val viewModel: HomeViewModel by activityViewModels()
    private lateinit var rvBubbles: RecyclerView
    private lateinit var bubbleAdapter: BubbleAdapter
    private val matrix = ColorMatrix().apply { setSaturation(0f) }
    private val monoFilter = ColorMatrixColorFilter(matrix)
    private val MAX_GROUPS = 6
    private val menuHandler = Handler(Looper.getMainLooper())
    private var maxDragDist = 0f
    private var pendingMenuGroup: CategoryGroup? = null
    private lateinit var itemTouchHelper: ItemTouchHelper
    private val bubbleLayoutListener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
        if (::bubbleAdapter.isInitialized) bubbleAdapter.updateHeight(rvBubbles)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        rvBubbles = view.findViewById(R.id.rv_bubbles)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val appContainer = (requireActivity().application as StoicApplication).container
        val appPrefs = appContainer.appPreferencesRepository
        val settingsRepo = appContainer.settingsRepository

        val tcClock = view.findViewById<View>(R.id.tc_clock)
        val tcDate = view.findViewById<View>(R.id.tc_date)
        tcClock.setOnClickListener { LaunchHelper.openClock(requireContext()) }
        tcDate.setOnClickListener { LaunchHelper.openCalendar(requireContext()) }

        bubbleAdapter = BubbleAdapter(
            monoFilter = monoFilter,
            onBubbleClick = { group -> openGroupDialog(group) },
            onCreateGroup = { showCreateGroupDialog() }
        )

        rvBubbles.adapter = bubbleAdapter
        rvBubbles.setHasFixedSize(true)
        rvBubbles.itemAnimator = null
        rvBubbles.isNestedScrollingEnabled = false
        rvBubbles.overScrollMode = View.OVER_SCROLL_NEVER
        rvBubbles.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT or
            ItemTouchHelper.START or ItemTouchHelper.END, 0
        ) {
            private var menuRunnable: Runnable? = null

            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                if (vh.itemViewType == BubbleAdapter.TYPE_ADD || target.itemViewType == BubbleAdapter.TYPE_ADD)
                    return false
                bubbleAdapter.moveItem(vh.bindingAdapterPosition, target.bindingAdapterPosition)
                maxDragDist += 1f
                cancelMenuTimer()
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {}

            override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int {
                if (vh.itemViewType == BubbleAdapter.TYPE_ADD) return makeMovementFlags(0, 0)
                return super.getMovementFlags(rv, vh)
            }

            override fun onSelectedChanged(vh: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(vh, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && vh != null) {
                    vh.itemView.apply { scaleX = 1.08f; scaleY = 1.08f; alpha = 0.9f; translationZ = 12f }
                    maxDragDist = 0f
                    val pos = vh.bindingAdapterPosition
                    pendingMenuGroup = if (pos in 0 until bubbleAdapter.getOriginalNames().size)
                        bubbleAdapter.getCategoryAt(pos) else null
                    scheduleMenuTimer(vh)
                }
                if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                    cancelMenuTimer()
                    pendingMenuGroup = null
                }
            }

            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                vh.itemView.apply { scaleX = 1f; scaleY = 1f; alpha = 1f; translationZ = 0f }
                cancelMenuTimer()
                lifecycleScope.launch { appPrefs.reorderUserGroups(bubbleAdapter.getOriginalNames()) }
            }

            override fun onChildDraw(c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                dx: Float, dy: Float, actionState: Int, isActive: Boolean) {
                if (isActive) vh.itemView.elevation = 12f
                super.onChildDraw(c, rv, vh, dx, dy, actionState, isActive)
            }

            private fun scheduleMenuTimer(vh: RecyclerView.ViewHolder) {
                cancelMenuTimer()
                menuRunnable = Runnable {
                    if (maxDragDist < 1f && pendingMenuGroup != null) {
                        menuHandler.post {
                            val group = pendingMenuGroup
                            if (group != null) showGroupOptions(group)
                            pendingMenuGroup = null
                        }
                    }
                }
                menuHandler.postDelayed(menuRunnable!!, 2500)
            }

            private fun cancelMenuTimer() {
                menuRunnable?.let { menuHandler.removeCallbacks(it) }
                menuRunnable = null
            }
        })
        itemTouchHelper = touchHelper
        touchHelper.attachToRecyclerView(rvBubbles)

        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                viewModel.uiState,
                appPrefs.appGroupsFlow,
                appPrefs.userGroupsListFlow,
                settingsRepo.customCategoryNames
            ) { apps, appGroups, groupList, customNames ->
                buildBubbleData(apps, appGroups, groupList, customNames)
            }.collectLatest { bubbles ->
                bubbleAdapter.submitCategories(bubbles)
                setupGridLayout(bubbles.size)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            combine(viewModel.accentColor, viewModel.isWallpaperEnabled) { c, w -> c to w }
                .collectLatest { (color, isWallpaper) ->
                    val contentColor = ColorHelper.getTextColorForAccent(color, isWallpaper)
                    val secondaryColor = ColorHelper.getSecondaryTextColorForAccent(color, isWallpaper)
                    (tcClock as? TextView)?.setTextColor(contentColor)
                    (tcDate as? TextView)?.setTextColor(secondaryColor)
                    bubbleAdapter.updateColors(contentColor, secondaryColor)
                }
        }

        rvBubbles.viewTreeObserver.addOnGlobalLayoutListener(bubbleLayoutListener)

        view.setOnLongClickListener {
            val options = mutableListOf(
                com.diez.stoiclauncher.presentation.common.MenuOption("Crear grupo"),
                com.diez.stoiclauncher.presentation.common.MenuOption(getString(R.string.settings))
            )
            com.diez.stoiclauncher.presentation.common.BottomSheetMenu(
                "", options, viewModel.accentColor.value
            ) { index ->
                when (index) {
                    0 -> showCreateGroupDialog()
                    1 -> startActivity(android.content.Intent(requireContext(),
                        com.diez.stoiclauncher.presentation.settings.SettingsActivity::class.java))
                }
            }.show(parentFragmentManager, "home_options")
            true
        }
    }

    private fun buildBubbleData(
        apps: List<AppModel>,
        appGroups: Map<String, String>,
        groupList: List<String>,
        customNames: Map<String, String>
    ): List<CategoryGroup> {
        return groupList.map { groupName ->
            val displayName = customNames[groupName] ?: groupName
            val appsInGroup = apps.filter { appGroups[it.uniqueId] == groupName }
            CategoryGroup(displayName, appsInGroup, groupName)
        }
    }

    private fun setupGridLayout(count: Int) {
        val columns = when {
            count <= 1 -> 1
            count <= 2 -> 1
            else -> 2
        }
        val glm = GridLayoutManager(requireContext(), columns)
        glm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                if (columns == 1) return 1
                if (count % 2 == 1 && position == count - 1) return columns
                return 1
            }
        }
        rvBubbles.layoutManager = glm
    }

    private fun openGroupDialog(group: CategoryGroup) {
        if (group.apps.isEmpty()) {
            android.widget.Toast.makeText(requireContext(),
                "Grupo vacío — mantené presionada una app y seleccioná \"Grupo\"",
                android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val dialog = android.app.Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar)
        dialog.setContentView(R.layout.dialog_folder_overlay)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        val tvTitle = dialog.findViewById<TextView>(R.id.tv_folder_title)
        val rvApps = dialog.findViewById<RecyclerView>(R.id.rv_folder_apps)
        val btnClose = dialog.findViewById<View>(R.id.btn_close_folder)
        tvTitle.text = group.name
        tvTitle.setOnLongClickListener { dialog.dismiss(); showGroupOptions(group); true }

        rvApps.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 4)
        val gridAdapter = com.diez.stoiclauncher.presentation.home.AppAdapter(
            onAppClick = { app -> lifecycleScope.launch { AppLaunchHelper.launchApp(requireContext(), app) }; dialog.dismiss() },
            onAppLongClick = { app ->
                val menuOptions = mutableListOf(
                    com.diez.stoiclauncher.presentation.common.MenuOption("Quitar del grupo"),
                    com.diez.stoiclauncher.presentation.common.MenuOption("Agregar a otro grupo"),
                    com.diez.stoiclauncher.presentation.common.MenuOption("Crear grupo"),
                    com.diez.stoiclauncher.presentation.common.MenuOption("Lanzar")
                )
                com.diez.stoiclauncher.presentation.common.BottomSheetMenu(
                    app.label, menuOptions, viewModel.accentColor.value
                ) { idx ->
                    when (idx) {
                        0 -> { viewModel.setAppGroup(app, null); dialog.dismiss() }
                        1 -> { dialog.dismiss(); showMoveToGroupDialog(app) }
                        2 -> { showCreateGroupAndAddAppDialog(app); dialog.dismiss() }
                        3 -> { lifecycleScope.launch { AppLaunchHelper.launchApp(requireContext(), app) }; dialog.dismiss() }
                    }
                }.show(parentFragmentManager, "group_app_menu")
                true
            },
            hideLabelsForSingleApps = false
        )
        gridAdapter.submitList(group.apps)
        rvApps.adapter = gridAdapter
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showGroupOptions(group: CategoryGroup) {
        val options = listOf(
            com.diez.stoiclauncher.presentation.common.MenuOption("Renombrar"),
            com.diez.stoiclauncher.presentation.common.MenuOption("Eliminar grupo"),
            com.diez.stoiclauncher.presentation.common.MenuOption("Crear otro grupo")
        )
        com.diez.stoiclauncher.presentation.common.BottomSheetMenu(
            group.name, options, viewModel.accentColor.value
        ) { index ->
            when (index) {
                0 -> showRenameDialog(group)
                1 -> showDeleteConfirm(group)
                2 -> showCreateGroupDialog()
            }
        }.show(parentFragmentManager, "bubble_options")
    }

    private fun showRenameDialog(group: CategoryGroup) {
        showInputDialog(
            title = "Renombrar grupo",
            prefill = group.name,
            hint = "Nuevo nombre"
        ) { newName ->
            val settingsRepo = (requireActivity().application as StoicApplication).container.settingsRepository
            lifecycleScope.launch {
                viewModel.renameGroup(group.originalName, newName)
                settingsRepo.setCustomCategoryName(group.originalName, newName)
            }
        }
    }

    private fun showCreateGroupDialog() {
        if (bubbleAdapter.totalBubbles >= MAX_GROUPS) {
            android.widget.Toast.makeText(requireContext(),
                "Máximo $MAX_GROUPS grupos", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val appPrefs = (requireActivity().application as StoicApplication).container.appPreferencesRepository
        showInputDialog(
            title = "Crear grupo",
            prefill = "",
            hint = "Nombre del grupo"
        ) { name ->
            lifecycleScope.launch { appPrefs.addUserGroup(name) }
        }
    }

    private fun showInputDialog(title: String, prefill: String, hint: String, onConfirm: (String) -> Unit) {
        val dlg = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val inputView = layoutInflater.inflate(R.layout.layout_bottom_sheet_input, null)
        dlg.setContentView(inputView)

        val accentColor = viewModel.accentColor.value
        val isWallpaper = viewModel.isWallpaperEnabled.value
        val contentColor = ColorHelper.getTextColorForAccent(accentColor, isWallpaper)
        val secondaryColor = ColorHelper.getSecondaryTextColorForAccent(accentColor, isWallpaper)
        com.diez.stoiclauncher.presentation.util.UiHelper.setupBottomSheetColor(dlg, inputView, accentColor)

        val tvTitle = inputView.findViewById<android.widget.TextView>(R.id.tv_title)
        val etInput = inputView.findViewById<android.widget.EditText>(R.id.et_input)
        val btnConfirm = inputView.findViewById<android.widget.TextView>(R.id.btn_confirm)

        tvTitle.text = title; tvTitle.setTextColor(contentColor)
        etInput.setText(prefill); etInput.hint = hint
        etInput.setTextColor(contentColor); etInput.setHintTextColor(secondaryColor)
        if (prefill.isNotEmpty()) etInput.selectAll()
        btnConfirm.setTextColor(contentColor)

        btnConfirm.setOnClickListener {
            val name = etInput.text.toString().trim()
            if (name.isNotEmpty()) onConfirm(name)
            dlg.dismiss()
        }
        dlg.show()
        etInput.requestFocus()
    }

    private fun showDeleteConfirm(group: CategoryGroup) {
        val appPrefs = (requireActivity().application as StoicApplication).container.appPreferencesRepository
        val options = listOf(
            com.diez.stoiclauncher.presentation.common.MenuOption("Eliminar"),
            com.diez.stoiclauncher.presentation.common.MenuOption("Cancelar")
        )
        com.diez.stoiclauncher.presentation.common.BottomSheetMenu(
            "¿Eliminar \"${group.name}\"?", options, viewModel.accentColor.value
        ) { index ->
            if (index == 0) {
                lifecycleScope.launch {
                    viewModel.deleteGroup(group.originalName)
                    appPrefs.removeUserGroup(group.originalName)
                }
            }
        }.show(parentFragmentManager, "delete_group")
    }

    private fun showMoveToGroupDialog(app: AppModel) {
        val appContainer = (requireActivity().application as StoicApplication).container
        val appPrefs = appContainer.appPreferencesRepository
        val settingsRepo = appContainer.settingsRepository
        lifecycleScope.launch {
            val groupList = appPrefs.userGroupsListFlow.first()
            val customNames = settingsRepo.customCategoryNames.first()
            val currentGroup = app.groupId

            val availableGroups = groupList.filter { it != currentGroup }
            if (availableGroups.isEmpty()) {
                android.widget.Toast.makeText(requireContext(), "No hay otros grupos", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            val labels = availableGroups.map { customNames[it] ?: it }
            val menuOptions = labels.map { com.diez.stoiclauncher.presentation.common.MenuOption(it) }
            com.diez.stoiclauncher.presentation.common.BottomSheetMenu(
                "Mover a:", menuOptions, viewModel.accentColor.value
            ) { index ->
                viewModel.setAppGroup(app, availableGroups[index])
            }.show(parentFragmentManager, "move_group")
        }
    }

    private fun showCreateGroupAndAddAppDialog(app: AppModel) {
        if (bubbleAdapter.totalBubbles >= MAX_GROUPS) {
            android.widget.Toast.makeText(requireContext(),
                "Máximo $MAX_GROUPS grupos", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val appPrefs = (requireActivity().application as StoicApplication).container.appPreferencesRepository
        showInputDialog(
            title = "Crear grupo",
            prefill = "",
            hint = "Nombre del grupo"
        ) { name ->
            lifecycleScope.launch {
                appPrefs.addUserGroup(name)
                viewModel.setAppGroup(app, name)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        rvBubbles.viewTreeObserver.removeOnGlobalLayoutListener(bubbleLayoutListener)
    }
}

class BubbleAdapter(
    private val monoFilter: ColorMatrixColorFilter,
    private val onBubbleClick: (CategoryGroup) -> Unit,
    private val onCreateGroup: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var categories: List<CategoryGroup> = emptyList()
    private var textColor = Color.WHITE
    private var secondaryColor = 0xB3FFFFFF.toInt()
    private var itemHeight: Int? = null

    companion object {
        const val TYPE_BUBBLE = 0
        const val TYPE_ADD = 1
    }

    val totalBubbles: Int get() = categories.size

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tv_bubble_title)
        val count: TextView = view.findViewById(R.id.tv_bubble_count)
        val icon1: ImageView = view.findViewById(R.id.iv_icon_1)
        val icon2: ImageView = view.findViewById(R.id.iv_icon_2)
        val icon3: ImageView = view.findViewById(R.id.iv_icon_3)
        val icon4: ImageView = view.findViewById(R.id.iv_icon_4)
    }

    inner class AddVH(view: View) : RecyclerView.ViewHolder(view)

    fun submitCategories(list: List<CategoryGroup>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = categories.size
            override fun getNewListSize() = list.size
            override fun areItemsTheSame(o: Int, n: Int) = categories[o].originalName == list[n].originalName
            override fun areContentsTheSame(o: Int, n: Int) = categories[o] == list[n]
        })
        categories = list
        diff.dispatchUpdatesTo(this@BubbleAdapter)
    }

    fun moveItem(fromPos: Int, toPos: Int) {
        val mutable = categories.toMutableList()
        val moved = mutable.removeAt(fromPos)
        mutable.add(toPos, moved)
        categories = mutable
        notifyItemMoved(fromPos, toPos)
    }

    fun getOriginalNames(): List<String> = categories.map { it.originalName }

    fun getCategoryAt(position: Int): CategoryGroup? = categories.getOrNull(position)

    fun updateColors(text: Int, secondary: Int) {
        textColor = text
        secondaryColor = secondary
        notifyDataSetChanged()
    }

    fun updateHeight(rv: RecyclerView) {
        val total = if (categories.isEmpty()) 1 else categories.size
        val cols = if (total <= 1) 1 else if (total <= 2) 1 else 2
        val rows = Math.ceil(total.toDouble() / cols).toInt()
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

    override fun getItemViewType(position: Int): Int {
        if (categories.isEmpty() && position == 0) return TYPE_ADD
        return TYPE_BUBBLE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_bubble, parent, false)
        return if (viewType == TYPE_ADD) AddVH(v) else VH(v)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is AddVH) {
            val view = holder.itemView
            view.findViewById<TextView>(R.id.tv_bubble_title).apply {
                text = "+"; setTextColor(textColor); textSize = 28f
            }
            view.findViewById<TextView>(R.id.tv_bubble_count).visibility = View.GONE
            listOf<ImageView>(
                view.findViewById(R.id.iv_icon_1), view.findViewById(R.id.iv_icon_2),
                view.findViewById(R.id.iv_icon_3), view.findViewById(R.id.iv_icon_4)
            ).forEach { it.visibility = View.GONE }
            itemHeight?.let { view.layoutParams.height = it }
            view.setOnClickListener { onCreateGroup() }
            return
        }

        val h = holder as VH
        val category = categories[position]
        h.title.text = category.name
        h.title.setTextColor(textColor)
        h.title.textSize = 13f
        h.count.visibility = View.VISIBLE
        h.count.text = if (category.apps.isEmpty()) "Vacío" else "${category.apps.size} apps"
        h.count.setTextColor(secondaryColor)

        listOf(h.icon1, h.icon2, h.icon3, h.icon4).forEach { it.visibility = View.INVISIBLE }
        category.apps.take(4).forEachIndexed { index, app ->
            if (index < 4 && app.icon != null) {
                listOf(h.icon1, h.icon2, h.icon3, h.icon4)[index].apply {
                    setImageDrawable(app.icon)
                    colorFilter = monoFilter
                    visibility = View.VISIBLE
                }
            }
        }

        itemHeight?.let { h.itemView.layoutParams.height = it }
        h.itemView.setOnClickListener { onBubbleClick(category) }
    }

    override fun getItemCount(): Int = if (categories.isEmpty()) 1 else categories.size
}
