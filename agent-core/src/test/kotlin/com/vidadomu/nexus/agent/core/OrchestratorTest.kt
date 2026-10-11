package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.agent.AgentOrchestrator
import com.vidadomu.nexus.agent.core.agent.SystemPrompt
import com.vidadomu.nexus.agent.core.config.AgentConfig
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.llm.LlmException
import com.vidadomu.nexus.agent.core.llm.Role
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider.Companion.text
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider.Companion.tools
import com.vidadomu.nexus.agent.core.tools.AgentSession
import com.vidadomu.nexus.agent.core.tools.AgentTool
import com.vidadomu.nexus.agent.core.tools.Attachment
import com.vidadomu.nexus.agent.core.tools.NexusToolkit
import com.vidadomu.nexus.agent.core.tools.ToolCategory
import com.vidadomu.nexus.agent.core.tools.ToolOutcome
import com.vidadomu.nexus.agent.core.tools.UiEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class OrchestratorTest {
    private val config = AgentConfig(provider = "scripted", modelId = "test-model", region = "eu-west-1", endpoint = "http://proxy", maxToolRounds = 4)
    private val kf = Attachment("att_1", "content://media/kf.png", "image/png", "kf.png", 1920, 1080)

    private fun orchestrator(h: Harness, llm: ScriptedLlmProvider, cfg: AgentConfig = config) =
        AgentOrchestrator(llm, cfg, NexusToolkit.all(h.deps), h.skills, h.assets, today = { "2026-10-02" })

    @Test fun `ninguna herramienta confirma ni paga`() {
        val names = NexusToolkit.all(Harness().deps).map { it.name }
        assertEquals(27, names.size)
        assertTrue(names.none { Regex("confirm|approve|pay|charge").containsMatchIn(it) })
        val evil = object : AgentTool {
            override val name = "confirm_generation"; override val description = ""; override val category = ToolCategory.PLAN
            override val inputSchema = JsonObject(emptyMap())
            override suspend fun run(input: JsonObject, session: AgentSession) = ToolOutcome(JsonObject(emptyMap()))
        }
        assertFailsWith<IllegalArgumentException> {
            AgentOrchestrator(ScriptedLlmProvider(emptyList()), config, listOf(evil), realSkills(), Harness().assets)
        }
    }

    @Test fun `asistente solo ve lectura y no puede preparar`() = runTest {
        val h = Harness()
        val llm = ScriptedLlmProvider(listOf(
            { r ->
                assertFalse("prepare_generation" in r.toolNames())
                assertFalse("associate_asset_to_project" in r.toolNames())
                assertTrue("recommend_models" in r.toolNames())
                tools(use("u1", "prepare_generation", """{"model_id":"${KLING_PRO.modelId}","prompt":"x","media":[{"role":"first_frame","attachment_id":"att_1"}]}"""))
            },
            { r -> assertTrue(r.result("u1").isError); text("En modo Asistente solo recomiendo.") },
        ))
        val o = orchestrator(h, llm)
        val conv = o.newConversation(mode = AgentMode.ASSISTANT)
        val t = o.send(conv, "prepáralo", listOf(kf))
        assertTrue(h.guard.plans().isEmpty())
        assertTrue(t.events.isEmpty())
    }

    @Test fun `copiloto propone organizar y crea proyecto directamente`() = runTest {
        val h = Harness()
        val llm = ScriptedLlmProvider(listOf(
            { tools(use("c", "create_project", """{"name":"El Faro"}""")) },
            { tools(use("a", "associate_asset_to_project", """{"attachment_id":"att_1","project":"El Faro","scene":"3","shot":"12","characters":["Mateo","Julián"]}""")) },
            { r -> assertTrue(r.resultJson("a")["proposed"]!!.jsonPrimitive.content.toBoolean()); text("Te propongo asociarlo.") },
        ))
        val o = orchestrator(h, llm)
        val conv = o.newConversation(mode = AgentMode.COPILOT)
        val t = o.send(conv, "organízalo", listOf(kf))
        assertEquals("El Faro", h.assets.projects().single().name)
        assertTrue(h.assets.snapshot().assets.isEmpty(), "la asociación no se aplica sin el usuario")
        val proposal = t.events.filterIsInstance<UiEvent.ProposedAction>().single()
        assertTrue(proposal.description.contains("plano 12"))
        o.applyPending(conv, proposal.id)
        val a = h.assets.snapshot().assets.single()
        assertEquals(h.assets.projects().single().id, a.projectId)
        assertEquals(2, a.characterIds.size)
        assertFailsWith<IllegalArgumentException> { o.applyPending(conv, proposal.id) }
    }

    @Test fun `agente aplica organizacion directamente`() = runTest {
        val h = Harness()
        h.assets.createProject("El Faro")
        val llm = ScriptedLlmProvider(listOf(
            { tools(use("a", "associate_asset_to_project", """{"attachment_id":"att_1","project":"el faro","shot":"12"}""")) },
            { text("Hecho.") },
        ))
        val o = orchestrator(h, llm)
        o.send(o.newConversation(mode = AgentMode.AGENT), "organízalo", listOf(kf))
        assertEquals(1, h.assets.snapshot().assets.size)
        assertEquals("12", h.assets.snapshot().shots.single().code)
    }

    @Test fun `tope de rondas corta el bucle y la conversacion sigue valida`() = runTest {
        val h = Harness()
        val loop = { _: com.vidadomu.nexus.agent.core.llm.LlmRequest -> tools(use("l${System.nanoTime()}", "list_projects")) }
        val llm = ScriptedLlmProvider(List(5) { loop } + listOf({ r ->
            // Tras cortar, el siguiente mensaje del usuario se fusiona: los roles siguen alternando.
            r.messages.zipWithNext().forEach { (a, b) -> assertTrue(a.role != b.role, "roles consecutivos iguales") }
            assertEquals(Role.USER, r.messages.first().role)
            text("ok")
        }))
        val o = orchestrator(h, llm)
        val conv = o.newConversation()
        val t = o.send(conv, "bucle")
        assertEquals("MAX_ROUNDS", t.stop)
        assertEquals(4, t.toolRounds)
        assertEquals("ok", o.send(conv, "sigue").text)
    }

    @Test fun `error del LLM deshace el mensaje`() = runTest {
        val h = Harness()
        val llm = ScriptedLlmProvider(listOf({ throw LlmException(LlmException.Kind.AUTH, "token") }, { text("hola") }))
        val o = orchestrator(h, llm)
        val conv = o.newConversation()
        assertFailsWith<LlmException> { o.send(conv, "uno") }
        assertTrue(conv.history().isEmpty())
        assertEquals("hola", o.send(conv, "dos").text)
    }

    @Test fun `modelo o parametro inventado no produce plan`() = runTest {
        val h = Harness()
        val llm = ScriptedLlmProvider(listOf(
            { tools(
                use("m", "prepare_generation", """{"model_id":"openai/sora-9/image-to-video","prompt":"x","media":[{"role":"first_frame","attachment_id":"att_1"}]}"""),
                use("p", "prepare_generation", """{"model_id":"${KLING_PRO.modelId}","prompt":"travelling lento","params":{"duration":8,"camera_speed":"slow"},"media":[{"role":"first_frame","attachment_id":"att_1"}]}"""),
                use("n", "prepare_generation", """{"model_id":"${KLING_PRO.modelId}","prompt":"travelling lento","params":{"duration":8}}"""),
            ) },
            { r ->
                assertTrue(r.result("m").isError)
                assertTrue(r.result("p").isError); assertTrue(r.result("p").content.toString().contains("camera_speed"))
                assertTrue(r.result("n").isError, "Kling i2v exige keyframe")
                text("corrijo")
            },
        ))
        val o = orchestrator(h, llm)
        o.send(o.newConversation(), "genera", listOf(kf))
        assertTrue(h.guard.plans().isEmpty())
    }

    @Test fun `reglas de gasto siempre en el sistema`() = runTest {
        val h = Harness()
        val llm = ScriptedLlmProvider(listOf({ r -> assertTrue(r.system.any { it.contains(SystemPrompt.SPEND_RULES) }); assertEquals("solo esto", r.system.first()); text("ok") }))
        orchestrator(h, llm, config.copy(systemPromptOverride = "solo esto")).let { it.send(it.newConversation(), "hola") }
        assertTrue(SystemPrompt.build(AgentMode.AGENT, null, "", null, "x").joinToString().contains("pulse GENERAR"))
    }

    @Test fun `documento se lee una vez y se reutiliza su resumen`() = runTest {
        val h=Harness()
        val llm=ScriptedLlmProvider(listOf(
            { r -> assertTrue(r.tools.isEmpty()); assertTrue(r.messages.any { m -> m.content.any {it is com.vidadomu.nexus.agent.core.llm.ContentBlock.Document} }); text("Mateo viaja a Madrid en 1920.") },
            { r -> assertFalse(r.messages.any {m -> m.content.any {it is com.vidadomu.nexus.agent.core.llm.ContentBlock.Document}}); assertTrue(r.messages.any {"1920" in it.text()}); text("Resumen final") },
            { r -> assertFalse(r.messages.any {m -> m.content.any {it is com.vidadomu.nexus.agent.core.llm.ContentBlock.Document}}); text("Continuacion") }
        ))
        val o=AgentOrchestrator(llm,config,NexusToolkit.all(h.deps),h.skills,h.assets,
            documents=com.vidadomu.nexus.agent.core.agent.AttachmentDocumentLoader {com.vidadomu.nexus.agent.core.llm.ContentBlock.Document("pdf","Script",byteArrayOf(1,2))})
        val conv=o.newConversation()
        assertEquals("Resumen final",o.send(conv,"Resume",listOf(Attachment("doc","file:///doc.pdf","application/pdf","doc.pdf"))).text)
        assertEquals("Continuacion",o.send(conv,"Sigue").text)
    }

    @Test fun `adjunto se describe al LLM con su id`() = runTest {
        val h = Harness()
        val llm = ScriptedLlmProvider(listOf({ r -> assertTrue(r.messages.last().text().contains("attachment_id=att_1")); text("visto") }))
        val o = orchestrator(h, llm)
        val conv = o.newConversation()
        o.send(conv, "mira", listOf(kf))
        assertEquals("content://media/kf.png", conv.session.attachment("att_1")?.uri)
        assertNull(conv.session.attachment("att_2"))
    }
}
