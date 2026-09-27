package fr.alaedine.aesh.presentation.student

import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.domain.repository.StudentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [StudentRepository] test double, avoiding the need for a mocking
 * library or a real Room database to unit test the student view models.
 */
class FakeStudentRepository(
    initialStudents: List<Student> = emptyList(),
) : StudentRepository {
    private val students = MutableStateFlow(initialStudents)
    private var nextId = (initialStudents.maxOfOrNull { it.id } ?: 0L) + 1

    override fun observeStudents(): Flow<List<Student>> = students

    override suspend fun getStudentById(id: Long): Student? = students.value.find { it.id == id }

    override suspend fun addStudent(student: Student): Long {
        val id = nextId++
        students.update { it + student.copy(id = id) }
        return id
    }

    override suspend fun updateStudent(student: Student) {
        students.update { list -> list.map { if (it.id == student.id) student else it } }
    }

    override suspend fun deleteStudent(student: Student) {
        students.update { list -> list.filterNot { it.id == student.id } }
    }
}
