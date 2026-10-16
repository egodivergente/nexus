package com.vidadomu.nexus.agent.core.media

/**
 * Editor de parámetros en texto, una línea por parámetro:
 *
 *     clave | Etiqueta | tipo | valor por defecto | opción1, opción2 | avanzado
 *
 * Solo la clave es obligatoria. Tipos: text, enum, integer, float, boolean, aspect_ratio, resolution, duration, seed.
 * Una línea con «*» tras la clave (p. ej. `prompt_extra*`) marca el parámetro como obligatorio.
 */
object MediaParamsText {
    val TYPES = setOf("text", "enum", "integer", "float", "boolean", "aspect_ratio", "resolution", "duration", "seed")

    /** Devuelve los parámetros o el primer error con su número de línea. */
    fun parse(text: String): Result<List<MediaParam>> {
        val out = mutableListOf<MediaParam>()
        text.lines().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val cells = line.split('|').map { it.trim() }
            var key = cells[0]
            val required = key.endsWith("*")
            if (required) key = key.removeSuffix("*").trim()
            if (!Regex("^[A-Za-z0-9_\\-]{1,40}$").matches(key)) return Result.failure(IllegalArgumentException("Línea ${index + 1}: la clave «$key» no es válida (letras, números, _ y -)."))
            if (out.any { it.key == key }) return Result.failure(IllegalArgumentException("Línea ${index + 1}: la clave «$key» está repetida."))
            val options = cells.getOrNull(4).orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val type = cells.getOrNull(2).orEmpty().lowercase().ifBlank { if (options.isNotEmpty()) "enum" else "text" }
            if (type !in TYPES) return Result.failure(IllegalArgumentException("Línea ${index + 1}: el tipo «$type» no existe (${TYPES.joinToString()})."))
            val default = cells.getOrNull(3).orEmpty()
            if (type == "enum" && options.isEmpty()) return Result.failure(IllegalArgumentException("Línea ${index + 1}: un parámetro enum necesita opciones."))
            if (options.isNotEmpty() && default.isNotEmpty() && default !in options) return Result.failure(IllegalArgumentException("Línea ${index + 1}: el valor por defecto «$default» no está entre las opciones."))
            out += MediaParam(key = key, label = cells.getOrNull(1).orEmpty().ifBlank { key }, type = type, default = default, options = options,
                required = required, advanced = cells.getOrNull(5).orEmpty().lowercase() in setOf("avanzado", "si", "sí", "1", "true"))
        }
        return Result.success(out)
    }

    fun format(params: List<MediaParam>): String = params.joinToString("\n") { p ->
        listOf(p.key + if (p.required) "*" else "", p.label.takeIf { it != p.key }.orEmpty(), p.type, p.default, p.options.joinToString(", "), if (p.advanced) "avanzado" else "")
            .dropLastWhile { it.isEmpty() }.joinToString(" | ")
    }

    /** «a, b; c» → [a, b, c] */
    fun list(text: String): List<String> = text.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }
}
