package com.anasdev.pdfreader.domain.repository

import com.anasdev.pdfreader.domain.model.PdfCollection
import kotlinx.coroutines.flow.Flow

interface CollectionRepository {
    fun getAllCollections(): Flow<List<PdfCollection>>
    suspend fun createCollection(name: String): Long
    suspend fun deleteCollection(collectionId: Long)
    suspend fun addFileToCollection(collectionId: Long, fileUri: String, title: String)
    suspend fun removeFileFromCollection(collectionId: Long, fileUri: String)
    suspend fun pruneInaccessibleCollectionFiles()
}
