package com.vidadomu.nexus.assistant

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Una respuesta del agente: quién, cuánto tardó y cuánto consumió. Sin textos ni archivos. */
@Serializable
data class TurnSample(
    val at: Long,
    val agent: String,
    val model: String,
    val durationMs: Long,
    val inputTokens: Int,
    val outputTokens: Int,
    val calls: Int,
    val rounds: Int,
)

/** Registro local y acotado (últimos 1000 turnos) para la telemetría de Ajustes → Sistema. Nada sale del teléfono. */
object TurnTelemetry {
    private const val KEY = "turns"
    private const val MAX = 1000
    private val json = Json { ignoreUnknownKeys = true }
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("telemetry_v1", Context.MODE_PRIVATE)

    @Synchronized
    fun record(context: Context, sample: TurnSample) {
        runCatching {
            val next = (all(context) + sample).takeLast(MAX)
            prefs(context).edit().putString(KEY, json.encodeToString(next)).apply()
        }
    }

    fun all(context: Context): List<TurnSample> =
        runCatching { json.decodeFromString<List<TurnSample>>(prefs(context).getString(KEY, "[]").orEmpty()) }.getOrDefault(emptyList())

    @Synchronized
    fun clear(context: Context) { prefs(context).edit().remove(KEY).apply() }
}
