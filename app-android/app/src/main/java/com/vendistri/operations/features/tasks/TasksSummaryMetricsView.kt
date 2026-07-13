package com.vendistri.operations.features.tasks

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vendistri.operations.design.LocalVendistriPalette

@Composable
internal fun TasksSummaryMetricsView(
    metrics: List<Pair<String, String>>,
    financialTasks: List<VendiTask> = emptyList()
) {
    val palette = LocalVendistriPalette.current
    val financialSummary = remember(financialTasks) { TaskFinancialHelpers.breakdownSummary(financialTasks) }
    var showGrossBreakdown by remember { mutableStateOf(false) }
    var showCommissionBreakdown by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        metrics.chunked(3).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { (label, value) ->
                    val disclosure = financialDisclosureKind(label)
                    if (disclosure != FinancialDisclosureKind.None) {
                        FinancialMetricBreakdownCell(
                            title = label,
                            value = value,
                            summary = financialSummary,
                            isGross = disclosure == FinancialDisclosureKind.Gross,
                            isExpanded = if (disclosure == FinancialDisclosureKind.Gross) showGrossBreakdown else showCommissionBreakdown,
                            onToggle = {
                                if (disclosure == FinancialDisclosureKind.Gross) {
                                    showGrossBreakdown = !showGrossBreakdown
                                } else {
                                    showCommissionBreakdown = !showCommissionBreakdown
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label, color = palette.textSecondary, style = MaterialTheme.typography.bodySmall)
                            Text(value, color = palette.textPrimary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private enum class FinancialDisclosureKind {
    Gross,
    Commission,
    None
}

private fun financialDisclosureKind(label: String): FinancialDisclosureKind {
    val normalized = label.lowercase()
    return when {
        normalized.contains("gross") -> FinancialDisclosureKind.Gross
        normalized.contains("commission") -> FinancialDisclosureKind.Commission
        else -> FinancialDisclosureKind.None
    }
}
