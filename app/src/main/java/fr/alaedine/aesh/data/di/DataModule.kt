package fr.alaedine.aesh.data.di

import androidx.room.Room
import fr.alaedine.aesh.data.local.AeshDatabase
import fr.alaedine.aesh.data.local.MIGRATION_1_2
import fr.alaedine.aesh.data.local.MIGRATION_2_3
import fr.alaedine.aesh.data.repository.BackupRepositoryImpl
import fr.alaedine.aesh.data.repository.DailyReportRepositoryImpl
import fr.alaedine.aesh.data.repository.ScheduleSlotRepositoryImpl
import fr.alaedine.aesh.data.repository.StudentRepositoryImpl
import fr.alaedine.aesh.domain.repository.BackupRepository
import fr.alaedine.aesh.domain.repository.DailyReportRepository
import fr.alaedine.aesh.domain.repository.ScheduleSlotRepository
import fr.alaedine.aesh.domain.repository.StudentRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private const val DATABASE_NAME = "aesh.db"

/**
 * Provides the Room database and the repositories built on top of it.
 *
 * Registered in [fr.alaedine.aesh.di.appModules] so ViewModels can depend on
 * [StudentRepository]/[DailyReportRepository]/[ScheduleSlotRepository]/
 * [BackupRepository] (the `domain`-facing interfaces) without knowing Room
 * is the underlying implementation.
 */
val dataModule =
    module {
        single {
            Room
                .databaseBuilder(androidContext(), AeshDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        }
        single { get<AeshDatabase>().studentDao() }
        single { get<AeshDatabase>().dailyReportDao() }
        single { get<AeshDatabase>().scheduleSlotDao() }
        single<StudentRepository> { StudentRepositoryImpl(get()) }
        single<DailyReportRepository> { DailyReportRepositoryImpl(get()) }
        single<ScheduleSlotRepository> { ScheduleSlotRepositoryImpl(get()) }
        single<BackupRepository> { BackupRepositoryImpl(get(), get(), get(), get()) }
    }
