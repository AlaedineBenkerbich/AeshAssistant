package fr.alaedine.aesh.data.local.dao

import androidx.room.Room
import fr.alaedine.aesh.data.local.AeshDatabase
import fr.alaedine.aesh.data.local.entity.StudentEntity
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
 * Exercises [StudentDao] against a real (in-memory) Room database. Run under
 * Robolectric so the Android SQLite framework Room delegates to is
 * available on the local JVM, without needing an emulator/device.
 *
 * [Config.application] swaps out the manifest-declared
 * [fr.alaedine.aesh.AeshApplication] (which starts Koin) for a plain
 * [android.app.Application]: these tests build Room directly and don't need
 * the DI graph, and starting Koin repeatedly across test methods would
 * throw [org.koin.core.error.KoinApplicationAlreadyStartedException].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class StudentDaoTest {
    private lateinit var database: AeshDatabase
    private lateinit var studentDao: StudentDao

    @BeforeTest
    fun createDatabase() {
        database =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AeshDatabase::class.java)
                .build()
        studentDao = database.studentDao()
    }

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `should return the generated id when a student is inserted`() =
        runTest {
            // Given
            val student = StudentEntity(firstName = "Alice", className = "CE2", ppsGoals = "Read aloud daily")

            // When
            val id = studentDao.insert(student)

            // Then
            assertEquals(student.copy(id = id), studentDao.getById(id))
        }

    @Test
    fun `should return null when no student exists for the given id`() =
        runTest {
            // Given / When
            val result = studentDao.getById(id = 42L)

            // Then
            assertNull(result)
        }

    @Test
    fun `should emit students ordered by first name when observing all`() =
        runTest {
            // Given
            studentDao.insert(StudentEntity(firstName = "Zoe", className = "CM1"))
            studentDao.insert(StudentEntity(firstName = "Amir", className = "CM2"))

            // When
            val students = studentDao.observeAll().first()

            // Then
            assertEquals(listOf("Amir", "Zoe"), students.map { it.firstName })
        }

    @Test
    fun `should persist changes when an existing student is updated`() =
        runTest {
            // Given
            val id = studentDao.insert(StudentEntity(firstName = "Lea", className = "CP", ppsGoals = "Initial goal"))
            val updated = StudentEntity(id = id, firstName = "Lea", className = "CP", ppsGoals = "Updated goal")

            // When
            studentDao.update(updated)

            // Then
            assertEquals(updated, studentDao.getById(id))
        }

    @Test
    fun `should remove the student when deleted`() =
        runTest {
            // Given
            val id = studentDao.insert(StudentEntity(firstName = "Nino", className = "CE1"))
            val inserted = studentDao.getById(id)!!

            // When
            studentDao.delete(inserted)

            // Then
            assertNull(studentDao.getById(id))
        }

    @Test
    fun `should insert every student preserving their ids when inserting all`() =
        runTest {
            // Given
            val students =
                listOf(
                    StudentEntity(id = 5L, firstName = "Alice", className = "CE2"),
                    StudentEntity(id = 9L, firstName = "Bob", className = "CM2"),
                )

            // When
            studentDao.insertAll(students)

            // Then
            assertEquals(students, studentDao.observeAll().first().sortedBy { it.id })
        }

    @Test
    fun `should remove every student when deleting all`() =
        runTest {
            // Given
            studentDao.insert(StudentEntity(firstName = "Alice", className = "CE2"))
            studentDao.insert(StudentEntity(firstName = "Bob", className = "CM2"))

            // When
            studentDao.deleteAll()

            // Then
            assertEquals(emptyList(), studentDao.observeAll().first())
        }
}
