package com.vidadomu.nexus.routing

import com.vidadomu.nexus.core.model.NexusModel
import com.vidadomu.nexus.data.repository.CuratedVideoSelection

/**
 * Regla Seedance (2026-11-01). En modo automático se ordenan las demás APIs compatibles por coste total y BytePlus va
 * siempre al final, aunque sea más barato. Una ruta Crun que llega a BytePlus (`direct_byteplus` o `byteplus_fallback`)
 * cuenta como BytePlus. Una elección manual de BytePlus se envía tal cual: nunca se desvía antes a otra API.
 */
object SeedanceBytePlusPolicy {
    private val crunFlags = listOf("direct_byteplus", "byteplus_fallback")

    fun isSeedance(model: NexusModel): Boolean = listOfNotNull(model.id, model.providerModelId, model.family, model.displayName)
        .any { it.contains("seedance", ignoreCase = true) }

    fun reachesBytePlus(model: NexusModel?, parameters: Map<String, String>): Boolean {
        if (model == null || !isSeedance(model)) return false
        if (model.providerId == CuratedVideoSelection.BYTEPLUS || model.id.startsWith("custom/byteplus-media/") ||
            model.providerModelId?.startsWith("custom/byteplus-media/") == true) return true
        return model.providerId == CuratedVideoSelection.CRUN && crunFlags.any { flag ->
            (parameters[flag] ?: model.parameters.firstOrNull { it.key == flag }?.defaultValue).equals("true", ignoreCase = true)
        }
    }
}
