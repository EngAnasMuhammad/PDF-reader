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
package com.github.barteksc.pdfviewer.util;

public class Constants {

    public static boolean DEBUG_MODE = false;

    /** Between 0 and 1, the thumbnails quality (default 0.3). Increasing this value may cause performance decrease */
    public static float THUMBNAIL_RATIO = 0.5f;

    /**
     * The size of the rendered parts (default 256)
     * 512px is the sweet spot. 1024px takes too much raw RAM per tile,
     * while 256px has too much CPU/JNI overhead. 512 balances RAM vs Speed perfectly.
     */
    public static float PART_SIZE = 512;

    /** 
     * Part of document above and below screen that should be preloaded, in dp.
     * 1000dp preloads roughly ~1.5 pages ahead. This prevents the "blank block" 
     * rendering glitch without devouring memory like 2000dp did.
     */
    public static int PRELOAD_OFFSET = 1000;

    public static class Cache {

        /** The size of the cache (number of bitmaps kept)
         * 120 is more than enough for 512px tiles. 
         * Lowering this drastically drops RAM usage (by clearing old scrolled pages) 
         * without affecting the current visible/preloaded pages. */
        public static int CACHE_SIZE = 150;

        /** Keep thumbnail cache light */
        public static int THUMBNAILS_CACHE_SIZE = 16;
    }

    public static class Pinch {

        public static float MAXIMUM_ZOOM = 10;

        public static float MINIMUM_ZOOM = 1;

    }

}
