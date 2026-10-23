package com.vidadomu.nexus.settings

import android.content.Context
import com.vidadomu.nexus.agent.core.config.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Private, versioned preferences. One validated write makes imports atomic for observers. */
class RuntimePreferences private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("nexus_runtime_rules_v1",0)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val mutable = MutableStateFlow(runCatching {
        json.decodeFromString<RuntimeConfiguration>(prefs.getString("configuration", "{}")!!).validated()
    }.getOrDefault(RuntimeConfiguration()))
    val state = mutable.asStateFlow()
    fun value(key: String, scope: RuleScope = RuleScope()) = state.value.value(key,scope)
    fun bool(key: String) = value(key).toBoolean()
    fun int(key: String, scope: RuleScope = RuleScope()) = value(key,scope).toIntOrNull() ?: Int.MAX_VALUE
    fun decimal(key: String) = value(key).toFloat()
    @Synchronized fun set(key: String, value: String?, scope: RuleScope = RuleScope()) = commit(state.value.set(key,value,scope))
    fun export(): String = json.encodeToString(state.value)
    fun import(text: String) = commit(json.decodeFromString<RuntimeConfiguration>(text).validated())
    private fun commit(value: RuntimeConfiguration) {
        check(prefs.edit().putString("configuration",json.encodeToString(value)).commit()) { "No se pudieron guardar los ajustes" }
        mutable.value = value
    }
    companion object {
        @Volatile private var instance: RuntimePreferences? = null
        fun get(context: Context): RuntimePreferences = instance ?: synchronized(this) {
            instance ?: RuntimePreferences(context).also { instance=it }
        }
    }
}
