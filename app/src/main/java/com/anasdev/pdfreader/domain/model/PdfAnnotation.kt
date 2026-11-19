package com.anasdev.pdfreader.domain.model

data class NormalizedPoint(
    val x: Float,
    val y: Float
) {
    val clampedX: Float get() = x.coerceIn(0f, 1f)
    val clampedY: Float get() = y.coerceIn(0f, 1f)

    companion object {
        fun of(x: Float, y: Float): NormalizedPoint =
            NormalizedPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
    }
}

data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val clampedLeft: Float get() = left.coerceIn(0f, 1f)
    val clampedTop: Float get() = top.coerceIn(0f, 1f)
    val clampedRight: Float get() = right.coerceIn(0f, 1f)
    val clampedBottom: Float get() = bottom.coerceIn(0f, 1f)

    companion object {
        fun of(left: Float, top: Float, right: Float, bottom: Float): NormalizedRect =
            NormalizedRect(
                left.coerceIn(0f, 1f),
                top.coerceIn(0f, 1f),
                right.coerceIn(0f, 1f),
                bottom.coerceIn(0f, 1f)
            )
    }
}

sealed interface PdfAnnotation {
    val id: String
    val pageIndex: Int
    val createdAt: Long
}

data class FreehandStroke(
    override val id: String,
    override val pageIndex: Int,
    override val createdAt: Long,
    val points: List<NormalizedPoint>,
    val colorArgb: Int,
    val strokeWidthRatio: Float,
    val isHighlighter: Boolean
) : PdfAnnotation

data class TextBox(
    override val id: String,
    override val pageIndex: Int,
    override val createdAt: Long,
    val xNorm: Float,          // Top-left X in normalized page space (0f..1f)
    val yNorm: Float,          // Top-left Y in normalized page space (0f..1f)
    val widthNorm: Float,      // Wrap width relative to page width (e.g., default 0.40f, clamped 0.12f..0.95f)
    val text: String,
    val fontSizeRatio: Float,  // Text size relative to page width (e.g., default 0.032f, range 0.015f..0.08f)
    val colorArgb: Int
) : PdfAnnotation

data class TextHighlight(
    override val id: String,
    override val pageIndex: Int,
    override val createdAt: Long,
    val rects: List<NormalizedRect>,
    val colorArgb: Int
) : PdfAnnotation

data class SavedSignature(
    val strokes: List<List<NormalizedPoint>>, // Normalized 0f..1f inside tight cropped bounding box
    val aspectRatio: Float,                   // width / height (clamped 0.4f..6.0f)
    val colorArgb: Int
)

data class Signature(
    override val id: String,
    override val pageIndex: Int,
    override val createdAt: Long,
    val xNorm: Float,
    val yNorm: Float,
    val widthNorm: Float,      // Default 0.35f, clamped 0.10f..0.90f
    val aspectRatio: Float,    // Locked width/height ratio in physical page proportions
    val strokes: List<List<NormalizedPoint>>,
    val strokeWidthRatio: Float = 0.004f, // Default 0.004f
    val colorArgb: Int
) : PdfAnnotation
