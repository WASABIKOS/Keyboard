package com.wasabikos.keyboard

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView

/** Biblioteca de emojis dentro del propio teclado (misma altura, reemplaza las teclas). */
class EmojiView(
    context: Context,
    private val listener: Listener,
    private val keyboard: View,
) : LinearLayout(context) {

    // Misma altura que el teclado: sin esto MATCH_PARENT crece hasta toda la pantalla
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = keyboard.measuredHeight.takeIf { it > 0 } ?: (232 * resources.displayMetrics.density).toInt()
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
    }


    interface Listener {
        fun onEmoji(emoji: String)
        fun onEmojiDelete()
        fun onEmojiClose()
    }

    private val dp = resources.displayMetrics.density
    private val prefs = context.getSharedPreferences("emoji", Context.MODE_PRIVATE)
    private var theme = KeyboardTheme.from(context)
    private var category = 0
    private var items: List<String> = emptyList()

    private val tabs = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    private val grid = GridView(context).apply {
        numColumns = 8
        stretchMode = GridView.STRETCH_COLUMN_WIDTH
        isVerticalScrollBarEnabled = false
        selector = GradientDrawable() // sin realce de color al tocar
    }
    private val adapter = object : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            ((convertView as? TextView) ?: TextView(context).apply {
                textSize = 24f
                gravity = Gravity.CENTER
                layoutParams = ViewGroup.LayoutParams(-1, (44 * dp).toInt())
            }).apply { text = items[position] }
    }

    private val catIcons = listOf(
        EmojiIcon.Kind.RECENT, EmojiIcon.Kind.SMILEY, EmojiIcon.Kind.ANIMAL, EmojiIcon.Kind.FOOD,
        EmojiIcon.Kind.ACTIVITY, EmojiIcon.Kind.TRAVEL, EmojiIcon.Kind.OBJECT, EmojiIcon.Kind.SYMBOL, EmojiIcon.Kind.FLAG,
    )
    private val closeIcon = EmojiIcon(context, EmojiIcon.Kind.CLOSE)
    private val deleteIcon = EmojiIcon(context, EmojiIcon.Kind.DELETE)
    private val deleteRepeat = object : Runnable {
        override fun run() { listener.onEmojiDelete(); postDelayed(this, 50) }
    }

    init {
        orientation = VERTICAL
        grid.adapter = adapter
        grid.setOnItemClickListener { v, _, pos, _ ->
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            val e = items[pos]
            addRecent(e)
            listener.onEmoji(e)
        }
        val h = (44 * dp).toInt()
        closeIcon.setOnClickListener { listener.onEmojiClose() }
        tabs.addView(closeIcon, LayoutParams(0, h, 1f))
        catIcons.forEachIndexed { i, kind ->
            tabs.addView(EmojiIcon(context, kind).apply { setOnClickListener { select(i) } }, LayoutParams(0, h, 1f))
        }
        deleteIcon.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    listener.onEmojiDelete(); postDelayed(deleteRepeat, 400)
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> removeCallbacks(deleteRepeat)
            }
            true
        }
        tabs.addView(deleteIcon, LayoutParams(0, h, 1f))
        addView(tabs)
        addView(grid, LayoutParams(-1, 0, 1f))

        select(if (recents().isEmpty()) 1 else 0)
        applyTheme(theme)
    }

    fun applyTheme(t: KeyboardTheme) {
        theme = t
        setBackgroundColor(t.background)
        closeIcon.color = t.text
        deleteIcon.color = t.text
        renderTabs()
    }

    // Hijos de tabs: [cerrar, categorías..., borrar]
    private fun renderTabs() = catIcons.indices.forEach { i ->
        (tabs.getChildAt(i + 1) as EmojiIcon).color = if (i == category) theme.text else theme.hint
    }

    fun select(i: Int) {
        category = i
        items = if (i == 0) recents() else Emojis.categories[i].second
        adapter.notifyDataSetChanged()
        grid.setSelection(0)
        renderTabs()
    }

    // --- Deslizar a izquierda/derecha cambia de categoría ---
    private var downX = 0f
    private var downY = 0f
    private var swiped = false

    override fun onInterceptTouchEvent(ev: android.view.MotionEvent): Boolean {
        when (ev.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> { downX = ev.x; downY = ev.y; swiped = false }
            android.view.MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX; val dy = ev.y - downY
                if (ev.y > tabs.bottom && kotlin.math.abs(dx) > 48 * dp && kotlin.math.abs(dx) > 2 * kotlin.math.abs(dy)) {
                    swipe(dx); return true
                }
            }
        }
        return false
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: android.view.MotionEvent): Boolean = true // consume el resto del gesto

    private fun swipe(dx: Float) {
        if (swiped) return
        swiped = true
        val next = (category + if (dx < 0) 1 else -1).coerceIn(0, catIcons.size - 1)
        if (next != category) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            select(next)
        }
    }

    private fun recents(): List<String> =
        prefs.getString("recent", "").orEmpty().split('\u0001').filter { it.isNotEmpty() }

    private fun addRecent(e: String) {
        val list = (listOf(e) + recents().filter { it != e }).take(32)
        prefs.edit().putString("recent", list.joinToString("\u0001")).apply()
    }
}
