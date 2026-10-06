package com.vidadomu.nexus

import com.vidadomu.nexus.provider.api.ProviderBalanceParser
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderBalanceParserTest {
    @Test fun readsNestedCrunCredits() {
        val status = ProviderBalanceParser.decode("""{"code":200,"data":{"balance":"1250.50"}}""",
            listOf("balance", "credits"))
        assertEquals("1250.5 créditos", status.balanceLabel)
    }

    @Test fun readsRunwayCreditBalanceRegardlessOfCasing() {
        val status = ProviderBalanceParser.decode("""{"organization":{"creditBalance":87}}""",
            listOf("credit_balance"))
        assertEquals("87 créditos", status.balanceLabel)
    }
}
