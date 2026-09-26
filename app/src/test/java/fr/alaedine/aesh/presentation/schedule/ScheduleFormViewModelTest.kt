package fr.alaedine.aesh.presentation.schedule

import fr.alaedine.aesh.domain.model.ParsedScheduleSlot
import fr.alaedine.aesh.domain.model.ScheduleSlot
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [ScheduleFormViewModel] reads/writes
 * [fr.alaedine.aesh.domain.repository.ScheduleSlotRepository] through
 * `viewModelScope`, which requires the `Main` dispatcher to be available;
 * [UnconfinedTestDispatcher] makes coroutines launched on it run eagerly so
 * state updates are visible immediately, without needing manual
 * virtual-time advancement.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleFormViewModelTest {

    @BeforeTest
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `should expose default fields when adding a new schedule slot`() = runTest {
        // Given / When
        val viewModel = ScheduleFormViewModel(FakeScheduleSlotRepository(), scheduleSlotId = null)

        // Then
        assertEquals(ScheduleFormUiState(), viewModel.uiState.value)
    }

    @Test
    fun `should load the existing schedule slot fields when editing`() = runTest {
        // Given
        val mathSlot = ScheduleSlot(
            id = 1L,
            dayOfWeek = DayOfWeek.TUESDAY,
            startTime = LocalTime.of(10, 0),
            endTime = LocalTime.of(11, 0),
            subject = "Mathématiques",
            room = "B12",
        )
        val repository = FakeScheduleSlotRepository(initialScheduleSlots = listOf(mathSlot))

        // When
        val viewModel = ScheduleFormViewModel(repository, scheduleSlotId = mathSlot.id)

        // Then
        val state = viewModel.uiState.value
        assertEquals(mathSlot.dayOfWeek, state.dayOfWeek)
        assertEquals(mathSlot.startTime, state.startTime)
        assertEquals(mathSlot.endTime, state.endTime)
        assertEquals(mathSlot.subject, state.subject)
        assertEquals(mathSlot.room, state.room)
        assertTrue(state.isEditing)
    }

    @Test
    fun `should not allow saving when the subject is blank`() = runTest {
        // Given
        val viewModel = ScheduleFormViewModel(FakeScheduleSlotRepository(), scheduleSlotId = null)

        // When
        viewModel.onSubjectChanged("")

        // Then
        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun `should not allow saving when the end time is not after the start time`() = runTest {
        // Given
        val viewModel = ScheduleFormViewModel(FakeScheduleSlotRepository(), scheduleSlotId = null)
        viewModel.onSubjectChanged("Mathématiques")

        // When
        viewModel.onStartTimeChanged(LocalTime.of(9, 0))
        viewModel.onEndTimeChanged(LocalTime.of(9, 0))

        // Then
        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun `should allow saving when the required fields are valid`() = runTest {
        // Given
        val viewModel = ScheduleFormViewModel(FakeScheduleSlotRepository(), scheduleSlotId = null)

        // When
        viewModel.onSubjectChanged("Mathématiques")

        // Then
        assertTrue(viewModel.uiState.value.canSave)
    }

    @Test
    fun `should add a new schedule slot when saving without a schedule slot id`() = runTest {
        // Given
        val repository = FakeScheduleSlotRepository()
        val viewModel = ScheduleFormViewModel(repository, scheduleSlotId = null)
        viewModel.onDayOfWeekChanged(DayOfWeek.THURSDAY)
        viewModel.onSubjectChanged("Mathématiques")
        viewModel.onRoomChanged("B12")

        // When
        viewModel.onSaveClicked()

        // Then
        val saved = repository.observeScheduleSlots().first().single()
        assertEquals(DayOfWeek.THURSDAY, saved.dayOfWeek)
        assertEquals("Mathématiques", saved.subject)
        assertEquals("B12", saved.room)
        assertTrue(viewModel.uiState.value.isSaved)
    }

    @Test
    fun `should update the existing schedule slot when saving with a schedule slot id`() = runTest {
        // Given
        val mathSlot = ScheduleSlot(
            id = 1L,
            dayOfWeek = DayOfWeek.MONDAY,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(9, 0),
            subject = "Mathématiques",
        )
        val repository = FakeScheduleSlotRepository(initialScheduleSlots = listOf(mathSlot))
        val viewModel = ScheduleFormViewModel(repository, scheduleSlotId = mathSlot.id)

        // When
        viewModel.onRoomChanged("B12")
        viewModel.onSaveClicked()

        // Then
        assertEquals("B12", repository.getScheduleSlotById(mathSlot.id)?.room)
        assertTrue(viewModel.uiState.value.isSaved)
    }

    @Test
    fun `should not persist anything when saving without the required fields`() = runTest {
        // Given
        val repository = FakeScheduleSlotRepository()
        val viewModel = ScheduleFormViewModel(repository, scheduleSlotId = null)
        viewModel.onSubjectChanged("")

        // When
        viewModel.onSaveClicked()

        // Then
        assertEquals(emptyList(), repository.observeScheduleSlots().first())
        assertFalse(viewModel.uiState.value.isSaved)
    }

    @Test
    fun `should apply every recognized scanner field when adding a new schedule slot`() = runTest {
        // Given
        val prefill = ParsedScheduleSlot(
            dayOfWeek = DayOfWeek.TUESDAY,
            startTime = LocalTime.of(14, 0),
            endTime = LocalTime.of(15, 0),
            subject = "Mathématiques",
            room = "B12",
        )

        // When
        val viewModel = ScheduleFormViewModel(FakeScheduleSlotRepository(), scheduleSlotId = null, prefill = prefill)

        // Then
        val state = viewModel.uiState.value
        assertEquals(DayOfWeek.TUESDAY, state.dayOfWeek)
        assertEquals(LocalTime.of(14, 0), state.startTime)
        assertEquals(LocalTime.of(15, 0), state.endTime)
        assertEquals("Mathématiques", state.subject)
        assertEquals("B12", state.room)
    }

    @Test
    fun `should keep the default fields the scanner didn't recognize`() = runTest {
        // Given
        val defaults = ScheduleFormUiState()
        val prefill = ParsedScheduleSlot(subject = "Mathématiques")

        // When
        val viewModel = ScheduleFormViewModel(FakeScheduleSlotRepository(), scheduleSlotId = null, prefill = prefill)

        // Then
        val state = viewModel.uiState.value
        assertEquals(defaults.dayOfWeek, state.dayOfWeek)
        assertEquals(defaults.startTime, state.startTime)
        assertEquals(defaults.endTime, state.endTime)
        assertEquals(defaults.room, state.room)
        assertEquals("Mathématiques", state.subject)
    }

    @Test
    fun `should not let a scanner prefill override the loaded values when editing`() = runTest {
        // Given
        val mathSlot = ScheduleSlot(
            id = 1L,
            dayOfWeek = DayOfWeek.MONDAY,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(9, 0),
            subject = "Mathématiques",
        )
        val repository = FakeScheduleSlotRepository(initialScheduleSlots = listOf(mathSlot))
        val prefill = ParsedScheduleSlot(subject = "Should not appear")

        // When
        val viewModel = ScheduleFormViewModel(repository, scheduleSlotId = mathSlot.id, prefill = prefill)

        // Then
        assertEquals(mathSlot.subject, viewModel.uiState.value.subject)
    }
}
