package com.beepalert;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Vertical volume-style fader. Drag up = higher level, drag down = lower. */
public class LevelView extends View {

    public interface OnLevelChangeListener { void onLevelChanged(int level); }

    private final int max;
    private int level;
    private OnLevelChangeListener listener;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public LevelView(Context ctx, int max, int initial) {
        super(ctx);
        this.max = max;
        this.level = initial;
        trackPaint.setColor(Color.rgb(224, 224, 224));
        fillPaint.setColor(Color.rgb(33, 150, 243));
        thumbPaint.setColor(Color.rgb(13, 71, 161));
    }

    public void setOnLevelChangeListener(OnLevelChangeListener l) { listener = l; }

    private float thumbH() { return getWidth() * 0.35f; }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), r = w / 2f;
        float th = thumbH();
        float usable = h - th;
        float thumbTop = usable * (1f - (float) level / max);

        rect.set(0, 0, w, h);
        c.drawRoundRect(rect, r, r, trackPaint);

        rect.set(0, thumbTop + th / 2f, w, h);
        c.drawRoundRect(rect, r, r, fillPaint);

        rect.set(0, thumbTop, w, thumbTop + th);
        c.drawRoundRect(rect, th / 2f, th / 2f, thumbPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                // fall through
            case MotionEvent.ACTION_MOVE:
                float th = thumbH();
                float usable = getHeight() - th;
                float frac = 1f - (e.getY() - th / 2f) / usable;
                frac = Math.max(0f, Math.min(1f, frac));
                int newLevel = Math.round(frac * max);
                if (newLevel != level) {
                    level = newLevel;
                    invalidate();
                    if (listener != null) listener.onLevelChanged(level);
                }
                return true;
        }
        return super.onTouchEvent(e);
    }
}
