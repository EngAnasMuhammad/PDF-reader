package com.anasdev.pdfreader.presentation.ui.screens.pdfScreen

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anasdev.pdfreader.R
import com.anasdev.pdfreader.domain.model.AnnotationTool
import com.anasdev.pdfreader.domain.model.NormalizedRect
import com.anasdev.pdfreader.domain.model.ReadingMode
import com.anasdev.pdfreader.domain.model.Signature
import com.anasdev.pdfreader.domain.model.TextBox
import com.anasdev.pdfreader.presentation.pdf.AddOrEditTextDialog
import com.anasdev.pdfreader.presentation.pdf.AnnotationRenderer
import com.anasdev.pdfreader.presentation.pdf.AnnotationStateHolder
import com.anasdev.pdfreader.presentation.pdf.CreateSignatureDialog
import com.anasdev.pdfreader.presentation.pdf.DiscardConfirmDialog
import com.anasdev.pdfreader.presentation.pdf.EditModeFloatingToolbar
import com.anasdev.pdfreader.presentation.pdf.ExportProgressDialog
import com.anasdev.pdfreader.presentation.pdf.MinimizedEditToolbarFab
import com.anasdev.pdfreader.presentation.pdf.PdfLoadErrorScreen
import com.anasdev.pdfreader.presentation.pdf.PdfTouchHandler
import com.anasdev.pdfreader.presentation.pdf.SavedSignaturePickerDialog
import com.anasdev.pdfreader.presentation.pdf.TextDialogState
import com.anasdev.pdfreader.presentation.pdf.TooltipIconButton
import com.anasdev.pdfreader.presentation.state.ExportUiEvent
import com.anasdev.pdfreader.presentation.state.PdfViewModel
import com.anasdev.pdfreader.presentation.utils.PdfColorFilters
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import com.github.barteksc.pdfviewer.selection.PdfTextSelectionManager
import com.github.barteksc.pdfviewer.util.FitPolicy
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfScreen(
    viewModel: PdfViewModel,
    onClickBack: () -> Unit,
    onClickSetting: () -> Unit
) {
    val pdfUri by viewModel.pdfUri.collectAsStateWithLifecycle()
    val pdfTitle by viewModel.pdfTitle.collectAsStateWithLifecycle()
    val startPage by viewModel.startPage.collectAsStateWithLifecycle()
    val keepScreenAwake by viewModel.keepScreenAwake.collectAsStateWithLifecycle()
    val isHorizontalScroll by viewModel.isHorizontalScroll.collectAsStateWithLifecycle()
    val readingMode by viewModel.readingMode.collectAsStateWithLifecycle()
    val pdfLoadError by viewModel.pdfLoadError.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.verifyCurrentPdfAccessible()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isEditMode by viewModel.isEditMode.collectAsStateWithLifecycle()
    val activeTool by viewModel.activeTool.collectAsStateWithLifecycle()
    val selectedColorArgb by viewModel.selectedColorArgb.collectAsStateWithLifecycle()
    val strokeWidthRatio by viewModel.strokeWidthRatio.collectAsStateWithLifecycle()
    val textFontSizeRatio by viewModel.textFontSizeRatio.collectAsStateWithLifecycle()
    val selectedAnnotationId by viewModel.selectedAnnotationId.collectAsStateWithLifecycle()
    val annotationsByPage by viewModel.annotationsByPage.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val hasUnsavedEditChanges by viewModel.hasUnsavedEditChanges.collectAsStateWithLifecycle()
    val isExporting by viewModel.isExporting.collectAsStateWithLifecycle()
    val savedSignature by viewModel.savedSignature.collectAsStateWithLifecycle()
    val searchUiState by viewModel.searchUiState.collectAsStateWithLifecycle()

    val selectedTextBox = remember(selectedAnnotationId, annotationsByPage) {
        if (selectedAnnotationId == null) null
        else annotationsByPage.values.asSequence().flatten().firstOrNull { it.id == selectedAnnotationId } as? TextBox
    }

    val selectedSignature = remember(selectedAnnotationId, annotationsByPage) {
        if (selectedAnnotationId == null) null
        else annotationsByPage.values.asSequence().flatten().firstOrNull { it.id == selectedAnnotationId } as? Signature
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var isStylePopoverVisible by remember { mutableStateOf(false) }
    var isToolbarMinimized by remember { mutableStateOf(false) }
    var textDialogState by remember { mutableStateOf<TextDialogState?>(null) }
    var showSavedSignatureDialog by remember { mutableStateOf(false) }
    var showCreateSignatureDialog by remember { mutableStateOf(false) }

    // Auto-close popover when switching away from Pen/Highlighter/Text
    LaunchedEffect(activeTool) {
        if (activeTool == AnnotationTool.ERASER || activeTool == AnnotationTool.NONE || activeTool == AnnotationTool.SIGNATURE) {
            isStylePopoverVisible = false
        }
    }

    // Reset toolbar states when exiting edit mode
    LaunchedEffect(isEditMode) {
        if (!isEditMode) {
            isStylePopoverVisible = false
            isToolbarMinimized = false
            textDialogState = null
            showSavedSignatureDialog = false
            showCreateSignatureDialog = false
        }
    }

    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }

    val defaultExportName = remember(pdfTitle) {
        val baseName = pdfTitle.removeSuffix(".pdf").ifBlank { "Document" }
        "${baseName}_annotated.pdf"
    }

    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destinationUri ->
        destinationUri?.let { uri ->
            viewModel.exportCopyToUri(uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.exportEvent.collect { event ->
            when (event) {
                is ExportUiEvent.Success -> {
                    val message = resources.getString(R.string.saved_to_downloads, event.displayName)
                    val result = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = resources.getString(R.string.open_action),
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.openExportedPdf(event.uri)
                    }
                }
                is ExportUiEvent.Failure -> {
                    snackbarHostState.showSnackbar(
                        message = "${resources.getString(R.string.export_failed)}: ${event.errorMessage}",
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    val backgroundColor = MaterialTheme.colorScheme.background.toArgb()
    val bgColor = MaterialTheme.colorScheme.background

    var showMenu by remember { mutableStateOf(false) }
    var showNightModeSubMenu by remember { mutableStateOf(false) }
    var pdfViewRef by remember { mutableStateOf<PDFView?>(null) }
    var isTopBarVisible by remember { mutableStateOf(true) }
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val topBarHeightDp = remember(topBarHeightPx) { with(density) { topBarHeightPx.toDp() } }
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }

    // Quick Pill state for tapping TextHighlight in Reading Mode
    val selectedHighlightState by viewModel.selectedHighlightState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    val annotationRenderer = remember { AnnotationRenderer() }

    // Lightweight state holder kept in sync on composition to prevent document reloads
    val stateHolder = remember { AnnotationStateHolder() }
    stateHolder.annotationsByPage = annotationsByPage
    stateHolder.searchMatchesByPage = searchUiState.matchesByPage
    stateHolder.activeSearchMatchId = searchUiState.currentMatch?.id
    stateHolder.isEditMode = isEditMode
    stateHolder.activeTool = activeTool
    stateHolder.selectedColorArgb = selectedColorArgb
    stateHolder.strokeWidthRatio = strokeWidthRatio
    stateHolder.selectedAnnotationId = selectedAnnotationId
    stateHolder.textFontSizeRatio = textFontSizeRatio
    stateHolder.onCanvasTouch = {
        if (isStylePopoverVisible) {
            isStylePopoverVisible = false
        }
        viewModel.clearSelectedHighlight()
    }
    stateHolder.onOpenAddText = { pageIndex, xNorm, yNorm ->
        textDialogState = TextDialogState.Add(pageIndex, xNorm, yNorm)
    }
    stateHolder.onOpenEditText = { textBox ->
        textDialogState = TextDialogState.Edit(textBox)
    }
    stateHolder.onSelectHighlight = { highlight, screenX, screenY ->
        viewModel.setSelectedHighlight(highlight, screenX, screenY)
    }
    stateHolder.onDismissHighlight = {
        viewModel.clearSelectedHighlight()
    }

    // Reset transient document UI state on document URI change
    LaunchedEffect(pdfUri) {
        viewModel.clearDocumentSessionUiState()
        pdfViewRef?.clearTextSelection()
    }

    // Invalidate PDFView on annotations or search matches change without re-calling load()
    LaunchedEffect(annotationsByPage, selectedAnnotationId, searchUiState.matchesByPage, searchUiState.currentMatchIndex) {
        pdfViewRef?.invalidate()
    }

    // Auto-jump to match when current match changes
    LaunchedEffect(searchUiState.currentMatchIndex, searchUiState.matches.firstOrNull()?.id) {
        searchUiState.currentMatch?.let { activeMatch ->
            pdfViewRef?.jumpToMatch(activeMatch.pageIndex, activeMatch.normalizedCenterY)
        }
    }

    val handleCloseEditMode = {
        if (hasUnsavedEditChanges) {
            showDiscardConfirmDialog = true
        } else {
            viewModel.discardAndExitEditMode()
        }
    }

    // Intercept back gesture in edit mode to prompt/discard without closing document
    BackHandler(enabled = isEditMode) {
        handleCloseEditMode()
    }

    // Intercept back gesture when search is open to close search without leaving document
    BackHandler(enabled = searchUiState.isSearchOpen && !isEditMode) {
        viewModel.closeSearch()
    }

    val localView = LocalView.current
    DisposableEffect(keepScreenAwake) {
        if (keepScreenAwake) {
            localView.keepScreenOn = true
        }
        onDispose {
            localView.keepScreenOn = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            pdfViewRef?.let { pdfView ->
                pdfView.clearTextSelection()
                viewModel.saveCurrentPage(pdfView.currentPage)
                pdfView.recycle() // Cleanly free C++ native PDFium document memory & bitmap caches
            }
            pdfViewRef = null
            viewModel.clearDocumentSessionUiState()
        }
    }

    if (pdfLoadError) {
        PdfLoadErrorScreen(
            bgColor = bgColor,
            onClickBack = onClickBack
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
        ) {
            // --- 1. PDF DOCUMENT LAYER (WITH READING MODE TINT) ---
            val activeColorFilter = when (readingMode) {
                ReadingMode.NORMAL -> null
                ReadingMode.SEPIA -> PdfColorFilters.sepiaColorFilter
                ReadingMode.DIMMED -> PdfColorFilters.dimmedColorFilter
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (activeColorFilter != null) {
                            Modifier.graphicsLayer { colorFilter = activeColorFilter }
                        } else {
                            Modifier
                        }
                    )
            ) {
                pdfUri?.let { uri ->
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            val pdfView = PDFView(context, null).apply {
                                this.minZoom = 0.5f
                                this.midZoom = 1.0f
                                this.maxZoom = 8.0f
                                this.setBackgroundColor(backgroundColor)
                            }
                            pdfViewRef = pdfView

                            pdfView.textSelectionManager?.setSelectionHighlightListener { colorArgb ->
                                val rectsByPage = pdfView.normalizedSelectionRectsByPage
                                if (rectsByPage.isNotEmpty()) {
                                    val mapped = rectsByPage.mapValues { (_, rectList) ->
                                        rectList.map { r ->
                                            NormalizedRect(r.left, r.top, r.right, r.bottom)
                                        }
                                    }
                                    viewModel.addTextHighlight(mapped, colorArgb)
                                }
                                pdfView.clearTextSelection()
                            }

                            val wrapper = PdfTouchHandler(
                                context = context,
                                pdfView = pdfView,
                                stateHolder = stateHolder,
                                viewModel = viewModel,
                                annotationRenderer = annotationRenderer,
                                onToggleTopBar = { isTopBarVisible = !isTopBarVisible },
                                onScrollDistance = { distanceY ->
                                    if (distanceY > 10 && isTopBarVisible) {
                                        isTopBarVisible = false
                                    } else if (distanceY < -10 && !isTopBarVisible) {
                                        isTopBarVisible = true
                                    }
                                }
                            )

                            wrapper.addView(pdfView, android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT)

                            pdfView.apply {
                                fromUri(uri)
                                    .enableSwipe(true)
                                    .swipeHorizontal(isHorizontalScroll)
                                    .enableDoubletap(false)
                                    .enableAnnotationRendering(true)
                                    .enableAntialiasing(true)
                                    .spacing(10)
                                    .documentTopPadding(120)
                                    .documentBottomPadding(70)
                                    .pageFitPolicy(FitPolicy.WIDTH)
                                    .fitEachPage(true)
                                    .scrollHandle(DefaultScrollHandle(context))
                                    .defaultPage(startPage)
                                    .onError { _ ->
                                        viewModel.onPdfLoadFailed(uri)
                                    }
                                    .onPageChange { page, _ ->
                                        viewModel.saveCurrentPage(page)
                                    }
                                    .onPageScroll { _, positionOffset ->
                                        if (selectedHighlightState != null) {
                                            viewModel.clearSelectedHighlight()
                                        }
                                        if (!stateHolder.isEditMode && !searchUiState.isSearchOpen) {
                                            if (positionOffset > 0.02f && isTopBarVisible) {
                                                isTopBarVisible = false
                                            } else if (positionOffset < -0.02f && !isTopBarVisible) {
                                                isTopBarVisible = true
                                            }
                                        }
                                    }
                                    .onDrawAll { canvas, pageWidth, pageHeight, displayedPage ->
                                        annotationRenderer.render(
                                            canvas = canvas,
                                            pageWidth = pageWidth,
                                            pageHeight = pageHeight,
                                            pageIndex = displayedPage,
                                            persistedAnnotations = stateHolder.annotationsByPage[displayedPage],
                                            activeStroke = stateHolder.activeStroke,
                                            isEditMode = stateHolder.isEditMode,
                                            selectedAnnotationId = stateHolder.selectedAnnotationId,
                                            searchMatches = stateHolder.searchMatchesByPage[displayedPage],
                                            activeSearchMatchId = stateHolder.activeSearchMatchId
                                        )
                                    }
                                    .load()
                            }

                            wrapper
                        },
                        update = {
                            // Repaint strokes without document reloading
                            pdfViewRef?.invalidate()
                        }
                    )
                }
            }


            // Top App Bar Layer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { topBarHeightPx = it.height }
                    .then(
                        if (activeColorFilter != null) {
                            Modifier.graphicsLayer { colorFilter = activeColorFilter }
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.TopCenter
            ) {
                AnimatedVisibility(
                    visible = isTopBarVisible || isEditMode || searchUiState.isSearchOpen,
                    enter = slideInVertically(initialOffsetY = { -it }),
                    exit = slideOutVertically(targetOffsetY = { -it })
                ) {
                    if (searchUiState.isSearchOpen) {
                        PdfSearchBar(
                            searchUiState = searchUiState,
                            onQueryChange = { query ->
                                val pdfView = pdfViewRef
                                val pageCount = pdfView?.pageCount ?: 0
                                val curPage = pdfView?.currentPage ?: 0
                                viewModel.onSearchQueryChange(
                                    newQuery = query,
                                    pageCount = pageCount,
                                    startPage = curPage,
                                    searchPageProvider = { pageIndex ->
                                        val text = pdfView?.extractPageTextForSearch(pageIndex) ?: ""
                                        val matchRanges = com.anasdev.pdfreader.domain.search.TextSearchHelper.findMatches(text, query.trim())
                                        matchRanges.map { range ->
                                            val rectFs = pdfView?.getNormalizedTextRectsForRange(
                                                pageIndex,
                                                range.originalStartIndex,
                                                range.originalLength
                                            ) ?: emptyList()
                                            val normRects = rectFs.map { r ->
                                                NormalizedRect(r.left, r.top, r.right, r.bottom)
                                            }
                                            com.anasdev.pdfreader.domain.model.SearchMatch(
                                                id = 0,
                                                pageIndex = pageIndex,
                                                rects = normRects
                                            )
                                        }
                                    }
                                )
                            },
                            onCloseSearch = { viewModel.closeSearch() },
                            onNextMatch = { viewModel.nextMatch() },
                            onPreviousMatch = { viewModel.previousMatch() }
                        )
                    } else if (!isEditMode) {
                        TopAppBar(
                            title = {
                                Text(
                                    text = pdfTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                            ),
                            navigationIcon = {
                                TooltipIconButton(
                                    label = stringResource(R.string.back),
                                    onClick = {
                                        pdfViewRef?.let { viewModel.saveCurrentPage(it.currentPage) }
                                        onClickBack()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.back)
                                    )
                                }
                            },
                            actions = {
                                // Save Copy quick action button when document has annotations
                                val hasAnnotations = annotationsByPage.values.any { it.isNotEmpty() }
                                if (hasAnnotations) {
                                    TooltipIconButton(
                                        label = stringResource(R.string.save_copy),
                                        onClick = { viewModel.exportCopyToDownloads() }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SaveAlt,
                                            contentDescription = stringResource(R.string.save_copy)
                                        )
                                    }
                                }

                                // Search in Document Toggle
                                TooltipIconButton(
                                    label = stringResource(R.string.search),
                                    onClick = { viewModel.openSearch() }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = stringResource(R.string.search)
                                    )
                                }

                                // More Options
                                TooltipIconButton(
                                    label = stringResource(R.string.more_options),
                                    onClick = {
                                        showMenu = true
                                        showNightModeSubMenu = false
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = stringResource(R.string.more_options)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    if (!showNightModeSubMenu) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.save_copy)) },
                                            leadingIcon = {
                                                Icon(Icons.Default.SaveAlt, contentDescription = null)
                                            },
                                            onClick = {
                                                showMenu = false
                                                viewModel.exportCopyToDownloads()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.save_as)) },
                                            leadingIcon = {
                                                Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null)
                                            },
                                            onClick = {
                                                showMenu = false
                                                saveAsLauncher.launch(defaultExportName)
                                            }
                                        )
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.reading_mode)) },
                                            onClick = { showNightModeSubMenu = true }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.setting)) },
                                            onClick = {
                                                showMenu = false
                                                pdfViewRef?.let { viewModel.saveCurrentPage(it.currentPage) }
                                                onClickSetting()
                                            }
                                        )
                                    } else {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.back)) },
                                            leadingIcon = {
                                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                            },
                                            onClick = { showNightModeSubMenu = false }
                                        )
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.normal_filter)) },
                                            onClick = {
                                                viewModel.setReadingMode(ReadingMode.NORMAL)
                                                showMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.sepia_filter)) },
                                            onClick = {
                                                viewModel.setReadingMode(ReadingMode.SEPIA)
                                                showMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.dim_filter)) },
                                            onClick = {
                                                viewModel.setReadingMode(ReadingMode.DIMMED)
                                                showMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        )
                    } else {
                        // Edit Mode Top Bar
                        TopAppBar(
                            title = {
                                Text(
                                    text = stringResource(R.string.annotate),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = if (isLandscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge
                                )
                            },
                            windowInsets = TopAppBarDefaults.windowInsets,
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                            ),
                            navigationIcon = {
                                TooltipIconButton(
                                    label = stringResource(R.string.close),
                                    onClick = handleCloseEditMode
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.close)
                                    )
                                }
                            },
                            actions = {
                                // Pan / Hand tool toggle (allows 1-finger scrolling without exiting edit mode)
                                TooltipIconButton(
                                    label = stringResource(R.string.pan_mode),
                                    onClick = {
                                        isStylePopoverVisible = false
                                        if (activeTool == AnnotationTool.NONE) {
                                            viewModel.setActiveTool(AnnotationTool.PEN)
                                        } else {
                                            viewModel.setActiveTool(AnnotationTool.NONE)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PanTool,
                                        contentDescription = stringResource(R.string.pan_mode),
                                        tint = if (activeTool == AnnotationTool.NONE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Undo
                                TooltipIconButton(
                                    label = stringResource(R.string.undo),
                                    onClick = { viewModel.undo() },
                                    enabled = canUndo
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Undo,
                                        contentDescription = stringResource(R.string.undo)
                                    )
                                }

                                // Redo
                                TooltipIconButton(
                                    label = stringResource(R.string.redo),
                                    onClick = { viewModel.redo() },
                                    enabled = canRedo
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Redo,
                                        contentDescription = stringResource(R.string.redo)
                                    )
                                }

                                // Done (Commit & Save to Room)
                                TextButton(onClick = {
                                    val hadChanges = hasUnsavedEditChanges
                                    viewModel.saveAndExitEditMode()
                                    if (hadChanges) {
                                        coroutineScope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = resources.getString(R.string.annotations_saved_in_app),
                                                actionLabel = resources.getString(R.string.save_copy),
                                                duration = SnackbarDuration.Long
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                viewModel.exportCopyToDownloads()
                                            }
                                        }
                                    }
                                }) {
                                    Text(
                                        text = stringResource(R.string.done),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // --- 3. EDIT MODE FLOATING TOOLBARS & POPOVERS ---

            // PORTRAIT: Single-strip horizontal pill + Popover above
            AnimatedVisibility(
                visible = isEditMode && !isToolbarMinimized && !isLandscape,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                EditModeFloatingToolbar(
                    isLandscape = false,
                    activeTool = activeTool,
                    selectedColorArgb = selectedColorArgb,
                    strokeWidthRatio = strokeWidthRatio,
                    textFontSizeRatio = textFontSizeRatio,
                    selectedTextBox = selectedTextBox,
                    selectedSignature = selectedSignature,
                    isStylePopoverVisible = isStylePopoverVisible,
                    onSelectPen = {
                        if (activeTool != AnnotationTool.PEN) {
                            viewModel.setActiveTool(AnnotationTool.PEN)
                        } else {
                            isStylePopoverVisible = !isStylePopoverVisible
                        }
                    },
                    onSelectHighlighter = {
                        if (activeTool != AnnotationTool.HIGHLIGHTER) {
                            viewModel.setActiveTool(AnnotationTool.HIGHLIGHTER)
                        } else {
                            isStylePopoverVisible = !isStylePopoverVisible
                        }
                    },
                    onSelectEraser = {
                        viewModel.setActiveTool(AnnotationTool.ERASER)
                        isStylePopoverVisible = false
                    },
                    onSelectText = {
                        if (activeTool != AnnotationTool.TEXT) {
                            viewModel.setActiveTool(AnnotationTool.TEXT)
                        } else {
                            isStylePopoverVisible = !isStylePopoverVisible
                        }
                    },
                    onSignatureClick = {
                        viewModel.setActiveTool(AnnotationTool.SIGNATURE)
                        isStylePopoverVisible = false
                        if (savedSignature == null) {
                            showCreateSignatureDialog = true
                        } else {
                            showSavedSignatureDialog = true
                        }
                    },
                    onToggleStylePopover = { isStylePopoverVisible = !isStylePopoverVisible },
                    onSelectColor = { viewModel.setSelectedColor(it) },
                    onStrokeWidthChange = { viewModel.setStrokeWidthRatio(it) },
                    onFontSizeChange = { viewModel.setTextFontSizeRatio(it) },
                    onEditText = { stateHolder.onOpenEditText?.invoke(it) },
                    onDeleteText = { viewModel.deleteAnnotationById(it) },
                    onDeleteSignature = {
                        selectedSignature?.let { viewModel.deleteAnnotationById(it.id) }
                    },
                    onMinimize = {
                        isStylePopoverVisible = false
                        isToolbarMinimized = true
                    }
                )
            }

            // LANDSCAPE: Single-strip vertical rail + Popover to the start (left in LTR)
            AnimatedVisibility(
                visible = isEditMode && !isToolbarMinimized && isLandscape,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .displayCutoutPadding()
                    .navigationBarsPadding()
                    .padding(
                        top = (if (topBarHeightDp > 0.dp) topBarHeightDp else 64.dp) + 8.dp,
                        bottom = 8.dp,
                        end = 16.dp
                    )
            ) {
                EditModeFloatingToolbar(
                    isLandscape = true,
                    activeTool = activeTool,
                    selectedColorArgb = selectedColorArgb,
                    strokeWidthRatio = strokeWidthRatio,
                    textFontSizeRatio = textFontSizeRatio,
                    selectedTextBox = selectedTextBox,
                    selectedSignature = selectedSignature,
                    isStylePopoverVisible = isStylePopoverVisible,
                    onSelectPen = {
                        if (activeTool != AnnotationTool.PEN) {
                            viewModel.setActiveTool(AnnotationTool.PEN)
                        } else {
                            isStylePopoverVisible = !isStylePopoverVisible
                        }
                    },
                    onSelectHighlighter = {
                        if (activeTool != AnnotationTool.HIGHLIGHTER) {
                            viewModel.setActiveTool(AnnotationTool.HIGHLIGHTER)
                        } else {
                            isStylePopoverVisible = !isStylePopoverVisible
                        }
                    },
                    onSelectEraser = {
                        viewModel.setActiveTool(AnnotationTool.ERASER)
                        isStylePopoverVisible = false
                    },
                    onSelectText = {
                        if (activeTool != AnnotationTool.TEXT) {
                            viewModel.setActiveTool(AnnotationTool.TEXT)
                        } else {
                            isStylePopoverVisible = !isStylePopoverVisible
                        }
                    },
                    onSignatureClick = {
                        viewModel.setActiveTool(AnnotationTool.SIGNATURE)
                        isStylePopoverVisible = false
                        if (savedSignature == null) {
                            showCreateSignatureDialog = true
                        } else {
                            showSavedSignatureDialog = true
                        }
                    },
                    onToggleStylePopover = { isStylePopoverVisible = !isStylePopoverVisible },
                    onSelectColor = { viewModel.setSelectedColor(it) },
                    onStrokeWidthChange = { viewModel.setStrokeWidthRatio(it) },
                    onFontSizeChange = { viewModel.setTextFontSizeRatio(it) },
                    onEditText = { stateHolder.onOpenEditText?.invoke(it) },
                    onDeleteText = { viewModel.deleteAnnotationById(it) },
                    onDeleteSignature = {
                        selectedSignature?.let { viewModel.deleteAnnotationById(it.id) }
                    },
                    onMinimize = {
                        isStylePopoverVisible = false
                        isToolbarMinimized = true
                    }
                )
            }

            // Mini-FAB: Collapsed toolbar pill in the bottom-end corner
            AnimatedVisibility(
                visible = isEditMode && isToolbarMinimized,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .displayCutoutPadding()
                    .padding(16.dp)
            ) {
                MinimizedEditToolbarFab(
                    onExpand = { isToolbarMinimized = false }
                )
            }

            // --- READING MODE EDIT FAB ---
            AnimatedVisibility(
                visible = !isEditMode && isTopBarVisible && !searchUiState.isSearchOpen,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .displayCutoutPadding()
                    .padding(16.dp)
            ) {
                FloatingActionButton(
                    onClick = { viewModel.enterEditMode() },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.edit_pdf)
                    )
                }
            }

            // --- READING MODE TAP-TO-RECOLOR / DELETE FLOATING PILL ---
            if (!isEditMode) {
                selectedHighlightState?.let { (highlight, coords) ->
                    val density = resources.displayMetrics.density
                    val pillWidthPx = 196f * density
                    val pillHeightPx = 54f * density

                    // Center pill above touch coordinate, clamped within screen margins
                    val screenW = resources.displayMetrics.widthPixels.toFloat()
                    val targetX = (coords.first - (pillWidthPx / 2f)).coerceIn(16f * density, screenW - pillWidthPx - 16f * density)
                    val targetY = (coords.second - pillHeightPx - 24f * density).coerceAtLeast(80f * density)

                    Surface(
                        modifier = Modifier
                            .offset { IntOffset(targetX.roundToInt(), targetY.roundToInt()) },
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // All available colors appear directly on the main pill
                            val allColors = listOf(
                                PdfTextSelectionManager.COLOR_YELLOW,
                                PdfTextSelectionManager.COLOR_GREEN,
                                PdfTextSelectionManager.COLOR_PINK,
                                PdfTextSelectionManager.COLOR_BLUE
                            )
                            for (colorArgb in allColors) {
                                val isSelected = highlight.colorArgb == colorArgb
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorArgb))
                                        .border(1.dp, Color(0x33000000), CircleShape)
                                        .clickable {
                                            viewModel.updateTextHighlightColor(highlight.id, colorArgb)
                                            viewModel.clearSelectedHighlight()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF212121))
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier
                                    .size(width = 1.dp, height = 20.dp)
                                    .padding(horizontal = 2.dp)
                            )

                            TooltipIconButton(
                                label = stringResource(R.string.delete_annotation),
                                onClick = {
                                    viewModel.deleteTextHighlight(highlight.id)
                                    viewModel.clearSelectedHighlight()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete_annotation),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // --- SNACKBAR HOST LAYER ---
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = if (isEditMode && !isToolbarMinimized && !isLandscape) 80.dp else 16.dp)
            )
        }

        // --- ADD / EDIT TEXT DIALOG ---
        AddOrEditTextDialog(
            dialogState = textDialogState,
            onDismiss = { textDialogState = null },
            onAddText = { pageIndex, xNorm, yNorm, text ->
                viewModel.addTextBox(pageIndex, xNorm, yNorm, text)
            },
            onUpdateText = { textBox, newText ->
                viewModel.updateTextBox(textBox.copy(text = newText), recordUndo = true)
            },
            onDeleteText = { id ->
                viewModel.deleteAnnotationById(id)
            }
        )

        // --- EXPORT PROGRESS MODAL DIALOG ---
        ExportProgressDialog(
            isExporting = isExporting
        )

        // --- DISCARD CONFIRMATION DIALOG ---
        DiscardConfirmDialog(
            visible = showDiscardConfirmDialog,
            onConfirmDiscard = { viewModel.discardAndExitEditMode() },
            onDismiss = { showDiscardConfirmDialog = false }
        )

        // --- SAVED SIGNATURE PICKER DIALOG ---
        if (showSavedSignatureDialog) {
            savedSignature?.let { sig ->
                SavedSignaturePickerDialog(
                    savedSignature = sig,
                    onUse = {
                        val currentPage = pdfViewRef?.currentPage ?: 0
                        viewModel.placeSavedSignature(currentPage)
                        showSavedSignatureDialog = false
                    },
                    onCreateNew = {
                        showSavedSignatureDialog = false
                        showCreateSignatureDialog = true
                    },
                    onDeleteSaved = {
                        viewModel.deleteSavedSignature()
                        showSavedSignatureDialog = false
                    },
                    onDismiss = {
                        showSavedSignatureDialog = false
                    }
                )
            }
        }

        // --- CREATE SIGNATURE PAD DIALOG ---
        if (showCreateSignatureDialog) {
            CreateSignatureDialog(
                onSave = { sig ->
                    viewModel.saveNewSignature(sig)
                    val currentPage = pdfViewRef?.currentPage ?: 0
                    viewModel.placeSavedSignature(currentPage)
                    showCreateSignatureDialog = false
                },
                onDismiss = {
                    showCreateSignatureDialog = false
                }
            )
        }
    }
}
