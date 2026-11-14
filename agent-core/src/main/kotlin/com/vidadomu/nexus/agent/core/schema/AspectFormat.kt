package com.vidadomu.nexus.agent.core.schema

import com.vidadomu.nexus.agent.core.registry.ModelSchema
import kotlin.math.abs

/**
 * Traduce un formato pedido («9:16») al parámetro real de un modelo: su clave de formato o, si solo publica
 * tamaños en píxeles («1584*2816», «2160x3840»), el mayor tamaño con esa proporción.
 *
 * Lo comparten la preparación de encargos y el router. Vidal 14-11: el router solo miraba la clave de formato
 * y descartaba los modelos nuevos que expresan 9:16 en píxeles (Seedream v5.5 Edit, GPT Image 2.6 Edit), así
 * que recomendaba generaciones antiguas mientras la preparación sí sabía usarlos.
 */
object AspectFormat {
    val RATIO_KEYS = setOf("aspect_ratio", "aspectRatio", "ratio")
    val SIZE_KEYS = setOf("size", "image_size", "resolution")
    val DIMENSIONS = Regex("""(\d{2,5})\s*[x*×]\s*(\d{2,5})""")

    fun param(schema: ModelSchema, ratio: String): Pair<String, String>? {
        val wanted = ratio.split(':').mapNotNull { it.trim().toDoubleOrNull() }.takeIf { it.size == 2 && it[1] > 0 }?.let { it[0] / it[1] }
        schema.parameters.firstOrNull { it.key in RATIO_KEYS || it.uiType == "ASPECT_RATIO" }?.let { p ->
            if (p.options.isEmpty() || ratio in p.options) return p.key to ratio
        }
        if (wanted == null) return null
        for (p in schema.parameters.filter { it.key in SIZE_KEYS }) {
            // Entre los tamaños con esa proporción (±2 %), el mayor.
            val match = p.options.mapNotNull { option ->
                DIMENSIONS.find(option)?.let { m ->
                    val w = m.groupValues[1].toDouble(); val h = m.groupValues[2].toDouble()
                    if (h > 0 && abs(w / h - wanted) / wanted <= 0.02) option to w * h else null
                }
            }.maxByOrNull { it.second }
            if (match != null) return p.key to match.first
        }
        return null
    }

    /** El modelo ofrece algún formato (clave de formato o tamaños en píxeles), aunque no sea el pedido. */
    fun offersFormats(schema: ModelSchema): Boolean = schema.parameters.any { p ->
        p.key in RATIO_KEYS || p.uiType == "ASPECT_RATIO" || (p.key in SIZE_KEYS && p.options.any { DIMENSIONS.containsMatchIn(it) })
    }
}
