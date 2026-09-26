package fr.alaedine.aesh.data.local

import androidx.room.TypeConverter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Room [androidx.room.TypeConverters] for column types with no built-in
 * mapping.
 *
 * [LocalDate] (see [fr.alaedine.aesh.data.local.entity.DailyReportEntity])
 * and [LocalTime] (see
 * [fr.alaedine.aesh.data.local.entity.ScheduleSlotEntity]) are stored as
 * their ISO-8601 string representation so values also sort correctly as
 * plain text. [DayOfWeek] is stored as its ISO `value` (1 = Monday ... 7 =
 * Sunday) so ordering by it lists a week Monday-first.
 */
class Converters {

    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun fromLocalTime(time: LocalTime?): String? = time?.toString()

    @TypeConverter
    fun toLocalTime(value: String?): LocalTime? = value?.let(LocalTime::parse)

    @TypeConverter
    fun fromDayOfWeek(dayOfWeek: DayOfWeek?): Int? = dayOfWeek?.value

    @TypeConverter
    fun toDayOfWeek(value: Int?): DayOfWeek? = value?.let(DayOfWeek::of)
}
