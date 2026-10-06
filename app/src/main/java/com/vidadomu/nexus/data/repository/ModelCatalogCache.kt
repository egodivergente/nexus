package com.vidadomu.nexus.data.repository

import android.content.Context
import android.util.Log
import com.vidadomu.nexus.core.model.NexusModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class ModelCatalogCache(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val cacheFile: File
        get() = File(context.cacheDir, CACHE_FILE_NAME)

    private val prefs = context.getSharedPreferences("nexus_catalog_meta", Context.MODE_PRIVATE)

    @Volatile
    private var memoryCache: List<NexusModel>? = null

    suspend fun getCachedModels(): List<NexusModel>? = withContext(Dispatchers.IO) {
        memoryCache?.let { return@withContext it }

        mutex.withLock {
            memoryCache?.let { return@withContext it }

            val content=runCatching {
                android.util.AtomicFile(cacheFile).openRead().bufferedReader(Charsets.UTF_8).use {it.readText()}
            }.getOrNull()
            recoverCatalog(content) {
                runCatching {
                    context.assets.open("atlas_catalog_snapshot.json").bufferedReader().use {
                        com.vidadomu.nexus.provider.atlas.AtlasModelMapper.parseModelsResponse(it.readText())
                    }
                }.getOrDefault(emptyList())
            }.also { models ->
                memoryCache=models.takeIf {it.isNotEmpty()}
                com.vidadomu.nexus.provider.atlas.AtlasModelCatalog.publishedSnapshot=models.filter {it.providerId=="atlas_cloud"}
            }.takeIf {it.isNotEmpty()}

        }
    }

    suspend fun saveModels(models: List<NexusModel>) = withContext(Dispatchers.IO) {
        if (models.isEmpty()) return@withContext

        mutex.withLock {
            memoryCache = models
            try {
                val serialized = json.encodeToString(models)
                val atomic=android.util.AtomicFile(cacheFile)
                val stream=atomic.startWrite()
                try { stream.write(serialized.toByteArray(Charsets.UTF_8)); atomic.finishWrite(stream) }
                catch(e:Exception) { atomic.failWrite(stream); throw e }
                prefs.edit().putLong(KEY_LAST_UPDATED, System.currentTimeMillis()).apply()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to write models to cache file: ${e.message}")
            }
        }
    }

    fun isCacheValid(): Boolean {
        if (!cacheFile.exists() || cacheFile.length() == 0L) return false
        val lastUpdated = prefs.getLong(KEY_LAST_UPDATED, 0L)
        val now = System.currentTimeMillis()
        return (now - lastUpdated) < CACHE_TTL_MS
    }

    fun getLastUpdatedTimestamp(): Long {
        return prefs.getLong(KEY_LAST_UPDATED, 0L)
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        mutex.withLock {
            memoryCache = null
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
            prefs.edit().remove(KEY_LAST_UPDATED).apply()
        }
    }

    companion object {
        private const val TAG = "ModelCatalogCache"
        private const val CACHE_FILE_NAME = "nexus_atlas_models_v3.json"
        private const val KEY_LAST_UPDATED = "last_updated_ts"
        const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 horas
    }
}

