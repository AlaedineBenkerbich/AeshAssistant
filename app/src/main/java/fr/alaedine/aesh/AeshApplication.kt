package fr.alaedine.aesh

import android.app.Application
import fr.alaedine.aesh.di.appModules
import fr.alaedine.aesh.domain.reminder.ReminderScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Entry point that bootstraps Koin with every module in [appModules] so
 * dependencies are available app-wide (Activities, ViewModels, and future
 * Workers) before any screen is composed, then (re)schedules the daily
 * report reminder so it keeps firing even if the app is never opened again
 * that day (see [ReminderScheduler]).
 */
class AeshApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val koinApp = startKoin {
            androidContext(this@AeshApplication)
            modules(appModules)
        }
        koinApp.koin.get<ReminderScheduler>().scheduleDaily()
    }
}

