package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.llm.AwsCredentials
import com.vidadomu.nexus.agent.core.llm.BedrockDirectProvider
import com.vidadomu.nexus.agent.core.llm.ContentBlock
import com.vidadomu.nexus.agent.core.llm.LlmException
import com.vidadomu.nexus.agent.core.llm.LlmMessage
import com.vidadomu.nexus.agent.core.llm.LlmRequest
import com.vidadomu.nexus.agent.core.llm.Role
import com.vidadomu.nexus.agent.core.llm.SigV4
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BedrockDirectTest {
    private val creds = AwsCredentials("AKIDEXAMPLE", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY")
    private val at = ZonedDateTime.of(2026, 10, 2, 19, 19, 3, 0, ZoneOffset.UTC)

    @Test fun `firma igual que botocore`() {
        // Referencia: botocore SigV4Auth con las mismas claves de ejemplo, fecha y cuerpo.
        val body = """{"messages":[{"role":"user","content":[{"text":"hola"}]}]}"""
        val h = SigV4.sign("POST", "bedrock-runtime.us-east-1.amazonaws.com",
            "/model/${SigV4.encode("us.anthropic.claude-haiku-4-5-20251001-v1:0")}/converse", body, "us-east-1", "bedrock", creds, at)
        assertEquals("20261002T191903Z", h["x-amz-date"])
        assertEquals("AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20261002/us-east-1/bedrock/aws4_request, SignedHeaders=host;x-amz-date, " +
            "Signature=728877f9dbd5e92a527c37f92161236bf10cdec2d90175978d5c1bc71d0a1a28", h["Authorization"])
    }

    private fun client(code: Int, reply: String, type: String? = null, seen: (okhttp3.Request) -> Unit = {}) = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            seen(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .apply { type?.let { header("x-amzn-ErrorType", it) } }
                .body(reply.toResponseBody("application/json".toMediaType())).build()
        }).build()

    private val req = LlmRequest(modelId = "us.anthropic.claude-sonnet-4-6", system = listOf("s"),
        messages = listOf(LlmMessage(Role.USER, listOf(ContentBlock.Text("hola")))), tools = emptyList(), maxTokens = 50)

    @Test fun `llama a la ruta del modelo sin modelId en el cuerpo`() = runTest {
        var url = ""; var body = ""; var auth = ""
        val p = BedrockDirectProvider("us-east-1", { creds }, client(200,
            """{"output":{"message":{"role":"assistant","content":[{"text":"ok"}]}},"stopReason":"end_turn","usage":{"inputTokens":5,"outputTokens":1}}""") {
            url = it.url.toString(); auth = it.header("Authorization").orEmpty()
            body = Buffer().also { b -> it.body!!.writeTo(b) }.readUtf8()
        }, now = { at })
        val r = p.complete(req)
        assertEquals("https://bedrock-runtime.us-east-1.amazonaws.com/model/us.anthropic.claude-sonnet-4-6/converse", url)
        assertFalse("modelId" in Json.parseToJsonElement(body).jsonObject, body)
        assertTrue(auth.startsWith("AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20261002/us-east-1/bedrock/aws4_request"))
        assertEquals("ok", (r.message.content.single() as ContentBlock.Text).text)
        assertEquals(5, r.usage.inputTokens)
    }

    @Test fun `cuenta tokens con cuerpo Converse y firma directa`() = runTest {
        var url="";var body=""
        val p=BedrockDirectProvider("us-east-1",{creds},client(200,"""{"inputTokens":123}""") {
            url=it.url.toString();body=Buffer().also {b -> it.body!!.writeTo(b)}.readUtf8()
        },now={at})
        assertEquals(123,p.countInputTokens(req))
        assertEquals("https://bedrock-runtime.us-east-1.amazonaws.com/model/anthropic.claude-sonnet-4-6/count-tokens", url)
        val converse=Json.parseToJsonElement(body).jsonObject["input"]!!.jsonObject["converse"]!!.jsonObject
        assertTrue("messages" in converse)
        assertFalse("inferenceConfig" in converse)
    }

    @Test fun `conteo resuelve perfiles Anthropic sin modificar otros identificadores`() = runTest {
        val base = "anthropic.claude-haiku-4-5-20251001-v1:0"
        val identifiers = listOf(base) + listOf("us", "eu", "apac", "au", "jp", "global").map { "$it.$base" }
        for (model in identifiers + listOf("custom-profile", "arn:aws:bedrock:us-east-1::foundation-model/$base")) {
            var path = ""
            val provider = BedrockDirectProvider("us-east-1", { creds }, client(200, """{"inputTokens":25}""") {
                path = it.url.encodedPath
            }, now = { at })
            assertEquals(25, provider.countInputTokens(req.copy(modelId = model)))
            val expected = if (model in identifiers) base else model
            assertEquals("/model/${SigV4.encode(expected)}/count-tokens", path)
        }
    }

    @Test fun `agente usa conteo real cuando la reserva por bytes superaria limite diario`() = runTest {
        val paths = mutableListOf<String>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val path = chain.request().url.encodedPath
            paths += path
            val reply = if (path == "/model/anthropic.claude-sonnet-4-6/count-tokens") {
                """{"inputTokens":25}"""
            } else if (path == "/model/us.anthropic.claude-sonnet-4-6/converse") {
                """{"output":{"message":{"role":"assistant","content":[{"text":"ok"}]}},"stopReason":"end_turn","usage":{"inputTokens":25,"outputTokens":1}}"""
            } else """{"message":"The provided model doesn't support counting tokens."}"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(if (path.contains("/us.") && path.endsWith("/count-tokens")) 400 else 200).message("test")
                .body(reply.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val ledger = com.vidadomu.nexus.agent.core.llm.LlmBudgetLedger(java.nio.file.Files.createTempDirectory("count-budget").resolve("usage.json").toFile())
        val limits = com.vidadomu.nexus.agent.core.llm.LlmLimits(dailyTokens = 500)
        ledger.reserve("previous", 0.001, 400, limits)
        val provider = com.vidadomu.nexus.agent.core.llm.BudgetedLlmProvider(
            BedrockDirectProvider("us-east-1", { creds }, http, now = { at }), ledger) { limits }
        assertEquals("ok", provider.complete(req.copy(system = listOf("context ".repeat(500)), turnId = "current")).message.text())
        assertEquals(listOf("/model/anthropic.claude-sonnet-4-6/count-tokens", "/model/us.anthropic.claude-sonnet-4-6/converse"), paths)
        assertTrue(ledger.spentToday() > 0.001)
    }

    @Test fun `modelo no habilitado se explica`() = runTest {
        val p = BedrockDirectProvider("us-east-1", { creds }, client(403,
            """{"message":"anthropic.claude-sonnet-5-5 is not available for this account."}""", "AccessDeniedException:http://internal"), now = { at })
        val e = assertFailsWith<LlmException> { p.complete(req) }
        assertEquals(LlmException.Kind.MODEL_NOT_ALLOWED, e.kind)
        assertTrue("no está disponible" in e.message.orEmpty())
    }

    @Test fun `claves malas son AUTH`() = runTest {
        val p = BedrockDirectProvider("us-east-1", { creds }, client(403, """{"message":"The security token included in the request is invalid."}""", "UnrecognizedClientException"), now = { at })
        assertEquals(LlmException.Kind.AUTH, assertFailsWith<LlmException> { p.complete(req) }.kind)
    }
}
