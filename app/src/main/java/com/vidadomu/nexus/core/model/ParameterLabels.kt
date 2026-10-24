package com.vidadomu.nexus.core.model

/** Spanish names for native keys. Unknown keys are made readable, never translated by guesswork. */
object ParameterLabels {
    data class Entry(val label: String, val help: String)

    private val entries: Map<String, Entry> = buildMap {
        fun alias(label: String, help: String, vararg keys: String) = keys.forEach { put(it, Entry(label, help)) }
        alias("Orientación del personaje", "Indica hacia dónde mira el personaje en el vídeo.", "character_orientation")
        alias("Conservar sonido original", "Mantiene el audio del vídeo de entrada.", "keep_original_sound")
        alias("Formato", "Proporción entre ancho y alto del resultado.", "aspect_ratio", "ratio")
        alias("Resolución", "Nivel de detalle en píxeles del resultado.", "resolution")
        alias("Duración", "Duración del resultado en segundos.", "duration")
        alias("Tamaño", "Dimensiones del resultado.", "size")
        alias("Calidad", "Nivel de calidad que solicita el proveedor.", "quality")
        alias("Semilla", "Repite un resultado usando el mismo número; vacío deja que el proveedor elija uno.", "seed")
        alias("Prompt negativo", "Describe lo que no quieres que aparezca.", "negative_prompt")
        alias("Generar sonido", "Pide al modelo que añada audio al resultado.", "audio", "generate_audio", "enable_audio", "sound")
        alias("Número de imágenes", "Cuántas imágenes se generan en una sola petición.", "n", "num_images", "number_of_images")
        alias("Número de resultados", "Cuántos resultados se generan en una sola petición.", "num_outputs")
        alias("Formato de archivo", "Tipo de archivo del resultado.", "output_format")
        alias("Fondo", "Tipo de fondo del resultado.", "background")
        alias("Moderación", "Nivel de filtrado de contenido del proveedor.", "moderation")
        alias("Fidelidad a las instrucciones", "Cuánto se ciñe el resultado al texto escrito.", "guidance_scale", "cfg_scale")
        alias("Pasos", "Iteraciones de generación; más pasos suelen tardar más.", "num_inference_steps", "steps")
        alias("Comprobación de seguridad", "Activa el filtro de seguridad del proveedor.", "enable_safety_checker")
        alias("Ampliar instrucciones", "El proveedor amplía y detalla automáticamente tu texto antes de generar.",
            "prompt_extend", "prompt_enhancement", "enable_prompt_expansion", "prompt_expansion")
        alias("Marca de agua", "Añade la marca de agua del proveedor al resultado.", "watermark")
        alias("Guardar último fotograma", "Devuelve también la última imagen del vídeo.", "return_last_frame")
        alias("Cámara fija", "Mantiene la cámara sin movimiento.", "camera_fixed")
        alias("Fotogramas por segundo", "Fluidez del vídeo resultante.", "fps")
        alias("Ancho (px)", "Ancho del resultado en píxeles.", "width")
        alias("Alto (px)", "Alto del resultado en píxeles.", "height")
        alias("Lado largo (px)", "Medida en píxeles del lado más largo. Alternativa a indicar ancho y alto.", "long_side")
        alias("Lado corto (px)", "Medida en píxeles del lado más corto. Alternativa a indicar ancho y alto.", "short_side")
        alias("Factor de escala", "1 conserva la resolución y solo mejora la calidad; 2 la duplica.", "percent")
        alias("Formato de salida", "Tipo de archivo del resultado.", "encode_format")
        alias("Calidad de salida", "Compresión del archivo; solo cuenta en formatos con pérdida (JPEG y WebP).", "encode_quality")
    }

    fun entry(key: String): Entry? = entries[key] ?: entries[key.lowercase()]

    fun label(key: String): String = entry(key)?.label ?: readable(key)

    fun help(key: String): String? = entry(key)?.help

    /** True when [name] is only the native key in another spelling, so a curated label is preferable. */
    fun isRawKey(key: String, name: String): Boolean =
        name.isBlank() || normalized(name) == normalized(key) || normalized(name) == normalized(readable(key))

    fun readable(key: String): String {
        val words = key.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
            .split('_', '-', '.', ' ').filter { it.isNotBlank() }.joinToString(" ").lowercase()
        return words.replaceFirstChar { it.uppercase() }.ifBlank { key }
    }

    const val UNKNOWN_HELP = "NEXUS aún no tiene una descripción verificada de este ajuste; se envía al proveedor tal como lo publica."

    private fun normalized(value: String) = value.lowercase().filter { it.isLetterOrDigit() }
}
