package com.anasdev.pdfreader.di

import com.anasdev.pdfreader.data.repository.PdfRepositoryImpl
import com.anasdev.pdfreader.data.repository.SettingsRepositoryImpl
import com.anasdev.pdfreader.domain.repository.PdfRepository
import com.anasdev.pdfreader.domain.repository.SettingsRepository
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