package com.vidadomu.nexus.assistant

import android.content.Context
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.llm.BrainPresets
import com.vidadomu.nexus.agent.core.llm.BrainProfile
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Cerebros (LLM) definidos por el usuario más BytePlus, que viene preconfigurado. El perfil (URL, modelo,
 * capacidades, límites…) se guarda en preferencias; la CLAVE nunca va ahí: se cifra aparte con SecureStorage,
 * una por cerebro, y no se copia a logs ni al repositorio.
 */
class BrainStore(private val app: NexusApplication) {
    private val prefs = app.getSharedPreferences("brain_profiles_v1", Context.MODE_PRIVATE)
    private val secure get() = app.secureStorage
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(BrainProfile.serializer())

    private fun stored(): List<BrainProfile> = runCatching { json.decodeFromString(serializer, prefs.getString(KEY, "[]").orEmpty()) }.getOrDefault(emptyList())

    /** BytePlus (guardado o de fábrica) primero y luego los demás por nombre. */
    fun all(): List<BrainProfile> {
        val saved = stored()
        val builtin = saved.firstOrNull { it.id == BrainPresets.BYTEPLUS_ID }?.copy(builtin = true) ?: BrainPresets.byteplus()
        return listOf(builtin) + saved.filter { it.id != BrainPresets.BYTEPLUS_ID }.sortedBy { it.name.lowercase() }
    }

    fun get(id: String): BrainProfile? = all().firstOrNull { it.id == id }

    /** [newKey]: null = no tocar la clave; "" = borrarla. Lanza IllegalArgumentException con el primer problema. */
    fun save(profile: BrainProfile, newKey: String?) {
        val clean = profile.copy(name = profile.name.trim(), baseUrl = profile.baseUrl.trim().trimEnd('/'), modelId = profile.modelId.trim(),
            builtin = profile.id == BrainPresets.BYTEPLUS_ID)
        clean.problems().firstOrNull()?.let { throw IllegalArgumentException(it) }
        val next = stored().filter { it.id != clean.id } + clean
        check(prefs.edit().putString(KEY, json.encodeToString(serializer, next)).commit()) { "No se pudo guardar el cerebro." }
        when {
            newKey == null -> Unit
            newKey.isBlank() -> secure.clearProviderApiKey(keyId(clean.id))
            else -> secure.saveProviderApiKey(keyId(clean.id), newKey.trim())
        }
    }

    /** BytePlus no se borra (siempre disponible): solo se puede quitar su clave. */
    fun delete(id: String): Boolean {
        if (id == BrainPresets.BYTEPLUS_ID) return false
        val next = stored().filter { it.id != id }
        check(prefs.edit().putString(KEY, json.encodeToString(serializer, next)).commit()) { "No se pudo eliminar el cerebro." }
        secure.clearProviderApiKey(keyId(id))
        return true
    }

    /** Copia con id y nombre nuevos (la clave también se copia para que funcione de inmediato). */
    fun duplicate(id: String): BrainProfile? {
        val source = get(id) ?: return null
        val copy = source.copy(id = uniqueId(BrainProfile.slug(source.name + " copia")), name = source.name + " (copia)", builtin = false)
        save(copy, apiKey(id).takeIf { it.isNotBlank() })
        return copy
    }

    fun uniqueId(base: String): String {
        val taken = all().map { it.id }.toSet()
        if (base !in taken && BrainProfile.ID.matches(base)) return base
        var n = 2
        while ("${base.take(36)}-$n" in taken) n++
        return "${base.take(36)}-$n"
    }

    fun apiKey(id: String): String = runCatching { secure.getProviderApiKey(keyId(id)) }.getOrDefault("")
    fun hasKey(id: String): Boolean = runCatching { secure.hasProviderApiKey(keyId(id)) }.getOrDefault(false)
    fun maskedKey(id: String): String = runCatching { secure.getMaskedProviderApiKey(keyId(id)).takeIf { secure.hasProviderApiKey(keyId(id)) }.orEmpty() }.getOrDefault("")

    companion object {
        private const val KEY = "profiles"
        fun keyId(id: String) = "nexus_brain_key_$id"
        const val PREFIX = "custom:"
        fun backendFor(id: String) = PREFIX + id
    }
}
