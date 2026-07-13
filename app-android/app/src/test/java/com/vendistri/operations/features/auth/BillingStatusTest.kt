package com.vendistri.operations.features.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingStatusTest {
    @Test
    fun parsesPastDueFromBackendSnakeCasePayload() {
        val status = BillingStatus.fromJson(
            """{"has_payment_method":false,"subscription_state":"past_due"}"""
        )

        assertFalse(status.hasPaymentMethod)
        assertEquals(SubscriptionState.PastDue, status.subscriptionState)
    }

    @Test
    fun parsesNamespacedAndCamelCaseSubscriptionStates() {
        val pastDue = BillingStatus.fromJson(
            """{"hasPaymentMethod":true,"subscriptionState":"SubscriptionState.past_due"}"""
        )
        val canceled = BillingStatus.fromJson(
            """{"hasPaymentMethod":true,"subscriptionState":"canceled"}"""
        )

        assertTrue(pastDue.hasPaymentMethod)
        assertEquals(SubscriptionState.PastDue, pastDue.subscriptionState)
        assertEquals(SubscriptionState.Canceled, canceled.subscriptionState)
    }
}
