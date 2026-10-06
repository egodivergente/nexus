package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.core.util.BoundedCache
import com.vidadomu.nexus.provider.atlas.*
import com.vidadomu.nexus.ui.create.*
import org.junit.Assert.*
import org.junit.Test
import okio.Buffer
import okhttp3.RequestBody.Companion.toRequestBody

class StudioReliabilityTest {
    private fun motion():NexusModel=NativeModelSchema.decode(AtlasModelCatalog.curatedModels.first().copy(id="motion-fixture",providerId="atlas_cloud",modalities=listOf(Modality.VIDEO)),
        javaClass.getResource("/native/kling-motion-malformed.json")!!.readText()).model
    private fun ready():CreateUiState {val model=motion();return CreateUiState(selectedModel=model,selectedModality=Modality.VIDEO,schemaReady=true,
        parameters=mapOf("character_orientation" to "video","keep_original_sound" to "false"),attachedMedia=listOf(
            AttachedMedia(uriString="file:///fixture.png",mimeType="image/png",slotType=MediaSlotType.PRIMARY_IMAGE),
            AttachedMedia(uriString="file:///fixture.mp4",mimeType="video/mp4",slotType=MediaSlotType.VIDEO_INPUT)))}
    @Test fun ctaReadinessAndMotionContractUseSameValidation() {
        val state=ready()
        assertTrue(creationReadiness(state).canSubmit)
        assertTrue(ModelCapability.MOTION_CONTROL in state.selectedModel!!.capabilities)
        assertFalse(ModelCapability.TEXT_TO_VIDEO in state.selectedModel!!.capabilities)
        assertFalse(state.selectedModel!!.parameters.any {it.key=="–"})
        assertEquals("Añade un vídeo de entrada.",creationReadiness(state.copy(attachedMedia=state.attachedMedia.take(1))).message)
        assertFalse(creationReadiness(state.copy(isSubmitting=true)).canSubmit)
        assertFalse(creationReadiness(state.copy(isPreparingMedia=true)).canSubmit)
        assertFalse(creationReadiness(state.copy(parameters=state.parameters+("character_orientation" to "invalid"))).canSubmit)
    }
    @Test fun incompatibleFileDoesNotBecomeReady() {
        val state=ready();assertFalse(creationReadiness(state.copy(attachedMedia=state.attachedMedia.map {it.copy(mimeType="audio/mpeg")})).canSubmit)
    }
    @Test fun presetsDiscardRemovedKeysAndInvalidOptionsButKeepFalseAndZero() {
        val model=motion().copy(parameters=listOf(ModelParameter("count","Cantidad",ParameterType.INTEGER,"1",minValue=0f,nativeType="integer"),ModelParameter("audio","Sonido",ParameterType.BOOLEAN,"true",nativeType="boolean")))
        assertEquals(mapOf("count" to "0","audio" to "false"),FormContract.restoredValues(model,mapOf("count" to "0","audio" to "false","obsolete" to "bad")))
        assertEquals("1",FormContract.restoredValues(model,mapOf("count" to "NaN"))["count"])
    }
    @Test fun formContractRemovesDuplicateKeysAndMediaGhosts() {
        val good=ModelParameter("mode","Modo",ParameterType.TEXT)
        assertEquals(listOf(good),FormContract.normalize(listOf(good,good.copy(displayName="Duplicate"),good.copy(key="–"),good.copy(key="image",type=ParameterType.IMAGE))))
    }
    @Test fun nullRequiredSchemaDoesNotBecomeExecutable() {
        val config=NativeModelSchema.decode(motion(),"""{"components":{"schemas":{"Input":{"required":["bad"],"properties":{"bad":null}}}}}""")
        assertTrue("bad" in config.unsupportedRequired)
    }
    @Test fun cacheEvictsLeastRecentlyUsed() {
        val cache=BoundedCache<String,Int>(2);cache["a"]=1;cache["b"]=2;assertEquals(1,cache["a"]);cache["c"]=3
        assertNull(cache["b"]);assertEquals(2,cache.size())
    }
    @Test fun diagnosticRedactsFreeTextWithoutChangingSubmissionValues() {
        val model=motion().copy(parameters=listOf(ModelParameter("negative_prompt","Prompt negativo",ParameterType.TEXT),ModelParameter("sound","Sonido",ParameterType.BOOLEAN)))
        val original=mapOf("negative_prompt" to "Texto privado", "sound" to "false")
        val preview=FormContract.previewValues(model,original)
        assertFalse(preview["negative_prompt"]!!.contains("Texto privado"));assertEquals("false",preview["sound"])
        assertEquals("Texto privado",original["negative_prompt"])
    }
    @Test fun byteProgressMatchesBodyAndDoesNotAlterPayload() {
        val bytes=ByteArray(180000) {(it%251).toByte()};val events=mutableListOf<Pair<Long,Long>>()
        val body=com.vidadomu.nexus.core.network.ProgressRequestBody(bytes.toRequestBody()) {sent,total->events+=sent to total}
        val buffer=Buffer();body.writeTo(buffer)
        assertArrayEquals(bytes,buffer.readByteArray());assertEquals(bytes.size.toLong() to bytes.size.toLong(),events.last());assertTrue(body.isOneShot())
    }
}
