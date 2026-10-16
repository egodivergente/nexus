package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.agent.AgentOrchestrator
import com.vidadomu.nexus.agent.core.agent.ClaimVerifier
import com.vidadomu.nexus.agent.core.assets.Asset
import com.vidadomu.nexus.agent.core.assets.AssetType
import com.vidadomu.nexus.agent.core.config.AgentConfig
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.cost.CostConfidence
import com.vidadomu.nexus.agent.core.llm.ContentBlock
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider.Companion.text
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider.Companion.tools
import com.vidadomu.nexus.agent.core.orders.DuplicateSubmission
import com.vidadomu.nexus.agent.core.orders.OrderStatus
import com.vidadomu.nexus.agent.core.registry.DerivedParam
import com.vidadomu.nexus.agent.core.registry.MediaSpec
import com.vidadomu.nexus.agent.core.registry.ModelRef
import com.vidadomu.nexus.agent.core.registry.ModelSchema
import com.vidadomu.nexus.agent.core.registry.ModelSummary
import com.vidadomu.nexus.agent.core.registry.ParamCondition
import com.vidadomu.nexus.agent.core.registry.ParamSpec
import com.vidadomu.nexus.agent.core.schema.ErrorDiagnosis
import com.vidadomu.nexus.agent.core.spend.GenerationPlan
import com.vidadomu.nexus.agent.core.spend.MediaRef
import com.vidadomu.nexus.agent.core.spend.SubmissionUncertain
import com.vidadomu.nexus.agent.core.tools.AgentSession
import com.vidadomu.nexus.agent.core.tools.Attachment
import com.vidadomu.nexus.agent.core.tools.GetUiStateTool
import com.vidadomu.nexus.agent.core.tools.NexusToolkit
import com.vidadomu.nexus.agent.core.tools.PrepareGenerationTool
import com.vidadomu.nexus.agent.core.tools.UiEvent
import com.vidadomu.nexus.agent.core.tools.UpdatePlanTool
import com.vidadomu.nexus.agent.core.tools.turnContext
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

val TENCENT = ModelRef(ATLAS, "tencent/video/upscaler")
private val TEMPLATES = listOf("720p", "1080p", "2k", "4k").flatMap { t -> listOf("live-action-enhance-$t", "anime-enhance-$t") }
private val TIERS = mapOf("720p" to (0.0 to 720.0), "1080p" to (721.0 to 1080.0), "2k" to (1081.0 to 1440.0), "4k" to (1441.0 to 2160.0))

/** Réplica del schema publicado (static.atlascloud.ai, 2026-10-16) + el campo que exige el backend. */
val TENCENT_SCHEMA = ModelSchema(
    TENCENT,
    listOf(
        ParamSpec("template", "Plantilla", "ENUM", "string", options = TEMPLATES, default = "anime-enhance-4k"),
        ParamSpec("short", "Lado corto", "INTEGER", "integer", min = 0.0, max = 2160.0, default = "0", advanced = true,
            conditions = TIERS.map { (tier, range) -> ParamCondition("template", TEMPLATES.filter { it.endsWith(tier) }, range.first, range.second) }),
        ParamSpec("fps", "FPS", "INTEGER", "integer", min = 1.0, max = 100.0),
        ParamSpec("keep_metadata", "Conservar metadatos", "BOOLEAN", "boolean", default = "true", advanced = true),
    ),
    MediaSpec(inputMode = "VIDEO_INPUT", videoField = "video_url", maxVideos = 1, mediaRequired = true),
    promptRequired = false, promptAccepted = false, sourceParams = mapOf("fps" to "video_fps"),
    derived = listOf(DerivedParam("resolution", "template",
        TEMPLATES.associateWith { mapOf("720p" to "720p", "1080p" to "1080p", "2k" to "1440p", "4k" to "4k").getValue(it.substringAfterLast('-')) },
        "Atlas exige resolution; Nexus la deduce de la plantilla.")),
)
val TENCENT_MODEL = ModelSummary(TENCENT, "Tencent Video Upscaler", organization = "TENCENT", modalities = setOf("VIDEO"),
    capabilities = setOf("UPSCALE", "VIDEO_TO_VIDEO"), inputMode = "VIDEO_INPUT", supportsVideoInput = true, mediaRequired = true,
    basePriceUsd = 0.004, priceUnit = "second", priceUnitVerified = true)

class TencentUpscaleFlowTest {
    private fun harness() = Harness(registry = FakeRegistry(MODELS + TENCENT_MODEL, SCHEMAS + (TENCENT to TENCENT_SCHEMA)))
    private fun json(s: String) = Json.parseToJsonElement(s).jsonObject

    private suspend fun video(h: Harness, seconds: Double = 30.0): Attachment {
        val a = h.assets.addAsset(Asset(id = "", uri = "file:///data/agent_media/clip.mp4", type = AssetType.VIDEO, displayName = "clip.mp4",
            mimeType = "video/mp4", durationSec = seconds, createdAt = h.now))
        return Attachment(a.id, a.uri, "video/mp4", "clip.mp4", durationSec = seconds, assetId = a.id)
    }

    private suspend fun prepare(h: Harness, session: AgentSession, att: Attachment, extra: String = "") =
        PrepareGenerationTool(h.deps).run(json("""{"model_id":"${TENCENT.modelId}","provider_id":"$ATLAS","params":{"template":"live-action-enhance-4k"},"media":[{"role":"video","attachment_id":"${att.id}"}]$extra}"""), session)

    @Test fun `upscale 4K separa prompt y parametros, deduce resolution y omite short incompatible`() = runTest {
        val h = harness(); val att = video(h); val s = AgentSession("c", AgentMode.AGENT).apply { attachments += att }
        val withPrompt = PrepareGenerationTool(h.deps).run(json("""{"model_id":"${TENCENT.modelId}","prompt":"upscale a 4k","params":{"template":"live-action-enhance-4k"},"media":[{"role":"video","attachment_id":"${att.id}"}]}"""), s)
        assertTrue(withPrompt.isError, "un modelo sin campo prompt no recibe texto libre")
        assertTrue(h.deps.orders.all().isEmpty(), "un plan inválido no crea tarjeta")

        val out = prepare(h, s, att)
        assertFalse(out.isError, out.result.toString())
        val plan = h.guard.plans().single()
        assertEquals("", plan.prompt)
        assertEquals(mapOf("template" to "live-action-enhance-4k", "fps" to "30"), plan.params, "short=0 por defecto no se envía con plantilla 4K; fps medidos del original")
        assertEquals("4k", plan.effectiveParams["resolution"])
        assertNull(plan.effectiveParams["short"])
        val order = h.deps.orders.all().single()
        assertEquals(OrderStatus.AWAITING_CONFIRMATION, order.status)
        assertEquals(order.id, (out.events.single() as UiEvent.PlanReady).orderId)
        // Coste con la tarifa 4K publicada, no con la base de 720P.
        val e = assertNotNull(plan.estimate)
        assertEquals(CostConfidence.PUBLISHED_RATE, e.confidence)
        assertTrue(abs(e.amountUsd!! - 30 * 0.036542) < 1e-9, e.toString())
    }

    @Test fun `un short explicito fuera del rango de la plantilla es un error`() = runTest {
        val h = harness(); val att = video(h); val s = AgentSession("c", AgentMode.AGENT).apply { attachments += att }
        val out = PrepareGenerationTool(h.deps).run(json("""{"model_id":"${TENCENT.modelId}","params":{"template":"live-action-enhance-4k","short":1080},"media":[{"role":"video","attachment_id":"${att.id}"}]}"""), s)
        assertTrue(out.isError)
        assertTrue("1441" in out.result.toString(), out.result.toString())
        val contradiction = PrepareGenerationTool(h.deps).run(json("""{"model_id":"${TENCENT.modelId}","params":{"template":"live-action-enhance-4k","resolution":"1080p"},"media":[{"role":"video","attachment_id":"${att.id}"}]}"""), s)
        assertTrue(contradiction.isError, "resolution contradictoria con la plantilla")
    }

    @Test fun `repetir prepare, reintentar y corregir no duplican la tarjeta`() = runTest {
        val h = harness(); val att = video(h); val s = AgentSession("c", AgentMode.AGENT).apply { attachments += att }
        prepare(h, s, att)
        prepare(h, s, att)
        val again = PrepareGenerationTool(h.deps).run(json("""{"model_id":"${TENCENT.modelId}","params":{"template":"anime-enhance-4k"},"media":[{"role":"video","attachment_id":"${att.id}"}]}"""), s)
        assertFalse(again.isError)
        val order = h.deps.orders.all().single()
        assertEquals(order.planId, (again.events.single() as UiEvent.PlanReady).plan.id)
        assertEquals(order.id, (again.events.single() as UiEvent.PlanReady).orderId)

        // El proveedor rechaza (definitivo): el mismo encargo queda en error con el mensaje literal.
        h.backend.failNext = IllegalArgumentException("resolution is required (720p, 1080p, 1440p or 4k)")
        val plan = h.guard.plan(order.planId)!!
        assertFailsWith<IllegalArgumentException> { h.deps.lifecycle.confirmAndSubmit(plan.id, plan.fingerprint()) }
        val failed = h.deps.orders.get(order.id)!!
        assertEquals(OrderStatus.FAILED, failed.status)
        assertEquals("resolution is required (720p, 1080p, 1440p or 4k)", failed.lastFailure!!.message)
        assertNotNull(failed.lastFailure!!.explanation)

        // Corrección por el agente sobre el MISMO encargo: conserva el vídeo y vuelve a pendiente.
        val fixed = UpdatePlanTool(h.deps).run(json("""{"order_id":"${order.id}","params":{"template":"live-action-enhance-4k"}}"""), s)
        assertFalse(fixed.isError, fixed.result.toString())
        val after = h.deps.orders.all().single()
        assertEquals(order.id, after.id)
        assertEquals(2, after.attempt)
        assertEquals(OrderStatus.AWAITING_CONFIRMATION, after.status)
        assertEquals(listOf("file:///data/agent_media/clip.mp4"), h.guard.plan(after.planId)!!.media.map { it.uri })
        assertEquals(0, h.backend.submitted.size, "corregir nunca envía")
    }

    @Test fun `dos pulsaciones simultaneas producen un solo envio`() = runTest {
        val h = Harness(registry = FakeRegistry(MODELS + TENCENT_MODEL, SCHEMAS + (TENCENT to TENCENT_SCHEMA)),
            backend = object : FakeBackend() {
                override suspend fun submit(plan: GenerationPlan) = run { delay(50); super.submit(plan) }
            })
        val att = video(h); val s = AgentSession("c", AgentMode.AGENT).apply { attachments += att }
        prepare(h, s, att)
        val plan = h.guard.plans().single()
        val results = listOf(
            async { runCatching { h.deps.lifecycle.confirmAndSubmit(plan.id, plan.fingerprint()) } },
            async { runCatching { h.deps.lifecycle.confirmAndSubmit(plan.id, plan.fingerprint()) } },
        ).awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        assertTrue(results.single { it.isFailure }.exceptionOrNull() is DuplicateSubmission)
        assertEquals(1, h.backend.submitted.size)
        assertEquals(OrderStatus.PROCESSING, h.deps.orders.all().single().status)
    }

    @Test fun `un resultado incierto bloquea el encargo y nunca se reintenta`() = runTest {
        val h = harness(); val att = video(h); val s = AgentSession("c", AgentMode.AGENT).apply { attachments += att }
        prepare(h, s, att)
        val plan = h.guard.plans().single()
        h.backend.failNext = SubmissionUncertain("timeout", "job_x")
        assertFailsWith<SubmissionUncertain> { h.deps.lifecycle.confirmAndSubmit(plan.id, plan.fingerprint()) }
        val o = h.deps.orders.all().single()
        assertEquals(OrderStatus.UNCERTAIN, o.status)
        assertFailsWith<Exception> { h.deps.lifecycle.confirmAndSubmit(plan.id, plan.fingerprint()) }
        val retry = UpdatePlanTool(h.deps).run(json("""{"order_id":"${o.id}","params":{"template":"anime-enhance-4k"}}"""), s)
        assertTrue(retry.isError)
        assertEquals(0, h.backend.submitted.size)
    }

    @Test fun `diagnostico de un campo exigido deduce la correccion de la plantilla`() {
        val withResolution = TENCENT_SCHEMA.copy(derived = emptyList(), parameters = TENCENT_SCHEMA.parameters +
            ParamSpec("resolution", "Resolución", "RESOLUTION", "string", options = listOf("720p", "1080p", "1440p", "4k")))
        val plan = GenerationPlan("p", TENCENT, "Tencent", "VIDEO", prompt = "", params = mapOf("template" to "live-action-enhance-4k"),
            effectiveParams = mapOf("template" to "live-action-enhance-4k", "keep_metadata" to "true"), media = listOf(MediaRef("video", "file:///v.mp4")), createdAt = 0)
        val d = assertNotNull(ErrorDiagnosis.diagnose("resolution is required (720p, 1080p, 1440p or 4k)", withResolution, plan))
        assertEquals(mapOf("resolution" to "4k"), d.fix!!.params)
        val twoK = plan.copy(effectiveParams = mapOf("template" to "anime-enhance-2k"))
        assertEquals("1440p", ErrorDiagnosis.diagnose("resolution is required (720p, 1080p, 1440p or 4k)", withResolution, twoK)!!.fix!!.params["resolution"])
        val unknown = ErrorDiagnosis.diagnose("resolution is required (720p, 1080p, 1440p or 4k)", TENCENT_SCHEMA.copy(derived = emptyList()), plan)!!
        assertNull(unknown.fix, "nunca propone una clave que el schema no tiene")
    }

    @Test fun `el verificador detecta exitos no respaldados y respeta negaciones`() {
        val h = harness()
        val o = h.deps.orders.upsertPlan(GenerationPlan("p", TENCENT, "Tencent Video Upscaler", "VIDEO", prompt = "", params = emptyMap(), effectiveParams = emptyMap(), createdAt = 0))
        val orders = h.deps.orders.all()
        assertEquals(ClaimVerifier.Claim.SUBMITTED, ClaimVerifier.verify("¡Listo! Ya lo he enviado y se está generando.", orders).first().claim)
        assertTrue(ClaimVerifier.verify("Lo he preparado. No se ha enviado todavía: pulsa Generar para confirmar el coste.", orders).isEmpty())
        assertTrue(ClaimVerifier.verify("Cuando pulses Generar quedará enviado.", orders).isEmpty())
        h.deps.orders.beginSubmit(o.id, o.fingerprint); h.deps.orders.accepted(o.id, "job_1")
        assertTrue(ClaimVerifier.verify("Se está generando.", h.deps.orders.all()).isEmpty())
        assertEquals(ClaimVerifier.Claim.COMPLETED, ClaimVerifier.verify("Ya está terminado.", h.deps.orders.all()).single().claim)
    }

    @Test fun `flujo de chat completo, el agente ve el error real y se corrige sin crear otra tarjeta`() = runTest {
        val h = harness(); val att = video(h)
        val cfg = AgentConfig(provider = "scripted", modelId = "test-model", region = "eu-west-1", endpoint = "http://proxy", maxToolRounds = 6)
        var orderId = ""
        val llm = ScriptedLlmProvider(listOf(
            // Turno 1: prepara el encargo… y miente diciendo que ya está enviado.
            { tools(use("t1", "prepare_generation", """{"model_id":"${TENCENT.modelId}","provider_id":"$ATLAS","params":{"template":"live-action-enhance-4k"},"media":[{"role":"video","attachment_id":"${att.id}"}]}""")) },
            { r -> orderId = r.resultJson("t1")["order_id"]!!.jsonPrimitive.content; text("¡Listo! Ya lo he enviado a Tencent y se está generando en 4K.") },
            // Verificación automática: debe ver el estado real y rehacer la respuesta.
            { r ->
                val last = (r.messages.last().content.single() as ContentBlock.Text).text
                assertTrue("[Verificación automática" in last && "Pendiente de confirmación" in last, last)
                text("He preparado el upscale a 4K (plantilla live-action-enhance-4k). Pulsa Generar en la tarjeta para confirmar el coste y enviarlo.")
            },
            // Turno 2: el usuario pulsó y el proveedor rechazó. El agente lo recibe en el contexto real.
            { r ->
                val ctx = r.messages.last().content.filterIsInstance<ContentBlock.Text>().joinToString("\n") { it.text }
                assertTrue("[Estado real de la interfaz" in ctx, ctx)
                assertTrue("resolution is required" in ctx, ctx)
                assertTrue("user_confirmed" in ctx, "ve la pulsación del usuario: $ctx")
                tools(use("t2", "get_ui_state", """{"order_id":"$orderId"}"""))
            },
            { r ->
                val state = r.resultJson("t2").toString()
                assertTrue("provider_message" in state && "FAILED" in state, state)
                tools(use("t3", "update_plan", """{"order_id":"$orderId","params":{"template":"live-action-enhance-4k"}}"""))
            },
            { r -> assertFalse(r.result("t3").isError, r.result("t3").content.toString()); text("Falló porque Atlas exigía «resolution». He corregido el mismo encargo: ahora envía resolution=4k. Pulsa Generar para confirmarlo.") },
        ))
        val o = AgentOrchestrator(llm, cfg, NexusToolkit.all(h.deps), h.skills, h.assets, today = { "2026-10-16" },
            uiContext = { h.deps.orders.turnContext(h.guard) }, verifiedOrders = { h.deps.orders.all() })
        val conv = o.newConversation(mode = AgentMode.AGENT)
        val events = mutableListOf<UiEvent>()
        val first = o.send(conv, "Haz upscale de este vídeo a 4K con Tencent", listOf(att)) { events += it }
        assertTrue(first.selfCorrected)
        assertFalse("enviado" in first.text.lowercase() && "no" !in first.text.lowercase())
        assertTrue(first.verifiedNotes.isEmpty(), first.verifiedNotes.toString())
        assertEquals(1, events.filterIsInstance<UiEvent.PlanReady>().map { it.orderId }.distinct().size)

        // El usuario pulsa; Atlas rechaza.
        val plan = h.guard.plan(h.deps.orders.get(orderId)!!.planId)!!
        h.backend.failNext = IllegalArgumentException("resolution is required (720p, 1080p, 1440p or 4k)")
        runCatching { h.deps.lifecycle.confirmAndSubmit(plan.id, plan.fingerprint()) }
        val second = o.send(conv, "¿Qué ha pasado?") { events += it }
        assertTrue("resolution" in second.text)
        assertEquals(1, h.deps.orders.all().size, "una sola tarjeta durante todo el flujo")
        assertEquals(1, events.filterIsInstance<UiEvent.PlanReady>().map { it.orderId }.distinct().size)
        assertEquals(OrderStatus.AWAITING_CONFIRMATION, h.deps.orders.get(orderId)!!.status)
        assertEquals(0, h.backend.submitted.size, "el agente nunca envía")
    }

    @Test fun `un envio antiguo fallido se corrige con un toque sin perder el video`() = runTest {
        val h = harness()
        // Plan de 1.9.8 tal como está en el S25: prompt sobrante, enviado y fallido.
        val old = GenerationPlan("plan_old", TENCENT, "Tencent Video Upscaler", "VIDEO", prompt = "Upscale to 4K",
            params = mapOf("template" to "live-action-enhance-4k", "keep_metadata" to "true", "short" to "2160"),
            effectiveParams = mapOf("template" to "live-action-enhance-4k"), media = listOf(MediaRef("video", "file:///clip.mp4", "video/mp4", null, "video_url")), createdAt = 0)
        h.guard.registerSubmitted(old)
        val order = h.deps.orders.upsertPlan(old)
        h.deps.orders.beginSubmit(order.id, order.fingerprint); h.deps.orders.accepted(order.id, "job_old")
        h.deps.orders.jobUpdate(order.id, "FAILED", error = "generate task failed, tencent-upscaler: resolution is required (720p, 1080p, 1440p or 4k)")
        val d = assertNotNull(ErrorDiagnosis.diagnose(h.deps.orders.get(order.id)!!.lastFailure!!.message, TENCENT_SCHEMA, old))
        val fix = assertNotNull(d.fix, "un campo deducido ofrece «preparar de nuevo»")
        h.deps.orders.annotateFailure(order.id, d.explanation, fix)
        val (revised, built) = h.deps.reviseOrder(order.id, com.vidadomu.nexus.agent.core.tools.PlanEdit(params = fix.params), com.vidadomu.nexus.agent.core.orders.Actor.USER)
        val plan = (built as com.vidadomu.nexus.agent.core.tools.PlanBuild.Ready).plan
        assertEquals(order.id, revised.id)
        assertEquals(OrderStatus.AWAITING_CONFIRMATION, revised.status)
        assertEquals(2, revised.attempt)
        assertEquals("", plan.prompt, "el prompt sobrante se retira, no bloquea")
        assertEquals("4k", plan.effectiveParams["resolution"])
        assertEquals("2160", plan.params["short"], "short válido en 4K se conserva")
        assertEquals(listOf("file:///clip.mp4"), plan.media.map { it.uri })
        assertTrue(plan.id != old.id, "el plan enviado no se reutiliza")
        assertEquals(0, h.backend.submitted.size)
    }

    /** Error real del intento 4 en el S25 (2026-10-16): el backend exige fps aunque el schema permite omitirlo. */
    @Test fun `fps se mide del video original, cambia la tarifa y sin medida no se envia`() = runTest {
        val h = harness(); val att = video(h); val s = AgentSession("c", AgentMode.AGENT).apply { attachments += att }
        h.frameRate = 59.94
        prepare(h, s, att)
        val sixty = h.guard.plans().single()
        assertEquals("60", sixty.params["fps"])
        assertTrue(abs(sixty.estimate!!.amountUsd!! - 30 * 0.036542 * 2) < 1e-9, "más de 30 fps duplica la tarifa publicada")
        h.frameRate = null
        val unknown = PrepareGenerationTool(h.deps).run(json("""{"model_id":"${TENCENT.modelId}","params":{"template":"anime-enhance-4k"},"media":[{"role":"video","attachment_id":"${att.id}"}],"new_copy":true}"""), s)
        assertTrue(unknown.isError, "sin fps medidos el encargo no es enviable")
        assertTrue("fps" in unknown.result.toString())
        val chosen = PrepareGenerationTool(h.deps).run(json("""{"model_id":"${TENCENT.modelId}","params":{"template":"anime-enhance-4k","fps":24},"media":[{"role":"video","attachment_id":"${att.id}"}],"new_copy":true}"""), s)
        assertFalse(chosen.isError, "un fps elegido explícitamente no necesita medida")
        val plan = (chosen.events.single() as UiEvent.PlanReady).plan
        assertEquals("24", plan.params["fps"])
        val d = assertNotNull(ErrorDiagnosis.diagnose("generate task failed, tencent-upscaler: fps must be an integer between 1 and 100",
            TENCENT_SCHEMA, plan.copy(params = plan.params - "fps")))
        assertEquals("fps", d.field)
        assertNotNull(d.fix, "ofrece preparar de nuevo con los fps del original")
        assertNull(ErrorDiagnosis.diagnose("fps must be an integer between 1 and 100", TENCENT_SCHEMA, plan)!!.fix, "si ya se envió un fps no se inventa otro")
    }

    @Test fun `preparar un encargo entrega la guia especifica del modelo una sola vez`() = runTest {
        val h = harness(); val att = video(h); val s = AgentSession("c", AgentMode.AGENT).apply { attachments += att }
        val first = prepare(h, s, att).result.jsonObject["model_guidance"]!!.jsonObject
        assertEquals("tencent-video-upscaler", first["skill"]!!.jsonPrimitive.content)
        assertTrue("short=0" in first["constraints"]!!.jsonPrimitive.content, first.toString())
        assertTrue("prompt" in first["common_mistakes"]!!.jsonPrimitive.content)
        val again = prepare(h, s, att, extra = ""","new_copy":true""").result.jsonObject["model_guidance"]!!.jsonObject
        assertNull(again["constraints"], "no se repite en la misma conversación")
    }

    @Test fun `la skill de Tencent cubre el modelo y explica que no hay prompt`() {
        val skill = assertNotNull(realSkills().forModel(TENCENT.modelId))
        assertEquals("tencent-video-upscaler", skill.id)
        val upscale = assertNotNull(skill.subsection("Plantillas por tarea", com.vidadomu.nexus.agent.core.skills.TASK_TEMPLATE_KEYS.getValue("upscale")))
        assertTrue("template" in upscale, upscale)
        assertTrue("SIN `prompt`" in skill.section("Estructura de prompt")!!)
    }

    @Test fun `get_ui_state expone lo que pinta la tarjeta y el error literal`() = runTest {
        val h = harness(); val att = video(h); val s = AgentSession("c", AgentMode.AGENT).apply { attachments += att }
        prepare(h, s, att)
        val o = h.deps.orders.all().single()
        h.deps.orders.rendered(o.id, "Pendiente · botón «Generar · 1,10 US$»")
        val out = GetUiStateTool(h.deps).run(JsonObject(emptyMap()), s).result.toString()
        assertTrue("botón «Generar" in out, out)
        assertTrue("\"can_send\":true" in out, out)
    }
}
