package com.anasdev.pdfreader.domain.search

import com.anasdev.pdfreader.domain.search.TextSearchHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSearchHelperTest {

    @Test
    fun testEmptyOrBlankQueryReturnsNoMatches() {
        val text = "Sample text here"
        assertTrue(TextSearchHelper.findMatches(text, "").isEmpty())
        assertTrue(TextSearchHelper.findMatches(text, "   ").isEmpty())
        assertTrue(TextSearchHelper.findMatches("", "text").isEmpty())
    }

    @Test
    fun testEnglishCaseInsensitiveMatching() {
        val text = "The Quick brown fox jumps over the lazy dog. Quick results!"
        val matches = TextSearchHelper.findMatches(text, "quick")
        assertEquals(2, matches.size)

        // First "Quick" at index 4, length 5
        assertEquals(4, matches[0].originalStartIndex)
        assertEquals(5, matches[0].originalLength)
        assertEquals("Quick", text.substring(matches[0].originalStartIndex, matches[0].originalStartIndex + matches[0].originalLength))

        // Second "Quick" at index 45, length 5
        assertEquals(45, matches[1].originalStartIndex)
        assertEquals(5, matches[1].originalLength)
        assertEquals("Quick", text.substring(matches[1].originalStartIndex, matches[1].originalStartIndex + matches[1].originalLength))
    }

    @Test
    fun testArabicAlefNormalization() {
        // Source text with different Alef forms: أ, إ, آ, and plain ا
        val text = "أحمد ذهب إلى السوق واشترى آلة جديدة مع احمد"
        // Searching for plain "احمد"
        val matchesAhmed = TextSearchHelper.findMatches(text, "احمد")
        assertEquals(2, matchesAhmed.size)
        // Matches "أحمد" (starts at 0)
        assertEquals(0, matchesAhmed[0].originalStartIndex)
        assertEquals(4, matchesAhmed[0].originalLength)
        assertEquals("أحمد", text.substring(matchesAhmed[0].originalStartIndex, matchesAhmed[0].originalStartIndex + matchesAhmed[0].originalLength))

        // Matches "احمد" (starts at 39)
        assertEquals(39, matchesAhmed[1].originalStartIndex)
        assertEquals(4, matchesAhmed[1].originalLength)

        // Searching for "الى" matches "إلى"
        val matchesIla = TextSearchHelper.findMatches(text, "الى")
        assertEquals(1, matchesIla.size)
        assertEquals(9, matchesIla[0].originalStartIndex)
        assertEquals("إلى", text.substring(matchesIla[0].originalStartIndex, matchesIla[0].originalStartIndex + matchesIla[0].originalLength))

        // Searching for "الة" matches "آلة"
        val matchesAlah = TextSearchHelper.findMatches(text, "الة")
        assertEquals(1, matchesAlah.size)
        assertEquals("آلة", text.substring(matchesAlah[0].originalStartIndex, matchesAlah[0].originalStartIndex + matchesAlah[0].originalLength))
    }

    @Test
    fun testArabicTaaMarbutaAndAlifMaqsurahNormalization() {
        val text = "مكتبة ومستشفى"
        // Searching with 'ه' matches 'ة'
        val matchesMaktabah = TextSearchHelper.findMatches(text, "مكتبه")
        assertEquals(1, matchesMaktabah.size)
        assertEquals("مكتبة", text.substring(matchesMaktabah[0].originalStartIndex, matchesMaktabah[0].originalStartIndex + matchesMaktabah[0].originalLength))

        // Searching with 'ي' matches 'ى'
        val matchesMustashfa = TextSearchHelper.findMatches(text, "مستشفي")
        assertEquals(1, matchesMustashfa.size)
        assertEquals("مستشفى", text.substring(matchesMustashfa[0].originalStartIndex, matchesMustashfa[0].originalStartIndex + matchesMustashfa[0].originalLength))
    }

    @Test
    fun testArabicTashkeelAndTatweelStrippingWithAccurateIndexMapping() {
        // Text with Fatha, Damma, Kasra, Shadda, Sukun and Tatweel (_)
        val text = "قَـرَأَ الطّـالِبُ كِتَاباً"

        // Search for plain "طالب" (without shadda, tatweel, damma)
        val matchesTalib = TextSearchHelper.findMatches(text, "طالب")
        assertEquals(1, matchesTalib.size)
        val match = matchesTalib[0]

        // The matched substring in the original text contains the shadda, tatweel, and damma: "طّـالِبُ"
        val matchedSubstr = text.substring(match.originalStartIndex, match.originalStartIndex + match.originalLength)
        assertEquals("طّـالِبُ", matchedSubstr)

        // Search for "قرا" matches "قَـرَأَ"
        val matchesQara = TextSearchHelper.findMatches(text, "قرا")
        assertEquals(1, matchesQara.size)
        val matchedQaraSubstr = text.substring(matchesQara[0].originalStartIndex, matchesQara[0].originalStartIndex + matchesQara[0].originalLength)
        assertEquals("قَـرَأَ", matchedQaraSubstr)
    }

    @Test
    fun testConsecutiveNonOverlappingMatches() {
        val text = "aaaa"
        val matches = TextSearchHelper.findMatches(text, "aa")
        assertEquals(2, matches.size)
        assertEquals(0, matches[0].originalStartIndex)
        assertEquals(2, matches[0].originalLength)
        assertEquals(2, matches[1].originalStartIndex)
        assertEquals(2, matches[1].originalLength)
    }
}
