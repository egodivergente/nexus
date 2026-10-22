package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.core.model.NexusModel
import com.vidadomu.nexus.ui.models.modelUse
import com.vidadomu.nexus.ui.models.providerName

internal data class CanvasModelIdentity(val title: String, val detail: String, val exactId: String)

/** Short presentation only. Requests retain the exact catalog/provider identifier. */
internal fun canvasModelIdentity(model: NexusModel): CanvasModelIdentity {
    val id=(model.providerModelId ?: model.id).lowercase()
    val parts=id.split('/')
    val mini=parts.firstOrNull { it.matches(Regex("h3(?:-[a-z0-9]+)*|hailuo-[0-9.]+")) }
    val title=if(mini!=null && ("minimax" in id || model.organization.contains("minimax",true))) {
        if(mini.startsWith("h3")) "H3"+mini.removePrefix("h3").split('-').filter {it.isNotBlank()}.joinToString(" ",prefix=if(mini=="h3") "" else " ") {it.replaceFirstChar(Char::uppercase)}
        else {
            val version=mini.removePrefix("hailuo-")
            val suffix=parts.dropWhile {it!=mini}.drop(1).flatMap {it.split('-')}
                .filter {it in setOf("fast","pro","standard","turbo")}.distinct()
                .joinToString(" ") {if(it=="standard") "Standard" else it.replaceFirstChar(Char::uppercase)}
            "Hailuo $version"+suffix.takeIf {it.isNotBlank()}?.let {" $it"}.orEmpty()
        }
    } else model.displayName.substringBefore(" · proyecto activo")
    val provider=if(model.providerId=="usr_byteplus-media") "BytePlus" else providerName(model.providerId)
    return CanvasModelIdentity(title, listOf(modelUse(model).label,provider).filter {it.isNotBlank()}.distinct().joinToString(" · "),model.providerModelId ?: model.id)
}
