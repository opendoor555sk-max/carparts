package com.nirmaan.calc

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.TypedValue
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Draws a [PlanDrawing] (top view or unfolded side view of a stair type), scaled to fit. */
class PlanView(ctx: Context, private val d: PlanDrawing) : View(ctx) {

    private fun dp(x: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, x, resources.displayMetrics)
    private fun sp(x: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, x, resources.displayMetrics)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = sp(14f); isFakeBoldText = true }
    private val info = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x22, 0x22, 0x22); textSize = sp(12f) }
    private val arrowP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xC6, 0x28, 0x28); style = Paint.Style.STROKE; strokeWidth = dp(1.8f) }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xC6, 0x28, 0x28); style = Paint.Style.FILL }

    private var x0 = 0.0
    private var x1 = 1.0
    private var y0 = 0.0
    private var y1 = 1.0

    init {
        val all = d.shapes.flatMap { it.pts } + d.arrows.flatten() + d.labels.map { it.p }
        if (all.isNotEmpty()) {
            x0 = all.minOf { it.x }; x1 = all.maxOf { it.x }
            y0 = all.minOf { it.y }; y1 = all.maxOf { it.y }
        }
        if (!(x1 - x0 > 1e-9)) { x0 -= 0.5; x1 += 0.5 }
        if (!(y1 - y0 > 1e-9)) { y0 -= 0.5; y1 += 0.5 }
    }

    private fun headH() = dp(26f)
    private fun footH() = d.info.size * dp(17f) + dp(8f)
    private fun padX() = dp(34f)
    private fun padY() = dp(18f)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).toFloat()
        val aw = w - 2 * padX()
        val ah = (aw * ((y1 - y0) / (x1 - x0))).toFloat().coerceIn(w * 0.25f, w * 1.3f)
        setMeasuredDimension(w.toInt(), (headH() + ah + 2 * padY() + footH()).toInt())
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        c.drawColor(Color.WHITE)
        val W = width.toFloat()
        c.drawText(d.title, dp(10f), dp(18f), title)
        val top = headH() + padY()
        val bottom = height - footH() - padY()
        val aw = W - 2 * padX()
        val ah = bottom - top
        val s = min(aw / (x1 - x0), ah / (y1 - y0)).toFloat()
        val ox = padX() + (aw - (x1 - x0).toFloat() * s) / 2
        val oy = top + (ah - (y1 - y0).toFloat() * s) / 2
        fun X(x: Double) = ox + ((x - x0) * s).toFloat()
        fun Y(y: Double) = oy + ((y1 - y) * s).toFloat()

        for (sh in d.shapes) {
            if (sh.pts.size < 2) continue
            val path = Path()
            path.moveTo(X(sh.pts[0].x), Y(sh.pts[0].y))
            for (i in 1 until sh.pts.size) path.lineTo(X(sh.pts[i].x), Y(sh.pts[i].y))
            if (sh.closed) path.close()
            if (sh.closed && (sh.fill ushr 24) != 0) { fill.color = sh.fill; c.drawPath(path, fill) }
            if ((sh.stroke ushr 24) != 0) {
                stroke.color = sh.stroke
                stroke.strokeWidth = dp(sh.width * 0.8f)
                stroke.pathEffect = if (sh.dashed) DashPathEffect(floatArrayOf(dp(6f), dp(4f)), 0f) else null
                c.drawPath(path, stroke)
            }
        }
        for (a in d.arrows) {
            if (a.size < 2) continue
            val path = Path()
            path.moveTo(X(a[0].x), Y(a[0].y))
            for (i in 1 until a.size) path.lineTo(X(a[i].x), Y(a[i].y))
            c.drawPath(path, arrowP)
            c.drawCircle(X(a[0].x), Y(a[0].y), dp(3.5f), dot)
            val p1 = a[a.size - 2]
            val p2 = a[a.size - 1]
            val ang = atan2((Y(p2.y) - Y(p1.y)).toDouble(), (X(p2.x) - X(p1.x)).toDouble())
            val hl = dp(9f).toDouble()
            val hx = X(p2.x); val hy = Y(p2.y)
            val head = Path()
            head.moveTo(hx, hy)
            head.lineTo((hx - hl * cos(ang - 0.45)).toFloat(), (hy - hl * sin(ang - 0.45)).toFloat())
            head.moveTo(hx, hy)
            head.lineTo((hx - hl * cos(ang + 0.45)).toFloat(), (hy - hl * sin(ang + 0.45)).toFloat())
            c.drawPath(head, arrowP)
        }
        for (l in d.labels) {
            txt.color = l.color
            txt.textSize = sp(l.size)
            txt.isFakeBoldText = l.bold
            txt.textAlign = when (l.align) { 0 -> Paint.Align.LEFT; 2 -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
            val px = X(l.p.x)
            val py = Y(l.p.y) + txt.textSize * 0.35f
            if (l.rot != 0f) {
                c.save(); c.rotate(l.rot, X(l.p.x), Y(l.p.y)); c.drawText(l.text, px, py, txt); c.restore()
            } else {
                // keep text inside the view
                val tw = txt.measureText(l.text)
                val left = when (l.align) { 0 -> px; 2 -> px - tw; else -> px - tw / 2 }
                val shift = when {
                    left < dp(2f) -> dp(2f) - left
                    left + tw > W - dp(2f) -> (W - dp(2f)) - (left + tw)
                    else -> 0f
                }
                c.drawText(l.text, px + shift, py, txt)
            }
        }
        var y = height - footH() + dp(14f)
        for (t in d.info) { c.drawText(t, dp(10f), y, info); y += dp(17f) }
        stroke.pathEffect = null
        stroke.color = Color.rgb(0xDD, 0xDD, 0xDD); stroke.strokeWidth = dp(1f)
        c.drawLine(0f, height - 1f, W, height - 1f, stroke)
    }
}
