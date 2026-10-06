package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.assets.Asset
import com.vidadomu.nexus.agent.core.assets.AssetIndex
import com.vidadomu.nexus.agent.core.assets.AssetQuery
import com.vidadomu.nexus.agent.core.assets.AssetRole
import com.vidadomu.nexus.agent.core.assets.AssetType
import com.vidadomu.nexus.agent.core.router.TaskSpec
import com.vidadomu.nexus.agent.core.skills.SkillKind
import com.vidadomu.nexus.agent.core.skills.SkillParser
import com.vidadomu.nexus.agent.core.skills.SkillSource
import com.vidadomu.nexus.agent.core.skills.SkillStore
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SkillsRouterAssetsTest {

    @Test fun `todas las skills reales parsean`() {
        val s = realSkills()
        assertTrue(s.errors.isEmpty(), "errores: ${s.errors}")
        assertTrue(s.models().size >= 23, "modelos: ${s.models().size}")
        assertTrue(s.expertise().size >= 11)
        assertEquals("kling", s.forModel(KLING_PRO.modelId)?.id)
        assertEquals("seedance", s.forModel(SEEDANCE.modelId)?.id)
        assertEquals("veo", s.forModel(VEO.modelId)?.id)
        assertNull(s.forModel(BROKEN.modelId))
        assertNotNull(s.get("kling")!!.section("Estructura de prompt"))
        assertTrue(s.catalogText().contains("cinematic_director"))
    }

    @Test fun `skill nueva en disco sustituye a la empaquetada sin recompilar`() {
        val packaged = SkillSource { listOf("assets/kling" to skill("kling", 1, "vieja")) }
        val updated = SkillSource { listOf("files/kling" to skill("kling", 2, "nueva")) }
        val store = SkillStore(listOf(packaged, updated))
        assertEquals(2, store.get("kling")!!.version)
        assertTrue(store.get("kling")!!.body.contains("nueva"))
        assertEquals("files/kling", store.get("kling")!!.origin)
    }

    @Test fun `skill rota no tumba el resto`() {
        val store = SkillStore(listOf(SkillSource { listOf("a" to "sin cabecera", "b" to skill("ok", 1, "x")) }))
        assertEquals(1, store.errors.size)
        assertNotNull(store.get("ok"))
    }

    @Test fun `skill de modelo sin match es invalida`() {
        assertFailsWith<Exception> { SkillParser.parse("---\nid: x\nkind: model\n---\ncuerpo") }
        assertEquals(SkillKind.EXPERTISE, SkillParser.parse("---\nid: y\nkind: expertise\n---\ncuerpo").kind)
    }

    private fun skill(id: String, v: Int, body: String) = "---\nid: $id\nkind: model\nversion: $v\nmatch: ['^kwaivgi/']\nmodalities: [VIDEO]\n---\n## Resumen\n$body\n"

    @Test fun `router plano cinematografico 8 s desde keyframe`() = runTest {
        val h = Harness()
        val r = h.router.route(TaskSpec(
            modality = "VIDEO", task = "image_to_video", durationSec = 8, imageInputs = 1,
            priorities = listOf("identity_preservation", "subtle_performance", "cinematic_look", "camera_control"), budget = "medium",
        ))
        val rec = assertNotNull(r.recommended)
        assertTrue(rec.schemaVerified)
        assertTrue(rec.ref != BROKEN && r.alternatives.none { it.ref == BROKEN }, "nunca un modelo con campos obligatorios no soportados")
        assertTrue(rec.ref != SEEDREAM && r.alternatives.none { it.ref == SEEDREAM })
        // La duración sugerida es válida para SU schema (Kling 3–15, Veo 4/6/8, Seedance 5/10).
        (listOf(rec) + r.alternatives).forEach { c ->
            val v = com.vidadomu.nexus.agent.core.schema.ParameterValidator.validate(SCHEMAS.getValue(c.ref), c.suggestedParams)
            assertTrue(v.ok, "${c.ref}: ${v.issues}")
            assertNotNull(c.suggestedParams["duration"])
        }
        assertNotNull(rec.estimate?.amountUsd)
        assertTrue(r.alternatives.isNotEmpty())
        assertTrue(r.alternatives.map { it.skillId }.none { it == rec.skillId } || r.alternatives.size > 1, "alternativas de otra familia primero")
        val seed = (listOf(rec) + r.alternatives).firstOrNull { it.ref == SEEDANCE }
        if (seed != null) assertTrue(seed.warnings.any { it.contains("ajustada") })
    }

    @Test fun `router respeta coste maximo`() = runTest {
        val h = Harness()
        val r = h.router.route(TaskSpec("VIDEO", "image_to_video", durationSec = 8, imageInputs = 1, maxCostUsd = 0.7))
        (listOfNotNull(r.recommended) + r.alternatives).forEach { assertTrue((it.estimate?.amountUsd ?: 0.0) <= 0.7, "${it.ref} ${it.estimate}") }
    }

    @Test fun `indice de assets busca sin tildes y versiona por plano`() = runTest {
        val dir = Files.createTempDirectory("idx").toFile()
        val idx = AssetIndex(java.io.File(dir, "index.json"), clock = { 5L })
        val p = idx.createProject("El Faro")
        val shot = idx.ensureShot(p.id, "12")
        val julian = idx.ensureCharacter("Julián", p.id)
        val k1 = idx.addAsset(Asset("", "content://k1", AssetType.IMAGE, AssetRole.KEYFRAME, displayName = "kf", projectId = p.id, shotId = shot.id, characterIds = listOf(julian.id), createdAt = 1))
        val k2 = idx.addAsset(Asset("", "content://k2", AssetType.IMAGE, AssetRole.KEYFRAME, displayName = "kf2", projectId = p.id, shotId = shot.id, createdAt = 2))
        assertEquals(1, k1.version); assertEquals(2, k2.version)
        assertEquals(k1.id, idx.addAsset(k1.copy(id = "")).id, "misma uri = mismo asset")
        assertEquals(listOf(k1.id), idx.search(AssetQuery(character = "julian")).map { it.id })
        assertEquals(1, idx.shotsWith("el faro", AssetType.IMAGE, AssetType.VIDEO).size)
        assertEquals("KEYFRAME", idx.shotStatus(shot.id))
        // Persistencia: otra instancia lee lo mismo.
        val again = AssetIndex(java.io.File(dir, "index.json"))
        assertEquals(2, again.snapshot().assets.size)
        assertEquals("El Faro", again.findProject("el faro")?.name)
    }
}
