package com.example.pdfreader.domain.repository

import com.example.pdfreader.domain.model.AppLanguage
import com.example.pdfreader.domain.model.AppTheme

interface SettingsRepository {
    fun getKeepScreenAwake(): Boolean
    fun setKeepScreenAwake(enabled: Boolean)

    fun getHorizontalScroll(): Boolean
    fun setHorizontalScroll(enabled: Boolean)

    fun getAppTheme(): AppTheme
    fun setAppTheme(theme: AppTheme)

    fun getAppLanguage(): AppLanguage
    fun setAppLanguage(language: AppLanguage)
}