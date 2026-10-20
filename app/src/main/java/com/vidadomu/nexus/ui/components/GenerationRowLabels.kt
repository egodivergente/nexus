package com.vidadomu.nexus.ui.components

import com.vidadomu.nexus.core.model.GenerationJob
import com.vidadomu.nexus.ui.create.ComparisonPlan

private const val MAX_TITLE_CHARS = 120
private val markdownNoise = Regex("""^\s*(?:#{1,6}\s*|[-*•>]\s+|\d+[.)]\s+)?(?:\*\*|__)?|(?:\*\*|__)\s*$""")
private val upperLabelPrefix = Regex("""^([^\p{Ll}:]{2,48}):\s*(.*)$""")

private fun String.hasLetterOrDigit() = any { it.isLetterOrDigit() }
private fun String.isUpperLabel() = hasLetterOrDigit() && none { it.isLowerCase() } && count { it.isLetter() } >= 2

/**
 * Título local de una fila: primera línea con contenido real del encargo. Omite rótulos de plantilla
 * («SUBJECT & SCENE», «## ESTILO:») y líneas de solo puntuación; nunca llama a un modelo.
 */
internal fun generationRowTitle(prompt: String, fallback: String): String {
    var firstLabel: String? = null
    for (raw in prompt.lineSequence()) {
        val line = raw.replace(markdownNoise, "").trim()
        if (!line.hasLetterOrDigit()) continue
        val prefixed = upperLabelPrefix.matchEntire(line)?.takeIf { it.groupValues[1].isUpperLabel() }
        val label = when {
            prefixed != null -> prefixed.groupValues[1]
            line.endsWith(':') && line.length <= 48 -> line.dropLast(1)
            line.length <= 48 && line.isUpperLabel() -> line
            else -> return clampTitle(line)
        }.trim('*', '_', ' ')
        val rest = prefixed?.groupValues?.get(2)?.trim('*', '_', ' ').orEmpty()
        if (rest.hasLetterOrDigit()) return clampTitle(rest)
        if (firstLabel == null) firstLabel = label
    }
    return firstLabel?.let(::clampTitle) ?: fallback
}

private fun clampTitle(text: String): String {
    val single = text.replace(Regex("\\s+"), " ").trim()
    if (single.length <= MAX_TITLE_CHARS) return single
    val cut = single.take(MAX_TITLE_CHARS).substringBeforeLast(' ').ifBlank { single.take(MAX_TITLE_CHARS) }
    return cut.trimEnd(',', ';', '.', ' ') + "…"
}

/** Variante declarada por una comparativa (número por modelo dentro del lote). */
internal fun comparisonVariantLabel(job: GenerationJob): String? {
    val number = job.parameters[ComparisonPlan.NUMBER]?.toIntOrNull()?.takeIf { it > 0 } ?: return null
    val size = job.parameters[ComparisonPlan.MODEL_SIZE]?.toIntOrNull()?.takeIf { it >= number }
    return if (size != null) "Variante $number de $size" else "Variante $number"
}

/**
 * Posición del trabajo entre los guardados con el mismo modelo y el mismo texto, por fecha de inicio.
 * Solo es un recuento del historial local: si se borran trabajos, la numeración se recalcula.
 */
internal fun attemptPosition(job: GenerationJob, history: List<GenerationJob>): Pair<Int, Int>? {
    val prompt = job.prompt.trim()
    if (prompt.isEmpty()) return null
    val same = history.filter { it.modelId == job.modelId && it.prompt.trim() == prompt }
        .sortedWith(compareBy<GenerationJob>({ it.startedAt }, { it.id }))
    if (same.size < 2) return null
    val index = same.indexOfFirst { it.id == job.id }
    return if (index < 0) null else (index + 1) to same.size
}

/**
 * Todas las etiquetas de un snapshot del historial de una vez (O(n log n)), para no recorrerlo en cada fila.
 * Se recalcula con cada snapshot nuevo; no se guarda entre snapshots.
 */
internal fun generationVariantLabels(history: List<GenerationJob>, historyComplete: Boolean): Map<String, String> {
    val labels = HashMap<String, String>()
    if (historyComplete) history.filter { it.prompt.isNotBlank() }
        .groupBy { it.modelId to it.prompt.trim() }.values.forEach { same ->
            if (same.size >= 2) same.sortedWith(compareBy<GenerationJob>({ it.startedAt }, { it.id }))
                .forEachIndexed { index, job -> labels[job.id] = "Intento ${index + 1} de ${same.size}" }
        }
    history.forEach { job -> comparisonVariantLabel(job)?.let { labels[job.id] = it } }
    return labels
}

/** Etiqueta honesta para distinguir intentos; sin historial completo no se numera. */
internal fun generationVariantLabel(job: GenerationJob, history: List<GenerationJob>, historyComplete: Boolean): String? =
    comparisonVariantLabel(job) ?: if (!historyComplete) null
        else attemptPosition(job, history)?.let { (number, total) -> "Intento $number de $total" }
