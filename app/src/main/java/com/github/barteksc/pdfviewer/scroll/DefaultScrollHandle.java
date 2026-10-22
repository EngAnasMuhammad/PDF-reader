package com.github.barteksc.pdfviewer.scroll;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.github.barteksc.pdfviewer.PDFView;
import com.anasdev.pdfreader.R;
import com.github.barteksc.pdfviewer.util.Util;

public class DefaultScrollHandle extends RelativeLayout implements ScrollHandle {

    private final static int HANDLE_LONG = 65;
    private final static int HANDLE_SHORT = 40;
    private final static int DEFAULT_TEXT_SIZE = 16;

    private float relativeHandlerMiddle = 0f;

    protected TextView textView;
    protected Context context;
    private boolean inverted;
    private PDFView pdfView;
    private float currentPos;

    private Handler handler = new Handler();
    private Runnable hidePageScrollerRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };

    public DefaultScrollHandle(Context context) {
        this(context, false);
    }

    public DefaultScrollHandle(Context context, boolean inverted) {
        super(context);
        this.context = context;
        this.inverted = inverted;
        textView = new TextView(context);
        setVisibility(INVISIBLE);
        setTextColor(Color.BLACK);
        setTextSize(DEFAULT_TEXT_SIZE);
    }

    private boolean isThemeDark = false;

    public void setHandleTheme(boolean isDarkTheme) {
        if (this.isThemeDark != isDarkTheme) {
            this.isThemeDark = isDarkTheme;
            if (pdfView != null) {
                setupLayout(pdfView);
            }
        }
    }

    @Override
    public void setupLayout(PDFView pdfView) {
        int align, width, height;
        
        // Colors dictated by setHandleTheme() called dynamically from Compose
        int bgColor = isThemeDark ? Color.parseColor("#CCFFFFFF") : Color.parseColor("#CC000000"); // 80% opacity
        int textColor = isThemeDark ? Color.BLACK : Color.WHITE;
        int borderColor = isThemeDark ? Color.parseColor("#33000000") : Color.parseColor("#33FFFFFF");

        // Build Pill Shape Drawable
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        background.setColor(bgColor);
        
        int strokeWidth = Math.max(1, Util.getDP(context, 1));
        background.setStroke(strokeWidth, borderColor);

        float radius = Util.getDP(context, 20); // 20dp fully rounded corners
        
        if (pdfView.isSwipeVertical()) {
            width = HANDLE_LONG;
            height = HANDLE_SHORT;
            if (inverted) { // Positioned on left ONLY if inverted is explicitly true
                align = ALIGN_PARENT_LEFT;
                background.setCornerRadii(new float[]{0, 0, radius, radius, radius, radius, 0, 0});
            } else { // Positioned on right ALWAYS in portrait/vertical swipe mode
                align = ALIGN_PARENT_RIGHT;
                background.setCornerRadii(new float[]{radius, radius, 0, 0, 0, 0, radius, radius});
            }
        } else {
            width = HANDLE_SHORT;
            height = HANDLE_LONG;
            if (inverted) {
                align = ALIGN_PARENT_TOP;
                background.setCornerRadii(new float[]{0, 0, 0, 0, radius, radius, radius, radius});
            } else {
                align = ALIGN_PARENT_BOTTOM;
                background.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
            }
        }

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
            setBackgroundDrawable(background);
        } else {
            setBackground(background);
        }

        LayoutParams lp = new LayoutParams(Util.getDP(context, width), Util.getDP(context, height));
        lp.setMargins(0, 0, 0, 0);

        LayoutParams tvlp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tvlp.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);

        textView.setTextColor(textColor);
        if (textView.getParent() != null) {
            ((ViewGroup) textView.getParent()).removeView(textView);
        }
        addView(textView, tvlp);

        lp.addRule(align);
        
        // CRITICAL FIX: To prevent a crash, remove ourselves from the old parent before adding again
        if (this.getParent() != null) {
            ((ViewGroup) this.getParent()).removeView(this);
        }
        pdfView.addView(this, lp);

        this.pdfView = pdfView;
    }

    @Override
    public void destroyLayout() {
        pdfView.removeView(this);
    }

    @Override
    public void setScroll(float position) {
        if (!shown()) {
            show();
        } else {
            handler.removeCallbacks(hidePageScrollerRunnable);
        }
        if (pdfView != null) {
            float minPos = 0;
            if (pdfView.isSwipeVertical() && pdfView.getDocumentTopPaddingPx() > 0) {
                minPos = pdfView.getDocumentTopPaddingPx();
            }
            float pdfViewSize = pdfView.isSwipeVertical() ? pdfView.getHeight() : pdfView.getWidth();
            float handleSize = pdfView.isSwipeVertical() ? getHeight() : getWidth();
            float maxPos = pdfViewSize - handleSize - 100;
            if (maxPos < minPos) maxPos = minPos;

            setPosition(minPos + (maxPos - minPos) * position + relativeHandlerMiddle);
        }
    }

    private void setPosition(float pos) {
        if (Float.isInfinite(pos) || Float.isNaN(pos)) {
            return;
        }
        float pdfViewSize;
        if (pdfView.isSwipeVertical()) {
            pdfViewSize = pdfView.getHeight();
        } else {
            pdfViewSize = pdfView.getWidth();
        }
        pos -= relativeHandlerMiddle;

        // Apply TopBar avoidance limit if swipe is vertical
        float minPos = 0;
        if (pdfView.isSwipeVertical() && pdfView.getDocumentTopPaddingPx() > 0) {
            minPos = pdfView.getDocumentTopPaddingPx();
        }

        float maxPos = pdfViewSize - 100 - (pdfView.isSwipeVertical() ? getHeight() : getWidth());
        if (maxPos < minPos) maxPos = minPos;

        if (pos < minPos) {
            pos = minPos;
        } else if (pos > maxPos) {
            pos = maxPos;
        }

        if (pdfView.isSwipeVertical()) {
            setY(pos);
        } else {
            setX(pos);
        }

        calculateMiddle();
        invalidate();
    }

    private void calculateMiddle() {
        float pos, viewSize, pdfViewSize;
        if (pdfView.isSwipeVertical()) {
            pos = getY();
            viewSize = getHeight();
            pdfViewSize = pdfView.getHeight();
        } else {
            pos = getX();
            viewSize = getWidth();
            pdfViewSize = pdfView.getWidth();
        }
        relativeHandlerMiddle = ((pos + relativeHandlerMiddle) / pdfViewSize) * viewSize;
    }

    @Override
    public void hideDelayed() {
        handler.postDelayed(hidePageScrollerRunnable, 1000);
    }

    @Override
    public void setPageNum(int pageNum) {
        String text = String.valueOf(pageNum);
        if (!textView.getText().equals(text)) {
            textView.setText(text);
        }
    }

    @Override
    public boolean shown() {
        return getVisibility() == VISIBLE;
    }

    @Override
    public void show() {
        setVisibility(VISIBLE);
    }

    @Override
    public void hide() {
        setVisibility(INVISIBLE);
    }

    public void setTextColor(int color) {
        textView.setTextColor(color);
    }

    /**
     * @param size text size in dp
     */
    public void setTextSize(int size) {
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
    }

    private boolean isPDFViewReady() {
        return pdfView != null && pdfView.getPageCount() > 0 && !pdfView.documentFitsView();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        if (!isPDFViewReady()) {
            return super.onTouchEvent(event);
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                pdfView.stopFling();
                handler.removeCallbacks(hidePageScrollerRunnable);
                if (pdfView.isSwipeVertical()) {
                    currentPos = event.getRawY() - getY();
                } else {
                    currentPos = event.getRawX() - getX();
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                float minPos = 0;
                if (pdfView.isSwipeVertical() && pdfView.getDocumentTopPaddingPx() > 0) {
                    minPos = pdfView.getDocumentTopPaddingPx();
                }

                if (pdfView.isSwipeVertical()) {
                    setPosition(event.getRawY() - currentPos + relativeHandlerMiddle);
                    float maxPos = pdfView.getHeight() - getHeight() - 100;
                    if (maxPos <= minPos) maxPos = minPos + 1;
                    float offset = (getY() - minPos) / (maxPos - minPos);
                    pdfView.setPositionOffset(offset, false);
                } else {
                    setPosition(event.getRawX() - currentPos + relativeHandlerMiddle);
                    float maxPos = pdfView.getWidth() - getWidth() - 100;
                    if (maxPos <= minPos) maxPos = minPos + 1;
                    float offset = (getX() - minPos) / (maxPos - minPos);
                    pdfView.setPositionOffset(offset, false);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                hideDelayed();
                pdfView.performPageSnap();
                return true;
        }

        return super.onTouchEvent(event);
    }
}
