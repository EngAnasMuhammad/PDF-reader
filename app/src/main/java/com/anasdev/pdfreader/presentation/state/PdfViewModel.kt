package com.anasdev.pdfreader.presentation.state

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anasdev.pdfreader.data.local.RecentFile
import com.anasdev.pdfreader.domain.model.AnnotationTool
import com.anasdev.pdfreader.domain.model.AppLanguage
import com.anasdev.pdfreader.domain.model.AppTheme
import com.anasdev.pdfreader.domain.model.FreehandStroke
import com.anasdev.pdfreader.domain.model.NormalizedPoint
import com.anasdev.pdfreader.domain.model.NormalizedRect
import com.anasdev.pdfreader.domain.model.PdfAnnotation
import com.anasdev.pdfreader.data.local.SignatureStorage
import com.anasdev.pdfreader.domain.model.PdfCollection
import com.anasdev.pdfreader.domain.model.ReadingMode
import com.anasdev.pdfreader.domain.model.SavedSignature
import com.anasdev.pdfreader.domain.model.SearchMatch
import com.anasdev.pdfreader.domain.model.SearchUiState
import com.anasdev.pdfreader.domain.model.Signature
import com.anasdev.pdfreader.domain.model.TextBox
import com.anasdev.pdfreader.domain.model.TextHighlight
import com.anasdev.pdfreader.domain.repository.AnnotationRepository
import com.anasdev.pdfreader.domain.repository.CollectionRepository
import com.anasdev.pdfreader.domain.repository.PdfExporter
import com.anasdev.pdfreader.domain.repository.PdfRepository
import com.anasdev.pdfreader.domain.repository.PdfToolsRepository
import com.anasdev.pdfreader.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

sealed interface ExportUiEvent {
    data class Success(val uri: Uri, val displayName: String) : ExportUiEvent
    data class Failure(val errorMessage: String) : ExportUiEvent
}

sealed interface ToolResultEvent {
    data class Success(val uri: Uri, val displayName: String) : ToolResultEvent
    data class Failure(val errorMessage: String) : ToolResultEvent
}

@HiltViewModel
class PdfViewModel @Inject constructor(
    private val pdfRepository: PdfRepository,
    private val settingsRepository: SettingsRepository,
    private val annotationRepository: AnnotationRepository,
    private val pdfExporter: PdfExporter,
    private val collectionRepository: CollectionRepository,
    private val signatureStorage: SignatureStorage,
    private val pdfToolsRepository: PdfToolsRepository,
) : ViewModel() {

    private val _pdfUri = MutableStateFlow<Uri?>(null)
    val pdfUri: StateFlow<Uri?> = _pdfUri.asStateFlow()

    private val _pdfTitle = MutableStateFlow("Document")
    val pdfTitle: StateFlow<String> = _pdfTitle.asStateFlow()

    private val _startPage = MutableStateFlow(0)
    val startPage: StateFlow<Int> = _startPage.asStateFlow()

    private val _pdfLoadError = MutableStateFlow(false)
    val pdfLoadError: StateFlow<Boolean> = _pdfLoadError.asStateFlow()

    val recentFiles: StateFlow<List<RecentFile>> = pdfRepository.getRecentFiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val collections: StateFlow<List<PdfCollection>> = collectionRepository.getAllCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedCollectionId = MutableStateFlow<Long?>(null)
    val selectedCollectionId: StateFlow<Long?> = _selectedCollectionId.asStateFlow()

    // --- SETTINGS STATE ---
    private val _keepScreenAwake = MutableStateFlow(settingsRepository.getKeepScreenAwake())
    val keepScreenAwake: StateFlow<Boolean> = _keepScreenAwake.asStateFlow()

    private val _isHorizontalScroll = MutableStateFlow(settingsRepository.getHorizontalScroll())
    val isHorizontalScroll: StateFlow<Boolean> = _isHorizontalScroll.asStateFlow()

    private val _appTheme = MutableStateFlow(settingsRepository.getAppTheme())
    val appTheme: StateFlow<AppTheme> = _appTheme.asStateFlow()

    private val _appLanguage = MutableStateFlow(settingsRepository.getAppLanguage())
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    private val _readingMode = MutableStateFlow(settingsRepository.getReadingMode())
    val readingMode: StateFlow<ReadingMode> = _readingMode.asStateFlow()

    // --- ANNOTATION EDIT STATE ---
    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

    private val _activeTool = MutableStateFlow(AnnotationTool.NONE)
    val activeTool: StateFlow<AnnotationTool> = _activeTool.asStateFlow()

    private var lastPenColor = DEFAULT_PEN_COLOR
    private var lastHighlighterColor = DEFAULT_HIGHLIGHTER_COLOR
    private var lastTextColor = DEFAULT_TEXT_COLOR
    private var lastPenWidth = DEFAULT_PEN_WIDTH
    private var lastHighlighterWidth = DEFAULT_HIGHLIGHTER_WIDTH
    private var lastTextFontSize = DEFAULT_TEXT_FONT_SIZE

    private val _selectedColorArgb = MutableStateFlow(DEFAULT_PEN_COLOR)
    val selectedColorArgb: StateFlow<Int> = _selectedColorArgb.asStateFlow()

    private val _strokeWidthRatio = MutableStateFlow(DEFAULT_PEN_WIDTH)
    val strokeWidthRatio: StateFlow<Float> = _strokeWidthRatio.asStateFlow()

    private val _textFontSizeRatio = MutableStateFlow(DEFAULT_TEXT_FONT_SIZE)
    val textFontSizeRatio: StateFlow<Float> = _textFontSizeRatio.asStateFlow()

    private val _selectedAnnotationId = MutableStateFlow<String?>(null)
    val selectedAnnotationId: StateFlow<String?> = _selectedAnnotationId.asStateFlow()

    // Selected highlight popup state (tap-to-recolor/delete pill in Reading mode)
    private val _selectedHighlightState = MutableStateFlow<Pair<TextHighlight, Pair<Float, Float>>?>(null)
    val selectedHighlightState: StateFlow<Pair<TextHighlight, Pair<Float, Float>>?> = _selectedHighlightState.asStateFlow()

    fun setSelectedHighlight(highlight: TextHighlight, screenX: Float, screenY: Float) {
        _selectedHighlightState.value = highlight to (screenX to screenY)
    }

    fun clearSelectedHighlight() {
        _selectedHighlightState.value = null
    }

    // --- REUSABLE VECTOR SIGNATURE STATE ---
    private val _savedSignature = MutableStateFlow<SavedSignature?>(null)
    val savedSignature: StateFlow<SavedSignature?> = _savedSignature.asStateFlow()

    init {
        viewModelScope.launch {
            _savedSignature.value = signatureStorage.loadSavedSignature()
        }
    }

    // In-memory draft for session buffering (changes committed to Room only on Done)
    private val _draftAnnotations = MutableStateFlow<Map<Int, List<PdfAnnotation>>?>(null)
    private var initialSessionSnapshot: Map<Int, List<PdfAnnotation>> = emptyMap()

    private val _hasUnsavedEditChanges = MutableStateFlow(false)
    val hasUnsavedEditChanges: StateFlow<Boolean> = _hasUnsavedEditChanges.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val roomAnnotations: StateFlow<Map<Int, List<PdfAnnotation>>> = _pdfUri
        .flatMapLatest { uri ->
            if (uri != null) {
                annotationRepository.getAnnotationsForFile(uri)
            } else {
                flowOf(emptyMap())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val annotationsByPage: StateFlow<Map<Int, List<PdfAnnotation>>> = combine(
        roomAnnotations,
        _draftAnnotations,
        _isEditMode
    ) { roomData, draftData, isEdit ->
        if (isEdit && draftData != null) {
            draftData
        } else {
            roomData
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    // --- PDF EXPORT STATE ---
    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportEvent = MutableSharedFlow<ExportUiEvent>()
    val exportEvent: SharedFlow<ExportUiEvent> = _exportEvent.asSharedFlow()

    // --- PDF TOOLS STATE (Images to PDF, Merge, etc.) ---
    private val _isProcessingTool = MutableStateFlow(false)
    val isProcessingTool: StateFlow<Boolean> = _isProcessingTool.asStateFlow()

    private val _toolProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val toolProgress: StateFlow<Pair<Int, Int>?> = _toolProgress.asStateFlow()

    private val _toolResultEvent = MutableSharedFlow<ToolResultEvent>()
    val toolResultEvent: SharedFlow<ToolResultEvent> = _toolResultEvent.asSharedFlow()

    // --- PDF IN-DOCUMENT SEARCH STATE ---
    private val _searchUiState = MutableStateFlow(SearchUiState())
    val searchUiState: StateFlow<SearchUiState> = _searchUiState.asStateFlow()

    private var activeSearchJob: Job? = null
    private var searchDebounceJob: Job? = null

    // --- UNDO / REDO STATE ---
    private sealed interface UndoAction {
        val annotation: PdfAnnotation
        data class Add(override val annotation: PdfAnnotation) : UndoAction
        data class Delete(override val annotation: PdfAnnotation) : UndoAction
        data class Update(val previous: PdfAnnotation, val updated: PdfAnnotation) : UndoAction {
            override val annotation: PdfAnnotation get() = updated
        }
    }

    private val undoStack = ArrayDeque<UndoAction>()
    private val redoStack = ArrayDeque<UndoAction>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private fun updateUndoRedoStates() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    private fun checkUnsavedChanges() {
        val current = _draftAnnotations.value ?: emptyMap()
        _hasUnsavedEditChanges.value = (current != initialSessionSnapshot)
    }

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

    fun setReadingMode(mode: ReadingMode) {
        settingsRepository.setReadingMode(mode)
        _readingMode.value = mode
    }

    fun clearRecentHistory() {
        viewModelScope.launch {
            pdfRepository.clearAllHistory()
        }
    }

    // --- ANNOTATION ACTIONS ---
    fun enterEditMode() {
        closeSearch()
        val current = roomAnnotations.value
        initialSessionSnapshot = current.mapValues { it.value.toList() }
        _draftAnnotations.value = current.mapValues { it.value.toMutableList() }
        undoStack.clear()
        redoStack.clear()
        updateUndoRedoStates()
        _hasUnsavedEditChanges.value = false
        _isEditMode.value = true
        if (_activeTool.value == AnnotationTool.NONE) {
            setActiveTool(AnnotationTool.PEN)
        }
    }

    fun saveAndExitEditMode() {
        val uri = _pdfUri.value
        val finalDraft = _draftAnnotations.value
        if (uri != null && finalDraft != null) {
            viewModelScope.launch {
                val initialMap = initialSessionSnapshot.values.asSequence().flatten().associateBy { it.id }
                val draftMap = finalDraft.values.asSequence().flatten().associateBy { it.id }

                val added = draftMap.values.filter { it.id !in initialMap }
                val deleted = initialMap.values.filter { it.id !in draftMap }
                val modified = draftMap.values.filter { it.id in initialMap && draftMap[it.id] != initialMap[it.id] }

                added.forEach { annotationRepository.saveAnnotation(uri, it) }
                modified.forEach { annotationRepository.saveAnnotation(uri, it) }
                deleted.forEach { annotationRepository.deleteAnnotation(it.id) }
            }
        }
        _draftAnnotations.value = null
        initialSessionSnapshot = emptyMap()
        undoStack.clear()
        redoStack.clear()
        updateUndoRedoStates()
        _hasUnsavedEditChanges.value = false
        _isEditMode.value = false
        _activeTool.value = AnnotationTool.NONE
        _selectedAnnotationId.value = null
    }

    fun discardAndExitEditMode() {
        _draftAnnotations.value = null
        initialSessionSnapshot = emptyMap()
        undoStack.clear()
        redoStack.clear()
        updateUndoRedoStates()
        _hasUnsavedEditChanges.value = false
        _isEditMode.value = false
        _activeTool.value = AnnotationTool.NONE
        _selectedAnnotationId.value = null
    }

    fun exitEditMode() {
        discardAndExitEditMode()
    }

    fun clearDocumentSessionUiState() {
        _selectedHighlightState.value = null
        closeSearch()
        exitEditMode()
    }

    fun setActiveTool(tool: AnnotationTool) {
        if (_activeTool.value == AnnotationTool.TEXT && tool != AnnotationTool.TEXT) {
            _selectedAnnotationId.value = null
        }
        if (_activeTool.value == AnnotationTool.SIGNATURE && tool != AnnotationTool.SIGNATURE) {
            _selectedAnnotationId.value = null
        }
        _activeTool.value = tool
        when (tool) {
            AnnotationTool.PEN -> {
                _selectedColorArgb.value = lastPenColor
                _strokeWidthRatio.value = lastPenWidth
            }
            AnnotationTool.HIGHLIGHTER -> {
                _selectedColorArgb.value = lastHighlighterColor
                _strokeWidthRatio.value = lastHighlighterWidth
            }
            AnnotationTool.TEXT -> {
                _selectedColorArgb.value = lastTextColor
                _textFontSizeRatio.value = lastTextFontSize
            }
            AnnotationTool.SIGNATURE -> {
                // Signature selection retains its own bounds/color
            }
            AnnotationTool.ERASER, AnnotationTool.NONE -> {
                _selectedAnnotationId.value = null
            }
        }
    }

    fun setSelectedColor(color: Int) {
        _selectedColorArgb.value = color
        when (_activeTool.value) {
            AnnotationTool.PEN -> lastPenColor = color
            AnnotationTool.HIGHLIGHTER -> lastHighlighterColor = color
            AnnotationTool.TEXT -> {
                lastTextColor = color
                val selectedId = _selectedAnnotationId.value
                if (selectedId != null) {
                    val currentBox = findAnnotationById(selectedId) as? TextBox
                    if (currentBox != null && currentBox.colorArgb != color) {
                        updateTextBox(currentBox.copy(colorArgb = color), recordUndo = true)
                    }
                }
            }
            else -> {}
        }
    }

    fun setStrokeWidthRatio(ratio: Float) {
        _strokeWidthRatio.value = ratio
        if (_activeTool.value == AnnotationTool.PEN) {
            lastPenWidth = ratio
        } else if (_activeTool.value == AnnotationTool.HIGHLIGHTER) {
            lastHighlighterWidth = ratio
        }
    }

    fun setTextFontSizeRatio(ratio: Float) {
        val clamped = ratio.coerceIn(MIN_TEXT_FONT_SIZE, MAX_TEXT_FONT_SIZE)
        _textFontSizeRatio.value = clamped
        lastTextFontSize = clamped
        val selectedId = _selectedAnnotationId.value
        if (selectedId != null) {
            val currentBox = findAnnotationById(selectedId) as? TextBox
            if (currentBox != null && currentBox.fontSizeRatio != clamped) {
                updateTextBox(currentBox.copy(fontSizeRatio = clamped), recordUndo = true)
            }
        }
    }

    fun selectAnnotation(id: String?) {
        _selectedAnnotationId.value = id
        if (id != null) {
            val annot = findAnnotationById(id)
            if (annot is TextBox) {
                _selectedColorArgb.value = annot.colorArgb
                _textFontSizeRatio.value = annot.fontSizeRatio
                lastTextColor = annot.colorArgb
                lastTextFontSize = annot.fontSizeRatio
            } else if (annot is Signature) {
                _selectedColorArgb.value = annot.colorArgb
            }
        }
    }

    fun findAnnotationById(id: String): PdfAnnotation? {
        val currentDraft = _draftAnnotations.value ?: annotationsByPage.value
        return currentDraft.values.asSequence().flatten().firstOrNull { it.id == id }
    }

    // --- TEXT HIGHLIGHT ACTIONS ---
    fun addTextHighlight(rectsByPage: Map<Int, List<NormalizedRect>>, colorArgb: Int) {
        if (rectsByPage.isEmpty()) return
        val uri = _pdfUri.value

        val newHighlights = mutableListOf<TextHighlight>()
        for ((pageIndex, rects) in rectsByPage) {
            if (rects.isEmpty()) continue
            val highlight = TextHighlight(
                id = UUID.randomUUID().toString(),
                pageIndex = pageIndex,
                createdAt = System.currentTimeMillis(),
                rects = rects,
                colorArgb = colorArgb
            )
            newHighlights.add(highlight)
        }
        if (newHighlights.isEmpty()) return

        if (_isEditMode.value) {
            val currentDraft = _draftAnnotations.value?.toMutableMap() ?: mutableMapOf()
            for (h in newHighlights) {
                val pageList = currentDraft[h.pageIndex]?.toMutableList() ?: mutableListOf()
                pageList.add(h)
                currentDraft[h.pageIndex] = pageList
                undoStack.addLast(UndoAction.Add(h))
            }
            _draftAnnotations.value = currentDraft
            redoStack.clear()
            updateUndoRedoStates()
            checkUnsavedChanges()
        } else if (uri != null) {
            viewModelScope.launch {
                for (h in newHighlights) {
                    annotationRepository.saveAnnotation(uri, h)
                }
            }
        }
    }

    fun updateTextHighlightColor(highlightId: String, newColorArgb: Int) {
        if (_isEditMode.value) {
            val currentDraft = _draftAnnotations.value?.toMutableMap() ?: return
            for ((pageIndex, list) in currentDraft) {
                val index = list.indexOfFirst { it.id == highlightId }
                if (index != -1) {
                    val previous = list[index] as? TextHighlight ?: return
                    if (previous.colorArgb == newColorArgb) return
                    val updated = previous.copy(colorArgb = newColorArgb)
                    val pageList = list.toMutableList()
                    pageList[index] = updated
                    currentDraft[pageIndex] = pageList
                    _draftAnnotations.value = currentDraft

                    undoStack.addLast(UndoAction.Update(previous, updated))
                    redoStack.clear()
                    updateUndoRedoStates()
                    checkUnsavedChanges()
                    return
                }
            }
        } else {
            val uri = _pdfUri.value ?: return
            val highlight = findAnnotationById(highlightId) as? TextHighlight ?: return
            if (highlight.colorArgb == newColorArgb) return
            val updated = highlight.copy(colorArgb = newColorArgb)
            viewModelScope.launch {
                annotationRepository.saveAnnotation(uri, updated)
            }
        }
    }

    fun deleteTextHighlight(highlightId: String) {
        if (_isEditMode.value) {
            deleteAnnotationById(highlightId)
        } else {
            viewModelScope.launch {
                annotationRepository.deleteAnnotation(highlightId)
                if (_selectedAnnotationId.value == highlightId) {
                    _selectedAnnotationId.value = null
                }
            }
        }
    }

    fun addTextBox(pageIndex: Int, xNorm: Float, yNorm: Float, text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return

        val width = DEFAULT_TEXT_WIDTH.coerceIn(MIN_TEXT_WIDTH, MAX_TEXT_WIDTH)
        val clampedX = xNorm.coerceIn(0f, 1f - width)
        val clampedY = yNorm.coerceIn(0f, 0.95f)

        val textBox = TextBox(
            id = UUID.randomUUID().toString(),
            pageIndex = pageIndex,
            createdAt = System.currentTimeMillis(),
            xNorm = clampedX,
            yNorm = clampedY,
            widthNorm = width,
            text = trimmed,
            fontSizeRatio = _textFontSizeRatio.value,
            colorArgb = _selectedColorArgb.value
        )

        val currentDraft = _draftAnnotations.value?.toMutableMap() ?: mutableMapOf()
        val pageList = currentDraft[pageIndex]?.toMutableList() ?: mutableListOf()
        pageList.add(textBox)
        currentDraft[pageIndex] = pageList
        _draftAnnotations.value = currentDraft
        _selectedAnnotationId.value = textBox.id

        undoStack.addLast(UndoAction.Add(textBox))
        redoStack.clear()
        updateUndoRedoStates()
        checkUnsavedChanges()
    }

    fun updateTextBox(updated: TextBox, recordUndo: Boolean = true) {
        val currentDraft = _draftAnnotations.value?.toMutableMap() ?: return
        val pageList = currentDraft[updated.pageIndex]?.toMutableList() ?: return
        val index = pageList.indexOfFirst { it.id == updated.id }
        if (index == -1) return
        val previous = pageList[index]
        if (previous == updated) return

        pageList[index] = updated
        currentDraft[updated.pageIndex] = pageList
        _draftAnnotations.value = currentDraft

        if (recordUndo) {
            undoStack.addLast(UndoAction.Update(previous, updated))
            redoStack.clear()
            updateUndoRedoStates()
        }
        checkUnsavedChanges()
    }

    fun commitTextBoxTransform(original: TextBox, final: TextBox) {
        if (original == final) return
        val currentDraft = _draftAnnotations.value?.toMutableMap() ?: return
        val pageList = currentDraft[final.pageIndex]?.toMutableList() ?: return
        val index = pageList.indexOfFirst { it.id == final.id }
        if (index != -1) {
            pageList[index] = final
            currentDraft[final.pageIndex] = pageList
            _draftAnnotations.value = currentDraft
        }
        undoStack.addLast(UndoAction.Update(original, final))
        redoStack.clear()
        updateUndoRedoStates()
        checkUnsavedChanges()
    }

    // --- SIGNATURE ACTIONS ---
    fun saveNewSignature(signature: SavedSignature) {
        viewModelScope.launch {
            signatureStorage.saveSignature(signature)
            _savedSignature.value = signature
        }
    }

    fun deleteSavedSignature() {
        viewModelScope.launch {
            signatureStorage.clearSavedSignature()
            _savedSignature.value = null
        }
    }

    fun placeSavedSignature(
        pageIndex: Int,
        centerXNorm: Float = 0.5f,
        centerYNorm: Float = 0.5f
    ) {
        val saved = _savedSignature.value ?: return
        val defaultWidth = 0.35f
        val clampedWidth = defaultWidth.coerceIn(0.10f, 0.90f)
        val clampedAspect = saved.aspectRatio.coerceIn(0.4f, 6.0f)
        val approxHeight = (clampedWidth / clampedAspect).coerceIn(0.02f, 0.90f)

        val xNorm = (centerXNorm - clampedWidth / 2f).coerceIn(0f, 1f - clampedWidth)
        val yNorm = (centerYNorm - approxHeight / 2f).coerceIn(0f, 1f - approxHeight)

        val signature = Signature(
            id = UUID.randomUUID().toString(),
            pageIndex = pageIndex,
            createdAt = System.currentTimeMillis(),
            xNorm = xNorm,
            yNorm = yNorm,
            widthNorm = clampedWidth,
            aspectRatio = clampedAspect,
            strokes = saved.strokes,
            strokeWidthRatio = 0.004f,
            colorArgb = saved.colorArgb
        )

        val currentDraft = _draftAnnotations.value?.toMutableMap() ?: mutableMapOf()
        val pageList = currentDraft[pageIndex]?.toMutableList() ?: mutableListOf()
        pageList.add(signature)
        currentDraft[pageIndex] = pageList
        _draftAnnotations.value = currentDraft

        _selectedAnnotationId.value = signature.id
        setActiveTool(AnnotationTool.SIGNATURE)

        undoStack.addLast(UndoAction.Add(signature))
        redoStack.clear()
        updateUndoRedoStates()
        checkUnsavedChanges()
    }

    fun updateSignature(updated: Signature, recordUndo: Boolean = true) {
        val currentDraft = _draftAnnotations.value?.toMutableMap() ?: return
        val pageList = currentDraft[updated.pageIndex]?.toMutableList() ?: return
        val index = pageList.indexOfFirst { it.id == updated.id }
        if (index == -1) return
        val previous = pageList[index]
        if (previous == updated) return

        pageList[index] = updated
        currentDraft[updated.pageIndex] = pageList
        _draftAnnotations.value = currentDraft

        if (recordUndo) {
            undoStack.addLast(UndoAction.Update(previous, updated))
            redoStack.clear()
            updateUndoRedoStates()
        }
        checkUnsavedChanges()
    }

    fun commitSignatureTransform(original: Signature, final: Signature) {
        if (original == final) return
        val currentDraft = _draftAnnotations.value?.toMutableMap() ?: return
        val pageList = currentDraft[final.pageIndex]?.toMutableList() ?: return
        val index = pageList.indexOfFirst { it.id == final.id }
        if (index != -1) {
            pageList[index] = final
            currentDraft[final.pageIndex] = pageList
            _draftAnnotations.value = currentDraft
        }
        undoStack.addLast(UndoAction.Update(original, final))
        redoStack.clear()
        updateUndoRedoStates()
        checkUnsavedChanges()
    }

    fun deleteAnnotationById(id: String) {
        val currentDraft = _draftAnnotations.value?.toMutableMap() ?: return
        for ((pageIndex, list) in currentDraft) {
            val found = list.firstOrNull { it.id == id }
            if (found != null) {
                val pageList = list.toMutableList()
                pageList.remove(found)
                currentDraft[pageIndex] = pageList
                _draftAnnotations.value = currentDraft
                if (_selectedAnnotationId.value == id) {
                    _selectedAnnotationId.value = null
                }
                undoStack.addLast(UndoAction.Delete(found))
                redoStack.clear()
                updateUndoRedoStates()
                checkUnsavedChanges()
                return
            }
        }
    }

    fun commitStroke(pageIndex: Int, points: List<NormalizedPoint>) {
        if (points.isEmpty()) return
        val tool = _activeTool.value
        val isHighlighter = tool == AnnotationTool.HIGHLIGHTER

        // Single tap dot support: duplicate slightly so a dot is drawn
        val finalPoints = if (points.size == 1) {
            val p = points[0]
            listOf(p, NormalizedPoint.of(p.x + 0.0001f, p.y + 0.0001f))
        } else {
            points
        }

        val stroke = FreehandStroke(
            id = UUID.randomUUID().toString(),
            pageIndex = pageIndex,
            createdAt = System.currentTimeMillis(),
            points = finalPoints,
            colorArgb = _selectedColorArgb.value,
            strokeWidthRatio = _strokeWidthRatio.value,
            isHighlighter = isHighlighter
        )

        // Mutate in-memory draft only (zero premature Room writes)
        val currentDraft = _draftAnnotations.value?.toMutableMap() ?: mutableMapOf()
        val pageList = currentDraft[pageIndex]?.toMutableList() ?: mutableListOf()
        pageList.add(stroke)
        currentDraft[pageIndex] = pageList
        _draftAnnotations.value = currentDraft

        undoStack.addLast(UndoAction.Add(stroke))
        redoStack.clear()
        updateUndoRedoStates()
        checkUnsavedChanges()
    }

    fun eraseStrokeAt(pageIndex: Int, point: NormalizedPoint) {
        val currentDraft = _draftAnnotations.value?.toMutableMap() ?: return
        val pageList = currentDraft[pageIndex]?.toMutableList() ?: return

        // Hit-test in reverse order (topmost / most recently added annotation first)
        val targetAnnotation = pageList.asReversed().firstOrNull { annot ->
            when (annot) {
                is FreehandStroke -> {
                    val threshold = max(0.025f, annot.strokeWidthRatio)
                    isPointNearStroke(point, annot.points, threshold)
                }
                is TextBox -> {
                    val approxHeight = (annot.fontSizeRatio * 1.5f).coerceAtLeast(0.035f)
                    val lineEstimate = (annot.text.length / 15 + 1).coerceAtMost(8)
                    val totalHeight = approxHeight * lineEstimate
                    point.x >= annot.xNorm - 0.015f &&
                    point.x <= annot.xNorm + annot.widthNorm + 0.015f &&
                    point.y >= annot.yNorm - 0.015f &&
                    point.y <= annot.yNorm + totalHeight + 0.015f
                }
                is TextHighlight -> {
                    val pad = 0.01f
                    annot.rects.any { r ->
                        point.x >= r.left - pad &&
                        point.x <= r.right + pad &&
                        point.y >= r.top - pad &&
                        point.y <= r.bottom + pad
                    }
                }
                is Signature -> {
                    // Deriving approximate height from widthNorm and aspectRatio (aspectRatio = width / height in physical page)
                    val approxHeight = (annot.widthNorm / annot.aspectRatio).coerceIn(0.01f, 1f)
                    point.x >= annot.xNorm - 0.02f &&
                    point.x <= annot.xNorm + annot.widthNorm + 0.02f &&
                    point.y >= annot.yNorm - 0.02f &&
                    point.y <= annot.yNorm + approxHeight + 0.02f
                }
            }
        } ?: return

        pageList.remove(targetAnnotation)
        currentDraft[pageIndex] = pageList
        _draftAnnotations.value = currentDraft

        if (_selectedAnnotationId.value == targetAnnotation.id) {
            _selectedAnnotationId.value = null
        }

        undoStack.addLast(UndoAction.Delete(targetAnnotation))
        redoStack.clear()
        updateUndoRedoStates()
        checkUnsavedChanges()
    }

    private fun isPointNearStroke(point: NormalizedPoint, points: List<NormalizedPoint>, threshold: Float): Boolean {
        if (points.isEmpty()) return false
        val px = point.x
        val py = point.y
        val thresholdSq = threshold * threshold

        if (points.size == 1) {
            val dx = px - points[0].x
            val dy = py - points[0].y
            return (dx * dx + dy * dy) <= thresholdSq
        }

        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            val distSq = pointToSegmentDistanceSq(px, py, p1.x, p1.y, p2.x, p2.y)
            if (distSq <= thresholdSq) return true
        }
        return false
    }

    private fun pointToSegmentDistanceSq(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val l2 = dx * dx + dy * dy
        if (l2 == 0f) {
            val dpx = px - x1
            val dpy = py - y1
            return dpx * dpx + dpy * dpy
        }
        val t = ((px - x1) * dx + (py - y1) * dy) / l2
        val clampedT = t.coerceIn(0f, 1f)
        val projX = x1 + clampedT * dx
        val projY = y1 + clampedT * dy
        val dpx = px - projX
        val dpy = py - projY
        return dpx * dpx + dpy * dpy
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val action = undoStack.removeLast()
        val currentDraft = _draftAnnotations.value?.toMutableMap() ?: mutableMapOf()
        when (action) {
            is UndoAction.Add -> {
                val pageList = currentDraft[action.annotation.pageIndex]?.toMutableList() ?: mutableListOf()
                pageList.removeAll { it.id == action.annotation.id }
                currentDraft[action.annotation.pageIndex] = pageList
                _draftAnnotations.value = currentDraft
                if (_selectedAnnotationId.value == action.annotation.id) {
                    _selectedAnnotationId.value = null
                }
                redoStack.addLast(action)
            }
            is UndoAction.Delete -> {
                val pageList = currentDraft[action.annotation.pageIndex]?.toMutableList() ?: mutableListOf()
                pageList.add(action.annotation)
                currentDraft[action.annotation.pageIndex] = pageList
                _draftAnnotations.value = currentDraft
                redoStack.addLast(action)
            }
            is UndoAction.Update -> {
                val pageList = currentDraft[action.previous.pageIndex]?.toMutableList() ?: mutableListOf()
                val index = pageList.indexOfFirst { it.id == action.previous.id }
                if (index != -1) {
                    pageList[index] = action.previous
                } else {
                    pageList.add(action.previous)
                }
                currentDraft[action.previous.pageIndex] = pageList
                _draftAnnotations.value = currentDraft
                if (_selectedAnnotationId.value == action.previous.id && action.previous is TextBox) {
                    _selectedColorArgb.value = action.previous.colorArgb
                    _textFontSizeRatio.value = action.previous.fontSizeRatio
                }
                redoStack.addLast(action)
            }
        }
        updateUndoRedoStates()
        checkUnsavedChanges()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val action = redoStack.removeLast()
        val currentDraft = _draftAnnotations.value?.toMutableMap() ?: mutableMapOf()
        when (action) {
            is UndoAction.Add -> {
                val pageList = currentDraft[action.annotation.pageIndex]?.toMutableList() ?: mutableListOf()
                pageList.add(action.annotation)
                currentDraft[action.annotation.pageIndex] = pageList
                _draftAnnotations.value = currentDraft
                if (action.annotation is TextBox) {
                    _selectedAnnotationId.value = action.annotation.id
                }
                undoStack.addLast(action)
            }
            is UndoAction.Delete -> {
                val pageList = currentDraft[action.annotation.pageIndex]?.toMutableList() ?: mutableListOf()
                pageList.removeAll { it.id == action.annotation.id }
                currentDraft[action.annotation.pageIndex] = pageList
                _draftAnnotations.value = currentDraft
                if (_selectedAnnotationId.value == action.annotation.id) {
                    _selectedAnnotationId.value = null
                }
                undoStack.addLast(action)
            }
            is UndoAction.Update -> {
                val pageList = currentDraft[action.updated.pageIndex]?.toMutableList() ?: mutableListOf()
                val index = pageList.indexOfFirst { it.id == action.updated.id }
                if (index != -1) {
                    pageList[index] = action.updated
                } else {
                    pageList.add(action.updated)
                }
                currentDraft[action.updated.pageIndex] = pageList
                _draftAnnotations.value = currentDraft
                if (_selectedAnnotationId.value == action.updated.id && action.updated is TextBox) {
                    _selectedColorArgb.value = action.updated.colorArgb
                    _textFontSizeRatio.value = action.updated.fontSizeRatio
                }
                undoStack.addLast(action)
            }
        }
        updateUndoRedoStates()
        checkUnsavedChanges()
    }

    // --- PDF ACTIONS ---
    fun loadPdf(uri: Uri) {
        clearDocumentSessionUiState()
        _pdfLoadError.value = false
        _pdfUri.value = null
        _startPage.value = 0
        _draftAnnotations.value = null
        initialSessionSnapshot = emptyMap()
        _isEditMode.value = false
        _activeTool.value = AnnotationTool.NONE
        _selectedAnnotationId.value = null
        undoStack.clear()
        redoStack.clear()
        updateUndoRedoStates()
        _hasUnsavedEditChanges.value = false

        viewModelScope.launch {
            if (!pdfRepository.isUriAccessible(uri)) {
                onPdfLoadFailed(uri)
                return@launch
            }

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

    fun onPdfLoadFailed(uri: Uri) {
        _pdfLoadError.value = true
        viewModelScope.launch {
            pdfRepository.deleteRecentFileByUri(uri.toString())
        }
    }

    fun validateRecentFiles() {
        viewModelScope.launch {
            pdfRepository.pruneInaccessibleRecentFiles()
            collectionRepository.pruneInaccessibleCollectionFiles()
        }
    }

    // --- COLLECTION ACTIONS ---
    fun selectCollection(collectionId: Long?) {
        _selectedCollectionId.value = collectionId
    }

    fun createCollection(name: String) {
        viewModelScope.launch {
            val newId = collectionRepository.createCollection(name)
            if (newId > 0) {
                _selectedCollectionId.value = newId
            }
        }
    }

    fun deleteCollection(collectionId: Long) {
        viewModelScope.launch {
            collectionRepository.deleteCollection(collectionId)
            if (_selectedCollectionId.value == collectionId) {
                _selectedCollectionId.value = null
            }
        }
    }

    fun toggleFileInCollection(collectionId: Long, uri: String, title: String, isIncluded: Boolean) {
        viewModelScope.launch {
            if (isIncluded) {
                collectionRepository.addFileToCollection(collectionId, uri, title)
            } else {
                collectionRepository.removeFileFromCollection(collectionId, uri)
            }
        }
    }

    fun addPickedPdfToCollection(collectionId: Long, uri: Uri) {
        viewModelScope.launch {
            if (!pdfRepository.isUriAccessible(uri)) return@launch
            val hasLongTermPermission = pdfRepository.takePersistableUriPermission(uri)
            val title = pdfRepository.extractFileName(uri)
            collectionRepository.addFileToCollection(collectionId, uri.toString(), title)
            if (hasLongTermPermission) {
                val existing = pdfRepository.getFileByUri(uri.toString())
                if (existing == null) {
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
        }
    }

    fun verifyCurrentPdfAccessible() {
        val uri = _pdfUri.value ?: return
        viewModelScope.launch {
            if (!pdfRepository.isUriAccessible(uri)) {
                onPdfLoadFailed(uri)
            }
        }
    }

    fun saveCurrentPage(page: Int) {
        val uri = _pdfUri.value?.toString() ?: return
        _startPage.value = page
        viewModelScope.launch {
            pdfRepository.updatePage(uri, page)
        }
    }

    // --- PDF EXPORT ACTIONS ---
    fun exportCopyToDownloads() {
        if (_isExporting.value) return
        val sourceUri = _pdfUri.value ?: return
        val title = _pdfTitle.value
        val annotations = _draftAnnotations.value ?: annotationsByPage.value

        viewModelScope.launch {
            if (!pdfRepository.isUriAccessible(sourceUri)) {
                onPdfLoadFailed(sourceUri)
                return@launch
            }
            _isExporting.value = true
            val result = pdfExporter.saveCopyToDownloads(sourceUri, title, annotations)
            _isExporting.value = false
            result.onSuccess { exported ->
                _exportEvent.emit(ExportUiEvent.Success(exported.uri, exported.displayName))
            }.onFailure { e ->
                if (e is java.io.FileNotFoundException || !pdfRepository.isUriAccessible(sourceUri)) {
                    onPdfLoadFailed(sourceUri)
                }
                _exportEvent.emit(ExportUiEvent.Failure(e.localizedMessage ?: "Export failed"))
            }
        }
    }

    fun exportCopyToUri(destinationUri: Uri) {
        if (_isExporting.value) return
        val sourceUri = _pdfUri.value ?: return
        val annotations = _draftAnnotations.value ?: annotationsByPage.value

        viewModelScope.launch {
            if (!pdfRepository.isUriAccessible(sourceUri)) {
                onPdfLoadFailed(sourceUri)
                return@launch
            }
            _isExporting.value = true
            val result = pdfExporter.saveCopyToUri(sourceUri, destinationUri, annotations)
            _isExporting.value = false
            result.onSuccess { uri ->
                _exportEvent.emit(ExportUiEvent.Success(uri, "PDF"))
            }.onFailure { e ->
                if (e is java.io.FileNotFoundException || !pdfRepository.isUriAccessible(sourceUri)) {
                    onPdfLoadFailed(sourceUri)
                }
                _exportEvent.emit(ExportUiEvent.Failure(e.localizedMessage ?: "Export failed"))
            }
        }
    }

    fun openExportedPdf(uri: Uri) {
        loadPdf(uri)
    }

    fun deleteRecentFile(recentFile: RecentFile) {
        viewModelScope.launch {
            pdfRepository.deleteRecentFile(recentFile)
        }
    }

    fun convertImagesToPdf(uris: List<Uri>, fileName: String) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isProcessingTool.value = true
            _toolProgress.value = Pair(0, uris.size)
            try {
                val result = pdfToolsRepository.convertImagesToPdf(
                    imageUris = uris,
                    outputFileName = fileName,
                    onProgress = { current, total ->
                        _toolProgress.value = Pair(current, total)
                    }
                )
                result.onSuccess { savedUri ->
                    _toolResultEvent.emit(ToolResultEvent.Success(savedUri, fileName))
                }.onFailure { error ->
                    _toolResultEvent.emit(ToolResultEvent.Failure(error.localizedMessage ?: "Failed to convert images to PDF"))
                }
            } catch (e: Exception) {
                _toolResultEvent.emit(ToolResultEvent.Failure(e.localizedMessage ?: "Failed to convert images to PDF"))
            } finally {
                _isProcessingTool.value = false
                _toolProgress.value = null
            }
        }
    }

    fun resolvePdfDisplayName(uri: Uri): String {
        return pdfRepository.extractFileName(uri)
    }

    fun mergePdfs(uris: List<Uri>, fileName: String) {
        if (uris.size < 2) return
        viewModelScope.launch {
            _isProcessingTool.value = true
            _toolProgress.value = Pair(0, uris.size)
            try {
                val result = pdfToolsRepository.mergePdfs(
                    pdfUris = uris,
                    outputFileName = fileName,
                    onProgress = { current, total ->
                        _toolProgress.value = Pair(current, total)
                    }
                )
                result.onSuccess { savedUri ->
                    _toolResultEvent.emit(ToolResultEvent.Success(savedUri, fileName))
                }.onFailure { error ->
                    _toolResultEvent.emit(ToolResultEvent.Failure(error.localizedMessage ?: "Failed to merge PDFs"))
                }
            } catch (e: Exception) {
                _toolResultEvent.emit(ToolResultEvent.Failure(e.localizedMessage ?: "Failed to merge PDFs"))
            } finally {
                _isProcessingTool.value = false
                _toolProgress.value = null
            }
        }
    }

    // --- IN-DOCUMENT TEXT SEARCH ACTIONS ---

    fun openSearch() {
        if (_isEditMode.value) {
            discardAndExitEditMode()
        }
        _searchUiState.value = _searchUiState.value.copy(
            isSearchOpen = true
        )
    }

    fun closeSearch() {
        searchDebounceJob?.cancel()
        searchDebounceJob = null
        activeSearchJob?.cancel()
        activeSearchJob = null
        _searchUiState.value = SearchUiState(isSearchOpen = false)
    }

    fun onSearchQueryChange(
        newQuery: String,
        pageCount: Int,
        startPage: Int = _startPage.value,
        searchPageProvider: suspend (pageIndex: Int) -> List<SearchMatch>
    ) {
        val previousQuery = _searchUiState.value.query
        val trimmedNew = newQuery.trim()
        val trimmedPrevious = previousQuery.trim()

        // Always preserve raw newQuery so users can freely type whitespace/multi-word phrases
        _searchUiState.value = _searchUiState.value.copy(
            query = newQuery
        )

        // If only trailing/leading spaces were added or removed without changing the trimmed search term,
        // preserve current results and skip restarting or cancelling the search job
        if (trimmedNew == trimmedPrevious) {
            return
        }

        searchDebounceJob?.cancel()
        activeSearchJob?.cancel()
        activeSearchJob = null

        _searchUiState.value = _searchUiState.value.copy(
            isSearching = false
        )

        if (trimmedNew.isBlank() || pageCount <= 0) {
            _searchUiState.value = _searchUiState.value.copy(
                matches = emptyList(),
                currentMatchIndex = -1,
                matchesByPage = emptyMap(),
                isSearching = false
            )
            return
        }

        searchDebounceJob = viewModelScope.launch {
            delay(250.milliseconds) // ~250ms debounce
            startSearch(trimmedNew, startPage, pageCount, searchPageProvider)
        }
    }

    fun startSearch(
        query: String,
        startPage: Int,
        pageCount: Int,
        searchPageProvider: suspend (pageIndex: Int) -> List<SearchMatch>
    ) {
        activeSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isBlank() || pageCount <= 0) {
            _searchUiState.value = _searchUiState.value.copy(
                query = query,
                matches = emptyList(),
                currentMatchIndex = -1,
                matchesByPage = emptyMap(),
                isSearching = false
            )
            return
        }

        activeSearchJob = viewModelScope.launch(Dispatchers.IO) {
            _searchUiState.value = _searchUiState.value.copy(
                query = query,
                isSearching = true,
                matches = emptyList(),
                currentMatchIndex = -1,
                matchesByPage = emptyMap()
            )

            val accumulatedMatches = mutableListOf<SearchMatch>()
            val accumulatedByPage = mutableMapOf<Int, MutableList<SearchMatch>>()

            // Scan strictly in sequential document order (0 until pageCount)
            // so match IDs and numbering (1/N..N/N) always follow Page 1 -> Last Page order
            for (pageIndex in 0 until pageCount) {
                ensureActive()

                val pageMatches = try {
                    searchPageProvider(pageIndex)
                } catch (_: Exception) {
                    emptyList()
                }

                ensureActive()

                if (pageMatches.isNotEmpty()) {
                    val assignedMatches = pageMatches.mapIndexed { indexOnPage, match ->
                        match.copy(id = accumulatedMatches.size + indexOnPage)
                    }

                    accumulatedMatches.addAll(assignedMatches)
                    val pageList = accumulatedByPage.getOrPut(pageIndex) { mutableListOf() }
                    pageList.addAll(assignedMatches)

                    val currentSnapshot = accumulatedMatches.toList()
                    val byPageSnapshot = accumulatedByPage.mapValues { it.value.toList() }

                    withContext(Dispatchers.Main.immediate) {
                        val prevIndex = _searchUiState.value.currentMatchIndex
                        // Smart selection: as matches accumulate, auto-select the first match whose pageIndex >= startPage
                        val targetIndex = if (prevIndex == -1) {
                            val candidateIndex = currentSnapshot.indexOfFirst { it.pageIndex >= startPage }
                            if (candidateIndex != -1) candidateIndex else -1
                        } else {
                            prevIndex
                        }
                        _searchUiState.value = _searchUiState.value.copy(
                            matches = currentSnapshot,
                            currentMatchIndex = targetIndex,
                            matchesByPage = byPageSnapshot
                        )
                    }
                }
            }

            withContext(Dispatchers.Main.immediate) {
                val finalMatches = _searchUiState.value.matches
                val finalIndex = _searchUiState.value.currentMatchIndex
                // If scan finished and no match had pageIndex >= startPage, select the first match in the document (index 0)
                val resolvedIndex = if (finalIndex == -1 && finalMatches.isNotEmpty()) 0 else finalIndex
                _searchUiState.value = _searchUiState.value.copy(
                    isSearching = false,
                    currentMatchIndex = resolvedIndex
                )
            }
        }
    }

    fun nextMatch() {
        val state = _searchUiState.value
        val total = state.matches.size
        if (total <= 1) return
        val next = (state.currentMatchIndex + 1) % total
        _searchUiState.value = state.copy(currentMatchIndex = next)
    }

    fun previousMatch() {
        val state = _searchUiState.value
        val total = state.matches.size
        if (total <= 1) return
        val prev = if (state.currentMatchIndex - 1 < 0) total - 1 else state.currentMatchIndex - 1
        _searchUiState.value = state.copy(currentMatchIndex = prev)
    }

    fun selectMatch(matchIndex: Int) {
        val state = _searchUiState.value
        if (matchIndex in state.matches.indices) {
            _searchUiState.value = state.copy(currentMatchIndex = matchIndex)
        }
    }

    companion object {
        val PEN_COLORS = listOf(
            0xFFE53935.toInt(), // Red
            0xFF1E88E5.toInt(), // Blue
            0xFF212121.toInt(), // Black
            0xFF43A047.toInt(), // Green
            0xFFFB8C00.toInt()  // Orange
        )

        val HIGHLIGHTER_COLORS = listOf(
            0xFFFFF176.toInt(), // Pastel Yellow
            0xFFA5D6A7.toInt(), // Pastel Green
            0xFF90CAF9.toInt(), // Pastel Blue
            0xFFF48FB1.toInt(), // Pastel Pink
            0xFFFFCC80.toInt()  // Pastel Orange
        )

        const val DEFAULT_PEN_COLOR = 0xFFE53935.toInt()
        const val DEFAULT_HIGHLIGHTER_COLOR = 0xFFFFF176.toInt()
        const val DEFAULT_PEN_WIDTH = 0.005f
        const val DEFAULT_HIGHLIGHTER_WIDTH = 0.025f

        const val DEFAULT_TEXT_COLOR = 0xFF212121.toInt()
        const val DEFAULT_TEXT_FONT_SIZE = 0.032f
        const val MIN_TEXT_FONT_SIZE = 0.015f
        const val MAX_TEXT_FONT_SIZE = 0.080f
        const val DEFAULT_TEXT_WIDTH = 0.40f
        const val MIN_TEXT_WIDTH = 0.12f
        const val MAX_TEXT_WIDTH = 0.95f
    }
}

