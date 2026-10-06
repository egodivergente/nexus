package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.agent.AgentOrchestrator
import com.vidadomu.nexus.agent.core.assets.AssetQuery
import com.vidadomu.nexus.agent.core.assets.AssetRole
import com.vidadomu.nexus.agent.core.assets.AssetType
import com.vidadomu.nexus.agent.core.config.AgentConfig
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider.Companion.text
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider.Companion.tools
import com.vidadomu.nexus.agent.core.spend.JobStatus
import com.vidadomu.nexus.agent.core.spend.PlanState
import com.vidadomu.nexus.agent.core.spend.SpendViolation
import com.vidadomu.nexus.agent.core.tools.Attachment
import com.vidadomu.nexus.agent.core.tools.NexusToolkit
import com.vidadomu.nexus.agent.core.tools.UiEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Criterio de éxito del encargo, de punta a punta con un LLM de guion: keyframe de Mateo y Julián →
 * plano cinematográfico de 8 s. Comprueba que cada paso usa datos reales (registro, schema, skills,
 * precio), que NADA se envía sin el toque en GENERAR, y que el resultado queda asociado con lineage.
 */
class MateoJulianFlowTest {

    @Test fun `keyframe a plano de 8 s con confirmacion y lineage`() = runTest {
        val h = Harness()
        val project = h.assets.createProject("El Faro", "Cortometraje")
        val kf = Attachment("att_1", "content://media/external/images/media/4211", "image/png", "faro_e03_p12_keyframe.png", 1920, 1080)
        var chosen = ""
        var provider = ""
        var suggested: JsonObject? = null
        var planId = ""
        val prompt = "Slow cinematic dolly-in toward the two men at the lighthouse railing. Mateo speaks quietly, Julián listens and glances down. " +
            "Soft overcast light, gentle wind in their hair, shallow depth of field. Keep both faces, clothing and proportions exactly as in the first frame."

        val llm = ScriptedLlmProvider(listOf(
            { r ->
                assertTrue(r.messages.last().text().contains("attachment_id=att_1"))
                assertTrue(r.system.any { it.contains("Proyecto activo: El Faro") })
                tools(use("i", "inspect_asset_metadata", """{"attachment_id":"att_1"}"""), use("p", "list_projects"))
            },
            { tools(use("r", "recommend_models", """{"modality":"VIDEO","task":"image_to_video","duration_sec":8,"image_inputs":1,
                "priorities":["identity_preservation","subtle_performance","cinematic_look","camera_control"],"budget":"medium"}""")) },
            { r ->
                val rec = r.resultJson("r")["recommended"] as JsonObject
                chosen = rec.path("ref", "modelId").jsonPrimitive.content
                provider = rec.path("ref", "providerId").jsonPrimitive.content
                suggested = rec["suggestedParams"] as JsonObject
                val skill = rec["skillId"]!!.jsonPrimitive.content
                tools(
                    use("s1", "load_skill", """{"skill_id":"$skill","section":"Estructura de prompt"}"""),
                    use("s2", "load_skill", """{"skill_id":"cinematic_director"}"""),
                    use("s3", "load_skill", """{"skill_id":"character_consistency"}"""),
                    use("g", "get_model_schema", """{"provider_id":"$provider","model_id":"$chosen"}"""),
                )
            },
            { r ->
                listOf("s1", "s2", "s3", "g").forEach { assertTrue(!r.result(it).isError, "$it: ${r.result(it).content}") }
                tools(use("c", "create_generation_prompt", """{"provider_id":"$provider","model_id":"$chosen","task":"image_to_video","prompt":${kotlinx.serialization.json.JsonPrimitive(prompt)}}"""))
            },
            { r ->
                val w = r.resultJson("c")["warnings"].toString()
                assertTrue(w.contains("MOVIMIENTO"), w)
                tools(use("v", "validate_parameters", """{"provider_id":"$provider","model_id":"$chosen","params":$suggested}"""))
            },
            { r ->
                assertEquals("true", r.resultJson("v")["ok"]!!.jsonPrimitive.content)
                tools(use("prep", "prepare_generation", """{"provider_id":"$provider","model_id":"$chosen","prompt":${kotlinx.serialization.json.JsonPrimitive(prompt)},
                    "params":$suggested,"media":[{"role":"first_frame","attachment_id":"att_1"}],"task":"image_to_video",
                    "rationale":"Mejor equilibrio entre identidad y cámara para 8 s.","scene":"3","shot":"12","characters":["Mateo","Julián"]}"""))
            },
            { r ->
                val res = r.result("prep"); assertTrue(!res.isError, res.content.toString())
                planId = res.content.path("plan", "plan_id").jsonPrimitive.content
                tools(use("sub", "submit_generation", """{"plan_id":"$planId"}"""))
            },
            { r ->
                assertEquals("false", r.resultJson("sub")["submitted"]!!.jsonPrimitive.content)
                text("Preparado: revisa la tarjeta y pulsa GENERAR si te convence.")
            },
            // Segundo turno: el usuario pregunta por el resultado.
            { tools(use("st", "get_generation_status", """{"job_id":"job_1"}""")) },
            { r -> assertEquals("COMPLETED", r.resultJson("st")["status"]!!.jsonPrimitive.content); text("Terminado e importado al plano 12.") },
            // Tercer turno: el LLM intenta reutilizar el plan para otra generación.
            { tools(use("again", "submit_generation", """{"plan_id":"$planId"}""")) },
            { r -> assertTrue(r.result("again").isError); text("Para otra versión preparo un plan nuevo.") },
        ))

        val o = AgentOrchestrator(llm, AgentConfig(modelId = "opus-test", region = "eu-west-1", endpoint = "http://proxy"), NexusToolkit.all(h.deps), h.skills, h.assets)
        val conv = o.newConversation(mode = AgentMode.COPILOT, activeProjectId = project.id)
        val turn = o.send(conv, "Con este keyframe de Mateo y Julián (escena 3, plano 12) quiero un plano cinematográfico de 8 segundos: travelling lento hacia ellos mientras hablan en voz baja, sin cambiar sus caras.", listOf(kf))

        // 1) Nada enviado: solo plan + petición de confirmación.
        assertTrue(h.backend.submitted.isEmpty())
        val ready = turn.events.filterIsInstance<UiEvent.PlanReady>().single()
        assertEquals(planId, turn.events.filterIsInstance<UiEvent.AwaitingConfirmation>().single().planId)
        assertEquals(PlanState.AWAITING_CONFIRMATION, h.guard.state(planId))
        val plan = ready.plan
        assertEquals(chosen, plan.ref.modelId)
        assertTrue(MODELS.any { it.ref == plan.ref }, "modelo del registro")
        assertEquals("image", plan.media.single().fieldKey, "keyframe en el campo del schema")
        assertTrue(plan.params["duration"] != null && plan.effectiveParams.values.none { it.isBlank() || it == "–" })
        assertNotNull(plan.estimate?.amountUsd, "coste estimado visible antes de confirmar")
        assertEquals(listOf("inspect_asset_metadata", "list_projects", "recommend_models", "load_skill", "load_skill", "load_skill", "get_model_schema",
            "create_generation_prompt", "validate_parameters", "prepare_generation", "submit_generation"), turn.toolCalls)

        // 2) El usuario pulsa GENERAR: un envío, con lineage registrado.
        val submitted = h.deps.lifecycle.confirmAndSubmit(planId)
        assertEquals("job_1", submitted.jobId)
        assertEquals(1, h.backend.submitted.size)
        assertFailsWith<SpendViolation> { h.deps.lifecycle.confirmAndSubmit(planId) }
        assertEquals(1, h.backend.submitted.size, "una confirmación = una generación")
        val keyframe = h.assets.assetByUri(kf.uri)!!
        assertEquals(AssetRole.KEYFRAME, keyframe.role)
        val shot = h.assets.snapshot().shots.single { it.code == "12" }
        assertEquals(shot.id, keyframe.shotId)
        assertEquals(setOf("Mateo", "Julián"), h.assets.snapshot().characters.map { it.name }.toSet())

        // 3) Termina en el proveedor; el asistente consulta e importa.
        h.backend.statuses["job_1"] = JobStatus("job_1", "COMPLETED", 1f, listOf("content://nexus/out/job_1.mp4"), actualCostUsd = 0.76)
        o.send(conv, "¿Ha terminado?")
        val out = h.assets.assetByUri("content://nexus/out/job_1.mp4")!!
        assertEquals(AssetType.VIDEO, out.type)
        assertEquals(AssetRole.OUTPUT, out.role)
        assertEquals(project.id, out.projectId)
        assertEquals(shot.id, out.shotId)
        assertEquals(keyframe.id, out.parentAssetId, "lineage: vídeo ← keyframe")
        val gen = h.assets.generation("job_1")!!
        assertEquals("COMPLETED", gen.status)
        assertEquals(planId, gen.agentPlanId)
        assertEquals(prompt, gen.prompt)
        assertEquals(0.76, gen.actualCostUsd)
        assertEquals(listOf(out.id), gen.outputAssetIds)
        assertEquals("VIDEO", h.assets.shotStatus(shot.id))
        assertEquals(listOf(out.id), h.assets.search(AssetQuery(character = "julian", type = AssetType.VIDEO)).map { it.id })

        // 4) Reutilizar el plan no lanza otra generación.
        o.send(conv, "Hazme otra igual")
        assertEquals(1, h.backend.submitted.size)
    }

    @Test fun `fallo al enviar exige pulsar GENERAR de nuevo`() = runTest {
        val h = Harness()
        val llm = ScriptedLlmProvider(listOf(
            { tools(use("prep", "prepare_generation", """{"model_id":"${KLING_STD.modelId}","prompt":"slow dolly-in, keep faces","params":{"duration":"8"},"media":[{"role":"first_frame","attachment_id":"att_1"}]}""")) },
            { text("listo") },
        ))
        val o = AgentOrchestrator(llm, AgentConfig(modelId = "m", region = "r", endpoint = "e"), NexusToolkit.all(h.deps), h.skills, h.assets)
        val t = o.send(o.newConversation(), "genera", listOf(Attachment("att_1", "content://k", "image/jpeg")))
        val id = t.events.filterIsInstance<UiEvent.PlanReady>().single().plan.id
        h.backend.failNext = java.io.IOException("sin red")
        assertFailsWith<java.io.IOException> { h.deps.lifecycle.confirmAndSubmit(id) }
        assertEquals(PlanState.PREPARED, h.guard.state(id))
        assertTrue(h.backend.submitted.isEmpty())
        h.deps.lifecycle.confirmAndSubmit(id)
        assertEquals(1, h.backend.submitted.size)
    }
}
