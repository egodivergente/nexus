package com.vidadomu.nexus.provider.runway
import kotlinx.serialization.json.*
object RunwayBilling {
    fun confirmedUsd(root:JsonObject):Double? {
        val terminal=root["status"]?.jsonPrimitive?.content in setOf("SUCCEEDED","FAILED","CANCELLED")
        if(!terminal) return null
        return (root["cost"] as? JsonObject)?.get("credits")?.jsonPrimitive?.doubleOrNull
            ?.takeIf { it.isFinite() && it>=0 }?.times(.01)
    }
    fun estimatedUsd(root:JsonObject):Double? = (root["estimatedCost"] as? JsonObject)?.get("credits")?.jsonPrimitive?.doubleOrNull
        ?.takeIf { it.isFinite() && it>=0 }?.times(.01)
}
