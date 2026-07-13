package com.vendistri.operations.features.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CommissionPaymentTypeTest {
    @Test
    fun directDepositUsesBackendRawValueAndDisplayLabel() {
        assertEquals("direct_deposit", CommissionPaymentType.DirectDeposit.rawValue)
        assertEquals("Direct Deposit", CommissionPaymentType.DirectDeposit.label)
    }

    @Test
    fun legacyAchParsesAsDirectDeposit() {
        assertEquals(CommissionPaymentType.DirectDeposit, CommissionPaymentType.from("ach"))
    }

    @Test
    fun optionsDoNotIncludeOther() {
        assertFalse(CommissionPaymentType.entries.any { it.rawValue == "other" })
    }
}
