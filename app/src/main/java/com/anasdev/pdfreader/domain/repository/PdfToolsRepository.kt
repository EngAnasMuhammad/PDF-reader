package com.anasdev.pdfreader.domain.repository

import android.net.Uri

interface PdfToolsRepository {
    /**
     * Converts a list of image URIs to a single PDF document offline.
     * Saves the resulting PDF into Downloads/PDFReader directory.
     *
     * @param imageUris Ordered list of image URIs to include as PDF pages.
     * @param outputFileName Target filename (e.g. "MyPhotos.pdf").
     * @param onProgress Callback receiving (currentProcessedCount, totalCount).
     * @return Result containing the content Uri of the saved PDF in MediaStore/storage.
     */
    suspend fun convertImagesToPdf(
        imageUris: List<Uri>,
        outputFileName: String,
        onProgress: (current: Int, total: Int) -> Unit,
    ): Result<Uri>

    /**
     * Merges a list of PDF URIs into a single PDF document offline.
     * Saves the resulting merged PDF into Downloads/PDFReader directory.
     *
     * @param pdfUris Ordered list of PDF URIs to merge (minimum 2).
     * @param outputFileName Target filename (e.g. "Merged_Document.pdf").
     * @param onProgress Callback receiving (currentMergedCount, totalCount).
     * @return Result containing the content Uri of the saved PDF in MediaStore/storage.
     */
    suspend fun mergePdfs(
        pdfUris: List<Uri>,
        outputFileName: String,
        onProgress: (current: Int, total: Int) -> Unit,
    ): Result<Uri>
}
