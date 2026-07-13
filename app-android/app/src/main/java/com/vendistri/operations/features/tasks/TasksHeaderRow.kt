package com.vendistri.operations.features.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vendistri.operations.R
import com.vendistri.operations.components.BackButton
import com.vendistri.operations.components.RevenueChip
import com.vendistri.operations.design.AppColors
import java.time.LocalDate

@Composable
internal fun TasksPanelHeader(
    tab: TasksHomePanelTab,
    summary: TaskSummary,
    tasks: List<VendiTask>,
    selectedDate: LocalDate,
    onClose: () -> Unit,
    onCalendarClick: (() -> Unit)?
) {
    val financials = TaskFinancialHelpers.sumTaskFinancials(tasks)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 2.dp)) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            RevenueChip(financials.gross)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackButton(onClick = onClose, modifier = Modifier.offset(x = (-8).dp))
                if (onCalendarClick == null) {
                    Text(
                        panelDateTitle(tab, selectedDate),
                        modifier = Modifier.offset(x = 8.dp),
                        color = AppColors.muted,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    TextButton(onClick = onCalendarClick, modifier = Modifier.offset(x = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(panelDateTitle(tab, selectedDate), color = AppColors.muted, fontWeight = FontWeight.SemiBold)
                            Icon(
                                painter = painterResource(R.drawable.ic_calendar_grid),
                                contentDescription = "Calendar",
                                tint = AppColors.muted,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }
        if (tab == TasksHomePanelTab.Tasks) {
            TaskStatusTotalsRow(summary)
        }
        TasksSummaryMetricsView(
            metrics = listOf(
                "Locations" to TaskGroupingHelpers.groupByLocation(tasks).size.toString(),
                "Machines" to TaskGroupingHelpers.groupByMachine(tasks).size.toString(),
                "Tasks" to tasks.size.toString(),
                "Refunds" to "$${money(financials.refunds)}",
                "Commission" to "$${money(financials.commission)}",
                "Net Revenue" to "$${money(financials.net)}"
            ),
            financialTasks = tasks
        )
        if (tab == TasksHomePanelTab.CompletedToday) {
            CompletedActivityRow(tasks)
        }
    }
}

@Composable
private fun CompletedActivityRow(tasks: List<VendiTask>) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TaskRollupMetricsRow(
            durationMinutes = TaskGroupingHelpers.totalDurationMinutes(tasks),
            distanceMiles = TaskGroupingHelpers.totalDistanceMiles(tasks),
            spacing = 18.dp
        )
        Spacer(modifier = Modifier.weight(1f))
    }
}
