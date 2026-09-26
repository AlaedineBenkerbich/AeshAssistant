package fr.alaedine.aesh.data.local

import androidx.room.TypeConverter
import java.time.LocalDate

/**
 * Room [androidx.room.TypeConverters] for column types with no built-in
 * mapping. Only [LocalDate] is needed today (see
 * [fr.alaedine.aesh.data.local.entity.DailyReportEntity]); it's stored as
 * its ISO-8601 (`yyyy-MM-dd`) string representation so values also sort
 * correctly as plain text.
 */
class Converters {

    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
}
