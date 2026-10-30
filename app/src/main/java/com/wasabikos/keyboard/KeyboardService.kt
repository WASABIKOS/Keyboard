package com.wasabikos.keyboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.Toast
import java.util.Locale

class KeyboardService : InputMethodService(), KeyboardView.Listener {

    private var view: KeyboardView? = null
    private var recognizer: SpeechRecognizer? = null
    private var lastShiftTap = 0L

    private var emoji: EmojiView? = null

    override fun onCreateInputView(): View {
        val kb = KeyboardView(this).also { it.listener = this; view = it }
        val em = EmojiView(this, emojiListener, kb).also { it.visibility = View.GONE; emoji = it }
        // El teclado (INVISIBLE, no GONE) fija la altura; los emojis lo cubren con la misma altura
        return FrameLayout(this).apply {
            addView(kb)
            addView(em, FrameLayout.LayoutParams(-1, -2))
        }
    }

    private fun showEmoji(show: Boolean) {
        emoji?.visibility = if (show) View.VISIBLE else View.GONE
        view?.visibility = if (show) View.INVISIBLE else View.VISIBLE
        if (show) emoji?.applyTheme(KeyboardTheme.from(this)) else updateAutoCaps()
    }

    private val emojiListener = object : EmojiView.Listener {
        override fun onEmoji(emoji: String) { currentInputConnection?.commitText(emoji, 1) }
        override fun onEmojiDelete() = onKey(KeyType.DELETE)
        override fun onEmojiClose() = showEmoji(false)
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val v = view ?: return
        v.theme = KeyboardTheme.from(this)
        v.reloadHeight()
        showEmoji(false)
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        textLayout = when {
            cls != InputType.TYPE_CLASS_TEXT -> Layouts.letters
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> Layouts.email
            variation == InputType.TYPE_TEXT_VARIATION_URI -> Layouts.url
            else -> Layouts.letters
        }
        v.rows = if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE)
            Layouts.numpad else textLayout
        // En contraseñas no se dicta: el audio saldría del dispositivo
        privateField = isPassword(info) || info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0
        v.voiceEnabled = !isPassword(info)
        v.enterLabel = when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> "Ir"
            EditorInfo.IME_ACTION_SEARCH -> "Buscar"
            EditorInfo.IME_ACTION_SEND -> "Enviar"
            EditorInfo.IME_ACTION_NEXT -> "Sig."
            EditorInfo.IME_ACTION_DONE -> "Listo"
            else -> null
        }
        updateAutoCaps()
    }

    private var textLayout = Layouts.letters
    private var privateField = false
    private var lastSpaceTime = 0L

    private fun isPassword(info: EditorInfo): Boolean {
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        return when (cls) {
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    private var hasSelection = false
    private var capsAfterUpdate = false

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
                                   candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        hasSelection = newSelStart != newSelEnd
        // Mayúscula automática tras borrar: se evalúa cuando el editor confirma, no en el toque
        if (capsAfterUpdate) { capsAfterUpdate = false; updateAutoCaps() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        view?.theme = KeyboardTheme.from(this)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        stopListening()
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }

    // --- Entrada de teclas ---

    override fun onText(text: String) {
        val v = view ?: return
        val ic = currentInputConnection ?: return
        ic.commitText(text, 1)
        if (v.shift == KeyboardView.Shift.ON) v.shift = KeyboardView.Shift.OFF
        if (text == "." || text == "?" || text == "!") updateAutoCaps()
    }

    override fun onKey(type: KeyType) {
        val ic = currentInputConnection ?: return
        val v = view ?: return
        when (type) {
            KeyType.DELETE -> {
                if (hasSelection) ic.commitText("", 1)
                else {
                    // Borra un carácter visible completo (emojis compuestos, banderas)
                    val before = ic.getTextBeforeCursor(8, 0)?.toString().orEmpty()
                    val n = if (before.isEmpty()) 1 else {
                        val it = java.text.BreakIterator.getCharacterInstance()
                        it.setText(before)
                        before.length - it.preceding(before.length).coerceAtLeast(0)
                    }
                    ic.deleteSurroundingText(n, 0)
                }
                capsAfterUpdate = true
            }
            KeyType.SPACE -> {
                if (v.listening) { stopListening(); return }
                // Doble espacio rápido tras una palabra → ". "
                val now = System.currentTimeMillis()
                val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
                if (!privateField && now - lastSpaceTime < 400 && before.length == 2 &&
                    before[1] == ' ' && before[0].isLetterOrDigit()) {
                    ic.deleteSurroundingText(1, 0)
                    ic.commitText(". ", 1)
                    lastSpaceTime = 0L
                } else {
                    ic.commitText(" ", 1)
                    lastSpaceTime = now
                }
                updateAutoCaps()
            }
            KeyType.ENTER -> {
                val info = currentInputEditorInfo
                val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
                val noEnterAction = info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
                if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED && !noEnterAction) {
                    ic.performEditorAction(action)
                } else {
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                }
                updateAutoCaps()
            }
            KeyType.SHIFT -> {
                val now = System.currentTimeMillis()
                v.shift = when {
                    v.shift == KeyboardView.Shift.OFF -> KeyboardView.Shift.ON
                    v.shift == KeyboardView.Shift.ON && now - lastShiftTap < 400 -> KeyboardView.Shift.LOCKED
                    else -> KeyboardView.Shift.OFF
                }
                lastShiftTap = now
            }
            KeyType.SYMBOLS -> { v.shift = KeyboardView.Shift.OFF; v.rows = Layouts.symbols }
            KeyType.MORE_SYMBOLS -> v.rows = Layouts.moreSymbols
            KeyType.LETTERS -> { v.rows = textLayout; updateAutoCaps() }
            KeyType.GLOBE -> switchKeyboard()
            KeyType.MIC -> if (v.voiceEnabled) toggleListening()
                else Toast.makeText(this, "Dictado desactivado en contraseñas", Toast.LENGTH_SHORT).show()
            KeyType.EMOJI -> showEmoji(true)
            KeyType.CHAR -> Unit
        }
    }

    override fun onCursor(delta: Int) {
        val ic = currentInputConnection ?: return
        val code = if (delta < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(kotlin.math.abs(delta)) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
    }

    /** Borra la palabra anterior junto con los espacios que la siguen. */
    override fun onDeleteWord() {
        val ic = currentInputConnection ?: return
        if (hasSelection) { ic.commitText("", 1); return }
        val before = ic.getTextBeforeCursor(64, 0)?.toString().orEmpty()
        if (before.isEmpty()) return
        val trimmed = before.trimEnd()
        val start = trimmed.indexOfLast { it.isWhitespace() } + 1
        ic.deleteSurroundingText((before.length - start).coerceAtLeast(1), 0)
        capsAfterUpdate = true
    }

    private fun switchKeyboard() {
        val switched = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) switchToNextInputMethod(false)
        else false
        if (!switched) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
    }

    private fun updateAutoCaps() {
        val v = view ?: return
        if (v.shift == KeyboardView.Shift.LOCKED || v.rows !== textLayout) return
        val info = currentInputEditorInfo ?: return
        val ic = currentInputConnection ?: return
        val caps = info.inputType != InputType.TYPE_NULL && ic.getCursorCapsMode(info.inputType) != 0
        v.shift = if (caps) KeyboardView.Shift.ON else KeyboardView.Shift.OFF
    }

    // --- Dictado por voz ---

    private fun toggleListening() {
        if (view?.listening == true) { stopListening(); return }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Concede el permiso de micrófono", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_REQUEST_MIC, true))
            return
        }
        val sr = recognizer ?: createRecognizer()?.also {
            it.setRecognitionListener(recognitionListener)
            recognizer = it
        } ?: run {
            Toast.makeText(this, "No hay servicio de voz (STT). Instala o activa Google Speech Services", Toast.LENGTH_LONG).show()
            return
        }
        val lang = spanishTag()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        sr.startListening(intent)
        view?.listening = true
        view?.spaceLabel = "Escuchando…"
    }

    /** Detecta el motor STT: el del sistema y, si no hay, el reconocedor local (Android 12+). */
    private fun createRecognizer(): SpeechRecognizer? = when {
        SpeechRecognizer.isRecognitionAvailable(this) -> SpeechRecognizer.createSpeechRecognizer(this)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(this) ->
            SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
        else -> null
    }

    /** Español siempre: usa la variante regional del sistema si es español, si no es-US (latinoamericano). */
    private fun spanishTag(): String {
        val l = Locale.getDefault()
        return if (l.language == "es" && l.country.isNotEmpty()) l.toLanguageTag() else "es-US"
    }

    private fun stopListening() {
        recognizer?.cancel()
        currentInputConnection?.finishComposingText()
        onListeningEnded()
    }

    private fun onListeningEnded() {
        view?.listening = false
        view?.spaceLabel = ""
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    private val spokenPunctuation = listOf(
        "signo de interrogación" to "?", "signo de exclamación" to "!",
        "nueva línea" to "\n", "punto y coma" to ";", "dos puntos" to ":",
        "puntos suspensivos" to "…", "punto y aparte" to ".\n", "coma" to ",", "punto" to ".",
    )

    /** Convierte la puntuación dictada ("coma", "punto"…) en signos, sin espacio previo. */
    private fun punctuate(text: String): String {
        var out = text
        for ((word, sign) in spokenPunctuation) {
            out = out.replace(Regex("\\s*\\b$word\\b", RegexOption.IGNORE_CASE), sign)
        }
        // Mayúscula tras fin de frase dentro del propio dictado
        return out.replace(Regex("([.!?\n]\\s*)(\\p{Ll})")) { it.groupValues[1] + it.groupValues[2].uppercase() }
    }

    /** Añade espacio inicial y mayúscula según el texto que precede al cursor. */
    private fun format(text: String): String {
        val before = currentInputConnection?.getTextBeforeCursor(2, 0)?.toString().orEmpty()
        val needsSpace = before.isNotEmpty() && !before.last().isWhitespace()
        val sentenceStart = before.isBlank() || before.trimEnd().lastOrNull() in listOf('.', '!', '?', '\n')
        val body = if (sentenceStart) punctuate(text).replaceFirstChar { it.titlecase(Locale.getDefault()) } else punctuate(text)
        return (if (needsSpace) " " else "") + body
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle?) {
            val text = firstResult(partialResults) ?: return
            currentInputConnection?.setComposingText(format(text), 1)
        }

        override fun onResults(results: Bundle?) {
            val ic = currentInputConnection
            val text = firstResult(results)
            if (ic != null) {
                if (text != null) {
                    ic.setComposingText("", 1) // descarta el parcial antes de calcular el formato
                    ic.commitText(format(text), 1)
                } else ic.finishComposingText()
            }
            onListeningEnded()
        }

        override fun onError(error: Int) {
            currentInputConnection?.finishComposingText()
            onListeningEnded()
            val msg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No se entendió, intenta de nuevo"
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Error de red en el dictado"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Falta el permiso de micrófono"
                SpeechRecognizer.ERROR_CLIENT -> null
                else -> "Error de dictado ($error)"
            }
            msg?.let { Toast.makeText(this@KeyboardService, it, Toast.LENGTH_SHORT).show() }
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
