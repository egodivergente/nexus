package com.vidadomu.nexus.core.currency

import android.util.Log
import com.vidadomu.nexus.core.security.ISecureStorage
import com.vidadomu.nexus.core.security.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CurrencyPreference(val displayName: String, val description: String) {
    EUR_SMART("Euros (€)", "Importes en euros con dos decimales"),
    EUR_STRICT("Euros (€ · preferencia guardada)", "Importes en euros con dos decimales"),
    USD("Dólares ($ USD)", "Muestra los importes originales en dólares estadounidenses")
}

data class CurrencyState(
    val rate: Double = 0.8666, // 1 USD = ~0.8666 EUR (ECB reference)
    val lastUpdated: Long = 0L,
    val preference: CurrencyPreference = CurrencyPreference.EUR_SMART,
    val isUpdating: Boolean = false,
    val error: String? = null
) {
    val formattedLastUpdated: String
        get() {
            if (lastUpdated == 0L) return "Tasa de referencia sin conexión"
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "ES"))
            return sdf.format(Date(lastUpdated))
        }

    val rateSummaryText: String
        get() = "1 USD = ${DecimalFormat("#.####", DecimalFormatSymbols(Locale("es", "ES"))).format(rate)} EUR (BCE)"
}

class CurrencyManager(
    private val okHttpClient: OkHttpClient,
    private val secureStorage: ISecureStorage
) {
    constructor(secureStorage: ISecureStorage) : this(OkHttpClient(), secureStorage)

    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(
        CurrencyState(
            rate = secureStorage.getUsdToEurRate().takeIf { it > 0.0 } ?: 0.8666,
            lastUpdated = secureStorage.getExchangeRateLastUpdated(),
            preference = parsePreference(secureStorage.getCurrencyPreference())
        )
    )
    val state: StateFlow<CurrencyState> = _state.asStateFlow()

    fun setPreference(preference: CurrencyPreference) {
        secureStorage.setCurrencyPreference(preference.name)
        _state.update { it.copy(preference = preference) }
    }

    suspend fun refreshRate(): Result<Double> = withContext(Dispatchers.IO) {
        _state.update { it.copy(isUpdating = true, error = null) }
        val request = Request.Builder()
            .url(EXCHANGE_API_URL)
            .get()
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errMsg = "Error del servidor de divisas (HTTP ${response.code})"
                    _state.update { it.copy(isUpdating = false, error = errMsg) }
                    return@withContext Result.failure(Exception(errMsg))
                }

                val bodyStr = response.body?.string()
                if (bodyStr.isNullOrBlank()) {
                    val errMsg = "Respuesta vacía del servidor de divisas."
                    _state.update { it.copy(isUpdating = false, error = errMsg) }
                    return@withContext Result.failure(Exception(errMsg))
                }

                val rootObj = json.parseToJsonElement(bodyStr).jsonObject
                val ratesObj = rootObj["rates"]?.jsonObject
                val eurRate = ratesObj?.get("EUR")?.jsonPrimitive?.doubleOrNull

                if (eurRate == null || eurRate <= 0.0) {
                    val errMsg = "Formato de tasa EUR no reconocido."
                    _state.update { it.copy(isUpdating = false, error = errMsg) }
                    return@withContext Result.failure(Exception(errMsg))
                }

                val now = System.currentTimeMillis()
                secureStorage.setUsdToEurRate(eurRate)

                _state.update {
                    it.copy(
                        rate = eurRate,
                        lastUpdated = now,
                        isUpdating = false,
                        error = null
                    )
                }
                Log.d(TAG, "Exchange rate updated: 1 USD = $eurRate EUR")
                Result.success(eurRate)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update currency exchange rate", e)
            val errMsg = "Sin conexión con el servicio de divisas. Usando tasa guardada."
            _state.update { it.copy(isUpdating = false, error = errMsg) }
            Result.failure(e)
        }
    }

    /** Presentation only: exchange-rate retrieval and persisted preferences are unchanged. */
    private fun number(value: Double, decimals: Int): String {
        if (!value.isFinite()) return "—"
        val pattern = if(decimals == 0) "#,##0" else "#,##0." + "0".repeat(decimals)
        return DecimalFormat(pattern, DecimalFormatSymbols(Locale("es", "ES"))).format(value)
    }

    private fun displayAmount(usdAmount: Double, smallCost: Boolean): String {
        if (!usdAmount.isFinite()) return "—"
        val current=_state.value
        // Without a usable exchange rate, show the real dollar figure instead of a fake «0,00 €».
        val dollars=current.preference==CurrencyPreference.USD || !(current.rate.isFinite() && current.rate>0)
        val amount=if(dollars) usdAmount else usdAmount*current.rate
        val unit=if(dollars) "US$" else "€"
        return if(smallCost && amount>0 && number(amount,2)=="0,00") "<0,01 $unit"
            else "${number(amount,2)} $unit"
    }
    fun formatUsd(usdAmount: Double): String = displayAmount(usdAmount,true)
    fun formatBalance(usdAmount: Double): String = displayAmount(usdAmount,false)
    fun formatCost(usdAmount: Double): String = displayAmount(usdAmount,true)
    fun formatUnitPrice(usdAmount: Double): String = displayAmount(usdAmount,true)
    fun formatCredits(credits: Double): String = if(credits.isFinite()) "${number(credits, 0)} créditos" else "—"
    /** Original USD precision is reserved for expanded details. */
    fun formatUsdDetails(usdAmount: Double): String = if(usdAmount.isFinite()) "${number(usdAmount, 4)} USD" else "—"

    fun formatModelPricing(basePriceUsd: Double?, unit: String?, fallbackStr: String): String {
        if (basePriceUsd == null || !basePriceUsd.isFinite() || basePriceUsd <= 0.0) return fallbackStr
        val unitLabel = when (unit?.lowercase()) {
            "img", "image" -> "imagen"
            "clip", "video" -> "clip"
            "audio" -> "audio"
            "min" -> "min"
            "tokens", "1m tokens" -> "1M tokens"
            else -> unit ?: "solicitud"
        }
        return "${formatUsd(basePriceUsd)} / $unitLabel"
    }

    fun formatAtlasApiPrice(rawPrice: String?): String {
        if(rawPrice.isNullOrBlank()) return "Sin coste reportado"
        val usd = rawPrice.toDoubleOrNull() ?: return "Coste no disponible"
        return formatUsd(usd)
    }
    fun formatAtlasApiPrice(usdVal: Double): String = formatUsd(usdVal)

    private fun parsePreference(prefStr: String): CurrencyPreference {
        return try {
            CurrencyPreference.valueOf(prefStr)
        } catch (e: Exception) {
            CurrencyPreference.EUR_SMART
        }
    }

    companion object {
        private const val TAG = "NexusCurrency"
        // Frankfurter API oficial respaldada por los datos de referencia del Banco Central Europeo (ECB)
        const val EXCHANGE_API_URL = "https://api.frankfurter.dev/v1/latest?from=USD&symbols=EUR"
    }
}
