package com.anasdev.pdfreader.presentation.pdf

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.withTranslation
import com.anasdev.pdfreader.domain.model.FreehandStroke
import com.anasdev.pdfreader.domain.model.NormalizedPoint
import com.anasdev.pdfreader.domain.model.NormalizedRect
import com.anasdev.pdfreader.domain.model.PdfAnnotation
import com.anasdev.pdfreader.domain.model.Signature
import com.anasdev.pdfreader.domain.model.TextBox
import com.anasdev.pdfreader.domain.model.TextHighlight
import kotlin.math.roundToInt

class AnnotationRenderer {

    // Pre-allocated reusable Paint instances to prevent GC allocations during 60fps draw loops
    private val penPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val highlighterPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        }
    }

    private val textHighlightPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        }
    }

    private val textPaint = TextPaint().apply {
        isAntiAlias = true
        isSubpixelText = true
    }

    private val selectionStrokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = 0xFF1976D2.toInt() // Blue selection highlight
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }

    private val handleFillPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = 0xFF1976D2.toInt()
    }

    private val handleStrokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = 0xFFFFFFFF.toInt()
        strokeWidth = 3f
    }

    private val rectF = RectF()

    // Pre-allocated reusable Path instance
    private val reusablePath = Path()

    // LRU Cache for StaticLayout to avoid allocations during panning/zooming
    private data class LayoutCacheKey(
        val id: String,
        val text: String,
        val colorArgb: Int,
        val widthPx: Int,
        val textSizePx: Float
    )

    private val layoutCache = object : LinkedHashMap<LayoutCacheKey, StaticLayout>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LayoutCacheKey, StaticLayout>?): Boolean {
            return size > 64
        }
    }

    fun getOrCreateLayout(textBox: TextBox, pageWidth: Float): StaticLayout {
        val boxWidthPx = (textBox.widthNorm * pageWidth).roundToInt().coerceAtLeast(16)
        val textSizePx = (textBox.fontSizeRatio * pageWidth).coerceAtLeast(8f)
        val key = LayoutCacheKey(textBox.id, textBox.text, textBox.colorArgb, boxWidthPx, textSizePx)

        return layoutCache.getOrPut(key) {
            val paint = TextPaint(textPaint).apply {
                color = textBox.colorArgb
                textSize = textSizePx
            }
            StaticLayout.Builder.obtain(textBox.text, 0, textBox.text.length, paint, boxWidthPx)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .build()
        }
    }

    fun computeTextBoxBoundsNorm(textBox: TextBox, pageWidth: Float, pageHeight: Float): RectF {
        if (pageWidth <= 0f || pageHeight <= 0f) {
            return RectF(textBox.xNorm, textBox.yNorm, textBox.xNorm + textBox.widthNorm, textBox.yNorm + 0.05f)
        }
        val layout = getOrCreateLayout(textBox, pageWidth)
        val hNorm = layout.height.toFloat() / pageHeight
        return RectF(
            textBox.xNorm,
            textBox.yNorm,
            (textBox.xNorm + textBox.widthNorm).coerceAtMost(1f),
            (textBox.yNorm + hNorm).coerceAtMost(1f)
        )
    }

    fun computeSignatureBoundsNorm(signature: Signature, pageWidth: Float, pageHeight: Float): RectF {
        if (pageWidth <= 0f || pageHeight <= 0f) {
            val approxHNorm = signature.widthNorm / signature.aspectRatio
            return RectF(signature.xNorm, signature.yNorm, signature.xNorm + signature.widthNorm, signature.yNorm + approxHNorm)
        }
        val sigWidthPx = signature.widthNorm * pageWidth
        val sigHeightPx = sigWidthPx / signature.aspectRatio
        val hNorm = sigHeightPx / pageHeight
        return RectF(
            signature.xNorm,
            signature.yNorm,
            (signature.xNorm + signature.widthNorm).coerceAtMost(1f),
            (signature.yNorm + hNorm).coerceAtMost(1f)
        )
    }

    fun render(
        canvas: Canvas,
        pageWidth: Float,
        pageHeight: Float,
        pageIndex: Int,
        persistedAnnotations: List<PdfAnnotation>?,
        activeStroke: ActiveStroke?,
        isEditMode: Boolean = false,
        selectedAnnotationId: String? = null
    ) {
        if (pageWidth <= 0f || pageHeight <= 0f) return

        // 1. Draw persisted annotations on this page
        persistedAnnotations?.forEach { annotation ->
            if (annotation.pageIndex == pageIndex) {
                when (annotation) {
                    is TextHighlight -> {
                        drawTextHighlight(
                            canvas = canvas,
                            pageWidth = pageWidth,
                            pageHeight = pageHeight,
                            highlight = annotation,
                            isSelected = annotation.id == selectedAnnotationId
                        )
                    }
                    is FreehandStroke -> {
                        drawStroke(
                            canvas = canvas,
                            pageWidth = pageWidth,
                            pageHeight = pageHeight,
                            points = annotation.points,
                            colorArgb = annotation.colorArgb,
                            strokeWidthRatio = annotation.strokeWidthRatio,
                            isHighlighter = annotation.isHighlighter
                        )
                    }
                    is TextBox -> {
                        drawTextBox(
                            canvas = canvas,
                            pageWidth = pageWidth,
                            pageHeight = pageHeight,
                            textBox = annotation,
                            isSelected = isEditMode && (annotation.id == selectedAnnotationId)
                        )
                    }
                    is Signature -> {
                        drawSignature(
                            canvas = canvas,
                            pageWidth = pageWidth,
                            pageHeight = pageHeight,
                            signature = annotation,
                            isSelected = isEditMode && (annotation.id == selectedAnnotationId)
                        )
                    }
                }
            }
        }

        // 2. Draw live in-progress stroke if it is currently being drawn on this page
        if (activeStroke != null && activeStroke.pageIndex == pageIndex && activeStroke.points.isNotEmpty()) {
            drawStroke(
                canvas = canvas,
                pageWidth = pageWidth,
                pageHeight = pageHeight,
                points = activeStroke.points,
                colorArgb = activeStroke.colorArgb,
                strokeWidthRatio = activeStroke.strokeWidthRatio,
                isHighlighter = activeStroke.isHighlighter
            )
        }
    }

    private fun drawTextBox(
        canvas: Canvas,
        pageWidth: Float,
        pageHeight: Float,
        textBox: TextBox,
        isSelected: Boolean
    ) {
        val layout = getOrCreateLayout(textBox, pageWidth)
        val boxLeft = textBox.xNorm * pageWidth
        val boxTop = textBox.yNorm * pageHeight
        val boxWidthPx = (textBox.widthNorm * pageWidth).roundToInt().coerceAtLeast(16)
        val boxHeightPx = layout.height.toFloat()

        // Draw the text
        canvas.withTranslation(boxLeft, boxTop) {
            layout.draw(canvas)
        }

        // Selection Chrome & Resize Handle (Edit Mode only)
        if (isSelected) {
            val pad = 8f
            val left = boxLeft - pad
            val top = boxTop - pad
            val right = boxLeft + boxWidthPx + pad
            val bottom = boxTop + boxHeightPx + pad

            canvas.drawRoundRect(left, top, right, bottom, 8f, 8f, selectionStrokePaint)

            // Resize handle at bottom-right corner
            val handleRadius = 14f
            canvas.drawCircle(right, bottom, handleRadius, handleFillPaint)
            canvas.drawCircle(right, bottom, handleRadius, handleStrokePaint)
        }
    }

    private fun drawSignature(
        canvas: Canvas,
        pageWidth: Float,
        pageHeight: Float,
        signature: Signature,
        isSelected: Boolean
    ) {
        val sigLeft = signature.xNorm * pageWidth
        val sigTop = signature.yNorm * pageHeight
        val sigWidthPx = signature.widthNorm * pageWidth
        val sigHeightPx = sigWidthPx / signature.aspectRatio

        penPaint.apply {
            color = signature.colorArgb
            strokeWidth = (signature.strokeWidthRatio * pageWidth).coerceAtLeast(1f)
        }

        // Draw each vector stroke in the signature
        for (stroke in signature.strokes) {
            if (stroke.isEmpty()) continue

            if (stroke.size == 1) {
                val p0 = stroke[0]
                val x = sigLeft + p0.x * sigWidthPx
                val y = sigTop + p0.y * sigHeightPx
                canvas.drawPoint(x, y, penPaint)
                continue
            }

            reusablePath.rewind()
            val first = stroke[0]
            var currentX = sigLeft + first.x * sigWidthPx
            var currentY = sigTop + first.y * sigHeightPx
            reusablePath.moveTo(currentX, currentY)

            if (stroke.size == 2) {
                val second = stroke[1]
                reusablePath.lineTo(sigLeft + second.x * sigWidthPx, sigTop + second.y * sigHeightPx)
            } else {
                for (i in 1 until stroke.size) {
                    val next = stroke[i]
                    val nextX = sigLeft + next.x * sigWidthPx
                    val nextY = sigTop + next.y * sigHeightPx
                    val midX = (currentX + nextX) / 2f
                    val midY = (currentY + nextY) / 2f
                    reusablePath.quadTo(currentX, currentY, midX, midY)
                    currentX = nextX
                    currentY = nextY
                }
                reusablePath.lineTo(currentX, currentY)
            }

            canvas.drawPath(reusablePath, penPaint)
        }

        // Selection Chrome & Resize Handle (Edit Mode only)
        if (isSelected) {
            val pad = 8f
            val left = sigLeft - pad
            val top = sigTop - pad
            val right = sigLeft + sigWidthPx + pad
            val bottom = sigTop + sigHeightPx + pad

            canvas.drawRoundRect(left, top, right, bottom, 8f, 8f, selectionStrokePaint)

            // Resize handle at bottom-right corner
            val handleRadius = 14f
            canvas.drawCircle(right, bottom, handleRadius, handleFillPaint)
            canvas.drawCircle(right, bottom, handleRadius, handleStrokePaint)
        }
    }

    private fun drawStroke(
        canvas: Canvas,
        pageWidth: Float,
        pageHeight: Float,
        points: List<NormalizedPoint>,
        colorArgb: Int,
        strokeWidthRatio: Float,
        isHighlighter: Boolean
    ) {
        if (points.isEmpty()) return

        val paint = if (isHighlighter) {
            highlighterPaint.apply {
                strokeWidth = (strokeWidthRatio * pageWidth).coerceAtLeast(1f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    color = colorArgb or -0x1000000 // Force alpha to 0xFF
                } else {
                    color = (colorArgb and 0x00FFFFFF) or (96 shl 24)
                }
            }
        } else {
            penPaint.apply {
                color = colorArgb
                strokeWidth = (strokeWidthRatio * pageWidth).coerceAtLeast(1f)
            }
        }

        reusablePath.rewind()

        if (points.size == 1) {
            val p0 = points[0]
            val x = p0.x * pageWidth
            val y = p0.y * pageHeight
            canvas.drawPoint(x, y, paint)
            return
        }

        val first = points[0]
        var currentX = first.x * pageWidth
        var currentY = first.y * pageHeight
        reusablePath.moveTo(currentX, currentY)

        if (points.size == 2) {
            val second = points[1]
            reusablePath.lineTo(second.x * pageWidth, second.y * pageHeight)
        } else {
            for (i in 1 until points.size) {
                val next = points[i]
                val nextX = next.x * pageWidth
                val nextY = next.y * pageHeight
                val midX = (currentX + nextX) / 2f
                val midY = (currentY + nextY) / 2f
                reusablePath.quadTo(currentX, currentY, midX, midY)
                currentX = nextX
                currentY = nextY
            }
            reusablePath.lineTo(currentX, currentY)
        }

        canvas.drawPath(reusablePath, paint)
    }

    private fun drawTextHighlight(
        canvas: Canvas,
        pageWidth: Float,
        pageHeight: Float,
        highlight: TextHighlight,
        isSelected: Boolean
    ) {
        if (highlight.rects.isEmpty()) return

        textHighlightPaint.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                color = highlight.colorArgb or -0x1000000
            } else {
                color = (highlight.colorArgb and 0x00FFFFFF) or (160 shl 24)
            }
        }

        val cornerRadius = 0.003f * pageWidth

        for (rect in highlight.rects) {
            val left = rect.left * pageWidth
            val top = rect.top * pageHeight
            val right = rect.right * pageWidth
            val bottom = rect.bottom * pageHeight
            rectF.set(left, top, right, bottom)
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, textHighlightPaint)

            if (isSelected) {
                val pad = 4f
                val selRect = RectF(left - pad, top - pad, right + pad, bottom + pad)
                canvas.drawRoundRect(selRect, cornerRadius + 2f, cornerRadius + 2f, selectionStrokePaint)
            }
        }
    }

    fun findTextHighlightAt(
        pageIndex: Int,
        normX: Float,
        normY: Float,
        annotations: List<PdfAnnotation>
    ): TextHighlight? {
        val pad = 0.01f
        return annotations.asReversed().filterIsInstance<TextHighlight>().firstOrNull { h ->
            h.pageIndex == pageIndex && h.rects.any { r ->
                normX >= r.left - pad &&
                normX <= r.right + pad &&
                normY >= r.top - pad &&
                normY <= r.bottom + pad
            }
        }
    }
}

data class ActiveStroke(
    val pageIndex: Int,
    val points: MutableList<NormalizedPoint>,
    val colorArgb: Int,
    val strokeWidthRatio: Float,
    val isHighlighter: Boolean
)
