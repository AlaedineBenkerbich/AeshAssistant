package fr.alaedine.aesh.data.repository

import androidx.room.Room
import fr.alaedine.aesh.data.local.AeshDatabase
import fr.alaedine.aesh.data.local.dao.StudentDao
import fr.alaedine.aesh.domain.model.Student
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Exercises [StudentRepositoryImpl] end-to-end against a real (in-memory)
 * Room database, verifying the `domain`-facing CRUD contract — including the
 * [Student] <-> [fr.alaedine.aesh.data.local.entity.StudentEntity] mapping —
 * that the rest of the app depends on.
 *
 * [Config.application] swaps out the manifest-declared
 * [fr.alaedine.aesh.AeshApplication] (which starts Koin) for a plain
 * [android.app.Application]: these tests build Room directly and don't need
 * the DI graph, and starting Koin repeatedly across test methods would
 * throw [org.koin.core.error.KoinApplicationAlreadyStartedException].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class StudentRepositoryImplTest {
    private lateinit var database: AeshDatabase
    private lateinit var studentDao: StudentDao
    private lateinit var repository: StudentRepositoryImpl

    @BeforeTest
    fun createRepository() {
        database =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AeshDatabase::class.java)
                .build()
        studentDao = database.studentDao()
        repository = StudentRepositoryImpl(studentDao)
    }

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `should return the generated id when a student is added`() =
        runTest {
            // Given
            val student = Student(firstName = "Alice", className = "CE2", ppsGoals = "Read aloud daily")

            // When
            val id = repository.addStudent(student)

            // Then
            assertEquals(student.copy(id = id), repository.getStudentById(id))
        }

    @Test
    fun `should return null when no student exists for the given id`() =
        runTest {
            // Given / When
            val result = repository.getStudentById(id = 42L)

            // Then
            assertNull(result)
        }

    @Test
    fun `should emit students ordered by first name when observing students`() =
        runTest {
            // Given
            repository.addStudent(Student(firstName = "Zoe", className = "CM1"))
            repository.addStudent(Student(firstName = "Amir", className = "CM2"))

            // When
            val students = repository.observeStudents().first()

            // Then
            assertEquals(listOf("Amir", "Zoe"), students.map { it.firstName })
        }

    @Test
    fun `should persist changes when an existing student is updated`() =
        runTest {
            // Given
            val id = repository.addStudent(Student(firstName = "Lea", className = "CP", ppsGoals = "Initial goal"))
            val updated = Student(id = id, firstName = "Lea", className = "CP", ppsGoals = "Updated goal")

            // When
            repository.updateStudent(updated)

            // Then
            assertEquals(updated, repository.getStudentById(id))
        }

    @Test
    fun `should remove the student when deleted`() =
        runTest {
            // Given
            val id = repository.addStudent(Student(firstName = "Nino", className = "CE1"))
            val student = repository.getStudentById(id)!!

            // When
            repository.deleteStudent(student)

            // Then
            assertNull(repository.getStudentById(id))
        }
}
