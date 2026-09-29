package com.example.pdfreader.presentation.utils

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

object PdfColorFilters {
    val sepiaPaint = Paint().apply {
        val darkSepiaMatrix = ColorMatrix(floatArrayOf(
            0.334f, 0.653f, 0.160f, 0f, 0f,
            0.296f, 0.583f, 0.142f, 0f, 0f,
            0.231f, 0.453f, 0.111f, 0f, 0f,
            0f,     0f,     0f,     1f, 0f
        ))
        colorFilter = ColorMatrixColorFilter(darkSepiaMatrix)
    }

    val dimmedPaint = Paint().apply {
        val dimMatrix = ColorMatrix(floatArrayOf(
            0.4f, 0f,   0f,   0f, 0f,
            0f,   0.4f, 0f,   0f, 0f,
            0f,   0f,   0.4f, 0f, 0f,
            0f,   0f,   0f,   1f, 0f
        ))
        colorFilter = ColorMatrixColorFilter(dimMatrix)
    }
}