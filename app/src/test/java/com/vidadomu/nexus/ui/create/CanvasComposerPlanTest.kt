package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.core.model.*
import org.junit.Assert.*
import org.junit.Test

class CanvasComposerPlanTest {
    private fun video(id: String) = NexusModel(id,"MiniMax very long catalog name $id","atlas_cloud",
        organization="MiniMax",modalities=listOf(Modality.VIDEO),capabilities=listOf(ModelCapability.TEXT_TO_VIDEO),parameters=emptyList())

    @Test fun minimaxVersionsAndVariantsRemainDistinctWithoutChangingIds() {
        val expected=mapOf("minimax/h3-max/text-to-video" to "H3 Max",
            "minimax/h3-max-turbo/image-to-video" to "H3 Max Turbo",
            "minimax/h3-developer/text-to-video" to "H3 Developer",
            "minimax/hailuo-02/t2v-pro" to "Hailuo 02 Pro",
            "minimax/hailuo-2.3/i2v-pro" to "Hailuo 2.3 Pro",
            "minimax/hailuo-2.3/fast" to "Hailuo 2.3 Fast")
        expected.forEach { (id,title) ->
            val m=video(id);val identity=canvasModelIdentity(m)
            assertEquals(title,identity.title);assertEquals(id,identity.exactId);assertEquals(id,m.id)
            assertTrue(identity.detail.contains("Atlas"))
        }
        assertEquals(expected.size,expected.keys.map {canvasModelIdentity(video(it)).title}.distinct().size)
    }
    @Test fun byteplusUsesOwnProviderIdentityAndExactConfiguredEndpoint() {
        val identity=canvasModelIdentity(video("seedance").copy(providerId=BYTEPLUS_MEDIA_PROVIDER,providerModelId="ep-user-configured"))
        assertTrue(identity.detail.contains("BytePlus"));assertEquals("ep-user-configured",identity.exactId)
    }
    @Test fun videoBatchRetainsVideoAndUsesOneOutputPerIndependentRequest() {
        val count=ModelParameter("num_videos","VÃ­deos",ParameterType.INTEGER,"2",minValue=1f,maxValue=4f)
        val source=CreateUiState(selectedModel=video("minimax/h3/text-to-video"),selectedModality=Modality.VIDEO,
            prompt="Una cÃ¡mara recorre un invernadero",parameters=mapOf("num_videos" to "4"),schemaReady=true)
        val targets=listOf(video("minimax/h3-fast/text-to-video").copy(providerId="fixture"),video("minimax/hailuo-2.3/t2v-pro").copy(providerId="fixture"),
            com.vidadomu.nexus.provider.atlas.NativeModelSchema.decode(video("minimax/h3/text-to-video"),
                """{"components":{"schemas":{"Input":{"required":["prompt"],"properties":{"prompt":{"type":"string"},"num_videos":{"type":"integer","minimum":1,"maximum":4,"default":1}}}}}}""").model)
        targets.forEach {target ->
            val prepared=ComparisonPlan.adapt(source,target.copy(parameters=listOf(count)))
            assertEquals(Modality.VIDEO,prepared.state.selectedModality)
            assertEquals(source.prompt,prepared.state.prompt)
            assertEquals("1",prepared.state.parameters[count.key])
        }
        val image=targets.first().copy(modalities=listOf(Modality.IMAGE),capabilities=listOf(ModelCapability.TEXT_TO_IMAGE))
        assertThrows(IllegalArgumentException::class.java) {ComparisonPlan.adapt(source,image)}
    }
    @Test fun videoReferencesAreMappedToDeclaredImageRoleAndOtherFilesAreRejected() {
        val m=video("video").copy(providerId="fixture",contractEndpoint="/video",contractMedia=listOf(ContractMediaField("image","Inicio","image/*",true)),
            capabilities=listOf(ModelCapability.IMAGE_TO_VIDEO))
        val media=AttachedMedia("one","file:///one.png","image/png",MediaSlotType.REFERENCE_IMAGE,fieldKey="old",remoteUrl="https://old.invalid/one")
        val source=CreateUiState(selectedModel=m,selectedModality=Modality.VIDEO,prompt="Avanza lentamente",schemaReady=true,attachedMedia=listOf(media))
        val prepared=ComparisonPlan.adapt(source,m)
        assertEquals("image",prepared.state.attachedMedia.single().fieldKey)
        assertNull(prepared.state.attachedMedia.single().remoteUrl)
        assertThrows(IllegalArgumentException::class.java) {ComparisonPlan.adapt(source.copy(attachedMedia=listOf(media.copy(mimeType="video/mp4"))),m)}
    }
    @Test fun nativeVideoQuantityHonorsExactOptionsAndBounds() {
        val p=ModelParameter("num_videos","VÃ­deos",ParameterType.INTEGER,"1",minValue=1f,maxValue=2f)
        assertEquals(p,nativeCountParameter(video("video").copy(parameters=listOf(p))))
        assertTrue(nativeCountAllowed(p,2));assertFalse(nativeCountAllowed(p,4))
        assertFalse(nativeCountAllowed(p.copy(options=listOf("1","4")),2))
    }
    @Test fun restoredNativeQuantityCannotBeMultipliedByAnOldRepeatedChoice() {
        val p=ModelParameter("num_videos","Vídeos",ParameterType.INTEGER,"1",minValue=1f,maxValue=2f)
        assertEquals(CanvasQuantity(8,true),canvasQuantity(p,mapOf(p.key to "1"),8))
        assertEquals(CanvasQuantity(2,false),canvasQuantity(p,mapOf(p.key to "2"),8))
        assertEquals(CanvasQuantity(2,false),canvasQuantity(p,mapOf(p.key to "2"),1))
        assertEquals(CanvasQuantity(4,true),canvasQuantity(null,emptyMap(),4))
    }
    @Test fun repeatedPriceShowsTotalOnlyWhenKnownAndNeverPresentsUnitCreditsAsTheTotal() {
        val money=com.vidadomu.nexus.ui.components.EstimateUi("2 USD",usd=2.0,isRequestTotal=true)
        assertEquals("8.0 USD",canvasQuantityCost(money,CanvasQuantity(4,true)) {"$it USD"})
        assertEquals("2 USD",canvasQuantityCost(money,CanvasQuantity(4,false)) {"$it USD"})
        val credits=com.vidadomu.nexus.ui.components.EstimateUi("10 créditos",hasPrice=true)
        assertEquals("10 créditos/u",canvasQuantityCost(credits,CanvasQuantity(4,true)) {"$it USD"})
        val missing=com.vidadomu.nexus.ui.components.EstimateUi("Precio por confirmar")
        assertEquals(missing.amount,canvasQuantityCost(missing,CanvasQuantity(4,true)) {"$it USD"})
    }
}
