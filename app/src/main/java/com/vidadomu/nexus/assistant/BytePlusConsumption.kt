package com.vidadomu.nexus.assistant

import android.content.Context
import androidx.work.*
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.llm.BytePlusUsageLedger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** Only the existing private bridge credential enters Android, never billing credentials. */
class BytePlusConsumption private constructor(private val app: NexusApplication) {
    val ledger = BytePlusUsageLedger(File(app.filesDir, "assistant/byteplus-usage.json"))
    private val prefs = app.getSharedPreferences("byteplus_consumption_v1", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val cache = com.vidadomu.nexus.agent.core.llm.BytePlusOfficialCache(File(app.filesDir, "assistant/byteplus-official.json"))
    private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()
    val periodic get() = prefs.getBoolean("periodic", true)
    val interval get() = prefs.getInt("minutes", 30).coerceAtLeast(15)
    fun cached(period: String): JsonObject? = cache.get(period)
    fun lastAttempt(period: String) = prefs.getLong("attempt_$period", 0)
    fun lastError(period: String): String? = prefs.getString("error_$period", null)
    fun stale(period: String): Boolean = lastError(period) != null ||
        (cached(period)?.get("fetched_at")?.jsonPrimitive?.longOrNull ?: 0) < System.currentTimeMillis() - interval * 60_000L

    suspend fun sync(period: String, force: Boolean = false) = withContext(Dispatchers.IO) { mutex.withLock {
        require(period in setOf("day", "month"))
        prefs.edit().putLong("attempt_$period", System.currentTimeMillis()).apply()
        try {
            val token = app.secureStorage.getProviderApiKey("nexus_flow_recovery")
            check(token.isNotBlank()) { "Vincula el puente privado de ASUS en Ajustes → Proveedores → Google Flow. No se necesita una clave de facturación en la APK." }
            val request = Request.Builder().url("http://100.112.57.2:8899/api/byteplus/usage?period=$period&refresh=${if (force) 1 else 0}")
                .header("Authorization", "Bearer $token").get().build()
            val snapshot = client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { when(response.code) {
                    401, 403 -> "El puente privado requiere una vinculación válida."
                    404 -> "El puente ASUS todavía no tiene activado el apartado administrativo BytePlus."
                    else -> "No se pudo consultar el puente ASUS (HTTP ${response.code})."
                } }
                val body = response.body ?: error("El puente devolvió una respuesta vacía.")
                val raw = body.byteStream().use { input ->
                    val bytes = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (bytes.size() <= 512 * 1024) {
                        val count = input.read(buffer, 0, minOf(buffer.size, 512 * 1024 + 1 - bytes.size()))
                        if (count < 0) break
                        bytes.write(buffer, 0, count)
                    }
                    bytes.toByteArray()
                }
                check(raw.size <= 512 * 1024) { "Respuesta administrativa demasiado grande." }
                Json.parseToJsonElement(raw.toString(Charsets.UTF_8)).jsonObject
            }
            check(snapshot["schema"]?.jsonPrimitive?.intOrNull == 1 && snapshot["period"]?.jsonPrimitive?.contentOrNull == period) { "Respuesta administrativa incompatible." }
            val pricing = snapshot["pricing"] as? JsonObject
            val previous = cached(period)?.get("pricing") as? JsonObject
            val merged = if (pricing?.get("status")?.jsonPrimitive?.contentOrNull != "ok" && previous?.get("updated_at") != null)
                JsonObject(snapshot + ("pricing" to JsonObject(previous + mapOf("status" to JsonPrimitive("stale"), "error" to (pricing?.get("error") ?: JsonPrimitive("Tarifa sin actualizar.")))))) else snapshot
            cache.put(period, merged)
            val errors = listOf("stats", "billing", "quota").any { snapshot[it]?.jsonObject?.get("status")?.jsonPrimitive?.contentOrNull != "ok" }
            prefs.edit().apply { if (errors) putString("error_$period", "Sincronización parcial; revisa la disponibilidad de cada fuente.") else remove("error_$period") }.apply()
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            val safe = if (error is IllegalStateException) error.message else "No se puede conectar con el puente privado ASUS. Se conservan los últimos datos."
            prefs.edit().putString("error_$period", safe).apply()
        }
    } }

    fun ratesFor(model: String, configured: Pair<Double, Double>?): Pair<Double, Double>? {
        if (configured != null) return configured
        for (period in listOf("month", "day")) {
            cachedBytePlusRates(model, cached(period)?.get("pricing") as? JsonObject, System.currentTimeMillis())?.let { return it }
        }
        return null
    }

    @Synchronized fun checkAlerts() {
        val day = java.time.LocalDate.now().toString()
        val data = ledger.snapshot()
        val totals = ledger.totals(data.entries.filter { it.date == day })
        val cost = data.limits.alertUsd?.let { threshold -> totals.usd?.let { it >= threshold } } == true
        val tokens = data.limits.alertTokens?.let { threshold -> totals.input?.let { input -> totals.output?.let { input + it >= threshold } } } == true
        if (!cost && !tokens) return
        val key = "alert_${day}_${data.limits.alertUsd}_${data.limits.alertTokens}"
        if (prefs.getBoolean(key, false)) return
        if (!androidx.core.app.NotificationManagerCompat.from(app).areNotificationsEnabled()) return
        val manager = app.getSystemService(android.app.NotificationManager::class.java)
        val channel = "byteplus_usage_alerts"
        manager.createNotificationChannel(android.app.NotificationChannel(channel, "Consumo BytePlus", android.app.NotificationManager.IMPORTANCE_DEFAULT))
        val notice = androidx.core.app.NotificationCompat.Builder(app, channel)
            .setSmallIcon(android.R.drawable.stat_notify_error).setContentTitle("Alerta de consumo BytePlus")
            .setContentText("Nexus ha alcanzado tu umbral diario de ${if (cost) "coste estimado" else "tokens"}. Revisa Ajustes → Consumo BytePlus.")
            .setAutoCancel(true).build()
        manager.notify(73021, notice)
        prefs.edit().putBoolean(key, true).apply()
    }
    fun configurePeriodic(enabled: Boolean, minutes: Int = interval) {
        prefs.edit().putBoolean("periodic", enabled).putInt("minutes", minutes.coerceAtLeast(15)).apply()
        schedule()
    }
    fun schedule() {
        val manager = WorkManager.getInstance(app)
        if (!periodic || com.vidadomu.nexus.BuildConfig.OFFLINE_QA) { manager.cancelUniqueWork(WORK); return }
        val work = PeriodicWorkRequestBuilder<BytePlusSyncWorker>(interval.toLong(), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        manager.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, work)
    }
    companion object {
        private const val WORK = "byteplus-administrative-sync"
        @Volatile private var instance: BytePlusConsumption? = null
        fun get(context: Context): BytePlusConsumption = instance ?: synchronized(this) {
            instance ?: BytePlusConsumption(context.applicationContext as NexusApplication).also { instance = it }
        }
    }
}
class BytePlusSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as NexusApplication).ready.await()
        val store = BytePlusConsumption.get(applicationContext)
        if (!store.periodic) return Result.success()
        store.sync("day"); store.sync("month")
        return if (store.lastError("day") != null || store.lastError("month") != null) Result.retry() else Result.success()
    }
}
