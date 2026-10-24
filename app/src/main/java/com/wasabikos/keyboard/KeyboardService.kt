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
import android.widget.LinearLayout
import android.widget.Toast
import java.util.Locale

class KeyboardService : InputMethodService(), KeyboardView.Listener {

    private var view: KeyboardView? = null
    private var panel: AssistantPanel? = null
    private val panelOpen get() = panel?.visibility == View.VISIBLE
    private var recognizer: SpeechRecognizer? = null
    private var lastShiftTap = 0L

    override fun onCreateInputView(): View =
        KeyboardView(this).also {
            it.listener = this
                        view = it
        }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val v = view ?: return
        v.theme = KeyboardTheme.from(this)
        panel?.visibility = View.GONE
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        v.rows = if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE)
            Layouts.symbols else Layouts.letters
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        view?.theme = KeyboardTheme.from(this)
        panel?.applyTheme(KeyboardTheme.from(this))
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
        if (panelOpen) {
            panel?.type(text)
            if (v.shift == KeyboardView.Shift.ON) v.shift = KeyboardView.Shift.OFF
            return
        }
        val ic = currentInputConnection ?: return
        ic.commitText(text, 1)
        if (v.shift == KeyboardView.Shift.ON) v.shift = KeyboardView.Shift.OFF
        if (text == "." || text == "?" || text == "!") updateAutoCaps()
    }

    override fun onKey(type: KeyType) {
        val ic = currentInputConnection ?: return
        val v = view ?: return
        if (panelOpen && routeToPanel(type, v)) return
        when (type) {
            KeyType.DELETE -> {
                val selected = ic.getSelectedText(0)
                if (!selected.isNullOrEmpty()) ic.commitText("", 1)
                else {
                    // Borra un code point completo (emojis/pares sustitutos)
                    val before = ic.getTextBeforeCursor(2, 0)
                    val n = if (before != null && before.length == 2 &&
                        Character.isSurrogatePair(before[0], before[1])) 2 else 1
                    ic.deleteSurroundingText(n, 0)
                }
                updateAutoCaps()
            }
            KeyType.SPACE -> {
                if (v.listening) stopListening() else { ic.commitText(" ", 1); updateAutoCaps() }
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
            KeyType.LETTERS -> { v.rows = Layouts.letters; updateAutoCaps() }
            KeyType.GLOBE -> switchKeyboard()
            KeyType.MIC -> toggleListening()
            KeyType.PANEL -> openPanel()
            KeyType.CHAR -> Unit
        }
    }

    /** Con el panel abierto, borrar/espacio/enter actúan sobre su borrador. */
    private fun routeToPanel(type: KeyType, v: KeyboardView): Boolean {
        val p = panel ?: return false
        when (type) {
            KeyType.DELETE -> p.delete()
            KeyType.SPACE -> if (v.listening) stopListening() else p.type(" ")
            KeyType.ENTER -> p.send()
            KeyType.PANEL -> closePanel()
            else -> return false
        }
        return true
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
        if (v.shift == KeyboardView.Shift.LOCKED || v.rows !== Layouts.letters) return
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

    /** Añade espacio inicial y mayúscula según el texto que precede al cursor. */
    private fun format(text: String): String {
        val before = currentInputConnection?.getTextBeforeCursor(2, 0)?.toString().orEmpty()
        val needsSpace = before.isNotEmpty() && !before.last().isWhitespace()
        val sentenceStart = before.isBlank() || before.trimEnd().lastOrNull() in listOf('.', '!', '?', '\n')
        val body = if (sentenceStart) text.replaceFirstChar { it.titlecase(Locale.getDefault()) } else text
        return (if (needsSpace) " " else "") + body
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle?) {
            val text = firstResult(partialResults) ?: return
            if (panelOpen) return
            currentInputConnection?.setComposingText(format(text), 1)
        }

        override fun onResults(results: Bundle?) {
            val ic = currentInputConnection
            val text = firstResult(results)
            if (panelOpen) {
                text?.let { t -> panel?.let { p -> p.type((if (p.draft.isEmpty() || p.draft.endsWith(" ")) "" else " ") + t) } }
            } else if (ic != null) {
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
