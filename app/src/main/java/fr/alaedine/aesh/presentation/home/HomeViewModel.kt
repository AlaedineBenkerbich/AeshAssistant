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

/**
 * Presentation-layer state holder for the dashboard/home screen.
 *
 * Combines [scheduleSlotRepository], [studentRepository] and
 * [dailyReportRepository] to derive today's [LessonBlock]s: solely the
 * schedule slots whose day of week matches today, each resolved with its
 * assigned students paired with whether they already have a report for
 * today (see [StudentReportStatus]) — this is what determines which
 * students need an observation today, not the full student roster. Also
 * derives which days of the week have at least one scheduled class, so the
 * dashboard's calendar week strip can mark them with an event dot.
 */
class HomeViewModel(
    private val studentRepository: StudentRepository,
    private val dailyReportRepository: DailyReportRepository,
    private val scheduleSlotRepository: ScheduleSlotRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        val today = _uiState.value.date
        combine(
            studentRepository.observeStudents(),
            dailyReportRepository.observeReports(),
            scheduleSlotRepository.observeScheduleSlots(),
        ) { students, reports, scheduleSlots ->
            val studentsById = students.associateBy { it.id }
            val studentIdsWithReportToday =
                reports
                    .filter { it.date == today }
                    .mapTo(mutableSetOf()) { it.studentId }
            val lessonBlocks =
                scheduleSlots
                    .filter { it.dayOfWeek == today.dayOfWeek }
                    .sortedBy { it.startTime }
                    .map { slot -> slot.toLessonBlock(studentsById, studentIdsWithReportToday) }
            val daysWithScheduledClasses = scheduleSlots.mapTo(mutableSetOf()) { it.dayOfWeek }
            lessonBlocks to daysWithScheduledClasses
        }.onEach { (lessonBlocks, daysWithScheduledClasses) ->
            _uiState.update {
                it.copy(
                    lessonBlocks = lessonBlocks,
                    daysWithScheduledClasses = daysWithScheduledClasses,
                    isLoading = false,
                )
            }
        }.launchIn(viewModelScope)
    }

    /** Resolves this slot's [fr.alaedine.aesh.domain.model.ScheduleSlot.studentIds] into a [LessonBlock], skipping any id that no longer matches a known student. */
    private fun ScheduleSlot.toLessonBlock(
        studentsById: Map<Long, Student>,
        studentIdsWithReportToday: Set<Long>,
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
                        StudentReportStatus(student = student, hasReportToday = student.id in studentIdsWithReportToday)
                    }
                },
        )
}
