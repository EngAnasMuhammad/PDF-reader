package com.anasdev.pdfreader.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.anasdev.pdfreader.data.local.AnnotationDao
import com.anasdev.pdfreader.data.local.RecentFile
import com.anasdev.pdfreader.data.local.RecentFileDao
import com.anasdev.pdfreader.domain.repository.PdfRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import javax.inject.Inject

class PdfRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: RecentFileDao,
    private val annotationDao: AnnotationDao
) : PdfRepository {

    override fun getRecentFiles(): Flow<List<RecentFile>> = dao.getRecentFiles()

    override suspend fun getFileByUri(uriString: String): RecentFile? = dao.getFileByUri(uriString)

    override suspend fun insertRecentFile(file: RecentFile) = dao.insertRecentFile(file)

    override suspend fun updatePage(uriString: String, page: Int) = dao.updatePage(uriString, page)

    override suspend fun deleteRecentFile(file: RecentFile) = withContext(Dispatchers.IO) {
        dao.deleteRecentFile(file)
        annotationDao.clearAnnotationsForFile(file.uri)
    }

    override suspend fun deleteRecentFileByUri(uriString: String) = withContext(Dispatchers.IO) {
        dao.deleteRecentFileByUri(uriString)
        annotationDao.clearAnnotationsForFile(uriString)
    }

    override suspend fun clearAllHistory() = dao.clearAllHistory()

    override fun takePersistableUriPermission(uri: Uri): Boolean {
        return try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            true
        } catch (e: SecurityException) {
            e.printStackTrace()
            false
        }
    }

    override fun extractFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path?.substringAfterLast('/')
        }
        return result ?: "Document"
    }

    override suspend fun isUriAccessible(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (e: SecurityException) {
            false
        } catch (e: FileNotFoundException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun pruneInaccessibleRecentFiles() = withContext(Dispatchers.IO) {
        val files = dao.getRecentFilesList()
        for (file in files) {
            val uri = file.uri.toUri()
            if (!isUriAccessible(uri)) {
                dao.deleteRecentFile(file)
                annotationDao.clearAnnotationsForFile(file.uri)
            }
        }
    }
}