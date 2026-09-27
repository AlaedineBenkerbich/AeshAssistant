package fr.alaedine.aesh.data.backup

import kotlinx.serialization.Serializable

/**
 * Root JSON document written by
 * [fr.alaedine.aesh.data.repository.BackupRepositoryImpl.exportBackup] and
 * read back by
 * [fr.alaedine.aesh.data.repository.BackupRepositoryImpl.importBackup].
 *
 * Mirrors the full content of [fr.alaedine.aesh.data.local.AeshDatabase] as
 * plain-data DTOs (see `BackupMapper.kt` for the Room entity <-> DTO
 * mapping) rather than serializing Room entities directly, so the on-disk
 * file format stays stable and human-readable independent of internal
 * persistence details.
 *
 * [schemaVersion] identifies this shape; bump it whenever a field is added,
 * renamed or removed so a future `importBackup` can detect and migrate
 * older backup files instead of silently misreading them.
 */
@Serializable
data class BackupPayload(
    val schemaVersion: Int = SCHEMA_VERSION,
    val students: List<BackupStudent> = emptyList(),
    val dailyReports: List<BackupDailyReport> = emptyList(),
    val scheduleSlots: List<BackupScheduleSlot> = emptyList(),
    val scheduleSlotStudentCrossRefs: List<BackupScheduleSlotStudentCrossRef> = emptyList(),
) {
    companion object {
        const val SCHEMA_VERSION = 2
    }
}

/** JSON-friendly mirror of [fr.alaedine.aesh.data.local.entity.StudentEntity]. */
@Serializable
data class BackupStudent(
    val id: Long,
    val firstName: String,
    val className: String,
    val ppsGoals: String,
)

/** JSON-friendly mirror of [fr.alaedine.aesh.data.local.entity.DailyReportEntity]. */
@Serializable
data class BackupDailyReport(
    val id: Long,
    val studentId: Long,
    /** ISO-8601 (`YYYY-MM-DD`), matching how Room itself stores it (see `Converters`). */
    val date: String,
    val moodLevel: Int,
    val focusLevel: Int,
    val socialInteractions: Int,
    val freeNotes: String,
)

/** JSON-friendly mirror of [fr.alaedine.aesh.data.local.entity.ScheduleSlotEntity]. */
@Serializable
data class BackupScheduleSlot(
    val id: Long,
    /** ISO `DayOfWeek.value` (1 = Monday ... 7 = Sunday), matching `Converters`. */
    val dayOfWeek: Int,
    /** ISO-8601 (`HH:mm[:ss]`), matching how Room itself stores it (see `Converters`). */
    val startTime: String,
    val endTime: String,
    val subject: String,
    val room: String,
)

/** JSON-friendly mirror of [fr.alaedine.aesh.data.local.entity.ScheduleSlotStudentCrossRef]. */
@Serializable
data class BackupScheduleSlotStudentCrossRef(
    val scheduleSlotId: Long,
    val studentId: Long,
)
