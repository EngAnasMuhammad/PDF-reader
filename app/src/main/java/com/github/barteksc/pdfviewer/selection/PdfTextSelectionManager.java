package com.github.barteksc.pdfviewer.selection;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Toast;

import com.github.barteksc.pdfviewer.PDFView;
import com.github.barteksc.pdfviewer.util.SizeF;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class PdfTextSelectionManager {

    private final PDFView pdfView;
    private final Context context;

    private int selectedPageIndex = -1;
    private int startCharIndex = -1;
    private int endCharIndex = -1;
    private String selectedText = "";
    private final List<RectF> cachedNormalizedRects = new ArrayList<>();
    private final List<RectF> highlightScreenRects = new ArrayList<>();

    // Teardrop handle anchor positions and hit-boxes
    private float startAnchorX = 0f;
    private float startAnchorY = 0f;
    private float endAnchorX = 0f;
    private float endAnchorY = 0f;
    private boolean hasStartHandle = false;
    private boolean hasEndHandle = false;

    private final RectF startHandleRect = new RectF();
    private final RectF endHandleRect = new RectF();

    private boolean isSelectionActive = false;
    private boolean isDraggingStartHandle = false;
    private boolean isDraggingEndHandle = false;

    private final Paint highlightPaint = new Paint();
    private final Paint handlePaint = new Paint();

    private ActionMode actionMode;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicLong currentRequestId = new AtomicLong(0);
    private ExecutorService executor;

    public interface SelectionHighlightListener {
        void onApplyHighlight(int colorArgb);
    }

    private SelectionHighlightListener highlightListener;

    public void setSelectionHighlightListener(SelectionHighlightListener listener) {
        this.highlightListener = listener;
    }

    public int getSelectedPageIndex() {
        return selectedPageIndex;
    }

    public int getStartCharIndex() {
        return startCharIndex;
    }

    public int getEndCharIndex() {
        return endCharIndex;
    }

    public List<RectF> getHighlightScreenRects() {
        return new ArrayList<>(highlightScreenRects);
    }

    public List<RectF> getNormalizedSelectionRects() {
        synchronized (cachedNormalizedRects) {
            return new ArrayList<>(cachedNormalizedRects);
        }
    }

    public PdfTextSelectionManager(PDFView pdfView) {
        this.pdfView = pdfView;
        this.context = pdfView.getContext();

        highlightPaint.setColor(Color.parseColor("#4D33B5E5")); // Semi-transparent blue
        highlightPaint.setStyle(Paint.Style.FILL);

        handlePaint.setColor(Color.parseColor("#FF007ACC")); // Solid blue
        handlePaint.setAntiAlias(true);
        handlePaint.setStyle(Paint.Style.FILL);
    }

    private synchronized ExecutorService getExecutor() {
        if (executor == null || executor.isShutdown() || executor.isTerminated()) {
            executor = Executors.newSingleThreadExecutor();
        }
        return executor;
    }

    public boolean isSelectionActive() {
        return isSelectionActive;
    }

    public boolean isDraggingHandle() {
        return isDraggingStartHandle || isDraggingEndHandle;
    }

    /**
     * Detects whether text direction is RTL based on the first strong directional character.
     * Supports Arabic, Hebrew, and other RTL scripts.
     */
    public static boolean isRtl(String text) {
        if (text == null || text.isEmpty()) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            byte dir = Character.getDirectionality(c);
            if (dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
                dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) {
                return true;
            } else if (dir == Character.DIRECTIONALITY_LEFT_TO_RIGHT) {
                return false;
            }
        }
        return false;
    }

    /**
     * Triggered on long press to select word at touch position asynchronously.
     * Keeps PDFium hit-testing completely off the main thread.
     */
    public boolean onLongPress(float touchX, float touchY) {
        if (pdfView.getPdfFile() == null) return false;
        final int pageIndex = pdfView.getCurrentPage();
        if (pageIndex < 0) return false;

        float mappedX = -pdfView.getCurrentXOffset() + touchX;
        float mappedY = -pdfView.getCurrentYOffset() + touchY;

        float zoom = pdfView.getZoom();
        SizeF pageSize = pdfView.getPdfFile().getScaledPageSize(pageIndex, zoom);
        if (pageSize.getWidth() <= 0 || pageSize.getHeight() <= 0) return false;

        float pageX, pageY;
        if (pdfView.isSwipeVertical()) {
            pageX = pdfView.getPdfFile().getSecondaryPageOffset(pageIndex, zoom);
            pageY = pdfView.getPdfFile().getPageOffset(pageIndex, zoom);
        } else {
            pageY = pdfView.getPdfFile().getSecondaryPageOffset(pageIndex, zoom);
            pageX = pdfView.getPdfFile().getPageOffset(pageIndex, zoom);
        }

        float localX = mappedX - pageX;
        float localY = mappedY - pageY;

        com.github.barteksc.pdfviewer.util.Size originalSize = pdfView.getPdfFile().getOriginalPageSize(pageIndex);
        if (originalSize.getWidth() <= 0 || originalSize.getHeight() <= 0) return false;

        final float pdfX = (localX / pageSize.getWidth()) * originalSize.getWidth();
        final float pdfY = ((pageSize.getHeight() - localY) / pageSize.getHeight()) * originalSize.getHeight();

        final long reqId = currentRequestId.incrementAndGet();

        getExecutor().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (currentRequestId.get() != reqId) return;

                    int totalChars = pdfView.getTextPageCountChars(pageIndex);
                    if (totalChars <= 0 || currentRequestId.get() != reqId) return;

                    // 1. Initial hit-test with standard tolerance
                    int charIndex = pdfView.getTextPageCharIndexAtPos(pageIndex, pdfX, pdfY, 16.0, 16.0);
                    // 2. Whitespace fallback with wider tolerance
                    if (charIndex < 0) {
                        charIndex = pdfView.getTextPageCharIndexAtPos(pageIndex, pdfX, pdfY, 32.0, 32.0);
                    }
                    if (charIndex < 0 || charIndex >= totalChars || currentRequestId.get() != reqId) return;

                    // Expand to word boundaries (strictly guarded loop)
                    int wordStart = charIndex;
                    while (wordStart > 0) {
                        String ch = pdfView.getTextPageText(pageIndex, wordStart - 1, 1);
                        if (isWordSeparator(ch)) break;
                        wordStart--;
                    }

                    int wordEnd = charIndex;
                    while (wordEnd < totalChars - 1) {
                        String ch = pdfView.getTextPageText(pageIndex, wordEnd + 1, 1);
                        if (isWordSeparator(ch)) break;
                        wordEnd++;
                    }

                    if (currentRequestId.get() != reqId) return;

                    final int finalStart = wordStart;
                    final int finalEnd = wordEnd;
                    int count = (finalEnd - finalStart) + 1;
                    final String text = pdfView.getTextPageText(pageIndex, finalStart, count);
                    final List<RectF> normRects = extractNormalizedRects(pageIndex, finalStart, finalEnd);

                    if (currentRequestId.get() != reqId) return;

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (currentRequestId.get() != reqId) return;
                            applySelection(pageIndex, finalStart, finalEnd, text, normRects);
                        }
                    });
                } catch (Throwable ignored) {}
            }
        });

        return true;
    }

    private boolean isWordSeparator(String ch) {
        if (ch == null || ch.isEmpty()) return true;
        char c = ch.charAt(0);
        return Character.isWhitespace(c) || c == '.' || c == ',' || c == '!' || c == '?' || c == ';' || c == ':';
    }

    public void setSelection(final int pageIndex, final int startIdx, final int endIdx) {
        if (pdfView.getPdfFile() == null || pageIndex < 0) return;
        final long reqId = currentRequestId.incrementAndGet();
        getExecutor().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (currentRequestId.get() != reqId) return;
                    int totalChars = pdfView.getTextPageCountChars(pageIndex);
                    if (totalChars <= 0) return;

                    int s = Math.max(0, Math.min(startIdx, endIdx));
                    int e = Math.min(totalChars - 1, Math.max(startIdx, endIdx));
                    if (s > e) return;

                    int count = (e - s) + 1;
                    final String text = pdfView.getTextPageText(pageIndex, s, count);
                    final List<RectF> normRects = extractNormalizedRects(pageIndex, s, e);

                    if (currentRequestId.get() != reqId) return;

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (currentRequestId.get() != reqId) return;
                            applySelection(pageIndex, s, e, text, normRects);
                        }
                    });
                } catch (Throwable ignored) {}
            }
        });
    }

    /**
     * Applies selection on the main thread and computes screen rects using pure float math
     * from cached normalized rects. Never acquires PdfFile.lock.
     */
    public void applySelection(int pageIndex, int startIdx, int endIdx, String text, List<RectF> normalizedRects) {
        if (startIdx > endIdx) {
            int temp = startIdx;
            startIdx = endIdx;
            endIdx = temp;
        }

        this.selectedPageIndex = pageIndex;
        this.startCharIndex = startIdx;
        this.endCharIndex = endIdx;
        this.selectedText = text != null ? text : "";
        synchronized (cachedNormalizedRects) {
            this.cachedNormalizedRects.clear();
            if (normalizedRects != null) {
                this.cachedNormalizedRects.addAll(normalizedRects);
            }
        }

        updateScreenRectsFromNormalized();

        if (!highlightScreenRects.isEmpty()) {
            this.isSelectionActive = true;
            pdfView.invalidate();
            showActionMode();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && actionMode != null && !pdfView.isAnimating() && !isDraggingHandle()) {
                try {
                    actionMode.invalidateContentRect();
                } catch (Exception ignored) {}
            }
        } else {
            clearSelection();
        }
    }

    /**
     * Extracts normalized rectangles (0f..1f relative to page) on background executor.
     * Safely executes native PDFium character bounding box calls off the main thread.
     *
     * Converts to normalized coordinates, sorts strictly top-to-bottom line order,
     * and merges contiguous character rects on the same visual line (within 0.6f * lineHeight).
     */
    private List<RectF> extractNormalizedRects(int pageIndex, int startIdx, int endIdx) {
        if (pdfView.getPdfFile() == null || pageIndex < 0 || pageIndex >= pdfView.getPageCount() || startIdx < 0 || endIdx < startIdx) {
            return Collections.emptyList();
        }

        int count = (endIdx - startIdx) + 1;
        List<RectF> pdfRects = pdfView.getTextPageRects(pageIndex, startIdx, count);
        if (pdfRects == null || pdfRects.isEmpty()) {
            return Collections.emptyList();
        }

        com.github.barteksc.pdfviewer.util.Size originalSize = pdfView.getPdfFile().getOriginalPageSize(pageIndex);
        if (originalSize == null) return Collections.emptyList();
        float origW = originalSize.getWidth();
        float origH = originalSize.getHeight();
        if (origW <= 0f || origH <= 0f) {
            return Collections.emptyList();
        }

        // 1. Convert all PDFium character rects to normalized page coordinates (0f..1f, top-left origin)
        List<RectF> rawNormRects = new ArrayList<>(pdfRects.size());
        for (RectF r : pdfRects) {
            float minX = Math.min(r.left, r.right);
            float maxX = Math.max(r.left, r.right);
            float minY = Math.min(r.top, r.bottom);
            float maxY = Math.max(r.top, r.bottom);

            float normLeft = (minX / origW);
            float normRight = (maxX / origW);
            float normTop = (origH - maxY) / origH;
            float normBottom = (origH - minY) / origH;

            float clampedLeft = Math.max(0f, Math.min(1f, normLeft));
            float clampedTop = Math.max(0f, Math.min(1f, normTop));
            float clampedRight = Math.max(0f, Math.min(1f, normRight));
            float clampedBottom = Math.max(0f, Math.min(1f, normBottom));

            if (clampedRight > clampedLeft && clampedBottom > clampedTop) {
                rawNormRects.add(new RectF(clampedLeft, clampedTop, clampedRight, clampedBottom));
            }
        }

        if (rawNormRects.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. Sort rects strictly and transitively: top-to-bottom, then left-to-right
        // (Guarantees strict weak ordering, preventing TimSort "Comparison method violates its general contract!" crash)
        Collections.sort(rawNormRects, new Comparator<RectF>() {
            @Override
            public int compare(RectF r1, RectF r2) {
                int cmpTop = Float.compare(r1.top, r2.top);
                if (cmpTop != 0) {
                    return cmpTop;
                }
                return Float.compare(r1.left, r2.left);
            }
        });

        // 3. Merge contiguous character boxes on the same line into uniform highlight bars
        List<RectF> mergedLines = new ArrayList<>();
        RectF currentLine = null;

        for (RectF rect : rawNormRects) {
            if (currentLine == null) {
                currentLine = new RectF(rect);
            } else {
                float lineHeight = Math.max(currentLine.height(), rect.height());
                if (Math.abs(currentLine.centerY() - rect.centerY()) < lineHeight * 0.6f) {
                    currentLine.left = Math.min(currentLine.left, rect.left);
                    currentLine.right = Math.max(currentLine.right, rect.right);
                    currentLine.top = Math.min(currentLine.top, rect.top);
                    currentLine.bottom = Math.max(currentLine.bottom, rect.bottom);
                } else {
                    mergedLines.add(currentLine);
                    currentLine = new RectF(rect);
                }
            }
        }
        if (currentLine != null) {
            mergedLines.add(currentLine);
        }

        return mergedLines;
    }

    /**
     * Pure float math projection from cached normalized rects to screen coordinates.
     * Zero locks, zero JNI calls, ultra-fast for 60fps rendering during pan/zoom.
     *
     * Serves as the Single Source of Truth for BOTH highlight bars and handle anchors.
     */
    public void updateScreenRectsFromNormalized() {
        highlightScreenRects.clear();
        if (selectedPageIndex < 0 || pdfView.getPdfFile() == null || selectedPageIndex >= pdfView.getPageCount()) {
            hasStartHandle = false;
            hasEndHandle = false;
            startHandleRect.setEmpty();
            endHandleRect.setEmpty();
            return;
        }

        List<RectF> rectsCopy;
        synchronized (cachedNormalizedRects) {
            if (cachedNormalizedRects.isEmpty()) {
                hasStartHandle = false;
                hasEndHandle = false;
                startHandleRect.setEmpty();
                endHandleRect.setEmpty();
                return;
            }
            rectsCopy = new ArrayList<>(cachedNormalizedRects);
        }

        float zoom = pdfView.getZoom();
        SizeF pageSize = pdfView.getPdfFile().getScaledPageSize(selectedPageIndex, zoom);
        if (pageSize == null || pageSize.getWidth() <= 0 || pageSize.getHeight() <= 0) {
            hasStartHandle = false;
            hasEndHandle = false;
            startHandleRect.setEmpty();
            endHandleRect.setEmpty();
            return;
        }

        float pageX, pageY;
        if (pdfView.isSwipeVertical()) {
            pageX = pdfView.getPdfFile().getSecondaryPageOffset(selectedPageIndex, zoom);
            pageY = pdfView.getPdfFile().getPageOffset(selectedPageIndex, zoom);
        } else {
            pageY = pdfView.getPdfFile().getSecondaryPageOffset(selectedPageIndex, zoom);
            pageX = pdfView.getPdfFile().getPageOffset(selectedPageIndex, zoom);
        }

        float offsetX = pdfView.getCurrentXOffset();
        float offsetY = pdfView.getCurrentYOffset();
        float pageW = pageSize.getWidth();
        float pageH = pageSize.getHeight();

        for (RectF norm : rectsCopy) {
            float left = pageX + norm.left * pageW + offsetX;
            float top = pageY + norm.top * pageH + offsetY;
            float right = pageX + norm.right * pageW + offsetX;
            float bottom = pageY + norm.bottom * pageH + offsetY;
            highlightScreenRects.add(new RectF(left, top, right, bottom));
        }

        if (!highlightScreenRects.isEmpty()) {
            boolean isRtl = isRtl(selectedText);
            RectF first = highlightScreenRects.get(0);
            RectF last = highlightScreenRects.get(highlightScreenRects.size() - 1);

            // RTL vs LTR Anchor placement at the true visual edges of text lines
            startAnchorX = isRtl ? first.right : first.left;
            startAnchorY = first.bottom;
            endAnchorX = isRtl ? last.left : last.right;
            endAnchorY = last.bottom;

            hasStartHandle = !Float.isNaN(startAnchorX) && !Float.isNaN(startAnchorY);
            hasEndHandle = !Float.isNaN(endAnchorX) && !Float.isNaN(endAnchorY);

            float density = context.getResources().getDisplayMetrics().density;
            float r = 10.5f * density;
            float d = 1.35f * r;
            float touchTargetRadius = 24f * density; // 48dp touch target hitbox

            float startCenterY = startAnchorY + d;
            float endCenterY = endAnchorY + d;

            startHandleRect.set(startAnchorX - touchTargetRadius, startCenterY - touchTargetRadius,
                                startAnchorX + touchTargetRadius, startCenterY + touchTargetRadius);
            endHandleRect.set(endAnchorX - touchTargetRadius, endCenterY - touchTargetRadius,
                              endAnchorX + touchTargetRadius, endCenterY + touchTargetRadius);
        } else {
            hasStartHandle = false;
            hasEndHandle = false;
            startHandleRect.setEmpty();
            endHandleRect.setEmpty();
        }
    }

    /**
     * Draws a Material 3 teardrop handle pointing precisely up at (anchorX, anchorY),
     * with the rounded bulb hanging cleanly below the line so it never obscures text.
     */
    private void drawTeardropHandle(Canvas canvas, float anchorX, float anchorY, Paint paint) {
        if (Float.isNaN(anchorX) || Float.isNaN(anchorY) || Float.isInfinite(anchorX) || Float.isInfinite(anchorY)) {
            return;
        }
        float density = context.getResources().getDisplayMetrics().density;
        float r = 10.5f * density; // ~10.5dp radius
        float d = 1.35f * r;      // Distance from tip to bulb center

        float cx = anchorX;
        float cy = anchorY + d;

        float sinAlpha = Math.min(1.0f, Math.max(0.0f, r / d));
        float alpha = (float) Math.asin(sinAlpha);
        float cosAlpha = (float) Math.cos(alpha);
        float alphaDeg = (float) Math.toDegrees(alpha);

        float t1X = cx - r * cosAlpha;
        float t1Y = cy - r * sinAlpha;
        float t2X = cx + r * cosAlpha;
        float t2Y = cy - r * sinAlpha;

        Path path = new Path();
        path.moveTo(anchorX, anchorY);
        path.lineTo(t2X, t2Y);

        RectF oval = new RectF(cx - r, cy - r, cx + r, cy + r);
        path.arcTo(oval, 360f - alphaDeg, 180f + 2 * alphaDeg, false);

        path.lineTo(anchorX, anchorY);
        path.close();

        canvas.drawPath(path, paint);
    }

    /**
     * Called when zoom/scroll/fling animation ends to update the ActionMode position once.
     */
    public void onAnimationEnd() {
        if (isSelectionActive && !cachedNormalizedRects.isEmpty()) {
            updateScreenRectsFromNormalized();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && actionMode != null) {
                try {
                    actionMode.invalidateContentRect();
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Pure math draw pass executed on PDFView's onDraw. Never hits native PDFium lock.
     */
    public void drawSelection(Canvas canvas) {
        if (!isSelectionActive || cachedNormalizedRects.isEmpty()) return;

        updateScreenRectsFromNormalized();

        // Draw highlights
        for (RectF rect : highlightScreenRects) {
            canvas.drawRect(rect, highlightPaint);
        }

        // Draw sleek Material 3 teardrop handles
        if (hasStartHandle) {
            drawTeardropHandle(canvas, startAnchorX, startAnchorY, handlePaint);
        }
        if (hasEndHandle) {
            drawTeardropHandle(canvas, endAnchorX, endAnchorY, handlePaint);
        }
    }

    public boolean onTouchEvent(MotionEvent event) {
        if (!isSelectionActive) return false;

        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (hasStartHandle && hasEndHandle) {
                    float startCenterX = startHandleRect.centerX();
                    float startCenterY = startHandleRect.centerY();
                    float endCenterX = endHandleRect.centerX();
                    float endCenterY = endHandleRect.centerY();

                    float density = context.getResources().getDisplayMetrics().density;
                    float touchTolerance = 28f * density; // Generous ~56dp touch hitbox

                    float distStart = (float) Math.hypot(x - startCenterX, y - startCenterY);
                    float distEnd = (float) Math.hypot(x - endCenterX, y - endCenterY);

                    if (distStart <= touchTolerance || distEnd <= touchTolerance) {
                        dismissColorDropdown();
                        if (distStart <= distEnd) {
                            isDraggingStartHandle = true;
                            isDraggingEndHandle = false;
                        } else {
                            isDraggingStartHandle = false;
                            isDraggingEndHandle = true;
                        }
                        return true;
                    }
                }
                return false;

            case MotionEvent.ACTION_MOVE:
                if (isDraggingStartHandle || isDraggingEndHandle) {
                    if (pdfView.getPdfFile() == null || selectedPageIndex < 0) return false;

                    float density = context.getResources().getDisplayMetrics().density;
                    float hitTestOffsetY = 16f * density; // Point above handle bulb towards text baseline
                    float hitX = x;
                    float hitY = y - hitTestOffsetY;

                    float mappedX = -pdfView.getCurrentXOffset() + hitX;
                    float mappedY = -pdfView.getCurrentYOffset() + hitY;

                    float zoom = pdfView.getZoom();
                    SizeF pageSize = pdfView.getPdfFile().getScaledPageSize(selectedPageIndex, zoom);
                    if (pageSize.getWidth() <= 0 || pageSize.getHeight() <= 0) return false;

                    float pageX, pageY;
                    if (pdfView.isSwipeVertical()) {
                        pageX = pdfView.getPdfFile().getSecondaryPageOffset(selectedPageIndex, zoom);
                        pageY = pdfView.getPdfFile().getPageOffset(selectedPageIndex, zoom);
                    } else {
                        pageY = pdfView.getPdfFile().getSecondaryPageOffset(selectedPageIndex, zoom);
                        pageX = pdfView.getPdfFile().getPageOffset(selectedPageIndex, zoom);
                    }

                    float localX = mappedX - pageX;
                    float localY = mappedY - pageY;
                    com.github.barteksc.pdfviewer.util.Size originalSize = pdfView.getPdfFile().getOriginalPageSize(selectedPageIndex);
                    if (originalSize.getWidth() <= 0 || originalSize.getHeight() <= 0) return false;

                    final float pdfX = (localX / pageSize.getWidth()) * originalSize.getWidth();
                    final float pdfY = ((pageSize.getHeight() - localY) / pageSize.getHeight()) * originalSize.getHeight();

                    final long reqId = currentRequestId.incrementAndGet();
                    final boolean isStart = isDraggingStartHandle;
                    final int page = selectedPageIndex;
                    final int currentStart = startCharIndex;
                    final int currentEnd = endCharIndex;

                    getExecutor().execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (currentRequestId.get() != reqId) return;

                                int totalChars = pdfView.getTextPageCountChars(page);
                                if (totalChars <= 0 || currentRequestId.get() != reqId) return;

                                // 1. Initial hit-test with standard tolerance
                                int charIdx = pdfView.getTextPageCharIndexAtPos(page, pdfX, pdfY, 16.0, 16.0);

                                // 2. Whitespace / inter-line fallback: retry with wider tolerance
                                if (charIdx < 0) {
                                    charIdx = pdfView.getTextPageCharIndexAtPos(page, pdfX, pdfY, 32.0, 32.0);
                                }
                                if (charIdx < 0) {
                                    charIdx = pdfView.getTextPageCharIndexAtPos(page, pdfX, pdfY, 48.0, 32.0);
                                }

                                if (charIdx < 0 || currentRequestId.get() != reqId) return;

                                charIdx = Math.max(0, Math.min(totalChars - 1, charIdx));

                                int newStart, newEnd;
                                boolean swapToStart = isStart;
                                if (isStart) {
                                    if (charIdx > currentEnd) {
                                        newStart = currentEnd;
                                        newEnd = charIdx;
                                        swapToStart = false; // Start handle crossed over End handle -> became End handle
                                    } else {
                                        newStart = charIdx;
                                        newEnd = currentEnd;
                                    }
                                } else {
                                    if (charIdx < currentStart) {
                                        newStart = charIdx;
                                        newEnd = currentStart;
                                        swapToStart = true; // End handle crossed over Start handle -> became Start handle
                                    } else {
                                        newStart = currentStart;
                                        newEnd = charIdx;
                                    }
                                }

                                if (newStart == currentStart && newEnd == currentEnd) return;

                                int count = (newEnd - newStart) + 1;
                                final String text = pdfView.getTextPageText(page, newStart, count);
                                final List<RectF> normRects = extractNormalizedRects(page, newStart, newEnd);

                                if (currentRequestId.get() != reqId) return;

                                final boolean finalSwapToStart = swapToStart;
                                final int finalStart = newStart;
                                final int finalEnd = newEnd;

                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (currentRequestId.get() != reqId) return;
                                        if (isDraggingStartHandle || isDraggingEndHandle) {
                                            isDraggingStartHandle = finalSwapToStart;
                                            isDraggingEndHandle = !finalSwapToStart;
                                        }
                                        applySelection(page, finalStart, finalEnd, text, normRects);
                                    }
                                });
                            } catch (Throwable ignored) {}
                        }
                    });
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDraggingStartHandle || isDraggingEndHandle) {
                    isDraggingStartHandle = false;
                    isDraggingEndHandle = false;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && actionMode != null && !pdfView.isAnimating()) {
                        try {
                            actionMode.invalidateContentRect();
                        } catch (Exception ignored) {}
                    }
                    return true;
                }
                break;
        }

        return false;
    }

    public void clearSelection() {
        currentRequestId.incrementAndGet();
        dismissColorDropdown();
        isSelectionActive = false;
        isDraggingStartHandle = false;
        isDraggingEndHandle = false;
        selectedPageIndex = -1;
        startCharIndex = -1;
        endCharIndex = -1;
        selectedText = "";
        cachedNormalizedRects.clear();
        highlightScreenRects.clear();
        hasStartHandle = false;
        hasEndHandle = false;
        startHandleRect.setEmpty();
        endHandleRect.setEmpty();

        if (actionMode != null) {
            ActionMode am = actionMode;
            actionMode = null;
            try {
                am.finish();
            } catch (Exception ignored) {}
        }
        pdfView.invalidate();
    }

    public void dispose() {
        clearSelection();
        synchronized (this) {
            if (executor != null) {
                try {
                    executor.shutdownNow();
                } catch (Exception ignored) {}
                executor = null;
            }
        }
    }

    private static final int MENU_COPY = 1;
    private static final int MENU_SHARE = 2;
    private static final int MENU_SELECT_ALL = 3;
    private static final int MENU_HIGHLIGHT_DEFAULT = 100;
    private static final int MENU_EXPAND_COLORS = 101;

    public static final int COLOR_YELLOW = 0xFFFFF176;
    public static final int COLOR_GREEN = 0xFFA5D6A7;
    public static final int COLOR_PINK = 0xFFF48FB1;
    public static final int COLOR_BLUE = 0xFF90CAF9;

    public static final int[] ALL_HIGHLIGHT_COLORS = new int[] {
        COLOR_YELLOW,
        COLOR_GREEN,
        COLOR_PINK,
        COLOR_BLUE
    };

    private PopupWindow colorDropdownPopup;

    public void dismissColorDropdown() {
        if (colorDropdownPopup != null && colorDropdownPopup.isShowing()) {
            try {
                colorDropdownPopup.dismiss();
            } catch (Exception ignored) {}
            colorDropdownPopup = null;
        }
    }

    private GradientDrawable createSwatchDrawable(int colorArgb) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(colorArgb);
        int sizePx = (int) (22 * context.getResources().getDisplayMetrics().density);
        drawable.setSize(sizePx, sizePx);
        drawable.setStroke((int) context.getResources().getDisplayMetrics().density, 0x33000000);
        return drawable;
    }

    private Drawable createChevronDownDrawable() {
        int sizePx = (int) (18 * context.getResources().getDisplayMetrics().density);
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.0f * context.getResources().getDisplayMetrics().density);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        boolean isDark = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        paint.setColor(isDark ? 0xFFDDDDDD : 0xFF444444);
        Path path = new Path();
        float p = sizePx;
        path.moveTo(p * 0.22f, p * 0.38f);
        path.lineTo(p * 0.50f, p * 0.64f);
        path.lineTo(p * 0.78f, p * 0.38f);
        canvas.drawPath(path, paint);
        return new BitmapDrawable(context.getResources(), bitmap);
    }

    private SpannableString createSwatchSpannable(int colorArgb) {
        float density = context.getResources().getDisplayMetrics().density;
        int widthPx = (int) (24 * density);
        int heightPx = (int) (24 * density);
        int circleRadiusPx = (int) (10 * density);

        Bitmap bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        float cx = widthPx / 2f;
        float cy = heightPx / 2f;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(colorArgb);
        canvas.drawCircle(cx, cy, circleRadiusPx, paint);

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.0f * density);
        stroke.setColor(0x33000000);
        canvas.drawCircle(cx, cy, circleRadiusPx - (0.5f * density), stroke);

        BitmapDrawable drawable = new BitmapDrawable(context.getResources(), bitmap);
        drawable.setBounds(0, 0, widthPx, heightPx);

        SpannableString spannable = new SpannableString(" ");
        spannable.setSpan(new ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    private SpannableString createArrowSpannable() {
        float density = context.getResources().getDisplayMetrics().density;
        int widthPx = (int) (20 * density);
        int heightPx = (int) (24 * density);
        int arrowWidth = (int) (10 * density);

        Bitmap bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.0f * density);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        boolean isDark = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        paint.setColor(isDark ? 0xFFDDDDDD : 0xFF444444);

        float startX = (widthPx - arrowWidth) / 2f;
        float cy = heightPx / 2f;
        Path path = new Path();
        path.moveTo(startX, cy - (2.5f * density));
        path.lineTo(startX + (arrowWidth / 2f), cy + (3.0f * density));
        path.lineTo(startX + arrowWidth, cy - (2.5f * density));
        canvas.drawPath(path, paint);

        BitmapDrawable drawable = new BitmapDrawable(context.getResources(), bitmap);
        drawable.setBounds(0, 0, widthPx, heightPx);

        SpannableString spannable = new SpannableString(" ");
        spannable.setSpan(new ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    private void showColorDropdown(final ActionMode mode) {
        dismissColorDropdown();
        if (highlightScreenRects.isEmpty()) return;

        float density = context.getResources().getDisplayMetrics().density;
        boolean isDark = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        int paddingPx = (int) (6 * density);
        container.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(18 * density);
        bg.setColor(isDark ? 0xFF2C2C2C : 0xFFFFFFFF);
        bg.setStroke((int) (1 * density), isDark ? 0x33FFFFFF : 0x1F000000);
        container.setBackground(bg);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            container.setElevation(8 * density);
        }

        int[] otherColors = new int[] {
            COLOR_GREEN,
            COLOR_PINK,
            COLOR_BLUE
        };

        int itemSize = (int) (36 * density);
        int swatchSize = (int) (22 * density);

        for (final int colorArgb : otherColors) {
            FrameLayout itemFrame = new FrameLayout(context);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(itemSize, itemSize);
            itemFrame.setLayoutParams(lp);

            View circle = new View(context);
            FrameLayout.LayoutParams circleLp = new FrameLayout.LayoutParams(swatchSize, swatchSize, Gravity.CENTER);
            circle.setLayoutParams(circleLp);
            circle.setBackground(createSwatchDrawable(colorArgb));
            itemFrame.addView(circle);

            itemFrame.setClickable(true);
            itemFrame.setFocusable(true);
            TypedValue outValue = new TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true);
            itemFrame.setBackgroundResource(outValue.resourceId);

            itemFrame.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismissColorDropdown();
                    if (highlightListener != null) {
                        highlightListener.onApplyHighlight(colorArgb);
                    }
                    if (mode != null) {
                        mode.finish();
                    }
                }
            });

            container.addView(itemFrame);
        }

        colorDropdownPopup = new PopupWindow(container, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        colorDropdownPopup.setOutsideTouchable(true);
        colorDropdownPopup.setFocusable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            colorDropdownPopup.setElevation(10 * density);
        }

        float minL = Float.MAX_VALUE, minT = Float.MAX_VALUE;
        float maxR = Float.MIN_VALUE, maxB = Float.MIN_VALUE;
        for (RectF r : highlightScreenRects) {
            minL = Math.min(minL, r.left);
            minT = Math.min(minT, r.top);
            maxR = Math.max(maxR, r.right);
            maxB = Math.max(maxB, r.bottom);
        }

        int[] location = new int[2];
        pdfView.getLocationOnScreen(location);
        int selScreenLeft = (int) (location[0] + minL);
        int selScreenTop = (int) (location[1] + minT);
        int selScreenBottom = (int) (location[1] + maxB);

        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        int popupWidth = (int) (52 * density);
        int targetX = Math.max((int) (12 * density), Math.min(selScreenLeft + (int) (12 * density), screenWidth - popupWidth - (int) (12 * density)));

        int targetY;
        if (selScreenTop > 180 * density) {
            targetY = selScreenTop - (int) (8 * density);
        } else {
            targetY = selScreenBottom + (int) (52 * density);
        }
        targetY = Math.max((int) (60 * density), Math.min(targetY, screenHeight - (int) (180 * density)));

        try {
            colorDropdownPopup.showAtLocation(pdfView, Gravity.NO_GRAVITY, targetX, targetY);
        } catch (Exception ignored) {}
    }

    private void populateMenu(Menu menu) {
        // Arrow to expand colors placed on the other side of the yellow circle (order 0)
        MenuItem arrowItem = menu.add(Menu.NONE, MENU_EXPAND_COLORS, 0, createArrowSpannable());
        arrowItem.setIcon(createChevronDownDrawable());
        arrowItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        // Yellow circle as default color shortcut (order 1)
        MenuItem yellowItem = menu.add(Menu.NONE, MENU_HIGHLIGHT_DEFAULT, 1, createSwatchSpannable(COLOR_YELLOW));
        yellowItem.setIcon(createSwatchDrawable(COLOR_YELLOW));
        yellowItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        MenuItem copyItem = menu.add(Menu.NONE, MENU_COPY, 10, "Copy");
        copyItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        MenuItem selectAllItem = menu.add(Menu.NONE, MENU_SELECT_ALL, 11, "Select All");
        selectAllItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        MenuItem shareItem = menu.add(Menu.NONE, MENU_SHARE, 12, "Share");
        shareItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
    }

    private boolean handleMenuItemClick(ActionMode mode, MenuItem item) {
        int id = item.getItemId();
        if (id == MENU_HIGHLIGHT_DEFAULT) {
            dismissColorDropdown();
            if (highlightListener != null) {
                highlightListener.onApplyHighlight(COLOR_YELLOW);
            }
            mode.finish();
            return true;
        }

        if (id == MENU_EXPAND_COLORS) {
            if (colorDropdownPopup != null && colorDropdownPopup.isShowing()) {
                dismissColorDropdown();
            } else {
                showColorDropdown(mode);
            }
            return true;
        }

        switch (id) {
            case MENU_COPY:
                dismissColorDropdown();
                copyToClipboard();
                mode.finish();
                return true;
            case MENU_SHARE:
                dismissColorDropdown();
                shareSelectedText();
                mode.finish();
                return true;
            case MENU_SELECT_ALL:
                dismissColorDropdown();
                selectAllOnPage();
                return true;
        }
        return false;
    }

    private void showActionMode() {
        if (actionMode != null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pdfView.startActionMode(new ActionMode.Callback2() {
                @Override
                public void onGetContentRect(ActionMode mode, View view, Rect outRect) {
                    if (!highlightScreenRects.isEmpty()) {
                        float minL = Float.MAX_VALUE, minT = Float.MAX_VALUE;
                        float maxR = -Float.MAX_VALUE, maxB = -Float.MAX_VALUE;
                        for (RectF r : highlightScreenRects) {
                            minL = Math.min(minL, r.left);
                            minT = Math.min(minT, r.top);
                            maxR = Math.max(maxR, r.right);
                            maxB = Math.max(maxB, r.bottom);
                        }
                        if (maxR > minL && maxB > minT) {
                            outRect.set((int) minL, (int) minT, (int) maxR, (int) maxB);
                            return;
                        }
                    }
                    if (view != null) {
                        outRect.set(0, 0, Math.max(1, view.getWidth()), Math.max(1, view.getHeight()));
                    } else {
                        outRect.set(0, 0, 1, 1);
                    }
                }

                @Override
                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    actionMode = mode;
                    populateMenu(menu);
                    return true;
                }

                @Override
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    return false;
                }

                @Override
                public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    return handleMenuItemClick(mode, item);
                }

                @Override
                public void onDestroyActionMode(ActionMode mode) {
                    actionMode = null;
                    clearSelection();
                }
            }, ActionMode.TYPE_FLOATING);
        } else {
            pdfView.startActionMode(new ActionMode.Callback() {
                @Override
                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    actionMode = mode;
                    populateMenu(menu);
                    return true;
                }

                @Override
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    return false;
                }

                @Override
                public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    return handleMenuItemClick(mode, item);
                }

                @Override
                public void onDestroyActionMode(ActionMode mode) {
                    actionMode = null;
                    clearSelection();
                }
            });
        }
    }

    private void copyToClipboard() {
        if (selectedText != null && !selectedText.isEmpty()) {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("PDF Text", selectedText);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void shareSelectedText() {
        if (selectedText != null && !selectedText.isEmpty()) {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, selectedText);
            context.startActivity(Intent.createChooser(intent, "Share text"));
        }
    }

    private void selectAllOnPage() {
        if (selectedPageIndex < 0) return;
        final int page = selectedPageIndex;
        final long reqId = currentRequestId.incrementAndGet();
        getExecutor().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (currentRequestId.get() != reqId) return;
                    int totalChars = pdfView.getTextPageCountChars(page);
                    if (totalChars <= 0 || currentRequestId.get() != reqId) return;

                    final String text = pdfView.getTextPageText(page, 0, totalChars);
                    final List<RectF> normRects = extractNormalizedRects(page, 0, totalChars - 1);

                    if (currentRequestId.get() != reqId) return;

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (currentRequestId.get() != reqId) return;
                            applySelection(page, 0, totalChars - 1, text, normRects);
                        }
                    });
                } catch (Throwable ignored) {}
            }
        });
    }
}
