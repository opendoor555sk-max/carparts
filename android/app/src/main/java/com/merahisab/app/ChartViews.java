package com.merahisab.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** Two small hand-drawn charts (no libraries): a line chart with soft fill, and a donut. */
final class ChartViews {
    private ChartViews() {}

    static String shortMoney(double v) {
        double a = Math.abs(v);
        String s;
        if (a >= 10000000) s = trim(a / 10000000) + "Cr";
        else if (a >= 100000) s = trim(a / 100000) + "L";
        else if (a >= 1000) s = trim(a / 1000) + "K";
        else s = String.valueOf((long) a);
        return (v < 0 ? "-" : "") + "₹" + s;
    }

    private static String trim(double d) {
        String x = String.format(java.util.Locale.US, "%.1f", d);
        return x.endsWith(".0") ? x.substring(0, x.length() - 2) : x;
    }

    static final class Line extends View {
        private final double[] v;
        private final String[] lab;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Line(Context c, double[] values, String[] labels) {
            super(c);
            v = values; lab = labels;
        }

        @Override protected void onDraw(Canvas cv) {
            int w = getWidth(), h = getHeight();
            if (w == 0 || h == 0 || v.length == 0) return;
            float padL = Ui.dp(34), padR = Ui.dp(6), padT = Ui.dp(6), padB = Ui.dp(18);
            double mx = 0, mn = 0;
            for (double d : v) { if (d > mx) mx = d; if (d < mn) mn = d; }
            if (mx <= 0 && mn >= 0) mx = 1000;
            double step = niceStep((mx - mn) / 4.0);
            double top = Math.ceil(mx / step) * step, bot = Math.floor(mn / step) * step;
            if (top == bot) top = bot + step;
            float cw = w - padL - padR, ch = h - padT - padB;
            p.setTextSize(Ui.dp(8.5f));
            for (double t = bot; t <= top + step * 0.01; t += step) {
                float y = padT + (float) ((top - t) / (top - bot)) * ch;
                p.setColor(Ui.LINE); p.setStrokeWidth(1f); p.setStyle(Paint.Style.STROKE);
                cv.drawLine(padL, y, w - padR, y, p);
                p.setStyle(Paint.Style.FILL); p.setColor(Ui.MUTED); p.setTextAlign(Paint.Align.RIGHT);
                cv.drawText(shortMoney(t), padL - Ui.dp(4), y + Ui.dp(3), p);
            }
            int n = v.length;
            float[] xs = new float[n], ys = new float[n];
            for (int i = 0; i < n; i++) {
                xs[i] = padL + (n == 1 ? cw / 2 : cw * i / (n - 1));
                ys[i] = padT + (float) ((top - v[i]) / (top - bot)) * ch;
            }
            float zero = padT + (float) ((top - 0) / (top - bot)) * ch;
            Path line = new Path(), fill = new Path();
            for (int i = 0; i < n; i++) {
                if (i == 0) { line.moveTo(xs[i], ys[i]); fill.moveTo(xs[i], zero); fill.lineTo(xs[i], ys[i]); }
                else { line.lineTo(xs[i], ys[i]); fill.lineTo(xs[i], ys[i]); }
            }
            fill.lineTo(xs[n - 1], zero); fill.close();
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, padT, 0, zero, Color.argb(90, 20, 184, 166), Color.argb(5, 20, 184, 166), Shader.TileMode.CLAMP));
            cv.drawPath(fill, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Ui.dp(2f)); p.setColor(Ui.T1); p.setStrokeJoin(Paint.Join.ROUND);
            cv.drawPath(line, p);
            for (int i = 0; i < n; i++) {
                p.setStyle(Paint.Style.FILL); p.setColor(Ui.SURFACE);
                cv.drawCircle(xs[i], ys[i], Ui.dp(3.5f), p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Ui.dp(1.8f)); p.setColor(Ui.T1);
                cv.drawCircle(xs[i], ys[i], Ui.dp(3.5f), p);
            }
            p.setStyle(Paint.Style.FILL); p.setColor(Ui.MUTED); p.setTextAlign(Paint.Align.CENTER);
            for (int i = 0; i < n; i++) cv.drawText(lab[i], xs[i], h - Ui.dp(4), p);
        }

        private static double niceStep(double raw) {
            if (raw <= 0) return 1000;
            double e = Math.pow(10, Math.floor(Math.log10(raw)));
            double f = raw / e;
            double n = f <= 1 ? 1 : f <= 2 ? 2 : f <= 2.5 ? 2.5 : f <= 5 ? 5 : 10;
            return n * e;
        }
    }

    static final class Donut extends View {
        private final double[] v;
        private final int[] col;
        private final String center, caption;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Donut(Context c, double[] values, int[] colors, String centerText, String captionText) {
            super(c);
            v = values; col = colors; center = centerText; caption = captionText;
        }

        @Override protected void onDraw(Canvas cv) {
            int w = getWidth(), h = getHeight();
            float size = Math.min(w, h), sw = Ui.dp(13);
            float l = (w - size) / 2f + sw / 2f, t = (h - size) / 2f + sw / 2f;
            RectF r = new RectF(l, t, l + size - sw, t + size - sw);
            double tot = 0;
            for (double d : v) tot += d;
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(sw);
            if (tot <= 0) {
                p.setColor(Ui.LINE);
                cv.drawArc(r, 0, 360, false, p);
            } else {
                float a = -90;
                for (int i = 0; i < v.length; i++) {
                    float sweep = (float) (v[i] / tot * 360.0);
                    p.setColor(col[i]);
                    cv.drawArc(r, a, Math.max(0, sweep - (v.length > 1 ? 2f : 0f)), false, p);
                    a += sweep;
                }
            }
            p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.CENTER);
            p.setColor(Ui.TEXT); p.setFakeBoldText(true); p.setTextSize(Ui.dp(11));
            cv.drawText(center, w / 2f, h / 2f + Ui.dp(1), p);
            p.setFakeBoldText(false); p.setColor(Ui.MUTED); p.setTextSize(Ui.dp(8));
            cv.drawText(caption, w / 2f, h / 2f + Ui.dp(11), p);
        }
    }
}
