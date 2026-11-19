package com.anasdev.pdfreader.domain.repository

import android.net.Uri
import com.anasdev.pdfreader.domain.model.PdfAnnotation
import java.io.OutputStream

data class ExportedPdf(
    val uri: Uri,
    val displayName: String,
    val relativePath: String? = null
)

interface PdfExporter {
    suspend fun exportAnnotatedPdf(
        sourceUri: Uri,
        outputStream: OutputStream,
        annotationsByPage: Map<Int, List<PdfAnnotation>>
    ): Result<Unit>

    suspend fun saveCopyToDownloads(
        sourceUri: Uri,
        originalTitle: String,
        annotationsByPage: Map<Int, List<PdfAnnotation>>
    ): Result<ExportedPdf>

    suspend fun saveCopyToUri(
        sourceUri: Uri,
        destinationUri: Uri,
        annotationsByPage: Map<Int, List<PdfAnnotation>>
    ): Result<Uri>
}
