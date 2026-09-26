package fr.alaedine.aesh.data.local.dao

import androidx.room.Room
import fr.alaedine.aesh.data.local.AeshDatabase
import fr.alaedine.aesh.data.local.entity.ScheduleSlotEntity
import java.time.DayOfWeek
import java.time.LocalTime
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

    @BeforeTest
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AeshDatabase::class.java)
            .build()
        scheduleSlotDao = database.scheduleSlotDao()
    }

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `should return the generated id when a schedule slot is inserted`() = runTest {
        // Given
        val scheduleSlot = ScheduleSlotEntity(
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
    fun `should return null when no schedule slot exists for the given id`() = runTest {
        // Given / When
        val result = scheduleSlotDao.getById(id = 42L)

        // Then
        assertNull(result)
    }

    @Test
    fun `should emit schedule slots ordered by day then start time when observing all`() = runTest {
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
    fun `should persist changes when an existing schedule slot is updated`() = runTest {
        // Given
        val id = scheduleSlotDao.insert(
            ScheduleSlotEntity(
                dayOfWeek = DayOfWeek.MONDAY,
                startTime = LocalTime.of(8, 0),
                endTime = LocalTime.of(9, 0),
                subject = "Mathématiques",
            ),
        )
        val updated = ScheduleSlotEntity(
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
    fun `should remove the schedule slot when deleted`() = runTest {
        // Given
        val id = scheduleSlotDao.insert(
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
    fun `should insert every schedule slot preserving their ids when inserting all`() = runTest {
        // Given
        val scheduleSlots = listOf(
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
    fun `should remove every schedule slot when deleting all`() = runTest {
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
}
