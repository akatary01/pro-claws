package com.vendistri.operations.features.auth

import com.vendistri.operations.network.ApiClient
import com.vendistri.operations.network.HttpMethod

class AuthApi(
    private val apiClient: ApiClient
) {
    suspend fun bootstrapUser(): String {
        return apiClient.request(HttpMethod.Get, "/user").body
    }

    suspend fun organizationSummary(): String {
        return apiClient.request(HttpMethod.Get, "/user/organization").body
    }

    suspend fun subscriptionStatus(): String {
        return apiClient.request(HttpMethod.Get, "/user/organization/subscription/status").body
    }

    suspend fun fetchCsrf() {
        apiClient.request(HttpMethod.Get, "/user/csrf")
    }

    suspend fun signIn(email: String, password: String) {
        fetchCsrf()
        apiClient.request(
            method = HttpMethod.Post,
            path = "/user/signin",
            body = """{"email":"${email.jsonEscaped()}","password":"${password.jsonEscaped()}"}"""
        )
    }

    suspend fun signOut() {
        apiClient.request(HttpMethod.Post, "/user/signout")
    }

    fun clearSessionCookies() {
        apiClient.clearCookies()
    }

    suspend fun requestPasswordReset(email: String) {
        apiClient.request(
            method = HttpMethod.Post,
            path = "/user/password/reset/request",
            body = """{"email":"${email.jsonEscaped()}"}"""
        )
    }
}

fun normalizeIdentityEmail(email: String): String = email.trim().lowercase()

private fun String.jsonEscaped(): String {
    return replace("\\", "\\\\").replace("\"", "\\\"")
}
