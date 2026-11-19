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
        dismissColorDropdown();
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

    private void dismissColorDropdown() {
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
        if (selectedPageIndex >= 0) {
            int totalChars = pdfView.getTextPageCountChars(selectedPageIndex);
            if (totalChars > 0) {
                setSelection(selectedPageIndex, 0, totalChars - 1);
            }
        }
    }
}
