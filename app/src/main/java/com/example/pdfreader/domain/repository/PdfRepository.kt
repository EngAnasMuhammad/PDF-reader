package com.example.pdfreader.domain.repository

import android.net.Uri
import com.example.pdfreader.data.local.RecentFile
import kotlinx.coroutines.flow.Flow

interface PdfRepository {
    fun getRecentFiles(): Flow<List<RecentFile>>
    suspend fun getFileByUri(uriString: String): RecentFile?
    suspend fun insertRecentFile(file: RecentFile)
    suspend fun updatePage(uriString: String, page: Int)
    suspend fun deleteRecentFile(file: RecentFile)
    suspend fun clearAllHistory()

    fun takePersistableUriPermission(uri: Uri): Boolean
    fun extractFileName(uri: Uri): String
}