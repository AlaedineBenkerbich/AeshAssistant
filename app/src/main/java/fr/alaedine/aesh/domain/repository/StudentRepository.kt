package fr.alaedine.aesh.domain.repository

import fr.alaedine.aesh.domain.model.Student
import kotlinx.coroutines.flow.Flow

/**
 * Framework-agnostic contract for persisting and retrieving [Student]s.
 *
 * Implemented by [fr.alaedine.aesh.data.repository.StudentRepositoryImpl] on
 * top of Room. Presentation-layer view models (and future domain use cases)
 * depend on this interface only, never on the `data` layer directly, keeping
 * the Clean Architecture dependency rule (dependencies always point inward)
 * intact.
 */
interface StudentRepository {
    /** Emits the current list of students every time the underlying data changes. */
    fun observeStudents(): Flow<List<Student>>

    /** Returns the student with [id], or `null` if none exists. */
    suspend fun getStudentById(id: Long): Student?

    /** Persists a new [student] and returns its generated id. */
    suspend fun addStudent(student: Student): Long

    /** Persists changes to an existing student. */
    suspend fun updateStudent(student: Student)

    /** Removes [student] from persistence. */
    suspend fun deleteStudent(student: Student)
}
