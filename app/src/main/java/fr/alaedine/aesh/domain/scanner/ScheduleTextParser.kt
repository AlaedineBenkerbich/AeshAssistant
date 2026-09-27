package fr.alaedine.aesh.domain.scanner

import fr.alaedine.aesh.domain.model.ParsedScheduleSlot
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Best-effort parser turning the raw text recognized (by
 * [fr.alaedine.aesh.domain.repository.ScheduleScannerRepository]) from a
 * photo of a physical schedule into [ParsedScheduleSlot] fields.
 *
 * Physical schedules come in wildly different layouts (grids, lists,
 * handwriting...), and OCR output only preserves line breaks, not the
 * original 2D layout. This therefore only recognizes a handful of common
 * patterns, matched independently on every line of the recognized text —
 * a day-of-week name (French or English), an "HH:MM - HH:MM"-ish time
 * range, and a "salle"/"room" label — keeping the first match found for
 * each field and leaving the rest for the user to fill in or correct on
 * the pre-filled form. It never throws: unrecognized fields are simply
 * left `null`.
 */
object ScheduleTextParser {
    private val DAY_KEYWORDS: Map<String, DayOfWeek> =
        mapOf(
            "lundi" to DayOfWeek.MONDAY,
            "monday" to DayOfWeek.MONDAY,
            "mardi" to DayOfWeek.TUESDAY,
            "tuesday" to DayOfWeek.TUESDAY,
            "mercredi" to DayOfWeek.WEDNESDAY,
            "wednesday" to DayOfWeek.WEDNESDAY,
            "jeudi" to DayOfWeek.THURSDAY,
            "thursday" to DayOfWeek.THURSDAY,
            "vendredi" to DayOfWeek.FRIDAY,
            "friday" to DayOfWeek.FRIDAY,
            "samedi" to DayOfWeek.SATURDAY,
            "saturday" to DayOfWeek.SATURDAY,
            "dimanche" to DayOfWeek.SUNDAY,
            "sunday" to DayOfWeek.SUNDAY,
        )

    // Longest names first so e.g. "tuesday" is tried before any shorter accidental match.
    private val DAY_REGEX =
        Regex(
            "\\b(?:" + DAY_KEYWORDS.keys.sortedByDescending { it.length }.joinToString("|") + ")\\b",
            RegexOption.IGNORE_CASE,
        )

    // A single "HH:MM"-ish token, e.g. "8h", "8h30", "08:00", "08.30".
    private const val TIME_PATTERN = """(\d{1,2})[:h.](\d{2})?"""
    private val TIME_TOKEN_REGEX = Regex("""\b$TIME_PATTERN\b""", RegexOption.IGNORE_CASE)

    // Two time tokens joined by a dash/"à"/"to", e.g. "8h-9h", "08:00 à 09:00".
    private val TIME_RANGE_REGEX =
        Regex(
            """\b$TIME_PATTERN\b\s*(?:-|–|—|à|to)\s*\b$TIME_PATTERN\b""",
            RegexOption.IGNORE_CASE,
        )

    // A "salle"/"room"/"classroom" label followed by a single alphanumeric
    // token (e.g. "Salle B12", "Room 203", "Salle Gymnase"). Deliberately
    // captures only one token — combined with matching per-line rather than
    // on the whole text, this can never swallow unrelated text (e.g. the
    // subject on the next line) into the room field.
    private val ROOM_REGEX =
        Regex(
            """\b(?:salle|room|classroom)\b\s*[:\-]?\s*([\p{L}0-9][\p{L}0-9\-]*)""",
            RegexOption.IGNORE_CASE,
        )

    private val WHITESPACE_REGEX = Regex("""\s+""")

    fun parse(recognizedText: String): ParsedScheduleSlot {
        val lines = recognizedText.lines().map { it.trim() }.filter { it.isNotBlank() }

        var dayOfWeek: DayOfWeek? = null
        var startTime: LocalTime? = null
        var endTime: LocalTime? = null
        var room: String? = null

        for (line in lines) {
            dayOfWeek = dayOfWeek ?: findDayOfWeek(line)
            room = room ?: findRoom(line)
            if (startTime == null || endTime == null) {
                val (lineStart, lineEnd) = findTimeRange(line)
                startTime = startTime ?: lineStart
                endTime = endTime ?: lineEnd
            }
        }

        return ParsedScheduleSlot(
            dayOfWeek = dayOfWeek,
            startTime = startTime,
            endTime = endTime,
            subject = findSubject(lines),
            room = room,
        )
    }

    private fun findDayOfWeek(line: String): DayOfWeek? = DAY_REGEX.find(line)?.let { DAY_KEYWORDS[it.value.lowercase()] }

    /** @return the recognized start/end time, either of which may be `null` if not found. */
    private fun findTimeRange(line: String): Pair<LocalTime?, LocalTime?> {
        TIME_RANGE_REGEX.find(line)?.let { match ->
            val start = toLocalTimeOrNull(match.groupValues[1], match.groupValues[2])
            val end = toLocalTimeOrNull(match.groupValues[3], match.groupValues[4])
            if (start != null && end != null) {
                // Physical schedules print "start - end"; swap if OCR/order
                // reversed them so the result is always a valid range.
                return if (end.isAfter(start)) start to end else end to start
            }
        }
        val single = TIME_TOKEN_REGEX.find(line)?.let { toLocalTimeOrNull(it.groupValues[1], it.groupValues[2]) }
        return single to null
    }

    private fun toLocalTimeOrNull(
        hourText: String,
        minuteText: String,
    ): LocalTime? {
        val hour = hourText.toIntOrNull() ?: return null
        val minute = minuteText.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }

    private fun findRoom(line: String): String? = ROOM_REGEX.find(line)?.groupValues?.get(1)

    /**
     * The subject is assumed to be the longest line left after stripping
     * every other recognized field from each candidate line: schedules
     * typically print the subject name more prominently/verbosely than the
     * day, time or room.
     */
    private fun findSubject(lines: List<String>): String? =
        lines
            .map { it.stripRecognizedFields() }
            .filter { it.isNotBlank() }
            .maxByOrNull { it.length }

    private fun String.stripRecognizedFields(): String {
        var result = TIME_RANGE_REGEX.replace(this, " ")
        result = TIME_TOKEN_REGEX.replace(result, " ")
        result = DAY_REGEX.replace(result, " ")
        result = ROOM_REGEX.replace(result, " ")
        return result.replace(WHITESPACE_REGEX, " ").trim(' ', '-', ':', ',', '.')
    }
}
