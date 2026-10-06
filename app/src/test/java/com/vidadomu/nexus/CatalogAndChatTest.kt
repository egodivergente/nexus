package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.atlas.*
import com.vidadomu.nexus.ui.models.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CatalogAndChatTest {
    private val base=AtlasModelCatalog.curatedModels.first()
    @Test fun separatesReferenceEditAndTextImage() {
        assertEquals(CreationUse.IMAGE_REFS,modelUse(base.copy(id="test/edit",inputMode=ModelInputMode.MULTI_IMAGE)))
        assertEquals(CreationUse.IMAGE_EDIT,modelUse(base.copy(id="test/image-to-image",inputMode=ModelInputMode.SINGLE_IMAGE)))
        assertEquals(CreationUse.IMAGE_TEXT,modelUse(base.copy(id="test/text-to-image",inputMode=ModelInputMode.TEXT_ONLY)))
    }
    @Test fun catalogBasePriceIsNotAClipQuote() {
        val model=base.copy(modalities=listOf(Modality.VIDEO),basePriceUsd=0.2,priceUnit="unidad sin especificar",priceUnitVerified=false)
        assertFalse(model.priceUnitVerified)
        assertEquals("sin unidad confirmada", billingUnit(model))
    }
    @Test fun latestFourThenOlderKeepsEveryId() {
        val all=(1..8).map { base.copy(id="m"+it,displayName="Modelo v"+it,versionNumeric=it.toDouble()) }
        val recent=recentModels(all)
        assertEquals(listOf("m8","m7","m6","m5"),recent.take(4).map{it.id})
        assertEquals(8,(recent.take(4)+recent.drop(4)).distinctBy{it.id}.size)
    }
    @Test fun semanticVersionTenIsNewerThanSeven() {
        assertEquals("Modelo 4.10",recentModels(listOf(base.copy(displayName="Modelo 4.7"),base.copy(displayName="Modelo 4.10"))).first().displayName)
    }
    @Test fun chatMessagesAndReplyUseChatProtocol() {
        assertEquals("user",AtlasChatProtocol.userMessages("Hola")[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("Hola",AtlasChatProtocol.userMessages("Hola")[0].jsonObject["content"]!!.jsonPrimitive.content)
        val root=Json.parseToJsonElement("""{"id":"chat-1","choices":[{"message":{"role":"assistant","content":"Respuesta de prueba"}}]}""").jsonObject
        assertEquals("Respuesta de prueba",AtlasChatProtocol.responseText(root))
        assertNull(AtlasChatProtocol.responseText(buildJsonObject{put("id","no-text")}))
    }
    @Test fun llmDoesNotDependOnMissingMediaSchema() = runBlocking {
        val model=base.copy(modalities=listOf(Modality.CHAT),schemaUrl=null,chatSamplingParameters=listOf("max_tokens","temperature"))
        val config=NativeModelSchema.load(model)
        assertTrue(config.unsupportedRequired.isEmpty())
        assertEquals(ModelInputMode.TEXT_ONLY,config.model.inputMode)
        assertEquals("1024",config.model.parameters.first{it.key=="max_tokens"}.defaultValue)
        assertNull(NativeModelSchema.validate(config.model,mapOf("prompt" to "Hola")))
    }
}
