package fr.alaedine.aesh.data.di

import fr.alaedine.aesh.data.scanner.MlKitScheduleScannerRepository
import fr.alaedine.aesh.data.speech.AndroidSpeechToTextRepository
import fr.alaedine.aesh.domain.repository.ScheduleScannerRepository
import fr.alaedine.aesh.domain.repository.SpeechToTextRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Provides the on-device OCR scanner used to pre-fill the schedule form
 * from a photo (see `presentation.schedule.scanner.ScheduleScannerRoute`)
 * and the on-device speech recognizer used to dictate observation notes.
 * Kept separate from [dataModule] since it's independent of Room/local
 * persistence — pure on-device ML, backed by ML Kit rather than the app's
 * own database.
 */
val scannerModule =
    module {
        single<ScheduleScannerRepository> { MlKitScheduleScannerRepository(androidContext()) }
        single<SpeechToTextRepository> { AndroidSpeechToTextRepository(androidContext()) }
    }
