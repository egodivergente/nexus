package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.atlas.*
import kotlinx.serialization.json.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PublishedTariffTest {
    private fun model(): NexusModel {
        val price = Json.parseToJsonElement(javaClass.getResource("/native/kling-motion-price.json")!!.readText()).jsonObject
        val mapped = AtlasModelMapper.mapJsonObjectToNexusModel(JsonObject(price + ("type" to JsonPrimitive("Video"))))!!
        // These arithmetic tests supply an explicit verified tariff. The original catalog
        // fixture lacks a unit and must not independently establish per-second billing.
        return NativeModelSchema.decode(mapped,javaClass.getResource("/native/kling-motion.json")!!.readText()).model
            .copy(priceUnit="seg",priceUnitVerified=true)
    }
    @Test fun liveCatalogTariffUsesDiscountedRateAndWebUnit() {
        val m=model()
        assertEquals(.107,m.basePriceUsd!!,1e-10)
        assertEquals("seg",m.priceUnit)
        assertTrue(m.priceUnitVerified)
        val quote=PublishedTariff.quote(m)!!
        assertEquals(.107,quote.usd,1e-10)
        assertEquals("s",quote.unit)
    }
    @Test fun localSourceDurationGivesLabelledEstimateWithoutUpload() = runBlocking {
        val m=model()
        val quote=AtlasPriceQuote.fetch(m,mapOf("character_orientation" to "video"),1,8.25)!!
        assertEquals(.88275,quote.usd,1e-10)
        assertEquals(.107,quote.unitUsd!!,1e-10)
        assertNull(quote.unit)
        assertTrue(quote.configuration.contains("estimación"))
        assertTrue(quote.configuration.contains("8.25"))
    }
    @Test fun unknownDurationKeepsRateInsteadOfPretendingTotal() {
        for(duration in listOf(null,0.0,-1.0,Double.NaN,Double.POSITIVE_INFINITY)) {
            assertEquals("s",PublishedTariff.quote(model(),duration)!!.unit)
        }
    }
    @Test fun fixedOutputDurationNeverMultipliesSourceLength() {
        val m=model().copy(parameters=listOf(ModelParameter("duration","Duración",ParameterType.INTEGER)))
        assertEquals("s",PublishedTariff.quote(m,30.0)!!.unit)
    }
    @Test fun multiVideoNeverGuessesTotal() {
        assertEquals("s",PublishedTariff.quote(model().copy(videosArrayField="videos"),30.0)!!.unit)
    }
    @Test fun explicitCatalogUnitWinsOverVideoDefault() {
        val m=AtlasModelMapper.mapJsonObjectToNexusModel(Json.parseToJsonElement("""{"model":"test/video","type":"Video","price":{"actual":{"base_price":"0.5","unit":"generation"}}}""").jsonObject)!!
        assertEquals("gen",m.priceUnit)
        assertEquals("generación",PublishedTariff.quote(m,8.0)!!.unit)
    }
    @Test fun absentRateOrUnknownUnitDoesNotInventPrice() {
        assertNull(PublishedTariff.quote(model().copy(basePriceUsd=null)))
        assertNull(PublishedTariff.quote(model().copy(priceUnitVerified=false)))
        assertNull(PublishedTariff.quote(model().copy(basePriceUsd=Double.NaN)))
    }
    @Test fun missingVideoUnitNeverDefaultsToSeconds() {
        val raw=Json.parseToJsonElement(javaClass.getResource("/native/kling-motion-price.json")!!.readText()).jsonObject
        val mapped=AtlasModelMapper.mapJsonObjectToNexusModel(JsonObject(raw + ("type" to JsonPrimitive("Video"))))!!
        assertFalse(mapped.priceUnitVerified)
        assertNull(PublishedTariff.quote(mapped))
    }
    @Test fun directCalculatorQuoteIsAlreadyAWholeTotal() {
        val q=AtlasPriceQuote.decode("""{"code":200,"data":{"price":"0.535"}}""","5 s")
        assertNull(q.unit)
        assertNull(q.unitUsd)
        assertEquals(.535,q.usd,1e-10)
    }
}
