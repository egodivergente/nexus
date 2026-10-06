package com.vidadomu.nexus

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.core.database.NexusDatabase
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses in-memory databases exclusively. Never opens the user's history. */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    private fun upgrade(partial: Boolean) {
        val helper = NexusDatabase(InstrumentationRegistry.getInstrumentation().targetContext, loadOnOpen = false)
        SQLiteDatabase.create(null).use { db ->
            db.execSQL("CREATE TABLE generations (id TEXT PRIMARY KEY, provider_id TEXT, model_id TEXT, modality TEXT${if (partial) ", actual_cost_usd REAL" else ""})")
            db.execSQL("CREATE TABLE outputs (id TEXT PRIMARY KEY, job_id TEXT, type TEXT)")
            db.execSQL("INSERT INTO generations (id,provider_id,model_id,modality) VALUES ('preserve','runway','runway/example/t2i','VIDEO')")
            db.execSQL("INSERT INTO outputs VALUES ('result','preserve','VIDEO')")
            db.beginTransaction()
            try { helper.onUpgrade(db, 1, 3); db.setTransactionSuccessful() }
            finally { db.endTransaction() }
            db.rawQuery("SELECT id,modality,actual_cost_usd,estimated_cost_usd FROM generations", null).use {
                assertTrue(it.moveToFirst()); assertEquals("preserve", it.getString(0)); assertEquals("IMAGE", it.getString(1))
                assertFalse(it.moveToNext())
            }
            db.rawQuery("SELECT id,type FROM outputs", null).use {
                assertTrue(it.moveToFirst()); assertEquals("result", it.getString(0)); assertEquals("IMAGE", it.getString(1))
            }
            db.rawQuery("PRAGMA integrity_check", null).use { assertTrue(it.moveToFirst()); assertEquals("ok", it.getString(0)) }
        }
        helper.close()
    }
    @Test fun versionOnePreservesHistoryAndOutputs() = upgrade(false)
    @Test fun partiallyAppliedMigrationDoesNotEraseHistory() = upgrade(true)
    @Test fun malformedSchemaFailsWithoutDroppingExistingData() {
        val helper = NexusDatabase(InstrumentationRegistry.getInstrumentation().targetContext, loadOnOpen = false)
        SQLiteDatabase.create(null).use { db ->
            db.execSQL("CREATE TABLE outputs (id TEXT)")
            db.execSQL("INSERT INTO outputs VALUES ('preserve')")
            assertTrue(runCatching { helper.onUpgrade(db, 1, 3) }.isFailure)
            db.rawQuery("SELECT id FROM outputs", null).use { assertTrue(it.moveToFirst()); assertEquals("preserve", it.getString(0)) }
        }
        helper.close()
    }
}
