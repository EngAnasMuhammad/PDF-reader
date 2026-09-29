package com.example.pdfreader.di

import com.example.pdfreader.data.repository.PdfRepositoryImpl
import com.example.pdfreader.data.repository.SettingsRepositoryImpl
import com.example.pdfreader.domain.repository.PdfRepository
import com.example.pdfreader.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Module 2: For mapping interfaces to implementations
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        settingsRepositoryImpl: SettingsRepositoryImpl
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindPdfRepository(
        pdfRepositoryImpl: PdfRepositoryImpl
    ): PdfRepository
}