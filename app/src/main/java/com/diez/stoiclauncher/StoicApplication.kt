package com.diez.stoiclauncher

import android.app.Application
import com.diez.stoiclauncher.core.di.AppContainer

class StoicApplication : Application() {
    
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
