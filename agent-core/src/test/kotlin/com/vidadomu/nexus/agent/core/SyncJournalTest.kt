package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.sync.*
import java.io.File
import java.nio.file.Files
import kotlin.test.*
import kotlinx.serialization.json.*

class SyncJournalTest {
    class Cloud:SyncRemote {
        val commits=linkedMapOf<String,ByteArray>();val blobs=mutableMapOf<String,ByteArray>();var fail=false
        override fun commits()=commits.map {(name,bytes)->SyncObject(name,name,bytes.size.toLong())}
        override fun readCommit(item:SyncObject)=commits.getValue(item.name)
        override fun putCommit(commit:SyncCommit,bytes:ByteArray){if(fail)throw java.io.IOException("offline");commits["commit-${commit.id}.json"]=bytes}
        override fun putBlob(name:String,file:File){blobs.putIfAbsent(name,file.readBytes())}
        override fun getBlob(name:String,destination:File){destination.writeBytes(blobs.getValue(name))}
    }
    private fun file()=File(Files.createTempDirectory("nexus-sync-test").toFile(),"state.json")
    private fun row(name:String)=buildJsonObject {put("id","p");put("name",name);put("createdAt",1)}
    private fun cycle(j:SyncJournal,rows:Map<String,JsonObject>):SyncResult=j.exchange(rows).also {j.acknowledge(it.rows)}
    @Test fun projectsArriveWithoutChangingIdentity() {
        val cloud=Cloud();val a=SyncJournal(file(),cloud);val b=SyncJournal(file(),cloud)
        val input=mapOf("projects:p" to row("Mateo"));cycle(a,input)
        assertEquals(input,cycle(b,emptyMap()).rows);assertEquals(1,cloud.commits.size)
    }
    @Test fun independentOfflineChangesAreCombined() {
        val cloud=Cloud();val a=SyncJournal(file(),cloud);val b=SyncJournal(file(),cloud)
        cycle(a,mapOf("projects:p" to row("Mateo")));val baseline=cycle(b,emptyMap()).rows
        cycle(a,baseline+mapOf("scenes:a" to row("A")))
        cycle(b,baseline+mapOf("shots:b" to row("B")))
        assertEquals(3,cycle(a,baseline+mapOf("scenes:a" to row("A"))).rows.size)
    }
    @Test fun concurrentEditsRetainBothVersionsAndConverge() {
        val cloud=Cloud();val a=SyncJournal(file(),cloud);val b=SyncJournal(file(),cloud)
        val initial=mapOf("projects:p" to row("base"));cycle(a,initial);cycle(b,emptyMap())
        val left=mapOf("projects:p" to row("Pixel"));val right=mapOf("projects:p" to row("S25"))
        cycle(a,left);val br=cycle(b,right);val ar=cycle(a,left)
        assertEquals(ar.rows,br.rows);assertEquals(1,ar.conflicts.size)
        assertEquals(setOf("Pixel","S25"),ar.conflicts.single().versions.map {it.value!!["name"]!!.jsonPrimitive.content}.toSet())
        a.resolve("projects:p",ar.conflicts.single().versions.first {it.value==row("Pixel")}.id)
        val resolved=cycle(a,ar.rows);val received=cycle(b,br.rows)
        assertTrue(resolved.conflicts.isEmpty());assertEquals(row("Pixel"),received.rows["projects:p"])
    }
    @Test fun failedUploadSurvivesProcessRestartWithoutDuplicates() {
        val cloud=Cloud();val state=file();var journal=SyncJournal(state,cloud)
        val input=mapOf("projects:p" to row("local"));cloud.fail=true
        assertFailsWith<java.io.IOException>{journal.exchange(input)}
        cloud.fail=false;journal=SyncJournal(state,cloud);cycle(journal,input);cycle(journal,input)
        assertEquals(1,cloud.commits.size)
    }
    @Test fun interruptedApplyCanBeReplayedWithoutTreatingRemoteRowsAsDeletions() {
        val cloud=Cloud();val a=SyncJournal(file(),cloud);val path=file();var b=SyncJournal(path,cloud)
        val input=mapOf("projects:p" to row("remote"));cycle(a,input)
        b.exchange(emptyMap()) // process dies before applying or acknowledging
        b=SyncJournal(path,cloud);assertEquals(input,cycle(b,emptyMap()).rows)
        assertEquals(1,cloud.commits.size)
    }
    @Test fun deleteAndConcurrentEditRemainRecoverable() {
        val cloud=Cloud();val a=SyncJournal(file(),cloud);val b=SyncJournal(file(),cloud)
        cycle(a,mapOf("projects:p" to row("base")));cycle(b,emptyMap());cycle(a,emptyMap())
        val conflict=cycle(b,mapOf("projects:p" to row("edited"))).conflicts.single()
        assertTrue(conflict.versions.any {it.value==null});assertTrue(conflict.versions.any {it.value==row("edited")})
    }
    @Test fun editsDuringAnInterruptedApplyDoNotSilentlySupersedeUnseenRemoteChanges() {
        val cloud=Cloud();val a=SyncJournal(file(),cloud);val b=SyncJournal(file(),cloud)
        val base=mapOf("projects:p" to row("base"));cycle(a,base);cycle(b,emptyMap())
        cycle(a,mapOf("projects:p" to row("remote")))
        b.exchange(base) // incoming rows were downloaded, but local compare-and-swap rejected apply
        val next=cycle(b,mapOf("projects:p" to row("local edit during transfer")))
        assertEquals(1,next.conflicts.size)
        assertEquals(setOf("remote","local edit during transfer"),next.conflicts.single().versions.map {it.value!!["name"]!!.jsonPrimitive.content}.toSet())
    }
    @Test fun corruptBlobIsNeverPublishedAsLocalOriginal() {
        val cloud=Cloud();val root=Files.createTempDirectory("nexus-blobs").toFile();val store=SyncBlobs(root,cloud)
        val reference=store.capture("real media".byteInputStream(),"png")
        assertEquals("real media",store.obtain(reference).readText())
        store.existing(reference)!!.delete();cloud.blobs[reference.removePrefix(SyncBlobs.PREFIX)]="corrupt".toByteArray()
        assertFailsWith<IllegalArgumentException>{store.obtain(reference)}
        assertNull(store.existing(reference));assertFalse(root.listFiles()!!.any {it.extension=="partial"})
    }
    @Test fun contentAddressingDeduplicatesIdenticalMedia() {
        val cloud=Cloud();val blobs=SyncBlobs(Files.createTempDirectory("nexus-blobs").toFile(),cloud)
        assertEquals(blobs.capture("same".byteInputStream(),"mp4"),blobs.capture("same".byteInputStream(),"mp4"))
        assertEquals(1,cloud.blobs.size)
    }
    @Test fun invalidEntityAndPathAreRejected() {
        val cloud=Cloud();val journal=SyncJournal(file(),cloud)
        assertFailsWith<IllegalArgumentException>{journal.exchange(mapOf("credentials:secret" to row("x")))}
        assertFailsWith<IllegalArgumentException>{SyncBlobs(file().parentFile,cloud).obtain("nexus-sync:../../private")}
    }
    @Test fun devicePathAndVerifiedPortableCopyAreTheSameAssetVersion() {
        val cloud=Cloud();val a=SyncJournal(file(),cloud);val b=SyncJournal(file(),cloud)
        val local=buildJsonObject {put("id","a");put("name","Original");put("uri","content://other-device/original")}
        val portable=JsonObject(local+mapOf("uri" to JsonPrimitive(SyncBlobs.PREFIX+"a".repeat(64)+".png")))
        cycle(a,mapOf("assets:a" to local));val received=cycle(b,mapOf("assets:a" to portable))
        assertTrue(received.conflicts.isEmpty());assertEquals(portable,received.rows["assets:a"])
        val merged=cycle(a,mapOf("assets:a" to local));assertTrue(merged.conflicts.isEmpty());assertEquals(portable,merged.rows["assets:a"])
        assertTrue(cycle(a,merged.rows).conflicts.isEmpty())
    }
    @Test fun differentVerifiedOriginalBytesStillRequireExplicitConflictResolution() {
        val cloud=Cloud();val a=SyncJournal(file(),cloud);val b=SyncJournal(file(),cloud)
        fun original(hash:String)=buildJsonObject {put("id","a");put("name","Original");put("uri",SyncBlobs.PREFIX+hash.repeat(64)+".png")}
        cycle(a,mapOf("assets:a" to original("a")))
        assertEquals(1,cycle(b,mapOf("assets:a" to original("b"))).conflicts.size)
    }
    @Test fun sameVerifiedBytesWithDifferentExtensionsAreTheSameAssetVersion() {
        val cloud=Cloud();val a=SyncJournal(file(),cloud);val b=SyncJournal(file(),cloud)
        fun original(extension:String)=buildJsonObject {put("id","a");put("name","Original");put("uri",SyncBlobs.PREFIX+"a".repeat(64)+"."+extension)}
        cycle(a,mapOf("assets:a" to original("jpeg")))
        assertTrue(cycle(b,mapOf("assets:a" to original("jpg"))).conflicts.isEmpty())
        assertTrue(cycle(a,mapOf("assets:a" to original("jpeg"))).conflicts.isEmpty())
    }
    private fun generation(references:List<String>,outputs:List<String> = emptyList())=buildJsonObject {
        put("id","g");put("referenceUris",JsonArray(references.map(::JsonPrimitive)));put("outputAssetIds",JsonArray(outputs.map(::JsonPrimitive)))
    }
    @Test fun generationReferencesUseVerifiedCopiesAcrossDevicesAndRestarts() {
        val cloud=Cloud();val path=file();var a=SyncJournal(path,cloud);val b=SyncJournal(file(),cloud)
        val local=generation(listOf(SyncBlobs.PREFIX+"a".repeat(64)+".jpeg"));val portable=generation(listOf(SyncBlobs.PREFIX+"a".repeat(64)+".jpg"))
        cycle(a,mapOf("generations:g" to local));val received=cycle(b,mapOf("generations:g" to portable))
        assertTrue(received.conflicts.isEmpty());assertEquals(portable,received.rows["generations:g"])
        a=SyncJournal(path,cloud);val again=cycle(a,mapOf("generations:g" to local))
        assertTrue(again.conflicts.isEmpty());assertEquals(portable,again.rows["generations:g"])
    }
    @Test fun referenceExtensionsDoNotCreateConflictsButDifferentBytesDo() {
        val cloud=Cloud();val a=SyncJournal(file(),cloud);val b=SyncJournal(file(),cloud);val c=SyncJournal(file(),cloud)
        fun refs(hash:String,extension:String)=mapOf("generations:g" to generation(listOf(SyncBlobs.PREFIX+hash.repeat(64)+"."+extension)))
        cycle(a,refs("a","jpeg"));assertTrue(cycle(b,refs("a","jpg")).conflicts.isEmpty())
        assertEquals(1,cycle(c,refs("b","jpg")).conflicts.size)
    }
    @Test fun generationReferenceCountAndOrderRemainMeaningful() {
        val cloud=Cloud();val a=SyncJournal(file(),cloud);val b=SyncJournal(file(),cloud);val c=SyncJournal(file(),cloud)
        val first=SyncBlobs.PREFIX+"a".repeat(64)+".png";val second=SyncBlobs.PREFIX+"b".repeat(64)+".png"
        cycle(a,mapOf("generations:g" to generation(listOf(first,second))))
        assertEquals(1,cycle(b,mapOf("generations:g" to generation(listOf(second,first)))).conflicts.size)
        assertEquals(1,cycle(c,mapOf("generations:g" to generation(listOf(first)))).conflicts.size)
    }
    @Test fun anUnverifiedLocalReferenceCannotHideAChangedGenerationInput() {
        val cloud=Cloud();val a=SyncJournal(file(),cloud);val b=SyncJournal(file(),cloud)
        val changed=generation(listOf("file:/pixel/different-original.png"))
        val known=generation(listOf(SyncBlobs.PREFIX+"a".repeat(64)+".png"))
        cycle(a,mapOf("generations:g" to changed))
        val result=cycle(b,mapOf("generations:g" to known))
        assertEquals(1,result.conflicts.size)
        assertEquals(setOf(changed,known),result.conflicts.single().versions.map {it.value}.toSet())
    }
    private fun output(id:String,hash:String,project:String="p")=buildJsonObject {put("id",id);put("projectId",project);put("uri",SyncBlobs.PREFIX+hash.repeat(64)+".png")}
    @Test fun copiedOutputIdsAreEquivalentOnlyWithTheSameVerifiedBytesAndProject() {
        val cloud=Cloud();val a=SyncJournal(file(),cloud);val b=SyncJournal(file(),cloud)
        cycle(a,mapOf("assets:a" to output("a","a"),"generations:g" to generation(emptyList(),listOf("a"))))
        val merged=cycle(b,mapOf("assets:b" to output("b","a"),"generations:g" to generation(emptyList(),listOf("b"))))
        assertTrue(merged.conflicts.isEmpty());assertEquals(listOf("a"),merged.rows.getValue("generations:g")["outputAssetIds"]!!.jsonArray.map {it.jsonPrimitive.content})
        val c=SyncJournal(file(),cloud)
        assertEquals(1,cycle(c,mapOf("assets:c" to output("c","b"),"generations:g" to generation(emptyList(),listOf("c")))).conflicts.size)
    }
    @Test fun outputIdsFromDifferentProjectsAreNotSilentlyCombined() {
        val cloud=Cloud();val a=SyncJournal(file(),cloud);val b=SyncJournal(file(),cloud)
        cycle(a,mapOf("assets:a" to output("a","a","first"),"generations:g" to generation(emptyList(),listOf("a"))))
        assertEquals(1,cycle(b,mapOf("assets:b" to output("b","a","second"),"generations:g" to generation(emptyList(),listOf("b")))).conflicts.size)
    }
    @Test fun resolvingARealMetadataConflictKeepsItsVerifiedReference() {
        val cloud=Cloud();val a=SyncJournal(file(),cloud);val b=SyncJournal(file(),cloud)
        val local=JsonObject(generation(listOf(SyncBlobs.PREFIX+"a".repeat(64)+".jpeg"))+mapOf("prompt" to JsonPrimitive("local edit")))
        val portable=JsonObject(generation(listOf(SyncBlobs.PREFIX+"a".repeat(64)+".png"))+mapOf("prompt" to JsonPrimitive("remote edit")))
        cycle(a,mapOf("generations:g" to local));val received=cycle(b,mapOf("generations:g" to portable));val conflict=received.conflicts.single()
        b.resolve("generations:g",conflict.versions.first {it.value==local}.id)
        val resolved=cycle(b,received.rows).rows.getValue("generations:g")
        assertEquals(JsonPrimitive("local edit"),resolved["prompt"])
        assertEquals(portable["referenceUris"],resolved["referenceUris"])
    }
}
