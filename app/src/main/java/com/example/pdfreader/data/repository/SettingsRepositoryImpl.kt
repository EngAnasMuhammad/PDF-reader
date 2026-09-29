package com.example.pdfreader.data.repository

import android.content.Context
import androidx.core.content.edit
import com.example.pdfreader.domain.model.AppLanguage
import com.example.pdfreader.domain.model.AppTheme
import com.example.pdfreader.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : SettingsRepository {

    private val prefs = context.getSharedPreferences("pdf_settings", Context.MODE_PRIVATE)

    override fun getKeepScreenAwake(): Boolean = prefs.getBoolean("awake", false)

    override fun setKeepScreenAwake(enabled: Boolean) {
        prefs.edit { putBoolean("awake", enabled) }
    }

    override fun getHorizontalScroll(): Boolean = prefs.getBoolean("horizontal", false)

    override fun setHorizontalScroll(enabled: Boolean) {
        prefs.edit { putBoolean("horizontal", enabled) }
    }

    override fun getAppTheme(): AppTheme {
        val savedTheme = prefs.getString("app_theme", AppTheme.SYSTEM.name) ?: AppTheme.SYSTEM.name
        return AppTheme.valueOf(savedTheme)
    }

    override fun setAppTheme(theme: AppTheme) {
        prefs.edit { putString("app_theme", theme.name) }
    }

    override fun getAppLanguage(): AppLanguage {
        val savedLang = prefs.getString("app_lang", AppLanguage.ENGLISH.name) ?: AppLanguage.ENGLISH.name
        return AppLanguage.valueOf(savedLang)
    }

    override fun setAppLanguage(language: AppLanguage) {
        prefs.edit { putString("app_lang", language.name) }
    }
}