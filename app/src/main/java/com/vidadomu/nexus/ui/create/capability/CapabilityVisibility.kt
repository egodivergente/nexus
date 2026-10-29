package com.vidadomu.nexus.ui.create.capability

internal data class CapabilityVisibility(val hidden: Set<String>, val stable: Boolean)

/** Hidden draft values cannot activate other branches: evaluate against what the request can contain. */
internal fun capabilityVisibility(rules: List<CapabilityConstraint>, values: Map<String,String>, counts: Map<String,Int>): CapabilityVisibility {
    var hidden = emptySet<String>()
    repeat(rules.size + 2) {
        val active = values.filterKeys {it !in hidden}
        val activeCounts = counts.filterKeys {it !in hidden}
        val next = rules.filter {it.hidden == true && it.applies(active, activeCounts)}.map {it.fieldKey}.toSet()
        if (next == hidden) return CapabilityVisibility(next, stable = true)
        hidden = next
    }
    return CapabilityVisibility(hidden, stable = false)
}
