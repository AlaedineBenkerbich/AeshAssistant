package fr.alaedine.aesh.data.di

import fr.alaedine.aesh.data.ai.GeminiNanoTextGenerationRepository
import fr.alaedine.aesh.data.pdf.AndroidPdfExportRepository
import fr.alaedine.aesh.domain.repository.AiTextGenerationRepository
import fr.alaedine.aesh.domain.repository.PdfExportRepository
import fr.alaedine.aesh.domain.usecase.ExtractObservationNotesUseCase
import fr.alaedine.aesh.domain.usecase.GenerateEssReportUseCase
import org.koin.dsl.module

/**
 * Provides the ESS report screen's dependencies: the on-device Gemini Nano
 * text generator, the PDF export repository, and the
 * [GenerateEssReportUseCase] business rule built on top of both, plus the
 * [ExtractObservationNotesUseCase] that reuses the same generator (and the
 * [scannerModule]'s OCR) to fill the observation form from voice/photos. Kept
 * separate from [dataModule] since neither touches Room/local persistence —
 * same rationale as [scannerModule].
 */
val essReportModule =
    module {
        single<AiTextGenerationRepository> { GeminiNanoTextGenerationRepository() }
        single<PdfExportRepository> { AndroidPdfExportRepository() }
        single { GenerateEssReportUseCase(get(), get()) }
        single { ExtractObservationNotesUseCase(get(), get()) }
    }
