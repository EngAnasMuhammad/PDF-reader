package com.example.pdfreader.di

import android.content.Context
import androidx.room.Room
import com.example.pdfreader.data.local.AppDatabase
import com.example.pdfreader.data.local.RecentFileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Module 1: For builders and 3rd party libraries
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context = context,
            klass = AppDatabase::class.java,
            name = "App database"
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    @Singleton
    fun provideRecentFileDao(database: AppDatabase): RecentFileDao {
        return database.getRecentFileDao()
    }
}

