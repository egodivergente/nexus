package com.vidadomu.nexus.ui.components

/** Missing data is not a failed request, and neither is a zero-price quote. */
enum class PriceAvailability(val label: String) {
    KNOWN("Tarifa disponible"), LOADING("Consultando precio…"),
    UNAVAILABLE("Precio por confirmar"), ERROR("Consulta no disponible")
}
internal fun priceAvailability(pending: Boolean, failed: Boolean): PriceAvailability = when {
    pending -> PriceAvailability.LOADING
    failed -> PriceAvailability.ERROR
    else -> PriceAvailability.UNAVAILABLE
}
