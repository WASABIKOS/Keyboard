package com.wasabikos.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onText(text: String)
        fun onKey(type: KeyType)
    }

    enum class Shift { OFF, ON, LOCKED }

    var listener: Listener? = null
    var theme: KeyboardTheme = KeyboardTheme.from(context)
        set(value) { field = value; invalidate() }
    var rows: List<List<Key>> = Layouts.letters
        set(value) { field = value; layoutKeys(); invalidate() }
    var shift = Shift.OFF
        set(value) { field = value; invalidate() }
    var listening = false
        set(value) { field = value; invalidate() }
    var spaceLabel = ""
        set(value) { field = value; invalidate() }
    var enterLabel: String? = null
        set(value) { field = value; invalidate() }

    private val dp = resources.displayMetrics.density
    private val rowHeight = 56 * dp
    private val pad = 4 * dp
    private val radius = 8 * dp

    private val bounds = mutableListOf<Pair<Key, RectF>>()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * dp
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private var pressed: Key? = null
    private var pressedRect: RectF? = null
    private var downY = 0f
    private var popupKeys: List<String> = emptyList()
    private var popupRect: RectF? = null
    private var popupIndex = -1

    private val handler = Handler(Looper.getMainLooper())
    private val longPress = Runnable { showPopup() }
    private var consumed = false
    // Mantener pulsada la barra espaciadora inicia el dictado
    private val longPressSpace = Runnable {
        consumed = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        listener?.onKey(KeyType.MIC)
    }
    private val repeatDelete = object : Runnable {
        override fun run() {
            listener?.onKey(KeyType.DELETE)
            handler.postDelayed(this, 50)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (rows.size * rowHeight + 2 * pad).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = layoutKeys()

    private fun layoutKeys() {
        bounds.clear()
        if (width == 0) return
        // Ancho unitario basado en la fila con más peso, así las filas cortas quedan centradas (fila "a…ñ")
        val unit = (width - 2 * pad) / rows.maxOf { r -> r.sumOf { it.weight.toDouble() } }.toFloat()
        rows.forEachIndexed { i, row ->
            val total = row.sumOf { it.weight.toDouble() }.toFloat() * unit
            var x = (width - total) / 2
            val top = pad + i * rowHeight
            row.forEach { key ->
                val w = key.weight * unit
                bounds += key to RectF(x + pad / 2, top + pad / 2, x + w - pad / 2, top + rowHeight - pad / 2)
                x += w
            }
        }
    }

    private fun displayLabel(key: Key): String =
        if (key.type == KeyType.CHAR && shift != Shift.OFF) key.label.uppercase() else key.label

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.background)
        for ((key, r) in bounds) {
            // Estilo AMOLED plano: sin fondo de tecla salvo al pulsar o dictando
            val bg = when {
                key === pressed && key.type != KeyType.CHAR -> theme.keyPressed
                else -> null
            }
            bg?.let { fill.color = it; canvas.drawRoundRect(r, radius, radius, fill) }

            val fg = if (key.type == KeyType.MIC && listening) theme.onAccent else theme.text
            textPaint.color = fg
            stroke.color = fg
            when (key.type) {
                KeyType.SHIFT -> drawShift(canvas, r)
                KeyType.DELETE -> drawDelete(canvas, r)
                KeyType.ENTER -> drawEnter(canvas, r)
                KeyType.GLOBE -> drawGlobe(canvas, r)
                KeyType.MIC -> drawMic(canvas, r)
                KeyType.PANEL -> Unit
                KeyType.SPACE -> {
                    textPaint.color = theme.hint
                    drawText(canvas, spaceLabel, r, 13)
                }
                KeyType.SYMBOLS, KeyType.LETTERS, KeyType.MORE_SYMBOLS -> drawText(canvas, key.label, r, 15)
                KeyType.CHAR -> {
                    drawText(canvas, displayLabel(key), r, if (key.label.length > 1) 16 else 24)
                    key.hint?.let {
                        textPaint.color = theme.hint
                        textPaint.textSize = 11 * dp
                        canvas.drawText(it, r.right - 7 * dp, r.top + 13 * dp, textPaint)
                    }
                }
            }
        }
        drawPopup(canvas)
    }

    private fun drawText(canvas: Canvas, text: String, r: RectF, sp: Int) {
        textPaint.textSize = sp * dp
        val y = r.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(text, r.centerX(), y, textPaint)
    }

    private fun drawShift(canvas: Canvas, r: RectF) {
        val cx = r.centerX(); val cy = r.centerY(); val s = 9 * dp
        val p = Path().apply {
            moveTo(cx, cy - s); lineTo(cx - s, cy); lineTo(cx - s / 2, cy)
            lineTo(cx - s / 2, cy + s); lineTo(cx + s / 2, cy + s); lineTo(cx + s / 2, cy)
            lineTo(cx + s, cy); close()
        }
        if (shift == Shift.OFF) {
            stroke.color = theme.hint
            canvas.drawPath(p, stroke)
        } else {
            fill.color = if (shift == Shift.LOCKED) theme.accent else stroke.color
            canvas.drawPath(p, fill)
            if (shift == Shift.LOCKED) canvas.drawLine(cx - s / 2, cy + s + 4 * dp, cx + s / 2, cy + s + 4 * dp, stroke)
        }
    }

    private fun drawDelete(canvas: Canvas, r: RectF) {
        val cx = r.centerX(); val cy = r.centerY(); val s = 10 * dp
        canvas.drawLine(cx - s, cy - s, cx - s, cy + s, stroke)
        canvas.drawLine(cx - s / 2, cy, cx + 1.3f * s, cy, stroke)
        canvas.drawLine(cx - s / 2, cy, cx + s / 6, cy - 2 * s / 3, stroke)
        canvas.drawLine(cx - s / 2, cy, cx + s / 6, cy + 2 * s / 3, stroke)
    }

    private fun drawEnter(canvas: Canvas, r: RectF) {
        val cx = r.centerX(); val cy = r.centerY(); val s = 9 * dp
        val p = Path().apply {
            moveTo(cx + s, cy - s); lineTo(cx + s, cy + s / 3); lineTo(cx - s, cy + s / 3)
        }
        canvas.drawPath(p, stroke)
        canvas.drawLine(cx - s, cy + s / 3, cx - s / 2, cy - s / 6, stroke)
        canvas.drawLine(cx - s, cy + s / 3, cx - s / 2, cy + s * 5 / 6, stroke)
    }

    private fun drawGlobe(canvas: Canvas, r: RectF) {
        val cx = r.centerX(); val cy = r.centerY(); val s = 10 * dp
        canvas.drawCircle(cx, cy, s, stroke)
        canvas.drawOval(RectF(cx - s / 2.2f, cy - s, cx + s / 2.2f, cy + s), stroke)
        canvas.drawLine(cx - s, cy, cx + s, cy, stroke)
    }

    private fun drawMic(canvas: Canvas, r: RectF) {
        val cx = r.centerX(); val cy = r.centerY() - 2 * dp; val s = 5 * dp
        fill.color = stroke.color
        canvas.drawRoundRect(RectF(cx - s, cy - 2 * s, cx + s, cy + s / 2), s, s, fill)
        canvas.drawArc(RectF(cx - 1.8f * s, cy - 1.5f * s, cx + 1.8f * s, cy + 1.6f * s), 0f, 180f, false, stroke)
        canvas.drawLine(cx, cy + 1.6f * s, cx, cy + 2.4f * s, stroke)
    }

    private fun drawPopup(canvas: Canvas) {
        val pr = popupRect ?: return
        fill.color = theme.keySpecial
        canvas.drawRoundRect(pr, radius, radius, fill)
        val cellW = pr.width() / popupKeys.size
        popupKeys.forEachIndexed { i, s ->
            val cell = RectF(pr.left + i * cellW, pr.top, pr.left + (i + 1) * cellW, pr.bottom)
            if (i == popupIndex) {
                fill.color = theme.keyPressed
                canvas.drawRoundRect(cell, radius, radius, fill)
                textPaint.color = theme.text
            } else textPaint.color = theme.text
            drawText(canvas, applyShift(s), cell, 22)
        }
    }

    private fun applyShift(s: String) = if (shift != Shift.OFF) s.uppercase() else s

    private fun keyAt(x: Float, y: Float): Pair<Key, RectF>? =
        bounds.firstOrNull { it.second.contains(x, y) }
            ?: bounds.minByOrNull { (_, r) ->
                val dx = x - r.centerX(); val dy = y - r.centerY(); dx * dx + dy * dy
            }

    private fun showPopup() {
        val key = pressed ?: return
        val r = pressedRect ?: return
        if (key.popup.isEmpty()) return
        popupKeys = key.popup
        val cellW = r.width().coerceAtLeast(40 * dp)
        val w = cellW * popupKeys.size
        val left = (r.centerX() - cellW / 2).coerceIn(pad, width - pad - w)
        // El popup se dibuja dentro de la vista; en la primera fila se superpone a la propia tecla
        val top = (r.top - rowHeight).coerceAtLeast(0f)
        popupRect = RectF(left, top, left + w, top + rowHeight - pad)
        popupIndex = 0
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val (key, rect) = keyAt(event.x, event.y) ?: return true
                pressed = key; pressedRect = rect; downY = event.y
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                if (key.type == KeyType.DELETE) {
                    listener?.onKey(KeyType.DELETE)
                    handler.postDelayed(repeatDelete, 400)
                } else if (key.type == KeyType.SPACE) {
                    handler.postDelayed(longPressSpace, 400)
                } else if (key.popup.isNotEmpty()) {
                    handler.postDelayed(longPress, 350)
                }
                invalidate()
            }
            // Deslizar hacia arriba desde la barra espaciadora abre la ventana de Opencode
            MotionEvent.ACTION_MOVE -> if (pressed?.type == KeyType.SPACE && !consumed) {
                if (downY - event.y > 40 * dp) {
                    consumed = true
                    handler.removeCallbacks(longPressSpace)
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    listener?.onKey(KeyType.PANEL)
                }
            } else popupRect?.let { pr ->
                val idx = ((event.x - pr.left) / (pr.width() / popupKeys.size)).toInt()
                    .coerceIn(0, popupKeys.size - 1)
                if (idx != popupIndex) { popupIndex = idx; invalidate() }
            }
            MotionEvent.ACTION_UP -> {
                val key = pressed
                if (popupRect != null && popupIndex >= 0) {
                    listener?.onText(applyShift(popupKeys[popupIndex]))
                } else if (key != null && key.type != KeyType.DELETE && !consumed) {
                    if (key.type == KeyType.CHAR) listener?.onText(displayLabel(key))
                    else listener?.onKey(key.type)
                }
                reset()
            }
            MotionEvent.ACTION_CANCEL -> reset()
        }
        return true
    }

    private fun reset() {
        handler.removeCallbacks(longPress)
        handler.removeCallbacks(repeatDelete)
        handler.removeCallbacks(longPressSpace)
        consumed = false
        pressed = null; pressedRect = null
        popupRect = null; popupKeys = emptyList(); popupIndex = -1
        invalidate()
    }
}
