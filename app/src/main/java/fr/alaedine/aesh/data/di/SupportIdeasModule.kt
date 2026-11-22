package fr.alaedine.aesh.data.di

import fr.alaedine.aesh.domain.usecase.SuggestSupportIdeasUseCase
import org.koin.dsl.module

/**
 * Provides what suggests support ideas for a student from their daily
 * observations (see `presentation.student.ideas.SupportIdeasRoute`): the
 * [SuggestSupportIdeasUseCase] business rule, built on [dataModule]'s daily
 * reports and [essReportModule]'s on-device AI text generator. Kept separate
 * from both since it is a feature of its own — same rationale as
 * [observationNotesModule].
 */
val supportIdeasModule =
    module {
        single { SuggestSupportIdeasUseCase(get(), get()) }
    }
