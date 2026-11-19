package com.anasdev.pdfreader.data.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.anasdev.pdfreader.domain.model.FreehandStroke
import com.anasdev.pdfreader.domain.model.PdfAnnotation
import com.anasdev.pdfreader.domain.model.Signature
import com.anasdev.pdfreader.domain.model.TextBox
import com.anasdev.pdfreader.domain.model.TextHighlight
import com.anasdev.pdfreader.domain.repository.ExportedPdf
import com.anasdev.pdfreader.domain.repository.PdfExporter
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.blend.BlendMode
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import androidx.core.graphics.createBitmap

@Singleton
class PdfExporterImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PdfExporter {

    private val isPdfBoxInitialized = AtomicBoolean(false)

    private fun ensurePdfBoxInitialized() {
        if (isPdfBoxInitialized.compareAndSet(false, true)) {
            PDFBoxResourceLoader.init(context.applicationContext)
        }
    }

    override suspend fun exportAnnotatedPdf(
        sourceUri: Uri,
        outputStream: OutputStream,
        annotationsByPage: Map<Int, List<PdfAnnotation>>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            ensurePdfBoxInitialized()

            val inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: throw IOException("Could not open source PDF from URI: $sourceUri")

            // 20MB in-memory buffer before spilling to temp file (prevents OOM on large PDFs)
            val memorySetting = MemoryUsageSetting.setupMixed(20 * 1024 * 1024L).setTempDir(context.cacheDir)

            val document = inputStream.use { stream ->
                PDDocument.load(stream, memorySetting)
            }

            document.use { doc ->
                for ((pageIndex, annotations) in annotationsByPage) {
                    if (pageIndex < 0 || pageIndex >= doc.numberOfPages) continue
                    val highlights = annotations.filterIsInstance<TextHighlight>()
                    val strokes = annotations.filterIsInstance<FreehandStroke>()
                    val textBoxes = annotations.filterIsInstance<TextBox>()
                    val signatures = annotations.filterIsInstance<Signature>()
                    if (highlights.isEmpty() && strokes.isEmpty() && textBoxes.isEmpty() && signatures.isEmpty()) continue

                    val page = doc.getPage(pageIndex)
                    val rotation = page.rotation
                    val box = page.cropBox ?: page.mediaBox
                    val llx = box.lowerLeftX
                    val lly = box.lowerLeftY
                    val w = box.width
                    val h = box.height

                    val visualWidth = if (rotation == 90 || rotation == 270) h else w
                    val visualHeight = if (rotation == 90 || rotation == 270) w else h

                    PDPageContentStream(
                        doc,
                        page,
                        PDPageContentStream.AppendMode.APPEND,
                        true, // compress
                        true  // resetContext to isolate graphics state
                    ).use { contentStream ->
                        for (highlight in highlights) {
                            if (highlight.rects.isEmpty()) continue
                            renderTextHighlightToContentStream(
                                contentStream = contentStream,
                                highlight = highlight,
                                llx = llx,
                                lly = lly,
                                w = w,
                                h = h,
                                rotation = rotation
                            )
                        }

                        for (stroke in strokes) {
                            if (stroke.points.isEmpty()) continue
                            renderStrokeToContentStream(
                                contentStream = contentStream,
                                stroke = stroke,
                                llx = llx,
                                lly = lly,
                                w = w,
                                h = h,
                                rotation = rotation,
                                visualWidth = visualWidth
                            )
                        }

                        for (signature in signatures) {
                            if (signature.strokes.isEmpty()) continue
                            renderSignatureToContentStream(
                                contentStream = contentStream,
                                signature = signature,
                                llx = llx,
                                lly = lly,
                                w = w,
                                h = h,
                                rotation = rotation,
                                visualWidth = visualWidth,
                                visualHeight = visualHeight
                            )
                        }

                        for (textBox in textBoxes) {
                            if (textBox.text.isBlank()) continue
                            renderTextBoxToContentStream(
                                doc = doc,
                                contentStream = contentStream,
                                textBox = textBox,
                                llx = llx,
                                lly = lly,
                                w = w,
                                h = h,
                                rotation = rotation,
                                visualWidth = visualWidth,
                                visualHeight = visualHeight
                            )
                        }
                    }
                }

                doc.save(outputStream)
            }
        }
    }

    private fun renderTextBoxToContentStream(
        doc: PDDocument,
        contentStream: PDPageContentStream,
        textBox: TextBox,
        llx: Float,
        lly: Float,
        w: Float,
        h: Float,
        rotation: Int,
        visualWidth: Float,
        visualHeight: Float
    ) {
        val wVis = (textBox.widthNorm * visualWidth).coerceAtLeast(10f)
        val fontSizePts = (textBox.fontSizeRatio * visualWidth).coerceAtLeast(4f)

        // Target render supersampling scale
        var scale = 4f
        val baseW = (wVis * scale).roundToInt().coerceAtLeast(16)
        val baseTextSize = fontSizePts * scale

        val textPaint = TextPaint().apply {
            isAntiAlias = true
            isSubpixelText = true
            color = textBox.colorArgb
            textSize = baseTextSize
        }

        val layout = StaticLayout.Builder.obtain(textBox.text, 0, textBox.text.length, textPaint, baseW)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()

        var bmpW = layout.width.coerceAtLeast(1)
        var bmpH = layout.height.coerceAtLeast(1)

        val maxDim = maxOf(bmpW, bmpH)
        val finalLayout: StaticLayout
        if (maxDim > 1600) {
            val downScale = 1600f / maxDim
            scale *= downScale
            val adjustedW = (wVis * scale).roundToInt().coerceAtLeast(16)
            textPaint.textSize = fontSizePts * scale
            finalLayout = StaticLayout.Builder.obtain(textBox.text, 0, textBox.text.length, textPaint, adjustedW)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .build()
            bmpW = finalLayout.width.coerceAtLeast(1)
            bmpH = finalLayout.height.coerceAtLeast(1)
        } else {
            finalLayout = layout
        }

        val bitmap = createBitmap(bmpW, bmpH)
        val canvas = Canvas(bitmap)
        finalLayout.draw(canvas)

        val hVis = wVis * (bmpH.toFloat() / bmpW.toFloat())
        val hNorm = hVis / visualHeight

        val pdImage = LosslessFactory.createFromImage(doc, bitmap)
        bitmap.recycle()

        // Map visual bottom-left corner (xNorm, yNorm + hNorm)
        val (ox, oy) = mapNormalizedPointToPdf(textBox.xNorm, textBox.yNorm + hNorm, llx, lly, w, h, rotation)

        contentStream.saveGraphicsState()
        contentStream.transform(Matrix.getRotateInstance(Math.toRadians(rotation.toDouble()), ox, oy))
        contentStream.drawImage(pdImage, 0f, 0f, wVis, hVis)
        contentStream.restoreGraphicsState()
    }

    private fun renderSignatureToContentStream(
        contentStream: PDPageContentStream,
        signature: Signature,
        llx: Float,
        lly: Float,
        w: Float,
        h: Float,
        rotation: Int,
        visualWidth: Float,
        visualHeight: Float
    ) {
        val r = ((signature.colorArgb shr 16) and 0xFF) / 255f
        val g = ((signature.colorArgb shr 8) and 0xFF) / 255f
        val b = (signature.colorArgb and 0xFF) / 255f
        val a = ((signature.colorArgb ushr 24) and 0xFF) / 255f

        val lineWidth = (signature.strokeWidthRatio * visualWidth).coerceAtLeast(0.5f)

        val sigWidthPts = signature.widthNorm * visualWidth
        val sigHeightPts = if (signature.aspectRatio > 0f) sigWidthPts / signature.aspectRatio else sigWidthPts
        val hNorm = sigHeightPts / visualHeight

        contentStream.saveGraphicsState()
        contentStream.setStrokingColor(r, g, b)
        contentStream.setLineWidth(lineWidth)
        contentStream.setLineCapStyle(1)  // Round cap
        contentStream.setLineJoinStyle(1) // Round join
        if (a < 1.0f) {
            val gs = PDExtendedGraphicsState().apply {
                strokingAlphaConstant = a
            }
            contentStream.setGraphicsStateParameters(gs)
        }

        for (stroke in signature.strokes) {
            if (stroke.isEmpty()) continue
            val mappedPoints = stroke.map { pt ->
                val ptXNorm = signature.xNorm + pt.x * signature.widthNorm
                val ptYNorm = signature.yNorm + pt.y * hNorm
                mapNormalizedPointToPdf(ptXNorm, ptYNorm, llx, lly, w, h, rotation)
            }

            when (mappedPoints.size) {
                1 -> {
                    val (x, y) = mappedPoints[0]
                    contentStream.moveTo(x, y)
                    contentStream.lineTo(x + 0.1f, y + 0.1f)
                    contentStream.stroke()
                }
                2 -> {
                    val (x0, y0) = mappedPoints[0]
                    val (x1, y1) = mappedPoints[1]
                    contentStream.moveTo(x0, y0)
                    contentStream.lineTo(x1, y1)
                    contentStream.stroke()
                }
                else -> {
                    val first = mappedPoints[0]
                    var currentX = first.first
                    var currentY = first.second
                    contentStream.moveTo(currentX, currentY)

                    var pathCurrentX = currentX
                    var pathCurrentY = currentY

                    for (i in 1 until mappedPoints.size) {
                        val next = mappedPoints[i]
                        val nextX = next.first
                        val nextY = next.second
                        val midX = (currentX + nextX) / 2f
                        val midY = (currentY + nextY) / 2f

                        val c1x = pathCurrentX + (2f / 3f) * (currentX - pathCurrentX)
                        val c1y = pathCurrentY + (2f / 3f) * (currentY - pathCurrentY)
                        val c2x = midX + (2f / 3f) * (currentX - midX)
                        val c2y = midY + (2f / 3f) * (currentY - midY)

                        contentStream.curveTo(c1x, c1y, c2x, c2y, midX, midY)

                        pathCurrentX = midX
                        pathCurrentY = midY
                        currentX = nextX
                        currentY = nextY
                    }
                    contentStream.lineTo(currentX, currentY)
                    contentStream.stroke()
                }
            }
        }

        contentStream.restoreGraphicsState()
    }

    private fun renderStrokeToContentStream(
        contentStream: PDPageContentStream,
        stroke: FreehandStroke,
        llx: Float,
        lly: Float,
        w: Float,
        h: Float,
        rotation: Int,
        visualWidth: Float
    ) {
        val r = ((stroke.colorArgb shr 16) and 0xFF) / 255f
        val g = ((stroke.colorArgb shr 8) and 0xFF) / 255f
        val b = (stroke.colorArgb and 0xFF) / 255f
        val a = ((stroke.colorArgb ushr 24) and 0xFF) / 255f

        val lineWidth = (stroke.strokeWidthRatio * visualWidth).coerceAtLeast(0.5f)

        contentStream.saveGraphicsState()
        contentStream.setStrokingColor(r, g, b)
        contentStream.setLineWidth(lineWidth)
        contentStream.setLineCapStyle(1)  // Round cap
        contentStream.setLineJoinStyle(1) // Round join

        if (stroke.isHighlighter) {
            val gs = PDExtendedGraphicsState().apply {
                blendMode = BlendMode.MULTIPLY
                strokingAlphaConstant = 1.0f
            }
            contentStream.setGraphicsStateParameters(gs)
        } else if (a < 1.0f) {
            val gs = PDExtendedGraphicsState().apply {
                strokingAlphaConstant = a
            }
            contentStream.setGraphicsStateParameters(gs)
        }

        val mappedPoints = stroke.points.map { pt ->
            mapNormalizedPointToPdf(pt.x, pt.y, llx, lly, w, h, rotation)
        }

        when (mappedPoints.size) {
            1 -> {
                val (x, y) = mappedPoints[0]
                contentStream.moveTo(x, y)
                contentStream.lineTo(x + 0.1f, y + 0.1f)
                contentStream.stroke()
            }
            2 -> {
                val (x0, y0) = mappedPoints[0]
                val (x1, y1) = mappedPoints[1]
                contentStream.moveTo(x0, y0)
                contentStream.lineTo(x1, y1)
                contentStream.stroke()
            }
            else -> {
                val first = mappedPoints[0]
                var currentX = first.first
                var currentY = first.second
                contentStream.moveTo(currentX, currentY)

                var pathCurrentX = currentX
                var pathCurrentY = currentY

                for (i in 1 until mappedPoints.size) {
                    val next = mappedPoints[i]
                    val nextX = next.first
                    val nextY = next.second
                    val midX = (currentX + nextX) / 2f
                    val midY = (currentY + nextY) / 2f

                    // Convert quadratic Bézier to cubic Bézier:
                    // C1 = P0 + 2/3 * (Q - P0)
                    // C2 = P2 + 2/3 * (Q - P2)
                    val c1x = pathCurrentX + (2f / 3f) * (currentX - pathCurrentX)
                    val c1y = pathCurrentY + (2f / 3f) * (currentY - pathCurrentY)
                    val c2x = midX + (2f / 3f) * (currentX - midX)
                    val c2y = midY + (2f / 3f) * (currentY - midY)

                    contentStream.curveTo(c1x, c1y, c2x, c2y, midX, midY)

                    pathCurrentX = midX
                    pathCurrentY = midY
                    currentX = nextX
                    currentY = nextY
                }
                contentStream.lineTo(currentX, currentY)
                contentStream.stroke()
            }
        }

        contentStream.restoreGraphicsState()
    }

    private fun renderTextHighlightToContentStream(
        contentStream: PDPageContentStream,
        highlight: TextHighlight,
        llx: Float,
        lly: Float,
        w: Float,
        h: Float,
        rotation: Int
    ) {
        val r = ((highlight.colorArgb shr 16) and 0xFF) / 255f
        val g = ((highlight.colorArgb shr 8) and 0xFF) / 255f
        val b = (highlight.colorArgb and 0xFF) / 255f

        contentStream.saveGraphicsState()
        contentStream.setNonStrokingColor(r, g, b)

        val gs = PDExtendedGraphicsState().apply {
            blendMode = BlendMode.MULTIPLY
            nonStrokingAlphaConstant = 1.0f
        }
        contentStream.setGraphicsStateParameters(gs)

        for (rect in highlight.rects) {
            val p1 = mapNormalizedPointToPdf(rect.left, rect.top, llx, lly, w, h, rotation)
            val p2 = mapNormalizedPointToPdf(rect.right, rect.bottom, llx, lly, w, h, rotation)

            val minX = kotlin.math.min(p1.first, p2.first)
            val minY = kotlin.math.min(p1.second, p2.second)
            val rectW = kotlin.math.abs(p2.first - p1.first)
            val rectH = kotlin.math.abs(p2.second - p1.second)

            contentStream.addRect(minX, minY, rectW, rectH)
            contentStream.fill()
        }

        contentStream.restoreGraphicsState()
    }

    private fun mapNormalizedPointToPdf(
        xNorm: Float,
        yNorm: Float,
        llx: Float,
        lly: Float,
        w: Float,
        h: Float,
        rotation: Int
    ): Pair<Float, Float> {
        val pdfX: Float
        val pdfY: Float

        when (rotation) {
            90 -> {
                pdfX = llx + yNorm * w
                pdfY = lly + xNorm * h
            }
            180 -> {
                pdfX = llx + (1f - xNorm) * w
                pdfY = lly + yNorm * h
            }
            270 -> {
                pdfX = llx + (1f - yNorm) * w
                pdfY = lly + (1f - xNorm) * h
            }
            else -> { // 0 or any other unrotated
                pdfX = llx + xNorm * w
                pdfY = lly + (1f - yNorm) * h
            }
        }

        return Pair(pdfX, pdfY)
    }

    override suspend fun saveCopyToDownloads(
        sourceUri: Uri,
        originalTitle: String,
        annotationsByPage: Map<Int, List<PdfAnnotation>>
    ): Result<ExportedPdf> = withContext(Dispatchers.IO) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val baseName = originalTitle.removeSuffix(".pdf").ifBlank { "Document" }
        val displayName = "${baseName}_annotated_$timeStamp.pdf"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val itemUri = resolver.insert(collection, contentValues)
                ?: return@withContext Result.failure(IOException("Failed to create download record in MediaStore"))

            try {
                resolver.openOutputStream(itemUri)?.use { out ->
                    exportAnnotatedPdf(sourceUri, out, annotationsByPage).getOrThrow()
                } ?: throw IOException("Failed to open output stream for $itemUri")

                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)

                Result.success(
                    ExportedPdf(
                        uri = itemUri,
                        displayName = displayName,
                        relativePath = Environment.DIRECTORY_DOWNLOADS
                    )
                )
            } catch (t: Throwable) {
                resolver.delete(itemUri, null, null)
                Result.failure(t)
            }
        } else {
            // Android 9 and lower (API 26-28 fallback)
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val destinationFile = File(downloadsDir, displayName)

            try {
                FileOutputStream(destinationFile).use { out ->
                    exportAnnotatedPdf(sourceUri, out, annotationsByPage).getOrThrow()
                }
                Result.success(
                    ExportedPdf(
                        uri = Uri.fromFile(destinationFile),
                        displayName = displayName,
                        relativePath = destinationFile.absolutePath
                    )
                )
            } catch (t: Throwable) {
                if (destinationFile.exists()) {
                    destinationFile.delete()
                }
                Result.failure(t)
            }
        }
    }

    override suspend fun saveCopyToUri(
        sourceUri: Uri,
        destinationUri: Uri,
        annotationsByPage: Map<Int, List<PdfAnnotation>>
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val out = context.contentResolver.openOutputStream(destinationUri, "wt")
                ?: throw IOException("Could not open destination URI for writing: $destinationUri")

            out.use { stream ->
                exportAnnotatedPdf(sourceUri, stream, annotationsByPage).getOrThrow()
            }
            destinationUri
        }
    }
}
