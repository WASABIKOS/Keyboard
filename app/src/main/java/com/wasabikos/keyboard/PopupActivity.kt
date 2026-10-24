package com.wasabikos.keyboard

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Ventana flotante con la web de Opencode, pegada encima del teclado.
 * La ventana ocupa toda la pantalla pero es transparente: la tarjeta va abajo y sube con el teclado;
 * tocar el área vacía la cierra. Al cerrar se manda al fondo para conservar la página cargada.
 */
class PopupActivity : Activity() {

    private val dp by lazy { resources.displayMetrics.density }
    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val theme = KeyboardTheme.from(this)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        )

        web = newWebView(Prefs.get(this, Prefs.OPENCODE_URL))
        val p = (8 * dp).toInt()

        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setPadding(p, p / 2, p, 0)
            addView(TextView(context).apply {
                text = "⟳"; textSize = 18f; setTextColor(theme.hint); setPadding(p * 2, p / 2, p * 2, p / 2)
                setOnClickListener { web.reload() }
            })
            addView(TextView(context).apply {
                text = "✕"; textSize = 18f; setTextColor(theme.hint); setPadding(p * 2, p / 2, p, p / 2)
                setOnClickListener { close() }
            })
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(theme.background)
                cornerRadii = floatArrayOf(16 * dp, 16 * dp, 16 * dp, 16 * dp, 0f, 0f, 0f, 0f)
            }
            clipToOutline = true
            isClickable = true // que los toques en la tarjeta no la cierren
            addView(bar)
            addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        }

        val root = FrameLayout(this).apply {
            setOnClickListener { close() } // tocar fuera de la tarjeta
            addView(card, FrameLayout.LayoutParams(-1, (resources.displayMetrics.heightPixels * 0.45).toInt(), Gravity.BOTTOM))
        }
        // Android 15 dibuja de borde a borde: respetamos barra de estado, navegación y teclado a mano
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(0, bars.top, 0, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
            }
            insets
        }
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        focusPrompt()
    }

    private fun close() {
        CookieManager.getInstance().flush()
        moveTaskToBack(true)
    }

    /** Pone el foco en el cuadro de mensaje de Opencode para que el teclado siga abierto. */
    private fun focusPrompt() {
        web.requestFocus()
        web.evaluateJavascript(
            "(function(){var e=document.querySelector('textarea,[contenteditable=\"true\"],input[type=text]');" +
                "if(e){e.focus();}})()", null
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun newWebView(url: String) = WebView(this).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        setBackgroundColor(KeyboardTheme.from(context).background)
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                // La SPA tarda en montar el compositor
                view.postDelayed({ focusPrompt() }, 800)
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                val allowed = request.resources.filter { it == PermissionRequest.RESOURCE_AUDIO_CAPTURE && mic }
                if (allowed.isEmpty()) request.deny() else request.grant(allowed.toTypedArray())
            }
        }
        loadUrl(url)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else close()
    }
}
