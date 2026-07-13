package com.vendistri.operations.features.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vendistri.operations.design.LocalVendistriPalette

@Composable
internal fun ReadOnlyFinancialBreakdownRows(
    tasks: List<VendiTask>,
    showGross: Boolean = true,
    grossLabel: String = "Gross Revenue",
    commissionLabel: String = "Commission",
    showNet: Boolean = true,
    netLabel: String = "Net Revenue",
    showCommissionPercent: Boolean = false
) {
    val summary = remember(tasks) { TaskFinancialHelpers.breakdownSummary(tasks) }
    ReadOnlyFinancialBreakdownRows(
        summary = summary,
        showGross = showGross,
        grossLabel = grossLabel,
        commissionLabel = commissionLabel,
        showNet = showNet,
        netLabel = netLabel,
        showCommissionPercent = showCommissionPercent
    )
}

@Composable
internal fun ReadOnlyFinancialBreakdownRows(
    summary: TaskFinancialBreakdownSummary,
    showGross: Boolean = true,
    grossLabel: String = "Gross Revenue",
    commissionLabel: String = "Commission",
    showNet: Boolean = true,
    netLabel: String = "Net Revenue",
    showCommissionPercent: Boolean = false
) {
    var showGrossBreakdown by remember(summary) { mutableStateOf(false) }
    var showCommissionBreakdown by remember(summary) { mutableStateOf(false) }
    val resolvedCommissionLabel = if (showCommissionPercent) {
        TaskCommissionCalculator.commissionPercentText(
            gross = summary.gross,
            commission = summary.commission
        )?.let { "$commissionLabel ($it)" } ?: commissionLabel
    } else {
        commissionLabel
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showGross) {
            FinancialDisclosureRow(
                label = grossLabel,
                value = "$${money(summary.gross)}",
                isExpanded = showGrossBreakdown,
                canExpand = summary.hasGrossBreakdown,
                onToggle = { showGrossBreakdown = !showGrossBreakdown }
            )
            if (showGrossBreakdown && summary.hasGrossBreakdown) {
                GrossBreakdownDetail(summary = summary)
            }
        }
        FinancialLabelValueRow(label = "Refunds", value = "$${money(summary.refunds)}")
        FinancialDisclosureRow(
            label = resolvedCommissionLabel,
            value = "$${money(summary.commission)}",
            isExpanded = showCommissionBreakdown,
            canExpand = summary.hasCommissionBreakdown,
            onToggle = { showCommissionBreakdown = !showCommissionBreakdown }
        )
        if (showCommissionBreakdown && summary.hasCommissionBreakdown) {
            CommissionBreakdownDetail(summary = summary)
        }
        if (showNet) {
            FinancialLabelValueRow(label = netLabel, value = "$${money(summary.net)}")
        }
    }
}

@Composable
internal fun FinancialMetricBreakdownCell(
    title: String,
    value: String,
    summary: TaskFinancialBreakdownSummary,
    isGross: Boolean,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalVendistriPalette.current
    val canExpand = if (isGross) summary.hasGrossBreakdown else summary.hasCommissionBreakdown
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (canExpand) Modifier.clickable(onClick = onToggle) else Modifier),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = palette.textSecondary, style = MaterialTheme.typography.bodySmall)
                if (canExpand) {
                    Text(
                        if (isExpanded) "⌃" else "⌄",
                        color = palette.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(
                value,
                color = palette.textPrimary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isExpanded && canExpand) {
            val detail = if (isGross) {
                "Cash $${money(summary.grossCash)} • Card $${money(summary.grossCard)}"
            } else {
                summary.commissionByPaymentType.joinToString(" • ") { "${it.label} $${money(it.amount)}" }
            }
            Text(
                detail,
                color = palette.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun FinancialLabelValueRow(label: String, value: String) {
    val palette = LocalVendistriPalette.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = palette.textSecondary, fontWeight = FontWeight.SemiBold)
        Text(value, color = palette.textPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FinancialDisclosureRow(
    label: String,
    value: String,
    isExpanded: Boolean,
    canExpand: Boolean,
    onToggle: () -> Unit
) {
    val palette = LocalVendistriPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (canExpand) Modifier.clickable(onClick = onToggle) else Modifier),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = palette.textSecondary, fontWeight = FontWeight.SemiBold)
            if (canExpand) {
                Text(if (isExpanded) "⌃" else "⌄", color = palette.textSecondary, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(value, color = palette.textPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GrossBreakdownDetail(summary: TaskFinancialBreakdownSummary) {
    val palette = LocalVendistriPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text("CASH $${money(summary.grossCash)}", color = palette.textSecondary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        Text("CARD $${money(summary.grossCard)}", color = palette.textSecondary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CommissionBreakdownDetail(summary: TaskFinancialBreakdownSummary) {
    val palette = LocalVendistriPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        summary.commissionByPaymentType.forEach { line ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(line.label, color = palette.textSecondary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Text("$${money(line.amount)}", color = palette.textSecondary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
