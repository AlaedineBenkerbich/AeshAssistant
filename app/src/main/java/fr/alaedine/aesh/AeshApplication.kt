package fr.alaedine.aesh

import android.app.Application
import fr.alaedine.aesh.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Entry point that bootstraps Koin with every module in [appModules] so
 * dependencies are available app-wide (Activities, ViewModels, and future
 * Workers) before any screen is composed.
 */
class AeshApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@AeshApplication)
            modules(appModules)
        }
    }
}
