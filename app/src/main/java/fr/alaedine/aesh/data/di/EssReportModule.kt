package fr.alaedine.aesh.data.di

import fr.alaedine.aesh.data.ai.GeminiNanoTextGenerationRepository
import fr.alaedine.aesh.data.pdf.AndroidPdfExportRepository
import fr.alaedine.aesh.domain.repository.AiTextGenerationRepository
import fr.alaedine.aesh.domain.repository.PdfExportRepository
import fr.alaedine.aesh.domain.usecase.GenerateEssReportUseCase
import org.koin.dsl.module

/**
 * Provides the ESS report screen's dependencies: the on-device Gemini Nano
 * text generator, the PDF export repository, and the
 * [GenerateEssReportUseCase] business rule built on top of both. Kept
 * separate from [dataModule] since neither touches Room/local persistence —
 * same rationale as [scannerModule]. The text generator is shared with
 * [observationNotesModule], which uses it to sort dictated or scanned notes.
 */
val essReportModule =
    module {
        single<AiTextGenerationRepository> { GeminiNanoTextGenerationRepository() }
        single<PdfExportRepository> { AndroidPdfExportRepository() }
        single { GenerateEssReportUseCase(get(), get()) }
    }
