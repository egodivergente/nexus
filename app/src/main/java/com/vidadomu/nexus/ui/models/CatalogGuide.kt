package com.vidadomu.nexus.ui.models

import com.vidadomu.nexus.core.model.*

internal fun catalogFamily(model: NexusModel): String {
    val id = (model.providerModelId?.takeIf { it.isNotBlank() } ?: model.id).lowercase(java.util.Locale.ROOT).replace('_', '-')
    return when {
        "seedream" in id -> "Seedream"
        "seedance" in id -> "Seedance"
        "kling" in id -> "Kling"
        "qwen-image" in id -> "Qwen Image"
        "qwen" in id -> "Qwen"
        "nano-banana" in id || "gemini-image" in id -> "Nano Banana"
        "gpt-image" in id -> "GPT Image"
        "grok-imagine" in id -> "Grok Imagine"
        "hailuo" in id -> "Hailuo"
        "tripo" in id -> "Tripo"
        "happyhorse" in id || "happy-horse" in id -> "HappyHorse"
        "minimax" in id || Regex("\\bh3\\b").containsMatchIn(id) -> "MiniMax H3"
        Regex("\\bwan\\b").containsMatchIn(id) -> "Wan"
        "flux" in id -> "FLUX"
        "veo" in id -> "Veo"
        "imagen" in id -> "Imagen"
        "sora" in id -> "Sora"
        "midjourney" in id || model.organization.equals("Midjourney", true) -> "Midjourney"
        "vidu" in id -> "Vidu"
        "pixverse" in id -> "PixVerse"
        "hunyuan" in id -> "Hunyuan"
        "magnific" in id -> "Magnific"
        "runway" in id || "gen4" in id || "gen-4" in id -> "Runway"
        "mai-image" in id -> "MAI Image"
        "z-image" in id -> "Z-Image"
        "ideogram" in id -> "Ideogram"
        "luma" in id || "ray-" in id -> "Luma"
        "pika" in id -> "Pika"
        else -> model.modelFamily.takeIf { it.isNotBlank() } ?: creator(model)
    }
}

internal data class CatalogSection(val family: String, val models: List<NexusModel>)
internal fun catalogSections(models: List<NexusModel>, favorites: Set<String>): List<CatalogSection> =
    models.groupBy(::catalogFamily).entries.sortedWith(
        compareByDescending<Map.Entry<String,List<NexusModel>>> { it.value.any { m -> modelBookmark(m) in favorites } }
            .thenByDescending { it.value.maxOf { m -> m.providerPriority } }.thenBy { it.key }
    ).map { (family, routes) -> CatalogSection(family, recentModels(routes).sortedByDescending { modelBookmark(it) in favorites }) }

internal data class ModelGuide(val purpose: String, val strength: String, val note: String, val source: String?)

/** Editorial claims are selected from the provider's actual profile, never from Pro/Fast names alone. */
private val guideCache = com.vidadomu.nexus.core.util.BoundedCache<NexusModel, ModelGuide>(512)
internal fun modelGuide(model: NexusModel): ModelGuide = guideCache.getOrPut(model) { buildModelGuide(model) }

internal fun modelGroupHighlight(models: List<NexusModel>): String? = models
    .flatMap { modelGuide(it).strength.split(" · ") }
    .map(String::trim).filter { it.isNotBlank() && !it.startsWith("Uso:") }
    .distinct().take(2).joinToString(" · ").takeIf(String::isNotBlank)

private fun buildModelGuide(model: NexusModel): ModelGuide {
    val id = model.id.lowercase()
    val profile = (model.description + " " + PublishedModelNotes.get(model.providerModelId ?: model.id)).lowercase()
    val purpose = when {
        "layer-decomposition" in id -> "Separa una imagen en capas transparentes para recomponer y editar sus elementos."
        "motion-control" in id -> "Transfiere a un personaje de una foto los movimientos y gestos de un vídeo."
        "lip-sync" in id || "lipsync" in id -> "Sincroniza la boca del personaje con una voz o pista de audio."
        "background-remov" in id || "remove-background" in id -> "Recorta el sujeto y elimina el fondo para preparar composiciones."
        "upscal" in id -> "Amplía la resolución de un archivo existente para preparar su entrega."
        "inpaint" in id -> "Retoca una zona de la imagen mediante instrucciones y una máscara."
        "outpaint" in id -> "Extiende la imagen más allá de sus bordes para cambiar el encuadre."
        "reference-to-image" in id && ("video" in profile || model.supportsVideoInput) -> "Crea imágenes nuevas usando un vídeo como referencia visual."
        "reference-to-video" in id || ModelCapability.REFERENCE_TO_VIDEO in model.capabilities -> "Construye un clip guiado por referencias de personajes, objetos o escenas."
        "extend" in id -> "Continúa un vídeo existente añadiendo una nueva parte a la escena."
        "edit" in id && Modality.IMAGE in model.modalities -> "Edita imágenes con instrucciones: cambia elementos, fondos o estilo."
        "image-to-video" in id -> "Anima una imagen: describe la acción y el movimiento de la cámara."
        "text-to-video" in id -> "Crea un vídeo desde una descripción, sin necesitar un fotograma inicial."
        "text-to-image" in id -> "Crea una imagen desde texto: describe la composición, el sujeto y el estilo."
        else -> usefulDescription(model)
    }
    val strengths = profileStrengths(model)
    val strength = strengths.take(2).joinToString(" · ").ifBlank {
        when {
            "motion-control" in id -> "Control de actuación, baile y gestos"
            "layer-decomposition" in id -> "Elementos separados para edición posterior"
            Modality.VIDEO in model.modalities && model.supportsLastFrame -> "Control del fotograma final"
            model.maxImages > 1 -> "Trabajo con varias imágenes de referencia"
            Modality.VIDEO in model.modalities && model.supportsAudio -> "Opciones de audio en el vídeo"
            else -> "Uso: ${modelUse(model).label.lowercase()}"
        }
    }
    val note = if (strength.contains("identidad")) "La ficha anuncia conservación de identidad; falta evaluar el parecido con tus referencias."
        else "Capacidades descritas por el proveedor; comprueba las entradas y opciones de esta variante."
    val source = if(model.providerId in setOf("atlas_cloud", "atlas", "atlas-cloud")) "https://www.atlascloud.ai/models/${model.providerModelId ?: model.id}" else null
    return ModelGuide(purpose, strength, note, source)
}

/** Strengths stated in the provider's own profile; heuristic flags never contribute. */
internal fun profileStrengths(model: NexusModel): List<String> {
    val profile = (model.description + " " + PublishedModelNotes.get(model.providerModelId ?: model.id)).lowercase()
    return buildList {
        if (Regex("preserv\\w*.{0,100}(identity|facial)|consisten.{0,60}(identity|subject|character)|identity.{0,40}(preserv|consisten)").containsMatchIn(profile))
            add("Consistencia facial e identidad con referencias")
        if (listOf("typograph", "text rendering", "text render", "text in image").any { it in profile })
            add("Texto y tipografía dentro de la imagen")
        if ("transparent" in profile) add("Salida con transparencias")
        if (Modality.VIDEO in model.modalities && ("first" in profile && "last" in profile && "frame" in profile || "start-end frame" in profile || "start-to-end frame" in profile)) add("Control del inicio y del final del plano")
        if (Modality.VIDEO in model.modalities && listOf("native audio", "synchronized audio", "synchronised audio", "synchronized sound").any { it in profile }) add("Vídeo con audio integrado")
        if (listOf("low latency", "efficiency-focused", "rapid", "fast generation").any { it in profile }) add("Iteración rápida según la ficha del proveedor")
        if ("mask" in profile) add("Edición localizada con máscara")
        if ("multi-image" in profile || "multiple reference" in profile) add("Composición a partir de varias referencias")
        if ("cfg scale control" in profile) add("Ajuste entre fidelidad al texto y libertad creativa")
    }
}

internal const val CATALOG_CARD_SUMMARY_MAX_CHARS = 60

/** Whole sentence or whole " · " clauses that fit two lines of a 411 dp card; never cut inside a word. */
internal fun fitCardSummary(text: String, maxChars: Int = CATALOG_CARD_SUMMARY_MAX_CHARS): String? {
    val clean = text.trim().replace(Regex("\\s+"), " ")
    if (clean.isEmpty() || maxChars <= 0) return null
    if (clean.length <= maxChars) return clean
    val sentence = clean.split(Regex("(?<=[.!?])\\s+")).first()
    if (sentence.length <= maxChars) return sentence
    var fitted = ""
    for (clause in sentence.split(" · ")) {
        val next = if (fitted.isEmpty()) clause else "$fitted · $clause"
        if (next.length > maxChars) break
        fitted = next
    }
    return fitted.takeIf { it.isNotEmpty() }
}

private fun spanishList(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items.single()
    else -> items.dropLast(1).joinToString(", ") + " y " + items.last()
}

/** Task list in plain words: "Desde texto, animar imagen y con referencias", trimmed by whole items. */
internal fun workflowSummary(workflows: List<CatalogWorkflow>, maxChars: Int = CATALOG_CARD_SUMMARY_MAX_CHARS): String? {
    val labels = workflows.map { it.label }.distinct()
    if (labels.isEmpty()) return null
    if (labels.size == 1) return fitCardSummary(workflows.first().summary, maxChars) ?: fitCardSummary(labels.single(), maxChars)
    fun phrase(shown: List<String>, rest: Int): String {
        val words = shown.mapIndexed { i, label -> if (i == 0) label else label.replaceFirstChar { it.lowercase() } }
        return if (rest > 0) words.joinToString(", ") + " y $rest más" else spanishList(words)
    }
    for (count in labels.size downTo 1) {
        val candidate = phrase(labels.take(count), labels.size - count)
        if (candidate.length <= maxChars) return candidate
    }
    return null
}

/** One complete function per card: editorial advice first, then the executable tasks, then the use description. */
internal fun catalogCardSummary(routes: List<NexusModel>, prefix: String = "", maxChars: Int = CATALOG_CARD_SUMMARY_MAX_CHARS): String {
    val room = maxChars - prefix.length
    val body = routes.firstNotNullOfOrNull(CatalogFocus::forModel)?.good?.let { fitCardSummary(it, room) }
        ?: workflowSummary(routes.flatMap(::catalogWorkflows).distinct(), room)
        ?: routes.firstOrNull()?.let { fitCardSummary(usefulDescription(it), room) }
    return when {
        body == null -> prefix.removeSuffix(" · ").trim()
        else -> prefix + body
    }
}
