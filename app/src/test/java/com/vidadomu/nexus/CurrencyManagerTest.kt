package com.vidadomu.nexus

import com.vidadomu.nexus.core.currency.CurrencyManager
import com.vidadomu.nexus.core.currency.CurrencyPreference
import com.vidadomu.nexus.core.security.ISecureStorage
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FakeSecureStorage(
    var preference: String = "EUR_SMART",
    var rate: Double = 0.86663,
    var timestamp: Long = 1726000000000L
) : ISecureStorage {
    private val providerKeys = mutableMapOf<String, String>()
    override fun saveProviderApiKey(providerId: String, apiKey: String) { providerKeys[providerId] = apiKey }
    override fun getProviderApiKey(providerId: String): String = providerKeys[providerId].orEmpty()
    override fun hasProviderApiKey(providerId: String): Boolean = getProviderApiKey(providerId).isNotBlank()
    override fun clearProviderApiKey(providerId: String) { providerKeys.remove(providerId) }
    override fun getMaskedProviderApiKey(providerId: String): String = getProviderApiKey(providerId)
    override fun saveAtlasApiKey(apiKey: String) {}
    override fun getAtlasApiKey(): String = ""
    override fun hasAtlasApiKey(): Boolean = false
    override fun clearAtlasApiKey() {}
    override fun getMaskedAtlasApiKey(): String = ""
    override fun isMockModeEnabled(): Boolean = false
    override fun setMockModeEnabled(enabled: Boolean) {}
    override fun getCurrencyPreference(): String = preference
    override fun setCurrencyPreference(pref: String) { preference = pref }
    override fun getUsdToEurRate(): Double = rate
    override fun setUsdToEurRate(rate: Double) {
        this.rate = rate
        this.timestamp = System.currentTimeMillis()
    }
    override fun getExchangeRateLastUpdated(): Long = timestamp
    override fun getCachedBalance(): com.vidadomu.nexus.core.model.AccountBalance? = null
    override fun saveCachedBalance(balance: com.vidadomu.nexus.core.model.AccountBalance) {}
    private val lastModels = mutableMapOf<com.vidadomu.nexus.core.model.Modality, String>()
    override fun getLastModelId(modality: com.vidadomu.nexus.core.model.Modality): String? = lastModels[modality]
    override fun setLastModelId(modality: com.vidadomu.nexus.core.model.Modality, modelId: String) { lastModels[modality] = modelId }
}

class CurrencyManagerTest {

    private lateinit var fakeStorage: FakeSecureStorage
    private lateinit var currencyManager: CurrencyManager

    @Before
    fun setUp() {
        fakeStorage = FakeSecureStorage(
            preference = "EUR_SMART",
            rate = 0.86663,
            timestamp = 1726000000000L
        )
        currencyManager = CurrencyManager(fakeStorage)
    }

    @Test
    fun testSmartEurFormatting_SmallAmountInCentimos() {
        // 0.012 USD * 0.86663 = 0.01039956 EUR (< 1.00 EUR) -> ~1,04 cént.
        val result = currencyManager.formatUsd(0.012)
        assertTrue("Sub-euro amounts should format with cént.", result.contains("cént."))
        assertTrue("Expected ~1,04 cént, got: $result", result.contains("1,04") || result.contains("1.04"))
    }

    @Test
    fun testSmartEurFormatting_SubEuroCostInCentimos() {
        // 0.14 USD * 0.86663 = 0.1213 EUR -> ~12,1 cént.
        val result = currencyManager.formatUsd(0.14)
        assertTrue("0.14 USD should format with cént.", result.contains("cént."))
        assertTrue("Expected ~12,1 cént, got: $result", result.contains("12,1") || result.contains("12.1"))
    }

    @Test
    fun testSmartEurFormatting_LargeAmountInEuros() {
        // 2.50 USD * 0.86663 = 2.166 EUR (>= 1.00 EUR) -> ~2,17 €
        val result = currencyManager.formatUsd(2.50)
        assertTrue("Amounts >= 1.00 EUR should show euro symbol (€)", result.contains("€"))
        assertFalse("Amounts >= 1.00 EUR should not contain cént.", result.contains("cént."))
        assertTrue("Expected ~2,17 €, got: $result", result.contains("2,17") || result.contains("2.17"))
    }

    @Test
    fun testFormatModelPricing_WithBasePrice() {
        val formatted = currencyManager.formatModelPricing(0.012, "img", "$0.012 / image")
        assertTrue("Formatted pricing should contain img", formatted.contains("/ img"))
        assertTrue("Formatted pricing should be in céntimos", formatted.contains("cént."))
        assertTrue("Formatted pricing should start with ~", formatted.startsWith("~"))
    }

    @Test
    fun testFormatModelPricing_NullFallback() {
        val fallback = "Gratuito"
        val formatted = currencyManager.formatModelPricing(null, null, fallback)
        assertEquals(fallback, formatted)
    }

    @Test
    fun testFormatAtlasApiPrice() {
        val formatted = currencyManager.formatAtlasApiPrice("0.14")
        assertTrue("Atlas API price should show céntimos", formatted.contains("cént."))

        val formattedDouble = currencyManager.formatAtlasApiPrice(0.14)
        assertEquals(formatted, formattedDouble)
    }

    @Test
    fun testUsdPreferenceMode() {
        currencyManager.setPreference(CurrencyPreference.USD)
        val result = currencyManager.formatUsd(0.012)
        assertTrue("Should start with $, got: $result", result.startsWith("$"))
        assertTrue("Should contain 0,012 or 0.012, got: $result", result.contains("0,012") || result.contains("0.012"))

        val resultLarge = currencyManager.formatUsd(1.50)
        assertTrue("Should start with $, got: $resultLarge", resultLarge.startsWith("$"))
        assertTrue("Should contain 1,50 or 1.50, got: $resultLarge", resultLarge.contains("1,50") || resultLarge.contains("1.50"))
    }

    @Test
    fun testStrictEurPreferenceMode() {
        currencyManager.setPreference(CurrencyPreference.EUR_STRICT)
        val result = currencyManager.formatUsd(0.012)
        assertTrue("Strict mode should always show €", result.contains("€"))
        assertFalse("Strict mode should never show cént.", result.contains("cént."))
    }

    @Test
    fun testOfflineFallbackRate() {
        val emptyStorage = FakeSecureStorage(rate = 0.0)
        val fallbackManager = CurrencyManager(emptyStorage)
        assertEquals(0.8666, fallbackManager.state.value.rate, 0.001)
    }
}
