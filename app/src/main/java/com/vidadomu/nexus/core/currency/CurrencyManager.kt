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
    EUR_SMART("Euros (€ / céntimos inteligentes)", "Muestra microprecios (< 1 €) en céntimos y precios mayores en euros"),
    EUR_STRICT("Euros (€ estricto)", "Muestra todos los importes exclusivamente en euros (€)"),
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

    /**
     * Formatea un valor en dólares (USD) según las preferencias activas.
     */
    fun formatUsd(usdAmount: Double): String {
        val currentState = _state.value
        val symbols = DecimalFormatSymbols(Locale("es", "ES"))

        return when (currentState.preference) {
            CurrencyPreference.USD -> {
                val pattern = if (usdAmount < 0.10) "0.000" else "0.00"
                val df = DecimalFormat(pattern, symbols)
                "$" + df.format(usdAmount)
            }
            CurrencyPreference.EUR_STRICT -> {
                val eur = usdAmount * currentState.rate
                val pattern = if (eur < 0.10) "0.000" else "0.00"
                val df = DecimalFormat(pattern, symbols)
                "${df.format(eur)} €"
            }
            CurrencyPreference.EUR_SMART -> {
                val eur = usdAmount * currentState.rate
                if (eur < 1.0) {
                    val cents = eur * 100.0
                    val pattern = if (cents < 10.0) "0.00" else "0.0"
                    val df = DecimalFormat(pattern, symbols)
                    "${df.format(cents)} cént."
                } else {
                    val df = DecimalFormat("0.00", symbols)
                    "${df.format(eur)} €"
                }
            }
        }
    }

    /**
     * Formatea el saldo disponible de la cuenta (en EUR o USD según preferencia).
     */
    fun formatBalance(usdAmount: Double): String {
        val currentState = _state.value
        val symbols = DecimalFormatSymbols(Locale("es", "ES"))
        return when (currentState.preference) {
            CurrencyPreference.USD -> {
                val df = DecimalFormat("0.00", symbols)
                "$" + df.format(usdAmount)
            }
            else -> {
                val eur = usdAmount * currentState.rate
                val df = DecimalFormat("0.00", symbols)
                "${df.format(eur)} €"
            }
        }
    }

    /**
     * Alias de formateo de coste para salidas y recibos.
     */
    fun formatCost(usdAmount: Double): String = formatUsd(usdAmount)

    /**
     * Formatea el precio unitario de un modelo (ej. "~1,3 cént. / img" o "~0,13 € / clip").
     */
    fun formatUnitPrice(usdAmount: Double): String {
        val current = _state.value
        val df = DecimalFormat("0.####", DecimalFormatSymbols(Locale("es", "ES")))
        return if(current.preference == CurrencyPreference.USD) "$" + df.format(usdAmount)
            else df.format(usdAmount * current.rate) + " €"
    }

    fun formatModelPricing(basePriceUsd: Double?, unit: String?, fallbackStr: String): String {
        if (basePriceUsd == null || basePriceUsd <= 0.0) return fallbackStr
        val formattedCost = formatUsd(basePriceUsd)
        val unitLabel = when (unit?.lowercase()) {
            "img", "image" -> "img"
            "clip", "video" -> "clip"
            "audio", "min" -> "min"
            "tokens", "1m tokens" -> "1M tokens"
            else -> unit ?: "req"
        }
        return "~$formattedCost / $unitLabel"
    }

    /**
     * Parsea y formatea un string de precio que devuelve la API de Atlas (ej. "0.14" -> "12,1 cént.").
     */
    fun formatAtlasApiPrice(rawPrice: String?): String {
        if (rawPrice.isNullOrBlank()) return "Sin coste reportado"
        val usdVal = rawPrice.toDoubleOrNull() ?: return rawPrice
        return formatUsd(usdVal)
    }

    fun formatAtlasApiPrice(usdVal: Double): String {
        return formatUsd(usdVal)
    }

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
