package com.nirmaan.calc

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.TypedValue
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Side view of the stair stringer: steps, board, floor, top landing and cumulative marks. */
class StairView(ctx: Context, private val p: StairPlan) : View(ctx) {

    private fun dp(x: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, x, resources.displayMetrics)
    private fun sp(x: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, x, resources.displayMetrics)

    private val wood = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xE8, 0xC9, 0x9B); style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x33, 0x2A, 0x1E); style = Paint.Style.STROKE; strokeWidth = dp(1.6f) }
    private val floor = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x90, 0x90, 0x90); style = Paint.Style.FILL }
    private val dim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x15, 0x65, 0xC0); style = Paint.Style.STROKE; strokeWidth = dp(1f) }
    private val dimTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x15, 0x65, 0xC0); textSize = sp(9.5f); textAlign = Paint.Align.RIGHT }
    private val info = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x22, 0x22, 0x22); textSize = sp(12.5f) }
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = sp(15f); isFakeBoldText = true }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (w * 0.95f).toInt())
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        c.drawColor(Color.WHITE)
        val n = p.n
        val ur = p.ur
        val ut = p.ut
        val bw = p.bw
        if (n < 1 || ur <= 0 || ut <= 0) return
        if (p.install) return drawInstall(c)
        if (p.finished) return drawFinished(c)
        val th = atan2(ur, ut)
        val m = ur / ut
        val xEnd = (n - 1) * ut
        val top = n * ur
        val drop = bw / cos(th)                 // vertical distance between board edges
        val xb = (drop - ur) / m                 // bottom edge meets the floor here
        val ybEnd = m * xEnd + ur - drop         // bottom edge under the top landing

        val xmin = min(-ut, xb) - 0.2 * ut
        val xmax = xEnd + 1.6 * ut
        val ymin = -0.12 * top
        val ymax = top * 1.04
        val pad = dp(10f)
        val W = width.toFloat()
        val H = height.toFloat()
        val s = min((W - 2 * pad) / (xmax - xmin), (H - 2 * pad) / (ymax - ymin)).toFloat()
        val ox = pad + ((W - 2 * pad) - (xmax - xmin).toFloat() * s) / 2
        fun X(x: Double) = ox + ((x - xmin) * s).toFloat()
        fun Y(y: Double) = H - pad - ((y - ymin) * s).toFloat()

        // floor + top landing
        c.drawRect(X(xmin), Y(0.0), X(xmax), Y(ymin), floor)
        c.drawRect(X(xEnd), Y(top), X(xmax), Y(top - max(0.6 * ur, bw * 0.8)), floor)

        // stringer board with the step profile cut out
        val path = Path()
        path.moveTo(X(xb), Y(0.0))
        path.lineTo(X(0.0), Y(0.0))
        for (k in 1..n) {
            path.lineTo(X((k - 1) * ut), Y(k * ur))
            if (k < n) path.lineTo(X(k * ut), Y(k * ur))
        }
        path.lineTo(X(xEnd), Y(ybEnd))
        path.close()
        c.drawPath(path, wood)
        c.drawPath(path, line)

        // dimension line along the top edge with a tick + running length at every nosing
        val g = dp(16f)
        val nx = (-sin(th)).toFloat()           // screen normal pointing up-left
        val ny = (-cos(th)).toFloat()
        val sx = X(-ut) + nx * g
        val sy = Y(0.0) + ny * g
        val ex = X((n - 1) * ut) + nx * g
        val ey = Y(n * ur) + ny * g
        c.drawLine(sx, sy, ex, ey, dim)
        c.drawLine(X(-ut), Y(0.0), sx + nx * dp(4f), sy + ny * dp(4f), dim)
        val ang = Math.toDegrees(atan2(-ny.toDouble(), -nx.toDouble())).toFloat()
        val maxLabels = max(1, (hypot((ex - sx).toDouble(), (ey - sy).toDouble()) / (dimTxt.textSize * 1.25)).toInt())
        val step = max(1, (n + maxLabels - 1) / maxLabels)
        for (k in 1..n) {
            val px = X((k - 1) * ut)
            val py = Y(k * ur)
            val tx = px + nx * g
            val ty = py + ny * g
            c.drawLine(px + nx * dp(3f), py + ny * dp(3f), tx + nx * dp(4f), ty + ny * dp(4f), dim)
            if (k % step == 0 || k == n) {
                val ax = tx + nx * dp(6f)
                val ay = ty + ny * dp(6f)
                c.save()
                c.rotate(ang, ax, ay)
                c.drawText("$k: " + p.marks.getOrElse(k - 1) { "" }, ax, ay + dimTxt.textSize * 0.35f, dimTxt)
                c.restore()
            }
        }

        // info block in the empty space under the stringer (bottom-right)
        info.textAlign = Paint.Align.RIGHT
        title.textAlign = Paint.Align.RIGHT
        var y = Y(0.0) - dp(6f)
        for (t in p.info.reversed()) {
            c.drawText(t, W - pad, y, info)
            y -= info.textSize * 1.3f
        }
        c.drawText("Stringer Layout", W - pad, y - sp(2f), title)
    }

    /** Finished staircase: floor, landing, stringer (dropped by tread thickness), tread + riser boards. */
    private fun drawFinished(c: Canvas) {
        val n = p.n
        val ur = p.ur
        val ut = p.ut
        val tt = p.tt
        val nose = p.nose
        val rt = p.rt
        val bw = p.bw
        val th = atan2(ur, ut)
        val m = ur / ut
        val xEnd = (n - 1) * ut
        val top = n * ur
        val drop = bw / cos(th)
        val xb = (drop - ur) / m
        val xmin = min(-ut, xb) - 0.3 * ut
        val xmax = xEnd + 1.6 * ut
        val ymin = -0.12 * top
        val ymax = top * 1.06
        val pad = dp(10f)
        val W = width.toFloat()
        val H = height.toFloat()
        val s = min((W - 2 * pad) / (xmax - xmin), (H - 2 * pad) / (ymax - ymin)).toFloat()
        val ox = pad + ((W - 2 * pad) - (xmax - xmin).toFloat() * s) / 2
        fun X(x: Double) = ox + ((x - xmin) * s).toFloat()
        fun Y(y: Double) = H - pad - ((y - ymin) * s).toFloat()
        val board = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xB9, 0x7A, 0x3C); style = Paint.Style.FILL }
        val riser = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xF3, 0xE2, 0xC3); style = Paint.Style.FILL }
        val pale = Paint(wood).apply { alpha = 150 }

        c.drawRect(X(xmin), Y(0.0), X(xmax), Y(ymin), floor)
        c.drawRect(X(xEnd), Y(top), X(xmax), Y(top - max(0.6 * ur, bw * 0.8)), floor)

        // stringer, lowered by the tread thickness so finished treads land on the step heights
        val path = Path()
        path.moveTo(X(xb), Y(0.0))
        path.lineTo(X(0.0), Y(0.0))
        for (k in 1..n) {
            path.lineTo(X((k - 1) * ut), Y(k * ur - tt))
            if (k < n) path.lineTo(X(k * ut), Y(k * ur - tt))
        }
        path.lineTo(X(xEnd), Y(m * xEnd + ur - drop - tt))
        path.close()
        c.drawPath(path, pale)
        c.drawPath(path, line)

        for (k in 1..n) {
            // riser board in front of each step face
            val rx = (k - 1) * ut
            c.drawRect(X(rx - rt), Y(k * ur - tt), X(rx), Y((k - 1) * ur), riser)
            c.drawRect(X(rx - rt), Y(k * ur - tt), X(rx), Y((k - 1) * ur), line)
            if (k < n) {
                // tread board with nosing over the riser
                c.drawRect(X(rx - rt - nose), Y(k * ur), X(k * ut), Y(k * ur - tt), board)
                c.drawRect(X(rx - rt - nose), Y(k * ur), X(k * ut), Y(k * ur - tt), line)
            }
        }
        // step numbers
        val num = Paint(dimTxt).apply { textAlign = Paint.Align.CENTER }
        for (k in 1 until n) c.drawText(k.toString(), X((k - 0.5) * ut), Y(k * ur) - dp(3f), num)

        info.textAlign = Paint.Align.RIGHT
        title.textAlign = Paint.Align.RIGHT
        var y = Y(0.0) - dp(6f)
        for (t in p.info.reversed()) {
            c.drawText(t, W - pad, y, info)
            y -= info.textSize * 1.3f
        }
        c.drawText("Finished Layout", W - pad, y - sp(2f), title)
    }

    /** How the stringer sits: on the lower floor (cut by tread thickness) and against the header of the upper floor. */
    private fun drawInstall(c: Canvas) {
        val n = p.n
        val ur = p.ur
        val ut = p.ut
        val tt = p.tt
        val bw = p.bw
        val th = atan2(ur, ut)
        val m = ur / ut
        val xEnd = (n - 1) * ut
        val top = n * ur
        val drop = bw / cos(th)
        val xb = (drop - ur) / m
        val fth = if (p.floorTh > 0) p.floorTh else 0.6 * ur
        val hasMap = p.riseMarks.isNotEmpty()
        // extra room on the left (height marks) and under the floor (run marks)
        val xmin = min(-ut, xb) - (if (hasMap) 3.2 else 0.3) * ut
        val xmax = xEnd + 2.2 * ut
        val ymin = -(if (hasMap) 0.30 else 0.12) * top
        val ymax = top * 1.06
        val pad = dp(10f)
        val W = width.toFloat()
        val H = height.toFloat()
        val s = min((W - 2 * pad) / (xmax - xmin), (H - 2 * pad) / (ymax - ymin)).toFloat()
        val ox = pad + ((W - 2 * pad) - (xmax - xmin).toFloat() * s) / 2
        fun X(x: Double) = ox + ((x - xmin) * s).toFloat()
        fun Y(y: Double) = H - pad - ((y - ymin) * s).toFloat()
        val header = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x8D, 0x6E, 0x63); style = Paint.Style.FILL }
        val red = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xC6, 0x28, 0x28); style = Paint.Style.STROKE; strokeWidth = dp(2f) }

        c.drawRect(X(xmin), Y(0.0), X(xmax), Y(ymin), floor)
        // upper floor with its header (rim) at the stair opening
        c.drawRect(X(xEnd), Y(top), X(xmax), Y(top - fth), floor)
        c.drawRect(X(xEnd), Y(top), X(xEnd + 0.25 * ut), Y(top - fth), header)

        val yb = m * xEnd + ur - drop - tt
        val path = Path()
        path.moveTo(X(xb), Y(0.0))
        path.lineTo(X(0.0), Y(0.0))
        for (k in 1..n) {
            path.lineTo(X((k - 1) * ut), Y(k * ur - tt))
            if (k < n) path.lineTo(X(k * ut), Y(k * ur - tt))
        }
        path.lineTo(X(xEnd), Y(yb))
        path.close()
        c.drawPath(path, wood)
        c.drawPath(path, line)
        // attachment points
        c.drawLine(X(xEnd) + dp(2f), Y(top - tt - ur), X(xEnd) + dp(2f), Y(yb), red)
        c.drawLine(X(xb), Y(0.0) + dp(2f), X(0.0), Y(0.0) + dp(2f), red)

        val lbl = Paint(dimTxt).apply { textAlign = Paint.Align.LEFT; textSize = sp(11f) }
        if (hasMap) drawRiseRunMap(c, ::X, ::Y, xmin, s)
        c.drawText("Header", X(xEnd + 0.3 * ut), Y(top - fth / 2) + lbl.textSize / 2, lbl)
        c.drawText("Upar ka floor", X(xEnd + 0.3 * ut), Y(top) - dp(4f), lbl)
        c.drawText("Neeche ka floor", X(xmin) + dp(4f), Y(0.0) + lbl.textSize + dp(3f), lbl)

        info.textAlign = Paint.Align.RIGHT
        title.textAlign = Paint.Align.RIGHT
        var y = Y(0.0) - dp(6f)
        for (t in p.info.reversed()) {
            c.drawText(t, W - pad, y, info)
            y -= info.textSize * 1.3f
        }
        c.drawText("Installation", W - pad, y - sp(2f), title)
    }

    /** Rise / run map: height of every step on a vertical scale (left), run of every tread on the floor (bottom). */
    private fun drawRiseRunMap(c: Canvas, X: (Double) -> Float, Y: (Double) -> Float, xmin: Double, s: Float) {
        val n = p.n
        val ur = p.ur
        val ut = p.ut
        val green = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x2E, 0x7D, 0x32); style = Paint.Style.STROKE; strokeWidth = dp(1f) }
        val guide = Paint(green).apply { alpha = 90; pathEffect = android.graphics.DashPathEffect(floatArrayOf(dp(3f), dp(3f)), 0f) }
        val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x1B, 0x5E, 0x20); textSize = sp(9.5f); textAlign = Paint.Align.LEFT }

        // vertical scale: floor to each step top
        val ax = X(xmin) + dp(6f)
        c.drawLine(ax, Y(0.0), ax, Y(n * ur), green)
        val every = max(1, kotlin.math.ceil(txt.textSize * 1.15 / (ur * s)).toInt())
        for (k in 1..n) {
            val y = Y(k * ur)
            c.drawLine(ax - dp(3f), y, ax + dp(3f), y, green)
            c.drawLine(ax, y, X((k - 1) * ut), y, guide)
            if (k % every == 0 || k == n) c.drawText("$k: " + p.riseMarks.getOrElse(k - 1) { "" }, ax + dp(5f), y - dp(2f), txt)
        }
        c.drawText("Oonchai (floor se)", ax, Y(n * ur) - txt.textSize * 1.4f, txt)

        // horizontal scale under the floor: first riser to each tread edge
        val fy = Y(0.0) + dp(12f)
        val tr = p.runMarks.size
        c.drawLine(X(0.0), fy, X(tr * ut), fy, green)
        c.drawLine(X(0.0), fy - dp(3f), X(0.0), fy + dp(3f), green)
        val rot = Paint(txt).apply { textAlign = Paint.Align.RIGHT }
        val everyX = max(1, kotlin.math.ceil(txt.textSize * 1.15 / (ut * s)).toInt())
        for (k in 1..tr) {
            val x = X(k * ut)
            c.drawLine(x, fy - dp(3f), x, fy + dp(3f), green)
            if (k % everyX == 0 || k == tr) {
                c.save()
                c.rotate(-90f, x + txt.textSize * 0.35f, fy + dp(5f))
                c.drawText("$k: " + p.runMarks[k - 1], x + txt.textSize * 0.35f, fy + dp(5f), rot)
                c.restore()
            }
        }
        c.drawText("Aage (pehle riser se)", X(0.0), fy - dp(5f), txt)
    }
}
