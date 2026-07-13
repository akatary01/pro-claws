package com.vendistri.operations.features.tasks

data class TaskFinancialTotals(
    val gross: Double = 0.0,
    val refunds: Double = 0.0,
    val commission: Double = 0.0,
    val net: Double = 0.0
)

data class TaskCommissionPaymentBreakdown(
    val label: String,
    val amount: Double
)

data class TaskFinancialBreakdownSummary(
    val gross: Double = 0.0,
    val grossCash: Double = 0.0,
    val grossCard: Double = 0.0,
    val refunds: Double = 0.0,
    val commission: Double = 0.0,
    val net: Double = 0.0,
    val commissionByPaymentType: List<TaskCommissionPaymentBreakdown> = emptyList()
) {
    val hasGrossBreakdown: Boolean
        get() = true

    val hasCommissionBreakdown: Boolean
        get() = commissionByPaymentType.any { kotlin.math.abs(it.amount) >= 0.01 }
}

data class TaskDisplayedFinancials(
    val gross: Double = 0.0,
    val grossCash: Double = 0.0,
    val grossCard: Double = 0.0,
    val refunds: Double = 0.0,
    val commission: Double = 0.0,
    val net: Double = 0.0
)

object TaskFinancialHelpers {
    fun sumTaskFinancials(tasks: List<VendiTask>): TaskFinancialTotals {
        return TaskGroupingHelpers.uniqueTasksById(tasks).fold(TaskFinancialTotals()) { acc, task ->
            val values = taskFinancials(task)
            TaskFinancialTotals(
                gross = acc.gross + values.gross,
                refunds = acc.refunds + values.refunds,
                commission = acc.commission + values.commission,
                net = acc.net + values.net
            )
        }
    }

    fun taskFinancials(task: VendiTask): TaskFinancialTotals {
        if (task.type == TaskType.MachineRefund) {
            return TaskFinancialTotals(refunds = if (displaysClearedFinancials(task)) 0.0 else task.refunds ?: 0.0)
        }
        if (task.type != TaskType.MachineCollection || !shouldUseRevenueValues(task)) {
            return TaskFinancialTotals()
        }
        val gross = task.gross ?: 0.0
        val refunds = task.refunds ?: 0.0
        val commission = task.commission ?: 0.0
        val net = task.net ?: roundCurrency(gross - refunds - commission)
        return TaskFinancialTotals(gross = gross, refunds = refunds, commission = commission, net = net)
    }

    fun displayedFinancials(task: VendiTask): TaskDisplayedFinancials {
        if (displaysClearedFinancials(task)) return TaskDisplayedFinancials()
        if (task.type == TaskType.MachineRefund) {
            return TaskDisplayedFinancials(refunds = task.refunds ?: 0.0)
        }
        if (task.type != TaskType.MachineCollection) return TaskDisplayedFinancials()

        val gross = task.gross ?: 0.0
        val refunds = task.refunds ?: 0.0
        val commission = task.commission ?: 0.0
        val net = task.net ?: TaskCommissionCalculator.calculatedNet(
            gross = gross,
            refunds = refunds,
            commission = commission
        )
        return TaskDisplayedFinancials(
            gross = gross,
            grossCash = task.grossCash ?: 0.0,
            grossCard = task.grossCard ?: 0.0,
            refunds = refunds,
            commission = commission,
            net = net
        )
    }

    private fun shouldUseRevenueValues(task: VendiTask): Boolean {
        if (task.status == TaskStatus.Done) return true
        if (task.status == TaskStatus.Cancelled || task.status == TaskStatus.Error) return false
        return (task.gross ?: 0.0) != 0.0 ||
            (task.refunds ?: 0.0) != 0.0 ||
            (task.commission ?: 0.0) != 0.0 ||
            (task.net ?: 0.0) != 0.0
    }

    private fun displaysClearedFinancials(task: VendiTask): Boolean {
        return task.status == TaskStatus.Cancelled || task.status == TaskStatus.Error
    }

    fun breakdownSummary(tasks: List<VendiTask>): TaskFinancialBreakdownSummary {
        var gross = 0.0
        var grossCash = 0.0
        var grossCard = 0.0
        var refunds = 0.0
        var commission = 0.0
        var net = 0.0
        val commissionByPaymentType = mutableMapOf<String, Double>()

        TaskGroupingHelpers.uniqueTasksById(tasks).forEach { task ->
            val values = taskFinancials(task)
            gross += values.gross
            refunds += values.refunds
            commission += values.commission
            net += values.net

            if (task.type == TaskType.MachineCollection && shouldUseRevenueValues(task)) {
                grossCash += task.grossCash ?: 0.0
                grossCard += task.grossCard ?: 0.0
                if (kotlin.math.abs(values.commission) >= 0.01) {
                    val label = task.commissionPaymentType?.label ?: "Unspecified"
                    commissionByPaymentType[label] = (commissionByPaymentType[label] ?: 0.0) + values.commission
                }
            }
        }

        return TaskFinancialBreakdownSummary(
            gross = gross,
            grossCash = grossCash,
            grossCard = grossCard,
            refunds = refunds,
            commission = commission,
            net = net,
            commissionByPaymentType = commissionByPaymentType
                .toList()
                .sortedBy { it.first }
                .map { (label, amount) -> TaskCommissionPaymentBreakdown(label, amount) }
        )
    }

    fun isFinancialMetric(label: String): Boolean {
        val normalized = label.trim().lowercase()
        return normalized.startsWith("gross") ||
            normalized.startsWith("refund") ||
            normalized.startsWith("commission") ||
            normalized.startsWith("net")
    }

    fun shouldEmphasizeFinancialMetric(label: String): Boolean = isFinancialMetric(label)

    fun shouldEmphasizeDisplayMetric(label: String): Boolean = true
}
