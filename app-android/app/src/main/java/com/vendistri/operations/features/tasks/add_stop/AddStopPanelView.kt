package com.vendistri.operations.features.tasks.add_stop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.vendistri.operations.components.CompactCalendarPicker
import com.vendistri.operations.components.SearchableDropdownOption
import com.vendistri.operations.components.SearchableMultiSelectDropdown
import com.vendistri.operations.design.AppColors
import com.vendistri.operations.features.location.AppLocation
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.TaskDateFormatters
import com.vendistri.operations.features.tasks.TaskSelectionIndicator
import com.vendistri.operations.features.tasks.SharedTaskNotesTextField
import com.vendistri.operations.features.tasks.TaskTypeIcon
import com.vendistri.operations.features.tasks.actions.TaskAssignee
import com.vendistri.operations.features.tasks.actions.TaskAssigneeDropdown
import com.vendistri.operations.features.tasks.taskTypeLabel
import com.vendistri.operations.utils.AddressFormatter
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun AddStopPanelView(
    state: AddStopUiState,
    locationsById: Map<String, AppLocation>,
    onPrepare: () -> Unit,
    onClose: () -> Unit,
    onDateChanged: (LocalDate) -> Unit,
    onNotesChanged: (String) -> Unit,
    onLocationToggle: (String) -> Unit,
    onMachineToggle: (String) -> Unit,
    onTaskTypeToggle: (String, TaskType) -> Unit,
    onAssigneeSelected: (String, String?) -> Unit,
    onConfirmPrecheck: () -> Unit,
    onConfirmRescheduleExisting: () -> Unit,
    onDismissPrecheckAlert: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) { onPrepare() }
    var isCalendarOpen by remember { mutableStateOf(false) }
    var calendarMonth by remember(state.selectedDate) { mutableStateOf(YearMonth.from(state.selectedDate)) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 14.dp)
                .padding(top = 20.dp, bottom = 20.dp)
                .widthIn(max = 620.dp)
                .fillMaxHeight()
                .imePadding(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, AppColors.border.copy(alpha = 0.65f)),
            shadowElevation = 12.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    AddStopHeader(
                        state = state,
                        onDateClick = { isCalendarOpen = !isCalendarOpen }
                    )
                    LocationSelector(
                        options = addStopLocationOptions(
                            locations = locationsById.values.toList(),
                            machines = state.machines
                        ),
                        selectedIds = state.selectedLocationIds,
                        onToggle = onLocationToggle
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        MachineGroups(
                            state = state,
                            locationsById = locationsById,
                            onLocationToggle = onLocationToggle,
                            onMachineToggle = onMachineToggle,
                            onTaskTypeToggle = onTaskTypeToggle,
                            onAssigneeSelected = onAssigneeSelected
                        )
                    }
                    SharedTaskNotesTextField(
                        text = state.sharedNotes,
                        onTextChange = onNotesChanged,
                        placeholder = "Notes...",
                        height = 88.dp,
                        enabled = !state.isSaving
                    )
                    AddStopFooter(
                        isSaving = state.isSaving,
                        onClose = onClose,
                        onSave = onSave
                    )
                }
                if (isCalendarOpen) {
                    CompactCalendarPicker(
                        selectedDate = state.selectedDate,
                        visibleMonth = calendarMonth,
                        onDateSelected = {
                            if (!it.isBefore(LocalDate.now())) {
                                onDateChanged(it)
                                calendarMonth = YearMonth.from(it)
                                isCalendarOpen = false
                            }
                        },
                        onVisibleMonthChanged = { calendarMonth = it },
                        minimumDate = LocalDate.now(),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 58.dp, end = 16.dp)
                            .zIndex(30f)
                    )
                }
            }
        }

        if (state.isLoading || state.isSaving) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(52.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }

        AddStopPrecheckDialog(
            alert = state.precheckAlert,
            isSaving = state.isSaving,
            onConfirmPrecheck = onConfirmPrecheck,
            onConfirmRescheduleExisting = onConfirmRescheduleExisting,
            onDismiss = onDismissPrecheckAlert
        )
    }
}

@Composable
private fun AddStopPrecheckDialog(
    alert: AddStopPrecheckAlertState?,
    isSaving: Boolean,
    onConfirmPrecheck: () -> Unit,
    onConfirmRescheduleExisting: () -> Unit,
    onDismiss: () -> Unit
) {
    if (alert == null) return
    val confirmText = when (alert) {
        is AddStopPrecheckAlertState.Blocked -> null
        is AddStopPrecheckAlertState.Confirm -> "Save"
        is AddStopPrecheckAlertState.RescheduleExisting -> "Reschedule"
    }
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(alert.title) },
        text = { Text(alert.message) },
        confirmButton = {
            if (confirmText != null) {
                TextButton(
                    onClick = {
                        when (alert) {
                            is AddStopPrecheckAlertState.Confirm -> onConfirmPrecheck()
                            is AddStopPrecheckAlertState.RescheduleExisting -> onConfirmRescheduleExisting()
                            is AddStopPrecheckAlertState.Blocked -> Unit
                        }
                    },
                    enabled = !isSaving
                ) {
                    Text(confirmText)
                }
            } else {
                TextButton(onClick = onDismiss, enabled = !isSaving) {
                    Text("OK")
                }
            }
        },
        dismissButton = {
            if (confirmText != null) {
                TextButton(onClick = onDismiss, enabled = !isSaving) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
private fun AddStopHeader(
    state: AddStopUiState,
    onDateClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Add Stop", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                AddStopDateChip(
                    date = state.selectedDate,
                    enabled = !state.isSaving,
                    onClick = onDateClick
                )
            }
            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            state.warningMessage?.let {
                Text(it, color = AppColors.pending, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AddStopDateChip(date: LocalDate, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                date.format(TaskDateFormatters.abbreviatedWeekdayShortDay),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun LocationSelector(
    options: List<SearchableDropdownOption>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit
) {
    SearchableMultiSelectDropdown(
        label = "Select location",
        options = options,
        selectedIds = selectedIds,
        onToggle = onToggle
    )
}

@Composable
private fun MachineGroups(
    state: AddStopUiState,
    locationsById: Map<String, AppLocation>,
    onLocationToggle: (String) -> Unit,
    onMachineToggle: (String) -> Unit,
    onTaskTypeToggle: (String, TaskType) -> Unit,
    onAssigneeSelected: (String, String?) -> Unit
) {
    if (state.selectedLocationIds.isEmpty()) {
        return
    }

    val machinesByLocation = state.visibleMachines.groupBy { it.locationId }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.selectedLocationIds.forEach { locationId ->
            val location = locationsById[locationId] ?: return@forEach
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, AppColors.border),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(location.name, fontWeight = FontWeight.Bold)
                            AddressFormatter.singleLineWithoutCountry(location.address)?.let {
                                Text(it, color = AppColors.muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        RemoveCircleButton(onClick = { onLocationToggle(locationId) })
                    }
                    machinesByLocation[locationId].orEmpty().forEach { machine ->
                        MachineRow(
                            machine = machine,
                            state = state,
                            onMachineToggle = onMachineToggle,
                            onTaskTypeToggle = onTaskTypeToggle,
                            onAssigneeSelected = onAssigneeSelected
                        )
                    }
                    if (machinesByLocation[locationId].isNullOrEmpty()) {
                        EmptyHint("No machines at this location.")
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoveCircleButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(24.dp),
        shape = CircleShape,
        color = AppColors.muted.copy(alpha = 0.78f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("-", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MachineRow(
    machine: AddStopMachine,
    state: AddStopUiState,
    onMachineToggle: (String) -> Unit,
    onTaskTypeToggle: (String, TaskType) -> Unit,
    onAssigneeSelected: (String, String?) -> Unit
) {
    val isSelected = machine.id in state.selectedMachineIds
    val blockReason = machine.blockReason
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = blockReason == null) { onMachineToggle(machine.id) },
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (isSelected) AppColors.vendBlue else AppColors.border)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TaskSelectionIndicator(
                    isSelected = isSelected,
                    tint = AppColors.vendBlue,
                    size = 22.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        machine.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    blockReason?.let { Text(it, color = AppColors.pending, style = MaterialTheme.typography.bodySmall) }
                }
            }
            if (isSelected) {
                AddStopTaskTypeDropdown(
                    selectedTypes = state.selectedTaskTypesByMachineId[machine.id].orEmpty(),
                    hasPaymentMethod = machine.hasPaymentMethod,
                    onToggle = { onTaskTypeToggle(machine.id, it) }
                )
                TaskAssigneeDropdown(
                    label = assigneeLabel(
                        selectedAssigneeId = state.selectedAssigneeIdByMachineId[machine.id],
                        assignees = state.assignees
                    ),
                    assignees = state.assignees,
                    enabled = true,
                    onSelected = { onAssigneeSelected(machine.id, it ?: AddStopAssigneeValue.Unassigned) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun AddStopTaskTypeDropdown(
    selectedTypes: Set<TaskType>,
    hasPaymentMethod: Boolean,
    onToggle: (TaskType) -> Unit
) {
    var isOpen by remember { mutableStateOf(false) }
    val title = AddStopTypeCatalog.all
        .filter { it in selectedTypes }
        .joinToString(", ") { taskTypeLabel(it) }
        .ifBlank { "Type" }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            onClick = { isOpen = !isOpen },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, AppColors.border)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(if (isOpen) "⌃" else "⌄", color = AppColors.muted, fontWeight = FontWeight.SemiBold)
            }
        }
        if (isOpen) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AddStopTypeCatalog.all.forEach { type ->
                    val disabled = type == TaskType.MachineCollection && !hasPaymentMethod
                    AddStopTaskTypeOption(
                        type = type,
                        isSelected = type in selectedTypes,
                        enabled = !disabled,
                        onClick = { onToggle(type) }
                    )
                }
            }
        }
    }
}

private fun assigneeLabel(selectedAssigneeId: String?, assignees: List<TaskAssignee>): String {
    return when (selectedAssigneeId) {
        null -> "Assignee"
        AddStopAssigneeValue.Unassigned -> "Unassigned"
        else -> assignees.firstOrNull { it.id == selectedAssigneeId }?.displayLabel ?: "Assignee"
    }
}

@Composable
private fun AddStopFooter(isSaving: Boolean, onClose: () -> Unit, onSave: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onClose, enabled = !isSaving) {
            Text("Cancel")
        }
        Button(
            onClick = onSave,
            enabled = !isSaving,
            modifier = Modifier
                .widthIn(min = 86.dp)
                .heightIn(min = 40.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.outline,
                disabledContentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text("Save", fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun addStopLocationOptions(
    locations: List<AppLocation>,
    machines: List<AddStopMachine>
): List<SearchableDropdownOption> {
    val machineNamesByLocationId = machines
        .mapNotNull { machine ->
            val locationId = machine.locationId ?: return@mapNotNull null
            locationId to machine.name
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, names) -> names.joinToString(" ") }

    return locations
        .filterNot { it.discontinued }
        .map { location ->
            val subtitle = AddressFormatter.singleLineWithoutCountry(location.address)
            SearchableDropdownOption(
                id = location.id,
                title = location.name,
                subtitle = subtitle,
                searchText = listOf(
                    location.name,
                    subtitle.orEmpty(),
                    machineNamesByLocationId[location.id].orEmpty()
                )
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
            )
        }
        .sortedBy { it.title.lowercase() }
}

@Composable
private fun AddStopTaskTypeOption(type: TaskType, isSelected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (isSelected) AppColors.vendBlue else AppColors.border),
        color = when {
            !enabled -> MaterialTheme.colorScheme.surfaceVariant
            isSelected -> AppColors.vendBlue.copy(alpha = 0.10f)
            else -> MaterialTheme.colorScheme.surface
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TaskTypeIcon(
                type = type,
                modifier = Modifier.size(18.dp),
                tint = if (enabled) MaterialTheme.colorScheme.onBackground else AppColors.muted
            )
            Text(
                text = taskTypeLabel(type),
                modifier = Modifier.weight(1f),
                color = if (enabled) MaterialTheme.colorScheme.onBackground else AppColors.muted,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            TaskSelectionIndicator(
                isSelected = isSelected,
                tint = AppColors.vendBlue,
                size = 20.dp
            )
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, color = AppColors.muted, style = MaterialTheme.typography.bodySmall)
}
