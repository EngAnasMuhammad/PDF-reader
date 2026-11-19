package com.anasdev.pdfreader

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfExporterCoordinateMappingTest {

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
            else -> {
                pdfX = llx + xNorm * w
                pdfY = lly + (1f - yNorm) * h
            }
        }

        return Pair(pdfX, pdfY)
    }

    @Test
    fun testUnrotatedCoordinateMapping() {
        val llx = 0f
        val lly = 0f
        val w = 595f
        val h = 842f

        // Top-Left (0, 0) in screen -> (0, 842) in PDF space
        val topLeft = mapNormalizedPointToPdf(0f, 0f, llx, lly, w, h, 0)
        assertEquals(0f, topLeft.first, 0.001f)
        assertEquals(842f, topLeft.second, 0.001f)

        // Bottom-Right (1, 1) in screen -> (595, 0) in PDF space
        val bottomRight = mapNormalizedPointToPdf(1f, 1f, llx, lly, w, h, 0)
        assertEquals(595f, bottomRight.first, 0.001f)
        assertEquals(0f, bottomRight.second, 0.001f)

        // Center (0.5, 0.5) -> (297.5, 421)
        val center = mapNormalizedPointToPdf(0.5f, 0.5f, llx, lly, w, h, 0)
        assertEquals(297.5f, center.first, 0.001f)
        assertEquals(421f, center.second, 0.001f)
    }

    @Test
    fun testRotated90CoordinateMapping() {
        val llx = 0f
        val lly = 0f
        val w = 595f
        val h = 842f

        val center = mapNormalizedPointToPdf(0.5f, 0.5f, llx, lly, w, h, 90)
        assertEquals(297.5f, center.first, 0.001f)
        assertEquals(421f, center.second, 0.001f)
    }

    @Test
    fun testRotated180CoordinateMapping() {
        val llx = 0f
        val lly = 0f
        val w = 595f
        val h = 842f

        val center = mapNormalizedPointToPdf(0.5f, 0.5f, llx, lly, w, h, 180)
        assertEquals(297.5f, center.first, 0.001f)
        assertEquals(421f, center.second, 0.001f)
    }

    @Test
    fun testRotated270CoordinateMapping() {
        val llx = 0f
        val lly = 0f
        val w = 595f
        val h = 842f

        val center = mapNormalizedPointToPdf(0.5f, 0.5f, llx, lly, w, h, 270)
        assertEquals(297.5f, center.first, 0.001f)
        assertEquals(421f, center.second, 0.001f)
    }

    @Test
    fun testQuadraticToCubicBezierConversion() {
        val p0x = 0f
        val p0y = 0f
        val qx = 50f
        val qy = 100f
        val p2x = 100f
        val p2y = 0f

        val c1x = p0x + (2f / 3f) * (qx - p0x)
        val c1y = p0y + (2f / 3f) * (qy - p0y)
        val c2x = p2x + (2f / 3f) * (qx - p2x)
        val c2y = p2y + (2f / 3f) * (qy - p2y)

        assertEquals(33.333f, c1x, 0.01f)
        assertEquals(66.666f, c1y, 0.01f)
        assertEquals(66.666f, c2x, 0.01f)
        assertEquals(66.666f, c2y, 0.01f)
    }

    @Test
    fun testTextBoxOriginMappingAcrossRotations() {
        val llx = 0f
        val lly = 0f
        val w = 600f
        val h = 800f

        // A text box at visual top-left: xNorm = 0.1, yNorm = 0.1, widthNorm = 0.3, hNorm = 0.05
        // Visual bottom-left corner of the box is at (xNorm, yNorm + hNorm) = (0.1, 0.15)
        val xNorm = 0.1f
        val yNorm = 0.1f
        val hNorm = 0.05f
        val visualBottomLeftY = yNorm + hNorm // 0.15f

        // Rotation 0: ox = llx + xNorm * w = 60, oy = lly + (1 - visualBottomLeftY) * h = 0.85 * 800 = 680
        val (ox0, oy0) = mapNormalizedPointToPdf(xNorm, visualBottomLeftY, llx, lly, w, h, 0)
        assertEquals(60f, ox0, 0.001f)
        assertEquals(680f, oy0, 0.001f)

        // Rotation 90: ox = llx + visualBottomLeftY * w = 0.15 * 600 = 90, oy = lly + xNorm * h = 0.1 * 800 = 80
        val (ox90, oy90) = mapNormalizedPointToPdf(xNorm, visualBottomLeftY, llx, lly, w, h, 90)
        assertEquals(90f, ox90, 0.001f)
        assertEquals(80f, oy90, 0.001f)

        // Rotation 180: ox = llx + (1 - xNorm) * w = 0.9 * 600 = 540, oy = lly + visualBottomLeftY * h = 0.15 * 800 = 120
        val (ox180, oy180) = mapNormalizedPointToPdf(xNorm, visualBottomLeftY, llx, lly, w, h, 180)
        assertEquals(540f, ox180, 0.001f)
        assertEquals(120f, oy180, 0.001f)

        // Rotation 270: ox = llx + (1 - visualBottomLeftY) * w = 0.85 * 600 = 510, oy = lly + (1 - xNorm) * h = 0.9 * 800 = 720
        val (ox270, oy270) = mapNormalizedPointToPdf(xNorm, visualBottomLeftY, llx, lly, w, h, 270)
        assertEquals(510f, ox270, 0.001f)
        assertEquals(720f, oy270, 0.001f)
    }

    private fun mapRectToPdfBoundingBox(
        leftNorm: Float,
        topNorm: Float,
        rightNorm: Float,
        bottomNorm: Float,
        llx: Float,
        lly: Float,
        w: Float,
        h: Float,
        rotation: Int
    ): Pair<Pair<Float, Float>, Pair<Float, Float>> {
        val p1 = mapNormalizedPointToPdf(leftNorm, topNorm, llx, lly, w, h, rotation)
        val p2 = mapNormalizedPointToPdf(rightNorm, bottomNorm, llx, lly, w, h, rotation)
        val minX = kotlin.math.min(p1.first, p2.first)
        val minY = kotlin.math.min(p1.second, p2.second)
        val rectW = kotlin.math.abs(p2.first - p1.first)
        val rectH = kotlin.math.abs(p2.second - p1.second)
        return Pair(Pair(minX, minY), Pair(rectW, rectH))
    }

    @Test
    fun testTextHighlightRectMappingAcrossRotations() {
        val llx = 0f
        val lly = 0f
        val w = 500f
        val h = 1000f

        // A highlight rectangle: left=0.1, top=0.2, right=0.9, bottom=0.3
        val l = 0.1f
        val t = 0.2f
        val r = 0.9f
        val b = 0.3f

        // Rotation 0:
        // p1 = (0.1*500, (1-0.2)*1000) = (50, 800)
        // p2 = (0.9*500, (1-0.3)*1000) = (450, 700)
        // minX = 50, minY = 700, rectW = 400, rectH = 100
        val (origin0, size0) = mapRectToPdfBoundingBox(l, t, r, b, llx, lly, w, h, 0)
        assertEquals(50f, origin0.first, 0.001f)
        assertEquals(700f, origin0.second, 0.001f)
        assertEquals(400f, size0.first, 0.001f)
        assertEquals(100f, size0.second, 0.001f)

        // Rotation 90:
        // p1 = (0.2*500, 0.1*1000) = (100, 100)
        // p2 = (0.3*500, 0.9*1000) = (150, 900)
        // minX = 100, minY = 100, rectW = 50, rectH = 800
        val (origin90, size90) = mapRectToPdfBoundingBox(l, t, r, b, llx, lly, w, h, 90)
        assertEquals(100f, origin90.first, 0.001f)
        assertEquals(100f, origin90.second, 0.001f)
        assertEquals(50f, size90.first, 0.001f)
        assertEquals(800f, size90.second, 0.001f)

        // Rotation 180:
        // p1 = ((1-0.1)*500, 0.2*1000) = (450, 200)
        // p2 = ((1-0.9)*500, 0.3*1000) = (50, 300)
        // minX = 50, minY = 200, rectW = 400, rectH = 100
        val (origin180, size180) = mapRectToPdfBoundingBox(l, t, r, b, llx, lly, w, h, 180)
        assertEquals(50f, origin180.first, 0.001f)
        assertEquals(200f, origin180.second, 0.001f)
        assertEquals(400f, size180.first, 0.001f)
        assertEquals(100f, size180.second, 0.001f)

        // Rotation 270:
        // p1 = ((1-0.2)*500, (1-0.1)*1000) = (400, 900)
        // p2 = ((1-0.3)*500, (1-0.9)*1000) = (350, 100)
        // minX = 350, minY = 100, rectW = 50, rectH = 800
        val (origin270, size270) = mapRectToPdfBoundingBox(l, t, r, b, llx, lly, w, h, 270)
        assertEquals(350f, origin270.first, 0.001f)
        assertEquals(100f, origin270.second, 0.001f)
        assertEquals(50f, size270.first, 0.001f)
        assertEquals(800f, size270.second, 0.001f)
    }

    private fun computeVisualDimensions(w: Float, h: Float, rotation: Int): Pair<Float, Float> {
        val visualWidth = if (rotation == 90 || rotation == 270) h else w
        val visualHeight = if (rotation == 90 || rotation == 270) w else h
        return Pair(visualWidth, visualHeight)
    }

    @Test
    fun testSignatureStrokeCoordinateMappingAcrossRotations() {
        val llx = 0f
        val lly = 0f
        val w = 600f
        val h = 800f

        val xNorm = 0.2f
        val yNorm = 0.3f
        val widthNorm = 0.4f
        val aspectRatio = 2.0f // width / height

        // Test center of signature local coordinate (0.5, 0.5)
        val localPtX = 0.5f
        val localPtY = 0.5f

        // Rotation 0
        val (visualWidth0, visualHeight0) = computeVisualDimensions(w, h, 0)
        val sigWidthPts0 = widthNorm * visualWidth0
        val sigHeightPts0 = sigWidthPts0 / aspectRatio
        val hNorm0 = sigHeightPts0 / visualHeight0
        val ptXNorm0 = xNorm + localPtX * widthNorm
        val ptYNorm0 = yNorm + localPtY * hNorm0
        val (pdfX0, pdfY0) = mapNormalizedPointToPdf(ptXNorm0, ptYNorm0, llx, lly, w, h, 0)
        assertEquals(240f, pdfX0, 0.01f)
        assertEquals(500f, pdfY0, 0.01f)

        // Rotation 90
        val (visualWidth90, visualHeight90) = computeVisualDimensions(w, h, 90)
        val sigWidthPts90 = widthNorm * visualWidth90
        val sigHeightPts90 = sigWidthPts90 / aspectRatio
        val hNorm90 = sigHeightPts90 / visualHeight90
        val ptXNorm90 = xNorm + localPtX * widthNorm
        val ptYNorm90 = yNorm + localPtY * hNorm90
        val (pdfX90, pdfY90) = mapNormalizedPointToPdf(ptXNorm90, ptYNorm90, llx, lly, w, h, 90)
        assertEquals(260f, pdfX90, 0.01f)
        assertEquals(320f, pdfY90, 0.01f)

        // Rotation 180
        val (visualWidth180, visualHeight180) = computeVisualDimensions(w, h, 180)
        val sigWidthPts180 = widthNorm * visualWidth180
        val sigHeightPts180 = sigWidthPts180 / aspectRatio
        val hNorm180 = sigHeightPts180 / visualHeight180
        val ptXNorm180 = xNorm + localPtX * widthNorm
        val ptYNorm180 = yNorm + localPtY * hNorm180
        val (pdfX180, pdfY180) = mapNormalizedPointToPdf(ptXNorm180, ptYNorm180, llx, lly, w, h, 180)
        assertEquals(360f, pdfX180, 0.01f)
        assertEquals(300f, pdfY180, 0.01f)

        // Rotation 270
        val (visualWidth270, visualHeight270) = computeVisualDimensions(w, h, 270)
        val sigWidthPts270 = widthNorm * visualWidth270
        val sigHeightPts270 = sigWidthPts270 / aspectRatio
        val hNorm270 = sigHeightPts270 / visualHeight270
        val ptXNorm270 = xNorm + localPtX * widthNorm
        val ptYNorm270 = yNorm + localPtY * hNorm270
        val (pdfX270, pdfY270) = mapNormalizedPointToPdf(ptXNorm270, ptYNorm270, llx, lly, w, h, 270)
        assertEquals(340f, pdfX270, 0.01f)
        assertEquals(480f, pdfY270, 0.01f)
    }
}
