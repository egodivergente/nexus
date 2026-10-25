package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.net.JsonPaths
import com.vidadomu.nexus.agent.core.net.JsonTemplate
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonTemplateTest {
    @Test fun placeholdersKeepTypesAndOmitMissingValues() {
        val template = JsonTemplate.parse("""{"prompt":"{{ prompt }}","duration":"{{duration}}","label":"v={{seed}}!","gone":"{{missing}}","list":["{{a}}","{{missing}}"]}""")
        assertEquals(setOf("prompt", "duration", "seed", "missing", "a"), JsonTemplate.placeholders(template))
        val out = JsonTemplate.render(template, mapOf("prompt" to JsonPrimitive("hola"), "duration" to JsonPrimitive(5),
            "seed" to JsonPrimitive(7), "a" to JsonPrimitive(true)))
        assertEquals(Json.parseToJsonElement("""{"prompt":"hola","duration":5,"label":"v=7!","list":[true]}"""), out)
        assertEquals("/v1/x/abc", JsonTemplate.renderText("/v1/x/{{id}}", mapOf("id" to JsonPrimitive("abc"))))
        assertEquals("{{ not closed", JsonTemplate.renderText("{{ not closed", emptyMap()))
    }

    @Test fun bracketIndexesAndWildcardsResolve() {
        val root = Json.parseToJsonElement("""{"data":[{"url":"u0"},{"url":"u1"}]}""")
        assertEquals("u1", JsonPaths.string(root, "data[1].url"))
        assertEquals(listOf("u0", "u1"), JsonPaths.all(root, "data[*].url").map { it.jsonPrimitive.content })
    }
}
