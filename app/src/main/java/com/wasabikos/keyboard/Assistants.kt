package com.wasabikos.keyboard

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ChatLine(val fromMe: Boolean, val text: String)

/** Destino del panel: enviar un mensaje y leer los últimos de la sesión. Llamadas bloqueantes (usar en hilo). */
interface Assistant {
    val name: String
    fun recent(limit: Int = 4): List<ChatLine>
    fun send(text: String): String
}

object Prefs {
    const val AXON_URL = "axon_url"
    const val AXON_KEY = "axon_key"
    const val OPENCODE_URL = "opencode_url"
    const val OPENCODE_PASS = "opencode_pass"
    private const val AXON_SESSION = "axon_session"

    val defaults = mapOf(
        AXON_URL to "https://aura.taild7e6a0.ts.net/hermes-api",
        OPENCODE_URL to "https://aura.taild7e6a0.ts.net:10000",
    )

    fun get(ctx: Context, key: String): String =
        ctx.getSharedPreferences("assistants", Context.MODE_PRIVATE).getString(key, null) ?: defaults[key].orEmpty()

    fun set(ctx: Context, key: String, value: String) =
        ctx.getSharedPreferences("assistants", Context.MODE_PRIVATE).edit().putString(key, value.trim()).apply()

    fun axonSession(ctx: Context) = get(ctx, AXON_SESSION)
    fun setAxonSession(ctx: Context, id: String) = set(ctx, AXON_SESSION, id)
}

private fun http(
    url: String,
    method: String = "GET",
    body: JSONObject? = null,
    headers: Map<String, String> = emptyMap(),
    readTimeoutMs: Int = 30_000,
): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.requestMethod = method
        conn.connectTimeout = 15_000
        conn.readTimeout = readTimeoutMs
        conn.setRequestProperty("Accept", "application/json")
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
        }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw RuntimeException("HTTP $code ${text.take(120)}")
        return text
    } finally {
        conn.disconnect()
    }
}

/** Acepta `[...]` o `{messages|sessions|data: [...]}`. */
private fun array(raw: String, vararg keys: String): JSONArray {
    val t = raw.trim()
    if (t.startsWith("[")) return JSONArray(t)
    val o = JSONObject(t)
    keys.forEach { k -> o.optJSONArray(k)?.let { return it } }
    return JSONArray()
}

private fun contentText(v: Any?): String = when (v) {
    is String -> v
    is JSONArray -> (0 until v.length()).joinToString("") { i ->
        val p = v.opt(i)
        if (p is JSONObject) p.optString("text") else p?.toString().orEmpty()
    }
    is JSONObject -> v.optString("text")
    else -> ""
}

/** Hermes (chat de AXON): mismas rutas que `src/lib/hermes.ts`. */
class HermesAssistant(private val ctx: Context) : Assistant {
    override val name = "AXON"
    private val base get() = Prefs.get(ctx, Prefs.AXON_URL).trimEnd('/')

    private fun headers(session: String?) = buildMap {
        Prefs.get(ctx, Prefs.AXON_KEY).takeIf { it.isNotEmpty() }?.let { put("Authorization", "Bearer $it") }
        put("X-Hermes-Session-Key", "axon-keyboard")
        session?.let { put("X-Hermes-Session-Id", it) }
    }

    private fun session(): String {
        Prefs.axonSession(ctx).takeIf { it.isNotEmpty() }?.let { return it }
        val o = JSONObject(http("$base/api/sessions", "POST", JSONObject().put("title", "Teclado"), headers(null)))
        val id = (o.optJSONObject("session") ?: o).getString("id")
        Prefs.setAxonSession(ctx, id)
        return id
    }

    override fun recent(limit: Int): List<ChatLine> {
        val id = session()
        val arr = array(http("$base/api/sessions/$id/messages?limit=$limit&order=latest", headers = headers(id)),
            "messages", "data")
        val lines = (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { it.optString("role") in listOf("user", "assistant") }
            .map { ChatLine(it.optString("role") == "user", contentText(it.opt("content"))) }
            .filter { it.text.isNotBlank() }
        // order=latest devuelve lo más reciente primero
        val ordered = if (arr.length() > 1 && arr.getJSONObject(0).optDouble("timestamp", 0.0) >
            arr.getJSONObject(arr.length() - 1).optDouble("timestamp", 0.0)) lines.reversed() else lines
        return ordered.takeLast(limit)
    }

    override fun send(text: String): String {
        val id = session()
        val run = JSONObject(http("$base/v1/runs", "POST",
            JSONObject().put("session_id", id).put("input", text).put("stream", false), headers(id)))
        val runId = run.optString("run_id").ifEmpty { run.optString("id") }
        if (runId.isEmpty()) throw RuntimeException("Hermes no devolvió run_id")
        val deadline = System.currentTimeMillis() + 5 * 60_000
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(1500)
            val snap = JSONObject(http("$base/v1/runs/$runId", headers = headers(id)))
            when (snap.optString("status")) {
                "completed" -> return recent(1).lastOrNull { !it.fromMe }?.text ?: "✓"
                "failed", "cancelled", "interrupted" ->
                    throw RuntimeException("Run ${snap.optString("status")}: ${snap.optString("error")}")
            }
        }
        return "Sigue trabajando en el servidor; revisa AXON."
    }
}

/** Servidor de opencode (`opencode serve` / `opencode web`): usa la sesión más reciente. */
class OpencodeAssistant(private val ctx: Context) : Assistant {
    override val name = "Opencode"
    private val base get() = Prefs.get(ctx, Prefs.OPENCODE_URL).trimEnd('/')

    private fun headers() = buildMap {
        Prefs.get(ctx, Prefs.OPENCODE_PASS).takeIf { it.isNotEmpty() }?.let {
            put("Authorization", "Basic " + Base64.encodeToString("opencode:$it".toByteArray(), Base64.NO_WRAP))
        }
    }

    private fun session(): String {
        val arr = array(http("$base/session", headers = headers()), "data")
        val latest = (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { !it.has("parentID") }
            .maxByOrNull { it.optJSONObject("time")?.optLong("updated") ?: 0L }
        latest?.let { return it.getString("id") }
        return JSONObject(http("$base/session", "POST", JSONObject().put("title", "Teclado"), headers())).getString("id")
    }

    private fun lineOf(m: JSONObject): ChatLine {
        val parts = m.optJSONArray("parts") ?: JSONArray()
        val text = (0 until parts.length()).map { parts.getJSONObject(it) }
            .filter { it.optString("type") == "text" }
            .joinToString("\n") { it.optString("text") }
        return ChatLine(m.optJSONObject("info")?.optString("role") == "user", text)
    }

    override fun recent(limit: Int): List<ChatLine> {
        val arr = array(http("$base/session/${session()}/message", headers = headers()), "data")
        return (0 until arr.length()).map { lineOf(arr.getJSONObject(it)) }
            .filter { it.text.isNotBlank() }.takeLast(limit)
    }

    override fun send(text: String): String {
        val body = JSONObject().put("parts", JSONArray().put(JSONObject().put("type", "text").put("text", text)))
        // Responde al terminar el turno del agente: puede tardar
        val res = http("$base/session/${session()}/message", "POST", body, headers(), readTimeoutMs = 10 * 60_000)
        return lineOf(JSONObject(res)).text.ifBlank { "✓ (sin texto, revisa Opencode)" }
    }
}
