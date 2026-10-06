package com.vidadomu.nexus.core.network
class LocalQaInterceptor:okhttp3.Interceptor {
    override fun intercept(chain:okhttp3.Interceptor.Chain):okhttp3.Response {
        if(com.vidadomu.nexus.BuildConfig.OFFLINE_QA) throw java.io.IOException("NEXUS Lab: red desactivada")
        return chain.proceed(chain.request())
    }
}
