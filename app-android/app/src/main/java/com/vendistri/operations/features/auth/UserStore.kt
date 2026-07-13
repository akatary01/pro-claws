package com.vendistri.operations.features.auth

import com.vendistri.operations.network.ApiClient
import com.vendistri.operations.network.NetworkError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class UserStore(
    private val authApi: AuthApi = AuthApi(ApiClient())
) {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    val currentUser: User?
        get() = _state.value.user

    val isBillingBlocked: Boolean
        get() = _state.value.billingStatus?.subscriptionState in setOf(
            SubscriptionState.PastDue,
            SubscriptionState.Canceled
        )

    private val hasOrganizationRole: Boolean
        get() = currentUser?.let { it.isOwner || it.isAdmin || it.isOperator } == true

    suspend fun refreshSubscriptionStatusIfAvailable(force: Boolean = false) {
        if (!hasOrganizationRole) {
            _state.update { it.copy(billingStatus = null, billingStatusError = null) }
            return
        }
        if (_state.value.billingStatus != null && !force) return
        try {
            val status = BillingStatus.fromJson(authApi.subscriptionStatus())
            _state.update { it.copy(billingStatus = status, billingStatusError = null) }
        } catch (error: Exception) {
            _state.update { it.copy(billingStatus = null, billingStatusError = error.message) }
        }
    }

    fun presentPaymentRequired(appWebUrl: String) {
        val url = "${appWebUrl.trimEnd('/')}/account-settings?tab=payment-methods&open=card"
        _state.update { it.copy(paymentRequiredUrl = url) }
    }

    fun clearPaymentRequired() {
        _state.update { it.copy(paymentRequiredUrl = null) }
    }

    suspend fun initUser() {
        _state.update { it.copy(bootstrapState = AuthBootstrapState.Loading, message = null) }
        try {
            val user = User.fromJson(authApi.bootstrapUser())
            _state.update {
                it.copy(
                    user = user,
                    bootstrapState = AuthBootstrapState.RestoredSession,
                    message = null
                )
            }
        } catch (error: NetworkError.HttpStatus) {
            if (error.code in 400..499) {
                authApi.clearSessionCookies()
                _state.update {
                    AuthUiState(bootstrapState = AuthBootstrapState.Unauthenticated)
                }
            } else {
                _state.update {
                    it.copy(
                        user = null,
                        bootstrapState = AuthBootstrapState.NetworkFailure,
                        message = error.message
                    )
                }
            }
        } catch (error: Exception) {
            _state.update {
                it.copy(
                    user = null,
                    bootstrapState = AuthBootstrapState.NetworkFailure,
                    message = error.message ?: "The request failed. Please try again."
                )
            }
        }
    }

    suspend fun signIn(email: String, password: String) {
        val normalizedEmail = normalizeIdentityEmail(email)
        _state.update { it.copy(isSubmitting = true, message = null) }
        try {
            authApi.signIn(email = normalizedEmail, password = password)
            initUser()
        } catch (error: Exception) {
            _state.update {
                it.copy(
                    user = null,
                    bootstrapState = AuthBootstrapState.Unauthenticated,
                    message = error.message ?: "Invalid email or password."
                )
            }
        } finally {
            _state.update { it.copy(isSubmitting = false) }
        }
    }

    suspend fun signOut() {
        _state.value = AuthUiState(bootstrapState = AuthBootstrapState.Unauthenticated)
        runCatching { authApi.signOut() }
        authApi.clearSessionCookies()
    }

    suspend fun requestPasswordReset(email: String): Boolean {
        val normalizedEmail = normalizeIdentityEmail(email)
        _state.update { it.copy(isSubmitting = true, message = null) }
        return try {
            authApi.requestPasswordReset(normalizedEmail)
            _state.update { it.copy(isSubmitting = false, message = null) }
            true
        } catch (error: Exception) {
            _state.update {
                it.copy(
                    isSubmitting = false,
                    message = error.message ?: "Could not send a reset link. Please try again."
                )
            }
            false
        }
    }

}
