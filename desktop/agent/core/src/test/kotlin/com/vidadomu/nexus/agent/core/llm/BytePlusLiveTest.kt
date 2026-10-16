package com.vidadomu.nexus.agent.core.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Prueba CONTRA LA API REAL de BytePlus ModelArk. Solo se ejecuta si existe la variable de entorno BYTEPLUS_API_KEY
 * (la clave nunca está en el repositorio ni se escribe en ningún fichero); sin ella se omite. Gasta unos pocos tokens.
 *   $env:BYTEPLUS_API_KEY='…'; .\gradlew.bat :agent-core:test --tests '*BytePlusLiveTest*'
 */
class BytePlusLiveTest {
    private val key = System.getenv("BYTEPLUS_API_KEY").orEmpty().trim()
    private val profile = BrainPresets.byteplus()
    private val schema = buildJsonObject { put("type", "object"); put("properties", buildJsonObject { put("order_id", buildJsonObject { put("type", "string") }) }) }
    private fun user(text: String) = LlmMessage(Role.USER, listOf(ContentBlock.Text(text)))

    @Test fun `conexion y modelo activado`() = runTest {
        assumeTrue(key.isNotEmpty(), "sin BYTEPLUS_API_KEY: prueba real omitida")
        val r = BrainDiscovery.test(profile, { key })
        println("LIVE test(): ok=${r.ok} modelListed=${r.modelListed} models=${r.models.size} :: ${r.message}")
        assertTrue(r.ok, r.message)
    }

    @Test fun `Responses con herramienta, resultado y continuidad`() = runTest {
        assumeTrue(key.isNotEmpty(), "sin BYTEPLUS_API_KEY: prueba real omitida")
        val provider = BrainFactory.create(profile.copy(capabilities = profile.capabilities.copy(streaming = false)), { key })
        val tool = ToolSpec("get_ui_state", "Devuelve el estado real de la interfaz del usuario. Úsala siempre que te pregunten por el estado.", schema)
        val ask = user("Consulta el estado de la interfaz con la herramienta get_ui_state (order_id \"ord_1\") y dime en una frase qué estado tiene.")
        val first = provider.complete(LlmRequest(profile.modelId, listOf("Eres Nexus. Usa las herramientas cuando hagan falta."), listOf(ask), listOf(tool), 1024))
        println("LIVE 1: stop=${first.stopReason} usage=${first.usage} tools=${first.message.toolUses().map { it.name }}")
        val call = first.message.toolUses().firstOrNull() ?: error("El modelo no llamó a la herramienta. Texto: ${first.message.text().take(200)}")
        assertEquals("get_ui_state", call.name)
        val result = LlmMessage(Role.USER, listOf(ContentBlock.ToolResult(call.id, buildJsonObject { put("status", "AWAITING_CONFIRMATION") })))
        val second = provider.complete(LlmRequest(profile.modelId, listOf("Eres Nexus. Usa las herramientas cuando hagan falta."), listOf(ask, first.message, result), listOf(tool), 1024))
        println("LIVE 2: stop=${second.stopReason} text=${second.message.text().take(160)}")
        assertTrue(second.message.text().isNotBlank(), "tras el resultado de la herramienta debe responder con texto")
    }

    @Test fun `streaming entrega texto progresivo`() = runTest {
        assumeTrue(key.isNotEmpty(), "sin BYTEPLUS_API_KEY: prueba real omitida")
        val provider = BrainFactory.create(profile, { key }) as StreamingLlmProvider
        val deltas = mutableListOf<String>()
        val r = provider.completeStreaming(LlmRequest(profile.modelId, emptyList(), listOf(user("Cuenta del 1 al 5 separado por comas.")), emptyList(), 512)) { deltas += it }
        println("LIVE stream: ${deltas.size} trozos, final='${r.message.text().take(80)}' usage=${r.usage}")
        assertTrue(deltas.isNotEmpty() && r.message.text().isNotBlank())
    }

    @Test fun `Chat Completions tambien funciona con el mismo modelo`() = runTest {
        assumeTrue(key.isNotEmpty(), "sin BYTEPLUS_API_KEY: prueba real omitida")
        val chat = BrainFactory.create(profile.copy(protocol = BrainProtocol.OPENAI_CHAT, capabilities = profile.capabilities.copy(streaming = false)), { key })
        val r = chat.complete(LlmRequest(profile.modelId, emptyList(), listOf(user("Responde solo: OK")), emptyList(), 256))
        println("LIVE chat: ${r.message.text().take(60)} usage=${r.usage}")
        assertTrue(r.usage.inputTokens > 0)
    }
}
