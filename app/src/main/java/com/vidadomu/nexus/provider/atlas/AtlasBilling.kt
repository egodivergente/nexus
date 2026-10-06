package com.vidadomu.nexus.provider.atlas
import com.vidadomu.nexus.core.model.GenerationStatus
object AtlasBilling {
    fun confirmed(price:String?,status:GenerationStatus):Double? =
        if(status in setOf(GenerationStatus.COMPLETED,GenerationStatus.FAILED,GenerationStatus.CANCELLED))
            price?.toDoubleOrNull()?.takeIf { it.isFinite() && it>=0 } else null
}
