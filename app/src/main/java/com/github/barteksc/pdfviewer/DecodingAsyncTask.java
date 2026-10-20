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

import android.os.AsyncTask;

import com.github.barteksc.pdfviewer.source.DocumentSource;
import io.legere.pdfiumandroid.PdfDocument;
import io.legere.pdfiumandroid.PdfiumCore;
import com.github.barteksc.pdfviewer.util.Size;

import java.lang.ref.WeakReference;

class DecodingAsyncTask extends AsyncTask<Void, Void, Throwable> {

    private boolean cancelled;

    private WeakReference<PDFView> pdfViewReference;

    private PdfiumCore pdfiumCore;
    private String password;
    private DocumentSource docSource;
    private int[] userPages;
    private PdfFile pdfFile;

    // Cached UI properties for background thread safety
    private android.content.Context context;
    private com.github.barteksc.pdfviewer.util.FitPolicy pageFitPolicy;
    private com.github.barteksc.pdfviewer.util.Size viewSize;
    private boolean isSwipeVertical;
    private int spacingPx;
    private boolean isAutoSpacingEnabled;
    private boolean isFitEachPage;
    private int documentTopPaddingPx;

    DecodingAsyncTask(DocumentSource docSource, String password, int[] userPages, PDFView pdfView, PdfiumCore pdfiumCore) {
        this.docSource = docSource;
        this.userPages = userPages;
        this.cancelled = false;
        this.pdfViewReference = new WeakReference<>(pdfView);
        this.password = password;
        this.pdfiumCore = pdfiumCore;

        // Cache all view-related data on the UI thread
        this.context = pdfView.getContext().getApplicationContext();
        this.pageFitPolicy = pdfView.getPageFitPolicy();
        this.viewSize = new Size(pdfView.getWidth(), pdfView.getHeight());
        this.isSwipeVertical = pdfView.isSwipeVertical();
        this.spacingPx = pdfView.getSpacingPx();
        this.isAutoSpacingEnabled = pdfView.isAutoSpacingEnabled();
        this.isFitEachPage = pdfView.isFitEachPage();
        this.documentTopPaddingPx = pdfView.getDocumentTopPaddingPx();
    }

    @Override
    protected Throwable doInBackground(Void... params) {
        try {
            PDFView pdfView = pdfViewReference.get();
            if (pdfView != null) {
                PdfDocument pdfDocument = docSource.createDocument(context, pdfiumCore, password);
                pdfFile = new PdfFile(pdfiumCore, pdfDocument, pageFitPolicy, viewSize,
                        userPages, isSwipeVertical, spacingPx, isAutoSpacingEnabled,
                        isFitEachPage, documentTopPaddingPx);
                return null;
            } else {
                return new NullPointerException("pdfView == null");
            }

        } catch (Throwable t) {
            return t;
        }
    }

    @Override
    protected void onPostExecute(Throwable t) {
        PDFView pdfView = pdfViewReference.get();
        if (pdfView != null) {
            if (t != null) {
                pdfView.loadError(t);
                return;
            }
            if (!cancelled) {
                pdfView.loadComplete(pdfFile);
            }
        }
    }

    @Override
    protected void onCancelled() {
        cancelled = true;
    }
}
