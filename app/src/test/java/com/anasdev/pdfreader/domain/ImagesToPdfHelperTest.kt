package com.anasdev.pdfreader.domain

import com.anasdev.pdfreader.domain.tools.ImagesToPdfHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImagesToPdfHelperTest {

    @Test
    fun `sanitizeFileName appends pdf extension when missing`() {
        val result = ImagesToPdfHelper.sanitizeFileName("My Document")
        assertEquals("My Document.pdf", result)
    }

    @Test
    fun `sanitizeFileName preserves existing pdf extension case-insensitively`() {
        val resultLower = ImagesToPdfHelper.sanitizeFileName("contract.pdf")
        assertEquals("contract.pdf", resultLower)

        val resultUpper = ImagesToPdfHelper.sanitizeFileName("RECEIPT.PDF")
        assertEquals("RECEIPT.pdf", resultUpper)
    }

    @Test
    fun `sanitizeFileName replaces illegal filesystem characters with underscores`() {
        val result = ImagesToPdfHelper.sanitizeFileName("My:Photo/2026*Vacation?\"<draft>|test")
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
        val emptyResult = ImagesToPdfHelper.sanitizeFileName("")
        assertTrue(emptyResult.startsWith("Images_"))
        assertTrue(emptyResult.endsWith(".pdf"))

        val whitespaceResult = ImagesToPdfHelper.sanitizeFileName("   ")
        assertTrue(whitespaceResult.startsWith("Images_"))
        assertTrue(whitespaceResult.endsWith(".pdf"))

        val justExtResult = ImagesToPdfHelper.sanitizeFileName(".pdf")
        assertTrue(justExtResult.startsWith("Images_"))
        assertTrue(justExtResult.endsWith(".pdf"))
    }

    @Test
    fun `calculatePageDimensions preserves aspect ratio for portrait photos`() {
        val imgW = 1080
        val imgH = 1920
        val (pageW, pageH) = ImagesToPdfHelper.calculatePageDimensions(imgW, imgH)

        assertEquals(ImagesToPdfHelper.STANDARD_WIDTH_PT, pageW, 0.001f)

        val originalRatio = imgW.toFloat() / imgH.toFloat()
        val calculatedRatio = pageW / pageH
        assertEquals(originalRatio, calculatedRatio, 0.001f)
        assertEquals(ImagesToPdfHelper.STANDARD_WIDTH_PT / originalRatio, pageH, 0.001f)
    }

    @Test
    fun `calculatePageDimensions preserves aspect ratio for landscape photos`() {
        val imgW = 1920
        val imgH = 1080
        val (pageW, pageH) = ImagesToPdfHelper.calculatePageDimensions(imgW, imgH)

        assertEquals(ImagesToPdfHelper.STANDARD_WIDTH_PT, pageW, 0.001f)

        val originalRatio = imgW.toFloat() / imgH.toFloat()
        val calculatedRatio = pageW / pageH
        assertEquals(originalRatio, calculatedRatio, 0.001f)
        assertEquals(ImagesToPdfHelper.STANDARD_WIDTH_PT / originalRatio, pageH, 0.001f)
    }

    @Test
    fun `calculatePageDimensions produces square page for square images`() {
        val (pageW, pageH) = ImagesToPdfHelper.calculatePageDimensions(1000, 1000)
        assertEquals(ImagesToPdfHelper.STANDARD_WIDTH_PT, pageW, 0.001f)
        assertEquals(ImagesToPdfHelper.STANDARD_WIDTH_PT, pageH, 0.001f)
    }

    @Test
    fun `calculatePageDimensions scales height inversely proportional to aspect ratio`() {
        // Ultra-wide (3:1)
        val (wideW, wideH) = ImagesToPdfHelper.calculatePageDimensions(3000, 1000)
        assertEquals(ImagesToPdfHelper.STANDARD_WIDTH_PT, wideW, 0.001f)
        assertEquals(ImagesToPdfHelper.STANDARD_WIDTH_PT / 3.0f, wideH, 0.001f)
        assertEquals(3.0f, wideW / wideH, 0.001f)

        // Ultra-tall (1:3)
        val (tallW, tallH) = ImagesToPdfHelper.calculatePageDimensions(1000, 3000)
        assertEquals(ImagesToPdfHelper.STANDARD_WIDTH_PT, tallW, 0.001f)
        assertEquals(ImagesToPdfHelper.STANDARD_WIDTH_PT * 3.0f, tallH, 0.001f)
        assertEquals(1f / 3f, tallW / tallH, 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `calculatePageDimensions rejects zero or negative dimensions`() {
        ImagesToPdfHelper.calculatePageDimensions(0, 500)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `calculatePageDimensions rejects negative height`() {
        ImagesToPdfHelper.calculatePageDimensions(500, -100)
    }
}
