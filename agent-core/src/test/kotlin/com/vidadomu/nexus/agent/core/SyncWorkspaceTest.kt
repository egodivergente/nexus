package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.assets.AssetIndex
import com.vidadomu.nexus.agent.core.assets.IndexData
import com.vidadomu.nexus.agent.core.assets.Project
import com.vidadomu.nexus.agent.core.assets.ProjectTransferReceipt
import com.vidadomu.nexus.agent.core.sync.*
import java.io.File
import java.net.URI
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class SyncWorkspaceTest {
    private fun root()=Files.createTempDirectory("nexus-workspace-test").toFile()
    private fun workspace(index:AssetIndex,dir:File,cloud:SyncRemote)=SyncWorkspace(index,dir,cloud,open={uri->
        if(uri.startsWith("file:")){val source=File(URI(uri));SyncInput(source.extension,source.inputStream())}else null
    })
    @Test fun peerUpdateCannotBeOverwrittenByInFlightDriveSnapshot()=runTest {
        val file=File(root(),"index.json").apply {writeText("""{"projects":[{"id":"p","name":"Proyecto","createdAt":1}]}""")}
        val index=AssetIndex(file);val expected=index.snapshot()
        val peer=expected.copy(projects=expected.projects.map {it.copy(description="Editado en otro móvil")})
        index.mergeFromPeer(peer,expected,false)
        assertFailsWith<IllegalStateException>{index.applySyncedSnapshot(expected,expected)}
        assertEquals("Editado en otro móvil",index.snapshot().projects.single().description)
    }
    @Test fun realBytesMoveBetweenDevicesAndProjectDownloadIsSelective()=runTest {
        val cloud=SyncJournalTest.Cloud();val a=root();val b=root();val original=File(a,"image.png").apply {writeBytes(byteArrayOf(137.toByte(),80,78,71,13,10,26,10,1,2,3))}
        val sourceFile=File(a,"index.json").apply {writeText(buildJsonObject {
            put("projects",buildJsonArray {add(buildJsonObject {put("id","p");put("name","Mateo");put("createdAt",1)})})
            put("assets",buildJsonArray {add(buildJsonObject {put("id","a");put("projectId","p");put("uri",original.toURI().toString());put("type","IMAGE");put("displayName","Plano");put("createdAt",2)})})
        }.toString())}
        val source=AssetIndex(sourceFile);val target=AssetIndex(File(b,"index.json"))
        workspace(source,File(a,"sync"),cloud).cycle(null)
        workspace(target,File(b,"sync"),cloud).cycle(emptySet())
        assertEquals("p",target.snapshot().projects.single().id)
        assertTrue(target.snapshot().assets.single().uri.startsWith(SyncBlobs.PREFIX))
        workspace(target,File(b,"sync"),cloud).cycle(setOf("p"))
        val downloaded=File(URI(target.snapshot().assets.single().uri))
        assertContentEquals(original.readBytes(),downloaded.readBytes())
        assertEquals(1,cloud.blobs.size)
    }
    @Test fun downloadedCopyIsNotCapturedAgainNorPublishedAsADevicePath()=runTest {
        val cloud=SyncJournalTest.Cloud();val a=root();val b=root();val original=File(a,"image.png").apply {writeBytes(byteArrayOf(1,2,3,4,5))}
        val sourceFile=File(a,"index.json").apply {writeText(buildJsonObject {
            put("projects",buildJsonArray {add(buildJsonObject {put("id","p");put("name","Mateo");put("createdAt",1)})})
            put("assets",buildJsonArray {add(buildJsonObject {put("id","a");put("projectId","p");put("uri",original.toURI().toString());put("type","IMAGE");put("displayName","Plano");put("createdAt",2)})})
            put("generations",buildJsonArray {add(buildJsonObject {put("id","g");put("projectId","p");put("providerId","atlas");put("modelId","m");put("modality","IMAGE");put("status","SUCCEEDED");put("prompt","x");put("referenceUris",buildJsonArray {add(original.toURI().toString())});put("createdAt",3)})})
        }.toString())}
        workspace(AssetIndex(sourceFile),File(a,"sync"),cloud).cycle(null)
        val target=AssetIndex(File(b,"index.json"))
        workspace(target,File(b,"sync"),cloud).cycle(setOf("p"))
        val local=target.snapshot().assets.single().uri
        assertTrue(local.startsWith("file:") && File(URI(local)).exists())
        // Drive unreachable for uploads: the old code captured the copy again, failed and published the device path.
        var uploads=0
        val offline=object:SyncRemote by cloud {override fun putBlob(name:String,file:File){uploads++;throw java.io.IOException("offline")}}
        val commits=cloud.commits.size
        repeat(3) {
            val result=workspace(target,File(b,"sync"),offline).cycle(setOf("p"))
            assertEquals(0,result["missingMedia"]!!.jsonPrimitive.int)
        }
        assertEquals(0,uploads);assertEquals(commits,cloud.commits.size)
        assertEquals(local,target.snapshot().assets.single().uri);assertEquals(listOf(local),target.snapshot().generations.single().referenceUris)
    }
    @Test fun anotherDevicesCopyPathIsLeftAloneAndNeverTriggersALocalCopy()=runTest {
        val dir=root();val foreign="file:///data/user/0/com.vidadomu.nexus.agent/files/assistant/sync/workspaces/x/blobs/"+"ab".repeat(32)+".png"
        val file=File(dir,"index.json").apply {writeText(buildJsonObject {
            put("assets",buildJsonArray {add(buildJsonObject {put("id","a");put("uri",foreign);put("type","IMAGE");put("displayName","Del móvil");put("createdAt",1)})})
        }.toString())}
        val index=AssetIndex(file);var localized=0
        val sync=SyncWorkspace(index,File(dir,"sync"),SyncJournalTest.Cloud(),open={null},localUri={localized++;error("no local copy may be made while capturing")})
        val result=sync.cycle(null)
        assertEquals(1,result["missingMedia"]!!.jsonPrimitive.int);assertEquals(0,localized)
        assertEquals(foreign,index.snapshot().assets.single().uri)
    }
    @Test fun unavailableProviderLinkDoesNotPretendItsOriginalWasCopied()=runTest {
        val dir=root();val file=File(dir,"index.json").apply {writeText("""{"assets":[{"id":"a","uri":"https://expired.invalid/image.png","type":"IMAGE","displayName":"Original perdido","createdAt":1}]}""")}
        val result=workspace(AssetIndex(file),File(dir,"sync"),SyncJournalTest.Cloud()).cycle(null)
        assertEquals("partial",result["state"]!!.jsonPrimitive.content)
        assertEquals(1,result["missingMedia"]!!.jsonPrimitive.int)
    }
    @Test fun originalsWithoutAProjectRemainAvailableOnMobile()=runTest {
        val cloud=SyncJournalTest.Cloud();val a=root();val b=root()
        val original=File(a,"unassigned.png").apply {writeBytes(byteArrayOf(11,22,33,44))}
        val file=File(a,"index.json").apply {writeText(buildJsonObject {
            put("assets",buildJsonArray {add(buildJsonObject {put("id","unassigned");put("uri",original.toURI().toString());put("type","IMAGE");put("displayName","Original");put("createdAt",1)})})
        }.toString())}
        workspace(AssetIndex(file),File(a,"sync"),cloud).cycle(null)
        val target=AssetIndex(File(b,"index.json"))
        workspace(target,File(b,"sync"),cloud).cycle(emptySet())
        assertContentEquals(original.readBytes(),File(URI(target.snapshot().assets.single().uri)).readBytes())
    }
    @Test fun conversationsImportAsHistoryWithoutRunnablePlansOrCredentials() {
        val docs=SyncDocuments(root())
        val chat=Json.parseToJsonElement("""{"items":[{"type":"com.nexus.ChatItem.User","id":"1","text":"Hola"},{"type":"com.nexus.ChatItem.Assistant","id":"2","text":"Respuesta"}],"plans":{"danger":{"confirmed":true}},"credentials":{"token":"must-not-sync"}}""").jsonObject
        val rows=docs.capture(listOf(chat),emptyMap())
        assertEquals(1,rows.size);assertFalse(JsonObject(rows).toString().contains("must-not-sync"));assertFalse(JsonObject(rows).toString().contains("confirmed"))
        var count=0;docs.apply(rows){_,value->count=value["items"]!!.jsonArray.size}
        assertEquals(2,count);assertEquals(rows,docs.capture(emptyList(),emptyMap()))
    }
    @Test fun manuallyCopiedBytesHaveOneLibraryEntryAndKeepHistoricalIdentities()=runTest {
        val cloud=SyncJournalTest.Cloud();val a=root();val b=root()
        fun seed(directory:File,id:String):AssetIndex {
            val original=File(directory,"copy.png").apply {writeBytes(byteArrayOf(1,2,3,4))}
            val file=File(directory,"index.json").apply {writeText(buildJsonObject {
                put("projects",buildJsonArray {add(buildJsonObject {put("id","p");put("name","Proyecto");put("createdAt",1)})})
                put("assets",buildJsonArray {add(buildJsonObject {put("id",id);put("projectId","p");put("uri",original.toURI().toString());put("type","IMAGE");put("displayName","Copia manual");put("createdAt",1)})})
            }.toString())}
            return AssetIndex(file)
        }
        val first=seed(a,"a");val second=seed(b,"b")
        repeat(3){workspace(first,File(a,"sync"),cloud).cycle(null);workspace(second,File(b,"sync"),cloud).cycle(null)}
        for(index in listOf(first,second)) {
            assertEquals(1,index.snapshot().assets.count {!it.libraryDeleted})
            assertEquals(setOf("a","b"),index.snapshot().assets.map {it.id}.toSet())
            for(asset in index.snapshot().assets)assertTrue(File(URI(asset.uri)).exists())
        }
        assertEquals(1,cloud.blobs.size)
        assertTrue(File(a,"copy.png").exists());assertTrue(File(b,"copy.png").exists())
    }
    @Test fun identicalUnavailableUrlsHaveOneVisiblePointerWithoutInventingOriginalBytes()=runTest {
        val dir=root();val file=File(dir,"index.json").apply {writeText("""{"assets":[{"id":"a","uri":"https://expired.invalid/original.png","type":"IMAGE","displayName":"Original","createdAt":1},{"id":"b","uri":"https://expired.invalid/original.png","type":"IMAGE","displayName":"Copia","parentAssetId":"a","createdAt":2}]}""")}
        val index=AssetIndex(file);val cloud=SyncJournalTest.Cloud();val sync=workspace(index,File(dir,"sync"),cloud)
        repeat(3) {
            val result=sync.cycle(null)
            assertEquals("partial",result["state"]!!.jsonPrimitive.content)
            assertEquals(1,result["missingMedia"]!!.jsonPrimitive.int)
            assertEquals(setOf("a","b"),index.snapshot().assets.map {it.id}.toSet())
            assertEquals(1,index.snapshot().assets.count {!it.libraryDeleted})
            assertEquals("a",index.snapshot().assets.single {it.id=="b"}.parentAssetId)
        }
        assertTrue(cloud.blobs.isEmpty())
        assertTrue(index.snapshot().assets.all {it.uri=="https://expired.invalid/original.png"})
    }
    @Test fun unavailableUrlsFromDifferentProjectsOrDifferentSourcesRemainSeparate()=runTest {
        val dir=root();val file=File(dir,"index.json").apply {writeText("""{"projects":[{"id":"p","name":"P","createdAt":1},{"id":"q","name":"Q","createdAt":1}],"assets":[{"id":"a","projectId":"p","uri":"https://expired.invalid/one.png","type":"IMAGE","displayName":"P","createdAt":1},{"id":"b","projectId":"q","uri":"https://expired.invalid/one.png","type":"IMAGE","displayName":"Q","createdAt":1},{"id":"c","projectId":"p","uri":"https://expired.invalid/two.png","type":"IMAGE","displayName":"Otra","createdAt":1}]}""")}
        val index=AssetIndex(file)
        workspace(index,File(dir,"sync"),SyncJournalTest.Cloud()).cycle(null)
        assertEquals(3,index.snapshot().assets.count {!it.libraryDeleted})
    }
    @Test fun localTransferUndoSnapshotsStayPrivateAcrossIncomingSyncWithoutRepeatedApply()=runTest {
        val localDir=root();val peerDir=root();val cloud=SyncJournalTest.Cloud()
        try {
            val privateMarker="private-undo-snapshot-must-stay-local"
            val receipt=ProjectTransferReceipt("local-transfer","source","target",false,"fixture-fingerprint",
                before=buildJsonObject {put("projects",buildJsonArray {add(buildJsonObject {
                    put("id","unrelated-private-project");put("name",privateMarker);put("createdAt",1)
                })})},createdAt=1)
            val localFile=File(localDir,"index.json").apply {writeText(AssetIndex.JSON.encodeToString(IndexData.serializer(),
                IndexData(projects=listOf(Project("p","Proyecto compartido",createdAt=1)),projectTransfers=listOf(receipt))))}
            val peerFile=File(peerDir,"index.json")
            val local=AssetIndex(localFile);val peer=AssetIndex(peerFile)
            val syncDir=File(localDir,"sync")
            val localSync=workspace(local,syncDir,cloud);val peerSync=workspace(peer,File(peerDir,"sync"),cloud)

            localSync.cycle(null)
            peerSync.cycle(null)
            assertEquals("p",peer.snapshot().projects.single().id)
            assertTrue(peer.snapshot().projectTransfers.isEmpty())
            assertFalse(peerFile.readText().contains(privateMarker))
            assertTrue(cloud.commits.values.all {!it.toString(Charsets.UTF_8).contains(privateMarker)})

            peer.updateProject("p","Editado en el otro dispositivo")
            peerSync.cycle(null)
            localSync.cycle(null)
            assertEquals("Editado en el otro dispositivo",local.snapshot().projects.single().description)
            assertEquals(listOf(receipt),local.snapshot().projectTransfers)
            val beforeApply=File(syncDir,"index-before-apply.json")
            val serialized=Json.parseToJsonElement(beforeApply.readText()).jsonObject
            assertTrue(serialized["projectTransfers"]?.jsonArray.orEmpty().isEmpty())
            assertFalse(beforeApply.readText().contains(privateMarker))

            // The diagnostic is written only when a remote snapshot is actually applied. A local
            // receipt must not make every otherwise unchanged cycle take that path again.
            val unchangedMarker="no-additional-index-apply"
            beforeApply.writeText(unchangedMarker)
            val commits=cloud.commits.size
            repeat(2) {
                localSync.cycle(null)
                assertEquals(unchangedMarker,beforeApply.readText())
                assertEquals(listOf(receipt),local.snapshot().projectTransfers)
                assertEquals(commits,cloud.commits.size)
            }
            assertTrue(cloud.commits.values.all {!it.toString(Charsets.UTF_8).contains(privateMarker)})
        } finally {localDir.deleteRecursively();peerDir.deleteRecursively()}
    }
    @Test fun fileErasedOnOneDeviceKeepsItsDriveReferenceAndIsNotFetchedAgain()=runTest {
        val cloud=SyncJournalTest.Cloud();val a=root();val b=root();val c=root()
        val original=File(a,"agent_media/agent_1.png").apply {parentFile.mkdirs();writeBytes(byteArrayOf(1,2,3,9))}
        val sourceFile=File(a,"index.json").apply {writeText(buildJsonObject {
            put("assets",buildJsonArray {add(buildJsonObject {put("id","a");put("uri",original.toURI().toString());put("type","IMAGE");put("displayName","Plano");put("createdAt",2)})})
        }.toString())}
        val source=AssetIndex(sourceFile);val target=AssetIndex(File(b,"index.json"))
        workspace(source,File(a,"sync"),cloud).cycle(null)
        workspace(target,File(b,"sync"),cloud).cycle(null)
        val copy=target.snapshot().assets.single().uri
        assertTrue(File(URI(copy)).isFile)
        // «Eliminar» on device A: the row stays as removed and the original and A's Drive copy are erased.
        source.deleteLibraryAssets(setOf("a"))
        assertTrue(original.delete());File(a,"sync/blobs").listFiles().orEmpty().forEach {assertTrue(it.delete())}
        repeat(2) {assertEquals(0,workspace(source,File(a,"sync"),cloud).cycle(null)["missingMedia"]!!.jsonPrimitive.int)}
        assertTrue(source.snapshot().assets.single().uri.startsWith(SyncBlobs.PREFIX))
        assertTrue(File(a,"sync/blobs").listFiles().orEmpty().none {it.isFile})
        // A device that never had it receives the removed row without its bytes.
        val third=AssetIndex(File(c,"index.json"))
        workspace(third,File(c,"sync"),cloud).cycle(null)
        assertTrue(third.snapshot().assets.single().libraryDeleted)
        assertTrue(third.snapshot().assets.single().uri.startsWith(SyncBlobs.PREFIX))
        assertTrue(File(c,"sync/blobs").listFiles().orEmpty().none {it.isFile})
        // The device that already had a copy keeps it, and its reference to it.
        workspace(target,File(b,"sync"),cloud).cycle(null)
        assertTrue(target.snapshot().assets.single().libraryDeleted)
        assertEquals(copy,target.snapshot().assets.single().uri)
        assertTrue(File(URI(copy)).isFile)
        assertEquals(1,cloud.blobs.size)
    }
}
