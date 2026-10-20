package com.github.barteksc.pdfviewer.selection;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.github.barteksc.pdfviewer.PDFView;
import com.github.barteksc.pdfviewer.util.SizeF;

import java.util.ArrayList;
import java.util.List;

public class PdfTextSelectionManager {

    private final PDFView pdfView;
    private final Context context;

    private int selectedPageIndex = -1;
    private int startCharIndex = -1;
    private int endCharIndex = -1;
    private String selectedText = "";
    private final List<RectF> highlightScreenRects = new ArrayList<>();

    private final RectF startHandleRect = new RectF();
    private final RectF endHandleRect = new RectF();

    private boolean isSelectionActive = false;
    private boolean isDraggingStartHandle = false;
    private boolean isDraggingEndHandle = false;

    private final Paint highlightPaint = new Paint();
    private final Paint handlePaint = new Paint();

    private ActionMode actionMode;

    private static final float HANDLE_RADIUS = 24f;
    private static final float TOUCH_TOLERANCE = 45f;

    public PdfTextSelectionManager(PDFView pdfView) {
        this.pdfView = pdfView;
        this.context = pdfView.getContext();

        highlightPaint.setColor(Color.parseColor("#4D33B5E5")); // Semi-transparent blue
        highlightPaint.setStyle(Paint.Style.FILL);

        handlePaint.setColor(Color.parseColor("#FF007ACC")); // Solid blue
        handlePaint.setAntiAlias(true);
        handlePaint.setStyle(Paint.Style.FILL);
    }

    public boolean isSelectionActive() {
        return isSelectionActive;
    }

    public boolean isDraggingHandle() {
        return isDraggingStartHandle || isDraggingEndHandle;
    }

    /**
     * Triggered on long press to select word at touch position.
     */
    public boolean onLongPress(float touchX, float touchY) {
        int pageIndex = pdfView.getCurrentPage();
        if (pdfView.getPdfFile() == null) return false;

        float mappedX = -pdfView.getCurrentXOffset() + touchX;
        float mappedY = -pdfView.getCurrentYOffset() + touchY;

        float zoom = pdfView.getZoom();
        SizeF pageSize = pdfView.getPdfFile().getScaledPageSize(pageIndex, zoom);
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
        float pdfX = (localX / pageSize.getWidth()) * originalSize.getWidth();
        float pdfY = ((pageSize.getHeight() - localY) / pageSize.getHeight()) * originalSize.getHeight();

        int totalChars = pdfView.getTextPageCountChars(pageIndex);
        if (totalChars <= 0) return false;

        int charIndex = pdfView.getTextPageCharIndexAtPos(pageIndex, pdfX, pdfY, 15.0, 15.0);
        if (charIndex < 0 || charIndex >= totalChars) return false;

        // Expand to word boundaries
        int wordStart = charIndex;
        do {
            String ch = pdfView.getTextPageText(pageIndex, wordStart - 1, 1);
            if (isWordSeparator(ch)) break;
            wordStart--;
        } while (wordStart > 0);

        int wordEnd = charIndex;
        while (wordEnd < totalChars - 1) {
            String ch = pdfView.getTextPageText(pageIndex, wordEnd + 1, 1);
            if (isWordSeparator(ch)) break;
            wordEnd++;
        }

        setSelection(pageIndex, wordStart, wordEnd);
        return isSelectionActive;
    }

    private boolean isWordSeparator(String ch) {
        if (ch == null || ch.isEmpty()) return true;
        char c = ch.charAt(0);
        return Character.isWhitespace(c) || c == '.' || c == ',' || c == '!' || c == '?' || c == ';' || c == ':';
    }

    public void setSelection(int pageIndex, int startIdx, int endIdx) {
        if (startIdx > endIdx) {
            int temp = startIdx;
            startIdx = endIdx;
            endIdx = temp;
        }

        this.selectedPageIndex = pageIndex;
        this.startCharIndex = startIdx;
        this.endCharIndex = endIdx;
        int count = (endIdx - startIdx) + 1;

        this.selectedText = pdfView.getTextPageText(pageIndex, startIdx, count);
        recalculateHighlights();

        if (!highlightScreenRects.isEmpty()) {
            this.isSelectionActive = true;
            pdfView.invalidate();
            showActionMode();
        } else {
            clearSelection();
        }
    }

    public void recalculateHighlights() {
        highlightScreenRects.clear();
        if (selectedPageIndex < 0 || startCharIndex < 0 || endCharIndex < 0) return;
        if (pdfView.getPdfFile() == null) return;

        int count = (endCharIndex - startCharIndex) + 1;
        List<RectF> pdfRects = pdfView.getTextPageRects(selectedPageIndex, startCharIndex, count);

        float zoom = pdfView.getZoom();
        SizeF pageSize = pdfView.getPdfFile().getScaledPageSize(selectedPageIndex, zoom);
        float pageX, pageY;
        if (pdfView.isSwipeVertical()) {
            pageX = pdfView.getPdfFile().getSecondaryPageOffset(selectedPageIndex, zoom);
            pageY = pdfView.getPdfFile().getPageOffset(selectedPageIndex, zoom);
        } else {
            pageY = pdfView.getPdfFile().getSecondaryPageOffset(selectedPageIndex, zoom);
            pageX = pdfView.getPdfFile().getPageOffset(selectedPageIndex, zoom);
        }

        List<RectF> rawScreenRects = new ArrayList<>();
        for (RectF pdfRect : pdfRects) {
            RectF mappedRect = pdfView.getPdfFile().mapRectToDevice(
                    selectedPageIndex,
                    (int) pageX,
                    (int) pageY,
                    (int) pageSize.getWidth(),
                    (int) pageSize.getHeight(),
                    pdfRect
            );
            mappedRect.sort();
            mappedRect.offset(pdfView.getCurrentXOffset(), pdfView.getCurrentYOffset());
            rawScreenRects.add(mappedRect);
        }

        // Merge adjacent character boxes on the same line into smooth continuous bars
        List<RectF> mergedLines = mergeLineRects(rawScreenRects);
        highlightScreenRects.addAll(mergedLines);

        if (!highlightScreenRects.isEmpty()) {
            RectF first = highlightScreenRects.get(0);
            RectF last = highlightScreenRects.get(highlightScreenRects.size() - 1);

            startHandleRect.set(first.left - HANDLE_RADIUS, first.bottom, first.left + HANDLE_RADIUS, first.bottom + (2 * HANDLE_RADIUS));
            endHandleRect.set(last.right - HANDLE_RADIUS, last.bottom, last.right + HANDLE_RADIUS, last.bottom + (2 * HANDLE_RADIUS));

            // Dynamically update floating menu position as highlights shift
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && actionMode != null) {
                actionMode.invalidateContentRect();
            }
        }
    }

    private List<RectF> mergeLineRects(List<RectF> rawRects) {
        if (rawRects == null || rawRects.isEmpty()) return new ArrayList<>();

        List<RectF> merged = new ArrayList<>();
        RectF currentLine = null;

        for (RectF rect : rawRects) {
            if (currentLine == null) {
                currentLine = new RectF(rect);
            } else {
                float lineCenterY = currentLine.centerY();
                float rectCenterY = rect.centerY();
                float lineHeight = Math.max(currentLine.height(), rect.height());

                // If on the same line (vertical overlap)
                if (Math.abs(lineCenterY - rectCenterY) < lineHeight * 0.6f) {
                    currentLine.left = Math.min(currentLine.left, rect.left);
                    currentLine.right = Math.max(currentLine.right, rect.right);
                    currentLine.top = Math.min(currentLine.top, rect.top);
                    currentLine.bottom = Math.max(currentLine.bottom, rect.bottom);
                } else {
                    merged.add(currentLine);
                    currentLine = new RectF(rect);
                }
            }
        }
        if (currentLine != null) {
            merged.add(currentLine);
        }
        return merged;
    }

    public void drawSelection(Canvas canvas) {
        if (!isSelectionActive || highlightScreenRects.isEmpty()) return;

        recalculateHighlights();

        // Draw highlights
        for (RectF rect : highlightScreenRects) {
            canvas.drawRect(rect, highlightPaint);
        }

        // Draw teardrop selection handles
        if (!highlightScreenRects.isEmpty()) {
            RectF first = highlightScreenRects.get(0);
            RectF last = highlightScreenRects.get(highlightScreenRects.size() - 1);

            // Start handle
            canvas.drawCircle(first.left, first.bottom + HANDLE_RADIUS, HANDLE_RADIUS, handlePaint);
            // End handle
            canvas.drawCircle(last.right, last.bottom + HANDLE_RADIUS, HANDLE_RADIUS, handlePaint);
        }
    }

    public boolean onTouchEvent(MotionEvent event) {
        if (!isSelectionActive) return false;

        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (!highlightScreenRects.isEmpty()) {
                    RectF first = highlightScreenRects.get(0);
                    RectF last = highlightScreenRects.get(highlightScreenRects.size() - 1);

                    float startCenterX = first.left;
                    float startCenterY = first.bottom + HANDLE_RADIUS;
                    float endCenterX = last.right;
                    float endCenterY = last.bottom + HANDLE_RADIUS;

                    float distStart = (float) Math.hypot(x - startCenterX, y - startCenterY);
                    float distEnd = (float) Math.hypot(x - endCenterX, y - endCenterY);

                    if (distStart <= TOUCH_TOLERANCE || distEnd <= TOUCH_TOLERANCE) {
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
                    if (pdfView.getPdfFile() == null) return false;

                    float mappedX = -pdfView.getCurrentXOffset() + x;
                    float mappedY = -pdfView.getCurrentYOffset() + y;

                    float zoom = pdfView.getZoom();
                    SizeF pageSize = pdfView.getPdfFile().getScaledPageSize(selectedPageIndex, zoom);
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
                    float pdfX = (localX / pageSize.getWidth()) * originalSize.getWidth();
                    float pdfY = ((pageSize.getHeight() - localY) / pageSize.getHeight()) * originalSize.getHeight();

                    int charIdx = pdfView.getTextPageCharIndexAtPos(selectedPageIndex, pdfX, pdfY, 15.0, 15.0);
                    if (charIdx >= 0) {
                        if (isDraggingStartHandle) {
                            if (charIdx > endCharIndex) {
                                // Crossed over the end handle -> swap active handle!
                                isDraggingStartHandle = false;
                                isDraggingEndHandle = true;
                                setSelection(selectedPageIndex, endCharIndex, charIdx);
                            } else {
                                setSelection(selectedPageIndex, charIdx, endCharIndex);
                            }
                        } else {
                            if (charIdx < startCharIndex) {
                                // Crossed back over the start handle -> swap active handle!
                                isDraggingStartHandle = true;
                                isDraggingEndHandle = false;
                                setSelection(selectedPageIndex, charIdx, startCharIndex);
                            } else {
                                setSelection(selectedPageIndex, startCharIndex, charIdx);
                            }
                        }
                    }
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDraggingStartHandle || isDraggingEndHandle) {
                    isDraggingStartHandle = false;
                    isDraggingEndHandle = false;
                    return true;
                }
                break;
        }

        return false;
    }

    public void clearSelection() {
        isSelectionActive = false;
        isDraggingStartHandle = false;
        isDraggingEndHandle = false;
        selectedPageIndex = -1;
        startCharIndex = -1;
        endCharIndex = -1;
        selectedText = "";
        highlightScreenRects.clear();

        if (actionMode != null) {
            actionMode.finish();
            actionMode = null;
        }
        pdfView.invalidate();
    }

    private void showActionMode() {
        if (actionMode != null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pdfView.startActionMode(new ActionMode.Callback2() {
                @Override
                public void onGetContentRect(ActionMode mode, View view, Rect outRect) {
                    if (!highlightScreenRects.isEmpty()) {
                        float minL = Float.MAX_VALUE, minT = Float.MAX_VALUE;
                        float maxR = Float.MIN_VALUE, maxB = Float.MIN_VALUE;
                        for (RectF r : highlightScreenRects) {
                            minL = Math.min(minL, r.left);
                            minT = Math.min(minT, r.top);
                            maxR = Math.max(maxR, r.right);
                            maxB = Math.max(maxB, r.bottom);
                        }
                        outRect.set((int) minL, (int) minT, (int) maxR, (int) maxB);
                    }
                }

                @Override
                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    actionMode = mode;
                    menu.add(Menu.NONE, 1, Menu.NONE, "Copy");
                    menu.add(Menu.NONE, 2, Menu.NONE, "Share");
                    menu.add(Menu.NONE, 3, Menu.NONE, "Select All");
                    return true;
                }

                @Override
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    return false;
                }

                @Override
                public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    switch (item.getItemId()) {
                        case 1: // Copy
                            copyToClipboard();
                            mode.finish();
                            return true;
                        case 2: // Share
                            shareSelectedText();
                            mode.finish();
                            return true;
                        case 3: // Select All
                            selectAllOnPage();
                            return true;
                    }
                    return false;
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
                    menu.add(Menu.NONE, 1, Menu.NONE, "Copy");
                    menu.add(Menu.NONE, 2, Menu.NONE, "Share");
                    menu.add(Menu.NONE, 3, Menu.NONE, "Select All");
                    return true;
                }

                @Override
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    return false;
                }

                @Override
                public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    switch (item.getItemId()) {
                        case 1: // Copy
                            copyToClipboard();
                            mode.finish();
                            return true;
                        case 2: // Share
                            shareSelectedText();
                            mode.finish();
                            return true;
                        case 3: // Select All
                            selectAllOnPage();
                            return true;
                    }
                    return false;
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
        if (selectedPageIndex >= 0) {
            int totalChars = pdfView.getTextPageCountChars(selectedPageIndex);
            if (totalChars > 0) {
                setSelection(selectedPageIndex, 0, totalChars - 1);
            }
        }
    }
}
