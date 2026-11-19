package com.anasdev.pdfreader.di

import com.anasdev.pdfreader.data.repository.AnnotationRepositoryImpl
import com.anasdev.pdfreader.data.repository.PdfExporterImpl
import com.anasdev.pdfreader.data.repository.PdfRepositoryImpl
import com.anasdev.pdfreader.data.repository.SettingsRepositoryImpl
import com.anasdev.pdfreader.domain.repository.AnnotationRepository
import com.anasdev.pdfreader.domain.repository.PdfExporter
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

    @Binds
    @Singleton
    abstract fun bindAnnotationRepository(
        annotationRepositoryImpl: AnnotationRepositoryImpl
    ): AnnotationRepository

    @Binds
    @Singleton
    abstract fun bindPdfExporter(
        pdfExporterImpl: PdfExporterImpl
    ): PdfExporter

    @Binds
    @Singleton
    abstract fun bindCollectionRepository(
        collectionRepositoryImpl: com.anasdev.pdfreader.data.repository.CollectionRepositoryImpl
    ): com.anasdev.pdfreader.domain.repository.CollectionRepository

    @Binds
    @Singleton
    abstract fun bindPdfToolsRepository(
        pdfToolsRepositoryImpl: com.anasdev.pdfreader.data.repository.PdfToolsRepositoryImpl
    ): com.anasdev.pdfreader.domain.repository.PdfToolsRepository
}
