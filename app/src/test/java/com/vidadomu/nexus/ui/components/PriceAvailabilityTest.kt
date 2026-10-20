package com.vidadomu.nexus.ui.components

import org.junit.Assert.*
import org.junit.Test

class PriceAvailabilityTest {
    @Test fun missingQuoteDoesNotClaimNetworkFailure() {
        assertEquals(PriceAvailability.UNAVAILABLE,priceAvailability(false,false))
        assertFalse(PriceAvailability.UNAVAILABLE.label.contains("gratis",true))
    }
    @Test fun failedRequestIsDifferentFromMissingTariff() {assertEquals(PriceAvailability.ERROR,priceAvailability(false,true))}
    @Test fun retryLoadingSupersedesPreviousFailure() {assertEquals(PriceAvailability.LOADING,priceAvailability(true,true))}
    @Test fun knownZeroIsStillARealQuote() {assertEquals(PriceAvailability.KNOWN,EstimateUi("0",usd=0.0).availability)}
    @Test fun creditsRemainPricedWithoutAnInventedDollarConversion() {
        val estimate=EstimateUi("3 créditos",hasPrice=true)
        assertEquals(PriceAvailability.KNOWN,estimate.availability);assertNull(estimate.usd)
    }
}
