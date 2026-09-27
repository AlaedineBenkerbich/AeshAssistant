package fr.alaedine.aesh.data.local.entity

import fr.alaedine.aesh.domain.model.Student

/** Maps the Room-persisted row to the framework-agnostic domain model. */
fun StudentEntity.toDomain(): Student =
    Student(
        id = id,
        firstName = firstName,
        className = className,
        ppsGoals = ppsGoals,
    )

/** Maps the domain model to its Room-persisted representation. */
fun Student.toEntity(): StudentEntity =
    StudentEntity(
        id = id,
        firstName = firstName,
        className = className,
        ppsGoals = ppsGoals,
    )
