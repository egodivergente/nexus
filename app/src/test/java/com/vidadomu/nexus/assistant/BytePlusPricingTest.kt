package com.vidadomu.nexus.assistant

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.*

class BytePlusPricingTest {
    private val now = 1_000_000_000L
    private fun quote(currency: String = "USD", updated: Long = now, input: JsonElement = JsonPrimitive("1.2")) = buildJsonObject {
        put("updated_at", updated)
        put("rows", buildJsonArray { add(buildJsonObject {
            put("model", "fixture-model");put("currency", currency)
            put("input_usd_per_million", input);put("output_usd_per_million", "3.4")
        }) })
    }
    @Test fun exactModelAndSixDigitVersionUseQuote() {
        assertEquals(1.2 to 3.4, cachedBytePlusRates("fixture-model", quote(), now))
        assertEquals(1.2 to 3.4, cachedBytePlusRates("fixture-model-260101", quote(), now))
    }
    @Test fun unrelatedAliasesAndEndpointsNeverBorrowPrice() {
        for (model in listOf("fixture-model-fast", "fixture-model-260101-extra", "ep-fixture", "other"))
            assertNull(cachedBytePlusRates(model, quote(), now))
    }
    @Test fun missingStaleFutureAndForeignCurrencyAreUnavailable() {
        assertNull(cachedBytePlusRates("fixture-model", null, now))
        assertNull(cachedBytePlusRates("fixture-model", quote(updated = now - 86_400_001), now))
        assertNull(cachedBytePlusRates("fixture-model", quote(updated = now + 60_001), now))
        assertNull(cachedBytePlusRates("fixture-model", quote(currency = "CNY"), now))
    }
    @Test fun malformedAndNonFinitePricesAreUnavailable() {
        assertNull(cachedBytePlusRates("fixture-model", quote(input = buildJsonObject {}), now))
        assertNull(cachedBytePlusRates("fixture-model", quote(input = JsonPrimitive("NaN")), now))
        assertNull(cachedBytePlusRates("fixture-model", quote(input = JsonPrimitive("-1")), now))
    }
}
