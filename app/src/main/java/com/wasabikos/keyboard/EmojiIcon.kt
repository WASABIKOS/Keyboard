package com.wasabikos.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/** Iconos de línea monocromos para la barra de emojis (estilo de la referencia). */
class EmojiIcon(context: Context, val kind: Kind) : View(context) {

    enum class Kind { CLOSE, RECENT, SMILEY, ANIMAL, FOOD, ACTIVITY, TRAVEL, OBJECT, SYMBOL, FLAG, DELETE }

    var color = 0xFF9E9E9E.toInt()
        set(value) { field = value; invalidate() }

    private val dp = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.8f * dp
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val f = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        p.color = color; f.color = color
        val cx = width / 2f; val cy = height / 2f; val s = 10 * dp
        when (kind) {
            Kind.CLOSE -> {
                c.drawLine(cx - s * 0.6f, cy - s * 0.3f, cx, cy + s * 0.3f, p)
                c.drawLine(cx, cy + s * 0.3f, cx + s * 0.6f, cy - s * 0.3f, p)
            }
            Kind.RECENT -> {
                c.drawCircle(cx, cy, s, p)
                c.drawLine(cx, cy, cx, cy - s * 0.6f, p); c.drawLine(cx, cy, cx + s * 0.45f, cy + s * 0.3f, p)
            }
            Kind.SMILEY -> {
                c.drawCircle(cx, cy, s, p)
                c.drawCircle(cx - s * 0.35f, cy - s * 0.25f, 1.3f * dp, f)
                c.drawCircle(cx + s * 0.35f, cy - s * 0.25f, 1.3f * dp, f)
                c.drawArc(RectF(cx - s * 0.5f, cy - s * 0.2f, cx + s * 0.5f, cy + s * 0.55f), 20f, 140f, false, p)
            }
            Kind.ANIMAL -> {
                c.drawCircle(cx - s * 0.6f, cy - s * 0.6f, s * 0.3f, p)
                c.drawCircle(cx + s * 0.6f, cy - s * 0.6f, s * 0.3f, p)
                c.drawOval(RectF(cx - s * 0.85f, cy - s * 0.6f, cx + s * 0.85f, cy + s * 0.9f), p)
                c.drawCircle(cx, cy + s * 0.35f, 1.3f * dp, f)
            }
            Kind.FOOD -> {
                c.drawRoundRect(RectF(cx - s * 0.8f, cy - s * 0.6f, cx + s * 0.5f, cy + s * 0.8f), 3 * dp, 3 * dp, p)
                c.drawArc(RectF(cx + s * 0.2f, cy - s * 0.35f, cx + s, cy + s * 0.35f), -90f, 180f, false, p)
            }
            Kind.ACTIVITY -> {
                c.drawCircle(cx, cy, s, p)
                val pent = Path()
                for (i in 0..4) {
                    val a = Math.toRadians((-90 + i * 72).toDouble())
                    val x = cx + (s * 0.38f * Math.cos(a)).toFloat(); val y = cy + (s * 0.38f * Math.sin(a)).toFloat()
                    if (i == 0) pent.moveTo(x, y) else pent.lineTo(x, y)
                }
                pent.close(); c.drawPath(pent, f)
            }
            Kind.TRAVEL -> {
                c.drawRoundRect(RectF(cx - s, cy - s * 0.1f, cx + s, cy + s * 0.6f), 3 * dp, 3 * dp, p)
                c.drawLine(cx - s * 0.7f, cy - s * 0.1f, cx - s * 0.45f, cy - s * 0.7f, p)
                c.drawLine(cx - s * 0.45f, cy - s * 0.7f, cx + s * 0.45f, cy - s * 0.7f, p)
                c.drawLine(cx + s * 0.45f, cy - s * 0.7f, cx + s * 0.7f, cy - s * 0.1f, p)
                c.drawLine(cx - s * 0.7f, cy + s * 0.6f, cx - s * 0.7f, cy + s * 0.9f, p)
                c.drawLine(cx + s * 0.7f, cy + s * 0.6f, cx + s * 0.7f, cy + s * 0.9f, p)
            }
            Kind.OBJECT -> {
                c.drawArc(RectF(cx - s * 0.7f, cy - s, cx + s * 0.7f, cy + s * 0.4f), 140f, 260f, false, p)
                c.drawLine(cx - s * 0.35f, cy + s * 0.45f, cx + s * 0.35f, cy + s * 0.45f, p)
                c.drawLine(cx - s * 0.25f, cy + s * 0.8f, cx + s * 0.25f, cy + s * 0.8f, p)
            }
            Kind.SYMBOL -> {
                c.drawRoundRect(RectF(cx - s * 1.1f, cy - s * 0.65f, cx + s * 1.1f, cy + s * 0.65f), 3 * dp, 3 * dp, f)
                f.color = 0xFF000000.toInt(); f.textAlign = Paint.Align.CENTER
                f.textSize = 9 * dp; f.typeface = Typeface.DEFAULT_BOLD
                c.drawText("!?#", cx, cy + 3.2f * dp, f)
            }
            Kind.FLAG -> {
                c.drawLine(cx - s * 0.7f, cy - s, cx - s * 0.7f, cy + s, p)
                val fl = Path().apply {
                    moveTo(cx - s * 0.7f, cy - s); quadTo(cx, cy - s * 1.2f, cx + s * 0.8f, cy - s * 0.9f)
                    lineTo(cx + s * 0.8f, cy + s * 0.1f); quadTo(cx, cy - s * 0.2f, cx - s * 0.7f, cy)
                }
                c.drawPath(fl, p)
            }
            Kind.DELETE -> {
                val d = Path().apply {
                    moveTo(cx - s * 1.1f, cy); lineTo(cx - s * 0.5f, cy - s * 0.7f); lineTo(cx + s, cy - s * 0.7f)
                    lineTo(cx + s, cy + s * 0.7f); lineTo(cx - s * 0.5f, cy + s * 0.7f); close()
                }
                c.drawPath(d, f)
                p.color = 0xFF000000.toInt()
                c.drawLine(cx - s * 0.05f, cy - s * 0.3f, cx + s * 0.55f, cy + s * 0.3f, p)
                c.drawLine(cx + s * 0.55f, cy - s * 0.3f, cx - s * 0.05f, cy + s * 0.3f, p)
            }
        }
    }
}
