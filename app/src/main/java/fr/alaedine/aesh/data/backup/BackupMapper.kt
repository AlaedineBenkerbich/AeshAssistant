package fr.alaedine.aesh.data.backup

import fr.alaedine.aesh.data.local.entity.DailyReportEntity
import fr.alaedine.aesh.data.local.entity.ScheduleSlotEntity
import fr.alaedine.aesh.data.local.entity.StudentEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Maps the Room-persisted row to its backup JSON representation. */
fun StudentEntity.toBackup(): BackupStudent = BackupStudent(
    id = id,
    firstName = firstName,
    className = className,
    ppsGoals = ppsGoals,
)

/** Maps a backup JSON row back to its Room-persisted representation. */
fun BackupStudent.toEntity(): StudentEntity = StudentEntity(
    id = id,
    firstName = firstName,
    className = className,
    ppsGoals = ppsGoals,
)

/** Maps the Room-persisted row to its backup JSON representation. */
fun DailyReportEntity.toBackup(): BackupDailyReport = BackupDailyReport(
    id = id,
    studentId = studentId,
    date = date.toString(),
    moodLevel = moodLevel,
    focusLevel = focusLevel,
    socialInteractions = socialInteractions,
    freeNotes = freeNotes,
)

/** Maps a backup JSON row back to its Room-persisted representation. */
fun BackupDailyReport.toEntity(): DailyReportEntity = DailyReportEntity(
    id = id,
    studentId = studentId,
    date = LocalDate.parse(date),
    moodLevel = moodLevel,
    focusLevel = focusLevel,
    socialInteractions = socialInteractions,
    freeNotes = freeNotes,
)

/** Maps the Room-persisted row to its backup JSON representation. */
fun ScheduleSlotEntity.toBackup(): BackupScheduleSlot = BackupScheduleSlot(
    id = id,
    dayOfWeek = dayOfWeek.value,
    startTime = startTime.toString(),
    endTime = endTime.toString(),
    subject = subject,
    room = room,
)

/** Maps a backup JSON row back to its Room-persisted representation. */
fun BackupScheduleSlot.toEntity(): ScheduleSlotEntity = ScheduleSlotEntity(
    id = id,
    dayOfWeek = DayOfWeek.of(dayOfWeek),
    startTime = LocalTime.parse(startTime),
    endTime = LocalTime.parse(endTime),
    subject = subject,
    room = room,
)
