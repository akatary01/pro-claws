package com.vendistri.operations.features.tasks

import com.vendistri.operations.features.location.AppLocation
import com.vendistri.operations.features.location.LocationCommission
import com.vendistri.operations.features.location.LocationCommissionRoundingMode
import com.vendistri.operations.features.location.LocationCommissionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskCommissionCalculatorTest {
    @Test
    fun grossBreakdownRecalculatesGrossFromCashAndCard() {
        assertFalse(TaskCommissionCalculator.grossBreakdownValid(gross = 149.85, grossCash = 33.00, grossCard = 117.85))
        assertEquals(150.85, TaskCommissionCalculator.recalculatedGrossFromBreakdown(33.00, 117.85), 0.001)
        assertTrue(TaskCommissionCalculator.grossBreakdownValid(gross = 150.85, grossCash = 33.00, grossCard = 117.85))
    }

    @Test
    fun manualCommissionRecalculateIsHiddenWhenAutoCalcIsEnabled() {
        val location = locationWithCommission(percent = 30.0)

        assertTrue(
            TaskCommissionCalculator.canManuallyRecalculateCommission(
                autoCalcCommission = false,
                gross = 100.0,
                refunds = 0.0,
                commission = 20.0,
                location = location
            )
        )
        assertFalse(
            TaskCommissionCalculator.canManuallyRecalculateCommission(
                autoCalcCommission = true,
                gross = 100.0,
                refunds = 0.0,
                commission = 20.0,
                location = location
            )
        )
    }

    private fun locationWithCommission(percent: Double): AppLocation {
        return AppLocation(
            id = "location-1",
            name = "Location",
            timeZone = null,
            address = null,
            hours = null,
            commission = LocationCommission(
                type = LocationCommissionType.PercentGrossSales,
                value = percent,
                roundingMode = LocationCommissionRoundingMode.None,
                profitMarginPercent = null,
                lastCommissionPaidAt = null,
                paymentType = null,
                excludeRefundsFromCommission = false,
                excludeCardTransactionSurchargeFromCommission = false
            ),
            defaultAssigneeId = null,
            discontinued = false
        )
    }
}
