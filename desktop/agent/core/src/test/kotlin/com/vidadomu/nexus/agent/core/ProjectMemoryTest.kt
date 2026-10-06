package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.agent.AgentOrchestrator
import com.vidadomu.nexus.agent.core.assets.AssetIndex
import com.vidadomu.nexus.agent.core.config.AgentConfig
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider.Companion.text
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider.Companion.tools
import com.vidadomu.nexus.agent.core.tools.NexusToolkit
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** Memoria por proyecto: el asistente la guarda, la lee en cada mensaje y etiqueta lo generado. */
class ProjectMemoryTest {

    private val config = AgentConfig(provider = "scripted", modelId = "test-model", region = "eu-west-1", endpoint = "http://proxy", maxToolRounds = 6)
    private fun orchestrator(h: Harness, llm: ScriptedLlmProvider) =
        AgentOrchestrator(llm, config, NexusToolkit.all(h.deps), h.skills, h.assets, today = { "2026-10-02" })

    @Test fun `el asistente guarda la memoria y el siguiente mensaje la lleva en el prompt de sistema`() = runTest {
        val h = Harness()
        h.assets.createProject("Mateo y Vidal")
        val llm = ScriptedLlmProvider(listOf(
            { r ->
                assertTrue("open_project" in r.toolNames() && "update_project_memory" in r.toolNames())
                tools(use("o", "open_project", """{"project":"mateo y vidal"}"""))
            },
            { r ->
                assertFalse(r.result("o").isError)
                tools(use("u", "update_project_memory", """{"synopsis":"Corto sobre dos hermanos que reconstruyen un faro abandonado.","visual_style":"Anamórfico 2.39:1, luz dorada, grano fino","add_rules":["Mateo siempre con chaqueta verde"],"characters":[{"name":"Mateo","description":"9 años, pelo rizado castaño, chaqueta verde"}],"add_note":"El final es abierto"}"""))
            },
            { r ->
                val saved = r.resultJson("u")["saved"].toString()
                assertTrue("sinopsis" in saved && "personajes" in saved, saved)
                text("Guardado en la memoria de Mateo y Vidal.")
            },
            { r ->
                val sys = r.system.joinToString("\n")
                assertTrue("MEMORIA DEL PROYECTO ACTIVO" in sys, sys.takeLast(600))
                assertTrue("faro abandonado" in sys && "chaqueta verde" in sys && "Mateo" in sys && "El final es abierto" in sys)
                text("ok")
            },
        ))
        val o = orchestrator(h, llm)
        val conv = o.newConversation(mode = AgentMode.COPILOT)
        o.send(conv, "métete en mateo y vidal: va de dos hermanos y un faro", emptyList())
        val p = h.assets.findProject("Mateo y Vidal")!!
        assertEquals(p.id, conv.session.activeProjectId)
        assertEquals("Corto sobre dos hermanos que reconstruyen un faro abandonado.", p.memory.synopsis)
        assertEquals(listOf("Mateo siempre con chaqueta verde"), p.memory.rules)
        assertEquals(p.id, h.assets.findCharacter("Mateo", p.id)?.projectId)
        o.send(conv, "siguiente plano", emptyList())
    }

    @Test fun `en modo asistente no se escribe la memoria`() = runTest {
        val h = Harness()
        val llm = ScriptedLlmProvider(listOf({ r -> assertFalse("update_project_memory" in r.toolNames()); assertTrue("open_project" in r.toolNames()); text("solo leo") }))
        orchestrator(h, llm).let { it.send(it.newConversation(mode = AgentMode.ASSISTANT), "hola", emptyList()) }
    }

    @Test fun `quitar reglas y recorte de notas`() = runTest {
        val h = Harness()
        val p = h.assets.createProject("X")
        h.assets.updateProjectMemory(p.id) { it.copy(rules = listOf("Siempre de noche", "Sin música", "siempre de noche")) }
        assertEquals(2, h.assets.findProject(p.id)!!.memory.rules.size, "reglas duplicadas fuera")
        h.assets.updateProjectMemory(p.id) { m -> m.copy(notes = (1..200).map { com.vidadomu.nexus.agent.core.assets.ProjectNote(it.toLong(), "n$it") }) }
        val notes = h.assets.findProject(p.id)!!.memory.notes
        assertEquals(AssetIndex.MAX_NOTES, notes.size); assertEquals("n200", notes.last().text)
        val ctx = h.assets.projectContext(p.id, maxChars = 400)!!
        assertTrue(ctx.length <= 400 + 60 && "n200" in ctx, ctx)
    }

    @Test fun `lo generado a mano mientras el proyecto esta activo hereda el proyecto y persiste`() = runTest {
        val dir = Files.createTempDirectory("pm").toFile()
        var now = 1_000L
        val idx = AssetIndex(java.io.File(dir, "index.json"), clock = { now })
        val a = idx.createProject("A"); val b = idx.createProject("B")
        idx.startProjectSession(a.id); now = 2_000
        idx.startProjectSession(a.id) // idempotente
        now = 3_000; idx.startProjectSession(b.id)
        now = 4_000; idx.startProjectSession(null)
        assertNull(idx.projectAt(500))
        assertEquals(a.id, idx.projectAt(1_500)); assertEquals(a.id, idx.projectAt(2_999))
        assertEquals(b.id, idx.projectAt(3_500)); assertNull(idx.projectAt(4_500))
        idx.updateProjectMemory(a.id) { it.copy(synopsis = "Sinopsis A") }
        val again = AssetIndex(java.io.File(dir, "index.json"))
        assertEquals("Sinopsis A", again.findProject("A")!!.memory.synopsis)
        assertEquals(b.id, again.projectAt(3_500))
    }
}
