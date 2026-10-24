package com.wasabikos.keyboard

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

/**
 * Ventana sobre el teclado para hablar con AXON u Opencode sin salir de la app.
 * El texto lo escribe el propio teclado (un EditText dentro del IME no recibe teclas).
 */
class AssistantPanel(context: Context, private val onClose: () -> Unit) : LinearLayout(context) {

    private val dp = resources.displayMetrics.density
    private val assistants = listOf(HermesAssistant(context), OpencodeAssistant(context))
    private var current = 0
    private var busy = false
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private var theme = KeyboardTheme.from(context)
    private val tabs = assistants.map { tab(it.name) }
    private val log = TextView(context).apply { textSize = 14f; setLineSpacing(0f, 1.15f) }
    private val scroll = ScrollView(context).apply { addView(log) }
    private val input = TextView(context).apply { textSize = 16f; maxLines = 3 }
    private val sendBtn = TextView(context).apply {
        text = "➤"; textSize = 22f; gravity = Gravity.CENTER
        setOnClickListener { send() }
    }

    var draft = ""
        private set

    init {
        orientation = VERTICAL
        val p = (8 * dp).toInt()
        setPadding(p, p, p, 0)

        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        tabs.forEachIndexed { i, t ->
            t.setOnClickListener { select(i) }
            header.addView(t, LayoutParams(-2, -2).apply { marginEnd = p })
        }
        header.addView(android.view.View(context), LayoutParams(0, 1, 1f))
        header.addView(TextView(context).apply {
            text = "✕"; textSize = 18f; setPadding(p * 2, p, p, p)
            setOnClickListener { onClose() }
        })
        addView(header)
        addView(scroll, LayoutParams(-1, (150 * dp).toInt()).apply { topMargin = p / 2 })

        val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, p, 0, p) }
        row.addView(input, LayoutParams(0, -2, 1f))
        row.addView(sendBtn, LayoutParams((48 * dp).toInt(), (40 * dp).toInt()))
        addView(row)
        applyTheme(theme)
    }

    private fun tab(label: String) = TextView(context).apply {
        text = label; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
        setPadding((14 * dp).toInt(), (6 * dp).toInt(), (14 * dp).toInt(), (6 * dp).toInt())
    }

    fun applyTheme(t: KeyboardTheme) {
        theme = t
        setBackgroundColor(t.background)
        log.setTextColor(t.text)
        input.setTextColor(t.text)
        sendBtn.setTextColor(t.text)
        input.background = GradientDrawable().apply {
            cornerRadius = 20 * dp; setStroke((1 * dp).toInt(), t.hint)
        }
        val ip = (12 * dp).toInt()
        input.setPadding(ip, ip / 2, ip, ip / 2)
        (getChildAt(0) as LinearLayout).getChildAt(tabs.size + 1).let { (it as TextView).setTextColor(t.hint) }
        renderTabs(); renderInput()
    }

    private fun renderTabs() = tabs.forEachIndexed { i, t ->
        val on = i == current
        t.setTextColor(if (on) theme.background else theme.hint)
        t.background = GradientDrawable().apply {
            cornerRadius = 16 * dp
            setColor(if (on) theme.text else 0)
        }
    }

    private fun renderInput() {
        input.text = if (draft.isEmpty()) "Mensaje para ${assistants[current].name}…" else "$draft▌"
        input.alpha = if (draft.isEmpty()) 0.5f else 1f
        sendBtn.alpha = if (draft.isBlank() || busy) 0.3f else 1f
    }

    fun open() { select(current) }

    fun select(i: Int) {
        current = i
        renderTabs(); renderInput()
        val a = assistants[i]
        status("Cargando…")
        io.execute {
            val r = runCatching { a.recent() }
            main.post {
                if (a !== assistants[current]) return@post
                r.onSuccess { showLines(it) }.onFailure { status("Sin conexión con ${a.name}: ${it.message}") }
            }
        }
    }

    // --- Entrada desde el teclado ---
    fun type(text: String) { draft += text; renderInput() }
    fun delete() { if (draft.isNotEmpty()) { draft = draft.dropLast(1); renderInput() } }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || busy) return
        val a = assistants[current]
        draft = ""; busy = true
        appendLine(ChatLine(true, text)); appendStatus("${a.name} está trabajando…")
        renderInput()
        io.execute {
            val r = runCatching { a.send(text) }
            main.post {
                busy = false
                if (a === assistants[current]) {
                    removeStatus()
                    r.onSuccess { appendLine(ChatLine(false, it)) }
                        .onFailure { appendStatus("Error: ${it.message}") }
                }
                renderInput()
            }
        }
    }

    // --- Historial ---
    private val lines = SpannableStringBuilder()
    private var statusStart = -1

    private fun showLines(list: List<ChatLine>) {
        lines.clear(); statusStart = -1
        if (list.isEmpty()) appendStatus("Sin mensajes todavía") else list.forEach { appendLine(it) }
    }

    private fun status(text: String) { lines.clear(); statusStart = -1; appendStatus(text) }

    private fun appendLine(l: ChatLine) {
        if (lines.isNotEmpty()) lines.append("\n\n")
        val start = lines.length
        lines.append(if (l.fromMe) "Tú: " else "${assistants[current].name}: ")
        lines.setSpan(StyleSpan(Typeface.BOLD), start, lines.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        lines.append(l.text.trim().take(4000))
        flush()
    }

    private fun appendStatus(text: String) {
        removeStatus()
        statusStart = lines.length
        if (lines.isNotEmpty()) lines.append("\n\n")
        val s = lines.length
        lines.append(text)
        lines.setSpan(ForegroundColorSpan(theme.hint), s, lines.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        flush()
    }

    private fun removeStatus() {
        if (statusStart >= 0) { lines.delete(statusStart, lines.length); statusStart = -1 }
    }

    private fun flush() {
        log.text = SpannableStringBuilder(lines)
        scroll.post { scroll.fullScroll(FOCUS_DOWN) }
    }
}
