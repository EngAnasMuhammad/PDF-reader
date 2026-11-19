package com.anasdev.pdfreader.domain.tools

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ImagesToPdfHelper {
    const val STANDARD_WIDTH_PT = 595f   // Standard A4 width in pt
    const val MAX_LONGEST_EDGE_PT = 842f // Standard A4 height in pt

    /**
     * Sanitizes raw user input into a valid, safe PDF filename.
     * Replaces illegal file characters ([\\/:*?"<>|]) with underscores,
     * strips surrounding whitespace, ensures .pdf extension,
     * and falls back to a clean timestamped name if blank.
     */
    fun sanitizeFileName(rawName: String): String {
        val sanitized = rawName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val baseName = sanitized
            .replace(Regex("(?i)\\.pdf$"), "")
            .trim()

        val finalBase = baseName.ifBlank {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            "Images_$timeStamp"
        }

        return "$finalBase.pdf"
    }

    /**
     * Calculates PDF page dimensions (in points) tailored to the image aspect ratio.
     * Fixes the page width to [STANDARD_WIDTH_PT] and scales the page height proportionally,
     * ensuring uniform document width while preserving image proportions without distortion.
     */
    fun calculatePageDimensions(imageWidthPx: Int, imageHeightPx: Int): Pair<Float, Float> {
        require(imageWidthPx > 0 && imageHeightPx > 0) { "Image dimensions must be positive" }
        val aspectRatio = imageWidthPx.toFloat() / imageHeightPx.toFloat()
        val targetWidth = STANDARD_WIDTH_PT
        val targetHeight = targetWidth / aspectRatio
        return Pair(targetWidth, targetHeight)
    }
}
