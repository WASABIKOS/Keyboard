package com.wasabikos.keyboard

import android.content.Context

object Prefs {
    const val OPENCODE_URL = "opencode_url"

    private val defaults = mapOf(
        OPENCODE_URL to "https://aura.taild7e6a0.ts.net:10000/",
    )

    private fun sp(ctx: Context) = ctx.getSharedPreferences("assistants", Context.MODE_PRIVATE)

    fun get(ctx: Context, key: String): String =
        sp(ctx).getString(key, null)?.takeIf { it.isNotBlank() } ?: defaults[key].orEmpty()

    fun set(ctx: Context, key: String, value: String) = sp(ctx).edit().putString(key, value.trim()).apply()
}
