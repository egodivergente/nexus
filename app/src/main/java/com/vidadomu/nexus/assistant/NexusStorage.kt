package com.vidadomu.nexus.assistant

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.assets.AssetIndex
import com.vidadomu.nexus.agent.core.assets.IndexData
import com.vidadomu.nexus.agent.core.storage.CleanupResult
import com.vidadomu.nexus.agent.core.storage.StorageAnalysis
import com.vidadomu.nexus.agent.core.storage.StorageCleanup
import com.vidadomu.nexus.agent.core.storage.StorageScan
import com.vidadomu.nexus.sync.NexusDriveSync
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Android side of «Almacenamiento» (Ajustes → Sistema): sizes, duplicate search and «Liberar espacio». */
internal object NexusStorage {
    private fun dataDir(app: Context) = app.filesDir.parentFile!!

    /** Private attachment or export: Drive sync uploads it from where it is, without a copy. */
    fun ownedOriginal(app: Context, file: File): Boolean = runCatching {
        val path = file.canonicalPath
        listOf("agent_media", "agent_exports").any { path.startsWith(File(app.filesDir, it).canonicalPath + File.separator) }
    }.getOrDefault(false)

    /** A stored copy an import may reuse: an attachment, an export or a Drive copy. */
    fun reusableCopy(app: Context, file: File): Boolean = ownedOriginal(app, file) || runCatching {
        Regex(Regex.escape(File(app.filesDir, "assistant/sync/workspaces").canonicalPath + File.separator) + "[^/]+/blobs/[^/]+").matches(file.canonicalPath)
    }.getOrDefault(false)

    private fun scan(app: Context) = dataDir(app).let { dir ->
        StorageScan(dir, app.packageName, setOfNotNull(StorageScan.activeConversation(dir)),
            linkCount = { file -> android.system.Os.stat(file.path).st_nlink.toInt() }, databaseText = ::databaseText)
    }

    private fun runtime(app: Context) = AssistantRuntime.get(app.applicationContext as NexusApplication)
    private fun indexText(app: Context) = AssetIndex.JSON.encodeToString(IndexData.serializer(), runtime(app).assets.snapshot())

    suspend fun usage(app: Context) = withContext(Dispatchers.IO) { StorageCleanup(scan(app), runtime(app).hashes).usage() }

    suspend fun analyse(app: Context, progress: (Int, Int) -> Unit): StorageAnalysis = withContext(Dispatchers.IO) {
        val job = coroutineContext[kotlinx.coroutines.Job]
        StorageCleanup(scan(app), runtime(app).hashes).analyse(indexText(app), cancelled = { job?.isActive == false }, progress = progress)
    }

    /**
     * Waits for any Drive copy in progress and for the chat to finish saving, then frees [analysis]. The scan is made
     * again inside both locks, so a chat opened since the analysis is treated as open (never rewritten).
     */
    suspend fun free(app: Context, analysis: StorageAnalysis): CleanupResult = withContext(Dispatchers.IO) {
        NexusDriveSync.gate.withLock {
            AssistantChatStore.exclusive {
                val runtime = runtime(app)
                StorageCleanup(scan(app), runtime.hashes).free(analysis.plan,
                    rewriteIndex = { transform -> runtime.assets.rewriteReferences(transform) },
                    indexText = { indexText(app) },
                    reportDir = File(app.filesDir, "storage"),
                    rewriteOther = { transform -> rewriteRecent(app, transform) })
            }
        }
    }

    /** The media picker's last-use time, keyed by URI: moved to the copy that stays so the order is kept. */
    private fun rewriteRecent(app: Context, transform: (String) -> String): Int {
        val prefs = app.getSharedPreferences(StorageScan.RECENT_MEDIA, Context.MODE_PRIVATE)
        val values = prefs.all.mapNotNull { (key, value) -> (value as? Long)?.let { key to it } }.toMap()
        val next = HashMap<String, Long>(); val removed = HashSet<String>()
        values.forEach { (key, time) ->
            val target = transform(key)
            if (target != key) { removed += key; next[target] = maxOf(time, next[target] ?: values[target] ?: 0L) }
        }
        if (removed.isEmpty()) return 0
        val editor = prefs.edit()
        removed.forEach { editor.remove(it) }; next.forEach { (key, time) -> editor.putLong(key, time) }
        check(editor.commit()) { "recent_media_v1" }
        return removed.size
    }

    /**
     * Text and blob values of a database, plus its raw bytes: a name split across SQLite overflow pages is still
     * found by the query, and pending WAL pages by the raw read.
     */
    private fun databaseText(file: File): Sequence<String> = sequence {
        if (file.name.endsWith(".db") || file.name.endsWith(".workdb")) {
            val values = ArrayList<String>()
            try {
                SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    val tables = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
                    for (table in tables) try {
                        db.rawQuery("SELECT * FROM \"" + table.replace("\"", "\"\"") + "\"", null).use { c ->
                            while (c.moveToNext()) for (column in 0 until c.columnCount) when (c.getType(column)) {
                                Cursor.FIELD_TYPE_STRING -> values += c.getString(column)
                                Cursor.FIELD_TYPE_BLOB -> values += String(c.getBlob(column), Charsets.ISO_8859_1)
                            }
                        }
                    } catch (_: Exception) { /* a row too large for a cursor window: the raw read below still covers it */ }
                }
            } catch (_: Exception) { }
            yieldAll(values)
        }
        if (file.length() <= 64L * 1024 * 1024) yield(String(file.readBytes(), Charsets.ISO_8859_1))
    }
}
