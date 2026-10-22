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

import android.graphics.RectF;

import com.github.barteksc.pdfviewer.util.Constants;
import com.github.barteksc.pdfviewer.util.MathUtils;
import com.github.barteksc.pdfviewer.util.Util;
import com.github.barteksc.pdfviewer.util.SizeF;

import java.util.LinkedList;
import java.util.List;

import static com.github.barteksc.pdfviewer.util.Constants.Cache.CACHE_SIZE;
import static com.github.barteksc.pdfviewer.util.Constants.PRELOAD_OFFSET;

class PagesLoader {

    private PDFView pdfView;
    private int cacheOrder;
    private float xOffset;
    private float yOffset;
    private float pageRelativePartWidth;
    private float pageRelativePartHeight;
    private final RectF thumbnailRect = new RectF(0, 0, 1, 1);
    private final int preloadOffset;

    private class Holder {
        int row;
        int col;

        @Override
        public String toString() {
            return "Holder{" +
                    "row=" + row +
                    ", col=" + col +
                    '}';
        }
    }

    private class RenderRange {
        int page;
        GridSize gridSize;
        Holder leftTop;
        Holder rightBottom;

        RenderRange() {
            this.page = 0;
            this.gridSize = new GridSize();
            this.leftTop = new Holder();
            this.rightBottom = new Holder();
        }

        @Override
        public String toString() {
            return "RenderRange{" +
                    "page=" + page +
                    ", gridSize=" + gridSize +
                    ", leftTop=" + leftTop +
                    ", rightBottom=" + rightBottom +
                    '}';
        }
    }

    private class GridSize {
        int rows;
        int cols;

        @Override
        public String toString() {
            return "GridSize{" +
                    "rows=" + rows +
                    ", cols=" + cols +
                    '}';
        }
    }

    PagesLoader(PDFView pdfView) {
        this.pdfView = pdfView;
        this.preloadOffset = Util.getDP(pdfView.getContext(), PRELOAD_OFFSET);
    }

    private void getPageColsRows(GridSize grid, int pageIndex) {
        SizeF size = pdfView.pdfFile.getPageSize(pageIndex);
        float ratioX = 1f / size.getWidth();
        float ratioY = 1f / size.getHeight();
        final float partHeight = (Constants.PART_SIZE * ratioY) / pdfView.getZoom();
        final float partWidth = (Constants.PART_SIZE * ratioX) / pdfView.getZoom();
        grid.rows = MathUtils.ceil(1f / partHeight);
        grid.cols = MathUtils.ceil(1f / partWidth);
    }

    private void calculatePartSize(GridSize grid) {
        pageRelativePartWidth = 1f / (float) grid.cols;
        pageRelativePartHeight = 1f / (float) grid.rows;
    }


    /**
     * calculate the render range of each page
     */
    private List<RenderRange> getRenderRangeList(float minX, float minY, float maxX, float maxY, boolean expandRing) {

        float offsetFirst = pdfView.isSwipeVertical() ? minY : minX;
        float offsetLast = pdfView.isSwipeVertical() ? maxY : maxX;

        int firstPage = pdfView.pdfFile.getPageAtOffset(Math.max(0, offsetFirst), pdfView.getZoom());
        int lastPage = pdfView.pdfFile.getPageAtOffset(Math.max(0, offsetLast), pdfView.getZoom());

        List<RenderRange> renderRanges = new LinkedList<>();

        for (int page = firstPage; page <= lastPage; page++) {
            RenderRange range = new RenderRange();
            range.page = page;

            getPageColsRows(range.gridSize, range.page); // get the page's grid size that rows and cols
            SizeF scaledPageSize = pdfView.pdfFile.getScaledPageSize(range.page, pdfView.getZoom());
            float rowHeight = scaledPageSize.getHeight() / range.gridSize.rows;
            float colWidth = scaledPageSize.getWidth() / range.gridSize.cols;

            float pageOffset = pdfView.pdfFile.getPageOffset(page, pdfView.getZoom());
            float secondaryOffset = pdfView.pdfFile.getSecondaryPageOffset(page, pdfView.getZoom());

            float pageLeft = pdfView.isSwipeVertical() ? secondaryOffset : pageOffset;
            float pageTop = pdfView.isSwipeVertical() ? pageOffset : secondaryOffset;

            int ringPadding = expandRing ? 1 : 0;

            range.leftTop.col = Math.max(0, MathUtils.floor((minX - pageLeft) / colWidth) - ringPadding);
            range.rightBottom.col = Math.min(range.gridSize.cols - 1, MathUtils.floor((maxX - pageLeft) / colWidth) + ringPadding);

            range.leftTop.row = Math.max(0, MathUtils.floor((minY - pageTop) / rowHeight) - ringPadding);
            range.rightBottom.row = Math.min(range.gridSize.rows - 1, MathUtils.floor((maxY - pageTop) / rowHeight) + ringPadding);

            renderRanges.add(range);
        }

        return renderRanges;
    }

    private void loadVisible() {
        int parts = 0;

        // Pass 1: High Priority - On-Screen Visible Screen Bounds ONLY (0 preload offset)
        float minX = xOffset;
        float maxX = xOffset + pdfView.getWidth();
        float minY = yOffset;
        float maxY = yOffset + pdfView.getHeight();

        List<RenderRange> visibleRanges = getRenderRangeList(minX, minY, maxX, maxY, false);

        for (RenderRange range : visibleRanges) {
            loadThumbnail(range.page);
        }

        // Load visible on-screen tiles first without early abort
        for (RenderRange range : visibleRanges) {
            calculatePartSize(range.gridSize);
            parts += loadPage(range.page, range.leftTop.row, range.rightBottom.row, range.leftTop.col, range.rightBottom.col, CACHE_SIZE - parts);
        }

        // Pass 2: Low Priority - Surrounding Off-Screen Preload Tiles
        float scaledPreloadOffset = preloadOffset;
        float pMinX = xOffset - scaledPreloadOffset;
        float pMaxX = xOffset + pdfView.getWidth() + scaledPreloadOffset;
        float pMinY = yOffset - scaledPreloadOffset;
        float pMaxY = yOffset + pdfView.getHeight() + scaledPreloadOffset;

        List<RenderRange> preloadRanges = getRenderRangeList(pMinX, pMinY, pMaxX, pMaxY, true);

        for (RenderRange range : preloadRanges) {
            calculatePartSize(range.gridSize);
            parts += loadPage(range.page, range.leftTop.row, range.rightBottom.row, range.leftTop.col, range.rightBottom.col, CACHE_SIZE - parts);
            if (parts >= CACHE_SIZE) {
                break;
            }
        }
    }

    private int loadPage(int page, int firstRow, int lastRow, int firstCol, int lastCol,
                         int nbOfPartsLoadable) {
        int loaded = 0;
        for (int row = firstRow; row <= lastRow; row++) {
            for (int col = firstCol; col <= lastCol; col++) {
                if (loadCell(page, row, col, pageRelativePartWidth, pageRelativePartHeight)) {
                    loaded++;
                }
                if (loaded >= nbOfPartsLoadable) {
                    return loaded;
                }
            }
        }
        return loaded;
    }

    private boolean loadCell(int page, int row, int col, float pageRelativePartWidth, float pageRelativePartHeight) {

        float relX = pageRelativePartWidth * col;
        float relY = pageRelativePartHeight * row;
        float relWidth = pageRelativePartWidth;
        float relHeight = pageRelativePartHeight;

        if (relX + relWidth > 1) {
            relWidth = 1 - relX;
        }
        if (relY + relHeight > 1) {
            relHeight = 1 - relY;
        }
        RectF pageRelativeBounds = new RectF(relX, relY, relX + relWidth, relY + relHeight);

        float zoom = pdfView.getZoom();
        float renderWidth = pageRelativeBounds.width() * pdfView.pdfFile.getPageSize(page).getWidth() * zoom;
        float renderHeight = pageRelativeBounds.height() * pdfView.pdfFile.getPageSize(page).getHeight() * zoom;

        if (renderWidth > 0 && renderHeight > 0) {
            if (!pdfView.cacheManager.upPartIfContained(page, pageRelativeBounds, cacheOrder)) {
                pdfView.renderingHandler.addRenderingTask(page, renderWidth, renderHeight,
                        pageRelativeBounds, false, cacheOrder, pdfView.isBestQuality(),
                        pdfView.isAnnotationRendering());
            }

            cacheOrder++;
            return true;
        }
        return false;
    }

    private void loadThumbnail(int page) {
        SizeF pageSize = pdfView.pdfFile.getPageSize(page);
        float thumbnailWidth = pageSize.getWidth() * Constants.THUMBNAIL_RATIO;
        float thumbnailHeight = pageSize.getHeight() * Constants.THUMBNAIL_RATIO;
        if (!pdfView.cacheManager.containsThumbnail(page, thumbnailRect)) {
            pdfView.renderingHandler.addRenderingTask(page,
                    thumbnailWidth, thumbnailHeight, thumbnailRect,
                    true, 0, pdfView.isBestQuality(), pdfView.isAnnotationRendering());
        }
    }

    void loadPages() {
        cacheOrder = 1;
        xOffset = -pdfView.getCurrentXOffset();
        yOffset = -pdfView.getCurrentYOffset();

        loadVisible();
    }
}
