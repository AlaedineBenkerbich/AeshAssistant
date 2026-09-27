package fr.alaedine.aesh.data.local.entity

import fr.alaedine.aesh.domain.model.ScheduleSlot

/** Maps the Room-persisted row to the framework-agnostic domain model. */
fun ScheduleSlotEntity.toDomain(): ScheduleSlot =
    ScheduleSlot(
        id = id,
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        endTime = endTime,
        subject = subject,
        room = room,
    )

/** Maps the domain model to its Room-persisted representation. */
fun ScheduleSlot.toEntity(): ScheduleSlotEntity =
    ScheduleSlotEntity(
        id = id,
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        endTime = endTime,
        subject = subject,
        room = room,
    )
