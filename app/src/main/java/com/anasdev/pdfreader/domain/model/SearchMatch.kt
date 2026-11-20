package com.anasdev.pdfreader.domain.model

/**
 * Represents a single text match found in the PDF.
 *
 * @param id Unique identifier across all matches in the document (e.g. 0, 1, 2...)
 * @param pageIndex 0-based page index where the match was found
 * @param rects List of normalized bounding boxes (0f..1f relative to page dimensions)
 *              for each line/segment of this match
 */
data class SearchMatch(
    val id: Int,
    val pageIndex: Int,
    val rects: List<NormalizedRect>
) {
    /**
     * Center Y coordinate of the match on the page in 0f..1f normalized space.
     * Used for smooth centering / auto-scroll when jumping to this match.
     */
    val normalizedCenterY: Float
        get() {
            if (rects.isEmpty()) return 0.5f
            val minY = rects.minOf { it.top }
            val maxY = rects.maxOf { it.bottom }
            return (minY + maxY) / 2f
        }
}
