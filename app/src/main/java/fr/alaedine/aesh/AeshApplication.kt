package fr.alaedine.aesh

import android.app.Application
import com.google.android.material.color.DynamicColors
import fr.alaedine.aesh.di.appModules
import fr.alaedine.aesh.domain.reminder.ReminderScheduler
import fr.alaedine.aesh.presentation.report.notes.NotesScannerViewModel
import fr.alaedine.aesh.presentation.report.notes.deleteLeftoverNotesPhotos
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import java.io.File

/**
 * Entry point that bootstraps Koin with every module in [appModules] so
 * dependencies are available app-wide (Activities, ViewModels, and future
 * Workers) before any screen is composed, then (re)schedules the daily
 * report reminder so it keeps firing even if the app is never opened again
 * that day (see [ReminderScheduler]).
 *
 * Also applies dynamic color to the activities' window theme on Android 12+,
 * so the window background behind the Compose UI uses the same
 * wallpaper-derived palette as `AeshAssistantTheme` instead of Material's
 * static one.
 */
class AeshApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        val koinApp =
            startKoin {
                androidContext(this@AeshApplication)
                modules(appModules)
            }
        koinApp.koin.get<ReminderScheduler>().scheduleDaily()
        // Photos of notes left behind by a scan the app was killed in the middle of: they can show a child's personal
        // information, and no scan can be running yet.
        deleteLeftoverNotesPhotos(File(cacheDir, NotesScannerViewModel.PHOTO_DIRECTORY_NAME))
    }
}
