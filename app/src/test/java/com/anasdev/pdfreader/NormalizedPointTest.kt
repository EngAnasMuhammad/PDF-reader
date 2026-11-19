package com.anasdev.pdfreader

import com.anasdev.pdfreader.domain.model.NormalizedPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizedPointTest {

    @Test
    fun testNormalizedPointClamping() {
        val point = NormalizedPoint(-0.5f, 1.5f)
        assertEquals(0f, point.clampedX, 0.0001f)
        assertEquals(1f, point.clampedY, 0.0001f)

        val pointOf = NormalizedPoint.of(-1f, 2f)
        assertEquals(0f, pointOf.x, 0.0001f)
        assertEquals(1f, pointOf.y, 0.0001f)
    }
}
