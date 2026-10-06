package com.vidadomu.nexus.core.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

interface ISecureStorage {
    fun saveProviderApiKey(providerId: String, apiKey: String)
    fun getProviderApiKey(providerId: String): String
    fun hasProviderApiKey(providerId: String): Boolean
    fun clearProviderApiKey(providerId: String)
    fun getMaskedProviderApiKey(providerId: String): String
    fun saveAtlasApiKey(apiKey: String)
    fun getAtlasApiKey(): String
    fun hasAtlasApiKey(): Boolean
    fun clearAtlasApiKey()
    fun getMaskedAtlasApiKey(): String
    fun isMockModeEnabled(): Boolean
    fun setMockModeEnabled(enabled: Boolean)
    fun getCurrencyPreference(): String
    fun setCurrencyPreference(pref: String)
    fun getUsdToEurRate(): Double
    fun setUsdToEurRate(rate: Double)
    fun getExchangeRateLastUpdated(): Long
    fun getCachedBalance(): com.vidadomu.nexus.core.model.AccountBalance?
    fun saveCachedBalance(balance: com.vidadomu.nexus.core.model.AccountBalance)
    fun getLastModelId(modality: com.vidadomu.nexus.core.model.Modality): String?
    fun setLastModelId(modality: com.vidadomu.nexus.core.model.Modality, modelId: String)
}

class SecureStorage(context: Context) : ISecureStorage {

    var credentialStorageAvailable: Boolean = true
        private set
    private val sharedPreferences: SharedPreferences

    init {
        sharedPreferences = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Secure credential storage unavailable: ${e.javaClass.simpleName}")
            // Non-persistent, fail-closed storage: the app stays usable but cannot save secrets.
            credentialStorageAvailable = false
            context.getSharedPreferences("nexus_non_sensitive_preferences", Context.MODE_PRIVATE)
        }
    }

    override fun saveProviderApiKey(providerId: String, apiKey: String) {
        check(credentialStorageAvailable) { "No se puede guardar la clave: el almacenamiento seguro no está disponible." }
        sharedPreferences.edit().putString(keyForProvider(providerId), apiKey.trim()).apply()
    }

    override fun getProviderApiKey(providerId: String): String =
        sharedPreferences.getString(keyForProvider(providerId), "")?.trim().orEmpty()

    override fun hasProviderApiKey(providerId: String): Boolean = getProviderApiKey(providerId).isNotBlank()

    override fun clearProviderApiKey(providerId: String) {
        sharedPreferences.edit().remove(keyForProvider(providerId)).apply()
    }

    override fun getMaskedProviderApiKey(providerId: String): String = maskApiKey(getProviderApiKey(providerId))

    override fun saveAtlasApiKey(apiKey: String) {
        saveProviderApiKey("atlas_cloud", apiKey)
    }

    override fun getAtlasApiKey(): String {
        return getProviderApiKey("atlas_cloud")
    }

    override fun hasAtlasApiKey(): Boolean {
        return getAtlasApiKey().isNotBlank()
    }

    override fun clearAtlasApiKey() {
        clearProviderApiKey("atlas_cloud")
    }

    override fun getMaskedAtlasApiKey(): String {
        val key = getAtlasApiKey()
        return maskApiKey(key)
    }

    override fun isMockModeEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_MOCK_MODE, false)
    }

    override fun setMockModeEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_MOCK_MODE, enabled).apply()
    }

    override fun getCurrencyPreference(): String {
        return sharedPreferences.getString(KEY_CURRENCY_PREF, "EUR_SMART") ?: "EUR_SMART"
    }

    override fun setCurrencyPreference(pref: String) {
        sharedPreferences.edit().putString(KEY_CURRENCY_PREF, pref).apply()
    }

    override fun getUsdToEurRate(): Double {
        val rateFloat = sharedPreferences.getFloat(KEY_EXCHANGE_RATE, 0.8666f)
        return rateFloat.toDouble()
    }

    override fun setUsdToEurRate(rate: Double) {
        sharedPreferences.edit()
            .putFloat(KEY_EXCHANGE_RATE, rate.toFloat())
            .putLong(KEY_EXCHANGE_RATE_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    override fun getExchangeRateLastUpdated(): Long {
        return sharedPreferences.getLong(KEY_EXCHANGE_RATE_TIMESTAMP, 0L)
    }

    override fun getCachedBalance(): com.vidadomu.nexus.core.model.AccountBalance? {
        val avail = sharedPreferences.getFloat(KEY_BALANCE_AVAIL, -1f)
        if (avail < 0) return null
        val cash = sharedPreferences.getFloat(KEY_BALANCE_CASH, avail).toDouble()
        val bonus = sharedPreferences.getFloat(KEY_BALANCE_BONUS, 0f).toDouble()
        val frozen = sharedPreferences.getFloat(KEY_BALANCE_FROZEN, 0f).toDouble()
        val updated = sharedPreferences.getLong(KEY_BALANCE_TIMESTAMP, 0L)
        return com.vidadomu.nexus.core.model.AccountBalance(
            availableUsd = avail.toDouble(),
            cashUsd = cash,
            bonusUsd = bonus,
            frozenUsd = frozen,
            lastUpdated = updated
        )
    }

    override fun saveCachedBalance(balance: com.vidadomu.nexus.core.model.AccountBalance) {
        sharedPreferences.edit()
            .putFloat(KEY_BALANCE_AVAIL, balance.availableUsd.toFloat())
            .putFloat(KEY_BALANCE_CASH, balance.cashUsd.toFloat())
            .putFloat(KEY_BALANCE_BONUS, balance.bonusUsd.toFloat())
            .putFloat(KEY_BALANCE_FROZEN, balance.frozenUsd.toFloat())
            .putLong(KEY_BALANCE_TIMESTAMP, balance.lastUpdated)
            .apply()
    }

    override fun getLastModelId(modality: com.vidadomu.nexus.core.model.Modality): String? {
        return sharedPreferences.getString("nexus_last_model_${modality.name}", null)
    }

    override fun setLastModelId(modality: com.vidadomu.nexus.core.model.Modality, modelId: String) {
        sharedPreferences.edit().putString("nexus_last_model_${modality.name}", modelId).apply()
    }

    companion object {
        private const val TAG = "NexusSecurity"
        private const val SECURE_PREFS_NAME = "nexus_secure_prefs"
        private const val SECURE_PREFS_FALLBACK = "nexus_secure_fallback"
        private const val KEY_ATLAS_API_KEY = "atlas_cloud_api_key"
        private const val KEY_MOCK_MODE = "nexus_mock_mode_enabled"
        private const val KEY_CURRENCY_PREF = "nexus_currency_preference"
        private const val KEY_EXCHANGE_RATE = "nexus_usd_to_eur_rate"
        private const val KEY_EXCHANGE_RATE_TIMESTAMP = "nexus_usd_to_eur_timestamp"
        private const val KEY_BALANCE_AVAIL = "nexus_balance_available_usd"
        private const val KEY_BALANCE_CASH = "nexus_balance_cash_usd"
        private const val KEY_BALANCE_BONUS = "nexus_balance_bonus_usd"
        private const val KEY_BALANCE_FROZEN = "nexus_balance_frozen_usd"
        private const val KEY_BALANCE_TIMESTAMP = "nexus_balance_timestamp"

        private fun keyForProvider(providerId: String): String {
            if (providerId == "atlas_cloud") return KEY_ATLAS_API_KEY
            val safeId = providerId.lowercase().replace(Regex("[^a-z0-9_\\-]"), "_")
            return "provider_${safeId}_api_key"
        }

        fun maskApiKey(apiKey: String): String {
            if (apiKey.isBlank()) return "Not configured"
            val trimmed = apiKey.trim()
            return if (trimmed.length <= 4) {
                "••••"
            } else {
                "••••••••••••" + trimmed.takeLast(4)
            }
        }

        fun sanitizeForLogging(input: String): String {
            if (input.isBlank()) return input
            // Redact Bearer tokens
            val bearerRegex = Regex("(?i)Bearer\\s+[a-zA-Z0-9_\\-\\.]+")
            var sanitized = bearerRegex.replace(input, "Bearer [REDACTED]")
            // Redact api_key or apikey parameter values
            val keyParamRegex = Regex("(?i)(api[_-]?key[\"':= ]+)[a-zA-Z0-9_\\-\\.]+")
            sanitized = keyParamRegex.replace(sanitized, "$1[REDACTED]")
            return sanitized
        }
    }
}
