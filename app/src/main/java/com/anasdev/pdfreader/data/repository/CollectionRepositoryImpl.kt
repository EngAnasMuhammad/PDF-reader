package com.anasdev.pdfreader.data.repository

import com.anasdev.pdfreader.data.local.CollectionDao
import com.anasdev.pdfreader.data.local.CollectionEntity
import com.anasdev.pdfreader.data.local.CollectionFileEntity
import com.anasdev.pdfreader.domain.model.CollectionFile
import com.anasdev.pdfreader.domain.model.PdfCollection
import com.anasdev.pdfreader.domain.repository.CollectionRepository
import com.anasdev.pdfreader.domain.repository.PdfRepository
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CollectionRepositoryImpl @Inject constructor(
    private val collectionDao: CollectionDao,
    private val pdfRepository: PdfRepository
) : CollectionRepository {

    override fun getAllCollections(): Flow<List<PdfCollection>> {
        return collectionDao.getAllCollectionsWithFiles().map { list ->
            list.map { withFiles ->
                PdfCollection(
                    id = withFiles.collection.id,
                    name = withFiles.collection.name,
                    createdAt = withFiles.collection.createdAt,
                    files = withFiles.files
                        .sortedByDescending { it.addedAt }
                        .map { fileEntity ->
                            CollectionFile(
                                uri = fileEntity.fileUri,
                                title = fileEntity.title,
                                addedAt = fileEntity.addedAt
                            )
                        }
                )
            }
        }
    }

    override suspend fun createCollection(name: String): Long = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return@withContext -1L
        collectionDao.insertCollection(
            CollectionEntity(
                name = trimmed,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun deleteCollection(collectionId: Long) = withContext(Dispatchers.IO) {
        collectionDao.deleteCollectionById(collectionId)
    }

    override suspend fun addFileToCollection(
        collectionId: Long,
        fileUri: String,
        title: String
    ) = withContext(Dispatchers.IO) {
        collectionDao.addFileToCollection(
            CollectionFileEntity(
                collectionId = collectionId,
                fileUri = fileUri,
                title = title,
                addedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun removeFileFromCollection(
        collectionId: Long,
        fileUri: String
    ) = withContext(Dispatchers.IO) {
        collectionDao.removeFileFromCollection(collectionId, fileUri)
    }

    override suspend fun pruneInaccessibleCollectionFiles() = withContext(Dispatchers.IO) {
        val allUris = collectionDao.getAllCollectionFileUris()
        for (uriString in allUris) {
            val uri = uriString.toUri()
            if (!pdfRepository.isUriAccessible(uri)) {
                collectionDao.deleteFileFromAllCollections(uriString)
            }
        }
    }
}
