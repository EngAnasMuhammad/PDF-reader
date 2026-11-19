package com.anasdev.pdfreader

import com.anasdev.pdfreader.data.serializer.PdfAnnotationSerializer
import com.anasdev.pdfreader.domain.model.FreehandStroke
import com.anasdev.pdfreader.domain.model.NormalizedPoint
import com.anasdev.pdfreader.domain.model.NormalizedRect
import com.anasdev.pdfreader.domain.model.Signature
import com.anasdev.pdfreader.domain.model.TextBox
import com.anasdev.pdfreader.domain.model.TextHighlight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class PdfAnnotationSerializerTest {

    private val serializer = PdfAnnotationSerializer()
    private val testUri = "content://com.android.providers.downloads/document/123"

    @Test
    fun testNormalizedPointClamping() {
        val point = NormalizedPoint(-0.5f, 1.5f)
        assertEquals(0f, point.clampedX, 0.0001f)
        assertEquals(1f, point.clampedY, 0.0001f)

        val ofPoint = NormalizedPoint.of(-0.1f, 1.1f)
        assertEquals(0f, ofPoint.x, 0.0001f)
        assertEquals(1f, ofPoint.y, 0.0001f)
    }

    @Test
    fun testTextBoxSerializationRoundTrip() {
        val original = TextBox(
            id = UUID.randomUUID().toString(),
            pageIndex = 2,
            createdAt = 1716000000000L,
            xNorm = 0.15f,
            yNorm = 0.25f,
            widthNorm = 0.40f,
            text = "ملاحظة هامة / Important Note \uD83D\uDCDD",
            fontSizeRatio = 0.035f,
            colorArgb = -16777216
        )

        val entity = serializer.serialize(testUri, original)
        assertNotNull(entity)
        assertEquals(testUri, entity.fileUri)
        assertEquals(PdfAnnotationSerializer.TYPE_TEXT_BOX, entity.type)

        val deserialized = serializer.deserialize(entity)
        assertNotNull(deserialized)
        assertTrue(deserialized is TextBox)

        val restored = deserialized as TextBox
        assertEquals(original.id, restored.id)
        assertEquals(original.pageIndex, restored.pageIndex)
        assertEquals(original.createdAt, restored.createdAt)
        assertEquals(original.xNorm, restored.xNorm, 0.0001f)
        assertEquals(original.yNorm, restored.yNorm, 0.0001f)
        assertEquals(original.widthNorm, restored.widthNorm, 0.0001f)
        assertEquals(original.text, restored.text)
        assertEquals(original.fontSizeRatio, restored.fontSizeRatio, 0.0001f)
        assertEquals(original.colorArgb, restored.colorArgb)
    }

    @Test
    fun testFreehandStrokeSerializationRoundTrip() {
        val stroke = FreehandStroke(
            id = UUID.randomUUID().toString(),
            pageIndex = 0,
            createdAt = 1716000000000L,
            points = listOf(
                NormalizedPoint(0.1f, 0.2f),
                NormalizedPoint(0.3f, 0.4f),
                NormalizedPoint(0.5f, 0.6f)
            ),
            colorArgb = -65536,
            strokeWidthRatio = 0.008f,
            isHighlighter = false
        )

        val entity = serializer.serialize(testUri, stroke)
        assertNotNull(entity)
        assertEquals(testUri, entity.fileUri)
        assertEquals(PdfAnnotationSerializer.TYPE_FREEHAND_STROKE, entity.type)

        val deserialized = serializer.deserialize(entity)
        assertNotNull(deserialized)
        assertTrue(deserialized is FreehandStroke)

        val restored = deserialized as FreehandStroke
        assertEquals(stroke.id, restored.id)
        assertEquals(stroke.pageIndex, restored.pageIndex)
        assertEquals(stroke.points.size, restored.points.size)
        assertEquals(stroke.points[0].x, restored.points[0].x, 0.0001f)
        assertEquals(stroke.colorArgb, restored.colorArgb)
        assertEquals(stroke.strokeWidthRatio, restored.strokeWidthRatio, 0.0001f)
        assertEquals(stroke.isHighlighter, restored.isHighlighter)
    }

    @Test
    fun testTextHighlightSerializationRoundTrip() {
        val highlight = TextHighlight(
            id = UUID.randomUUID().toString(),
            pageIndex = 1,
            createdAt = 1716000000000L,
            rects = listOf(
                NormalizedRect(0.1f, 0.2f, 0.8f, 0.25f),
                NormalizedRect(0.1f, 0.26f, 0.6f, 0.31f)
            ),
            colorArgb = -3848 // 0xFFFFF176 (Pastel Yellow)
        )

        val entity = serializer.serialize(testUri, highlight)
        assertNotNull(entity)
        assertEquals(testUri, entity.fileUri)
        assertEquals(PdfAnnotationSerializer.TYPE_TEXT_HIGHLIGHT, entity.type)

        val deserialized = serializer.deserialize(entity)
        assertNotNull(deserialized)
        assertTrue(deserialized is TextHighlight)

        val restored = deserialized as TextHighlight
        assertEquals(highlight.id, restored.id)
        assertEquals(highlight.pageIndex, restored.pageIndex)
        assertEquals(highlight.createdAt, restored.createdAt)
        assertEquals(highlight.colorArgb, restored.colorArgb)
        assertEquals(2, restored.rects.size)
        assertEquals(0.1f, restored.rects[0].left, 0.0001f)
        assertEquals(0.2f, restored.rects[0].top, 0.0001f)
        assertEquals(0.8f, restored.rects[0].right, 0.0001f)
        assertEquals(0.25f, restored.rects[0].bottom, 0.0001f)
    }

    @Test
    fun testUnknownTypeReturnsNull() {
        val fakeEntity = com.anasdev.pdfreader.data.local.PdfAnnotationEntity(
            id = "123",
            fileUri = testUri,
            pageIndex = 0,
            type = "unknown_type",
            payloadJson = "{}",
            createdAt = 1000L
        )
        val result = serializer.deserialize(fakeEntity)
        assertNull(result)
    }

    @Test
    fun testInvalidJsonReturnsNull() {
        val corruptEntity = com.anasdev.pdfreader.data.local.PdfAnnotationEntity(
            id = "123",
            fileUri = testUri,
            pageIndex = 0,
            type = PdfAnnotationSerializer.TYPE_TEXT_BOX,
            payloadJson = "{not-valid-json",
            createdAt = 1000L
        )
        val result = serializer.deserialize(corruptEntity)
        assertNull(result)
    }

    @Test
    fun testSignatureSerializationRoundTrip() {
        val signature = Signature(
            id = UUID.randomUUID().toString(),
            pageIndex = 3,
            createdAt = 1716000000000L,
            xNorm = 0.20f,
            yNorm = 0.70f,
            widthNorm = 0.35f,
            aspectRatio = 2.5f,
            strokes = listOf(
                listOf(NormalizedPoint(0f, 0f), NormalizedPoint(0.5f, 0.5f), NormalizedPoint(1f, 1f)),
                listOf(NormalizedPoint(0.2f, 0.8f), NormalizedPoint(0.8f, 0.2f))
            ),
            strokeWidthRatio = 0.005f,
            colorArgb = -16777216
        )

        val entity = serializer.serialize(testUri, signature)
        assertNotNull(entity)
        assertEquals(testUri, entity.fileUri)
        assertEquals(PdfAnnotationSerializer.TYPE_SIGNATURE, entity.type)

        val deserialized = serializer.deserialize(entity)
        assertNotNull(deserialized)
        assertTrue(deserialized is Signature)

        val restored = deserialized as Signature
        assertEquals(signature.id, restored.id)
        assertEquals(signature.pageIndex, restored.pageIndex)
        assertEquals(signature.createdAt, restored.createdAt)
        assertEquals(signature.xNorm, restored.xNorm, 0.0001f)
        assertEquals(signature.yNorm, restored.yNorm, 0.0001f)
        assertEquals(signature.widthNorm, restored.widthNorm, 0.0001f)
        assertEquals(signature.aspectRatio, restored.aspectRatio, 0.0001f)
        assertEquals(signature.strokeWidthRatio, restored.strokeWidthRatio, 0.0001f)
        assertEquals(signature.colorArgb, restored.colorArgb)
        assertEquals(2, restored.strokes.size)
        assertEquals(3, restored.strokes[0].size)
        assertEquals(0.5f, restored.strokes[0][1].x, 0.0001f)
        assertEquals(0.5f, restored.strokes[0][1].y, 0.0001f)
    }
}
