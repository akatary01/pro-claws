package com.vendistri.operations.features.tasks

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskFinancialSectionViewTest {
    @Test
    fun moneyDisplayUsesGroupingSeparators() {
        assertEquals("1,933.65", money(1933.65))
        assertEquals("0.00", money(0.0))
    }

    @Test
    fun moneyInputStaysUngroupedAndParserAcceptsGroupedDisplayValues() {
        assertEquals("1933.65", formatMoneyInput(1933.65))
        assertEquals(1933.65, parseMoney("1,933.65") ?: 0.0, 0.001)
    }

    @Test
    fun collectionFinancialDraftUsesSelectedCommissionPaymentType() {
        val draft = collectionFinancialDraftFromInputs(
            task = collectionTask(),
            grossText = "149.85",
            grossCashText = "32.00",
            grossCardText = "117.85",
            refundsText = "0.00",
            commissionText = "44.00",
            commissionPaymentType = CommissionPaymentType.Check
        )

        assertEquals(149.85, draft.gross, 0.001)
        assertEquals(32.00, draft.grossCash, 0.001)
        assertEquals(117.85, draft.grossCard, 0.001)
        assertEquals(44.00, draft.commission, 0.001)
        assertEquals(CommissionPaymentType.Check, draft.commissionPaymentType)
        assertEquals(105.85, draft.net, 0.001)
    }

    private fun collectionTask(): VendiTask {
        return VendiTask(
            id = "collection-1",
            type = TaskType.MachineCollection,
            status = TaskStatus.Pending,
            isPublic = false,
            assignee = "operator-1",
            assigneeName = "Operator",
            assigneeEmail = null,
            machine = "machine-1",
            machineName = "Machine",
            collectionInputMode = CollectionInputMode.Dollars,
            creditsPerDollar = null,
            location = "location-1",
            locationName = "Location",
            locationAddress = null,
            scheduledFor = "2026-07-05",
            createdAt = null,
            startedAt = null,
            doneAt = null,
            isLive = null,
            duration = null,
            notes = null,
            distance = null,
            gross = null,
            grossCash = null,
            grossCard = null,
            refunds = null,
            commission = null,
            net = null,
            serviceTaskId = null,
            refillTaskId = null,
            refillTaskIds = emptyList(),
            pickupLines = emptyList()
        )
    }
}
