package com.vidadomu.nexus.ui.components

import com.vidadomu.nexus.core.model.GenerationJob
import java.text.Normalizer
import java.util.Locale

internal fun matchesCreationSearch(job: GenerationJob, query: String): Boolean {
    fun normalize(value:String) = Normalizer.normalize(value.lowercase(Locale.ROOT),Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"")
    val words=normalize(query).split(Regex("\\s+")).filter {it.isNotBlank()}
    if(words.isEmpty()) return true
    val text=normalize("${job.modelId} ${job.providerId} ${job.prompt}")
    return words.all {it in text}
}
