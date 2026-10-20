package com.anasdev.pdfreader.presentation.ui.screens.pdfScreen

import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anasdev.pdfreader.R
import com.anasdev.pdfreader.domain.model.ReadingMode
import com.anasdev.pdfreader.presentation.state.PdfViewModel
import com.anasdev.pdfreader.presentation.utils.PdfColorFilters
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import com.github.barteksc.pdfviewer.util.FitPolicy


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfScreen(
    viewModel: PdfViewModel,
    onClickBack: () -> Unit,
    onClickSetting: ()-> Unit
) {
    val pdfUri by viewModel.pdfUri.collectAsStateWithLifecycle()
    val pdfTitle by viewModel.pdfTitle.collectAsStateWithLifecycle()
    val startPage by viewModel.startPage.collectAsStateWithLifecycle()
    val keepScreenAwake by viewModel.keepScreenAwake.collectAsStateWithLifecycle()
    val isHorizontalScroll by viewModel.isHorizontalScroll.collectAsStateWithLifecycle()
    val readingMode by viewModel.readingMode.collectAsStateWithLifecycle()

    val backgroundColor = MaterialTheme.colorScheme.background.toArgb()

    var showMenu by remember { mutableStateOf(false) }
    var showNightModeSubMenu by remember { mutableStateOf(false) }
    var pdfViewRef by remember { mutableStateOf<PDFView?>(null) }

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
                viewModel.saveCurrentPage(pdfView.currentPage)
                pdfView.recycle() // Cleanly free C++ native PDFium document memory & bitmap caches
            }
        }
    }

    // Apply color filter to the entire Scaffold (which spans edge-to-edge covering system bars)
    var isTopBarVisible by remember { mutableStateOf(true) }
    val bgColor = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .graphicsLayer {
                colorFilter = when (readingMode) {
                    ReadingMode.NORMAL -> null
                    ReadingMode.SEPIA -> PdfColorFilters.sepiaColorFilter
                    ReadingMode.DIMMED -> PdfColorFilters.dimmedColorFilter
                }
            }
    ) {
        pdfUri?.let { uri ->
            AndroidView(
                modifier = Modifier.fillMaxSize(), // No padding here! Native PDF engine handles it via documentTopPadding(100)
                factory = { context ->
                        val pdfView = PDFView(context, null).apply {
                            this.minZoom = 0.5f
                            this.midZoom = 1.0f
                            this.maxZoom = 8.0f
                            this.setBackgroundColor(backgroundColor)
                        }
                        pdfViewRef = pdfView

                        val wrapper = object : FrameLayout(context) {
                            private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                                override fun onDoubleTap(e: MotionEvent): Boolean {
                                    val currentZoom = pdfView.zoom
                                    if (currentZoom < 0.94f) {
                                        pdfView.zoomWithAnimation(e.x, e.y, 0.95f)
                                    } else if (currentZoom < 2.5f) {
                                        pdfView.zoomWithAnimation(e.x, e.y, 3.0f)
                                    } else {
                                        pdfView.zoomWithAnimation(e.x, e.y, 0.95f)
                                    }
                                    return true
                                }

                                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                                    isTopBarVisible = !isTopBarVisible
                                    return super.onSingleTapConfirmed(e)
                                }
                                
                                override fun onScroll(
                                    e1: MotionEvent?,
                                    e2: MotionEvent,
                                    distanceX: Float,
                                    distanceY: Float
                                ): Boolean {
                                    if (distanceY > 10 && isTopBarVisible) {
                                        isTopBarVisible = false
                                    } else if (distanceY < -10 && !isTopBarVisible) {
                                        isTopBarVisible = true
                                    }
                                    return super.onScroll(e1, e2, distanceX, distanceY)
                                }
                            })

                            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                                gestureDetector.onTouchEvent(ev)
                                return super.dispatchTouchEvent(ev)
                            }
                        }

                        wrapper.addView(pdfView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

                        pdfView.apply {
                            fromUri(uri)
                                .enableSwipe(true)
                                .swipeHorizontal(isHorizontalScroll)
                                .enableDoubletap(false)
                                .enableAnnotationRendering(true)
                                .enableAntialiasing(true)
                                .spacing(10)
                                .documentTopPadding(100) // Native PDF internal margin (100dp) so it starts beneath TopBar!
                                .pageFitPolicy(FitPolicy.WIDTH)
                                .fitEachPage(true)
                                .scrollHandle(DefaultScrollHandle(context))
                                .defaultPage(startPage)
                                .onPageChange { page, _ ->
                                    viewModel.saveCurrentPage(page)
                                }
                                .onPageScroll { _, positionOffset ->
                                    // Smoothly hide topbar when reading downwards
                                    if (positionOffset > 0.02f && isTopBarVisible) {
                                        isTopBarVisible = false
                                    } else if (positionOffset < -0.02f && !isTopBarVisible) {
                                        isTopBarVisible = true
                                    }
                                }
                                .load()
                        }

                        wrapper
                    },
                    update = {
                        // Removed layer type updates from AndroidView to apply globally
                    }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    colorFilter = when (readingMode) {
                        ReadingMode.NORMAL -> null
                        ReadingMode.SEPIA -> PdfColorFilters.sepiaColorFilter
                        ReadingMode.DIMMED -> PdfColorFilters.dimmedColorFilter
                    }
                },
            contentAlignment = Alignment.TopCenter
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = isTopBarVisible,
                enter = androidx.compose.animation.slideInVertically(initialOffsetY = { -it }),
                exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { -it })
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = pdfTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    ),
                    navigationIcon = {
                        IconButton(onClick = {
                            pdfViewRef?.let { viewModel.saveCurrentPage(it.currentPage) }
                            onClickBack()
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            showMenu = true
                            showNightModeSubMenu = false
                        }) {
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
        }
    }
}