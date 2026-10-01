package com.nirmaan.calc;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Small drawn icons, so they look the same on every phone (no emoji). */
public class IconView extends View {
    public static final int GEAR = 0, HIST = 1, KEYBOARD = 2, LEFT = 3, RIGHT = 4, UNDO = 5, REDO = 6, BACKSPACE = 7;

    private final int type;
    private int color;
    private int hole;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;

    public IconView(Context c, int type, int color, int hole) {
        super(c);
        this.type = type;
        this.color = color;
        this.hole = hole;
        d = c.getResources().getDisplayMetrics().density;
    }

    public void setColor(int c) { color = c; invalidate(); }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2, cy = h / 2;
        float s = Math.min(w, h) * 0.62f;
        p.setColor(color);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        switch (type) {
            case GEAR: {
                p.setStyle(Paint.Style.FILL);
                float r = s * 0.30f;
                for (int i = 0; i < 8; i++) {
                    cv.save();
                    cv.rotate(i * 45f, cx, cy);
                    cv.drawRoundRect(new RectF(cx - s * 0.085f, cy - s * 0.47f, cx + s * 0.085f, cy - r + 2), 3 * d, 3 * d, p);
                    cv.restore();
                }
                cv.drawCircle(cx, cy, r + s * 0.04f, p);
                p.setColor(hole);
                cv.drawCircle(cx, cy, s * 0.13f, p);
                break;
            }
            case HIST: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2.6f * d);
                float r = s * 0.40f;
                RectF o = new RectF(cx - r, cy - r, cx + r, cy + r);
                cv.drawArc(o, 200f, 300f, false, p);
                // arrow head at the start of the arc (left side)
                double a = Math.toRadians(200);
                float ax = cx + (float) (r * Math.cos(a)), ay = cy + (float) (r * Math.sin(a));
                Path ar = new Path();
                ar.moveTo(ax - s * 0.16f, ay - s * 0.02f);
                ar.lineTo(ax, ay);
                ar.lineTo(ax + s * 0.06f, ay - s * 0.16f);
                cv.drawPath(ar, p);
                // hands
                cv.drawLine(cx, cy, cx, cy - r * 0.55f, p);
                cv.drawLine(cx, cy, cx + r * 0.45f, cy + r * 0.3f, p);
                break;
            }
            case KEYBOARD: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2f * d);
                float kw = s * 0.95f, kh = s * 0.6f;
                cv.drawRoundRect(new RectF(cx - kw / 2, cy - kh / 2, cx + kw / 2, cy + kh / 2), 3 * d, 3 * d, p);
                p.setStyle(Paint.Style.FILL);
                float k = kw / 7.2f;
                for (int row = 0; row < 2; row++)
                    for (int i = 0; i < 6; i++) {
                        float x = cx - kw / 2 + k * 0.7f + i * k * 1.03f;
                        float y = cy - kh / 2 + kh * (0.22f + row * 0.26f);
                        cv.drawRect(x, y, x + k * 0.62f, y + k * 0.5f, p);
                    }
                cv.drawRect(cx - kw * 0.28f, cy + kh * 0.2f, cx + kw * 0.28f, cy + kh * 0.2f + k * 0.5f, p);
                break;
            }
            case LEFT:
            case RIGHT: {
                p.setStyle(Paint.Style.FILL);
                float t = s * 0.36f;
                Path tr = new Path();
                if (type == LEFT) { tr.moveTo(cx - t * 0.6f, cy); tr.lineTo(cx + t * 0.5f, cy - t); tr.lineTo(cx + t * 0.5f, cy + t); }
                else { tr.moveTo(cx + t * 0.6f, cy); tr.lineTo(cx - t * 0.5f, cy - t); tr.lineTo(cx - t * 0.5f, cy + t); }
                tr.close();
                cv.drawPath(tr, p);
                break;
            }
            case UNDO:
            case REDO: {
                cv.save();
                if (type == REDO) cv.scale(-1f, 1f, cx, cy);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2.6f * d);
                Path pa = new Path();
                pa.moveTo(cx + s * 0.38f, cy + s * 0.30f);
                pa.lineTo(cx + s * 0.38f, cy + s * 0.02f);
                pa.quadTo(cx + s * 0.38f, cy - s * 0.18f, cx + s * 0.16f, cy - s * 0.18f);
                pa.lineTo(cx - s * 0.36f, cy - s * 0.18f);
                cv.drawPath(pa, p);
                Path hd = new Path();
                hd.moveTo(cx - s * 0.20f, cy - s * 0.36f);
                hd.lineTo(cx - s * 0.38f, cy - s * 0.18f);
                hd.lineTo(cx - s * 0.20f, cy);
                cv.drawPath(hd, p);
                cv.restore();
                break;
            }
            case BACKSPACE: {
                p.setStyle(Paint.Style.FILL);
                float bw = s * 0.95f, bh = s * 0.6f;
                Path b = new Path();
                b.moveTo(cx - bw / 2, cy);
                b.lineTo(cx - bw / 2 + bh / 2, cy - bh / 2);
                b.lineTo(cx + bw / 2, cy - bh / 2);
                b.lineTo(cx + bw / 2, cy + bh / 2);
                b.lineTo(cx - bw / 2 + bh / 2, cy + bh / 2);
                b.close();
                cv.drawPath(b, p);
                p.setColor(hole);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3f * d);
                float xc = cx + bw * 0.1f, xr = bh * 0.22f;
                cv.drawLine(xc - xr, cy - xr, xc + xr, cy + xr, p);
                cv.drawLine(xc - xr, cy + xr, xc + xr, cy - xr, p);
                break;
            }
        }
    }
}
