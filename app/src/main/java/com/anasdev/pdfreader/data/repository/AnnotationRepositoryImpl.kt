package com.anasdev.pdfreader.data.repository

import android.net.Uri
import com.anasdev.pdfreader.data.local.AnnotationDao
import com.anasdev.pdfreader.data.serializer.PdfAnnotationSerializer
import com.anasdev.pdfreader.domain.model.PdfAnnotation
import com.anasdev.pdfreader.domain.repository.AnnotationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnnotationRepositoryImpl @Inject constructor(
    private val dao: AnnotationDao,
    private val serializer: PdfAnnotationSerializer
) : AnnotationRepository {

    override fun getAnnotationsForFile(uri: Uri): Flow<Map<Int, List<PdfAnnotation>>> {
        return dao.getAnnotationsForFile(uri.toString()).map { entities ->
            entities.mapNotNull { serializer.deserialize(it) }
                .groupBy { it.pageIndex }
        }
    }

    override suspend fun saveAnnotation(uri: Uri, annotation: PdfAnnotation) {
        val entity = serializer.serialize(uri.toString(), annotation)
        dao.insertAnnotation(entity)
    }

    override suspend fun deleteAnnotation(id: String) {
        dao.deleteAnnotationById(id)
    }

    override suspend fun clearAnnotations(uri: Uri) {
        dao.clearAnnotationsForFile(uri.toString())
    }
}
