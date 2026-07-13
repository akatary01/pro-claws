package com.vendistri.operations.network

import com.vendistri.operations.BuildConfig

object NetworkConfig {
    val backendUrl: String = BuildConfig.BACKEND_URL.trimEnd('/')
    val appWebUrl: String = BuildConfig.APP_WEB_URL.trimEnd('/')
    val signupWebUrl: String = BuildConfig.SIGNUP_WEB_URL.trimEnd('/')
    val serviceFormWebUrl: String = BuildConfig.SERVICE_FORM_WEB_URL.trimEnd('/')

    fun backendWebSocketUrl(userId: String): String {
        val wsScheme = if (backendUrl.startsWith("https://")) "wss://" else "ws://"
        val withoutScheme = backendUrl
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
        return "$wsScheme$withoutScheme/ws/user/$userId"
    }
}
