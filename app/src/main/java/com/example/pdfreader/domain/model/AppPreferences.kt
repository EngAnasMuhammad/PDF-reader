package com.example.pdfreader.domain.model

import androidx.annotation.StringRes
import com.example.pdfreader.R

enum class AppTheme(@param:StringRes val displayName: Int) {
    SYSTEM(R.string.system_default),
    LIGHT(R.string.light_theme),
    DARK(R.string.dark_theme)
}

enum class AppLanguage(val tag: String, @param:StringRes val titleResId: Int) {
    SYSTEM("", R.string.lang_system),
    ENGLISH("en", R.string.lang_english),
    ARABIC("ar", R.string.lang_arabic)
}