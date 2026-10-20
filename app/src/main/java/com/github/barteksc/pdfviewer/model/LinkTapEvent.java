package com.github.barteksc.pdfviewer.model;

import android.graphics.RectF;
import io.legere.pdfiumandroid.api.Link;

public class LinkTapEvent {
    private float originalX;
    private float originalY;
    private float documentX;
    private float documentY;
    private RectF mappedLinkRect;
    private Link link;

    public LinkTapEvent(float originalX, float originalY, float documentX, float documentY, RectF mappedLinkRect, Link link) {
        this.originalX = originalX;
        this.originalY = originalY;
        this.documentX = documentX;
        this.documentY = documentY;
        this.mappedLinkRect = mappedLinkRect;
        this.link = link;
    }

    public float getOriginalX() {
        return originalX;
    }

    public float getOriginalY() {
        return originalY;
    }

    public float getDocumentX() {
        return documentX;
    }

    public float getDocumentY() {
        return documentY;
    }

    public RectF getMappedLinkRect() {
        return mappedLinkRect;
    }

    public Link getLink() {
        return link;
    }
}
