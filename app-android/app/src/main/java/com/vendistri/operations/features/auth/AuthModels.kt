package com.vendistri.operations.features.auth

import org.json.JSONObject

data class User(
    val id: String,
    val email: String,
    val isOperator: Boolean = false,
    val isAdmin: Boolean = false,
    val isOwner: Boolean = false,
    val firstName: String?,
    val lastName: String?
) {
    val displayName: String
        get() = listOfNotNull(firstName, lastName).joinToString(" ").ifBlank { email }

    companion object {
        fun fromJson(rawJson: String): User {
            val json = JSONObject(rawJson)
            return User(
                id = json.getString("id"),
                email = json.getString("email"),
                isOperator = json.optBoolean("is_operator", json.optBoolean("isOperator")),
                isAdmin = json.optBoolean("is_admin", json.optBoolean("isAdmin")),
                isOwner = json.optBoolean("is_owner", json.optBoolean("isOwner")),
                firstName = json.optString("first_name", json.optString("firstName")).ifBlank { null },
                lastName = json.optString("last_name", json.optString("lastName")).ifBlank { null }
            )
        }
    }
}

data class OrganizationSummary(
    val id: String,
    val title: String,
    val operatorTaskClaimingEnabled: Boolean?
) {
    companion object {
        fun fromJson(rawJson: String): OrganizationSummary {
            val json = JSONObject(rawJson)
            return OrganizationSummary(
                id = json.getString("id"),
                title = json.getString("title"),
                operatorTaskClaimingEnabled = if (json.has("operator_task_claiming_enabled")) {
                    json.optBoolean("operator_task_claiming_enabled")
                } else if (json.has("operatorTaskClaimingEnabled")) {
                    json.optBoolean("operatorTaskClaimingEnabled")
                } else {
                    null
                }
            )
        }
    }
}

enum class SubscriptionState {
    Trial, Grace, Active, PastDue, Canceled, Exempt;

    companion object {
        fun from(raw: String?): SubscriptionState {
            return when (raw.orEmpty().trim().lowercase().substringAfterLast('.')) {
                "grace" -> Grace
                "active" -> Active
                "past_due" -> PastDue
                "canceled" -> Canceled
                "exempt" -> Exempt
                else -> Trial
            }
        }
    }
}

data class BillingStatus(
    val hasPaymentMethod: Boolean,
    val subscriptionState: SubscriptionState
) {
    companion object {
        fun fromJson(rawJson: String): BillingStatus {
            val json = JSONObject(rawJson)
            return BillingStatus(
                hasPaymentMethod = json.optBoolean("hasPaymentMethod", json.optBoolean("has_payment_method")),
                subscriptionState = SubscriptionState.from(
                    json.optString("subscriptionState", json.optString("subscription_state"))
                )
            )
        }
    }
}

enum class AuthBootstrapState {
    Idle,
    Loading,
    RestoredSession,
    Unauthenticated,
    NetworkFailure;

    val isLoading: Boolean
        get() = this == Idle || this == Loading
}

data class AuthUiState(
    val user: User? = null,
    val bootstrapState: AuthBootstrapState = AuthBootstrapState.Idle,
    val message: String? = null,
    val isSubmitting: Boolean = false,
    val billingStatus: BillingStatus? = null,
    val billingStatusError: String? = null,
    val paymentRequiredUrl: String? = null
)
