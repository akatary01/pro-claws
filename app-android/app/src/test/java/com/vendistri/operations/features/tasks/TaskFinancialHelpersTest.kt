package com.vendistri.operations.features.tasks

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskFinancialHelpersTest {
    @Test
    fun breakdownSummaryAggregatesGrossBreakdownAndCommissionPaymentTypes() {
        val summary = TaskFinancialHelpers.breakdownSummary(
            listOf(
                collectionTask(
                    id = "collection-1",
                    gross = 150.0,
                    grossCash = 50.0,
                    grossCard = 100.0,
                    commission = 45.0,
                    commissionPaymentType = CommissionPaymentType.Cash,
                    net = 105.0
                ),
                collectionTask(
                    id = "collection-2",
                    gross = 80.0,
                    grossCash = 20.0,
                    grossCard = 60.0,
                    commission = 24.0,
                    commissionPaymentType = CommissionPaymentType.Check,
                    net = 56.0
                ),
                refundTask(refunds = 12.0)
            )
        )

        assertEquals(230.0, summary.gross, 0.001)
        assertEquals(70.0, summary.grossCash, 0.001)
        assertEquals(160.0, summary.grossCard, 0.001)
        assertEquals(12.0, summary.refunds, 0.001)
        assertEquals(69.0, summary.commission, 0.001)
        assertEquals(161.0, summary.net, 0.001)
        assertEquals(listOf("Cash", "Check"), summary.commissionByPaymentType.map { it.label })
        assertEquals(45.0, summary.commissionByPaymentType[0].amount, 0.001)
        assertEquals(24.0, summary.commissionByPaymentType[1].amount, 0.001)
    }

    @Test
    fun breakdownSummaryDeduplicatesTasksById() {
        val task = collectionTask(
            id = "collection-1",
            gross = 150.0,
            grossCash = 50.0,
            grossCard = 100.0,
            commission = 45.0,
            commissionPaymentType = CommissionPaymentType.Cash,
            net = 105.0
        )

        val summary = TaskFinancialHelpers.breakdownSummary(listOf(task, task))

        assertEquals(150.0, summary.gross, 0.001)
        assertEquals(50.0, summary.grossCash, 0.001)
        assertEquals(100.0, summary.grossCard, 0.001)
        assertEquals(45.0, summary.commission, 0.001)
        assertEquals(105.0, summary.net, 0.001)
    }

    @Test
    fun cancelledCollectionDisplaysAndAggregatesAsZero() {
        val task = collectionTask(
            id = "collection-1",
            gross = 150.0,
            grossCash = 50.0,
            grossCard = 100.0,
            commission = 45.0,
            commissionPaymentType = CommissionPaymentType.Cash,
            net = 105.0,
            status = TaskStatus.Cancelled
        )

        val displayed = TaskFinancialHelpers.displayedFinancials(task)
        val summary = TaskFinancialHelpers.breakdownSummary(listOf(task))

        assertEquals(0.0, displayed.gross, 0.001)
        assertEquals(0.0, displayed.grossCash, 0.001)
        assertEquals(0.0, displayed.grossCard, 0.001)
        assertEquals(0.0, displayed.commission, 0.001)
        assertEquals(0.0, displayed.net, 0.001)
        assertEquals(0.0, summary.gross, 0.001)
        assertEquals(0.0, summary.commission, 0.001)
        assertEquals(0.0, summary.net, 0.001)
    }

    @Test
    fun cancelledRefundDisplaysAndAggregatesAsZero() {
        val task = refundTask(refunds = 12.0, status = TaskStatus.Cancelled)

        val displayed = TaskFinancialHelpers.displayedFinancials(task)
        val summary = TaskFinancialHelpers.breakdownSummary(listOf(task))

        assertEquals(0.0, displayed.refunds, 0.001)
        assertEquals(0.0, summary.refunds, 0.001)
    }

    private fun collectionTask(
        id: String,
        gross: Double,
        grossCash: Double,
        grossCard: Double,
        commission: Double,
        commissionPaymentType: CommissionPaymentType,
        net: Double,
        status: TaskStatus = TaskStatus.Done
    ): VendiTask {
        return task(
            id = id,
            type = TaskType.MachineCollection,
            status = status,
            gross = gross,
            grossCash = grossCash,
            grossCard = grossCard,
            refunds = 0.0,
            commission = commission,
            commissionPaymentType = commissionPaymentType,
            net = net
        )
    }

    private fun refundTask(refunds: Double, status: TaskStatus = TaskStatus.Done): VendiTask {
        return task(
            id = "refund-1",
            type = TaskType.MachineRefund,
            status = status,
            gross = null,
            grossCash = null,
            grossCard = null,
            refunds = refunds,
            commission = null,
            commissionPaymentType = null,
            net = null
        )
    }

    private fun task(
        id: String,
        type: TaskType,
        status: TaskStatus = TaskStatus.Done,
        gross: Double?,
        grossCash: Double?,
        grossCard: Double?,
        refunds: Double?,
        commission: Double?,
        commissionPaymentType: CommissionPaymentType?,
        net: Double?
    ): VendiTask {
        return VendiTask(
            id = id,
            type = type,
            status = status,
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
            gross = gross,
            grossCash = grossCash,
            grossCard = grossCard,
            refunds = refunds,
            commission = commission,
            commissionPaymentType = commissionPaymentType,
            net = net,
            serviceTaskId = null,
            refillTaskId = null,
            refillTaskIds = emptyList(),
            pickupLines = emptyList()
        )
    }
}
