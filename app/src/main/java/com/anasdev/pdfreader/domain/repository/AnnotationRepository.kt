package com.anasdev.pdfreader.domain.repository

import android.net.Uri
import com.anasdev.pdfreader.domain.model.PdfAnnotation
import kotlinx.coroutines.flow.Flow

interface AnnotationRepository {
    fun getAnnotationsForFile(uri: Uri): Flow<Map<Int, List<PdfAnnotation>>>
    suspend fun saveAnnotation(uri: Uri, annotation: PdfAnnotation)
    suspend fun deleteAnnotation(id: String)
    suspend fun clearAnnotations(uri: Uri)
}
