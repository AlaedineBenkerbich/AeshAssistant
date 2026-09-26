package fr.alaedine.aesh.data.di

import androidx.room.Room
import fr.alaedine.aesh.data.local.AeshDatabase
import fr.alaedine.aesh.data.repository.StudentRepositoryImpl
import fr.alaedine.aesh.domain.repository.StudentRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private const val DATABASE_NAME = "aesh.db"

/**
 * Provides the Room database and the repositories built on top of it.
 *
 * Registered in [fr.alaedine.aesh.di.appModules] so ViewModels can depend on
 * [StudentRepository] (the `domain`-facing interface) without knowing Room
 * is the underlying implementation.
 */
val dataModule = module {
    single {
        Room.databaseBuilder(androidContext(), AeshDatabase::class.java, DATABASE_NAME).build()
    }
    single { get<AeshDatabase>().studentDao() }
    single<StudentRepository> { StudentRepositoryImpl(get()) }
}
