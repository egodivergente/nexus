package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.assets.*
import com.vidadomu.nexus.agent.core.sync.*
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.util.UUID
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody

class StorySyncCompatibilityTest {
    private fun dir()=Files.createTempDirectory("nexus-story-compat").toFile()
    private suspend fun command(index:AssetIndex,p:String,action:String,fields:JsonObjectBuilder.()->Unit={})=index.storyCommand(p,buildJsonObject {put("action",action);put("operation_id",UUID.randomUUID().toString());fields()}).also {assertTrue(it["ok"]!!.jsonPrimitive.boolean,it.toString())}
    private fun JsonObject.id()=getValue("id").jsonPrimitive.content
    private fun workspace(index:AssetIndex,root:File,cloud:SyncRemote,story:SyncRemote?,enabled:()->Boolean={true})=SyncWorkspace(index,File(root,"sync"),cloud,open={uri->
        if(uri.startsWith("file:"))File(URI(uri)).takeIf {it.isFile}?.let {SyncInput(it.extension,it.inputStream())}else null
    },storyRemote=story,storyEnabled=enabled)
    private class CountRemote(private val remote:SyncRemote):SyncRemote by remote {
        var reads=0
        override fun commits():List<SyncObject> {reads++;return remote.commits()}
    }

    @Test fun oldCommonEditsSurviveWhileExtendedFieldsAndPausedNarrativeStayLocal()=runBlocking<Unit> {
        val root=dir()
        try {
            val cloud=SyncJournalTest.Cloud();val narrative=SyncJournalTest.Cloud();val counted=CountRemote(narrative)
            val index=AssetIndex(File(root,"index.json"));val p=index.createProject("Original").id
            command(index,p,"update_project"){put("expected_revision",0);put("treatment","Tratamiento que un lector antiguo no conoce")}
            val scene=command(index,p,"create_scene"){put("title","Patio");put("description","Luz de tarde")}.id()
            val shot=command(index,p,"create_shot"){put("scene_id",scene);put("description","La mano tiembla");put("camera","Cámara fija")}.id()
            val context=index.storyGenerationContext(p,shot)
            var enabled=true;val current=workspace(index,root,cloud,counted){enabled}
            current.cycle(null)
            val old=SyncJournal(File(root,"old-reader.json"),cloud,"Lector antiguo")
            var oldRows=old.exchange(emptyMap()).rows;old.acknowledge(oldRows)
            assertFalse(oldRows.getValue("projects:$p").containsKey("treatment"))
            assertFalse(oldRows.getValue("shots:$shot").containsKey("camera"))
            fun publish(changes:Map<String,JsonObject>) {oldRows=old.exchange(oldRows+changes).rows;old.acknowledge(oldRows)}
            publish(mapOf("projects:$p" to JsonObject(oldRows.getValue("projects:$p")+mapOf("name" to JsonPrimitive("Renombrado en la versión antigua"))),
                "scenes:$scene" to JsonObject(oldRows.getValue("scenes:$scene")+mapOf("title" to JsonPrimitive("Patio renombrado"))),
                "shots:$shot" to JsonObject(oldRows.getValue("shots:$shot")+mapOf("code" to JsonPrimitive("Plano A")))))
            current.cycle(null)
            assertEquals("Renombrado en la versión antigua",index.findProject(p)?.name)
            assertEquals("Tratamiento que un lector antiguo no conoce",index.findProject(p)?.treatment)
            assertEquals("Cámara fija",index.snapshot().shots.single().camera)
            assertFalse(index.isStoryContextStale(context),"A cosmetic rename must not invalidate material")
            current.cycle(null);val stable=index.snapshot().shots.single().revision
            current.cycle(null);assertEquals(stable,index.snapshot().shots.single().revision)
            publish(mapOf("shots:$shot" to JsonObject(oldRows.getValue("shots:$shot")+mapOf("description" to JsonPrimitive("La mano deja de temblar")))))
            current.cycle(null)
            assertEquals("La mano deja de temblar",index.snapshot().shots.single().description)
            assertTrue(index.isStoryContextStale(context));assertEquals("Cámara fija",index.snapshot().shots.single().camera)
            val reads=counted.reads;val uploads=narrative.commits.size;enabled=false
            publish(mapOf("scenes:$scene" to JsonObject(oldRows.getValue("scenes:$scene")+mapOf("title" to JsonPrimitive("Título nuevo con guion pausado")))))
            val paused=current.cycle(null)
            assertEquals(reads,counted.reads);assertEquals(uploads,narrative.commits.size)
            assertEquals("disabled",paused.getValue("narrativeSync").jsonObject.getValue("state").jsonPrimitive.content)
            assertEquals("Título nuevo con guion pausado",index.snapshot().scenes.single().title)
            assertEquals("Luz de tarde",index.snapshot().scenes.single().description)
            assertEquals("Cámara fija",index.snapshot().shots.single().camera)
        } finally {root.deleteRecursively()}
    }

    @Test fun theNarrativeNamespaceIsInvisibleToLegacyCommitReaders() {
        val ordinary="11111111-1111-1111-1111-111111111111";val story="22222222-2222-2222-2222-222222222222"
        val client=OkHttpClient.Builder().addInterceptor {chain->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"files":[{"id":"old","name":"commit-$ordinary.json","size":"10"},{"id":"new","name":"story-v1-commit-$story.json","size":"10"}]}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val legacy=DriveRemote({"test-token"},"root",client)
        assertEquals(listOf("commit-$ordinary.json"),legacy.commits().map {it.name})
        assertEquals(listOf("commit-$story.json"),legacy.storyChannel().commits().map {it.name})
        assertFalse(SyncJournal.validKey("story:blocks:one"),"The legacy validation contract must not change")
    }

    @Test fun selectingProjectBDownloadsItsLinkedOriginalOwnedByProjectA()=runBlocking<Unit> {
        val root=dir()
        try {
            val cloud=SyncJournalTest.Cloud();val narrative=SyncJournalTest.Cloud();val a=AssetIndex(File(root,"a/index.json"));val b=AssetIndex(File(root,"b/index.json"))
            val owner=a.createProject("Propietario A").id;val use=a.createProject("Uso B").id
            val scene=command(a,use,"create_scene"){put("title","Escena B")}.id();val shot=command(a,use,"create_shot"){put("scene_id",scene)}.id()
            val original=File(root,"original.png").apply {writeBytes(byteArrayOf(1,2,3,4,5))}
            a.addAsset(Asset("shared",original.toURI().toString(),AssetType.IMAGE,displayName="Un original",projectId=owner,createdAt=1))
            command(a,use,"link_material"){put("asset_id","shared");put("scope","shot");put("target_id",shot);put("role","chosen");put("selected",true)}
            workspace(a,File(root,"a"),cloud,narrative).cycle(null)
            workspace(b,File(root,"b"),cloud,narrative).cycle(setOf(use))
            assertContentEquals(original.readBytes(),File(URI(b.asset("shared")!!.uri)).readBytes())
            assertEquals(owner,b.asset("shared")!!.projectId);assertEquals(use,b.snapshot().story.links.single().projectId)
            assertEquals(1,cloud.blobs.size)
        } finally {root.deleteRecursively()}
    }

    @Test fun incompleteGraphDoesNotBecomeAcknowledgedDeletionOnTheNextCycle()=runBlocking<Unit> {
        val root=dir()
        try {
            val cloud=SyncJournalTest.Cloud();val narrative=SyncJournalTest.Cloud();val source=AssetIndex(File(root,"source/index.json"));val target=AssetIndex(File(root,"target/index.json"))
            val p=source.createProject("Proyecto").id;val scene=command(source,p,"create_scene"){}.id();val shot=command(source,p,"create_shot"){put("scene_id",scene)}.id()
            workspace(source,File(root,"source-legacy"),cloud,null).cycle(null)
            val original=File(root,"late.png").apply {writeBytes(byteArrayOf(11,22,33))}
            source.addAsset(Asset("late",original.toURI().toString(),AssetType.IMAGE,displayName="Pendiente de llegar",projectId=p,createdAt=1))
            command(source,p,"link_material"){put("asset_id","late");put("scope","shot");put("target_id",shot);put("role","candidate")}
            val writer=SyncJournal(File(root,"story-writer.json"),narrative,"Source",setOf("story"))
            val uploaded=writer.exchange(StorySyncCodec.capture(source.snapshot()));writer.acknowledge(uploaded.rows)
            val receiver=workspace(target,File(root,"target"),cloud,narrative)
            repeat(2) {
                val status=receiver.cycle(null)
                assertTrue(status.getValue("narrativeSync").jsonObject.getValue("pendingDependencies").jsonPrimitive.int>0)
                assertTrue(target.snapshot().projects.isEmpty())
            }
            workspace(source,File(root,"source-full"),cloud,narrative).cycle(null)
            val status=receiver.cycle(null)
            assertEquals(0,status.getValue("narrativeSync").jsonObject.getValue("pendingDependencies").jsonPrimitive.int)
            assertEquals("late",target.snapshot().story.links.single().assetId)
            assertContentEquals(original.readBytes(),File(URI(target.asset("late")!!.uri)).readBytes())
        } finally {root.deleteRecursively()}
    }

    @Test fun deduplicationUsesTheLocalAssetVersionRetainedDuringAConflict()=runBlocking<Unit> {
        val root=dir()
        try {
            val cloud=SyncJournalTest.Cloud();val narrative=SyncJournalTest.Cloud()
            val index=AssetIndex(File(root,"index.json"));val p=index.createProject("Conflicto de original").id
            val initial=File(root,"initial.png").apply {writeBytes(byteArrayOf(1))}
            val local=File(root,"local.png").apply {writeBytes(byteArrayOf(2))}
            val other=File(root,"other.png").apply {writeBytes(byteArrayOf(3))}
            index.addAsset(Asset("a",initial.toURI().toString(),AssetType.IMAGE,displayName="A",projectId=p,createdAt=1))
            index.addAsset(Asset("z",other.toURI().toString(),AssetType.IMAGE,displayName="Z",projectId=p,createdAt=1))
            val sync=workspace(index,root,cloud,narrative);sync.cycle(null)
            val revisions=cloud.commits.values.flatMap {SyncJournal.json.decodeFromString<SyncCommit>(it.decodeToString()).revisions}
            val original=revisions.single {it.key=="assets:a"};val otherRow=revisions.single {it.key=="assets:z"}.value!!
            val remoteValue=JsonObject(original.value!!+mapOf("uri" to otherRow.getValue("uri")))
            val commit=SyncCommit(id=UUID.randomUUID().toString(),device="Otro dispositivo",revisions=listOf(
                SyncRevision("ffffffff-ffff-ffff-ffff-ffffffffffff","assets:a",listOf(original.id),remoteValue,"Otro dispositivo",2)))
            cloud.putCommit(commit,SyncJournal.json.encodeToString(SyncCommit.serializer(),commit).toByteArray())
            index.relocateAsset("a",index.asset("a")!!.uri,local.toURI().toString(),"A")
            val status=sync.cycle(null)
            assertTrue(status.getValue("conflicts").jsonArray.any {it.jsonObject["key"]?.jsonPrimitive?.content=="assets:a"})
            assertEquals(0,status.getValue("deduplicated").jsonPrimitive.int)
            assertEquals(2,index.snapshot().assets.count {!it.libraryDeleted})
            assertContentEquals(local.readBytes(),File(URI(index.asset("a")!!.uri)).readBytes())
            assertContentEquals(other.readBytes(),File(URI(index.asset("z")!!.uri)).readBytes())
        } finally {root.deleteRecursively()}
    }

    @Test fun acceptingTheSameLegacyProposalOfflineUsesOneSetOfCreationIdentities()=runBlocking<Unit> {
        val root=dir()
        try {
            val source=AssetIndex(null);val p=source.createProject("Propuesta compartida").id
            val scene=command(source,p,"create_scene"){}.id();val shot=command(source,p,"create_shot"){put("scene_id",scene)}.id()
            for(n in 1..2) {
                source.addAsset(Asset("ref$n","file:///reference-$n.png",AssetType.IMAGE,displayName="Ref $n",projectId=p,createdAt=n.toLong()))
                command(source,p,"link_material"){put("asset_id","ref$n");put("scope","shot");put("target_id",shot);put("role","reference")}
            }
            val proposal=command(source,p,"propose") {
                put("title","Preparar estructura")
                put("changes",buildJsonArray {
                    add(buildJsonObject {put("action","create_scene");put("title","Otra escena")})
                    add(buildJsonObject {put("action","create_shot");put("scene_id",scene);put("title","Plano nuevo")})
                    add(buildJsonObject {put("action","duplicate_shot");put("shot_id",shot);put("expected_revision",0)})
                    add(buildJsonObject {put("action","link_material");put("asset_id","ref1");put("scope","shot");put("target_id",shot);put("role","composition")})
                    add(buildJsonObject {put("action","set_preference");put("scope","shot");put("target_id",shot);put("text","Conservar la mirada")})
                    add(buildJsonObject {put("action","upsert_element");put("kind","location");put("name","Patio")})
                    add(buildJsonObject {put("action","import_script");put("scene_id",scene);put("text","Primer párrafo\n\nSegundo párrafo")})
                    add(buildJsonObject {put("action","enqueue_task");put("scene_id",scene);put("title","Preparar referencias")})
                })
            }.id()
            // Emulate a pending proposal written before creation seeds existed. Its sources have
            // a different array order on the other device, as can happen after a merge.
            val before=source.snapshot()
            val pending=before.copy(story=before.story.copy(proposals=before.story.proposals.map { value -> value.copy(changes=value.changes.map { change ->
                change.copy(command=JsonObject(change.command.filterKeys {it !in setOf("id","shot_block_id","block_ids")}))
            }) }))
            fun open(name:String,reversed:Boolean):AssetIndex {
                val file=File(root,"$name.json")
                file.writeText(AssetIndex.JSON.encodeToString(IndexData.serializer(),if(reversed)pending.copy(story=pending.story.copy(links=pending.story.links.reversed()))else pending))
                return AssetIndex(file)
            }
            val a=open("a",false);val b=open("b",true)
            for(index in listOf(a,b))command(index,p,"apply_proposal"){put("proposal_id",proposal);put("expected_revision",0)}
            assertEquals(a.snapshot().scenes.map {it.id}.toSet(),b.snapshot().scenes.map {it.id}.toSet())
            assertEquals(a.snapshot().shots.map {it.id}.toSet(),b.snapshot().shots.map {it.id}.toSet())
            assertEquals(a.snapshot().story.blocks.associateBy {it.id},b.snapshot().story.blocks.associateBy {it.id})
            assertEquals(a.snapshot().story.links.associateBy {it.id},b.snapshot().story.links.associateBy {it.id})
            assertEquals(a.snapshot().story.preferences,b.snapshot().story.preferences)
            assertEquals(a.snapshot().story.elements,b.snapshot().story.elements)
            assertEquals(a.snapshot().story.tasks,b.snapshot().story.tasks)
            assertEquals(2,a.snapshot().scenes.size);assertEquals(3,a.snapshot().shots.size)
        } finally {root.deleteRecursively()}
    }
}
