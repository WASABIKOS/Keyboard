package com.wasabikos.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build

/** Paleta del teclado: sigue modo claro/oscuro y, en Android 12+, los colores dinámicos (Material You). */
data class KeyboardTheme(
    val background: Int,
    val key: Int,
    val keySpecial: Int,
    val keyPressed: Int,
    val text: Int,
    val hint: Int,
    val accent: Int,
    val onAccent: Int,
) {
    companion object {
        fun from(context: Context): KeyboardTheme {
            val night = (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                fun c(id: Int) = context.getColor(id)
                return if (night) KeyboardTheme(
                    background = Color.BLACK,
                    key = c(android.R.color.system_neutral1_800),
                    keySpecial = c(android.R.color.system_neutral2_700),
                    keyPressed = c(android.R.color.system_accent1_700),
                    text = Color.WHITE,
                    hint = c(android.R.color.system_neutral2_300),
                    accent = c(android.R.color.system_accent1_200),
                    onAccent = c(android.R.color.system_accent1_800),
                ) else KeyboardTheme(
                    background = c(android.R.color.system_neutral1_100),
                    key = c(android.R.color.system_neutral1_10),
                    keySpecial = c(android.R.color.system_accent2_100),
                    keyPressed = c(android.R.color.system_accent1_200),
                    text = c(android.R.color.system_neutral1_900),
                    hint = c(android.R.color.system_neutral2_600),
                    accent = c(android.R.color.system_accent1_600),
                    onAccent = c(android.R.color.system_accent1_0),
                )
            }

            return if (night) KeyboardTheme(
                background = Color.BLACK,
                key = 0xFF1E1E1E.toInt(),
                keySpecial = 0xFF2C2C2C.toInt(),
                keyPressed = 0xFF3D5A40.toInt(),
                text = Color.WHITE,
                hint = 0xFFB0B0B0.toInt(),
                accent = 0xFF81C784.toInt(),
                onAccent = Color.BLACK,
            ) else KeyboardTheme(
                background = 0xFFECEFF1.toInt(),
                key = Color.WHITE,
                keySpecial = 0xFFCFD8DC.toInt(),
                keyPressed = 0xFFA5D6A7.toInt(),
                text = 0xFF212121.toInt(),
                hint = 0xFF616161.toInt(),
                accent = 0xFF2E7D32.toInt(),
                onAccent = Color.WHITE,
            )
        }
    }
}
