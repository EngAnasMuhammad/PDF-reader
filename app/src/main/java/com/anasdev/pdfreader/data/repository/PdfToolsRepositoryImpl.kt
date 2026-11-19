package com.anasdev.pdfreader.data.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import com.anasdev.pdfreader.R
import com.anasdev.pdfreader.domain.repository.PdfToolsRepository
import com.anasdev.pdfreader.domain.tools.ImagesToPdfHelper
import com.anasdev.pdfreader.domain.tools.MergePdfsHelper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

@Singleton
class PdfToolsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : PdfToolsRepository {

    private val isPdfBoxInitialized = AtomicBoolean(false)

    private fun ensurePdfBoxInitialized() {
        if (isPdfBoxInitialized.compareAndSet(false, true)) {
            PDFBoxResourceLoader.init(context.applicationContext)
        }
    }

    override suspend fun convertImagesToPdf(
        imageUris: List<Uri>,
        outputFileName: String,
        onProgress: (current: Int, total: Int) -> Unit,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        if (imageUris.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Image list cannot be empty"))
        }

        ensurePdfBoxInitialized()
        val sanitizedFileName = ImagesToPdfHelper.sanitizeFileName(outputFileName)
        val memSetting = MemoryUsageSetting.setupTempFileOnly().setTempDir(context.cacheDir)
        val document = PDDocument(memSetting)

        try {
            val totalImages = imageUris.size
            for ((index, uri) in imageUris.withIndex()) {
                val processedBitmap = loadAndProcessBitmap(uri)
                if (processedBitmap == null) {
                    Log.w("PdfTools", "Failed to decode image at index $index: $uri")
                    continue
                }

                try {
                    val (pageW, pageH) = ImagesToPdfHelper.calculatePageDimensions(
                        processedBitmap.width,
                        processedBitmap.height
                    )
                    val page = PDPage(PDRectangle(pageW, pageH))
                    document.addPage(page)

                    val pdImage = JPEGFactory.createFromImage(document, processedBitmap, 0.85f)
                    PDPageContentStream(document, page).use { stream ->
                        stream.drawImage(pdImage, 0f, 0f, pageW, pageH)
                    }
                } finally {
                    processedBitmap.recycle()
                }

                onProgress(index + 1, totalImages)
            }

            if (document.numberOfPages == 0) {
                return@withContext Result.failure(IOException("Could not decode any valid images from selection"))
            }

            saveDocumentToDownloads(document, sanitizedFileName)
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            try {
                document.close()
            } catch (_: Throwable) {}
        }
    }

    override suspend fun mergePdfs(
        pdfUris: List<Uri>,
        outputFileName: String,
        onProgress: (current: Int, total: Int) -> Unit,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        if (!MergePdfsHelper.validatePdfCount(pdfUris.size)) {
            return@withContext Result.failure(
                IllegalArgumentException("At least 2 PDF files are required for merging")
            )
        }

        ensurePdfBoxInitialized()
        val sanitizedFileName = MergePdfsHelper.sanitizeFileName(outputFileName)
        val tempOutputFile = File(context.cacheDir, "merge_tmp_${System.currentTimeMillis()}.pdf")

        val openStreams = mutableListOf<InputStream>()
        val openDocuments = mutableListOf<PDDocument>()
        var destinationDoc: PDDocument? = null

        try {
            val memSetting = MemoryUsageSetting.setupTempFileOnly().setTempDir(context.cacheDir)
            val partitionedMemSetting = memSetting.getPartitionedCopy(pdfUris.size + 1)
            destinationDoc = PDDocument(partitionedMemSetting)
            val merger = PDFMergerUtility()

            val totalCount = pdfUris.size
            for ((index, uri) in pdfUris.withIndex()) {
                val stream = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Cannot open input stream for URI: $uri")
                openStreams.add(stream)

                val sourceDoc = try {
                    PDDocument.load(stream, partitionedMemSetting)
                } catch (e: InvalidPasswordException) {
                    throw IOException(context.getString(R.string.encrypted_pdf_error), e)
                }

                if (sourceDoc.isEncrypted) {
                    throw IOException(context.getString(R.string.encrypted_pdf_error))
                }

                openDocuments.add(sourceDoc)
                merger.appendDocument(destinationDoc, sourceDoc)
                onProgress(index + 1, totalCount)
            }

            destinationDoc.save(tempOutputFile)

            saveFileToDownloads(tempOutputFile, sanitizedFileName)
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            try {
                destinationDoc?.close()
            } catch (_: Throwable) {}
            for (doc in openDocuments) {
                try {
                    doc.close()
                } catch (_: Throwable) {}
            }
            for (stream in openStreams) {
                try {
                    stream.close()
                } catch (_: Throwable) {}
            }
            if (tempOutputFile.exists()) {
                tempOutputFile.delete()
            }
        }
    }

    private fun loadAndProcessBitmap(uri: Uri): Bitmap? {
        // Method 1: ImageDecoder on Android 9+ (API 28+)
        // Decodes HEIC/HEIF, AVIF, WebP, JPEG, PNG natively, handles EXIF auto-rotation,
        // and scales safely during decode without multiple stream re-reads.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val size = info.size
                    val maxDimension = 1800
                    val longestEdge = max(size.width, size.height)
                    if (longestEdge > maxDimension) {
                        val scale = maxDimension.toFloat() / longestEdge
                        val targetW = (size.width * scale).roundToInt().coerceAtLeast(1)
                        val targetH = (size.height * scale).roundToInt().coerceAtLeast(1)
                        decoder.setTargetSize(targetW, targetH)
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }

                // If bitmap has alpha, composite onto white background for PDF rendering
                return if (decoded.hasAlpha()) {
                    val whiteBgBitmap =
                        createBitmap(decoded.width, decoded.height, Bitmap.Config.RGB_565)
                    val canvas = Canvas(whiteBgBitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    canvas.drawBitmap(decoded, 0f, 0f, null)
                    decoded.recycle()
                    whiteBgBitmap
                } else if (decoded.config == Bitmap.Config.HARDWARE) {
                    val softwareBitmap = decoded.copy(Bitmap.Config.ARGB_8888, false)
                    decoded.recycle()
                    softwareBitmap
                } else {
                    decoded
                }
            } catch (t: Throwable) {
                Log.w("PdfTools", "ImageDecoder failed for $uri, falling back to BitmapFactory", t)
            }
        }

        // Method 2: Robust BitmapFactory fallback using seekable FileDescriptor and BufferedInputStream
        return decodeWithBitmapFactory(uri)
    }

    private fun decodeWithBitmapFactory(uri: Uri): Bitmap? {
        val resolver = context.contentResolver

        // 1. Read EXIF Orientation using androidx.exifinterface
        val orientation = try {
            resolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val rotationDegrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

        // 2. Decode bounds using seekable FileDescriptor first (avoids stream unbuffered issues)
        var rawWidth = -1
        var rawHeight = -1
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        try {
            resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, boundsOptions)
                rawWidth = boundsOptions.outWidth
                rawHeight = boundsOptions.outHeight
            }
        } catch (_: Exception) {}

        if (rawWidth <= 0 || rawHeight <= 0) {
            try {
                resolver.openInputStream(uri)?.let { BufferedInputStream(it) }?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOptions)
                    rawWidth = boundsOptions.outWidth
                    rawHeight = boundsOptions.outHeight
                }
            } catch (_: Exception) {}
        }

        if (rawWidth <= 0 || rawHeight <= 0) {
            Log.e("PdfTools", "Could not determine image bounds for $uri")
            return null
        }

        // Downsample so longest edge is <= 1800 px (keeps peak RAM low)
        val maxDimension = 1800
        var sampleSize = 1
        val longestEdge = max(rawWidth, rawHeight)
        while ((longestEdge / (sampleSize * 2)) >= maxDimension) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decodedBitmap = try {
            resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, decodeOptions)
            } ?: resolver.openInputStream(uri)?.let { BufferedInputStream(it) }?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (e: Exception) {
            Log.e("PdfTools", "Failed to decode bitmap for $uri", e)
            null
        } ?: return null

        val opaqueBitmap = if (decodedBitmap.hasAlpha()) {
            val whiteBgBitmap =
                createBitmap(decodedBitmap.width, decodedBitmap.height, Bitmap.Config.RGB_565)
            val canvas = Canvas(whiteBgBitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            canvas.drawBitmap(decodedBitmap, 0f, 0f, null)
            decodedBitmap.recycle()
            whiteBgBitmap
        } else {
            decodedBitmap
        }

        return if (rotationDegrees != 0f) {
            val matrix = Matrix().apply { postRotate(rotationDegrees) }
            val rotated = Bitmap.createBitmap(
                opaqueBitmap,
                0,
                0,
                opaqueBitmap.width,
                opaqueBitmap.height,
                matrix,
                true
            )
            opaqueBitmap.recycle()
            rotated
        } else {
            opaqueBitmap
        }
    }

    private fun saveDocumentToDownloads(document: PDDocument, fileName: String): Result<Uri> {
        val relativeSubDir = "${Environment.DIRECTORY_DOWNLOADS}/PDFReader"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeSubDir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val itemUri = resolver.insert(collection, contentValues)
                ?: return Result.failure(IOException("Failed to create download record in MediaStore"))

            try {
                resolver.openOutputStream(itemUri)?.use { out ->
                    document.save(out)
                } ?: throw IOException("Failed to open output stream for $itemUri")

                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)

                Result.success(itemUri)
            } catch (t: Throwable) {
                resolver.delete(itemUri, null, null)
                Result.failure(t)
            }
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val pdfReaderDir = File(downloadsDir, "PDFReader")
            if (!pdfReaderDir.exists()) {
                pdfReaderDir.mkdirs()
            }
            val destinationFile = File(pdfReaderDir, fileName)

            try {
                FileOutputStream(destinationFile).use { out ->
                    document.save(out)
                }
                Result.success(Uri.fromFile(destinationFile))
            } catch (t: Throwable) {
                if (destinationFile.exists()) destinationFile.delete()
                Result.failure(t)
            }
        }
    }

    private fun saveFileToDownloads(file: File, fileName: String): Result<Uri> {
        val relativeSubDir = "${Environment.DIRECTORY_DOWNLOADS}/PDFReader"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeSubDir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val itemUri = resolver.insert(collection, contentValues)
                ?: return Result.failure(IOException("Failed to create download record in MediaStore"))

            try {
                resolver.openOutputStream(itemUri)?.use { out ->
                    file.inputStream().use { input -> input.copyTo(out) }
                } ?: throw IOException("Failed to open output stream for $itemUri")

                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)

                Result.success(itemUri)
            } catch (t: Throwable) {
                resolver.delete(itemUri, null, null)
                Result.failure(t)
            }
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val pdfReaderDir = File(downloadsDir, "PDFReader")
            if (!pdfReaderDir.exists()) {
                pdfReaderDir.mkdirs()
            }
            val destinationFile = File(pdfReaderDir, fileName)

            try {
                file.inputStream().use { input ->
                    FileOutputStream(destinationFile).use { out ->
                        input.copyTo(out)
                    }
                }
                Result.success(Uri.fromFile(destinationFile))
            } catch (t: Throwable) {
                if (destinationFile.exists()) destinationFile.delete()
                Result.failure(t)
            }
        }
    }
}
