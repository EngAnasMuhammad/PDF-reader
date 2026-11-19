package com.anasdev.pdfreader.domain

import com.anasdev.pdfreader.domain.tools.MergePdfsHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MergePdfsHelperTest {

    @Test
    fun `sanitizeFileName appends pdf extension when missing`() {
        val result = MergePdfsHelper.sanitizeFileName("Merged Reports")
        assertEquals("Merged Reports.pdf", result)
    }

    @Test
    fun `sanitizeFileName preserves existing pdf extension case-insensitively`() {
        val resultLower = MergePdfsHelper.sanitizeFileName("archive.pdf")
        assertEquals("archive.pdf", resultLower)

        val resultUpper = MergePdfsHelper.sanitizeFileName("BUNDLE.PDF")
        assertEquals("BUNDLE.pdf", resultUpper)
    }

    @Test
    fun `sanitizeFileName replaces illegal filesystem characters with underscores`() {
        val result = MergePdfsHelper.sanitizeFileName("Report:Q1/2026*Final?\"<merged>|doc")
        assertFalse(result.contains(":"))
        assertFalse(result.contains("/"))
        assertFalse(result.contains("*"))
        assertFalse(result.contains("?"))
        assertFalse(result.contains("\""))
        assertFalse(result.contains("<"))
        assertFalse(result.contains(">"))
        assertFalse(result.contains("|"))
        assertTrue(result.endsWith(".pdf"))
    }

    @Test
    fun `sanitizeFileName generates fallback timestamp name when input is blank`() {
        val emptyResult = MergePdfsHelper.sanitizeFileName("")
        assertTrue(emptyResult.startsWith("Merged_"))
        assertTrue(emptyResult.endsWith(".pdf"))

        val whitespaceResult = MergePdfsHelper.sanitizeFileName("   ")
        assertTrue(whitespaceResult.startsWith("Merged_"))
        assertTrue(whitespaceResult.endsWith(".pdf"))

        val justExtResult = MergePdfsHelper.sanitizeFileName(".pdf")
        assertTrue(justExtResult.startsWith("Merged_"))
        assertTrue(justExtResult.endsWith(".pdf"))
    }

    @Test
    fun `generateDefaultFileName starts with Merged_ prefix`() {
        val defaultName = MergePdfsHelper.generateDefaultFileName()
        assertTrue(defaultName.startsWith("Merged_"))
    }

    @Test
    fun `validatePdfCount accepts two or more files`() {
        assertTrue(MergePdfsHelper.validatePdfCount(2))
        assertTrue(MergePdfsHelper.validatePdfCount(3))
        assertTrue(MergePdfsHelper.validatePdfCount(10))
    }

    @Test
    fun `validatePdfCount rejects fewer than two files`() {
        assertFalse(MergePdfsHelper.validatePdfCount(0))
        assertFalse(MergePdfsHelper.validatePdfCount(1))
        assertFalse(MergePdfsHelper.validatePdfCount(-1))
    }
}
