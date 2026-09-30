package fr.alaedine.aesh.data.di

import fr.alaedine.aesh.data.scanner.MlKitTextRecognitionRepository
import fr.alaedine.aesh.domain.repository.TextRecognitionRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Provides the on-device OCR used to pre-fill the schedule form from a
 * photo (see `presentation.schedule.scanner.ScheduleScannerRoute`) and the
 * daily observation form from photos of handwritten notes (see
 * `presentation.report.notes.NotesScannerRoute`). Kept separate from
 * [dataModule] since it's independent of Room/local persistence — pure
 * on-device ML, backed by ML Kit rather than the app's own database.
 */
val scannerModule =
    module {
        single<TextRecognitionRepository> { MlKitTextRecognitionRepository(androidContext()) }
    }
