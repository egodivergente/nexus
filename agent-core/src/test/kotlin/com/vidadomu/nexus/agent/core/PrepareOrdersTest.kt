package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.registry.MediaSpec
import com.vidadomu.nexus.agent.core.registry.ModelRef
import com.vidadomu.nexus.agent.core.registry.ModelSchema
import com.vidadomu.nexus.agent.core.registry.ModelSummary
import com.vidadomu.nexus.agent.core.registry.ParamSpec
import com.vidadomu.nexus.agent.core.tools.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

/** El caso real: «usa todas como referencia y repite la foto con distintos modelos en 16:9», en una sola llamada. */
class PrepareOrdersTest {
    private val editA = ModelRef(ATLAS, "acme/edit-ratio/edit")     // formato con aspect_ratio, hasta 10 imágenes
    private val editB = ModelRef(ATLAS, "acme/edit-size/edit")      // formato solo con size, hasta 4 imágenes
    private fun edit(ref: ModelRef, name: String, max: Int) = ModelSummary(ref, name, modalities = setOf("IMAGE"), capabilities = setOf("IMAGE_EDITING"),
        inputMode = "MULTI_IMAGE", supportsImageInput = true, supportsReferenceImages = true, maxImages = max, mediaRequired = true)
    private val models = MODELS + edit(editA, "Edit Ratio", 10) + edit(editB, "Edit Size", 4)
    private val schemas = SCHEMAS + mapOf(
        editA to ModelSchema(editA, listOf(ParamSpec("aspect_ratio", "Formato", "ASPECT_RATIO", "string", options = listOf("1:1", "16:9", "9:16"), default = "1:1")),
            MediaSpec(inputMode = "MULTI_IMAGE", imagesArrayField = "images", maxImages = 10, mediaRequired = true)),
        editB to ModelSchema(editB, listOf(ParamSpec("size", "Tamaño", "ENUM", "string", options = listOf("2048*2048", "1920*1080", "2560*1440"), default = "2048*2048")),
            MediaSpec(inputMode = "MULTI_IMAGE", imagesArrayField = "images", maxImages = 4, mediaRequired = true)),
    )
    private fun harness() = Harness(registry = FakeRegistry(models, schemas))
    private fun session(images: Int = 11) = AgentSession("lote", AgentMode.AGENT).apply {
        repeat(images) { attachments += Attachment("ref$it", "file:///ref$it.png", "image/png") }
    }
    /** Los modelos de vídeo del catálogo de prueba parten de un fotograma inicial, no de referencias sueltas. */
    private fun frame() = """{"role":"first_frame","attachment_id":"ref0"}"""
    private fun refs(n: Int) = (0 until n).joinToString(",") { """{"role":"reference","attachment_id":"ref$it"}""" }
    private fun call(body: String) = Json.parseToJsonElement(body).jsonObject
    private fun orders(o: ToolOutcome) = o.result.jsonObject["orders"]!!.jsonArray.map { it.jsonObject }

    @Test fun `un lote de dos modelos queda preparado en una llamada con formato y referencias adaptados`() = runTest {
        val h = harness(); val s = session()
        val out = PrepareOrdersTool(h.deps).run(call("""{"prompt":"Mateo en la pradera de San Isidro, Madrid, 1928","aspect_ratio":"16:9",
            "media":[${refs(11)}],"orders":[{"model_id":"${editA.modelId}"},{"model_id":"${editB.modelId}"}]}"""), s)
        assertFalse(out.isError, out.result.toString())
        assertEquals(2, out.result.jsonObject["prepared"]!!.jsonPrimitive.int)
        assertEquals(2, out.events.filterIsInstance<UiEvent.PlanReady>().size)
        val plans = h.guard.plans().associateBy { it.ref }
        // Formato: clave propia del modelo o, si solo hay tamaños, el mayor con esa proporción.
        assertEquals("16:9", plans.getValue(editA).params["aspect_ratio"])
        assertEquals("2560*1440", plans.getValue(editB).params["size"])
        // Referencias: las primeras hasta el máximo de cada modelo, y se dice.
        assertEquals(10, plans.getValue(editA).media.size); assertEquals(4, plans.getValue(editB).media.size)
        assertEquals("file:///ref0.png", plans.getValue(editB).media.first().uri)
        assertTrue(orders(out).all { o -> o["adaptations"]!!.jsonArray.any { "como máximo" in it.jsonPrimitive.content } })
        // Dos lecturas por modelo (la del lote y la del validador; en la app la segunda sale de la caché) y nada enviado.
        assertEquals(4, h.registry.schemaCalls); assertTrue(h.backend.submitted.isEmpty())
    }

    @Test fun `un modelo que falla no impide los demas y devuelve su schema para corregirlo`() = runTest {
        val h = harness(); val s = session(2)
        val out = PrepareOrdersTool(h.deps).run(call("""{"prompt":"Retrato","media":[${refs(2)}],
            "orders":[{"model_id":"${editA.modelId}","params":{"aspect_ratio":"21:9"}},{"model_id":"${editB.modelId}"},{"model_id":"no/existe"}]}"""), s)
        assertFalse(out.isError)
        assertEquals(1, out.result.jsonObject["prepared"]!!.jsonPrimitive.int); assertEquals(2, out.result.jsonObject["failed"]!!.jsonPrimitive.int)
        val (bad, good, missing) = orders(out)
        assertFalse(bad["ok"]!!.jsonPrimitive.boolean); assertNotNull(bad["schema"]); assertNotNull(bad["issues"])
        assertTrue(good["ok"]!!.jsonPrimitive.boolean); assertNotNull(good["order_id"])
        assertFalse(missing["ok"]!!.jsonPrimitive.boolean); assertTrue("registro" in missing["error"]!!.jsonPrimitive.content)
        assertEquals(1, h.guard.plans().size)
    }

    @Test fun `el mismo modelo dos veces son dos tarjetas y los parametros comunes solo van donde existen`() = runTest {
        val h = harness(); val s = session(1)
        val out = PrepareOrdersTool(h.deps).run(call("""{"media":[${refs(1)}],"params":{"aspect_ratio":"9:16","no_existe":"x"},
            "orders":[{"model_id":"${editA.modelId}","prompt":"Variante A"},{"model_id":"${editA.modelId}","prompt":"Variante B"},{"model_id":"${editB.modelId}","prompt":"C"}]}"""), s)
        assertFalse(out.isError, out.result.toString())
        assertEquals(3, h.guard.plans().size)
        assertEquals(setOf("Variante A", "Variante B"), h.guard.plans().filter { it.ref == editA }.map { it.prompt }.toSet())
        assertTrue(h.guard.plans().filter { it.ref == editA }.all { it.params["aspect_ratio"] == "9:16" })
        assertNull(h.guard.plans().single { it.ref == editB }.params["aspect_ratio"])
    }

    @Test fun `sin encargos o con todos invalidos es un error y no crea tarjetas`() = runTest {
        val h = harness(); val s = session(0)
        assertTrue(PrepareOrdersTool(h.deps).run(call("""{"orders":[]}"""), s).isError)
        val out = PrepareOrdersTool(h.deps).run(call("""{"prompt":"x","orders":[{"model_id":"no/existe"}]}"""), s)
        assertTrue(out.isError); assertTrue(h.guard.plans().isEmpty()); assertTrue(out.events.isEmpty())
    }

    @Test fun `un valor que el modelo no admite se lleva al mas cercano y se dice, en vez de fallar`() = runTest {
        val h = harness(); val s = session(1)
        // Veo solo admite 4, 6 u 8 segundos: pedir 5 no puede costar una ronda entera del modelo.
        val out = PrepareOrdersTool(h.deps).run(call("""{"prompt":"Mateo en el Retiro","media":[${frame()}],"params":{"duration":"5"},
            "orders":[{"model_id":"${VEO.modelId}"},{"model_id":"${SEEDANCE.modelId}"}]}"""), s)
        assertFalse(out.isError, out.result.toString())
        assertEquals(2, out.result.jsonObject["prepared"]!!.jsonPrimitive.int)
        val plans = h.guard.plans().associateBy { it.ref }
        assertEquals("4", plans.getValue(VEO).params["duration"])        // 5 → 4 (empate 4/6: el menor, más barato)
        assertEquals("5", plans.getValue(SEEDANCE).params["duration"])   // Seedance sí admite 5: no se toca
        val veo = orders(out).first { it["model_id"]!!.jsonPrimitive.content == VEO.modelId }
        assertTrue(veo["adaptations"]!!.jsonArray.single().jsonPrimitive.content.let { "duration" in it && "4" in it })
        assertNull(orders(out).first { it["model_id"]!!.jsonPrimitive.content == SEEDANCE.modelId }["adaptations"])
    }

    @Test fun `el resumen cuenta todas las tarjetas del turno aunque el lote se complete en dos llamadas`() = runTest {
        val h = harness(); val s = session(1); val tool = PrepareOrdersTool(h.deps)
        fun summary(o: ToolOutcome) = o.result.jsonObject["summary_for_user"]?.jsonPrimitive?.content
        val first = tool.run(call("""{"prompt":"Mateo","media":[${frame()}],"orders":[{"model_id":"${KLING_PRO.modelId}"},{"model_id":"${SEEDANCE.modelId}"}]}"""), s)
        assertTrue(summary(first)!!.startsWith("Preparados 2 encargos"))
        // Segunda llamada del MISMO turno (p. ej. el modelo corrige uno que falló): el resumen cuenta los tres.
        val second = tool.run(call("""{"prompt":"Mateo","media":[${frame()}],"orders":[{"model_id":"${VEO.modelId}"}]}"""), s)
        val text = summary(second)!!
        assertTrue(text.startsWith("Preparados 3 encargos"), text)
        for (name in listOf("Kling O3 Pro", "Seedance 2.0", "Veo 3.1")) assertTrue(name in text, "$name en «$text»")
        assertEquals(3, h.guard.plans().size)
        // Otro turno empieza de cero: no arrastra las tarjetas del anterior.
        s.preparedThisTurn.clear(); s.turnOrderLines.clear()
        assertTrue(summary(tool.run(call("""{"prompt":"Otro","media":[${frame()}],"orders":[{"model_id":"${KLING_PRO.modelId}"}]}"""), s))!!.startsWith("Preparado 1 encargo"))
    }

    @Test fun `un lote preparado cierra el turno en una sola llamada al modelo y el siguiente turno ya trae su detalle`() = runTest {
        val h = harness(); val att = (0 until 2).map { Attachment("ref$it", "file:///ref$it.png", "image/png") }
        val llm = com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider(listOf(
            { _ -> com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider.tools(use("p1", "prepare_orders", """{"prompt":"Selfie de Mateo en el Templo de Debod","aspect_ratio":"16:9",
                "media":[${refs(2)}],"orders":[{"model_id":"${editA.modelId}"},{"model_id":"${editB.modelId}"}]}""")) },
            { r ->
                // Segundo turno («ahora en el Retiro»): el contexto ya trae modelo, parámetros y prompt; no hace falta get_ui_state.
                val ctx = r.system.joinToString("\n") + r.messages.joinToString("\n") { m -> m.content.filterIsInstance<com.vidadomu.nexus.agent.core.llm.ContentBlock.Text>().joinToString { it.text } }
                assertTrue("model_id=${editA.modelId}" in ctx, ctx.takeLast(1500)); assertTrue("Templo de Debod" in ctx); assertTrue("aspect_ratio=16:9" in ctx)
                com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider.text("Entendido: preparo la misma idea en el Retiro cuando me confirmes los modelos.")
            },
        ))
        val o = com.vidadomu.nexus.agent.core.agent.AgentOrchestrator(llm, com.vidadomu.nexus.agent.core.config.AgentConfig(provider = "scripted", modelId = "test-model",
            region = "eu-west-1", endpoint = "http://proxy", maxToolRounds = 4), NexusToolkit.all(h.deps), h.skills, h.assets,
            uiContext = { h.deps.orders.turnContext(h.guard) }, verifiedOrders = { h.deps.orders.all() })
        val conv = o.newConversation(mode = AgentMode.AGENT)
        val first = o.send(conv, "Hazme un selfie en Debod con dos modelos en 16:9", att)
        assertEquals(1, llm.requests.size, "una sola llamada al modelo")
        assertTrue(first.text.startsWith("Preparados 2 encargos"), first.text); assertTrue("GENERAR" in first.text)
        assertEquals(2, h.guard.plans().size); assertTrue(h.backend.submitted.isEmpty())
        val second = o.send(conv, "ahora en el retiro")
        assertEquals(2, llm.requests.size); assertTrue("Retiro" in second.text)
    }
}
