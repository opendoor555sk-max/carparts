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
}
