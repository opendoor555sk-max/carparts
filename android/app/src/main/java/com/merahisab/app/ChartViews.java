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

        static double niceStep(double raw) {
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
        private boolean pie = false;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Donut(Context c, double[] values, int[] colors, String centerText, String captionText) {
            super(c);
            v = values; col = colors; center = centerText; caption = captionText;
        }

        Donut asPie() { pie = true; return this; }

        @Override protected void onDraw(Canvas cv) {
            int w = getWidth(), h = getHeight();
            float size = Math.min(w, h), sw = pie ? Math.min(w, h) / 2f : Ui.dp(13);
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
            if (pie) return;
            p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.CENTER);
            p.setColor(Ui.TEXT); p.setFakeBoldText(true); p.setTextSize(Ui.dp(11));
            cv.drawText(center, w / 2f, h / 2f + Ui.dp(1), p);
            p.setFakeBoldText(false); p.setColor(Ui.MUTED); p.setTextSize(Ui.dp(8));
            cv.drawText(caption, w / 2f, h / 2f + Ui.dp(11), p);
        }
    }

    /** Vertical bars. One or two series (second is drawn beside the first). */
    static final class Bars extends View {
        private final double[] a, b;
        private final String[] lab;
        private final int c1, c2;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Bars(Context c, double[] first, double[] second, String[] labels, int color1, int color2) {
            super(c);
            a = first; b = second; lab = labels; c1 = color1; c2 = color2;
        }

        @Override protected void onDraw(Canvas cv) {
            int w = getWidth(), h = getHeight();
            int n = a.length;
            if (w == 0 || h == 0 || n == 0) return;
            float padL = Ui.dp(34), padR = Ui.dp(6), padT = Ui.dp(6), padB = Ui.dp(18);
            double mx = 0, mn = 0;
            for (int i = 0; i < n; i++) {
                mx = Math.max(mx, a[i]); mn = Math.min(mn, a[i]);
                if (b != null) { mx = Math.max(mx, b[i]); mn = Math.min(mn, b[i]); }
            }
            if (mx <= 0 && mn >= 0) mx = 1000;
            double step = Line.niceStep((mx - mn) / 4.0);
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
            float zero = padT + (float) ((top - 0) / (top - bot)) * ch;
            float slot = cw / n;
            int k = b == null ? 1 : 2;
            float bw = Math.min(Ui.dp(22), slot * 0.7f / k);
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < n; i++) {
                float cx = padL + slot * (i + 0.5f);
                float x0 = cx - bw * k / 2f;
                for (int j = 0; j < k; j++) {
                    double val = j == 0 ? a[i] : b[i];
                    float y = padT + (float) ((top - val) / (top - bot)) * ch;
                    p.setColor(j == 0 ? c1 : c2);
                    RectF r = new RectF(x0 + bw * j + 1, Math.min(y, zero), x0 + bw * (j + 1) - 1, Math.max(y, zero) + (y == zero ? 1 : 0));
                    cv.drawRoundRect(r, Ui.dp(3), Ui.dp(3), p);
                }
                p.setColor(Ui.MUTED); p.setTextAlign(Paint.Align.CENTER);
                cv.drawText(lab[i], cx, h - Ui.dp(4), p);
            }
        }
    }

    /** Horizontal bars with name, bar and amount - used for category splits. */
    static final class HBars extends View {
        private final String[] names;
        private final double[] v;
        private final int[] col;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        HBars(Context c, String[] n, double[] values, int[] colors) {
            super(c);
            names = n; v = values; col = colors;
        }

        @Override protected void onDraw(Canvas cv) {
            int w = getWidth(), h = getHeight(), n = v.length;
            if (n == 0) return;
            double mx = 1;
            for (double d : v) mx = Math.max(mx, d);
            float row = (float) h / n, lw = w * 0.34f, vw = Ui.dp(46);
            for (int i = 0; i < n; i++) {
                float cy = row * i + row / 2f;
                p.setStyle(Paint.Style.FILL); p.setTextSize(Ui.dp(10));
                p.setColor(Ui.TEXT); p.setTextAlign(Paint.Align.LEFT);
                String nm = names[i];
                while (nm.length() > 2 && p.measureText(nm) > lw - Ui.dp(4)) nm = nm.substring(0, nm.length() - 1);
                cv.drawText(nm, 0, cy + Ui.dp(3), p);
                float bx = lw, bmax = w - lw - vw;
                p.setColor(Ui.LINE);
                cv.drawRoundRect(new RectF(bx, cy - Ui.dp(5), bx + bmax, cy + Ui.dp(5)), Ui.dp(5), Ui.dp(5), p);
                p.setColor(col[i % col.length]);
                cv.drawRoundRect(new RectF(bx, cy - Ui.dp(5), bx + Math.max(Ui.dp(6), (float) (v[i] / mx) * bmax), cy + Ui.dp(5)), Ui.dp(5), Ui.dp(5), p);
                p.setColor(Ui.MUTED); p.setTextAlign(Paint.Align.RIGHT);
                cv.drawText(shortMoney(v[i]), w, cy + Ui.dp(3), p);
            }
        }
    }

    /** One bar split into coloured parts (100% bar). */
    static final class Stack extends View {
        private final double[] v;
        private final int[] col;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Stack(Context c, double[] values, int[] colors) {
            super(c);
            v = values; col = colors;
        }

        @Override protected void onDraw(Canvas cv) {
            int w = getWidth(), h = getHeight();
            double tot = 0;
            for (double d : v) tot += d;
            float top = h / 2f - Ui.dp(14), bot = h / 2f + Ui.dp(14);
            p.setStyle(Paint.Style.FILL);
            if (tot <= 0) {
                p.setColor(Ui.LINE);
                cv.drawRoundRect(new RectF(0, top, w, bot), Ui.dp(10), Ui.dp(10), p);
                return;
            }
            float x = 0;
            for (int i = 0; i < v.length; i++) {
                float seg = (float) (v[i] / tot * w);
                p.setColor(col[i % col.length]);
                cv.drawRect(x, top, x + Math.max(0, seg - 2), bot, p);
                x += seg;
            }
        }
    }
}
