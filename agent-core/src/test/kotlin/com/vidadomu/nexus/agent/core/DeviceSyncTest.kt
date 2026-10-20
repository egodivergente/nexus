package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.sync.*
import com.vidadomu.nexus.agent.core.assets.*
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*

class DeviceSyncTest {
    @Test fun preferenceTextNeverBecomesAnUnquotedJsonLiteral() {
        for (text in listOf("AGENT", "direct", "custom:brain", "model-name-v3", "http://127.0.0.1:8789", "true", "123", "", "a\\b")) {
            val encoded = buildJsonObject { put("value", syncSettingValue(text)) }.toString()
            assertEquals(JsonPrimitive(text), Json.parseToJsonElement(encoded).jsonObject.getValue("value"))
            assertTrue(encoded.contains("\"value\":\""))
        }
    }
    @Test fun preferenceContainersRemainMergeable() {
        assertIs<JsonArray>(syncSettingValue("[{\"id\":\"brain\"}]"))
        assertIs<JsonObject>(syncSettingValue("{\"maxToolRounds\":17}"))
        assertEquals(JsonPrimitive("{broken"), syncSettingValue("{broken"))
    }
    @Test fun nestedBareValuesAndNonJsonNumbersStayQuotedAsOriginalText() {
        for (text in listOf("{\"mode\":AGENT}", "[AGENT]", "[+1]", "[NaN]", "[Infinity]", "[01]", "{\"nested\":[{\"model\":custom}]}")) {
            assertEquals(JsonPrimitive(text), syncSettingValue(text), text)
        }
        assertIs<JsonArray>(syncSettingValue("[null,true,false,0,-1,1.2,1e-9]"))
    }
    private val json = Json
    private fun value(s: String) = json.parseToJsonElement(s)
    private fun encrypt(bytes: ByteArray, key: String, context: String = "response"): ByteArray = ByteArrayOutputStream().let { out ->
        SyncCrypto.encrypt(out, key, context).use { it.write(bytes) }; out.toByteArray()
    }
    @Test fun largeMediaStreamsAcrossAuthenticatedFrames() {
        val key = SyncCrypto.newKey()
        val data = ByteArray(2_000_013) { (it % 251).toByte() }
        val sealed = encrypt(data, key)
        val restored = SyncCrypto.decrypt(sealed.inputStream(), key, "response").use { it.readBytes() }
        assertContentEquals(data, restored)
        assertTrue(sealed.size > data.size)
    }
    @Test fun tamperWrongKeyTruncationAndWrongRequestAreRejected() {
        val key = SyncCrypto.newKey(); val data = encrypt(ByteArray(150_000) { 42 }, key)
        for ((bytes, secret, context) in listOf(
            Triple(data.copyOf(data.size - 1), key, "response"),
            Triple(data.copyOf().also { it[100] = (it[100].toInt() xor 1).toByte() }, key, "response"),
            Triple(data, SyncCrypto.newKey(), "response"), Triple(data, key, "other-request"))) {
            assertFails { SyncCrypto.decrypt(bytes.inputStream(), secret, context).use { it.readBytes() } }
        }
    }
    @Test fun framesFromDifferentResponsesCannotBeSpliced() {
        val key = SyncCrypto.newKey()
        val a = encrypt(ByteArray(70_000) { 1 }, key)
        val b = encrypt(ByteArray(70_000) { 2 }, key)
        // Same ordinal and same length, but a different authenticated stream identity.
        b.copyInto(a, 16, 16, 16 + 4 + 12 + 65552)
        assertFails { SyncCrypto.decrypt(a.inputStream(), key, "response").readBytes() }
    }
    @Test fun authorizationIsBoundToPathAndNeverContainsTheKey() {
        val key = SyncCrypto.newKey(); val token = SyncCrypto.request("/manifest", key)
        assertTrue(SyncCrypto.authorized(token, "/manifest", key))
        assertFalse(SyncCrypto.authorized(token, "/blob/abc", key))
        assertFalse(SyncCrypto.authorized(token, "/manifest", SyncCrypto.newKey()))
        assertFalse(token.contains(key)); assertFalse(SyncCrypto.validKey("short"))
    }
    @Test fun traversalAndAbsoluteNamesCannotEscapeSkillRoot() {
        val root = File(System.getProperty("java.io.tmpdir"), "nexus-sync-test")
        listOf("../secret", "/absolute", "nested/../../secret", "..\\secret").forEach { path -> assertFails { SyncCrypto.safeFile(root, path) } }
        assertEquals(File(root, "nested/SKILL.md").canonicalFile, SyncCrypto.safeFile(root, "nested/SKILL.md"))
    }
    @Test fun independentProjectEditsCombine() {
        val base = value("""{"projects":[{"id":"p","name":"Film","memory":{"tone":"warm","rules":[]}}]}""")
        val local = value("""{"projects":[{"id":"p","name":"New film","memory":{"tone":"warm","rules":["red coat"]}}]}""")
        val remote = value("""{"projects":[{"id":"p","name":"Film","memory":{"tone":"cold","rules":[]}}]}""")
        val result = SyncMerge.merge(local, remote, base)
        val project = result.value.jsonObject.getValue("projects").jsonArray.single().jsonObject
        assertEquals("New film", project.getValue("name").jsonPrimitive.content)
        assertEquals("cold", project.getValue("memory").jsonObject.getValue("tone").jsonPrimitive.content)
        assertTrue(result.conflicts.isEmpty())
    }
    @Test fun concurrentScalarEditsAreReportedWithoutOverwritingLocal() {
        val result = SyncMerge.merge(value("""{"synopsis":"left"}"""), value("""{"synopsis":"right"}"""), value("""{"synopsis":"original"}"""))
        assertEquals(value("""{"synopsis":"left"}"""), result.value)
        assertEquals(listOf("/synopsis"), result.conflicts)
    }
    @Test fun deletionsRequireOptInAndDoNotRemoveConcurrentChanges() {
        val old = value("""{"projects":[{"id":"p","name":"Old"}]}""")
        val remote = value("""{"projects":[]}""")
        assertEquals(old, SyncMerge.merge(old, remote, old).value)
        assertEquals(remote, SyncMerge.merge(old, remote, old, true).value)
        val edited = value("""{"projects":[{"id":"p","name":"Edited"}]}""")
        assertEquals(edited, SyncMerge.merge(edited, remote, old, true).value)
    }
    @Test fun repeatedDeliveryIsIdempotent() {
        val local = value("""{"rules":["a"],"projects":[{"id":"one"}]}""")
        val remote = value("""{"rules":["b"],"projects":[{"id":"two"}]}""")
        val once = SyncMerge.merge(local, remote).value
        assertEquals(once, SyncMerge.merge(once, remote, remote).value)
    }
    @Test fun indexMergePersistsNotifiesAndKeepsLocalActiveSession() = runTest {
        val file = File.createTempFile("nexus-index-sync", ".json").apply { delete() }
        try {
            val index = AssetIndex(file)
            val local = index.createProject("Local")
            index.startProjectSession(local.id)
            val sessions = index.snapshot().projectSessions
            val remote = IndexData(projects = listOf(Project("remote", "Remote", createdAt = 1)))
            index.mergeFromPeer(remote, null, false)
            assertEquals(setOf(local.id, "remote"), index.changes.value.projects.map { it.id }.toSet())
            assertEquals(sessions, index.snapshot().projectSessions)
            assertEquals(index.snapshot(), AssetIndex(file).snapshot())
        } finally { file.delete() }
    }
    @Test fun disabledMemoryScopeCannotDeleteProjects() = runTest {
        val index = AssetIndex(null)
        val local = index.createProject("Keep")
        index.mergeFromPeer(IndexData(), index.snapshot(), true, memory = false)
        assertNotNull(index.findProject(local.id))
    }
    @Test fun subsequentPeerEditAppliesAfterAnInitialConflictWhenLocalValueHasNotChanged() {
        val own = value("""{"tone":"local","name":"own"}""")
        val peer = value("""{"tone":"peer","name":"peer name"}""")
        val initial = SyncMerge.merge(own,peer).value
        val changed = value("""{"tone":"new peer tone","name":"peer name"}""")
        val result = SyncMerge.merge(initial,changed,peer,localBase=initial)
        assertEquals("new peer tone",result.value.jsonObject.getValue("tone").jsonPrimitive.content)
        assertEquals("own",result.value.jsonObject.getValue("name").jsonPrimitive.content)
        assertTrue(result.conflicts.isEmpty())
        val localEdit = value("""{"tone":"new local tone","name":"own"}""")
        assertTrue(SyncMerge.merge(localEdit,changed,peer,localBase=initial).conflicts.contains("/tone"))
    }
    @Test fun canonicalReplicaNamesDoNotContainParentSegments() {
        val root=File(System.getProperty("java.io.tmpdir"),"sync-root/../sync-root")
        val target=File(root,"nested/SKILL.md").canonicalFile
        assertEquals("nested/SKILL.md",SyncCrypto.relativePath(root,target))
        assertFails { SyncCrypto.relativePath(root,File(root,"../outside")) }
    }
    @Test fun verifiedPeerMediaRepairsAnUnavailablePathWithoutRestoringDeletedVisibility() = runTest {
        val index=AssetIndex(null)
        val original=Asset("image","file:///old-phone/missing",AssetType.IMAGE,displayName="image",libraryDeleted=true,createdAt=1)
        index.addAsset(original)
        val received=original.copy(uri="file:///verified-copy")
        val result=index.mergeFromPeer(IndexData(assets=listOf(received)),null,false,usableUri={it==received.uri})
        assertEquals(received.uri,result.snapshot.assets.single().uri)
        assertTrue(result.snapshot.assets.single().libraryDeleted)
    }
}
