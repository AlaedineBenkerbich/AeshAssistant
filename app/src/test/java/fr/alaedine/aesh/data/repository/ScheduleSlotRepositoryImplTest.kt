package fr.alaedine.aesh.data.repository

import androidx.room.Room
import fr.alaedine.aesh.data.local.AeshDatabase
import fr.alaedine.aesh.data.local.dao.ScheduleSlotDao
import fr.alaedine.aesh.domain.model.ScheduleSlot
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
 * Exercises [ScheduleSlotRepositoryImpl] end-to-end against a real
 * (in-memory) Room database, verifying the `domain`-facing CRUD contract —
 * including the [ScheduleSlot] <->
 * [fr.alaedine.aesh.data.local.entity.ScheduleSlotEntity] mapping — that the
 * rest of the app depends on.
 *
 * [Config.application] swaps out the manifest-declared
 * [fr.alaedine.aesh.AeshApplication] (which starts Koin) for a plain
 * [android.app.Application]: these tests build Room directly and don't need
 * the DI graph, and starting Koin repeatedly across test methods would
 * throw [org.koin.core.error.KoinApplicationAlreadyStartedException].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ScheduleSlotRepositoryImplTest {
    private lateinit var database: AeshDatabase
    private lateinit var scheduleSlotDao: ScheduleSlotDao
    private lateinit var repository: ScheduleSlotRepositoryImpl

    @BeforeTest
    fun createRepository() {
        database =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AeshDatabase::class.java)
                .build()
        scheduleSlotDao = database.scheduleSlotDao()
        repository = ScheduleSlotRepositoryImpl(scheduleSlotDao)
    }

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `should return the generated id when a schedule slot is added`() =
        runTest {
            // Given
            val scheduleSlot =
                ScheduleSlot(
                    dayOfWeek = DayOfWeek.MONDAY,
                    startTime = LocalTime.of(8, 0),
                    endTime = LocalTime.of(9, 0),
                    subject = "Mathématiques",
                    room = "B12",
                )

            // When
            val id = repository.addScheduleSlot(scheduleSlot)

            // Then
            assertEquals(scheduleSlot.copy(id = id), repository.getScheduleSlotById(id))
        }

    @Test
    fun `should return null when no schedule slot exists for the given id`() =
        runTest {
            // Given / When
            val result = repository.getScheduleSlotById(id = 42L)

            // Then
            assertNull(result)
        }

    @Test
    fun `should emit schedule slots ordered by day then start time when observing schedule slots`() =
        runTest {
            // Given
            repository.addScheduleSlot(
                ScheduleSlot(
                    dayOfWeek = DayOfWeek.TUESDAY,
                    startTime = LocalTime.of(8, 0),
                    endTime = LocalTime.of(9, 0),
                    subject = "Sport",
                ),
            )
            repository.addScheduleSlot(
                ScheduleSlot(
                    dayOfWeek = DayOfWeek.MONDAY,
                    startTime = LocalTime.of(8, 0),
                    endTime = LocalTime.of(9, 0),
                    subject = "Mathématiques",
                ),
            )

            // When
            val scheduleSlots = repository.observeScheduleSlots().first()

            // Then
            assertEquals(listOf("Mathématiques", "Sport"), scheduleSlots.map { it.subject })
        }

    @Test
    fun `should persist changes when an existing schedule slot is updated`() =
        runTest {
            // Given
            val id =
                repository.addScheduleSlot(
                    ScheduleSlot(
                        dayOfWeek = DayOfWeek.MONDAY,
                        startTime = LocalTime.of(8, 0),
                        endTime = LocalTime.of(9, 0),
                        subject = "Mathématiques",
                    ),
                )
            val updated =
                ScheduleSlot(
                    id = id,
                    dayOfWeek = DayOfWeek.WEDNESDAY,
                    startTime = LocalTime.of(14, 0),
                    endTime = LocalTime.of(15, 0),
                    subject = "Histoire",
                    room = "C3",
                )

            // When
            repository.updateScheduleSlot(updated)

            // Then
            assertEquals(updated, repository.getScheduleSlotById(id))
        }

    @Test
    fun `should remove the schedule slot when deleted`() =
        runTest {
            // Given
            val id =
                repository.addScheduleSlot(
                    ScheduleSlot(
                        dayOfWeek = DayOfWeek.MONDAY,
                        startTime = LocalTime.of(8, 0),
                        endTime = LocalTime.of(9, 0),
                        subject = "Mathématiques",
                    ),
                )
            val scheduleSlot = repository.getScheduleSlotById(id)!!

            // When
            repository.deleteScheduleSlot(scheduleSlot)

            // Then
            assertNull(repository.getScheduleSlotById(id))
        }
}
