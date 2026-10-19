package com.vidadomu.nexus.agent.core.media

/** Accept fractional or percentage APIs without turning invalid input into 0% or 100%. */
fun normalizedProviderProgress(value: Double?, percentage: Boolean = false): Float? {
    if (value == null || !value.isFinite() || value < 0.0 || value > 100.0) return null
    return (if (percentage || value > 1.0) value / 100.0 else value).toFloat()
}
