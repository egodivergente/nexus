package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.atlas.*
import com.vidadomu.nexus.provider.flow.FlowReferences
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CanvasInputPlanTest {
    private val base = NexusModel("fixture", "Fixture", "atlas_cloud", organization="Fixture",
        modalities=listOf(Modality.IMAGE), capabilities=emptyList(), parameters=emptyList(),
        supportsReferenceImages=true, supportsImageInput=true, maxImages=12)
    private fun configured(props: String, required: String) = NativeModelSchema.decode(base,
        """{"components":{"schemas":{"Input":{"properties":$props,"required":$required}}}}""").model

    @Test fun upscaleSchemaOverridesStaleReferenceFlagsAndDoesNotSendAPrompt() {
        val model = configured("""{"image":{"type":"string"},"scale":{"type":"integer","default":2}}""", """["image"]""")
        val field = CanvasInputPlan.fields(model).single()
        assertEquals("Imagen de origen",field.label)
        assertEquals(MediaSlotType.PRIMARY_IMAGE,field.slot)
        assertEquals(1,field.maximum)
        assertTrue(field.required)
        assertFalse(model.promptAccepted)
        val input=MediaInput(Modality.IMAGE,"file:///image.png","image/png",remoteUrl="https://example.invalid/image.png",slotType=field.slot)
        val request=NativeGenerationRequest.build(model,Modality.IMAGE,"Old prompt",emptyMap(),listOf(input),"test")
        val payload=AtlasGenerationPayload.build(request)
        assertFalse(payload.containsKey("prompt"))
        assertEquals("https://example.invalid/image.png",payload["image"]?.jsonPrimitive?.content)
    }

    @Test fun multiImageEditKeepsNativeArrayAndRemainingSelectionCount() {
        val model=configured("""{"prompt":{"type":"string"},"images":{"type":"array","maxItems":4,"items":{"type":"string"}}}""","""["prompt","images"]""")
        val field=CanvasInputPlan.fields(model).single()
        val media=(1..2).map {AttachedMedia(uriString="file:///$it.png",mimeType="image/png",slotType=field.slot)}
        assertEquals(2,field.remaining(media))
        val request=NativeGenerationRequest.build(model,Modality.IMAGE,"Edit",emptyMap(),media.map {
            MediaInput(Modality.IMAGE,it.uriString,it.mimeType,remoteUrl="https://example.invalid/${it.id}.png",slotType=it.slotType)
        },"test")
        assertEquals(2,AtlasGenerationPayload.build(request)["images"]!!.jsonArray.size)
    }

    @Test fun declaredContractHasNoExtraGenericReferenceSlot() {
        val source=ContractMediaField("source", "Imagen original", "image/*",true)
        val model=base.copy(contractEndpoint="/upscale",contractMedia=listOf(source),imagesArrayField="stale",inputMode=ModelInputMode.MULTI_IMAGE)
        val field=CanvasInputPlan.fields(model).single()
        assertEquals(source,field.contract)
        assertEquals(1,field.maximum)
        assertFalse(field.contains(AttachedMedia(uriString="file:///other.png",mimeType="image/png",slotType=field.slot,fieldKey="other")))
    }

    @Test fun imageAndMotionVideoHaveIndependentInputsAndLimits() {
        val model=configured("""{"image":{"type":"string"},"video":{"type":"string"}}""","""["image","video"]""")
        val fields=CanvasInputPlan.fields(model)
        assertEquals(listOf("image/*","video/*"),fields.map {it.mime})
        assertTrue(fields.all {it.required && it.maximum==1})
    }

    @Test fun frameRolesAreNotCollapsedIntoReferenceImages() {
        val model=configured("""{"first_frame_image":{"type":"string"},"last_frame_image":{"type":"string"}}""","""["first_frame_image"]""")
        val fields=CanvasInputPlan.fields(model)
        assertEquals(listOf(MediaSlotType.PRIMARY_IMAGE,MediaSlotType.LAST_FRAME),fields.map {it.slot})
        assertEquals(listOf(true,false),fields.map {it.required})
    }

    @Test fun flowVideoOnlyShowsInputsForItsSelectedMode() {
        val model=FlowReferences.model(Modality.VIDEO)
        assertTrue(CanvasInputPlan.fields(model,mapOf("mode" to "Texto")).isEmpty())
        assertEquals(listOf("initial_frame","end_frame"),CanvasInputPlan.fields(model,mapOf("mode" to "Fotogramas")).map {it.key})
        val refs=CanvasInputPlan.fields(model,mapOf("mode" to "Referencias")).single()
        assertEquals("reference_ids",refs.key)
        assertTrue(refs.maximum>1)
    }

    @Test fun textOnlySchemaDoesNotOfferMediaEvenWithStaleCatalogHints() {
        val model=configured("""{"prompt":{"type":"string"}}""","""["prompt"]""")
        assertTrue(CanvasInputPlan.fields(model).isEmpty())
    }

    @Test fun unboundedArraysStayMultipleWhilePickerBatchesAreBounded() {
        val model=configured("""{"images":{"type":"array","items":{"type":"string"}}}""","""["images"]""")
        val field=CanvasInputPlan.fields(model).single()
        assertTrue(field.maximum>1)
        assertEquals(CanvasInputPlan.PICKER_BATCH,minOf(CanvasInputPlan.PICKER_BATCH,field.remaining(emptyList())))
    }

    @Test fun aPublishedExactMimeDoesNotAcceptEveryImageFormat() {
        val mask=base.copy(contractEndpoint="/edit",contractMedia=listOf(ContractMediaField("mask","Máscara PNG","image/png",true)))
        val field=CanvasInputPlan.fields(mask).single()
        assertTrue(field.acceptsMime("image/png"))
        assertFalse(field.acceptsMime("image/jpeg"))
        assertFalse(field.acceptsMime("video/mp4"))
    }
}
