package com.example.pdfreader.presentation.state

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pdfreader.data.local.RecentFile
import com.example.pdfreader.domain.model.AppLanguage
import com.example.pdfreader.domain.model.AppTheme
import com.example.pdfreader.domain.repository.PdfRepository
import com.example.pdfreader.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PdfViewModel @Inject constructor(
    private val pdfRepository: PdfRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _pdfUri = MutableStateFlow<Uri?>(null)
    val pdfUri: StateFlow<Uri?> = _pdfUri.asStateFlow()

    private val _pdfTitle = MutableStateFlow("Document")
    val pdfTitle: StateFlow<String> = _pdfTitle.asStateFlow()

    private val _startPage = MutableStateFlow(0)
    val startPage: StateFlow<Int> = _startPage.asStateFlow()

    val recentFiles: StateFlow<List<RecentFile>> = pdfRepository.getRecentFiles()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // --- SETTINGS STATE ---
    private val _keepScreenAwake = MutableStateFlow(settingsRepository.getKeepScreenAwake())
    val keepScreenAwake: StateFlow<Boolean> = _keepScreenAwake.asStateFlow()

    private val _isHorizontalScroll = MutableStateFlow(settingsRepository.getHorizontalScroll())
    val isHorizontalScroll: StateFlow<Boolean> = _isHorizontalScroll.asStateFlow()

    private val _appTheme = MutableStateFlow(settingsRepository.getAppTheme())
    val appTheme: StateFlow<AppTheme> = _appTheme.asStateFlow()

    private val _appLanguage = MutableStateFlow(settingsRepository.getAppLanguage())
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    // --- SETTINGS ACTIONS ---
    fun toggleKeepScreenAwake(isEnabled: Boolean) {
        settingsRepository.setKeepScreenAwake(isEnabled)
        _keepScreenAwake.value = isEnabled
    }

    fun toggleHorizontalScroll(isEnabled: Boolean) {
        settingsRepository.setHorizontalScroll(isEnabled)
        _isHorizontalScroll.value = isEnabled
    }

    fun setAppTheme(theme: AppTheme) {
        settingsRepository.setAppTheme(theme)
        _appTheme.value = theme
    }

    fun setAppLanguage(language: AppLanguage) {
        settingsRepository.setAppLanguage(language)
        _appLanguage.value = language
    }

    fun clearRecentHistory() {
        viewModelScope.launch {
            pdfRepository.clearAllHistory()
        }
    }

    // --- PDF ACTIONS ---
    fun loadPdf(uri: Uri) {
        _pdfUri.value = null
        _startPage.value = 0

        viewModelScope.launch {
            val hasLongTermPermission = pdfRepository.takePersistableUriPermission(uri)
            val title = pdfRepository.extractFileName(uri)

            _pdfTitle.value = title

            val existingFile = pdfRepository.getFileByUri(uri.toString())

            if (existingFile != null) {
                _startPage.value = existingFile.lastPage
                pdfRepository.insertRecentFile(existingFile.copy(timestamp = System.currentTimeMillis()))
            } else {
                _startPage.value = 0
                if (hasLongTermPermission) {
                    pdfRepository.insertRecentFile(
                        RecentFile(
                            uri = uri.toString(),
                            title = title,
                            timestamp = System.currentTimeMillis(),
                            lastPage = 0
                        )
                    )
                }
            }
            _pdfUri.value = uri
        }
    }

    fun saveCurrentPage(page: Int) {
        val uri = _pdfUri.value?.toString() ?: return
        viewModelScope.launch {
            pdfRepository.updatePage(uri, page)
        }
    }

    fun deleteRecentFile(recentFile: RecentFile) {
        viewModelScope.launch {
            pdfRepository.deleteRecentFile(recentFile)
        }
    }
}