package com.vidadomu.nexus.ui.create

internal fun creationProviderLabel(providerId: String?, atlasBalance: String?): String = when(providerId) {
    "atlas_cloud", "atlas", "atlas-cloud" -> atlasBalance?.let { "Atlas · $it" } ?: "Atlas Cloud"
    "crun" -> "Crun"
    "runway" -> "Runway"
    null -> "Elige un modelo"
    else -> providerId
}
