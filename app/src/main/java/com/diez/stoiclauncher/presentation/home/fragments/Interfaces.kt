package com.diez.stoiclauncher.presentation.home.fragments

import android.view.ViewGroup
import com.diez.stoiclauncher.domain.model.AppModel

interface AppActionListener {
    fun onAppLongClick(app: AppModel, source: String = "DRAWER"): Boolean
}

interface WidgetContainerProvider {
    fun attachWidgetContainer(container: ViewGroup)
    fun requestAddWidget()
}
