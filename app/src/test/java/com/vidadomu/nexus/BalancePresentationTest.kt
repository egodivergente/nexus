package com.vidadomu.nexus

import com.vidadomu.nexus.core.currency.CurrencyManager
import com.vidadomu.nexus.ui.components.presentBalance
import org.junit.Assert.*
import org.junit.Test

class BalancePresentationTest {
    private val currency=CurrencyManager(FakeSecureStorage())
    @Test fun existingProviderStringsUseTheSelectedCurrency() {
        assertEquals("8,27 €",presentBalance("9.5430 USD",currency))
        assertEquals("8,27 €",presentBalance("9,5430 USD",currency))
        assertEquals("1.622 créditos",presentBalance("1622.28 cr",currency))
    }
    @Test fun unavailableBalancesDoNotBecomeAnInventedZero() {
        assertEquals("No disponible",presentBalance("No disponible",currency))
        assertEquals("Consultando…",presentBalance("Consultando…",currency))
    }
    @Test fun groupedCreditBalancesAreReadableAcrossProviderLocales() {
        assertEquals("1.622 créditos",presentBalance("1.622,28 cr",currency))
        assertEquals("1.622 créditos",presentBalance("1,622.28 credits",currency))
    }
    @Test fun technicalFailuresStayOutOfBalanceLabels() {
        assertEquals("Saldo no disponible",presentBalance("Unable to resolve host provider.example",currency))
        assertEquals("Saldo no disponible",presentBalance("1.2.3 USD",currency))
    }
}
