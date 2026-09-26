package fr.alaedine.aesh.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import fr.alaedine.aesh.data.local.dao.StudentDao
import fr.alaedine.aesh.data.local.entity.StudentEntity

/**
 * The app's single local Room database. Additional entities/DAOs from
 * future milestones (`DailyReport`, `ScheduleSlot`, ...) will be added here
 * as their issues land.
 */
@Database(entities = [StudentEntity::class], version = 1, exportSchema = true)
abstract class AeshDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao
}
