package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.atlas.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class FiveSecondPriceTest {
    private val base=AtlasModelCatalog.curatedModels.first().copy(modalities=listOf(Modality.VIDEO),basePriceUsd=.071,priceUnit="seg",priceUnitVerified=true)
    private fun model(name:String): NexusModel = NativeModelSchema.decode(base.copy(id="test/"+name),javaClass.getResource("/native/$name.json")!!.readText()).model
    @Test fun textAndImageStartAtFiveSecondsWithoutSound() {
        for(name in listOf("kling-t2v","kling-i2v")) {
            val m=model(name); val values=VideoPricePreview.initialValues(m)
            assertEquals("5",values["duration"])
            assertEquals("false",values["sound"])
            assertFalse(VideoPricePreview.requiresVideo(m))
            assertTrue(VideoPricePreview.supportsFiveSeconds(m))
            val payload=AtlasPriceQuote.payload(m,values,if(name=="kling-i2v") 1 else 0)
            assertEquals(5,payload["duration"]!!.jsonPrimitive.int)
            assertFalse(payload["sound"]!!.jsonPrimitive.boolean)
            assertFalse(payload.containsKey("video"))
        }
    }
    @Test fun fiveSecondsOverridesTenSecondProviderDefault() {
        val m=model("kling-t2v").let { it.copy(parameters=it.parameters.map { p -> if(p.key=="duration") p.copy(defaultValue="10") else p }) }
        assertEquals("5",VideoPricePreview.initialValues(m)["duration"])
    }
    @Test fun unsupportedFiveSecondsIsEquivalentNotAnInvalidQuote() {
        val m=model("kling-t2v").let { it.copy(parameters=it.parameters.map { p -> if(p.key=="duration") p.copy(defaultValue="6",options=listOf("6","10")) else p }) }
        assertFalse(VideoPricePreview.supportsFiveSeconds(m))
        assertEquals("6",VideoPricePreview.initialValues(m)["duration"])
        val quote=VideoPricePreview.fallback(m)!!
        assertEquals(.355,quote.usd,1e-10)
        assertEquals(5.0,quote.clipSeconds!!,0.0)
        assertTrue(quote.configuration.contains("Equivalente"))
        assertTrue(quote.configuration.contains("6, 10"))
    }
    @Test fun liveQuotesIncludeSoundDifferenceForBothRoutes() {
        val fixtures=Json.parseToJsonElement(javaClass.getResource("/native/five-second-quotes.json")!!.readText()).jsonArray
        assertEquals(4,fixtures.size)
        for(f in fixtures) {
            val request=f.jsonObject["request"]!!.jsonObject
            val quote=AtlasPriceQuote.decode(f.jsonObject["response"].toString(),"5 s")
            assertEquals(if(request["sound"]!!.jsonPrimitive.boolean) .5355 else .357,quote.usd,1e-10)
            assertNull(quote.unit) // already whole clip; never multiply by five again
        }
    }
    @Test fun selectedDurationAndAudioRemainInLiveQuote() {
        val m=model("kling-i2v")
        val values=VideoPricePreview.initialValues(m)+mapOf("duration" to "10","sound" to "true")
        val payload=AtlasPriceQuote.payload(m,values,1)
        assertEquals(10,payload["duration"]!!.jsonPrimitive.int)
        assertTrue(payload["sound"]!!.jsonPrimitive.boolean)
        assertEquals(10.0,VideoPricePreview.fallback(m,values)!!.clipSeconds!!,0.0)
    }
    @Test fun motionControlIsNotQuotedAsTextToVideo() {
        val m=NativeModelSchema.decode(base,javaClass.getResource("/native/kling-motion.json")!!.readText()).model
        assertTrue(VideoPricePreview.requiresVideo(m))
        assertEquals("true",VideoPricePreview.initialValues(m)["keep_original_sound"])
        assertEquals("s",VideoPricePreview.fallback(m)!!.unit)
        assertNull(VideoPricePreview.fallback(m)!!.clipSeconds)
    }
    @Test fun numericBoundsDoNotAllowUnsupportedFiveSeconds() {
        val p=ModelParameter("duration","Duración",ParameterType.INTEGER,nativeType="integer",minValue=6f,maxValue=12f)
        assertFalse(VideoPricePreview.supportsFiveSeconds(base.copy(parameters=listOf(p))))
        assertTrue(VideoPricePreview.supportsFiveSeconds(base.copy(parameters=listOf(p.copy(minValue=3f)))))
    }
    @Test fun fallbackStillShowsAmountWhenCalculatorCannotRespond() {
        val quote=VideoPricePreview.fallback(model("kling-t2v"))!!
        assertEquals(.355,quote.usd,1e-10)
        assertEquals(5.0,quote.clipSeconds!!,0.0)
        assertTrue(quote.configuration.contains("tarifa base"))
    }
    @Test fun optionalSourceVideoDoesNotExcludeTextImagePreview() {
        val m=model("kling-t2v").copy(videoField="video",supportsVideoInput=true)
        assertFalse(VideoPricePreview.requiresVideo(m))
        assertEquals(5.0,VideoPricePreview.fallback(m)!!.clipSeconds!!,0.0)
    }
}
