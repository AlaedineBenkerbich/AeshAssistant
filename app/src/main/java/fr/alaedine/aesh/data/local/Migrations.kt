package fr.alaedine.aesh.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds the `daily_reports` table (see
 * [fr.alaedine.aesh.data.local.entity.DailyReportEntity]) introduced for the
 * Daily Tracking milestone (#5). The existing `students` table is left
 * untouched, so upgrading preserves any students already saved on-device.
 */
val MIGRATION_1_2: Migration =
    object : Migration(startVersion = 1, endVersion = 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `daily_reports` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `studentId` INTEGER NOT NULL,
                `date` TEXT NOT NULL,
                `moodLevel` INTEGER NOT NULL,
                `focusLevel` INTEGER NOT NULL,
                `socialInteractions` INTEGER NOT NULL,
                `freeNotes` TEXT NOT NULL,
                FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_reports_studentId_date` " +
                    "ON `daily_reports` (`studentId`, `date`)",
            )
        }
    }

/**
 * Adds the `schedule_slots` table (see
 * [fr.alaedine.aesh.data.local.entity.ScheduleSlotEntity]) introduced for
 * the Manual Schedule Management milestone (#8). Existing `students` and
 * `daily_reports` tables are left untouched, so upgrading preserves any data
 * already saved on-device.
 */
val MIGRATION_2_3: Migration =
    object : Migration(startVersion = 2, endVersion = 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `schedule_slots` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `dayOfWeek` INTEGER NOT NULL,
                `startTime` TEXT NOT NULL,
                `endTime` TEXT NOT NULL,
                `subject` TEXT NOT NULL,
                `room` TEXT NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

/**
 * Adds the `schedule_slot_student_cross_ref` junction table (see
 * [fr.alaedine.aesh.data.local.entity.ScheduleSlotStudentCrossRef]) so a
 * class slot can be assigned one or more students, introduced for the
 * Assign Students To Classes milestone. Existing `students`,
 * `daily_reports` and `schedule_slots` tables are left untouched, so
 * upgrading preserves any data already saved on-device — schedule slots
 * saved before this migration simply start out with no assigned students.
 */
val MIGRATION_3_4: Migration =
    object : Migration(startVersion = 3, endVersion = 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `schedule_slot_student_cross_ref` (
                `scheduleSlotId` INTEGER NOT NULL,
                `studentId` INTEGER NOT NULL,
                PRIMARY KEY(`scheduleSlotId`, `studentId`),
                FOREIGN KEY(`scheduleSlotId`) REFERENCES `schedule_slots`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_schedule_slot_student_cross_ref_studentId` " +
                    "ON `schedule_slot_student_cross_ref` (`studentId`)",
            )
        }
    }
