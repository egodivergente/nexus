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
        if (key.isNotBlank()) {
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
        val sanitizedUrl = SecureStorage.sanitizeForLogging(request.url.toString())
        Log.d(TAG, "--> ${request.method} $sanitizedUrl")

        val t1 = System.nanoTime()
        val response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            Log.e(TAG, "<-- HTTP FAILED: ${e.javaClass.simpleName} - ${e.message}")
            throw e
        }
        val t2 = System.nanoTime()
        val durationMs = (t2 - t1) / 1e6

        Log.d(TAG, "<-- ${response.code} ${response.message} (${String.format("%.1f", durationMs)}ms)")
        if (!response.isSuccessful) {
            try {
                val errorPeek = response.peekBody(1024 * 32).string()
                Log.w(TAG, "<-- HTTP ERROR BODY [${response.code}]: ${SecureStorage.sanitizeForLogging(errorPeek)}")
            } catch (e: Exception) {
                // Ignore peek failures
            }
        }
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
                humanReadableMessage = "Connection timed out. The operation may still be processing on the server.",
                technicalDetails = e.message,
                errorCode = "TIMEOUT"
            )
            is UnknownHostException -> ProviderError(
                humanReadableMessage = "No internet connection or unable to reach the server.",
                technicalDetails = e.message,
                errorCode = "NO_NETWORK"
            )
            is IOException -> ProviderError(
                humanReadableMessage = "Network error: ${e.localizedMessage ?: "Connection interrupted"}",
                technicalDetails = e.message,
                errorCode = "IO_ERROR"
            )
            is ProviderError -> e
            else -> ProviderError(
                humanReadableMessage = "Unexpected error: ${e.localizedMessage ?: "Unknown"}",
                technicalDetails = e.stackTraceToString(),
                errorCode = "UNKNOWN"
            )
        }
    }

    fun parseHttpError(statusCode: Int, responseBody: String?): ProviderError {
        val humanMsg = when (statusCode) {
            401 -> "Atlas Cloud rejected the API key. Please check your credentials in Settings."
            403 -> "Access forbidden. Your account may not have access to this specific model."
            404 -> "Model or requested endpoint not found on Atlas Cloud."
            409 -> "Conflict. A similar generation may already be in progress."
            429 -> "Rate limit reached or insufficient credits on Atlas Cloud."
            in 500..599 -> "Atlas Cloud servers encountered an internal error. Please try again shortly."
            else -> "Server returned HTTP $statusCode"
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
        return OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(secureStorage))
            .addInterceptor(RedactingLoggingInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
