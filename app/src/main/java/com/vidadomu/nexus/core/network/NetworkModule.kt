package com.vidadomu.nexus.core.network

import android.util.Log
import com.vidadomu.nexus.core.model.ProviderError
import com.vidadomu.nexus.core.security.SecureStorage
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class AuthInterceptor(private val secureStorage: SecureStorage) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val key = secureStorage.getAtlasApiKey()
        val requestBuilder = original.newBuilder()
        // Never leak the Atlas credential to another provider or to a presigned upload host.
        if (original.url.host.equals("api.atlascloud.ai", ignoreCase = true) && key.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $key")
        }
        val bodyContentType = original.body?.contentType()
        if (bodyContentType == null) {
            if (original.header("Content-Type") == null) {
                requestBuilder.header("Content-Type", "application/json")
            }
        }
        if (original.header("Accept") == null) {
            requestBuilder.header("Accept", "application/json")
        }
        return chain.proceed(requestBuilder.build())
    }
}

class RedactingLoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // Query strings and response bodies can contain signed media URLs or user text.
        Log.d(TAG, "--> ${request.method} ${request.url.host}")

        val t1 = System.nanoTime()
        val response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            Log.e(TAG, "<-- HTTP FAILED: ${e.javaClass.simpleName}")
            throw e
        }
        val t2 = System.nanoTime()
        val durationMs = (t2 - t1) / 1e6

        Log.d(TAG, "<-- ${response.code} ${response.message} (${String.format("%.1f", durationMs)}ms)")
        return response
    }

    companion object {
        private const val TAG = "NexusNetwork"
    }
}

object NetworkErrorParser {
    fun parse(e: Throwable): ProviderError {
        return when (e) {
            is SocketTimeoutException -> ProviderError(
                humanReadableMessage = "La conexión ha tardado demasiado. La operación podría seguir procesándose; revisa el historial antes de repetirla.",
                technicalDetails = e.javaClass.simpleName,
                errorCode = "TIMEOUT"
            )
            is UnknownHostException -> ProviderError(
                humanReadableMessage = "No se puede contactar con el servidor. Comprueba la conexión a Internet.",
                technicalDetails = e.javaClass.simpleName,
                errorCode = "NO_NETWORK"
            )
            is IOException -> ProviderError(
                humanReadableMessage = "Se ha interrumpido la conexión. Revisa el historial antes de repetir la solicitud.",
                technicalDetails = e.javaClass.simpleName,
                errorCode = "IO_ERROR"
            )
            is ProviderError -> e
            else -> ProviderError(
                humanReadableMessage = "No se ha podido completar la operación. Consulta los detalles del error.",
                technicalDetails = e.javaClass.simpleName,
                errorCode = "UNKNOWN"
            )
        }
    }

    fun parseHttpError(statusCode: Int, responseBody: String?): ProviderError {
        val humanMsg = when (statusCode) {
            401 -> "El proveedor ha rechazado la clave API. Revísala en Ajustes."
            402 -> "El proveedor requiere saldo o autorización de pago. Revisa tu cuenta."
            403 -> "Tu cuenta no tiene permiso para acceder a este recurso o modelo."
            404 -> "No se ha encontrado el modelo o recurso solicitado en el proveedor."
            409 -> "Hay un conflicto con la solicitud. Revisa si la generación ya aparece en el historial."
            429 -> "Se ha alcanzado el límite de solicitudes. Espera un momento antes de volver a intentarlo."
            in 500..599 -> "El proveedor ha devuelto un error del servidor. Revisa el historial antes de repetir la solicitud."
            else -> "El servidor ha devuelto un error HTTP $statusCode."
        }
        val sanitizedBody = responseBody?.let { SecureStorage.sanitizeForLogging(it) } ?: "No response body"
        return ProviderError(
            humanReadableMessage = humanMsg,
            technicalDetails = "HTTP $statusCode - $sanitizedBody",
            httpStatusCode = statusCode,
            errorCode = "HTTP_$statusCode"
        )
    }
}

object OkHttpClientFactory {
    fun create(secureStorage: SecureStorage): OkHttpClient {
        return OkHttpClient.Builder().addInterceptor(com.vidadomu.nexus.core.network.LocalQaInterceptor())
            .addInterceptor(AuthInterceptor(secureStorage))
            .addInterceptor(RedactingLoggingInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .callTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}
