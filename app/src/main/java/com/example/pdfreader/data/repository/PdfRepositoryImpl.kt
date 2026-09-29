package com.example.pdfreader.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.example.pdfreader.data.local.RecentFile
import com.example.pdfreader.data.local.RecentFileDao
import com.example.pdfreader.domain.repository.PdfRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class PdfRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: RecentFileDao
) : PdfRepository {

    override fun getRecentFiles(): Flow<List<RecentFile>> = dao.getRecentFiles()

    override suspend fun getFileByUri(uriString: String): RecentFile? = dao.getFileByUri(uriString)

    override suspend fun insertRecentFile(file: RecentFile) = dao.insertRecentFile(file)

    override suspend fun updatePage(uriString: String, page: Int) = dao.updatePage(uriString, page)

    override suspend fun deleteRecentFile(file: RecentFile) = dao.deleteRecentFile(file)

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
}