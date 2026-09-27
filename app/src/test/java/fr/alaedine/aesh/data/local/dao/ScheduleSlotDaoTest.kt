package fr.alaedine.aesh.data.local.dao

import androidx.room.Room
import fr.alaedine.aesh.data.local.AeshDatabase
import fr.alaedine.aesh.data.local.entity.ScheduleSlotEntity
import fr.alaedine.aesh.data.local.entity.ScheduleSlotStudentCrossRef
import fr.alaedine.aesh.data.local.entity.StudentEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Exercises [ScheduleSlotDao] against a real (in-memory) Room database. Run
 * under Robolectric so the Android SQLite framework Room delegates to is
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
class ScheduleSlotDaoTest {
    private lateinit var database: AeshDatabase
    private lateinit var scheduleSlotDao: ScheduleSlotDao
    private lateinit var studentDao: StudentDao

    @BeforeTest
    fun createDatabase() {
        database =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AeshDatabase::class.java)
                .build()
        scheduleSlotDao = database.scheduleSlotDao()
        studentDao = database.studentDao()
    }

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `should return the generated id when a schedule slot is inserted`() =
        runTest {
            // Given
            val scheduleSlot =
                ScheduleSlotEntity(
                    dayOfWeek = DayOfWeek.MONDAY,
                    startTime = LocalTime.of(8, 0),
                    endTime = LocalTime.of(9, 0),
                    subject = "Mathématiques",
                    room = "B12",
                )

            // When
            val id = scheduleSlotDao.insert(scheduleSlot)

            // Then
            assertEquals(scheduleSlot.copy(id = id), scheduleSlotDao.getById(id))
        }

    @Test
    fun `should return null when no schedule slot exists for the given id`() =
        runTest {
            // Given / When
            val result = scheduleSlotDao.getById(id = 42L)

            // Then
            assertNull(result)
        }

    @Test
    fun `should emit schedule slots ordered by day then start time when observing all`() =
        runTest {
            // Given
            scheduleSlotDao.insert(
                ScheduleSlotEntity(
                    dayOfWeek = DayOfWeek.TUESDAY,
                    startTime = LocalTime.of(8, 0),
                    endTime = LocalTime.of(9, 0),
                    subject = "Sport",
                ),
            )
            scheduleSlotDao.insert(
                ScheduleSlotEntity(
                    dayOfWeek = DayOfWeek.MONDAY,
                    startTime = LocalTime.of(10, 0),
                    endTime = LocalTime.of(11, 0),
                    subject = "Français",
                ),
            )
            scheduleSlotDao.insert(
                ScheduleSlotEntity(
                    dayOfWeek = DayOfWeek.MONDAY,
                    startTime = LocalTime.of(8, 0),
                    endTime = LocalTime.of(9, 0),
                    subject = "Mathématiques",
                ),
            )

            // When
            val scheduleSlots = scheduleSlotDao.observeAll().first()

            // Then
            assertEquals(listOf("Mathématiques", "Français", "Sport"), scheduleSlots.map { it.subject })
        }

    @Test
    fun `should persist changes when an existing schedule slot is updated`() =
        runTest {
            // Given
            val id =
                scheduleSlotDao.insert(
                    ScheduleSlotEntity(
                        dayOfWeek = DayOfWeek.MONDAY,
                        startTime = LocalTime.of(8, 0),
                        endTime = LocalTime.of(9, 0),
                        subject = "Mathématiques",
                    ),
                )
            val updated =
                ScheduleSlotEntity(
                    id = id,
                    dayOfWeek = DayOfWeek.WEDNESDAY,
                    startTime = LocalTime.of(14, 0),
                    endTime = LocalTime.of(15, 0),
                    subject = "Histoire",
                    room = "C3",
                )

            // When
            scheduleSlotDao.update(updated)

            // Then
            assertEquals(updated, scheduleSlotDao.getById(id))
        }

    @Test
    fun `should remove the schedule slot when deleted`() =
        runTest {
            // Given
            val id =
                scheduleSlotDao.insert(
                    ScheduleSlotEntity(
                        dayOfWeek = DayOfWeek.MONDAY,
                        startTime = LocalTime.of(8, 0),
                        endTime = LocalTime.of(9, 0),
                        subject = "Mathématiques",
                    ),
                )
            val inserted = scheduleSlotDao.getById(id)!!

            // When
            scheduleSlotDao.delete(inserted)

            // Then
            assertNull(scheduleSlotDao.getById(id))
        }

    @Test
    fun `should insert every schedule slot preserving their ids when inserting all`() =
        runTest {
            // Given
            val scheduleSlots =
                listOf(
                    ScheduleSlotEntity(
                        id = 7L,
                        dayOfWeek = DayOfWeek.MONDAY,
                        startTime = LocalTime.of(8, 0),
                        endTime = LocalTime.of(9, 0),
                        subject = "Mathématiques",
                    ),
                    ScheduleSlotEntity(
                        id = 8L,
                        dayOfWeek = DayOfWeek.TUESDAY,
                        startTime = LocalTime.of(10, 0),
                        endTime = LocalTime.of(11, 0),
                        subject = "Sport",
                    ),
                )

            // When
            scheduleSlotDao.insertAll(scheduleSlots)

            // Then
            assertEquals(scheduleSlots, scheduleSlotDao.observeAll().first().sortedBy { it.id })
        }

    @Test
    fun `should remove every schedule slot when deleting all`() =
        runTest {
            // Given
            scheduleSlotDao.insert(
                ScheduleSlotEntity(
                    dayOfWeek = DayOfWeek.MONDAY,
                    startTime = LocalTime.of(8, 0),
                    endTime = LocalTime.of(9, 0),
                    subject = "Mathématiques",
                ),
            )

            // When
            scheduleSlotDao.deleteAll()

            // Then
            assertEquals(emptyList(), scheduleSlotDao.observeAll().first())
        }

    @Test
    fun `should return every assigned student when observing all with students`() =
        runTest {
            // Given
            val aliceId = studentDao.insert(StudentEntity(firstName = "Alice", className = "CE2"))
            val amirId = studentDao.insert(StudentEntity(firstName = "Amir", className = "CM2"))
            val slotId =
                scheduleSlotDao.insert(
                    ScheduleSlotEntity(
                        dayOfWeek = DayOfWeek.MONDAY,
                        startTime = LocalTime.of(8, 0),
                        endTime = LocalTime.of(9, 0),
                        subject = "Mathématiques",
                    ),
                )
            scheduleSlotDao.insertStudentCrossRefs(
                listOf(
                    ScheduleSlotStudentCrossRef(scheduleSlotId = slotId, studentId = aliceId),
                    ScheduleSlotStudentCrossRef(scheduleSlotId = slotId, studentId = amirId),
                ),
            )

            // When
            val slotsWithStudents = scheduleSlotDao.observeAllWithStudents().first()

            // Then
            assertEquals(
                listOf("Alice", "Amir"),
                slotsWithStudents
                    .single()
                    .students
                    .map { it.firstName }
                    .sorted(),
            )
        }

    @Test
    fun `should return the slot joined with its assigned students when getting by id with students`() =
        runTest {
            // Given
            val aliceId = studentDao.insert(StudentEntity(firstName = "Alice", className = "CE2"))
            val slotId =
                scheduleSlotDao.insert(
                    ScheduleSlotEntity(
                        dayOfWeek = DayOfWeek.MONDAY,
                        startTime = LocalTime.of(8, 0),
                        endTime = LocalTime.of(9, 0),
                        subject = "Mathématiques",
                    ),
                )
            scheduleSlotDao.insertStudentCrossRefs(listOf(ScheduleSlotStudentCrossRef(scheduleSlotId = slotId, studentId = aliceId)))

            // When
            val result = scheduleSlotDao.getByIdWithStudents(slotId)

            // Then
            assertEquals(listOf("Alice"), result?.students?.map { it.firstName })
        }

    @Test
    fun `should return an empty student list when getting by id with students and none are assigned`() =
        runTest {
            // Given
            val slotId =
                scheduleSlotDao.insert(
                    ScheduleSlotEntity(
                        dayOfWeek = DayOfWeek.MONDAY,
                        startTime = LocalTime.of(8, 0),
                        endTime = LocalTime.of(9, 0),
                        subject = "Mathématiques",
                    ),
                )

            // When
            val result = scheduleSlotDao.getByIdWithStudents(slotId)

            // Then
            assertEquals(emptyList(), result?.students)
        }

    @Test
    fun `should remove the cross-ref row when the schedule slot is deleted`() =
        runTest {
            // Given
            val aliceId = studentDao.insert(StudentEntity(firstName = "Alice", className = "CE2"))
            val slot =
                ScheduleSlotEntity(
                    dayOfWeek = DayOfWeek.MONDAY,
                    startTime = LocalTime.of(8, 0),
                    endTime = LocalTime.of(9, 0),
                    subject = "Mathématiques",
                )
            val slotId = scheduleSlotDao.insert(slot)
            scheduleSlotDao.insertStudentCrossRefs(listOf(ScheduleSlotStudentCrossRef(scheduleSlotId = slotId, studentId = aliceId)))

            // When
            scheduleSlotDao.delete(slot.copy(id = slotId))

            // Then
            assertEquals(emptyList(), scheduleSlotDao.getAllStudentCrossRefs())
        }

    @Test
    fun `should remove the cross-ref row when the assigned student is deleted`() =
        runTest {
            // Given
            val alice = StudentEntity(firstName = "Alice", className = "CE2")
            val aliceId = studentDao.insert(alice)
            val slotId =
                scheduleSlotDao.insert(
                    ScheduleSlotEntity(
                        dayOfWeek = DayOfWeek.MONDAY,
                        startTime = LocalTime.of(8, 0),
                        endTime = LocalTime.of(9, 0),
                        subject = "Mathématiques",
                    ),
                )
            scheduleSlotDao.insertStudentCrossRefs(listOf(ScheduleSlotStudentCrossRef(scheduleSlotId = slotId, studentId = aliceId)))

            // When
            studentDao.delete(alice.copy(id = aliceId))

            // Then
            assertEquals(emptyList(), scheduleSlotDao.getAllStudentCrossRefs())
        }

    @Test
    fun `should remove only the given slot's cross-refs when deleting cross-refs for a slot`() =
        runTest {
            // Given: two slots, each with one assigned student
            val aliceId = studentDao.insert(StudentEntity(firstName = "Alice", className = "CE2"))
            val amirId = studentDao.insert(StudentEntity(firstName = "Amir", className = "CM2"))
            val mathSlotId =
                scheduleSlotDao.insert(
                    ScheduleSlotEntity(
                        dayOfWeek = DayOfWeek.MONDAY,
                        startTime = LocalTime.of(8, 0),
                        endTime = LocalTime.of(9, 0),
                        subject = "Mathématiques",
                    ),
                )
            val sportSlotId =
                scheduleSlotDao.insert(
                    ScheduleSlotEntity(
                        dayOfWeek = DayOfWeek.TUESDAY,
                        startTime = LocalTime.of(8, 0),
                        endTime = LocalTime.of(9, 0),
                        subject = "Sport",
                    ),
                )
            scheduleSlotDao.insertStudentCrossRefs(
                listOf(
                    ScheduleSlotStudentCrossRef(scheduleSlotId = mathSlotId, studentId = aliceId),
                    ScheduleSlotStudentCrossRef(scheduleSlotId = sportSlotId, studentId = amirId),
                ),
            )

            // When
            scheduleSlotDao.deleteStudentCrossRefsForSlot(mathSlotId)

            // Then
            assertEquals(listOf(sportSlotId), scheduleSlotDao.getAllStudentCrossRefs().map { it.scheduleSlotId })
        }

    @Test
    fun `should remove every cross-ref row when deleting all cross-refs`() =
        runTest {
            // Given
            val aliceId = studentDao.insert(StudentEntity(firstName = "Alice", className = "CE2"))
            val slotId =
                scheduleSlotDao.insert(
                    ScheduleSlotEntity(
                        dayOfWeek = DayOfWeek.MONDAY,
                        startTime = LocalTime.of(8, 0),
                        endTime = LocalTime.of(9, 0),
                        subject = "Mathématiques",
                    ),
                )
            scheduleSlotDao.insertStudentCrossRefs(listOf(ScheduleSlotStudentCrossRef(scheduleSlotId = slotId, studentId = aliceId)))

            // When
            scheduleSlotDao.deleteAllStudentCrossRefs()

            // Then
            assertEquals(emptyList(), scheduleSlotDao.getAllStudentCrossRefs())
        }
}
