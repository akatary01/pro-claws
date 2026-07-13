package com.vendistri.operations.features.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vendistri.operations.design.LocalVendistriPalette
import com.vendistri.operations.features.location.AppLocation
import com.vendistri.operations.features.tasks.actions.TaskActionKind

@Composable
internal fun TaskMachineCard(
    machineGroup: TaskMachineGroup,
    showCompletedMetrics: Boolean,
    onBulkTaskAction: (TaskActionKind, List<VendiTask>) -> Unit,
    onApplySharedNotes: suspend (List<String>, String?) -> Boolean,
    pendingMutationTaskIds: Set<String>,
    taskActions: TaskCardActions,
    financialDisplay: TaskFinancialDisplayMode = TaskFinancialDisplayMode.Full,
    appLocation: AppLocation?,
    autoCalcCommission: Boolean
) {
    val palette = LocalVendistriPalette.current
    val claimableTasks = machineGroup.tasks.filter(taskActions.canAssignToSelf)
    val completableTasks = machineGroup.tasks.filter {
        taskActions.canChangeStatus(it) && TaskRowActionPolicy.canUseSimpleDoneAction(it)
    }
    val assigneeDisplay = TaskMachineAssigneeHelper.display(machineGroup.tasks)
    val assigneeSummary = (assigneeDisplay as? TaskMachineAssigneeDisplay.Summary)?.text
    val showPerTaskAssignee = assigneeDisplay is TaskMachineAssigneeDisplay.Mixed
    val visitMetricText = TaskMachineVisitMetricFormatter.visitMetricText(machineGroup.tasks)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = palette.surfaceVariant,
        border = BorderStroke(1.dp, palette.border)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(machineGroup.name, color = palette.textPrimary, fontWeight = FontWeight.Bold)
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (showCompletedMetrics) {
                        TaskRollupMetricsRow(
                            durationMinutes = machineGroup.durationMinutes,
                            distanceMiles = machineGroup.distanceMiles,
                            compact = true
                        )
                    }
                    if (!showCompletedMetrics && completableTasks.size > 1) {
                        CompactTaskButton(text = "Mark All Done") {
                            taskActions.onMarkDone(completableTasks)
                        }
                    }
                    if (!showCompletedMetrics && claimableTasks.isNotEmpty()) {
                        CompactTaskButton(text = "Claim All") {
                            taskActions.onAssignAllToSelf(claimableTasks)
                        }
                    }
                }
            }
            if (assigneeSummary != null || visitMetricText != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (assigneeSummary != null) {
                        Text(
                            assigneeSummary,
                            modifier = Modifier.weight(1f),
                            color = palette.textSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text("", modifier = Modifier.weight(1f))
                    }
                    if (visitMetricText != null) {
                        Text(
                            visitMetricText,
                            color = palette.textSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1
                        )
                    }
                }
            }
            machineGroup.tasks.forEach { task ->
                TaskRowView(
                    task = task,
                    showAssignee = showPerTaskAssignee,
                    showCompletedMetrics = showCompletedMetrics,
                    isUpdating = task.id in pendingMutationTaskIds,
                    taskActions = taskActions,
                    financialDisplay = financialDisplay,
                    appLocation = appLocation,
                    autoCalcCommission = autoCalcCommission
                )
            }
        }
    }
}
