package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.assets.AssetIndex
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
    @Test fun unavailableProviderLinkDoesNotPretendItsOriginalWasCopied()=runTest {
        val dir=root();val file=File(dir,"index.json").apply {writeText("""{"assets":[{"id":"a","uri":"https://expired.invalid/image.png","type":"IMAGE","displayName":"Original perdido","createdAt":1}]}""")}
        val result=workspace(AssetIndex(file),File(dir,"sync"),SyncJournalTest.Cloud()).cycle(null)
        assertEquals("partial",result["state"]!!.jsonPrimitive.content)
        assertEquals(1,result["missingMedia"]!!.jsonPrimitive.int)
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
}
