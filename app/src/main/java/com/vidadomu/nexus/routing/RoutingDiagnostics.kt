package com.vidadomu.nexus.routing

import android.content.Context
import kotlinx.serialization.json.*

/** Bounded local diagnostics. No prompts, keys, input filenames or signed URLs. */
object RoutingDiagnostics {
    fun save(context:Context, model:String, candidates:Int, eligible:Int, message:String) {
        val row=buildJsonObject {
            put("at",System.currentTimeMillis());put("model",model);put("candidates",candidates)
            put("eligible",eligible);put("decision",message)
        }
        context.getSharedPreferences("nexus_routing_diagnostics_v1",Context.MODE_PRIVATE).edit()
            .putString("latest",row.toString()).apply()
    }
    fun latest(context:Context):String? = context.getSharedPreferences("nexus_routing_diagnostics_v1",Context.MODE_PRIVATE)
        .getString("latest",null)
}

/** Collapse only registered exact identities. Every original route remains in the repository and saved preferences. */
fun logicalVariantRepresentatives(models:List<com.vidadomu.nexus.core.model.NexusModel>,
    preferred:com.vidadomu.nexus.core.model.NexusModel,hasImages:Boolean,automatic:Boolean = true):List<com.vidadomu.nexus.core.model.NexusModel> =
    if(!automatic) models else models.groupBy { ExactRouteCompatibility.identity(it,hasImages) ?: "${it.providerId}:${it.id}" }
        .values.map { group -> group.firstOrNull { it.providerId==preferred.providerId && it.id==preferred.id } ?: group.first() }
