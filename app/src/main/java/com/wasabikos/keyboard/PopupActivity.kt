package com.wasabikos.keyboard

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
 * Ventana flotante sobre la app actual con la web de AXON u Opencode.
 * Al cerrarla se manda al fondo (no se destruye) para conservar la sesión cargada.
 */
class PopupActivity : Activity() {

    private val dp by lazy { resources.displayMetrics.density }
    private lateinit var webs: List<WebView>
    private lateinit var tabs: List<TextView>
    private var current = 1 // Opencode por defecto

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val theme = KeyboardTheme.from(this)

        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (resources.displayMetrics.heightPixels * 0.55).toInt())
        window.setGravity(Gravity.TOP)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(0.4f)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val urls = listOf(Prefs.get(this, Prefs.AXON_URL), Prefs.get(this, Prefs.OPENCODE_URL))
        webs = urls.map { newWebView(it) }
        tabs = listOf("AXON", "Opencode").map { label ->
            TextView(this).apply {
                text = label; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
                setPadding((14 * dp).toInt(), (6 * dp).toInt(), (14 * dp).toInt(), (6 * dp).toInt())
            }
        }

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            val p = (8 * dp).toInt()
            setPadding(p, p, p, p)
            tabs.forEachIndexed { i, t ->
                t.setOnClickListener { select(i) }
                addView(t, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = p })
            }
            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(TextView(context).apply {
                text = "⟳"; textSize = 20f; setTextColor(theme.hint); setPadding(p * 2, 0, p * 2, 0)
                setOnClickListener { webs[current].reload() }
            })
            addView(TextView(context).apply {
                text = "✕"; textSize = 20f; setTextColor(theme.hint); setPadding(p * 2, 0, p, 0)
                setOnClickListener { moveTaskToBack(true) }
            })
        }
        val content = FrameLayout(this).apply { webs.forEach { addView(it, FrameLayout.LayoutParams(-1, -1)) } }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.background)
            addView(header)
            addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        })
        select(current)
    }

    private fun select(i: Int) {
        current = i
        val theme = KeyboardTheme.from(this)
        webs.forEachIndexed { j, w -> w.visibility = if (j == i) View.VISIBLE else View.GONE }
        tabs.forEachIndexed { j, t ->
            val on = j == i
            t.setTextColor(if (on) theme.background else theme.hint)
            t.background = GradientDrawable().apply { cornerRadius = 16 * dp; setColor(if (on) theme.text else Color.TRANSPARENT) }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun newWebView(url: String) = WebView(this).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        setBackgroundColor(KeyboardTheme.from(context).background)
        webViewClient = WebViewClient()
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
        val w = webs[current]
        if (w.canGoBack()) w.goBack() else moveTaskToBack(true)
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }
}
