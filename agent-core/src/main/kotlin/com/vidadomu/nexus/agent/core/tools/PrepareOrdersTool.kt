package com.vidadomu.nexus.agent.core.tools

import com.vidadomu.nexus.agent.core.registry.ModelSchema
import com.vidadomu.nexus.agent.core.registry.ParamSpec
import com.vidadomu.nexus.agent.core.registry.ModelSummary
import kotlin.math.abs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Flujo fijo para preparar uno o varios encargos en UNA llamada. El modelo solo decide qué modelos y qué texto;
 * el código hace lo que nunca cambia: carga los schemas (en paralelo), adapta el formato y las referencias a cada
 * modelo, valida, estima el coste y deja una tarjeta por encargo. Nada se envía ni se gasta: eso sigue siendo
 * exclusivo del botón de la tarjeta.
 */
class PrepareOrdersTool(private val d: ToolDeps) : BaseTool(
    "prepare_orders", ToolCategory.PLAN,
    "Prepara UNO O VARIOS encargos en una sola llamada (la vía normal y más rápida): para cada modelo carga su schema, adapta el formato pedido " +
        "(aspect_ratio) y el número de referencias a lo que admite, valida, estima el coste y muestra UNA tarjeta por encargo. No hace falta llamar antes a " +
        "get_model_schema, validate_parameters, estimate_generation_cost ni create_generation_prompt. Si un encargo no es válido devuelve sus problemas y su schema " +
        "para corregirlo; los demás quedan preparados. No envía ni gasta nada.",
    schema {
        objectArray("orders", "Un elemento por encargo (p. ej. el mismo prompt en varios modelos).", required = true) {
            string("model_id", "Id exacto del registro.", required = true)
            string("provider_id", "Proveedor del modelo, si el id existe en varios.")
            string("prompt", "Prompt de este encargo; si falta se usa el común.")
            string("negative_prompt", "Solo si el schema lo admite; si falta se usa el común.")
            freeObject("params", "Parámetros propios de este modelo (claves de su schema).")
            objectArray("media", "Archivos de este encargo; si falta se usan los comunes.") {
                string("role", "Papel del archivo.", required = true, enum = PlanBuilder.ROLES)
                string("attachment_id", "Id de un adjunto de esta conversación.")
                string("asset_id", "Id de un asset del índice.")
            }
            string("rationale", "Por qué este modelo, en una frase para el usuario.")
        }
        string("prompt", "Prompt común a todos los encargos.")
        string("negative_prompt", "Prompt negativo común (se aplica solo donde el schema lo admite).")
        string("aspect_ratio", "Formato pedido, p. ej. 16:9. Se traduce al parámetro de cada modelo (aspect_ratio, ratio o size).")
        freeObject("params", "Parámetros comunes: se aplican solo a los modelos cuyo schema tiene esa clave.")
        objectArray("media", "Archivos comunes a todos los encargos (referencias, fotograma inicial…).") {
            string("role", "Papel del archivo.", required = true, enum = PlanBuilder.ROLES)
            string("attachment_id", "Id de un adjunto de esta conversación.")
            string("asset_id", "Id de un asset del índice.")
        }
        string("task", "Tarea (image_to_video…).")
        string("project", "Proyecto (nombre o id). Por defecto, el proyecto activo.")
        string("scene", "Código de escena.")
        string("shot", "Código de plano.")
        string("shot_id", "Id persistente del plano audiovisual de origen.")
        stringArray("characters", "Personajes que aparecen.")
    },
) {
    private class Item(val input: JsonObject, val model: ModelSummary?, val schema: ModelSchema?, val error: String?)

    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val requested = input.objList("orders")
        if (requested.isEmpty()) return fail("Indica al menos un encargo en «orders».")
        if (requested.size > MAX_ORDERS) return fail("Como máximo $MAX_ORDERS encargos por llamada; divide el lote.")

        // 1) Modelos y schemas. Los schemas vienen de la red: se piden todos a la vez y uno por modelo.
        val models = requested.map { o -> runCatching { d.resolveModel(o) } }
        val schemas = coroutineScope {
            models.mapNotNull { it.getOrNull()?.ref }.distinct().map { ref -> async { ref to runCatching { d.registry.schema(ref) } } }.awaitAll().toMap()
        }
        val items = requested.mapIndexed { i, o ->
            val model = models[i].getOrNull()
            val schema = model?.let { schemas[it.ref]?.getOrNull() }
            val error = models[i].exceptionOrNull()?.message
                ?: model?.let { schemas[it.ref]?.exceptionOrNull() }?.let { "No se pudo cargar el schema: ${it.message ?: it.javaClass.simpleName}" }
            Item(o, model, schema, error)
        }

        // 2) Cada encargo pasa por el mismo camino validado que prepare_generation, de uno en uno (comparten estado).
        val prepare = PrepareGenerationTool(d)
        val events = mutableListOf<UiEvent>()
        val lines = mutableListOf<String>()
        var total = 0.0; var unknownCost = false
        val seenModels = mutableSetOf<String>()
        var ready = 0
        // order_id → (línea del resumen, coste); vive en la sesión para que varias llamadas del mismo turno sumen.
        val turnLines = session.turnOrderLines
        val results = buildJsonArray {
            for (item in items) {
                val modelId = item.input.str("model_id").orEmpty()
                if (item.model == null || item.schema == null) {
                    add(buildJsonObject { put("model_id", modelId); put("ok", false); put("error", item.error ?: "Modelo no disponible.") })
                    continue
                }
                val notes = mutableListOf<String>()
                val params = adaptParams(input, item, notes)
                val media = adaptMedia(input, item, session, notes)
                val call = buildJsonObject {
                    put("model_id", item.model.ref.modelId); put("provider_id", item.model.ref.providerId)
                    (item.input.str("prompt") ?: input.str("prompt"))?.let { put("prompt", it) }
                    (item.input.str("negative_prompt") ?: input.str("negative_prompt"))?.takeIf { item.schema.param("negative_prompt") != null || item.input.str("negative_prompt") != null }
                        ?.let { put("negative_prompt", it) }
                    if (params.isNotEmpty()) put("params", JsonObject(params))
                    if (media.isNotEmpty()) put("media", JsonArray(media))
                    item.input.str("rationale")?.let { put("rationale", it) }
                    for (key in listOf("task", "project", "scene", "shot", "shot_id")) input.str(key)?.let { put(key, it) }
                    input["characters"]?.let { put("characters", it) }
                    // El mismo modelo dos veces en el lote son dos encargos distintos, no una corrección del anterior.
                    if (!seenModels.add(item.model.ref.toString())) put("new_copy", true)
                }
                val outcome = prepare.run(call, session)
                events += outcome.events
                if (!outcome.isError) {
                    ready++
                    val orderId = (outcome.result as? JsonObject)?.str("order_id")
                    val plan = orderId?.let { d.orders.get(it) }?.let { d.guard.plan(it.planId) }
                    val cost = plan?.estimate?.amountUsd
                    if (cost != null) total += cost else unknownCost = true
                    val line = "- **${item.model.name}**" + (plan?.params?.takeIf { it.isNotEmpty() }?.let { p -> ": " + p.entries.joinToString(", ") { "${it.key} ${it.value}" } } ?: "") +
                        " · " + (cost?.let { usd(it) } ?: "coste por confirmar") + notes.joinToString("") { " · $it" }
                    lines += line
                    // El lote puede completarse en varias llamadas (un modelo que hubo que corregir): el resumen las suma.
                    orderId?.let { if (it !in session.preparedThisTurn) session.preparedThisTurn += it }
                    if (orderId != null) turnLines[orderId] = line to (cost ?: Double.NaN)
                }
                add(buildJsonObject {
                    put("model_id", item.model.ref.modelId); put("ok", !outcome.isError)
                    if (notes.isNotEmpty()) put("adaptations", strings(notes))
                    val body = outcome.result as? JsonObject
                    if (outcome.isError) {
                        body?.get("error")?.let { put("error", it) }
                        body?.get("issues")?.let { put("issues", it) }
                        // El schema viaja con el fallo: se corrige en la siguiente llamada sin otra consulta.
                        put("schema", item.schema.toJson())
                    } else {
                        body?.get("order_id")?.let { put("order_id", it) }
                        body?.get("plan")?.let { put("plan", it) }
                        body?.get("model_guidance")?.let { put("model_guidance", it) }
                    }
                })
            }
        }
        val failed = items.size - ready
        // Todo lo preparado en el turno y que sigue pendiente de confirmación, en el orden en que se preparó.
        val turnOrders = session.preparedThisTurn.filter { id ->
            d.orders.get(id)?.status == com.vidadomu.nexus.agent.core.orders.OrderStatus.AWAITING_CONFIRMATION
        }
        val turnReady = turnOrders.size
        val turnTotal = turnOrders.mapNotNull { turnLines[it]?.second?.takeUnless(Double::isNaN) }.sum()
        val turnUnknown = turnOrders.any { turnLines[it]?.second?.isNaN() != false }
        val summary = buildJsonObject {
            put("prepared", ready); put("failed", failed); put("orders", results)
            // Con todo preparado, este texto ES la respuesta al usuario: el turno termina sin otra llamada al modelo.
            if (failed == 0 && turnReady > 0) put("summary_for_user", buildString {
                appendLine(if (turnReady == 1) "Preparado 1 encargo:" else "Preparados $turnReady encargos:")
                turnOrders.forEach { id -> turnLines[id]?.let { appendLine(it.first) } }
                appendLine()
                if (turnReady > 1) appendLine("Coste estimado total: **${usd(turnTotal)}**" + if (turnUnknown) " (sin contar los que están por confirmar)." else ".")
                append("Nada se ha enviado todavía. Revisa las tarjetas y pulsa **GENERAR**.")
            })
            put("next", when {
                failed == 0 -> "Las $ready tarjetas están PREPARADAS, no enviadas. Resume al usuario qué has preparado y cualquier adaptación (formato o referencias recortadas). El envío solo ocurre cuando pulse GENERAR."
                ready == 0 -> "Ningún encargo quedó preparado. Corrige con los «issues» y el «schema» devueltos y vuelve a llamar a prepare_orders solo con los que fallaron."
                else -> "$ready preparadas y $failed con problemas. Corrige las que fallaron con sus «issues» y «schema» (prepare_orders solo con esas) o explica al usuario por qué no se pueden preparar."
            })
        }
        return ToolOutcome(summary, isError = ready == 0, events = events)
    }

    /** Parámetros comunes (solo las claves que el schema tiene) + formato pedido traducido + los propios del encargo, que mandan. */
    private fun adaptParams(input: JsonObject, item: Item, notes: MutableList<String>): Map<String, kotlinx.serialization.json.JsonElement> {
        val schema = item.schema!!
        val out = linkedMapOf<String, kotlinx.serialization.json.JsonElement>()
        (input["params"] as? JsonObject)?.forEach { (k, v) -> if (schema.param(k) != null) out[k] = v }
        val own = item.input["params"] as? JsonObject
        input.str("aspect_ratio")?.let { ratio ->
            val (key, value) = aspectParam(schema, ratio) ?: run {
                if (schema.parameters.any { p -> p.key in RATIO_KEYS || p.uiType == "ASPECT_RATIO" || (p.key in SIZE_KEYS && p.options.any { DIMENSIONS.containsMatchIn(it) }) })
                    notes += "Este modelo no ofrece el formato $ratio; se deja su valor por defecto."
                return@let
            }
            if (own?.containsKey(key) != true) { out[key] = JsonPrimitive(value); if (value != ratio) notes += "Formato $ratio → $key=$value." }
        }
        own?.forEach { (k, v) -> out[k] = v }
        // Un valor que el modelo no admite (5 s donde solo hay 4, 6 u 8) se lleva al más cercano en vez de fallar.
        for ((k, v) in out.toList()) {
            val spec = schema.param(k) ?: continue
            val fixed = nearestAllowed(spec, v) ?: continue
            out[k] = fixed
            notes += "$k ${(v as? JsonPrimitive)?.content ?: v} no está disponible en este modelo: se usa ${(fixed as? JsonPrimitive)?.content ?: fixed}."
        }
        return out
    }

    /** Archivos propios o comunes. Si hay más imágenes de las que admite el modelo, se usan las primeras y se dice. */
    private fun adaptMedia(input: JsonObject, item: Item, session: AgentSession, notes: MutableList<String>): List<JsonObject> {
        val source = if (item.input.containsKey("media")) item.input else input
        val entries = source.objList("media")
        val max = item.schema!!.media.maxImages
        if (entries.isEmpty() || max <= 0) return entries
        val resolved = runCatching { resolveMedia(source, session, d.assets) }.getOrNull() ?: return entries
        val isImage = resolved.map { it.role != "video" && it.mimeType?.startsWith("video/") != true }
        val images = isImage.count { it }
        if (images <= max) return entries
        // Se conservan los fotogramas (rol único) y, del resto, las primeras referencias hasta el máximo.
        val fixed = entries.indices.filter { isImage[it] && entries[it].str("role") in setOf("first_frame", "last_frame") }
        var room = (max - fixed.size).coerceAtLeast(0)
        val keep = entries.indices.filter { i ->
            when {
                !isImage[i] || i in fixed -> true
                room > 0 -> { room--; true }
                else -> false
            }
        }
        notes += "${item.model!!.name} admite como máximo $max imágenes: se usan las primeras ${keep.count { isImage[it] }} de $images referencias."
        return keep.map { entries[it] }
    }

    companion object {
        const val MAX_ORDERS = 20
        private fun usd(v: Double) = String.format(java.util.Locale.US, "%.4f", v).trimEnd('0').trimEnd('.').replace('.', ',') + " USD"
        private val RATIO_KEYS = setOf("aspect_ratio", "aspectRatio", "ratio")
        private val SIZE_KEYS = setOf("size", "image_size", "resolution")
        private val DIMENSIONS = Regex("""(\d{2,5})\s*[x*×]\s*(\d{2,5})""")

        /**
         * El valor admitido más cercano cuando el pedido no vale, o null si ya vale o no se puede decidir.
         * Solo para números: en texto (un estilo, un modo de cámara) elegir «el más parecido» cambiaría la intención.
         */
        internal fun nearestAllowed(spec: ParamSpec, value: kotlinx.serialization.json.JsonElement): kotlinx.serialization.json.JsonElement? {
            val raw = (value as? JsonPrimitive)?.content?.trim() ?: return null
            val wanted = raw.toDoubleOrNull() ?: return null
            if (spec.options.isNotEmpty()) {
                if (raw in spec.options) return null
                val numeric = spec.options.mapNotNull { o -> o.trim().toDoubleOrNull()?.let { o to it } }
                // Todas las opciones han de ser números: si alguna no lo es, es una lista de nombres, no una escala.
                if (numeric.size != spec.options.size || numeric.isEmpty()) return null
                val best = numeric.minWith(compareBy({ abs(it.second - wanted) }, { it.second }))
                return JsonPrimitive(best.first).takeIf { best.first != raw }
            }
            val min = spec.min; val max = spec.max
            if (min == null && max == null) return null
            val clamped = wanted.coerceIn(min ?: Double.NEGATIVE_INFINITY, max ?: Double.POSITIVE_INFINITY)
            if (clamped == wanted) return null
            val text = if (spec.nativeType == "integer" || spec.uiType in setOf("DURATION", "INTEGER", "SEED") || raw.toIntOrNull() != null)
                clamped.toLong().toString() else clamped.toString()
            return JsonPrimitive(text)
        }

        /** Traduce «16:9» al parámetro real del modelo: su clave de formato o, si solo tiene tamaños, el tamaño con esa proporción. */
        internal fun aspectParam(schema: ModelSchema, ratio: String): Pair<String, String>? {
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
    }
}
