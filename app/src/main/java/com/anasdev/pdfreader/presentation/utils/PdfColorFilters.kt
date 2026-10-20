package com.anasdev.pdfreader.presentation.utils

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

object PdfColorFilters {
    
    val sepiaColorFilter = ColorFilter.colorMatrix(
        ColorMatrix(floatArrayOf(
            0.334f, 0.653f, 0.160f, 0f, 0f,
            0.296f, 0.583f, 0.142f, 0f, 0f,
            0.231f, 0.453f, 0.111f, 0f, 0f,
            0f,     0f,     0f,     1f, 0f
        ))
    )

    val dimmedColorFilter = ColorFilter.colorMatrix(
        ColorMatrix(floatArrayOf(
            0.4f, 0f,   0f,   0f, 0f,
            0f,   0.4f, 0f,   0f, 0f,
            0f,   0f,   0.4f, 0f, 0f,
            0f,   0f,   0f,   1f, 0f
        ))
    )
}