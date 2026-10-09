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

    @Test fun smallAmountsUseEurosWithTwoDecimals() {
        assertEquals("0,01 €", currencyManager.formatUsd(0.012))
        assertEquals("0,12 €", currencyManager.formatUsd(0.14))
        assertEquals("2,17 €", currencyManager.formatUsd(2.50))
    }
    @Test fun allPresentationAliasesAgree() {
        val amount = 9.543
        assertEquals(currencyManager.formatUsd(amount), currencyManager.formatBalance(amount))
        assertEquals(currencyManager.formatUsd(amount), currencyManager.formatCost(amount))
        assertEquals(currencyManager.formatUsd(amount), currencyManager.formatUnitPrice(amount))
    }
    @Test fun modelPriceRetainsTheActualUnit() {
        assertEquals("0,01 € / imagen", currencyManager.formatModelPricing(.012,"img","unused"))
        assertEquals("0,12 € / 5 s", currencyManager.formatModelPricing(.14,"5 s","unused"))
        assertEquals("Gratuito", currencyManager.formatModelPricing(null,null,"Gratuito"))
    }
    @Test fun atlasValuesUseTheSamePresentation() {
        assertEquals("0,12 €", currencyManager.formatAtlasApiPrice("0.14"))
        assertEquals(currencyManager.formatAtlasApiPrice("0.14"), currencyManager.formatAtlasApiPrice(.14))
        assertEquals("Coste no disponible", currencyManager.formatAtlasApiPrice("bad"))
    }
    @Test fun selectedUsdCurrencyUsesTwoDecimals() {
        currencyManager.setPreference(CurrencyPreference.USD)
        assertEquals("0,01 US$",currencyManager.formatUsd(.012))
        assertEquals("1.622,28 US$",currencyManager.formatBalance(1622.28))
    }
    @Test fun savedEuroPreferencesRemainCompatible() {
        currencyManager.setPreference(CurrencyPreference.EUR_STRICT)
        assertEquals("0,01 €",currencyManager.formatUsd(.012))
        assertEquals("EUR_STRICT",fakeStorage.preference)
    }
    @Test fun creditPrecisionAndGrouping() {
        assertEquals("1.622 créditos",currencyManager.formatCredits(1622.28))
        assertEquals("0 créditos",currencyManager.formatCredits(0.0))
    }
    @Test fun detailsKeepUsdPrecision() {
        assertEquals("0,0600 USD",currencyManager.formatUsdDetails(.06))
    }
    @Test fun invalidValuesDoNotBecomeZeroOrInfinity() {
        for(value in listOf(Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY)) {
            assertEquals("—",currencyManager.formatUsd(value))
            assertEquals("—",currencyManager.formatCredits(value))
            assertEquals("—",currencyManager.formatUsdDetails(value))
        }
    }
    @Test fun formattingIsIndependentOfDeviceLocale() {
        val original=java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.US)
            assertEquals("8,27 €",currencyManager.formatBalance(9.543))
        } finally { java.util.Locale.setDefault(original) }
    }
    @Test fun testOfflineFallbackRate() {
        assertEquals(.8666,CurrencyManager(FakeSecureStorage(rate=0.0)).state.value.rate,.00001)
    }
}
