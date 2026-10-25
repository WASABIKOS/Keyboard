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
class EmojiView(context: Context, private val listener: Listener) : LinearLayout(context) {

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
                textSize = 26f
                gravity = Gravity.CENTER
                layoutParams = ViewGroup.LayoutParams(-1, (46 * dp).toInt())
            }).apply { text = items[position] }
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
        Emojis.categories.forEachIndexed { i, (icon, _) ->
            tabs.addView(TextView(context).apply {
                text = icon; textSize = 18f; gravity = Gravity.CENTER
                setOnClickListener { select(i) }
            }, LayoutParams(0, (40 * dp).toInt(), 1f))
        }
        addView(tabs)
        addView(grid, LayoutParams(-1, 0, 1f))

        val bottom = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        bottom.addView(button("ABC") { listener.onEmojiClose() }, LayoutParams(0, (52 * dp).toInt(), 1.5f))
        bottom.addView(View(context), LayoutParams(0, 1, 7f))
        bottom.addView(button("⌫") { listener.onEmojiDelete() }, LayoutParams(0, (52 * dp).toInt(), 1.5f))
        addView(bottom)

        select(if (recents().isEmpty()) 1 else 0)
        applyTheme(theme)
    }

    private fun button(label: String, onClick: () -> Unit) = TextView(context).apply {
        text = label; textSize = 16f; gravity = Gravity.CENTER
        setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onClick()
        }
    }

    fun applyTheme(t: KeyboardTheme) {
        theme = t
        setBackgroundColor(t.background)
        (getChildAt(2) as LinearLayout).let { b ->
            (0 until b.childCount).forEach { (b.getChildAt(it) as? TextView)?.setTextColor(t.text) }
        }
        renderTabs()
    }

    private fun renderTabs() = (0 until tabs.childCount).forEach { i ->
        val v = tabs.getChildAt(i)
        v.alpha = if (i == category) 1f else 0.45f
        v.background = if (i == category) GradientDrawable().apply {
            cornerRadius = 12 * dp; setColor(theme.keyPressed)
        } else null
    }

    fun select(i: Int) {
        category = i
        items = if (i == 0) recents() else Emojis.categories[i].second
        adapter.notifyDataSetChanged()
        grid.setSelection(0)
        renderTabs()
    }

    private fun recents(): List<String> =
        prefs.getString("recent", "").orEmpty().split('\u0001').filter { it.isNotEmpty() }

    private fun addRecent(e: String) {
        val list = (listOf(e) + recents().filter { it != e }).take(32)
        prefs.edit().putString("recent", list.joinToString("\u0001")).apply()
    }
}
