package fr.alaedine.aesh.data.di

import fr.alaedine.aesh.data.speech.AndroidSpeechRecognitionRepository
import fr.alaedine.aesh.domain.repository.SpeechRecognitionRepository
import fr.alaedine.aesh.domain.usecase.RecognizeNotesFromPhotosUseCase
import fr.alaedine.aesh.domain.usecase.SortObservationNotesUseCase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Provides what fills the daily observation form's free-text fields from
 * dictated or photographed notes: the on-device speech recognizer, and the
 * use cases that read photos and sort the notes with the on-device AI model.
 * Those build on [scannerModule]'s OCR and [essReportModule]'s AI text
 * generator. Kept separate from [dataModule] since none of it touches
 * Room/local persistence — same rationale as [scannerModule].
 */
val observationNotesModule =
    module {
        single<SpeechRecognitionRepository> { AndroidSpeechRecognitionRepository(androidContext()) }
        single { RecognizeNotesFromPhotosUseCase(get()) }
        single { SortObservationNotesUseCase(get()) }
    }
