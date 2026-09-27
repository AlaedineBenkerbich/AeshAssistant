package fr.alaedine.aesh.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.alaedine.aesh.domain.model.ScheduleSlot
import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.domain.repository.DailyReportRepository
import fr.alaedine.aesh.domain.repository.ScheduleSlotRepository
import fr.alaedine.aesh.domain.repository.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/**
 * Presentation-layer state holder for the dashboard/home screen.
 *
 * Combines [scheduleSlotRepository], [studentRepository] and
 * [dailyReportRepository] with the currently selected date (see
 * [onDateSelected], [onPreviousWeekClicked] and [onNextWeekClicked]) to
 * derive that date's [LessonBlock]s: solely the schedule slots whose day of
 * week matches it, each resolved with its assigned students paired with
 * whether they already have a report for that date (see
 * [StudentReportStatus]) — this is what determines which students need an
 * observation, not the full student roster. Also derives which days of the
 * week have at least one scheduled class, so the dashboard's calendar week
 * strip can mark them with an event dot regardless of which date is
 * currently selected.
 */
class HomeViewModel(
    private val studentRepository: StudentRepository,
    private val dailyReportRepository: DailyReportRepository,
    private val scheduleSlotRepository: ScheduleSlotRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** The date whose lesson blocks/report statuses are shown, independent from [HomeUiState.today]; changed via [onDateSelected], [onPreviousWeekClicked] and [onNextWeekClicked]. */
    private val selectedDateFlow = MutableStateFlow(_uiState.value.date)

    init {
        combine(
            selectedDateFlow,
            studentRepository.observeStudents(),
            dailyReportRepository.observeReports(),
            scheduleSlotRepository.observeScheduleSlots(),
        ) { selectedDate, students, reports, scheduleSlots ->
            val studentsById = students.associateBy { it.id }
            val studentIdsWithReportOnSelectedDate =
                reports
                    .filter { it.date == selectedDate }
                    .mapTo(mutableSetOf()) { it.studentId }
            val lessonBlocks =
                scheduleSlots
                    .filter { it.dayOfWeek == selectedDate.dayOfWeek }
                    .sortedBy { it.startTime }
                    .map { slot -> slot.toLessonBlock(studentsById, studentIdsWithReportOnSelectedDate) }
            val daysWithScheduledClasses = scheduleSlots.mapTo(mutableSetOf()) { it.dayOfWeek }
            Triple(selectedDate, lessonBlocks, daysWithScheduledClasses)
        }.onEach { (selectedDate, lessonBlocks, daysWithScheduledClasses) ->
            _uiState.update {
                it.copy(
                    date = selectedDate,
                    lessonBlocks = lessonBlocks,
                    daysWithScheduledClasses = daysWithScheduledClasses,
                    isLoading = false,
                )
            }
        }.launchIn(viewModelScope)
    }

    /** Displays [date]'s classes/report statuses instead of whichever day was previously selected, letting the AESH freely browse the calendar and reach any day, not just the present. */
    fun onDateSelected(date: LocalDate) {
        selectedDateFlow.value = date
    }

    /** Moves the selected date back exactly one week, keeping the same day of week (e.g. Wednesday -> the previous week's Wednesday). */
    fun onPreviousWeekClicked() {
        selectedDateFlow.update { it.minusWeeks(1) }
    }

    /** Moves the selected date forward exactly one week, keeping the same day of week. */
    fun onNextWeekClicked() {
        selectedDateFlow.update { it.plusWeeks(1) }
    }

    /** Resolves this slot's [fr.alaedine.aesh.domain.model.ScheduleSlot.studentIds] into a [LessonBlock], skipping any id that no longer matches a known student. */
    private fun ScheduleSlot.toLessonBlock(
        studentsById: Map<Long, Student>,
        studentIdsWithReportOnSelectedDate: Set<Long>,
    ): LessonBlock =
        LessonBlock(
            scheduleSlotId = id,
            startTime = startTime,
            endTime = endTime,
            subject = subject,
            room = room,
            studentStatuses =
                studentIds.mapNotNull { studentId ->
                    studentsById[studentId]?.let { student ->
                        StudentReportStatus(student = student, hasReport = student.id in studentIdsWithReportOnSelectedDate)
                    }
                },
        )
}
