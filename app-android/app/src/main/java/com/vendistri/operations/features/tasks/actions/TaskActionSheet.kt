package com.vendistri.operations.features.tasks.actions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.vendistri.operations.components.CompactCalendarPicker
import com.vendistri.operations.components.SkeletonLine
import com.vendistri.operations.components.sheets.VendistriActionSheet
import com.vendistri.operations.components.sheets.VendistriActionSheetLargeContentThreshold
import com.vendistri.operations.components.sheets.rememberVendistriActionSheetState
import com.vendistri.operations.design.AppColors
import com.vendistri.operations.features.tasks.TaskBulkSelectionRules
import com.vendistri.operations.features.tasks.TaskDateFormatters
import com.vendistri.operations.features.tasks.TaskGroupingHelpers
import com.vendistri.operations.features.tasks.TaskScheduleDate
import com.vendistri.operations.features.tasks.TaskSelectionIndicator
import com.vendistri.operations.features.tasks.TaskTypeIcon
import com.vendistri.operations.features.tasks.VendiTask
import com.vendistri.operations.features.tasks.taskTypeLabel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskActionSheet(
    state: TaskActionState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onTaskAssigneeSelected: (String, String?) -> Unit,
    onDateSelected: (String) -> Unit,
    onQuickDateSelected: (LocalDate) -> Unit,
    onTaskSelectionToggle: (String) -> Unit
) {
    if (!state.isPresented) return

    val action = state.activeAction
    val tint = action.tint
    val selectedTasks = state.selectedTasks
    val canSave = when (action) {
        TaskActionKind.Reassign -> state.hasReassignChanges
        else -> selectedTasks.isNotEmpty()
    }
    val actionSheetContentSize = if (action == TaskActionKind.Reschedule) {
        maxOf(state.actionableTasks.size, VendistriActionSheetLargeContentThreshold)
    } else {
        state.actionableTasks.size
    }
    val sheetState = rememberVendistriActionSheetState(contentSize = actionSheetContentSize)
    val usesLargeContentHeight = actionSheetContentSize >= VendistriActionSheetLargeContentThreshold
    var isCalendarOpen by remember(action) { mutableStateOf(false) }
    var visibleCalendarMonth by remember(state.selectedDate) {
        mutableStateOf(java.time.YearMonth.from(state.selectedDate.toLocalDateOrToday()))
    }

    VendistriActionSheet(
        onDismissRequest = { if (!state.isSaving) onDismiss() },
        contentSize = actionSheetContentSize,
        sheetState = sheetState,
        content = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (usesLargeContentHeight) Modifier.weight(1f) else Modifier)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (usesLargeContentHeight) Modifier.fillMaxSize() else Modifier),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    TaskActionTopBar(
                        action = action,
                        isSaving = state.isSaving,
                        canSave = canSave,
                        onDismiss = onDismiss,
                        onConfirm = onConfirm
                    )

                    state.errorMessage?.let {
                        Text(it, color = AppColors.error, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }

                    if (action == TaskActionKind.Reschedule) {
                        RescheduleDateField(
                            date = state.selectedDate.toLocalDateOrToday(),
                            enabled = !state.isSaving,
                            onClick = { isCalendarOpen = !isCalendarOpen }
                        )
                        QuickRescheduleOptions(
                            state = state,
                            isSaving = state.isSaving,
                            onQuickDateSelected = {
                                isCalendarOpen = false
                                visibleCalendarMonth = java.time.YearMonth.from(it)
                                onQuickDateSelected(it)
                            }
                        )
                    }

                    if (action != TaskActionKind.Reassign) {
                        Text(
                            text = action.promptText(selectedTasks.size),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    TaskBulkSelectionRules.serviceBundleInfoMessage(
                        allTasks = state.actionableTasks,
                        selectedTaskIds = state.selectedTaskIds
                    )?.let { message ->
                        Text(message, color = AppColors.muted, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }

                    if (state.actionableTasks.isEmpty()) {
                        Text(action.emptyText, color = AppColors.muted, modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Medium)
                    } else {
                        TaskActionTaskList(
                            state = state,
                            action = action,
                            modifier = Modifier.then(if (usesLargeContentHeight) Modifier.weight(1f) else Modifier),
                            onTaskAssigneeSelected = onTaskAssigneeSelected,
                            onTaskSelectionToggle = onTaskSelectionToggle
                        )
                    }
                }
                if (isCalendarOpen && action == TaskActionKind.Reschedule) {
                    CompactCalendarPicker(
                        selectedDate = state.selectedDate.toLocalDateOrToday(),
                        visibleMonth = visibleCalendarMonth,
                        onDateSelected = {
                            if (!state.isSaving && !it.isBefore(LocalDate.now())) {
                                visibleCalendarMonth = java.time.YearMonth.from(it)
                                onDateSelected(it.toString())
                                isCalendarOpen = false
                            }
                        },
                        onVisibleMonthChanged = { visibleCalendarMonth = it },
                        minimumDate = LocalDate.now(),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(y = 90.dp)
                            .zIndex(4f)
                    )
                }
            }
        }
    )
}

@Composable
private fun TaskActionTaskList(
    state: TaskActionState,
    action: TaskActionKind?,
    modifier: Modifier = Modifier,
    onTaskAssigneeSelected: (String, String?) -> Unit,
    onTaskSelectionToggle: (String) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TaskGroupingHelpers.groupByMachine(state.actionableTasks).forEach { machineGroup ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = machineGroup.name,
                    color = AppColors.muted,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                machineGroup.tasks.forEach { task ->
                    if (action == TaskActionKind.Reassign) {
                        TaskActionReassignRow(
                            task = task,
                            state = state,
                            isSaving = state.isSaving,
                            onAssigneeSelected = { assigneeId -> onTaskAssigneeSelected(task.id, assigneeId) }
                        )
                    } else {
                        TaskActionSelectableRow(
                            task = task,
                            action = action,
                            selectedDate = if (state.selectedTaskIds.contains(task.id)) state.selectedDate.toLocalDateOrToday() else null,
                            isSelected = state.selectedTaskIds.contains(task.id),
                            isSaving = state.isSaving,
                            onToggle = { onTaskSelectionToggle(task.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RescheduleDateField(
    date: LocalDate,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, AppColors.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(date.format(TaskDateFormatters.mediumDate), fontWeight = FontWeight.SemiBold)
            Text("▦", color = AppColors.vendBlue, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun QuickRescheduleOptions(
    state: TaskActionState,
    isSaving: Boolean,
    onQuickDateSelected: (LocalDate) -> Unit
) {
    val sourceDate = state.sourceDate
    val today = LocalDate.now()
    val items = buildList {
        if (sourceDate != null && sourceDate != today) add("Today (${quickDateText(today)})" to today)
        state.nextServiceCadenceDate?.let { add("Next cadence (${quickDateText(it)})" to it) }
    }
    if (items.isEmpty() && !state.isLoadingNextServiceCadenceDate) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Quick Reschedule",
            color = AppColors.muted,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            items.forEach { (label, date) ->
                Surface(
                    onClick = { onQuickDateSelected(date) },
                    enabled = !isSaving,
                    shape = RoundedCornerShape(999.dp),
                    color = AppColors.vendBlue.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, AppColors.vendBlue.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = AppColors.vendBlue,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (state.isLoadingNextServiceCadenceDate) {
                Text("Loading cadence...", color = AppColors.muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TaskActionTopBar(
    action: TaskActionKind?,
    isSaving: Boolean,
    canSave: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onDismiss,
            enabled = !isSaving,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = if (action == TaskActionKind.Cancel || action == TaskActionKind.Delete) "Close" else "Cancel",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                fontWeight = FontWeight.SemiBold
            )
        }

        Text(
            text = action.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Surface(
            onClick = onConfirm,
            enabled = !isSaving && canSave,
            shape = CircleShape,
            color = if (!canSave || isSaving) AppColors.border else action.tint
        ) {
            Text(
                text = if (isSaving) action.loadingTitle else action.buttonTitle,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun TaskActionReassignRow(
    task: VendiTask,
    state: TaskActionState,
    isSaving: Boolean,
    onAssigneeSelected: (String?) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, AppColors.border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TaskTypeIcon(task.type, modifier = Modifier.size(20.dp))
            Text(
                text = taskTypeLabel(task.type),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (state.isLoadingAssignees) {
                SkeletonLine(width = 148.dp)
            } else {
                TaskAssigneeDropdown(
                    label = assigneeLabel(state, task),
                    assignees = state.assignees,
                    enabled = !isSaving,
                    width = 172.dp,
                    onSelected = onAssigneeSelected
                )
            }
        }
    }
}

@Composable
private fun TaskActionSelectableRow(
    task: VendiTask,
    action: TaskActionKind?,
    selectedDate: LocalDate?,
    isSelected: Boolean,
    isSaving: Boolean,
    onToggle: () -> Unit
) {
    val tint = action.tint
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isSaving, onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) tint.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, if (isSelected) tint.copy(alpha = 0.58f) else MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TaskSelectionIndicator(isSelected = isSelected, tint = tint)
            TaskTypeIcon(task.type, modifier = Modifier.size(20.dp), tint = if (isSelected) tint else MaterialTheme.colorScheme.onSurface)
            Text(
                text = taskTypeLabel(task.type),
                modifier = Modifier.weight(1f),
                color = if (isSelected) tint else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (action == TaskActionKind.Reschedule) {
                Text(
                    text = selectedDate?.let { "Scheduled ${it.format(TaskDateFormatters.mediumDate)}" } ?: "rescheduling",
                    color = AppColors.muted,
                    style = MaterialTheme.typography.labelSmall,
                    fontStyle = if (selectedDate == null) FontStyle.Italic else FontStyle.Normal,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

private val TaskActionKind?.title: String
    get() = when (this) {
        TaskActionKind.Reassign -> "Reassign"
        TaskActionKind.Reschedule -> "Reschedule"
        TaskActionKind.Cancel -> "Cancel Tasks"
        TaskActionKind.Delete -> "Delete Tasks"
        null -> "Tasks"
    }

private val TaskActionKind?.buttonTitle: String
    get() = when (this) {
        TaskActionKind.Cancel -> "Cancel"
        TaskActionKind.Delete -> "Delete"
        else -> "Save"
    }

private val TaskActionKind?.loadingTitle: String
    get() = when (this) {
        TaskActionKind.Cancel -> "Canceling..."
        TaskActionKind.Delete -> "Deleting..."
        else -> "Saving..."
    }

private val TaskActionKind?.emptyText: String
    get() = when (this) {
        TaskActionKind.Cancel -> "No incomplete tasks to cancel."
        TaskActionKind.Delete -> "No incomplete tasks to delete."
        else -> "No incomplete tasks selected."
    }

private val TaskActionKind?.tint: Color
    get() = when (this) {
        TaskActionKind.Cancel -> AppColors.pending
        TaskActionKind.Delete -> AppColors.error
        TaskActionKind.Reassign -> AppColors.vendBlue
        else -> AppColors.vendBlue
    }

private fun TaskActionKind?.promptText(selectedCount: Int): String {
    val noun = if (selectedCount == 1) "task" else "tasks"
    return when (this) {
        TaskActionKind.Cancel -> if (selectedCount == 0) "Select tasks to cancel." else "Cancel $selectedCount incomplete $noun:"
        TaskActionKind.Delete -> if (selectedCount == 0) "Select tasks to delete." else "Delete $selectedCount incomplete $noun:"
        TaskActionKind.Reassign -> if (selectedCount == 0) "Select tasks to reassign." else "Reassign $selectedCount incomplete $noun:"
        TaskActionKind.Reschedule -> if (selectedCount == 0) "Select tasks to reschedule." else "Reschedule $selectedCount incomplete $noun:"
        null -> "Select tasks."
    }
}

private fun String?.toLocalDateOrToday(): LocalDate {
    return TaskScheduleDate.parse(this) ?: LocalDate.now()
}

private fun quickDateText(date: LocalDate): String {
    return date.format(TaskDateFormatters.shortDay)
}

private fun assigneeLabel(state: TaskActionState, task: VendiTask): String {
    val assigneeId = state.assigneeByTaskId[task.id]
    if (assigneeId == null) return "Unassigned"
    return state.assignees.firstOrNull { it.id == assigneeId }?.displayLabel
        ?: task.assigneeName
        ?: task.assigneeEmail
        ?: "Assignee"
}
