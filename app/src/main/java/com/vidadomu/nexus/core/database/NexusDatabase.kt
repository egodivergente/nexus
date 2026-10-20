package com.vidadomu.nexus.core.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.vidadomu.nexus.core.model.GenerationJob
import com.vidadomu.nexus.core.model.GenerationOutput
import com.vidadomu.nexus.core.model.GenerationStatus
import com.vidadomu.nexus.core.model.Modality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class NexusDatabase(context: Context, loadOnOpen: Boolean = true) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val json = Json { ignoreUnknownKeys = true }
    private val _jobsFlow = MutableStateFlow<List<GenerationJob>>(emptyList())
    val jobsFlow: StateFlow<List<GenerationJob>> = _jobsFlow.asStateFlow()
    private val mutationLock = Mutex()
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        if (loadOnOpen) CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { loadHistory() }
    }

    suspend fun loadHistory() = withContext(Dispatchers.IO) {
        mutationLock.withLock {
            _loading.value = true
            try { reloadJobsSync(); _loadError.value = null }
            catch (e: Exception) {
                _loadError.value = "No se pudo abrir el historial. Tus datos se conservan."
                android.util.Log.e("NexusDatabase", "History load: ${e.javaClass.simpleName}")
            } finally { _loading.value = false }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE generations (
                id TEXT PRIMARY KEY,
                provider_job_id TEXT,
                provider_id TEXT NOT NULL,
                model_id TEXT NOT NULL,
                modality TEXT NOT NULL,
                prompt TEXT NOT NULL,
                negative_prompt TEXT,
                parameters_json TEXT,
                input_media_uri TEXT,
                status TEXT NOT NULL,
                progress REAL,
                started_at INTEGER NOT NULL,
                completed_at INTEGER,
                error TEXT,
                technical_details TEXT,
                actual_cost_usd REAL,
                estimated_cost_usd REAL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE outputs (
                id TEXT PRIMARY KEY,
                job_id TEXT NOT NULL,
                type TEXT NOT NULL,
                url TEXT NOT NULL,
                local_uri TEXT,
                mime_type TEXT,
                width INTEGER,
                height INTEGER,
                duration_seconds REAL,
                FOREIGN KEY (job_id) REFERENCES generations(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            val columns = db.rawQuery("PRAGMA table_info(generations)", null).use { cursor ->
                buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
            }
            for (column in listOf("actual_cost_usd", "estimated_cost_usd")) {
                if (column !in columns) db.execSQL("ALTER TABLE generations ADD COLUMN $column REAL")
            }
        }
        if (oldVersion < 3) {
            // RunwayProvider versions before v3 persisted every task/output as VIDEO.
            // Repair only the routes that the official Runway catalog defines as images.
            val imageRoute = "provider_id = 'runway' AND (model_id LIKE 'runway/%/t2i' OR model_id LIKE 'runway/%/i2i' OR model_id = 'runway/magnific_precision_upscaler_v2')"
            db.execSQL("UPDATE generations SET modality = 'IMAGE' WHERE $imageRoute")
            db.execSQL("UPDATE outputs SET type = 'IMAGE' WHERE job_id IN (SELECT id FROM generations WHERE $imageRoute)")
        }
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Preserve existing rows on downgrade instead of throwing SQLiteException at startup.
    }

    private fun reloadJobsSync() {
        val jobs = queryAllJobsSync()
        _jobsFlow.value = jobs
    }

    suspend fun insertOrUpdateJob(job: GenerationJob) = withContext(Dispatchers.IO) { mutationLock.withLock {
        writeJobSync(job)
    } }

    /** Peer import never changes a running local job or submits work to a provider. */
    suspend fun mergeCompletedJobFromPeer(job: GenerationJob) = withContext(Dispatchers.IO) { mutationLock.withLock {
        check(!_loading.value && _loadError.value == null)
        val local = _jobsFlow.value.firstOrNull { it.id == job.id }
        val next = com.vidadomu.nexus.sync.mergeCompletedPeerJob(local, job)
        if (next != null && next != local) writeJobSync(next)
    } }

    private fun writeJobSync(job: GenerationJob) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cv = ContentValues().apply {
                put("id", job.id)
                put("provider_job_id", job.providerJobId)
                put("provider_id", job.providerId)
                put("model_id", job.modelId)
                put("modality", job.modality.name)
                put("prompt", job.prompt)
                put("negative_prompt", job.negativePrompt)
                put("parameters_json", json.encodeToString(job.parameters))
                put("input_media_uri", job.inputMediaUri)
                put("status", job.status.name)
                put("progress", job.progress)
                put("started_at", job.startedAt)
                put("completed_at", job.completedAt)
                put("error", job.error)
                put("technical_details", job.technicalDetails)
                put("actual_cost_usd", job.actualCostUsd)
                put("estimated_cost_usd", job.estimatedCostUsd)
            }
            db.insertWithOnConflict(
                "generations",
                null,
                cv,
                SQLiteDatabase.CONFLICT_REPLACE
            )

            // Delete old outputs for this job and insert new ones
            db.delete("outputs", "job_id = ?", arrayOf(job.id))
            for (output in job.outputs) {
                val outCv = ContentValues().apply {
                    put("id", output.id)
                    put("job_id", output.jobId)
                    put("type", output.type.name)
                    put("url", output.url)
                    put("local_uri", output.localUri)
                    put("mime_type", output.mimeType)
                    put("width", output.width)
                    put("height", output.height)
                    put("duration_seconds", output.durationSeconds)
                }
                db.insertWithOnConflict("outputs", null, outCv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (!_loading.value && _loadError.value == null) {
            // Do not retain mutable collections owned by a provider/caller after the transaction.
            val publishedJob = job.copy(parameters = job.parameters.toMap(), outputs = job.outputs.toList())
            val current = _jobsFlow.value
            val idx = current.indexOfFirst { it.id == job.id }
            _jobsFlow.value = if (idx >= 0) {
                current.toMutableList().apply { set(idx, publishedJob) }.let { updated ->
                    if (current[idx].startedAt == job.startedAt) updated else updated.sortedByDescending { it.startedAt }
                }
            } else {
                (listOf(publishedJob) + current).sortedByDescending { it.startedAt }
            }
        } else {
            reloadJobsSync()
        }
    }

    suspend fun getJobById(id: String): GenerationJob? = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM generations WHERE id = ?", arrayOf(id))
        val job = cursor.use {
            if (it.moveToFirst()) {
                mapRowToJob(it, db)
            } else {
                null
            }
        }
        job
    }

    suspend fun deleteJob(id: String) = withContext(Dispatchers.IO) { mutationLock.withLock {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("outputs", "job_id = ?", arrayOf(id))
            db.delete("generations", "id = ?", arrayOf(id))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (!_loading.value && _loadError.value == null) {
            _jobsFlow.value = _jobsFlow.value.filterNot { it.id == id }
        } else {
            reloadJobsSync()
        }
    } }

    private fun queryAllJobsSync(): List<GenerationJob> {
        val db = readableDatabase
        val outputs = queryOutputs(db, "SELECT * FROM outputs", null).groupBy { it.jobId }
        val list = mutableListOf<GenerationJob>()
        val cursor = db.rawQuery("SELECT * FROM generations ORDER BY started_at DESC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(mapRowToJob(it, db, outputs[it.getString(it.getColumnIndexOrThrow("id"))].orEmpty()))
            }
        }
        return list
    }

    private fun mapRowToJob(cursor: Cursor, db: SQLiteDatabase, suppliedOutputs: List<GenerationOutput>? = null): GenerationJob {
        val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
        val providerJobId = cursor.getString(cursor.getColumnIndexOrThrow("provider_job_id"))
        val providerId = cursor.getString(cursor.getColumnIndexOrThrow("provider_id"))
        val modelId = cursor.getString(cursor.getColumnIndexOrThrow("model_id"))
        val modalityStr = cursor.getString(cursor.getColumnIndexOrThrow("modality")).orEmpty()
        val modality = runCatching { Modality.valueOf(modalityStr) }.getOrDefault(Modality.IMAGE)
        val prompt = cursor.getString(cursor.getColumnIndexOrThrow("prompt"))
        val negativePrompt = cursor.getString(cursor.getColumnIndexOrThrow("negative_prompt")) ?: ""
        val paramsJson = cursor.getString(cursor.getColumnIndexOrThrow("parameters_json")) ?: "{}"
        val inputMediaUri = cursor.getString(cursor.getColumnIndexOrThrow("input_media_uri"))
        val statusStr = cursor.getString(cursor.getColumnIndexOrThrow("status")).orEmpty()
        val status = runCatching { GenerationStatus.valueOf(statusStr) }.getOrDefault(GenerationStatus.FAILED)
        val progress = if (cursor.isNull(cursor.getColumnIndexOrThrow("progress"))) null else cursor.getFloat(cursor.getColumnIndexOrThrow("progress"))
        val startedAt = cursor.getLong(cursor.getColumnIndexOrThrow("started_at"))
        val completedAt = if (cursor.isNull(cursor.getColumnIndexOrThrow("completed_at"))) null else cursor.getLong(cursor.getColumnIndexOrThrow("completed_at"))
        val error = cursor.getString(cursor.getColumnIndexOrThrow("error"))
        val techDetails = cursor.getString(cursor.getColumnIndexOrThrow("technical_details"))
        val actualCostUsd = try {
            val idx = cursor.getColumnIndex("actual_cost_usd")
            if (idx >= 0 && !cursor.isNull(idx)) cursor.getDouble(idx) else null
        } catch (e: Exception) { null }
        val estimatedCostUsd = try {
            val idx = cursor.getColumnIndex("estimated_cost_usd")
            if (idx >= 0 && !cursor.isNull(idx)) cursor.getDouble(idx) else null
        } catch (e: Exception) { null }

        val params: Map<String, String> = try {
            json.decodeFromString(paramsJson)
        } catch (e: Exception) {
            emptyMap()
        }


        return GenerationJob(
            id = id,
            providerJobId = providerJobId,
            providerId = providerId,
            modelId = modelId,
            modality = modality,
            prompt = prompt,
            negativePrompt = negativePrompt,
            parameters = params,
            inputMediaUri = inputMediaUri,
            status = status,
            progress = progress,
            startedAt = startedAt,
            completedAt = completedAt,
            error = error,
            technicalDetails = techDetails,
            actualCostUsd = actualCostUsd,
            estimatedCostUsd = estimatedCostUsd,
            outputs = suppliedOutputs ?: queryOutputs(db, "SELECT * FROM outputs WHERE job_id = ?", arrayOf(id))
        )
    }

    private fun queryOutputs(db: SQLiteDatabase, sql: String, args: Array<String>?): List<GenerationOutput> {
        val outputs = mutableListOf<GenerationOutput>()
        val outCursor = db.rawQuery(sql, args)
        outCursor.use { oc ->
            while (oc.moveToNext()) {
                val typeStr = oc.getString(oc.getColumnIndexOrThrow("type")).orEmpty()
                outputs.add(
                    GenerationOutput(
                        id = oc.getString(oc.getColumnIndexOrThrow("id")),
                        jobId = oc.getString(oc.getColumnIndexOrThrow("job_id")),
                        type = runCatching { Modality.valueOf(typeStr) }.getOrDefault(Modality.IMAGE),
                        url = oc.getString(oc.getColumnIndexOrThrow("url")),
                        localUri = oc.getString(oc.getColumnIndexOrThrow("local_uri")),
                        mimeType = oc.getString(oc.getColumnIndexOrThrow("mime_type")) ?: "",
                        width = if (oc.isNull(oc.getColumnIndexOrThrow("width"))) null else oc.getInt(oc.getColumnIndexOrThrow("width")),
                        height = if (oc.isNull(oc.getColumnIndexOrThrow("height"))) null else oc.getInt(oc.getColumnIndexOrThrow("height")),
                        durationSeconds = if (oc.isNull(oc.getColumnIndexOrThrow("duration_seconds"))) null else oc.getFloat(oc.getColumnIndexOrThrow("duration_seconds"))
                    )
                )
            }
        }

        return outputs
    }

    suspend fun getTotalSpentUsd(): Double = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT SUM(actual_cost_usd) FROM generations WHERE status = 'COMPLETED' AND actual_cost_usd IS NOT NULL", null)
        cursor.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getDouble(0) else 0.0
        }
    }

    companion object {
        private const val DATABASE_NAME = "nexus.db"
        private const val DATABASE_VERSION = 3
    }
}
