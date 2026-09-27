package fr.alaedine.aesh.data.local.entity

import fr.alaedine.aesh.domain.model.DailyReport

/** Maps the Room-persisted row to the framework-agnostic domain model. */
fun DailyReportEntity.toDomain(): DailyReport =
    DailyReport(
        id = id,
        date = date,
        studentId = studentId,
        moodLevel = moodLevel,
        focusLevel = focusLevel,
        socialInteractions = socialInteractions,
        freeNotes = freeNotes,
    )

/** Maps the domain model to its Room-persisted representation. */
fun DailyReport.toEntity(): DailyReportEntity =
    DailyReportEntity(
        id = id,
        date = date,
        studentId = studentId,
        moodLevel = moodLevel,
        focusLevel = focusLevel,
        socialInteractions = socialInteractions,
        freeNotes = freeNotes,
    )
