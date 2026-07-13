package com.vendistri.operations.features.tasks

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun TaskMachineNotesFooter(
    machineGroup: TaskMachineGroup,
    onApplySharedNotes: suspend (List<String>, String?) -> Boolean,
    modifier: Modifier = Modifier
) {
    SharedTaskNotesFooter(
        tasks = machineGroup.tasks,
        focusKey = machineGroup.id,
        placeholder = "Notes for this machine...",
        onApplySharedNotes = onApplySharedNotes,
        modifier = modifier
    )
}
