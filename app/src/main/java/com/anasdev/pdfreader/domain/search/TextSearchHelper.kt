package com.anasdev.pdfreader.domain.search

/**
 * High-performance, memory-safe, pure Kotlin text search helper.
 *
 * Supports:
 * - Case-insensitive matching across Latin / Cyrillic / other alphabets.
 * - Full Arabic letter normalization:
 *   - Alef variants: 'أ', 'إ', 'آ', 'ٱ' -> 'ا'
 *   - Taa Marbuta: 'ة' -> 'ه'
 *   - Alif Maqsurah: 'ى' -> 'ي'
 *   - Persian/Urdu Yeh variants: 'ی', 'ئ' -> 'ي'
 * - Arabic Tashkeel / Harakat (diacritics: \u064B..\u065F, \u0670) and Tatweel/Kashida (\u0640) stripping.
 * - Accurate bidirectional index mapping from normalized string positions back to the original
 *   character indices in the source text, including any trailing diacritics attached to the matched glyphs,
 *   ensuring PDFium highlights the exact native glyphs.
 */
object TextSearchHelper {

    /**
     * Represents a match range in the original string coordinates.
     */
    data class MatchRange(
        val originalStartIndex: Int,
        val originalLength: Int
    )

    /**
     * Checks if an Arabic character is a diacritic (Tashkeel) or Tatweel (Kashida)
     * that should be ignored during search matching.
     */
    fun isIgnoredArabicChar(c: Char): Boolean {
        // Tashkeel range \u064B .. \u065F (Fathatan, Dammatan, Kasratan, Fatha, Damma, Kasra, Shadda, Sukun, etc.)
        // Quranic superscript Alef \u0670
        // Tatweel / Kashida \u0640
        return (c in '\u064B'..'\u065F') || c == '\u0670' || c == '\u0640'
    }

    /**
     * Normalizes a character:
     * - Case-folds to lowercase
     * - Normalizes Arabic letter variants to a canonical base letter
     */
    fun normalizeChar(c: Char): Char {
        return when (c) {
            'أ', 'إ', 'آ', 'ٱ' -> 'ا'
            'ة' -> 'ه'
            'ى', 'ی', 'ئ' -> 'ي'
            else -> c.lowercaseChar()
        }
    }

    /**
     * Internal representation of a normalized string with an index map
     * pointing back to the original string indices.
     */
    data class NormalizedText(
        val text: String,
        val originalIndices: IntArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as NormalizedText
            return text == other.text && originalIndices.contentEquals(other.originalIndices)
        }

        override fun hashCode(): Int {
            var result = text.hashCode()
            result = 31 * result + originalIndices.contentHashCode()
            return result
        }
    }

    /**
     * Normalizes text while preserving a 1:1 index map back to original character indices.
     * Ignored characters (tashkeel/tatweel) are skipped in the normalized text.
     */
    fun normalize(text: String): NormalizedText {
        if (text.isEmpty()) {
            return NormalizedText("", IntArray(0))
        }

        val sb = StringBuilder(text.length)
        val indexMap = IntArray(text.length)
        var normCount = 0

        for (i in text.indices) {
            val c = text[i]
            if (!isIgnoredArabicChar(c)) {
                sb.append(normalizeChar(c))
                indexMap[normCount] = i
                normCount++
            }
        }

        val finalIndices = if (normCount == text.length) {
            indexMap
        } else {
            indexMap.copyOf(normCount)
        }

        return NormalizedText(sb.toString(), finalIndices)
    }

    /**
     * Finds all non-overlapping match occurrences of [query] in [sourceText].
     *
     * @return List of [MatchRange] pointing to exact original character indices and lengths
     * in [sourceText], or empty list if no matches found or if query is blank.
     */
    fun findMatches(sourceText: String, query: String): List<MatchRange> {
        val trimmedQuery = query.trim()
        if (sourceText.isEmpty() || trimmedQuery.isEmpty()) {
            return emptyList()
        }

        val normalizedSource = normalize(sourceText)
        val normalizedQuery = normalize(trimmedQuery).text

        if (normalizedSource.text.isEmpty() || normalizedQuery.isEmpty()) {
            return emptyList()
        }

        val queryLen = normalizedQuery.length
        val results = mutableListOf<MatchRange>()
        var searchFromIndex = 0

        while (searchFromIndex <= normalizedSource.text.length - queryLen) {
            val foundNormIndex = normalizedSource.text.indexOf(normalizedQuery, searchFromIndex)
            if (foundNormIndex == -1) break

            val normEndIndex = foundNormIndex + queryLen - 1
            val origStartIndex = normalizedSource.originalIndices[foundNormIndex]
            var origEndIndex = normalizedSource.originalIndices[normEndIndex]

            // Include any trailing tashkeel/tatweel attached to the final matched character in sourceText
            while (origEndIndex + 1 < sourceText.length && isIgnoredArabicChar(sourceText[origEndIndex + 1])) {
                origEndIndex++
            }

            val origLength = (origEndIndex - origStartIndex) + 1
            results.add(MatchRange(origStartIndex, origLength))

            // Non-overlapping search: advance past this match in normalized string
            searchFromIndex = foundNormIndex + queryLen
        }

        return results
    }
}
