package com.example.pdfreader.presentation.ui.screens.pdfScreen

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pdfreader.R
import com.example.pdfreader.presentation.model.ReadingMode
import com.example.pdfreader.presentation.state.PdfViewModel
import com.example.pdfreader.presentation.utils.PdfColorFilters
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

    val backgroundColor = MaterialTheme.colorScheme.background.toArgb()

    var showMenu by remember { mutableStateOf(false) }
    var showNightModeSubMenu by remember { mutableStateOf(false) }
    var currentReadingMode by remember { mutableStateOf(ReadingMode.NORMAL) }

    val localView = LocalView.current
    DisposableEffect(keepScreenAwake) {
        if (keepScreenAwake) {
            localView.keepScreenOn = true
        }
        onDispose {
            localView.keepScreenOn = false
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = pdfTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onClickBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back) // Localized
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
                            contentDescription = stringResource(R.string.more_options) // Localized
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (!showNightModeSubMenu) {
                            // MAIN MENU
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.reading_mode)) },
                                onClick = { showNightModeSubMenu = true }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.setting)) },
                                onClick = {
                                    showMenu = false
                                    onClickSetting()
                                }
                            )
                        } else {
                            // SUB-MENU
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.back)) },
                                // FIX: Use standard RTL-aware icon instead of hardcoded text
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                },
                                onClick = { showNightModeSubMenu = false }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.normal_filter)) },
                                onClick = {
                                    currentReadingMode = ReadingMode.NORMAL
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sepia_filter)) },
                                onClick = {
                                    currentReadingMode = ReadingMode.SEPIA
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.dim_filter)) },
                                onClick = {
                                    currentReadingMode = ReadingMode.DIMMED
                                    showMenu = false
                                }
                            )
                        }
                    }
                }
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
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
                                .pageFitPolicy(FitPolicy.WIDTH)
                                .fitEachPage(true)
                                .scrollHandle(DefaultScrollHandle(context))
                                .defaultPage(startPage)
                                .onPageChange { page, _ ->
                                    viewModel.saveCurrentPage(page)
                                }
                                .load()
                        }

                        wrapper
                    },
                    update = { wrapper ->
                        val pdfView = wrapper.getChildAt(0) as PDFView

                        when (currentReadingMode) {
                            ReadingMode.NORMAL -> {
                                pdfView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                            }
                            ReadingMode.SEPIA -> {
                                pdfView.setLayerType(View.LAYER_TYPE_HARDWARE, PdfColorFilters.sepiaPaint)
                            }
                            ReadingMode.DIMMED -> {
                                pdfView.setLayerType(View.LAYER_TYPE_HARDWARE, PdfColorFilters.dimmedPaint)
                            }
                        }
                    }
                )
            }
        }
    }
}