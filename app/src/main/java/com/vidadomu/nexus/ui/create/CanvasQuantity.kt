package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.core.model.ModelParameter
import com.vidadomu.nexus.ui.components.EstimateUi

internal data class CanvasQuantity(val count: Int, val repeated: Boolean)

/** Never multiply a native multi-output request by a stale repeated quantity after restoring a preset. */
internal fun canvasQuantity(parameter: ModelParameter?, values: Map<String,String>, requested: Int): CanvasQuantity {
    if(parameter==null) return CanvasQuantity(requested,requested>1)
    val native=values[parameter.key]?.toIntOrNull()?.takeIf {it>0}
        ?: parameter.defaultValue.toIntOrNull()?.takeIf {it>0} ?: 1
    val repeat=requested>1 && !nativeCountAllowed(parameter,requested) && native==1 && nativeCountAllowed(parameter,1)
    return CanvasQuantity(if(repeat) requested else native,repeat)
}

internal fun canvasQuantityCost(estimate: EstimateUi, quantity: CanvasQuantity, formatUsd: (Double) -> String): String = when {
    !quantity.repeated -> estimate.amount
    isKnownCost(estimate) && estimate.usd!=null -> formatUsd(estimate.usd * quantity.count)
    estimate.hasPrice -> "${estimate.amount}/u"
    else -> estimate.amount
}
