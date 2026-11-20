/**
 * Copyright 2017 Bartosz Schiller
 * Modified in 2026: Migrated to io.legere.pdfiumandroid and added native text selection.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.barteksc.pdfviewer;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.SparseArray;
import android.util.SparseBooleanArray;

import com.github.barteksc.pdfviewer.exception.PageRenderingException;
import com.github.barteksc.pdfviewer.util.FitPolicy;
import com.github.barteksc.pdfviewer.util.PageSizeCalculator;
import io.legere.pdfiumandroid.PdfDocument;
import io.legere.pdfiumandroid.PdfPage;
import io.legere.pdfiumandroid.PdfTextPage;
import io.legere.pdfiumandroid.PdfiumCore;
import io.legere.pdfiumandroid.api.Bookmark;
import io.legere.pdfiumandroid.api.Link;
import io.legere.pdfiumandroid.api.Meta;
import com.github.barteksc.pdfviewer.util.Size;
import com.github.barteksc.pdfviewer.util.SizeF;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PdfFile {

    private static final Object lock = new Object();
    private PdfDocument pdfDocument;
    private PdfiumCore pdfiumCore;
    private int pagesCount = 0;
    /** Original page sizes */
    private List<Size> originalPageSizes = new ArrayList<>();
    /** Scaled page sizes */
    private List<SizeF> pageSizes = new ArrayList<>();
    /** Opened pages with indicator whether opening was successful */
    private SparseBooleanArray openedPages = new SparseBooleanArray();
    /** Opened native PdfPage instances */
    private SparseArray<PdfPage> openedPdfPages = new SparseArray<>();
    /** Opened native PdfTextPage instances */
    private SparseArray<PdfTextPage> openedPdfTextPages = new SparseArray<>();
    /** Page with maximum width */
    private Size originalMaxWidthPageSize = new Size(0, 0);
    /** Page with maximum height */
    private Size originalMaxHeightPageSize = new Size(0, 0);
    /** Scaled page with maximum height */
    private SizeF maxHeightPageSize = new SizeF(0, 0);
    /** Scaled page with maximum width */
    private SizeF maxWidthPageSize = new SizeF(0, 0);
    /** True if scrolling is vertical, else it's horizontal */
    private boolean isVertical;
    /** Fixed spacing between pages in pixels */
    private int spacingPx;
    /** Calculate spacing automatically so each page fits on it's own in the center of the view */
    private boolean autoSpacing;
    /** Calculated offsets for pages */
    private List<Float> pageOffsets = new ArrayList<>();
    /** Calculated auto spacing for pages */
    private List<Float> pageSpacing = new ArrayList<>();
    /** Calculated document length (width or height, depending on swipe mode) */
    private float documentLength = 0;
    private final FitPolicy pageFitPolicy;
    /**
     * True if every page should fit separately according to the FitPolicy,
     * else the largest page fits and other pages scale relatively
     */
    private final boolean fitEachPage;
    /**
     * The pages the user want to display in order
     * (ex: 0, 2, 2, 8, 8, 1, 1, 1)
     */
    private int[] originalUserPages;
    private int topPaddingPx = 0;
    private int bottomPaddingPx = 0;

    PdfFile(PdfiumCore pdfiumCore, PdfDocument pdfDocument, FitPolicy pageFitPolicy, Size viewSize, int[] originalUserPages,
            boolean isVertical, int spacing, boolean autoSpacing, boolean fitEachPage, int topPaddingPx) {
        this(pdfiumCore, pdfDocument, pageFitPolicy, viewSize, originalUserPages, isVertical, spacing, autoSpacing, fitEachPage, topPaddingPx, 0);
    }

    PdfFile(PdfiumCore pdfiumCore, PdfDocument pdfDocument, FitPolicy pageFitPolicy, Size viewSize, int[] originalUserPages,
            boolean isVertical, int spacing, boolean autoSpacing, boolean fitEachPage, int topPaddingPx, int bottomPaddingPx) {
        this.pdfiumCore = pdfiumCore;
        this.pdfDocument = pdfDocument;
        this.pageFitPolicy = pageFitPolicy;
        this.originalUserPages = originalUserPages;
        this.isVertical = isVertical;
        this.spacingPx = spacing;
        this.autoSpacing = autoSpacing;
        this.fitEachPage = fitEachPage;
        this.topPaddingPx = topPaddingPx;
        this.bottomPaddingPx = bottomPaddingPx;
        setup(viewSize);
    }

    private void setup(Size viewSize) {
        if (originalUserPages != null) {
            pagesCount = originalUserPages.length;
        } else {
            pagesCount = pdfDocument.getPageCount();
        }

        for (int i = 0; i < pagesCount; i++) {
            int docPage = documentPage(i);
            Size pageSize;
            try (PdfPage page = pdfDocument.openPage(docPage)) {
                if (page != null) {
                    pageSize = new Size(page.getPageWidthPoint(), page.getPageHeightPoint());
                } else {
                    pageSize = new Size(0, 0);
                }
            } catch (Exception e) {
                pageSize = new Size(0, 0);
            }

            if (pageSize.getWidth() > originalMaxWidthPageSize.getWidth()) {
                originalMaxWidthPageSize = pageSize;
            }
            if (pageSize.getHeight() > originalMaxHeightPageSize.getHeight()) {
                originalMaxHeightPageSize = pageSize;
            }
            originalPageSizes.add(pageSize);
        }

        recalculatePageSizes(viewSize);
    }

    /**
     * Call after view size change to recalculate page sizes, offsets and document length
     *
     * @param viewSize new size of changed view
     */
    public void recalculatePageSizes(Size viewSize) {
        pageSizes.clear();
        PageSizeCalculator calculator = new PageSizeCalculator(pageFitPolicy, originalMaxWidthPageSize,
                originalMaxHeightPageSize, viewSize, fitEachPage);
        maxWidthPageSize = calculator.getOptimalMaxWidthPageSize();
        maxHeightPageSize = calculator.getOptimalMaxHeightPageSize();

        for (Size size : originalPageSizes) {
            pageSizes.add(calculator.calculate(size));
        }
        if (autoSpacing) {
            prepareAutoSpacing(viewSize);
        }
        prepareDocLen();
        preparePagesOffset();
    }

    public int getPagesCount() {
        return pagesCount;
    }

    public Size getOriginalPageSize(int pageIndex) {
        if (pageIndex < 0 || pageIndex >= originalPageSizes.size()) {
            return new Size(0, 0);
        }
        return originalPageSizes.get(pageIndex);
    }

    public SizeF getPageSize(int pageIndex) {
        int docPage = documentPage(pageIndex);
        if (docPage < 0) {
            return new SizeF(0, 0);
        }
        return pageSizes.get(pageIndex);
    }

    public SizeF getScaledPageSize(int pageIndex, float zoom) {
        SizeF size = getPageSize(pageIndex);
        return new SizeF(size.getWidth() * zoom, size.getHeight() * zoom);
    }

    /**
     * get page size with biggest dimension (width in vertical mode and height in horizontal mode)
     *
     * @return size of page
     */
    public SizeF getMaxPageSize() {
        return isVertical ? maxWidthPageSize : maxHeightPageSize;
    }

    public float getMaxPageWidth() {
        return getMaxPageSize().getWidth();
    }

    public float getMaxPageHeight() {
        return getMaxPageSize().getHeight();
    }

    private void prepareAutoSpacing(Size viewSize) {
        pageSpacing.clear();
        for (int i = 0; i < getPagesCount(); i++) {
            SizeF pageSize = pageSizes.get(i);
            float spacing = Math.max(0, isVertical ? viewSize.getHeight() - pageSize.getHeight() :
                    viewSize.getWidth() - pageSize.getWidth());
            if (i < getPagesCount() - 1) {
                spacing += spacingPx;
            }
            pageSpacing.add(spacing);
        }
    }

    private void prepareDocLen() {
        float length = 0;
        for (int i = 0; i < getPagesCount(); i++) {
            SizeF pageSize = pageSizes.get(i);
            length += isVertical ? pageSize.getHeight() : pageSize.getWidth();
            if (autoSpacing) {
                length += pageSpacing.get(i);
            } else if (i < getPagesCount() - 1) {
                length += spacingPx;
            }
        }
        documentLength = length;
    }

    private void preparePagesOffset() {
        pageOffsets.clear();
        float offset = 0;
        for (int i = 0; i < getPagesCount(); i++) {
            SizeF pageSize = pageSizes.get(i);
            float size = isVertical ? pageSize.getHeight() : pageSize.getWidth();
            if (autoSpacing) {
                offset += pageSpacing.get(i) / 2f;
                if (i == 0) {
                    offset -= spacingPx / 2f;
                } else if (i == getPagesCount() - 1) {
                    offset += spacingPx / 2f;
                }
                pageOffsets.add(offset);
                offset += size + pageSpacing.get(i) / 2f;
            } else {
                pageOffsets.add(offset);
                offset += size + spacingPx;
            }
        }
    }

    public float getDocLen(float zoom) {
        return documentLength * zoom;
    }

    public int getTopPaddingPx() {
        return topPaddingPx;
    }

    public int getBottomPaddingPx() {
        return bottomPaddingPx;
    }

    /**
     * Get the page's height if swiping vertical, or width if swiping horizontal.
     */
    public float getPageLength(int pageIndex, float zoom) {
        SizeF size = getPageSize(pageIndex);
        return (isVertical ? size.getHeight() : size.getWidth()) * zoom;
    }

    public float getPageSpacing(int pageIndex, float zoom) {
        float spacing = autoSpacing ? pageSpacing.get(pageIndex) : spacingPx;
        return spacing * zoom;
    }

    /** Get primary page offset, that is Y for vertical scroll and X for horizontal scroll */
    public float getPageOffset(int pageIndex, float zoom) {
        int docPage = documentPage(pageIndex);
        if (docPage < 0) {
            return 0;
        }
        return pageOffsets.get(pageIndex) * zoom;
    }

    /** Get secondary page offset, that is X for vertical scroll and Y for horizontal scroll */
    public float getSecondaryPageOffset(int pageIndex, float zoom) {
        SizeF pageSize = getPageSize(pageIndex);
        if (isVertical) {
            float maxWidth = getMaxPageWidth();
            return zoom * (maxWidth - pageSize.getWidth()) / 2; //x
        } else {
            float maxHeight = getMaxPageHeight();
            return zoom * (maxHeight - pageSize.getHeight()) / 2; //y
        }
    }

    public int getPageAtOffset(float offset, float zoom) {
        int currentPage = 0;
        for (int i = 0; i < getPagesCount(); i++) {
            float off = pageOffsets.get(i) * zoom - getPageSpacing(i, zoom) / 2f;
            if (off >= offset) {
                break;
            }
            currentPage++;
        }
        return --currentPage >= 0 ? currentPage : 0;
    }

    public boolean openPage(int pageIndex) throws PageRenderingException {
        int docPage = documentPage(pageIndex);
        if (docPage < 0) {
            return false;
        }

        synchronized (lock) {
            if (openedPdfPages.get(docPage) == null) {
                try {
                    PdfPage page = pdfDocument.openPage(docPage);
                    if (page != null) {
                        openedPdfPages.put(docPage, page);
                        openedPages.put(docPage, true);
                        return true;
                    }
                } catch (Exception e) {
                    openedPages.put(docPage, false);
                    throw new PageRenderingException(pageIndex, e);
                }
            }
            return false;
        }
    }

    public boolean pageHasError(int pageIndex) {
        int docPage = documentPage(pageIndex);
        return !openedPages.get(docPage, false);
    }

    public void renderPageBitmap(Bitmap bitmap, int pageIndex, Rect bounds, boolean annotationRendering) {
        int docPage = documentPage(pageIndex);
        synchronized (lock) {
            PdfPage page = openedPdfPages.get(docPage);
            if (page != null) {
                page.renderPageBitmap(bitmap, bounds.left, bounds.top, bounds.width(), bounds.height(), annotationRendering, false, 0, 0xFFFFFFFF);
            } else {
                try (PdfPage tempPage = pdfDocument.openPage(docPage)) {
                    if (tempPage != null) {
                        tempPage.renderPageBitmap(bitmap, bounds.left, bounds.top, bounds.width(), bounds.height(), annotationRendering, false, 0, 0xFFFFFFFF);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    public Meta getMetaData() {
        if (pdfDocument == null) {
            return null;
        }
        return pdfDocument.getDocumentMeta();
    }

    public List<Bookmark> getBookmarks() {
        if (pdfDocument == null) {
            return new ArrayList<>();
        }
        return pdfDocument.getTableOfContents();
    }

    public List<Link> getPageLinks(int pageIndex) {
        int docPage = documentPage(pageIndex);
        synchronized (lock) {
            PdfPage page = openedPdfPages.get(docPage);
            if (page != null) {
                return page.getPageLinks();
            }
            try (PdfPage tempPage = pdfDocument.openPage(docPage)) {
                return tempPage != null ? tempPage.getPageLinks() : new ArrayList<>();
            } catch (Exception e) {
                return new ArrayList<>();
            }
        }
    }

    public RectF mapRectToDevice(int pageIndex, int startX, int startY, int sizeX, int sizeY,
                                 RectF rect) {
        int docPage = documentPage(pageIndex);
        synchronized (lock) {
            PdfPage page = openedPdfPages.get(docPage);
            if (page != null) {
                Rect mappedRect = page.mapRectToDevice(startX, startY, sizeX, sizeY, 0, rect);
                return new RectF(mappedRect);
            }
            try (PdfPage tempPage = pdfDocument.openPage(docPage)) {
                if (tempPage != null) {
                    Rect mappedRect = tempPage.mapRectToDevice(startX, startY, sizeX, sizeY, 0, rect);
                    return new RectF(mappedRect);
                }
            } catch (Exception ignored) {}
            return new RectF();
        }
    }

    // --- NATIVE FPDFTEXT LAYER FOR TEXT SELECTION ---

    public PdfTextPage openTextPage(int pageIndex) {
        int docPage = documentPage(pageIndex);
        if (docPage < 0) return null;
        synchronized (lock) {
            PdfTextPage textPage = openedPdfTextPages.get(docPage);
            if (textPage == null) {
                PdfPage page = openedPdfPages.get(docPage);
                if (page == null) {
                    try {
                        openPage(pageIndex);
                        page = openedPdfPages.get(docPage);
                    } catch (Exception ignored) {}
                }
                if (page != null) {
                    try {
                        textPage = page.openTextPage();
                        if (textPage != null) {
                            openedPdfTextPages.put(docPage, textPage);
                        }
                    } catch (Exception ignored) {}
                }
            }
            return textPage;
        }
    }

    public int getTextPageCountChars(int pageIndex) {
        synchronized (lock) {
            try {
                PdfTextPage textPage = openTextPage(pageIndex);
                return textPage != null ? textPage.textPageCountChars() : 0;
            } catch (Exception ignored) {
                return 0;
            }
        }
    }

    public String getTextPageText(int pageIndex, int startIndex, int length) {
        synchronized (lock) {
            try {
                PdfTextPage textPage = openTextPage(pageIndex);
                if (textPage != null && startIndex >= 0 && length > 0) {
                    int total = textPage.textPageCountChars();
                    if (startIndex < total) {
                        int safeLength = Math.min(length, total - startIndex);
                        return textPage.textPageGetText(startIndex, safeLength);
                    }
                }
            } catch (Exception ignored) {}
            return "";
        }
    }

    public int getTextPageCharIndexAtPos(int pageIndex, double x, double y, double xTolerance, double yTolerance) {
        synchronized (lock) {
            try {
                PdfTextPage textPage = openTextPage(pageIndex);
                return textPage != null ? textPage.textPageGetCharIndexAtPos(x, y, xTolerance, yTolerance) : -1;
            } catch (Exception ignored) {
                return -1;
            }
        }
    }

    public RectF getTextPageCharBox(int pageIndex, int charIndex) {
        synchronized (lock) {
            try {
                PdfTextPage textPage = openTextPage(pageIndex);
                if (textPage != null && charIndex >= 0) {
                    return textPage.textPageGetCharBox(charIndex);
                }
            } catch (Exception ignored) {}
            return null;
        }
    }

    public List<RectF> getTextPageRects(int pageIndex, int startIndex, int count) {
        synchronized (lock) {
            List<RectF> rects = new ArrayList<>();
            try {
                PdfTextPage textPage = openTextPage(pageIndex);
                if (textPage != null && startIndex >= 0 && count > 0) {
                    int total = textPage.textPageCountChars();
                    if (startIndex < total) {
                        int safeCount = Math.min(count, total - startIndex);
                        int rectCount = textPage.textPageCountRects(startIndex, safeCount);
                        for (int i = 0; i < rectCount; i++) {
                            RectF rect = textPage.textPageGetRect(i);
                            if (rect != null) {
                                rects.add(rect);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
            return rects;
        }
    }

    /**
     * Extracts full plain text of the given page for search indexing.
     * Uses an isolated native text page and closes it immediately to ensure zero memory accumulation.
     */
    public String extractPageTextForSearch(int pageIndex) {
        int docPage = documentPage(pageIndex);
        if (docPage < 0) return "";
        synchronized (lock) {
            // If already cached by active text selection, read directly
            PdfTextPage cachedTextPage = openedPdfTextPages.get(docPage);
            if (cachedTextPage != null) {
                int count = cachedTextPage.textPageCountChars();
                return count > 0 ? cachedTextPage.textPageGetText(0, count) : "";
            }

            // Otherwise, open transient text page and close immediately in finally
            PdfPage page = openedPdfPages.get(docPage);
            boolean openedPageTransiently = false;
            if (page == null) {
                try {
                    openPage(pageIndex);
                    page = openedPdfPages.get(docPage);
                    openedPageTransiently = true;
                } catch (Exception ignored) {
                    return "";
                }
            }
            if (page == null) return "";

            PdfTextPage transientTextPage = null;
            try {
                transientTextPage = page.openTextPage();
                if (transientTextPage != null) {
                    int charCount = transientTextPage.textPageCountChars();
                    return charCount > 0 ? transientTextPage.textPageGetText(0, charCount) : "";
                }
            } catch (Exception ignored) {
            } finally {
                if (transientTextPage != null) {
                    try {
                        transientTextPage.close();
                    } catch (Exception ignored) {}
                }
                if (openedPageTransiently && page != null) {
                    try {
                        page.close();
                        openedPdfPages.remove(docPage);
                        openedPages.delete(docPage);
                    } catch (Exception ignored) {}
                }
            }
            return "";
        }
    }

    /**
     * Resolves character index ranges on a page into normalized 0f..1f bounding boxes (top-left origin).
     * Synchronized on lock and closes any transient text pages immediately.
     */
    public List<RectF> getNormalizedTextRectsForRange(int pageIndex, int startIndex, int length) {
        if (length <= 0) return Collections.emptyList();
        int docPage = documentPage(pageIndex);
        if (docPage < 0) return Collections.emptyList();

        Size originalSize = getOriginalPageSize(pageIndex);
        float origW = originalSize != null ? originalSize.getWidth() : 0f;
        float origH = originalSize != null ? originalSize.getHeight() : 0f;
        if (origW <= 0f || origH <= 0f) return Collections.emptyList();

        List<RectF> rawPdfRects = new ArrayList<>();
        synchronized (lock) {
            PdfTextPage cachedTextPage = openedPdfTextPages.get(docPage);
            if (cachedTextPage != null) {
                int count = cachedTextPage.textPageCountRects(startIndex, length);
                for (int i = 0; i < count; i++) {
                    RectF r = cachedTextPage.textPageGetRect(i);
                    if (r != null) rawPdfRects.add(r);
                }
            } else {
                PdfPage page = openedPdfPages.get(docPage);
                boolean openedPageTransiently = false;
                if (page == null) {
                    try {
                        openPage(pageIndex);
                        page = openedPdfPages.get(docPage);
                        openedPageTransiently = true;
                    } catch (Exception ignored) {
                        return Collections.emptyList();
                    }
                }
                if (page == null) return Collections.emptyList();

                PdfTextPage transientTextPage = null;
                try {
                    transientTextPage = page.openTextPage();
                    if (transientTextPage != null) {
                        int count = transientTextPage.textPageCountRects(startIndex, length);
                        for (int i = 0; i < count; i++) {
                            RectF r = transientTextPage.textPageGetRect(i);
                            if (r != null) rawPdfRects.add(r);
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    if (transientTextPage != null) {
                        try {
                            transientTextPage.close();
                        } catch (Exception ignored) {}
                    }
                    if (openedPageTransiently && page != null) {
                        try {
                            page.close();
                            openedPdfPages.remove(docPage);
                            openedPages.delete(docPage);
                        } catch (Exception ignored) {}
                    }
                }
            }
        }

        if (rawPdfRects.isEmpty()) return Collections.emptyList();

        // Merge adjacent character boxes on the same line in PDF coordinate space
        List<RectF> mergedPdfRects = new ArrayList<>();
        RectF currentLine = null;

        for (RectF rect : rawPdfRects) {
            if (currentLine == null) {
                currentLine = new RectF(rect);
            } else {
                float lineCenterY = currentLine.centerY();
                float rectCenterY = rect.centerY();
                float lineHeight = Math.max(Math.abs(currentLine.height()), Math.abs(rect.height()));

                if (Math.abs(lineCenterY - rectCenterY) < lineHeight * 0.6f) {
                    currentLine.left = Math.min(currentLine.left, rect.left);
                    currentLine.right = Math.max(currentLine.right, rect.right);
                    currentLine.top = Math.max(currentLine.top, rect.top);
                    currentLine.bottom = Math.min(currentLine.bottom, rect.bottom);
                } else {
                    mergedPdfRects.add(currentLine);
                    currentLine = new RectF(rect);
                }
            }
        }
        if (currentLine != null) {
            mergedPdfRects.add(currentLine);
        }

        List<RectF> normalizedRects = new ArrayList<>(mergedPdfRects.size());
        for (RectF r : mergedPdfRects) {
            float minX = Math.min(r.left, r.right);
            float maxX = Math.max(r.left, r.right);
            float minY = Math.min(r.top, r.bottom);
            float maxY = Math.max(r.top, r.bottom);

            // In PDFium coordinates: (0,0) is bottom-left, y goes up
            // Normalized page coordinates: (0,0) is top-left, y goes down
            float normLeft = (minX / origW);
            float normRight = (maxX / origW);
            float normTop = (origH - maxY) / origH;
            float normBottom = (origH - minY) / origH;

            float clampedLeft = Math.max(0f, Math.min(1f, normLeft));
            float clampedTop = Math.max(0f, Math.min(1f, normTop));
            float clampedRight = Math.max(0f, Math.min(1f, normRight));
            float clampedBottom = Math.max(0f, Math.min(1f, normBottom));

            if (clampedRight > clampedLeft && clampedBottom > clampedTop) {
                normalizedRects.add(new RectF(clampedLeft, clampedTop, clampedRight, clampedBottom));
            }
        }

        return normalizedRects;
    }

    public void dispose() {
        synchronized (lock) {
            for (int i = 0; i < openedPdfTextPages.size(); i++) {
                PdfTextPage textPage = openedPdfTextPages.valueAt(i);
                if (textPage != null) {
                    try {
                        textPage.close();
                    } catch (Exception ignored) {}
                }
            }
            openedPdfTextPages.clear();

            for (int i = 0; i < openedPdfPages.size(); i++) {
                PdfPage page = openedPdfPages.valueAt(i);
                if (page != null) {
                    try {
                        page.close();
                    } catch (Exception ignored) {}
                }
            }
            openedPdfPages.clear();

            if (pdfDocument != null) {
                try {
                    pdfDocument.close();
                } catch (Exception ignored) {}
            }
            pdfDocument = null;
            originalUserPages = null;
        }
    }

    /**
     * Given the UserPage number, this method restrict it
     * to be sure it's an existing page. It takes care of
     * using the user defined pages if any.
     *
     * @param userPage A page number.
     * @return A restricted valid page number (example : -2 => 0)
     */
    public int determineValidPageNumberFrom(int userPage) {
        if (userPage <= 0) {
            return 0;
        }
        if (originalUserPages != null) {
            if (userPage >= originalUserPages.length) {
                return originalUserPages.length - 1;
            }
        } else {
            if (userPage >= getPagesCount()) {
                return getPagesCount() - 1;
            }
        }
        return userPage;
    }

    public int documentPage(int userPage) {
        int documentPage = userPage;
        if (originalUserPages != null) {
            if (userPage < 0 || userPage >= originalUserPages.length) {
                return -1;
            } else {
                documentPage = originalUserPages[userPage];
            }
        }

        if (documentPage < 0 || userPage >= getPagesCount()) {
            return -1;
        }

        return documentPage;
    }
}
