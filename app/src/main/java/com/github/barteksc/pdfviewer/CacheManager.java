/**
 * Copyright 2016 Bartosz Schiller
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
import androidx.annotation.Nullable;

import com.github.barteksc.pdfviewer.model.PagePart;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

import static com.github.barteksc.pdfviewer.util.Constants.Cache.CACHE_SIZE;
import static com.github.barteksc.pdfviewer.util.Constants.Cache.THUMBNAILS_CACHE_SIZE;

class CacheManager {

    private final PriorityQueue<PagePart> passiveCache;

    private final PriorityQueue<PagePart> activeCache;

    private final List<PagePart> thumbnails;

    private final Object passiveActiveLock = new Object();

    private final PagePartComparator orderComparator = new PagePartComparator();

    public CacheManager() {
        activeCache = new PriorityQueue<>(CACHE_SIZE, orderComparator);
        passiveCache = new PriorityQueue<>(CACHE_SIZE, orderComparator);
        thumbnails = new ArrayList<>();
    }

    public void cachePart(PagePart part) {
        synchronized (passiveActiveLock) {
            List<PagePart> toRemove = new ArrayList<>();
            for (PagePart passivePart : passiveCache) {
                if (passivePart.getPage() == part.getPage() &&
                        RectF.intersects(passivePart.getPageRelativeBounds(), part.getPageRelativeBounds())) {
                    toRemove.add(passivePart);
                }
            }
            for (PagePart removePart : toRemove) {
                passiveCache.remove(removePart);
                if (removePart.getRenderedBitmap() != null && !removePart.getRenderedBitmap().isRecycled()) {
                    removePart.getRenderedBitmap().recycle();
                }
            }

            // If cache too big, remove and recycle
            makeAFreeSpace();

            // Then add part
            activeCache.offer(part);
        }
    }

    public void makeANewSet() {
        synchronized (passiveActiveLock) {
            if (activeCache.isEmpty()) {
                return;
            }
            passiveCache.addAll(activeCache);
            activeCache.clear();
        }
    }

    private void makeAFreeSpace() {
        synchronized (passiveActiveLock) {
            while ((activeCache.size() + passiveCache.size()) >= CACHE_SIZE &&
                    !passiveCache.isEmpty()) {
                PagePart part = passiveCache.poll();
                part.getRenderedBitmap().recycle();
            }

            while ((activeCache.size() + passiveCache.size()) >= CACHE_SIZE &&
                    !activeCache.isEmpty()) {
                activeCache.poll().getRenderedBitmap().recycle();
            }
        }
    }

    public void cacheThumbnail(PagePart part) {
        synchronized (thumbnails) {
            // If cache too big, remove and recycle
            while (thumbnails.size() >= THUMBNAILS_CACHE_SIZE) {
                thumbnails.remove(0).getRenderedBitmap().recycle();
            }

            // Then add thumbnail
            addWithoutDuplicates(thumbnails, part);
        }

    }

    public boolean upPartIfContained(int page, RectF pageRelativeBounds, int toOrder) {
        synchronized (passiveActiveLock) {
            PagePart found = find(passiveCache, page, pageRelativeBounds);
            if (found != null) {
                passiveCache.remove(found);
                found.setCacheOrder(toOrder);
                activeCache.offer(found);
                return true;
            }

            return find(activeCache, page, pageRelativeBounds) != null;
        }
    }

    /**
     * Return true if already contains the described PagePart
     */
    public boolean containsThumbnail(int page, RectF pageRelativeBounds) {
        synchronized (thumbnails) {
            for (int i = 0; i < thumbnails.size(); i++) {
                PagePart part = thumbnails.get(i);
                if (part.getPage() == page &&
                        part.getPageRelativeBounds().left == pageRelativeBounds.left &&
                        part.getPageRelativeBounds().right == pageRelativeBounds.right &&
                        part.getPageRelativeBounds().top == pageRelativeBounds.top &&
                        part.getPageRelativeBounds().bottom == pageRelativeBounds.bottom) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Add part if it doesn't exist, recycle bitmap otherwise
     */
    private void addWithoutDuplicates(Collection<PagePart> collection, PagePart newPart) {
        for (PagePart part : collection) {
            if (part.equals(newPart)) {
                newPart.getRenderedBitmap().recycle();
                return;
            }
        }
        collection.add(newPart);
    }

    @Nullable
    private static PagePart find(PriorityQueue<PagePart> vector, int page, RectF bounds) {
        for (PagePart part : vector) {
            if (part.getPage() == page &&
                    part.getPageRelativeBounds().left == bounds.left &&
                    part.getPageRelativeBounds().right == bounds.right &&
                    part.getPageRelativeBounds().top == bounds.top &&
                    part.getPageRelativeBounds().bottom == bounds.bottom) {
                return part;
            }
        }
        return null;
    }

    public void getPageParts(List<PagePart> target) {
        synchronized (passiveActiveLock) {
            target.clear();
            target.addAll(passiveCache);
            target.addAll(activeCache);
        }
    }

    public void getThumbnails(List<PagePart> target) {
        synchronized (thumbnails) {
            target.clear();
            target.addAll(thumbnails);
        }
    }

    public List<PagePart> getPageParts() {
        synchronized (passiveActiveLock) {
            List<PagePart> parts = new ArrayList<>(passiveCache);
            parts.addAll(activeCache);
            return parts;
        }
    }

    public List<PagePart> getThumbnails() {
        synchronized (thumbnails) {
            return new ArrayList<>(thumbnails);
        }
    }

    public void recycle() {
        synchronized (passiveActiveLock) {
            for (PagePart part : passiveCache) {
                part.getRenderedBitmap().recycle();
            }
            passiveCache.clear();
            for (PagePart part : activeCache) {
                part.getRenderedBitmap().recycle();
            }
            activeCache.clear();
        }
        synchronized (thumbnails) {
            for (PagePart part : thumbnails) {
                part.getRenderedBitmap().recycle();
            }
            thumbnails.clear();
        }
    }

    class PagePartComparator implements Comparator<PagePart> {
        @Override
        public int compare(PagePart part1, PagePart part2) {
            if (part1.getCacheOrder() == part2.getCacheOrder()) {
                return 0;
            }
            // Evict higher cacheOrder (off-screen preload tiles) first when polling
            return part1.getCacheOrder() < part2.getCacheOrder() ? 1 : -1;
        }
    }

}
