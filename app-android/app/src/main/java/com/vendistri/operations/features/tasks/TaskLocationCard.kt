package com.vendistri.operations.features.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vendistri.operations.design.AppColors
import com.vendistri.operations.design.LocalVendistriPalette
import com.vendistri.operations.features.location.AppLocation
import com.vendistri.operations.features.location.LocationHours
import com.vendistri.operations.features.location.LocationHoursLabel
import com.vendistri.operations.features.tasks.actions.TaskActionKind
import com.vendistri.operations.utils.AddressFormatter
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate

@Composable
internal fun TaskLocationCard(
    locationGroup: TaskLocationGroup,
    isExpanded: Boolean,
    showCompletedMetrics: Boolean,
    onToggle: () -> Unit,
    onBulkTaskAction: (TaskActionKind, List<VendiTask>) -> Unit,
    onApplySharedNotes: suspend (List<String>, String?) -> Boolean,
    pendingMutationTaskIds: Set<String>,
    taskActions: TaskCardActions,
    liveTaskTarget: LiveTaskTarget? = null,
    liveTaskShowsTimer: Boolean = true,
    allowBulkActions: Boolean = true,
    showTaskMetrics: Boolean = true,
    financialDisplay: TaskFinancialDisplayMode = TaskFinancialDisplayMode.Full,
    appLocation: AppLocation?,
    autoCalcCommission: Boolean
) {
    val palette = LocalVendistriPalette.current
    val statusItems = LocationStatusRows.items(TaskStatusHelpers.statusCounts(locationGroup.tasks))
    val primaryStatusText = statusItems.firstOrNull()?.let { "${it.count} ${it.title.uppercase()}" }
    val statusText = if (isExpanded) primaryStatusText else LocationStatusRows.joinedStatusText(statusItems)
    val secondaryStatusItems = if (isExpanded) statusItems.drop(1) else emptyList()
    val addressLine = AddressFormatter.singleLine(
        streetLine = locationGroup.addressStreetLine ?: appLocation?.address?.street,
        cityStateZipLine = locationGroup.addressCityStateZipLine ?: AddressFormatter.cityStateZipLine(appLocation?.address)
    )
    val hoursDate = locationGroup.tasks.firstOrNull()?.scheduledFor?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: LocalDate.now()
    val hoursDisplay = LocationHours.display(appLocation, on = hoursDate)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, palette.border),
        color = palette.surface
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LocationStatusDotsView(colors = statusItems.map { it.color })
                        Text(
                            locationGroup.name,
                            color = palette.textPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.widthIn(max = 220.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (liveTaskTarget != null) {
                        LiveTaskPill(target = liveTaskTarget, showTimer = liveTaskShowsTimer)
                    } else {
                        statusText?.let {
                            Text(
                                text = it,
                                color = palette.textSecondary,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (isExpanded) "⌃" else "⌄",
                            color = palette.textSecondary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            if (isExpanded) {
                if (addressLine != null || hoursDisplay != null || secondaryStatusItems.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (addressLine != null) {
                            Text(
                                addressLine,
                                modifier = Modifier.weight(1f),
                                color = palette.textSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (hoursDisplay != null) {
                                LocationHoursLabel(display = hoursDisplay)
                            }
                            secondaryStatusItems.forEach { item ->
                                LocationStatusLineView(item = item)
                            }
                        }
                    }
                }
                if (!showCompletedMetrics && allowBulkActions) {
                    LocationActionRow(
                        onReschedule = { onBulkTaskAction(TaskActionKind.Reschedule, locationGroup.tasks) },
                        onReassign = { onBulkTaskAction(TaskActionKind.Reassign, locationGroup.tasks) }
                    )
                } else if (showCompletedMetrics && showTaskMetrics) {
                    TaskRollupMetricsRow(
                        durationMinutes = locationGroup.durationMinutes,
                        distanceMiles = locationGroup.distanceMiles,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        spacing = 18.dp
                    )
                }
                ReadOnlyFinancialBreakdownRows(
                    tasks = locationGroup.tasks,
                    showGross = financialDisplay == TaskFinancialDisplayMode.Full,
                    showNet = financialDisplay == TaskFinancialDisplayMode.Full
                )
                locationGroup.machineGroups.forEach { machine ->
                    TaskMachineCard(
                        machineGroup = machine,
                        showCompletedMetrics = showCompletedMetrics,
                        onBulkTaskAction = onBulkTaskAction,
                        onApplySharedNotes = onApplySharedNotes,
                        pendingMutationTaskIds = pendingMutationTaskIds,
                        taskActions = taskActions,
                        financialDisplay = financialDisplay,
                        appLocation = appLocation,
                        autoCalcCommission = autoCalcCommission
                    )
                }
                TaskLocationNotesFooter(
                    locationGroup = locationGroup,
                    onApplySharedNotes = onApplySharedNotes
                )
                if (!showCompletedMetrics && allowBulkActions) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { onBulkTaskAction(TaskActionKind.Cancel, locationGroup.tasks) }) {
                            Text("Cancel", color = AppColors.pending)
                        }
                        TextButton(onClick = { onBulkTaskAction(TaskActionKind.Delete, locationGroup.tasks) }) {
                            Text("Delete", color = AppColors.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiveTaskPill(
    target: LiveTaskTarget,
    showTimer: Boolean = true
) {
    val startedAtEpochMillis = remember(target.timerStartedAt) {
        target.timerStartedAt?.let { rawValue ->
            runCatching { Instant.parse(rawValue).toEpochMilli() }.getOrNull()
        }
    }
    var nowEpochMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtEpochMillis, showTimer) {
        if (!showTimer || startedAtEpochMillis == null) return@LaunchedEffect
        while (true) {
            nowEpochMillis = System.currentTimeMillis()
            delay(1000)
        }
    }
    val taskLabel = "${target.activeTaskCount.coerceAtLeast(1)} Live ${if (target.activeTaskCount == 1) "Task" else "Tasks"}"
    val label = if (showTimer && startedAtEpochMillis != null) {
        val elapsedSeconds = ((nowEpochMillis - startedAtEpochMillis) / 1000.0).coerceAtLeast(0.0)
        "$taskLabel ${formatTaskDuration(elapsedSeconds)}"
    } else {
        taskLabel
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = AppColors.statusDone.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, AppColors.statusDone.copy(alpha = 0.25f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = AppColors.statusDone,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LocationActionRow(
    onReschedule: () -> Unit,
    onReassign: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            onClick = onReschedule,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            color = AppColors.vendBlue.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, AppColors.vendBlue.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Reschedule", color = AppColors.vendBlue, fontWeight = FontWeight.Bold)
            }
        }
        Surface(
            onClick = onReassign,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            color = AppColors.pending.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, AppColors.pending.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Reassign", color = AppColors.pending, fontWeight = FontWeight.Bold)
            }
        }
    }
}
