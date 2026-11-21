package fr.alaedine.aesh.domain.scanner

import fr.alaedine.aesh.domain.model.ParsedScheduleSlot
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Best-effort parser turning the raw text recognized (by
 * [fr.alaedine.aesh.domain.repository.TextRecognitionRepository]) from a
 * photo of a physical schedule into [ParsedScheduleSlot] fields.
 *
 * Physical schedules come in wildly different layouts (grids, lists,
 * handwriting...), and OCR output only preserves line breaks, not the
 * original 2D layout. This therefore only recognizes a handful of common
 * patterns, matched on every line of the recognized text — a day-of-week
 * name (French or English, in full or abbreviated), a class's start and end
 * times ("8h30 - 9h25", "8 h 30 à 9 h 25", "13:30–14:25", "1:30 PM"...), and
 * a room ("Salle B 204", "CDI"...) — keeping the first match found for each
 * field and leaving the rest for the user to fill in or correct on the
 * pre-filled form. It never throws: unrecognized fields are simply left
 * `null`.
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

    // Also ordinary words ("mon", "mer", "sam"...), so only trusted as days in the cases findDayOfWeek allows.
    private val DAY_ABBREVIATIONS: Map<String, DayOfWeek> =
        mapOf(
            "lun" to DayOfWeek.MONDAY,
            "mon" to DayOfWeek.MONDAY,
            "mar" to DayOfWeek.TUESDAY,
            "tue" to DayOfWeek.TUESDAY,
            "mer" to DayOfWeek.WEDNESDAY,
            "wed" to DayOfWeek.WEDNESDAY,
            "jeu" to DayOfWeek.THURSDAY,
            "thu" to DayOfWeek.THURSDAY,
            "ven" to DayOfWeek.FRIDAY,
            "fri" to DayOfWeek.FRIDAY,
            "sam" to DayOfWeek.SATURDAY,
            "sat" to DayOfWeek.SATURDAY,
            "dim" to DayOfWeek.SUNDAY,
            "sun" to DayOfWeek.SUNDAY,
        )

    // Longest names first so e.g. "tuesday" is tried before any shorter accidental match.
    private val DAY_REGEX =
        Regex(
            "\\b(?:" + DAY_KEYWORDS.keys.sortedByDescending { it.length }.joinToString("|") + ")\\b",
            RegexOption.IGNORE_CASE,
        )
    private val ABBREVIATED_DAY_REGEX =
        Regex("\\b(?:" + DAY_ABBREVIATIONS.keys.joinToString("|") + ")\\b\\.?", RegexOption.IGNORE_CASE)

    /*
     * A single time: "8h", "8h30", "8 h 30", "14H00", "08:30", "1:30 PM", or "08.30" (with exactly two minute
     * digits). It can't be glued to other digits or dots, so phone numbers ("01.23.45.67.89"), dates ("12.03.2026")
     * and decimals ("1.5") aren't mistaken for times. Groups: hour, minutes, then AM/PM.
     */
    private const val TIME_PATTERN =
        """(?<![\d.:])(\d{1,2})(?:\s?[hH]\s?(\d{2})?|:(\d{2})|\.(\d{2})(?![.\d]))(?![\d:])(?:\s?([AaPp])\.?[Mm]\.?\b)?"""
    private val TIME_TOKEN_REGEX = Regex(TIME_PATTERN)

    // Two times joined by a dash, a slash, "à"/"au"/"to", optionally after "de"/"from": "de 8h à 9h", "8h-9h".
    private val TIME_RANGE_REGEX =
        Regex("""(?:\b(?:de|from)\s+)?$TIME_PATTERN\s*(?:-|–|—|/|→|>|\bà\b|\ba\b|\bau\b|\bto\b)\s*$TIME_PATTERN""", RegexOption.IGNORE_CASE)

    /** Classes happen during the day: anything else (a duration such as "EPS (2h)", a stray number) isn't a class time. */
    private val PLAUSIBLE_CLASS_HOURS = 7..20

    // A "salle"/"room" label followed by its name: "Salle B12", "salle B 204", "Salle n°12", "Room 203", "Salle 1.12".
    private val LABELLED_ROOM_REGEX =
        Regex(
            """\b(?:salle|room|classroom)\b\s*(?:n\s?[°o]\s*)?[:\-]?\s*([\p{L}0-9][\p{L}0-9.\-]*(?:\s+\d[\p{L}0-9.\-]*)?)""",
            RegexOption.IGNORE_CASE,
        )

    // A line holding nothing but a room: a well-known school place, or a room code such as "B204" or "S12".
    private val STANDALONE_ROOM_REGEX =
        Regex("""^(?:CDI|BCD|gymnase|préau|salle polyvalente|[A-Z]\d{2,3})$""", RegexOption.IGNORE_CASE)

    // Lines that name the teacher ("Mme Bernard", "M. Dupont") or the timetable itself rather than the class.
    private val TEACHER_LINE_REGEX = Regex("""^(?:M\.|MM\.|Mme|Mmes|Mlle|Mr|Mrs|Ms|Monsieur|Madame)(?:\s|$)""", RegexOption.IGNORE_CASE)
    private val HEADER_LINE_REGEX =
        Regex(
            """\b(?:emploi du temps|timetable|schedule|semaine|week|année|collège|college|école|lycée|tél|tel)\b""",
            RegexOption.IGNORE_CASE,
        )

    private val WHITESPACE_REGEX = Regex("""\s+""")
    private val LEFTOVER_CONNECTOR_REGEX = Regex("""^(?:de|à|au|from|to)\s+|\s+(?:de|à|au|from|to)$""", RegexOption.IGNORE_CASE)

    fun parse(recognizedText: String): ParsedScheduleSlot {
        val lines = recognizedText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val (startTime, endTime) = findTimes(lines.map { it.withoutRoom() })
        return ParsedScheduleSlot(
            dayOfWeek = lines.firstNotNullOfOrNull { findDayOfWeek(it) },
            startTime = startTime,
            endTime = endTime,
            subject = findSubject(lines),
            room = lines.firstNotNullOfOrNull { findRoom(it) },
        )
    }

    /**
     * A full day name anywhere, or an abbreviated one ("Lun", "Mar"...) when it can only mean a day: written with its
     * dot ("Lun."), or in a header row made of nothing but days.
     */
    private fun findDayOfWeek(line: String): DayOfWeek? {
        DAY_REGEX.find(line)?.let { return DAY_KEYWORDS[it.value.lowercase()] }
        val abbreviation = ABBREVIATED_DAY_REGEX.find(line) ?: return null
        val isWrittenAsAbbreviation = abbreviation.value.endsWith('.')
        return if (isWrittenAsAbbreviation ||
            line.isDayHeaderRow()
        ) {
            DAY_ABBREVIATIONS[abbreviation.value.trimEnd('.').lowercase()]
        } else {
            null
        }
    }

    private fun String.isDayHeaderRow(): Boolean =
        ABBREVIATED_DAY_REGEX.replace(DAY_REGEX.replace(this, ""), "").none { it.isLetterOrDigit() }

    /**
     * The first time range found on a single line; otherwise the first two separate times, in reading order, when
     * the second comes after the first (times stacked around the subject, as in "13h30 / Sciences / 14h30"); otherwise
     * just the first time. A time and a range from different lines are never combined: they belong to different classes.
     */
    private fun findTimes(lines: List<String>): Pair<LocalTime?, LocalTime?> {
        for (line in lines) {
            for (match in TIME_RANGE_REGEX.findAll(line)) {
                val start = match.toLocalTimeOrNull(groupOffset = 0) ?: continue
                val end = match.toLocalTimeOrNull(groupOffset = TIME_GROUP_COUNT) ?: continue
                // Physical schedules print "start - end"; swap if OCR/order reversed them so the range is valid.
                return if (end.isAfter(start)) start to end else end to start
            }
        }
        val times = lines.flatMap { line -> TIME_TOKEN_REGEX.findAll(line).mapNotNull { it.toLocalTimeOrNull(groupOffset = 0) } }
        val start = times.firstOrNull()
        val end = times.getOrNull(1)?.takeIf { start != null && it.isAfter(start) }
        return start to end
    }

    /** Reads the time in the [TIME_GROUP_COUNT] capture groups starting after [groupOffset], if it's a plausible class time. */
    private fun MatchResult.toLocalTimeOrNull(groupOffset: Int): LocalTime? {
        val group = { index: Int -> groupValues[groupOffset + index] }
        var hour = group(1).toIntOrNull() ?: return null
        val minute = listOf(group(2), group(3), group(4)).firstOrNull { it.isNotEmpty() }?.toIntOrNull() ?: 0
        when (group(5).lowercase()) {
            "p" -> if (hour in 1..11) hour += 12
            "a" -> if (hour == 12) hour = 0
        }
        if (hour !in PLAUSIBLE_CLASS_HOURS || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }

    private fun findRoom(line: String): String? =
        LABELLED_ROOM_REGEX
            .find(line)
            ?.groupValues
            ?.get(1)
            ?.trimEnd('.', '-')
            ?: line.takeIf { STANDALONE_ROOM_REGEX.matches(it) }

    private fun String.withoutRoom(): String = LABELLED_ROOM_REGEX.replace(this, " ")

    /**
     * The subject is assumed to be the longest line left after stripping
     * every other recognized field from each candidate line, leaving out the
     * lines that name the teacher, a room or the timetable itself: schedules
     * typically print the subject name more prominently/verbosely than the
     * day, time or room.
     */
    private fun findSubject(lines: List<String>): String? =
        lines
            .filterNot { it.isDayHeaderRow() || it.isTeacherHeaderOrRoomLine() }
            .map { it.stripRecognizedFields() }
            .filter { line -> line.any { it.isLetter() } }
            .maxByOrNull { it.length }

    private fun String.isTeacherHeaderOrRoomLine(): Boolean =
        TEACHER_LINE_REGEX.containsMatchIn(this) || HEADER_LINE_REGEX.containsMatchIn(this) || STANDALONE_ROOM_REGEX.matches(this)

    private fun String.stripRecognizedFields(): String {
        // Only the times recognized as class times: "EPS (2h)" keeps its duration.
        var result =
            TIME_RANGE_REGEX.replace(this) { range ->
                val isClassTimeRange = range.toLocalTimeOrNull(groupOffset = 0) != null && range.toLocalTimeOrNull(TIME_GROUP_COUNT) != null
                if (isClassTimeRange) " " else range.value
            }
        result = TIME_TOKEN_REGEX.replace(result) { time -> if (time.toLocalTimeOrNull(groupOffset = 0) != null) " " else time.value }
        result = DAY_REGEX.replace(result, " ")
        result = LABELLED_ROOM_REGEX.replace(result, " ")
        result = result.replace(WHITESPACE_REGEX, " ").trim(' ', '-', ':', ',', '.', '|', '/')
        return result.replace(LEFTOVER_CONNECTOR_REGEX, "").trim(' ', '-', ':', ',', '.', '|', '/')
    }

    /** Capture groups in [TIME_PATTERN]: hour, minutes after "h", after ":" or after ".", and AM/PM. */
    private const val TIME_GROUP_COUNT = 5
}
