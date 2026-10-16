package com.vidadomu.nexus.provider.custom

import android.content.Context
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.media.MediaProviderConfig
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File

/**
 * Proveedores multimedia definidos por el usuario. La configuración (sin secretos) va en preferencias; la CLAVE se cifra
 * aparte con SecureStorage, una por proveedor. Guardar o borrar recarga el registro y el catálogo sin reiniciar la app,
 * y no toca Atlas, Crun, Runway, Flow ni el simulado.
 */
class CustomMediaStore(private val app: NexusApplication, private val client: OkHttpClient) {
    private val prefs = app.getSharedPreferences("media_providers_v1", Context.MODE_PRIVATE)
    private val secure get() = app.secureStorage
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(MediaProviderConfig.serializer())

    fun all(): List<MediaProviderConfig> =
        runCatching { json.decodeFromString(serializer, prefs.getString(KEY, "[]").orEmpty()) }.getOrDefault(emptyList()).sortedBy { it.name.lowercase() }

    fun get(id: String): MediaProviderConfig? = all().firstOrNull { it.id == id }

    /** [newKey]: null = no tocar la clave; "" = borrarla. Lanza IllegalArgumentException con el primer problema. */
    fun save(config: MediaProviderConfig, newKey: String?) {
        val clean = config.copy(name = config.name.trim(), baseUrl = config.baseUrl.trim().trimEnd('/'))
        clean.problems().firstOrNull()?.let { throw IllegalArgumentException(it) }
        val next = all().filter { it.id != clean.id } + clean
        check(prefs.edit().putString(KEY, json.encodeToString(serializer, next)).commit()) { "No se pudo guardar el proveedor." }
        when {
            newKey == null -> Unit
            newKey.isBlank() -> secure.clearProviderApiKey(keyId(clean.id))
            else -> secure.saveProviderApiKey(keyId(clean.id), newKey.trim())
        }
        reload()
    }

    fun delete(id: String) {
        check(prefs.edit().putString(KEY, json.encodeToString(serializer, all().filter { it.id != id })).commit()) { "No se pudo eliminar el proveedor." }
        secure.clearProviderApiKey(keyId(id))
        reload()
    }

    fun duplicate(id: String): MediaProviderConfig? {
        val source = get(id) ?: return null
        val copy = source.copy(id = uniqueId(com.vidadomu.nexus.agent.core.llm.BrainProfile.slug(source.name + " copia")), name = source.name + " (copia)")
        save(copy, apiKey(id).takeIf { it.isNotBlank() })
        return copy
    }

    fun uniqueId(base: String): String {
        val taken = all().map { it.id }.toSet()
        if (base !in taken && com.vidadomu.nexus.agent.core.llm.BrainProfile.ID.matches(base)) return base
        var n = 2
        while ("${base.take(36)}-$n" in taken) n++
        return "${base.take(36)}-$n"
    }

    fun apiKey(id: String): String = runCatching { secure.getProviderApiKey(keyId(id)) }.getOrDefault("")
    fun hasKey(id: String): Boolean = runCatching { secure.hasProviderApiKey(keyId(id)) }.getOrDefault(false)
    fun maskedKey(id: String): String = runCatching { secure.getMaskedProviderApiKey(keyId(id)).takeIf { secure.hasProviderApiKey(keyId(id)) }.orEmpty() }.getOrDefault("")

    /** Un proveedor ejecutable por cada configuración válida. */
    fun providers(): List<ConfigurableMediaProvider> = all().filter { it.problems().isEmpty() }.map { c ->
        ConfigurableMediaProvider(c, client, { apiKey(c.id) }, File(app.filesDir, "custom_outputs"))
    }

    /** Comprueba la conexión de una configuración aún sin guardar, con la clave que se está escribiendo. */
    suspend fun test(config: MediaProviderConfig, key: String): Result<Boolean> =
        ConfigurableMediaProvider(config, client, { key }, File(app.filesDir, "custom_outputs")).testConnection(key)

    /** Publica los proveedores en el registro y refresca el catálogo, para que aparezcan de inmediato en el Lienzo y el agente. */
    fun reload() {
        app.providerRegistry.setCustomProviders(providers())
        app.modelCatalogRepository.refreshModels()
    }

    companion object {
        private const val KEY = "configs"
        fun keyId(id: String) = "nexus_media_key_$id"
    }
}
