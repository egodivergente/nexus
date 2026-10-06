package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.skills.SkillKind
import com.vidadomu.nexus.agent.core.tools.AgentSession
import com.vidadomu.nexus.agent.core.skills.TASK_TEMPLATE_KEYS
import com.vidadomu.nexus.agent.core.tools.CompareModelFamiliesTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Skills v2: cobertura de todo el catálogo, estructura mínima y herramienta de comparativa. */
class SkillsV2Test {

    private val coverage: Map<String, List<String>> by lazy {
        val raw = Thread.currentThread().contextClassLoader.getResource("skill_coverage.json")!!.readText()
        Json.parseToJsonElement(raw).jsonObject.mapValues { (_, v) -> v.jsonArray.map { it.jsonPrimitive.content } }
    }

    private val traits = setOf(
        "face_consistency", "identity_preservation", "subtle_performance", "natural_motion", "dynamic_action", "camera_control",
        "cinematic_look", "prompt_adherence", "text_rendering", "photorealism", "stylized", "native_audio", "multi_shot",
        "long_duration", "fast", "low_cost", "high_resolution", "physics", "multi_character",
    )

    @Test fun `cada id del catalogo cae en la skill de su familia`() {
        val s = realSkills()
        assertTrue(s.errors.isEmpty(), "errores: ${s.errors}")
        val wrong = coverage.flatMap { (fid, ids) -> ids.filter { s.forModel(it)?.id != fid }.map { "$it → ${s.forModel(it)?.id} (esperado $fid)" } }
        assertTrue(wrong.isEmpty(), wrong.joinToString("\n"))
    }

    @Test fun `skills de modelo v2 tienen las secciones clave y vocabulario controlado`() {
        val s = realSkills()
        val problems = s.models().flatMap { m ->
            buildList {
                listOf("Resumen", "Cuándo elegirlo", "Linaje", "Estructura de prompt", "Plantillas por tarea", "Idioma", "Errores habituales", "Fuentes")
                    .filter { m.section(it) == null }.forEach { add("${m.id}: falta «$it»") }
                (m.strengths + m.weaknesses + m.unverifiedTags).filter { it !in traits }.forEach { add("${m.id}: rasgo fuera de vocabulario «$it»") }
                (m.idealTasks + m.avoidTasks).filter { it !in TASK_TEMPLATE_KEYS }.forEach { add("${m.id}: tarea fuera de vocabulario «$it»") }
                if (m.sources.isEmpty()) add("${m.id}: sin fuentes")
                if (m.version < 2) add("${m.id}: versión ${m.version} < 2")
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
        assertTrue(s.models().size >= 23, "solo ${s.models().size} skills de modelo")
    }

    @Test fun `plantilla de tarea concreta para image-to-video de kling y text-to-image de gpt-image`() {
        val s = realSkills()
        val k = assertNotNull(s.get("kling")!!.subsection("Plantillas por tarea", TASK_TEMPLATE_KEYS.getValue("image_to_video")))
        assertTrue(k.lowercase().contains("image-to-video") || k.lowercase().contains("imagen a vídeo"), k.take(200))
        assertNotNull(s.get("gpt-image")!!.subsection("Plantillas por tarea", TASK_TEMPLATE_KEYS.getValue("text_to_image")))
    }

    @Test fun `compare_model_families devuelve criterio de cada familia`() = runTest {
        val h = Harness()
        val out = CompareModelFamiliesTool(h.deps).run(
            JsonObject(mapOf("families" to JsonArray(listOf(JsonPrimitive("kling"), JsonPrimitive("seedance"))), "task" to JsonPrimitive("image_to_video"))),
            AgentSession("s1", AgentMode.COPILOT),
        ).result.jsonObject
        val fams = out["families"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("kling", "seedance"), fams.map { it["id"]!!.jsonPrimitive.content })
        fams.forEach { assertNotNull(it["when_to_choose"], "${it["id"]} sin when_to_choose") }
        val bad = CompareModelFamiliesTool(h.deps).run(JsonObject(mapOf("families" to JsonArray(listOf(JsonPrimitive("nope"))))), AgentSession("s2", AgentMode.COPILOT))
        assertTrue(bad.result.toString().contains("desconocidas"))
        assertTrue(realSkills().models().all { it.kind == SkillKind.MODEL })
    }
}
