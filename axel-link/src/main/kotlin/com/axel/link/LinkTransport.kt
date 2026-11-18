package com.axel.link

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class LinkResponse(val code: Int, val body: String)

/** Una petición POST con JSON y credencial. Lanza [IOException] si la red falla. */
fun interface LinkTransport {
    @Throws(IOException::class)
    fun post(url: String, token: String, body: String, timeoutMs: Long): LinkResponse
}

/** HTTPS con la validación de certificados del sistema. Sin redirecciones: la credencial no viaja a otro servidor. */
class OkHttpLinkTransport(base: OkHttpClient? = null) : LinkTransport {
    private val client: OkHttpClient = (base?.newBuilder() ?: OkHttpClient.Builder())
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(0, TimeUnit.SECONDS)
        .build()

    override fun post(url: String, token: String, body: String, timeoutMs: Long): LinkResponse {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "axel-link/1")
            .post(body.toRequestBody(JSON))
            .build()
        val call = client.newCall(request)
        call.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
        call.execute().use { response ->
            return LinkResponse(response.code, response.body?.string().orEmpty())
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
