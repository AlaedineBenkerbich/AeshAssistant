package fr.alaedine.aesh.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import fr.alaedine.aesh.data.local.dao.DailyReportDao
import fr.alaedine.aesh.data.local.dao.ScheduleSlotDao
import fr.alaedine.aesh.data.local.dao.StudentDao
import fr.alaedine.aesh.data.local.entity.DailyReportEntity
import fr.alaedine.aesh.data.local.entity.ScheduleSlotEntity
import fr.alaedine.aesh.data.local.entity.ScheduleSlotStudentCrossRef
import fr.alaedine.aesh.data.local.entity.StudentEntity

/**
 * The app's single local Room database. Additional entities/DAOs from
 * future milestones will be added here as their issues land.
 */
@Database(
    entities = [
        StudentEntity::class,
        DailyReportEntity::class,
        ScheduleSlotEntity::class,
        ScheduleSlotStudentCrossRef::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AeshDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao

    abstract fun dailyReportDao(): DailyReportDao

    abstract fun scheduleSlotDao(): ScheduleSlotDao
}
