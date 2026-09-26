package fr.alaedine.aesh.domain.model

/**
 * A student followed by the AESH, framework-agnostic so it can be used
 * throughout `domain` and `presentation` without leaking persistence
 * details (see [fr.alaedine.aesh.data.local.entity.StudentEntity] for the
 * Room-mapped counterpart).
 *
 * @property id Unique identifier; `0` for a student not yet persisted.
 * @property firstName The student's first name.
 * @property className The class/group the student is enrolled in.
 * @property ppsGoals Free-text goals from the student's *Projet Personnalisé
 * de Scolarisation* (PPS), the individualized schooling plan for students
 * with disabilities.
 */
data class Student(
    val id: Long = 0L,
    val firstName: String,
    val className: String,
    val ppsGoals: String = "",
)
