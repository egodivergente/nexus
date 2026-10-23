package com.vidadomu.nexus.core.network

import android.content.Context
import com.vidadomu.nexus.agent.core.config.RuleScope
import com.vidadomu.nexus.settings.RuntimePreferences
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Bounded read-only retries. Generation POSTs and uploads are never retried here. */
class RuntimeNetworkInterceptor(context: Context): Interceptor {
    private val preferences=RuntimePreferences.get(context)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request=chain.request()
        val provider=when(request.url.host) {
            "api.atlascloud.ai" -> "atlas_cloud"
            "api.dev.runwayml.com" -> "runway"
            "api.crun.ai" -> "crun"
            "100.72.111.97", "100.112.57.2" -> "google_flow"
            else -> null
        }
        val scope=request.tag(RuleScope::class.java) ?: RuleScope(provider=provider)
        val seconds=preferences.int("generation.timeout",scope)
        val configured=chain.withReadTimeout(seconds,TimeUnit.SECONDS)
        val retries=if(request.method in setOf("GET","HEAD")) preferences.int("generation.retries",scope) else 0
        var attempt=0
        while(true) {
            try {
                val response=configured.proceed(request)
                if(response.code !in setOf(502,503,504) || attempt>=retries) return response
                response.close()
            } catch(e:IOException) {if(attempt>=retries || chain.call().isCanceled()) throw e}
            attempt++
        }
    }
}
