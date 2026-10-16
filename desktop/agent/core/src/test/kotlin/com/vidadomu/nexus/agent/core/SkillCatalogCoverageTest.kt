package com.vidadomu.nexus.agent.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Cada modelo de imagen, vídeo o audio del catálogo empaquetado debe tener una skill propia:
 * sin ella el agente escribe prompts y elige parámetros de forma genérica.
 */
class SkillCatalogCoverageTest {
    @Test fun `todos los generadores del catalogo tienen una skill especifica`() {
        // Desde agent-core el catálogo está en ../app; desde la copia de escritorio (desktop/agent/core), en ../../../app.
        val catalog = listOf("../app", "../../../app").map { File("$it/src/main/assets/atlas_catalog_snapshot.json") }.firstOrNull { it.isFile }
            ?: File("../app/src/main/assets/atlas_catalog_snapshot.json")
        assertTrue(catalog.isFile, "No se encuentra el catálogo de Atlas desde ${File(".").absoluteFile.normalize()}")
        val models = Json.parseToJsonElement(catalog.readText()).jsonObject["data"]!!.jsonArray.map { it.jsonObject }
            .filter { it["type"]?.jsonPrimitive?.content in setOf("Image", "Video", "Audio") }
            .map { it["model"]!!.jsonPrimitive.content }
        val skills = realSkills()
        val missing = models.filter { skills.forModel(it) == null }.sorted()
        File("build/skill-coverage-missing.txt").apply { parentFile.mkdirs() }.writeText(missing.joinToString("\n"))
        assertTrue(missing.isEmpty(), "${missing.size} de ${models.size} modelos sin skill:\n" + missing.joinToString("\n"))
    }
}
