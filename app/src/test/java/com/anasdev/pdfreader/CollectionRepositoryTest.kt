package com.anasdev.pdfreader

import android.net.Uri
import com.anasdev.pdfreader.data.local.CollectionDao
import com.anasdev.pdfreader.data.local.CollectionEntity
import com.anasdev.pdfreader.data.local.CollectionFileEntity
import com.anasdev.pdfreader.data.local.CollectionWithFiles
import com.anasdev.pdfreader.data.local.RecentFile
import com.anasdev.pdfreader.data.repository.CollectionRepositoryImpl
import com.anasdev.pdfreader.domain.repository.PdfRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class CollectionRepositoryTest {

    private class FakeCollectionDao : CollectionDao {
        val collections = mutableListOf<CollectionEntity>()
        val files = mutableListOf<CollectionFileEntity>()
        private var nextId = 1L

        private val flow = MutableStateFlow<List<CollectionWithFiles>>(emptyList())

        private fun emit() {
            val list = collections.sortedByDescending { it.createdAt }.map { coll ->
                val matchingFiles = files.filter { it.collectionId == coll.id }
                CollectionWithFiles(collection = coll, files = matchingFiles)
            }
            flow.value = list
        }

        override fun getAllCollectionsWithFiles(): Flow<List<CollectionWithFiles>> {
            emit()
            return flow
        }

        override suspend fun insertCollection(entity: CollectionEntity): Long {
            val id = if (entity.id == 0L) nextId++ else entity.id
            val saved = entity.copy(id = id)
            collections.add(saved)
            emit()
            return id
        }

        override suspend fun deleteCollectionById(collectionId: Long) {
            collections.removeAll { it.id == collectionId }
            files.removeAll { it.collectionId == collectionId }
            emit()
        }

        override suspend fun addFileToCollection(entity: CollectionFileEntity) {
            files.removeAll { it.collectionId == entity.collectionId && it.fileUri == entity.fileUri }
            files.add(entity)
            emit()
        }

        override suspend fun removeFileFromCollection(collectionId: Long, fileUri: String) {
            files.removeAll { it.collectionId == collectionId && it.fileUri == fileUri }
            emit()
        }

        override suspend fun getAllCollectionFileUris(): List<String> {
            return files.map { it.fileUri }.distinct()
        }

        override suspend fun deleteFileFromAllCollections(fileUri: String) {
            files.removeAll { it.fileUri == fileUri }
            emit()
        }
    }

    private class FakePdfRepository : PdfRepository {
        override fun getRecentFiles(): Flow<List<RecentFile>> = MutableStateFlow(emptyList())
        override suspend fun getFileByUri(uriString: String): RecentFile? = null
        override suspend fun insertRecentFile(file: RecentFile) {}
        override suspend fun updatePage(uriString: String, page: Int) {}
        override suspend fun deleteRecentFile(file: RecentFile) {}
        override suspend fun deleteRecentFileByUri(uriString: String) {}
        override suspend fun clearAllHistory() {}
        override fun takePersistableUriPermission(uri: Uri): Boolean = true
        override fun extractFileName(uri: Uri): String = "document.pdf"
        override suspend fun isUriAccessible(uri: Uri): Boolean = true
        override suspend fun pruneInaccessibleRecentFiles() {}
    }

    @Test
    fun testCreateAndGetAllCollections() = runBlocking {
        val fakeDao = FakeCollectionDao()
        val fakePdfRepo = FakePdfRepository()
        val repository = CollectionRepositoryImpl(fakeDao, fakePdfRepo)

        // Blank name should return -1
        val blankId = repository.createCollection("   ")
        assertEquals(-1L, blankId)

        // Valid name
        val studyId = repository.createCollection("Study")
        assertTrue(studyId > 0)

        // Add files
        repository.addFileToCollection(studyId, "content://pdf/1", "Math.pdf")
        kotlinx.coroutines.delay(10.milliseconds)
        repository.addFileToCollection(studyId, "content://pdf/2", "Physics.pdf")

        val result = repository.getAllCollections().first()
        assertEquals(1, result.size)
        assertEquals("Study", result[0].name)
        assertEquals(2, result[0].files.size)
        assertEquals("content://pdf/2", result[0].files[0].uri) // Added later, sorted descending

        // Remove a file
        repository.removeFileFromCollection(studyId, "content://pdf/1")
        val updated = repository.getAllCollections().first()
        assertEquals(1, updated[0].files.size)
        assertEquals("Physics.pdf", updated[0].files[0].title)

        // Delete collection
        repository.deleteCollection(studyId)
        val emptyResult = repository.getAllCollections().first()
        assertTrue(emptyResult.isEmpty())
    }
}
