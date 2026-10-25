package com.wasabikos.keyboard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Pantalla de configuración: activar el teclado, elegirlo y conceder el micrófono. */
class MainActivity : Activity() {

    companion object {
        const val EXTRA_REQUEST_MIC = "request_mic"
        private const val REQ_MIC = 1
    }

    private lateinit var micButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val p = (24 * dp).toInt()
            setPadding(p, p * 2, p, p)
        }
        fun button(text: String, action: () -> Unit) = Button(this).apply {
            this.text = text
            setOnClickListener { action() }
            root.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (12 * dp).toInt() })
        }

        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 26f
        })
        root.addView(TextView(this).apply {
            text = "1. Activa el teclado\n2. Selecciónalo como predeterminado\n3. Concede el micrófono para el dictado por voz"
            textSize = 16f
            setPadding(0, (16 * dp).toInt(), 0, (8 * dp).toInt())
        })
        button("1. Activar teclado") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        button("2. Seleccionar teclado") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        micButton = button("3. Permitir micrófono") {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
        }
        root.addView(EditText(this).apply { hint = "Prueba el teclado aquí" },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = (24 * dp).toInt() })
        setContentView(ScrollView(this).apply { addView(root) })

        if (intent.getBooleanExtra(EXTRA_REQUEST_MIC, false) && !hasMic()) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshMic()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshMic()
    }

    private fun hasMic() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun refreshMic() {
        micButton.isEnabled = !hasMic()
        micButton.text = if (hasMic()) "3. Micrófono concedido ✓" else "3. Permitir micrófono"
    }
}
