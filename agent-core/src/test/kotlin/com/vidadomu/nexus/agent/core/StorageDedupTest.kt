package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.assets.Asset
import com.vidadomu.nexus.agent.core.assets.AssetIndex
import com.vidadomu.nexus.agent.core.assets.AssetType
import com.vidadomu.nexus.agent.core.assets.IndexData
import com.vidadomu.nexus.agent.core.storage.*
import com.vidadomu.nexus.agent.core.sync.*
import java.io.File
import java.net.URI
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class StorageDedupTest {
    private val pkg = "com.vidadomu.nexus.agent"
    private val refs = MediaReferences(pkg)
    private fun root() = Files.createTempDirectory("nexus-storage-test").toFile()
    private val sha = "a".repeat(64)
    private val blob = "files/assistant/sync/workspaces/F/blobs/$sha.mp4"

    @Test fun findsEveryStoredFormOfAPrivatePath() {
        val text = """{"uri":"file:///data/user/0/$pkg/files/agent_media/agent_1.jpg","b":"\/data\/data\/$pkg\/files\/agent_exports\/edit_2.mp4"}
            Ver (/data/user/0/$pkg/files/agent_media/Plano%2001.mp4) y /data/user/0/$pkg/cache/x.png."""
        assertEquals(setOf("files/agent_media/agent_1.jpg", "files/agent_exports/edit_2.mp4", "files/agent_media/Plano 01.mp4", "cache/x.png"), refs.find(text))
    }

    @Test fun rewriteKeepsPrefixEscapingAndUnrelatedText() {
        val text = """{"a":"file:///data/user/0/$pkg/files/agent_media/agent_1.mp4","b":"\/data\/data\/$pkg\/files\/agent_media\/agent_1.mp4","c":"file:///data/user/0/$pkg/files/agent_media/agent_10.mp4"}"""
        val (next, count) = refs.rewrite(text, mapOf("files/agent_media/agent_1.mp4" to blob))
        assertEquals(2, count)
        assertEquals("""{"a":"file:///data/user/0/$pkg/$blob","b":"\/data\/data\/$pkg\/${blob.replace("/", "\\/")}","c":"file:///data/user/0/$pkg/files/agent_media/agent_10.mp4"}""", next)
        assertEquals(setOf(blob, "files/agent_media/agent_10.mp4"), refs.find(next))
    }

    @Test fun pathTraversalIsNeverAReference() {
        assertTrue(refs.find("/data/user/0/$pkg/files/../shared_prefs/x.xml").isEmpty())
    }

    @Test fun namesIndexCatchesBareNamesProviderUrisAndEncodedNames() {
        val names = MediaReferences.names("""content://$pkg.fileprovider/media/agent_9.mp4 "edit_3.mp4" Plano%2001.mp4""")
        assertTrue("agent_9.mp4" in names); assertTrue("edit_3.mp4" in names); assertTrue("Plano 01.mp4" in names)
    }

    private fun f(path: String, area: StorageArea, sha256: String = sha, length: Long = 10) = StoredFile(path, length, sha256, area)

    @Test fun keepsTheDriveCopyAndRemovesOnlyFreeAttachmentAndExportCopies() {
        val drive = f(blob, StorageArea.DRIVE_COPY)
        val attachment = f("files/agent_media/agent_1.mp4", StorageArea.ATTACHMENTS)
        val export = f("files/agent_exports/edit_1.mp4", StorageArea.EXPORTS)
        val usedByPrefs = f("files/agent_media/agent_2.mp4", StorageArea.ATTACHMENTS)
        val draft = f("files/draft_media/d.mp4", StorageArea.DRAFTS)
        val renamed = f("files/agent_exports/Plano 01.mp4", StorageArea.EXPORTS)
        val plan = DuplicatePlanner.plan(listOf(attachment, drive, export, usedByPrefs, draft, renamed), fixedNames = setOf("agent_2.mp4"))
        assertEquals(setOf(attachment, export), plan.removals.map { it.file }.toSet())
        assertTrue(plan.removals.all { it.keep == drive })
        assertEquals(20, plan.bytes)
        assertEquals(3, plan.protectedCopies)
    }

    @Test fun withoutADriveCopyOneOriginalAlwaysStaysAndDifferentExtensionsNeverMix() {
        val a = f("files/agent_media/agent_a.jpg", StorageArea.ATTACHMENTS)
        val b = f("files/agent_media/agent_b.jpg", StorageArea.ATTACHMENTS)
        val png = f("files/agent_media/agent_c.png", StorageArea.ATTACHMENTS)
        val other = f("files/agent_media/agent_d.jpg", StorageArea.ATTACHMENTS, sha256 = "b".repeat(64))
        val plan = DuplicatePlanner.plan(listOf(a, b, png, other), emptySet(), referenced = setOf(b.path))
        assertEquals(listOf(Removal(a, b)), plan.removals)
    }

    @Test fun allCopiesProtectedMeansNothingIsRemoved() {
        val a = f("files/agent_media/agent_a.jpg", StorageArea.ATTACHMENTS)
        val b = f("files/agent_media/agent_b.jpg", StorageArea.ATTACHMENTS)
        val plan = DuplicatePlanner.plan(listOf(a, b), setOf("agent_a.jpg", "agent_b.jpg"))
        assertTrue(plan.removals.isEmpty()); assertEquals(1, plan.protectedCopies)
    }

    @Test fun hashCacheTrustsOnlyUnchangedFiles() {
        val dir = root(); val store = File(dir, "hashes.json"); val media = File(dir, "a.bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val hashes = ContentHashes(store)
        val first = hashes.sha256(media); hashes.flush()
        val reloaded = ContentHashes(store)
        assertEquals(first, reloaded.cached(media))
        assertEquals(media, reloaded.find(first, 3))
        media.writeBytes(byteArrayOf(9, 9, 9, 9)); media.setLastModified(media.lastModified() + 5000)
        assertNull(reloaded.cached(media))
        assertNull(reloaded.find(first, 3))
        assertNotEquals(first, reloaded.sha256(media))
    }

    @Test fun indexRewriteChangesEveryStoredReferenceAtomically() = runTest {
        val dir = root(); val old = "file:///data/user/0/$pkg/files/agent_media/agent_1.mp4"
        val file = File(dir, "index.json").apply { writeText(buildJsonObject {
            put("assets", buildJsonArray { add(buildJsonObject { put("id", "a"); put("uri", old); put("type", "VIDEO"); put("displayName", "Plano"); put("createdAt", 1) }) })
            put("generations", buildJsonArray { add(buildJsonObject { put("id", "g"); put("providerId", "atlas"); put("modelId", "m"); put("modality", "video"); put("prompt", "p"); put("status", "COMPLETED"); put("referenceUris", buildJsonArray { add(old) }); put("createdAt", 1) }) })
        }.toString()) }
        val index = AssetIndex(file)
        assertNull(index.recovery)
        val changed = index.rewriteReferences { refs.rewrite(it, mapOf("files/agent_media/agent_1.mp4" to blob)).first }
        assertEquals(2, changed)
        val expected = "file:///data/user/0/$pkg/$blob"
        assertEquals(expected, index.snapshot().assets.single().uri)
        assertEquals(listOf(expected), index.snapshot().generations.single().referenceUris)
        assertEquals(expected, AssetIndex(file).snapshot().assets.single().uri)
    }

    @Test fun damagedIndexFallsBackToTheBackupAndKeepsTheDamagedFile() = runTest {
        val dir = root(); val file = File(dir, "index.json")
        val index = AssetIndex(file)
        index.createProject("Primero"); index.createProject("Segundo")
        assertTrue(AssetIndex.backupOf(file).isFile)
        file.writeText("{\"projects\": [ trunc")
        val recovered = AssetIndex(file)
        assertEquals(listOf("Primero"), recovered.projects().map { it.name })
        val recovery = assertNotNull(recovered.recovery)
        assertEquals("index.json.bak", recovery.source)
        val aside = File(dir, assertNotNull(recovery.quarantined))
        assertEquals("{\"projects\": [ trunc", aside.readText())
        recovered.createProject("Tercero")
        assertEquals(listOf("Primero", "Tercero"), AssetIndex(file).projects().map { it.name })
        assertEquals("{\"projects\": [ trunc", aside.readText())
    }

    @Test fun interruptedSaveUsesTheCompleteNewerTemporaryFile() = runTest {
        val dir = root(); val file = File(dir, "index.json")
        AssetIndex(file).createProject("Antes")
        val newer = File(dir, "index.json.tmp"); file.copyTo(newer)
        newer.writeText(newer.readText().replace("Antes", "Después"))
        file.renameTo(AssetIndex.backupOf(file))
        val loaded = AssetIndex(file)
        assertEquals(listOf("Después"), loaded.projects().map { it.name })
        assertEquals("index.json.tmp", loaded.recovery?.source)
    }

    @Test fun healthyIndexReportsNoRecovery() = runTest {
        val dir = root(); val file = File(dir, "index.json")
        AssetIndex(file).createProject("Uno")
        assertNull(AssetIndex(file).recovery)
        assertNull(AssetIndex(File(dir, "missing.json")).recovery)
    }

    private fun workspace(index: AssetIndex, dir: File, cloud: SyncRemote, hashes: ContentHashes?, adoptable: (File) -> Boolean) =
        SyncWorkspace(index, dir, cloud, open = { uri ->
            if (uri.startsWith("file:")) { val source = File(URI(uri)); SyncInput(source.extension, source.inputStream(), source) } else null
        }, hashes = hashes, adoptable = adoptable)

    @Test fun drivesSyncUploadsPrivateOriginalsWithoutCopyingThem() = runTest {
        val cloud = SyncJournalTest.Cloud(); val a = root(); val b = root()
        val media = File(a, "agent_media").apply { mkdirs() }
        val original = File(media, "agent_1.png").apply { writeBytes(byteArrayOf(137.toByte(), 80, 78, 71, 1, 2, 3)) }
        val sourceFile = File(a, "index.json").apply { writeText(buildJsonObject {
            put("projects", buildJsonArray { add(buildJsonObject { put("id", "p"); put("name", "Mateo"); put("createdAt", 1) }) })
            put("assets", buildJsonArray { add(buildJsonObject { put("id", "a"); put("projectId", "p"); put("uri", original.toURI().toString()); put("type", "IMAGE"); put("displayName", "Plano"); put("createdAt", 2) }) })
        }.toString()) }
        val hashes = ContentHashes(File(a, "hashes.json"))
        val adoptable: (File) -> Boolean = { it.canonicalPath.startsWith(media.canonicalPath + File.separator) }
        val source = AssetIndex(sourceFile)
        repeat(2) { workspace(source, File(a, "sync"), cloud, hashes, adoptable).cycle(setOf("p")) }
        val blobs = File(a, "sync/blobs").listFiles().orEmpty().filter { SyncBlobs.NAME.matches(it.name) }
        assertTrue(blobs.isEmpty(), "No local copy of the original: $blobs")
        assertEquals(original.toURI().toString(), source.snapshot().assets.single().uri)
        assertEquals(1, cloud.blobs.size)
        assertTrue(File(a, "hashes.json").isFile)
        val target = AssetIndex(File(b, "index.json"))
        workspace(target, File(b, "sync"), cloud, null) { false }.cycle(setOf("p"))
        assertContentEquals(original.readBytes(), File(URI(target.snapshot().assets.single().uri)).readBytes())
    }

    @Test fun adoptedOriginalRemovedAsDuplicateResolvesToItsTwin() = runTest {
        val cloud = SyncJournalTest.Cloud(); val a = root()
        val media = File(a, "agent_media").apply { mkdirs() }
        val bytes = byteArrayOf(4, 5, 6, 7)
        val first = File(media, "agent_1.png").apply { writeBytes(bytes) }
        val twin = File(media, "agent_2.png").apply { writeBytes(bytes) }
        val file = File(a, "index.json").apply { writeText(buildJsonObject {
            put("assets", buildJsonArray { add(buildJsonObject { put("id", "a"); put("uri", first.toURI().toString()); put("type", "IMAGE"); put("displayName", "Plano"); put("createdAt", 2) }) })
        }.toString()) }
        val hashes = ContentHashes(File(a, "hashes.json")); hashes.sha256(twin)
        val adoptable: (File) -> Boolean = { it.canonicalPath.startsWith(media.canonicalPath + File.separator) }
        val index = AssetIndex(file)
        workspace(index, File(a, "sync"), cloud, hashes, adoptable).cycle(null)
        val reference = SyncBlobs.PREFIX + hashes.sha256(first) + ".png"
        index.rewriteReferences { it.replace(first.toURI().toString(), twin.toURI().toString()) }
        first.delete()
        val blobs = SyncBlobs(File(a, "sync/blobs"), cloud, hashes, adoptable)
        assertEquals(twin.canonicalFile, blobs.existing(reference)?.canonicalFile)
        assertEquals(twin.canonicalFile, blobs.obtain(reference).canonicalFile)
        assertTrue(File(a, "sync/blobs").listFiles().orEmpty().none { SyncBlobs.NAME.matches(it.name) })
    }

    @Test fun withoutAdoptionSyncStillKeepsAVerifiedCopy() = runTest {
        val cloud = SyncJournalTest.Cloud(); val a = root()
        val original = File(a, "outside.png").apply { writeBytes(byteArrayOf(1, 1, 2, 3)) }
        val file = File(a, "index.json").apply { writeText(buildJsonObject {
            put("assets", buildJsonArray { add(buildJsonObject { put("id", "a"); put("uri", original.toURI().toString()); put("type", "IMAGE"); put("displayName", "Plano"); put("createdAt", 2) }) })
        }.toString()) }
        val index = AssetIndex(file)
        workspace(index, File(a, "sync"), cloud, ContentHashes(null)) { false }.cycle(null)
        assertEquals(1, File(a, "sync/blobs").listFiles().orEmpty().count { SyncBlobs.NAME.matches(it.name) })
    }

    private class Fixture(val dir: File, val pkg: String) {
        fun write(path: String, text: String) = File(dir, path).apply { parentFile.mkdirs(); writeText(text) }
        val bytes = "same video bytes"
        val sha = ContentHashes.hash(bytes.byteInputStream())
        val drive = write("files/assistant/sync/workspaces/F/blobs/$sha.mp4", bytes)
        val export = write("files/agent_exports/edit_1.mp4", bytes)
        val pinned = write("files/agent_media/agent_1.mp4", bytes)
        val other = write("files/agent_media/agent_2.mp4", bytes)
        val chat = write("files/assistant/conversations/old.json", """{"uri":"file:///data/user/0/$pkg/files/agent_exports/edit_1.mp4"}""").apply { setLastModified(1_000_000_000_000) }
        val open = write("files/assistant/conversations/open.json", """{"uri":"file:///data/user/0/$pkg/files/agent_exports/edit_1.mp4"}""")
        init {
            write("shared_prefs/nexus_model_drafts_v1.xml", "<map><string name=\"d\">file:///data/user/0/$pkg/files/agent_media/agent_1.mp4</string></map>")
            write("files/assistant/chat-v1.json", """{"conversationId":"open"}""")
        }
        val index = AssetIndex(File(dir, "files/assistant/index.json"))
        val scan = StorageScan(dir, pkg, setOfNotNull(StorageScan.activeConversation(dir))) { sequenceOf(it.readText(Charsets.ISO_8859_1)) }
        val hashes = ContentHashes(File(dir, "files/storage/hashes.json"))
        val cleanup = StorageCleanup(scan, hashes)
        fun encoded() = AssetIndex.JSON.encodeToString(IndexData.serializer(), index.snapshot())
        suspend fun free(plan: DuplicatePlan) = cleanup.free(plan, { t -> index.rewriteReferences(t) }, ::encoded, File(dir, "files/storage"))
    }

    @Test fun freeingRewritesReferencesKeepsChatOrderAndPinnedCopiesThenDeletes() = runTest {
        val x = Fixture(root(), pkg)
        x.index.addAsset(Asset("a", "file:///data/user/0/$pkg/files/agent_media/agent_2.mp4", AssetType.VIDEO, displayName = "Plano.mp4", createdAt = 1))
        val analysis = x.cleanup.analyse(x.encoded())
        // The export is mentioned by the open chat and agent_1 by a draft: only agent_2 can go.
        assertEquals(listOf("files/agent_media/agent_2.mp4"), analysis.plan.removals.map { it.file.path })
        assertEquals(StorageArea.DRIVE_COPY, analysis.plan.removals.single().keep.area)
        val result = x.free(analysis.plan)
        assertEquals(1, result.removed); assertEquals(x.bytes.length.toLong(), result.bytes)
        assertFalse(x.other.exists()); assertTrue(x.export.exists()); assertTrue(x.pinned.exists()); assertTrue(x.drive.exists())
        assertEquals("file:///data/user/0/$pkg/files/assistant/sync/workspaces/F/blobs/${x.sha}.mp4", x.index.asset("a")!!.uri)
        assertEquals("Plano.mp4", x.index.asset("a")!!.displayName)
        assertTrue(result.report!!.readText().contains("\"removed\""))
    }

    @Test fun closedChatsAreRewrittenWithoutChangingTheirDate() = runTest {
        val x = Fixture(root(), pkg)
        x.write("files/assistant/chat-v1.json", """{"conversationId":"another"}""")
        x.open.delete()
        val scan = StorageScan(x.dir, pkg, setOf("another")) { sequenceOf(it.readText(Charsets.ISO_8859_1)) }
        val cleanup = StorageCleanup(scan, x.hashes)
        val plan = cleanup.analyse(x.encoded()).plan
        assertEquals(setOf("files/agent_exports/edit_1.mp4", "files/agent_media/agent_2.mp4"), plan.removals.map { it.file.path }.toSet())
        val result = cleanup.free(plan, { t -> x.index.rewriteReferences(t) }, x::encoded, File(x.dir, "files/storage"))
        assertEquals(2, result.removed)
        assertFalse(x.export.exists())
        assertEquals("""{"uri":"file:///data/user/0/$pkg/files/assistant/sync/workspaces/F/blobs/${x.sha}.mp4"}""", x.chat.readText())
        assertEquals(1_000_000_000_000, x.chat.lastModified())
    }

    @Test fun aFileChangedAfterTheAnalysisIsKeptAndNotRewritten() = runTest {
        val x = Fixture(root(), pkg)
        x.index.addAsset(Asset("a", "file:///data/user/0/$pkg/files/agent_media/agent_2.mp4", AssetType.VIDEO, displayName = "Plano.mp4", createdAt = 1))
        val plan = x.cleanup.analyse(x.encoded()).plan
        x.other.writeText("edited after the analysis"); x.other.setLastModified(x.other.lastModified() + 5000)
        val result = x.free(plan)
        assertEquals(0, result.removed); assertEquals(1, result.kept); assertTrue(x.other.exists())
        assertEquals("file:///data/user/0/$pkg/files/agent_media/agent_2.mp4", x.index.asset("a")!!.uri)
    }

    @Test fun aNameThatAppearsInAFixedStoreDuringTheCleanupIsKept() = runTest {
        val x = Fixture(root(), pkg)
        x.index.addAsset(Asset("a", "file:///data/user/0/$pkg/files/agent_media/agent_2.mp4", AssetType.VIDEO, displayName = "Plano.mp4", createdAt = 1))
        val plan = x.cleanup.analyse(x.encoded()).plan
        // A background job records the file between the analysis and the deletion.
        val result = x.cleanup.free(plan, { t -> x.write("no_backup/androidx.work.workdb", "agent_2.mp4"); x.index.rewriteReferences(t) }, x::encoded, File(x.dir, "files/storage"))
        assertEquals(0, result.removed); assertTrue(x.other.exists())
        assertTrue(result.report!!.readText().contains("mentioned_in_fixed_store"))
    }

    @Test fun hardLinkedNamesAreNeverRemovedAndCountedOnce() = runTest {
        val x = Fixture(root(), pkg)
        x.index.addAsset(Asset("a", "file:///data/user/0/$pkg/files/agent_media/agent_2.mp4", AssetType.VIDEO, displayName = "Plano.mp4", createdAt = 1))
        val scan = StorageScan(x.dir, pkg, setOf("open"), linkCount = { if (it.name == "agent_2.mp4") 2 else 1 }) { sequenceOf(it.readText(Charsets.ISO_8859_1)) }
        val cleanup = StorageCleanup(scan, x.hashes)
        val analysis = cleanup.analyse(x.encoded())
        assertTrue(analysis.plan.removals.isEmpty())
        assertEquals(x.bytes.length.toLong() * 2 + x.bytes.length / 2, analysis.usage.single { it.area == StorageArea.ATTACHMENTS }.bytes + analysis.usage.single { it.area == StorageArea.EXPORTS }.bytes)
    }
}
