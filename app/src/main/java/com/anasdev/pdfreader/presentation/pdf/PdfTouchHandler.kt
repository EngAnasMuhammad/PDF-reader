package com.anasdev.pdfreader.presentation.pdf

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import com.anasdev.pdfreader.domain.model.AnnotationTool
import com.anasdev.pdfreader.domain.model.NormalizedPoint
import com.anasdev.pdfreader.domain.model.PdfAnnotation
import com.anasdev.pdfreader.domain.model.Signature
import com.anasdev.pdfreader.domain.model.TextBox
import com.anasdev.pdfreader.domain.model.TextHighlight
import com.anasdev.pdfreader.presentation.state.PdfViewModel
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.PagePoint
import kotlin.math.hypot

sealed interface TextDialogState {
    data class Add(val pageIndex: Int, val xNorm: Float, val yNorm: Float) : TextDialogState
    data class Edit(val textBox: TextBox) : TextDialogState
}

private enum class TextTouchMode {
    NONE,
    PENDING_TAP,
    DRAGGING_BOX,
    RESIZING_BOX
}

private enum class SignatureTouchMode {
    NONE,
    PENDING_TAP,
    DRAGGING_SIGNATURE,
    RESIZING_SIGNATURE
}

class AnnotationStateHolder {
    var annotationsByPage: Map<Int, List<PdfAnnotation>> = emptyMap()
    var isEditMode: Boolean = false
    var activeTool: AnnotationTool = AnnotationTool.NONE
    var selectedColorArgb: Int = PdfViewModel.DEFAULT_PEN_COLOR
    var strokeWidthRatio: Float = PdfViewModel.DEFAULT_PEN_WIDTH
    var activeStroke: ActiveStroke? = null
    var selectedAnnotationId: String? = null
    var textFontSizeRatio: Float = PdfViewModel.DEFAULT_TEXT_FONT_SIZE
    var onCanvasTouch: (() -> Unit)? = null
    var onOpenAddText: ((pageIndex: Int, xNorm: Float, yNorm: Float) -> Unit)? = null
    var onOpenEditText: ((TextBox) -> Unit)? = null
    var onSelectHighlight: ((TextHighlight, screenX: Float, screenY: Float) -> Unit)? = null
    var onDismissHighlight: (() -> Unit)? = null

    fun findTextBox(id: String): TextBox? {
        return annotationsByPage.values.flatten().firstOrNull { it.id == id } as? TextBox
    }

    fun findSignature(id: String): Signature? {
        return annotationsByPage.values.flatten().firstOrNull { it.id == id } as? Signature
    }
}

class PdfTouchHandler(
    context: Context,
    private val pdfView: PDFView,
    private val stateHolder: AnnotationStateHolder,
    private val viewModel: PdfViewModel,
    private val annotationRenderer: AnnotationRenderer,
    private val onToggleTopBar: () -> Unit,
    private val onScrollDistance: (distanceY: Float) -> Unit
) : FrameLayout(context) {

    private var isMultiTouchGesture = false
    private var forwardedDownToSuper = false
    private var strokeStartTimeMs = 0L

    private var eraserDownX = 0f
    private var eraserDownY = 0f
    private var pendingEraserPoint: PagePoint? = null
    private var isDraggingEraser = false

    private var textDownX = 0f
    private var textDownY = 0f
    private var dragStartNormX = 0f
    private var dragStartNormY = 0f
    private var initialBoxXNorm = 0f
    private var initialBoxYNorm = 0f
    private var initialBoxWidthNorm = 0f
    private var initialBoxForTransform: TextBox? = null
    private var pendingTextPoint: PagePoint? = null
    private var isDraggingText = false
    private var textTouchMode = TextTouchMode.NONE

    private var sigDownX = 0f
    private var sigDownY = 0f
    private var initialSigXNorm = 0f
    private var initialSigYNorm = 0f
    private var initialSigWidthNorm = 0f
    private var initialSigForTransform: Signature? = null
    private var pendingSigPoint: PagePoint? = null
    private var isDraggingSig = false
    private var sigTouchMode = SignatureTouchMode.NONE

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

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
            if (!stateHolder.isEditMode) {
                // If text selection is currently active, tapping dismisses it
                if (pdfView.textSelectionManager != null && pdfView.textSelectionManager.isSelectionActive) {
                    stateHolder.onDismissHighlight?.invoke()
                    return super.onSingleTapConfirmed(e)
                }

                // Hit test for existing TextHighlight at tap position
                val pagePoint = pdfView.mapScreenPointToPage(e.x, e.y)
                if (pagePoint != null) {
                    val annotations = stateHolder.annotationsByPage[pagePoint.pageIndex] ?: emptyList()
                    val highlight = annotationRenderer.findTextHighlightAt(
                        pagePoint.pageIndex,
                        pagePoint.normalizedX,
                        pagePoint.normalizedY,
                        annotations
                    )
                    if (highlight != null) {
                        stateHolder.onSelectHighlight?.invoke(highlight, e.x, e.y)
                        return true
                    }
                }
                stateHolder.onDismissHighlight?.invoke()
                onToggleTopBar()
            }
            return super.onSingleTapConfirmed(e)
        }

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            if (!stateHolder.isEditMode) {
                stateHolder.onDismissHighlight?.invoke()
                onScrollDistance(distanceY)
            }
            return super.onScroll(e1, e2, distanceX, distanceY)
        }
    })

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Default reading mode navigation when not editing or when Pan/Hand tool is selected
        if (!stateHolder.isEditMode || stateHolder.activeTool == AnnotationTool.NONE) {
            gestureDetector.onTouchEvent(ev)
            return super.dispatchTouchEvent(ev)
        }

        val actionMasked = ev.actionMasked

        // Auto-dismiss style popover on canvas touch down
        if (actionMasked == MotionEvent.ACTION_DOWN) {
            stateHolder.onCanvasTouch?.invoke()
        }

        // --- ACTIVE DRAWING / ERASING / TEXT GESTURE ROUTING ---

        // 1. Multi-Touch Transition & 250ms Long-Stroke Guard
        if ((actionMasked == MotionEvent.ACTION_POINTER_DOWN || ev.pointerCount >= 2) && !isMultiTouchGesture) {
            val currentStroke = stateHolder.activeStroke
            if (currentStroke != null) {
                val elapsedMs = ev.eventTime - strokeStartTimeMs
                if (elapsedMs >= 250L && currentStroke.points.size >= 4) {
                    // Deliberate stroke was drawn before brushing second finger -> commit it
                    viewModel.commitStroke(currentStroke.pageIndex, currentStroke.points.toList())
                }
                stateHolder.activeStroke = null
                pdfView.invalidate()
            }

            // Cancel pending eraser tap
            pendingEraserPoint = null
            isDraggingEraser = false

            // Commit any in-progress text transform before pinch starts
            if (textTouchMode == TextTouchMode.DRAGGING_BOX || textTouchMode == TextTouchMode.RESIZING_BOX) {
                val orig = initialBoxForTransform
                if (orig != null) {
                    val finalBox = stateHolder.findTextBox(orig.id)
                    if (finalBox != null && (finalBox.xNorm != orig.xNorm || finalBox.yNorm != orig.yNorm || finalBox.widthNorm != orig.widthNorm)) {
                        viewModel.commitTextBoxTransform(orig, finalBox)
                    }
                }
            }
            textTouchMode = TextTouchMode.NONE
            initialBoxForTransform = null
            pendingTextPoint = null
            isDraggingText = false

            // Commit any in-progress signature transform before pinch starts
            if (sigTouchMode == SignatureTouchMode.DRAGGING_SIGNATURE || sigTouchMode == SignatureTouchMode.RESIZING_SIGNATURE) {
                val orig = initialSigForTransform
                if (orig != null) {
                    val finalSig = stateHolder.findSignature(orig.id)
                    if (finalSig != null && (finalSig.xNorm != orig.xNorm || finalSig.yNorm != orig.yNorm || finalSig.widthNorm != orig.widthNorm)) {
                        viewModel.commitSignatureTransform(orig, finalSig)
                    }
                }
            }
            sigTouchMode = SignatureTouchMode.NONE
            initialSigForTransform = null
            pendingSigPoint = null
            isDraggingSig = false

            isMultiTouchGesture = true

            // Synthesize ACTION_DOWN if previous touch wasn't forwarded to super
            if (!forwardedDownToSuper) {
                val syntheticDown = MotionEvent.obtain(
                    ev.eventTime,
                    ev.eventTime,
                    MotionEvent.ACTION_DOWN,
                    ev.getX(0),
                    ev.getY(0),
                    ev.metaState
                )
                super.dispatchTouchEvent(syntheticDown)
                syntheticDown.recycle()
                forwardedDownToSuper = true
            }

            return super.dispatchTouchEvent(ev)
        }

        // 2. While in multi-touch gesture, forward everything to PDFView
        if (isMultiTouchGesture) {
            val result = super.dispatchTouchEvent(ev)
            if (actionMasked == MotionEvent.ACTION_UP || actionMasked == MotionEvent.ACTION_CANCEL) {
                isMultiTouchGesture = false
                forwardedDownToSuper = false
            }
            return result
        }

        val tool = stateHolder.activeTool

        // 3. Single-finger Pen or Highlighter drawing
        if (tool == AnnotationTool.PEN || tool == AnnotationTool.HIGHLIGHTER) {
            when (actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isMultiTouchGesture = false
                    forwardedDownToSuper = false
                    strokeStartTimeMs = ev.eventTime

                    val pagePoint = pdfView.mapScreenPointToPage(ev.x, ev.y)
                    if (pagePoint != null) {
                        stateHolder.activeStroke = ActiveStroke(
                            pageIndex = pagePoint.pageIndex,
                            points = mutableListOf(NormalizedPoint.of(pagePoint.normalizedX, pagePoint.normalizedY)),
                            colorArgb = stateHolder.selectedColorArgb,
                            strokeWidthRatio = stateHolder.strokeWidthRatio,
                            isHighlighter = (tool == AnnotationTool.HIGHLIGHTER)
                        )
                        pdfView.invalidate()
                        return true
                    } else {
                        // Touch on blank margin: allow scrolling
                        forwardedDownToSuper = true
                        return super.dispatchTouchEvent(ev)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val currentStroke = stateHolder.activeStroke
                    if (currentStroke != null) {
                        val pagePoint = pdfView.mapScreenPointToPage(ev.x, ev.y)
                        if (pagePoint != null && pagePoint.pageIndex == currentStroke.pageIndex) {
                            val lastPoint = currentStroke.points.lastOrNull()
                            val dx = pagePoint.normalizedX - (lastPoint?.x ?: 0f)
                            val dy = pagePoint.normalizedY - (lastPoint?.y ?: 0f)
                            // Filter micro-moves < 0.002f distance to optimize memory and rendering
                            if (lastPoint == null || (dx * dx + dy * dy) >= 0.002f * 0.002f) {
                                currentStroke.points.add(NormalizedPoint.of(pagePoint.normalizedX, pagePoint.normalizedY))
                                pdfView.invalidate()
                            }
                        }
                        return true
                    }
                    if (forwardedDownToSuper) return super.dispatchTouchEvent(ev)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val currentStroke = stateHolder.activeStroke
                    if (currentStroke != null) {
                        val pagePoint = pdfView.mapScreenPointToPage(ev.x, ev.y)
                        if (pagePoint != null && pagePoint.pageIndex == currentStroke.pageIndex) {
                            currentStroke.points.add(NormalizedPoint.of(pagePoint.normalizedX, pagePoint.normalizedY))
                        }
                        viewModel.commitStroke(currentStroke.pageIndex, currentStroke.points.toList())
                        stateHolder.activeStroke = null
                        pdfView.invalidate()
                        return true
                    }
                    if (forwardedDownToSuper) {
                        forwardedDownToSuper = false
                        return super.dispatchTouchEvent(ev)
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (stateHolder.activeStroke != null) {
                        stateHolder.activeStroke = null
                        pdfView.invalidate()
                    }
                    if (forwardedDownToSuper) {
                        forwardedDownToSuper = false
                        return super.dispatchTouchEvent(ev)
                    }
                    return true
                }
            }
        } else if (tool == AnnotationTool.ERASER) {
            when (actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isMultiTouchGesture = false
                    forwardedDownToSuper = false
                    eraserDownX = ev.x
                    eraserDownY = ev.y
                    isDraggingEraser = false
                    pendingEraserPoint = pdfView.mapScreenPointToPage(ev.x, ev.y)

                    if (pendingEraserPoint != null) {
                        // Hold off erasing until drag or tap-up to protect pinch-to-zoom
                        return true
                    } else {
                        forwardedDownToSuper = true
                        return super.dispatchTouchEvent(ev)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val dist = hypot(ev.x - eraserDownX, ev.y - eraserDownY)
                    if (!isDraggingEraser && dist > touchSlop) {
                        isDraggingEraser = true
                        pendingEraserPoint?.let {
                            viewModel.eraseStrokeAt(it.pageIndex, NormalizedPoint.of(it.normalizedX, it.normalizedY))
                        }
                        pendingEraserPoint = null
                    }

                    if (isDraggingEraser) {
                        val pagePoint = pdfView.mapScreenPointToPage(ev.x, ev.y)
                        if (pagePoint != null) {
                            viewModel.eraseStrokeAt(pagePoint.pageIndex, NormalizedPoint.of(pagePoint.normalizedX, pagePoint.normalizedY))
                        }
                        return true
                    }

                    if (forwardedDownToSuper) return super.dispatchTouchEvent(ev)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isMultiTouchGesture && !isDraggingEraser) {
                        // Tap-to-erase
                        pendingEraserPoint?.let {
                            viewModel.eraseStrokeAt(it.pageIndex, NormalizedPoint.of(it.normalizedX, it.normalizedY))
                        }
                    }
                    pendingEraserPoint = null
                    isDraggingEraser = false
                    if (forwardedDownToSuper) {
                        forwardedDownToSuper = false
                        return super.dispatchTouchEvent(ev)
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    pendingEraserPoint = null
                    isDraggingEraser = false
                    if (forwardedDownToSuper) {
                        forwardedDownToSuper = false
                        return super.dispatchTouchEvent(ev)
                    }
                    return true
                }
            }
        } else if (tool == AnnotationTool.TEXT) {
            when (actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isMultiTouchGesture = false
                    forwardedDownToSuper = false
                    textDownX = ev.x
                    textDownY = ev.y
                    isDraggingText = false
                    textTouchMode = TextTouchMode.NONE
                    initialBoxForTransform = null
                    pendingTextPoint = null

                    // 1. Check if user grabbed the resize handle of the currently selected TextBox
                    val currentSelectedId = stateHolder.selectedAnnotationId
                    val selectedBox = if (currentSelectedId != null) stateHolder.findTextBox(currentSelectedId) else null
                    if (selectedBox != null) {
                        val pageSize = pdfView.getPageSize(selectedBox.pageIndex)
                        val bounds = annotationRenderer.computeTextBoxBoundsNorm(selectedBox, pageSize.width, pageSize.height)
                        val handlePt = pdfView.mapPagePointToScreen(selectedBox.pageIndex, bounds.right, bounds.bottom)
                        val handleRadiusPx = 36f * context.resources.displayMetrics.density
                        if (handlePt != null && hypot(ev.x - handlePt.x, ev.y - handlePt.y) <= handleRadiusPx) {
                            textTouchMode = TextTouchMode.RESIZING_BOX
                            initialBoxForTransform = selectedBox
                            dragStartNormX = bounds.right
                            initialBoxWidthNorm = selectedBox.widthNorm
                            return true
                        }
                    }

                    // 2. Map screen touch point to page
                    val pagePoint = pdfView.mapScreenPointToPage(ev.x, ev.y)
                    if (pagePoint == null) {
                        // Blank margin: allow 1-finger scroll
                        forwardedDownToSuper = true
                        return super.dispatchTouchEvent(ev)
                    }

                    // 3. Check if user touched inside any TextBox on this page
                    val pageSize = pdfView.getPageSize(pagePoint.pageIndex)
                    val pageBoxes = stateHolder.annotationsByPage[pagePoint.pageIndex]?.filterIsInstance<TextBox>() ?: emptyList()
                    val hitBox = pageBoxes.asReversed().firstOrNull { box ->
                        val b = annotationRenderer.computeTextBoxBoundsNorm(box, pageSize.width, pageSize.height)
                        val tolX = if (pageSize.width > 0) (8f * context.resources.displayMetrics.density) / pageSize.width else 0.02f
                        val tolY = if (pageSize.height > 0) (8f * context.resources.displayMetrics.density) / pageSize.height else 0.02f
                        pagePoint.normalizedX >= (b.left - tolX) && pagePoint.normalizedX <= (b.right + tolX) &&
                                pagePoint.normalizedY >= (b.top - tolY) && pagePoint.normalizedY <= (b.bottom + tolY)
                    }

                    if (hitBox != null) {
                        textTouchMode = TextTouchMode.PENDING_TAP
                        initialBoxForTransform = hitBox
                        dragStartNormX = pagePoint.normalizedX
                        dragStartNormY = pagePoint.normalizedY
                        initialBoxXNorm = hitBox.xNorm
                        initialBoxYNorm = hitBox.yNorm
                        return true
                    }

                    // 4. Empty space on page: tap will deselect or add text
                    textTouchMode = TextTouchMode.PENDING_TAP
                    pendingTextPoint = pagePoint
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (forwardedDownToSuper) return super.dispatchTouchEvent(ev)

                    val dist = hypot(ev.x - textDownX, ev.y - textDownY)
                    if (!isDraggingText && dist > touchSlop) {
                        isDraggingText = true
                        if (textTouchMode == TextTouchMode.PENDING_TAP) {
                            val box = initialBoxForTransform
                            if (box != null) {
                                textTouchMode = TextTouchMode.DRAGGING_BOX
                                if (stateHolder.selectedAnnotationId != box.id) {
                                    viewModel.selectAnnotation(box.id)
                                }
                            } else {
                                // Dragged on empty area: cancel tap and transition to scrolling
                                textTouchMode = TextTouchMode.NONE
                                pendingTextPoint = null
                                forwardedDownToSuper = true
                                val synthDown = MotionEvent.obtain(
                                    ev.eventTime,
                                    ev.eventTime,
                                    MotionEvent.ACTION_DOWN,
                                    ev.x,
                                    ev.y,
                                    ev.metaState
                                )
                                super.dispatchTouchEvent(synthDown)
                                synthDown.recycle()
                                return super.dispatchTouchEvent(ev)
                            }
                        }
                    }

                    if (textTouchMode == TextTouchMode.DRAGGING_BOX) {
                        val box = initialBoxForTransform
                        if (box != null) {
                            val pagePoint = pdfView.mapScreenPointToPage(ev.x, ev.y)
                            if (pagePoint != null && pagePoint.pageIndex == box.pageIndex) {
                                val deltaX = pagePoint.normalizedX - dragStartNormX
                                val deltaY = pagePoint.normalizedY - dragStartNormY
                                val newX = (initialBoxXNorm + deltaX).coerceIn(0f, 1f - box.widthNorm)
                                val newY = (initialBoxYNorm + deltaY).coerceIn(0f, 0.98f)
                                val currentBox = stateHolder.findTextBox(box.id) ?: box
                                val updated = currentBox.copy(xNorm = newX, yNorm = newY)
                                viewModel.updateTextBox(updated, recordUndo = false)
                                pdfView.invalidate()
                            }
                        }
                        return true
                    } else if (textTouchMode == TextTouchMode.RESIZING_BOX) {
                        val box = initialBoxForTransform
                        if (box != null) {
                            val pagePoint = pdfView.mapScreenPointToPage(ev.x, ev.y)
                            if (pagePoint != null && pagePoint.pageIndex == box.pageIndex) {
                                val deltaWidth = pagePoint.normalizedX - dragStartNormX
                                val maxW = (1f - box.xNorm).coerceAtMost(PdfViewModel.MAX_TEXT_WIDTH)
                                val newWidth = (initialBoxWidthNorm + deltaWidth).coerceIn(PdfViewModel.MIN_TEXT_WIDTH, maxW)
                                val currentBox = stateHolder.findTextBox(box.id) ?: box
                                val updated = currentBox.copy(widthNorm = newWidth)
                                viewModel.updateTextBox(updated, recordUndo = false)
                                pdfView.invalidate()
                            }
                        }
                        return true
                    }

                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (forwardedDownToSuper) {
                        forwardedDownToSuper = false
                        return super.dispatchTouchEvent(ev)
                    }

                    when (textTouchMode) {
                        TextTouchMode.DRAGGING_BOX, TextTouchMode.RESIZING_BOX -> {
                            val orig = initialBoxForTransform
                            if (orig != null) {
                                val finalBox = stateHolder.findTextBox(orig.id)
                                if (finalBox != null && (finalBox.xNorm != orig.xNorm || finalBox.yNorm != orig.yNorm || finalBox.widthNorm != orig.widthNorm)) {
                                    viewModel.commitTextBoxTransform(orig, finalBox)
                                }
                            }
                        }
                        TextTouchMode.PENDING_TAP -> {
                            val box = initialBoxForTransform
                            if (box != null) {
                                if (box.id == stateHolder.selectedAnnotationId) {
                                    // Quick tap on already selected text box: open edit dialog
                                    stateHolder.onOpenEditText?.invoke(box)
                                } else {
                                    // Tap to select
                                    viewModel.selectAnnotation(box.id)
                                }
                            } else {
                                val pt = pendingTextPoint
                                if (pt != null) {
                                    if (stateHolder.selectedAnnotationId != null) {
                                        // Tap on blank area deselects
                                        viewModel.selectAnnotation(null)
                                    } else {
                                        // Open Add Text dialog
                                        stateHolder.onOpenAddText?.invoke(pt.pageIndex, pt.normalizedX, pt.normalizedY)
                                    }
                                }
                            }
                        }
                        TextTouchMode.NONE -> Unit
                    }

                    textTouchMode = TextTouchMode.NONE
                    initialBoxForTransform = null
                    pendingTextPoint = null
                    isDraggingText = false
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    textTouchMode = TextTouchMode.NONE
                    initialBoxForTransform = null
                    pendingTextPoint = null
                    isDraggingText = false
                    if (forwardedDownToSuper) {
                        forwardedDownToSuper = false
                        return super.dispatchTouchEvent(ev)
                    }
                    return true
                }
            }
        } else if (tool == AnnotationTool.SIGNATURE) {
            when (actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isMultiTouchGesture = false
                    forwardedDownToSuper = false
                    sigDownX = ev.x
                    sigDownY = ev.y
                    isDraggingSig = false
                    sigTouchMode = SignatureTouchMode.NONE
                    initialSigForTransform = null
                    pendingSigPoint = null

                    // 1. Check if user grabbed the resize handle of the currently selected Signature
                    val currentSelectedId = stateHolder.selectedAnnotationId
                    val selectedSig = if (currentSelectedId != null) stateHolder.findSignature(currentSelectedId) else null
                    if (selectedSig != null) {
                        val pageSize = pdfView.getPageSize(selectedSig.pageIndex)
                        val bounds = annotationRenderer.computeSignatureBoundsNorm(selectedSig, pageSize.width, pageSize.height)
                        val handlePt = pdfView.mapPagePointToScreen(selectedSig.pageIndex, bounds.right, bounds.bottom)
                        val handleRadiusPx = 36f * context.resources.displayMetrics.density
                        if (handlePt != null && hypot(ev.x - handlePt.x, ev.y - handlePt.y) <= handleRadiusPx) {
                            sigTouchMode = SignatureTouchMode.RESIZING_SIGNATURE
                            initialSigForTransform = selectedSig
                            dragStartNormX = bounds.right
                            initialSigWidthNorm = selectedSig.widthNorm
                            return true
                        }
                    }

                    // 2. Map screen touch point to page
                    val pagePoint = pdfView.mapScreenPointToPage(ev.x, ev.y)
                    if (pagePoint == null) {
                        // Blank margin: allow 1-finger scroll
                        forwardedDownToSuper = true
                        return super.dispatchTouchEvent(ev)
                    }

                    // 3. Check if user touched inside any Signature on this page
                    val pageSize = pdfView.getPageSize(pagePoint.pageIndex)
                    val pageSigs = stateHolder.annotationsByPage[pagePoint.pageIndex]?.filterIsInstance<Signature>() ?: emptyList()
                    val hitSig = pageSigs.asReversed().firstOrNull { sig ->
                        val b = annotationRenderer.computeSignatureBoundsNorm(sig, pageSize.width, pageSize.height)
                        val tolX = if (pageSize.width > 0) (8f * context.resources.displayMetrics.density) / pageSize.width else 0.02f
                        val tolY = if (pageSize.height > 0) (8f * context.resources.displayMetrics.density) / pageSize.height else 0.02f
                        pagePoint.normalizedX >= (b.left - tolX) && pagePoint.normalizedX <= (b.right + tolX) &&
                                pagePoint.normalizedY >= (b.top - tolY) && pagePoint.normalizedY <= (b.bottom + tolY)
                    }

                    if (hitSig != null) {
                        sigTouchMode = SignatureTouchMode.PENDING_TAP
                        initialSigForTransform = hitSig
                        dragStartNormX = pagePoint.normalizedX
                        dragStartNormY = pagePoint.normalizedY
                        initialSigXNorm = hitSig.xNorm
                        initialSigYNorm = hitSig.yNorm
                        return true
                    }

                    // 4. Empty space on page: tap will deselect
                    sigTouchMode = SignatureTouchMode.PENDING_TAP
                    pendingSigPoint = pagePoint
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (forwardedDownToSuper) return super.dispatchTouchEvent(ev)

                    val dist = hypot(ev.x - sigDownX, ev.y - sigDownY)
                    if (!isDraggingSig && dist > touchSlop) {
                        isDraggingSig = true
                        if (sigTouchMode == SignatureTouchMode.PENDING_TAP) {
                            val sig = initialSigForTransform
                            if (sig != null) {
                                sigTouchMode = SignatureTouchMode.DRAGGING_SIGNATURE
                                if (stateHolder.selectedAnnotationId != sig.id) {
                                    viewModel.selectAnnotation(sig.id)
                                }
                            } else {
                                // Dragged on empty area: cancel tap and transition to scrolling
                                sigTouchMode = SignatureTouchMode.NONE
                                pendingSigPoint = null
                                forwardedDownToSuper = true
                                val synthDown = MotionEvent.obtain(
                                    ev.eventTime,
                                    ev.eventTime,
                                    MotionEvent.ACTION_DOWN,
                                    ev.x,
                                    ev.y,
                                    ev.metaState
                                )
                                super.dispatchTouchEvent(synthDown)
                                synthDown.recycle()
                                return super.dispatchTouchEvent(ev)
                            }
                        }
                    }

                    if (sigTouchMode == SignatureTouchMode.DRAGGING_SIGNATURE) {
                        val sig = initialSigForTransform
                        if (sig != null) {
                            val pagePoint = pdfView.mapScreenPointToPage(ev.x, ev.y)
                            if (pagePoint != null && pagePoint.pageIndex == sig.pageIndex) {
                                val pageSize = pdfView.getPageSize(sig.pageIndex)
                                val b = annotationRenderer.computeSignatureBoundsNorm(sig, pageSize.width, pageSize.height)
                                val sigHeightNorm = b.height()
                                val deltaX = pagePoint.normalizedX - dragStartNormX
                                val deltaY = pagePoint.normalizedY - dragStartNormY
                                val newX = (initialSigXNorm + deltaX).coerceIn(0f, (1f - sig.widthNorm).coerceAtLeast(0f))
                                val newY = (initialSigYNorm + deltaY).coerceIn(0f, (1f - sigHeightNorm).coerceAtLeast(0f))
                                val currentSig = stateHolder.findSignature(sig.id) ?: sig
                                val updated = currentSig.copy(xNorm = newX, yNorm = newY)
                                viewModel.updateSignature(updated, recordUndo = false)
                                pdfView.invalidate()
                            }
                        }
                        return true
                    } else if (sigTouchMode == SignatureTouchMode.RESIZING_SIGNATURE) {
                        val sig = initialSigForTransform
                        if (sig != null) {
                            val pagePoint = pdfView.mapScreenPointToPage(ev.x, ev.y)
                            if (pagePoint != null && pagePoint.pageIndex == sig.pageIndex) {
                                val deltaWidth = pagePoint.normalizedX - dragStartNormX
                                val maxW = (1f - sig.xNorm).coerceAtMost(0.90f)
                                val newWidth = (initialSigWidthNorm + deltaWidth).coerceIn(0.10f, maxW)
                                val currentSig = stateHolder.findSignature(sig.id) ?: sig
                                val updated = currentSig.copy(widthNorm = newWidth)
                                viewModel.updateSignature(updated, recordUndo = false)
                                pdfView.invalidate()
                            }
                        }
                        return true
                    }

                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (forwardedDownToSuper) {
                        forwardedDownToSuper = false
                        return super.dispatchTouchEvent(ev)
                    }

                    when (sigTouchMode) {
                        SignatureTouchMode.DRAGGING_SIGNATURE, SignatureTouchMode.RESIZING_SIGNATURE -> {
                            val orig = initialSigForTransform
                            if (orig != null) {
                                val finalSig = stateHolder.findSignature(orig.id)
                                if (finalSig != null && (finalSig.xNorm != orig.xNorm || finalSig.yNorm != orig.yNorm || finalSig.widthNorm != orig.widthNorm)) {
                                    viewModel.commitSignatureTransform(orig, finalSig)
                                }
                            }
                        }
                        SignatureTouchMode.PENDING_TAP -> {
                            val sig = initialSigForTransform
                            if (sig != null) {
                                if (stateHolder.selectedAnnotationId != sig.id) {
                                    viewModel.selectAnnotation(sig.id)
                                }
                            } else {
                                if (stateHolder.selectedAnnotationId != null) {
                                    viewModel.selectAnnotation(null)
                                }
                            }
                        }
                        SignatureTouchMode.NONE -> Unit
                    }

                    sigTouchMode = SignatureTouchMode.NONE
                    initialSigForTransform = null
                    pendingSigPoint = null
                    isDraggingSig = false
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    sigTouchMode = SignatureTouchMode.NONE
                    initialSigForTransform = null
                    pendingSigPoint = null
                    isDraggingSig = false
                    if (forwardedDownToSuper) {
                        forwardedDownToSuper = false
                        return super.dispatchTouchEvent(ev)
                    }
                    return true
                }
            }
        }

        return super.dispatchTouchEvent(ev)
    }
}
