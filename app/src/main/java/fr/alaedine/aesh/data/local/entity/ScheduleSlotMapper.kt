package fr.alaedine.aesh.data.local.entity

import fr.alaedine.aesh.domain.model.ScheduleSlot

/**
 * Maps the Room-persisted row, joined with its assigned students, to the
 * framework-agnostic domain model (see
 * [fr.alaedine.aesh.data.local.dao.ScheduleSlotDao.observeAllWithStudents]/
 * [fr.alaedine.aesh.data.local.dao.ScheduleSlotDao.getByIdWithStudents]).
 */
fun ScheduleSlotWithStudents.toDomain(): ScheduleSlot =
    ScheduleSlot(
        id = scheduleSlot.id,
        dayOfWeek = scheduleSlot.dayOfWeek,
        startTime = scheduleSlot.startTime,
        endTime = scheduleSlot.endTime,
        subject = scheduleSlot.subject,
        room = scheduleSlot.room,
        studentIds = students.map { it.id },
    )

/** Maps the domain model to its Room-persisted representation, excluding student assignments (see [ScheduleSlotStudentCrossRef]). */
fun ScheduleSlot.toEntity(): ScheduleSlotEntity =
    ScheduleSlotEntity(
        id = id,
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        endTime = endTime,
        subject = subject,
        room = room,
    )
