package com.vendistri.operations.features.tasks

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun TaskLocationNotesFooter(
    locationGroup: TaskLocationGroup,
    onApplySharedNotes: suspend (List<String>, String?) -> Boolean,
    modifier: Modifier = Modifier
) {
    SharedTaskNotesFooter(
        tasks = locationGroup.tasks,
        focusKey = locationGroup.id,
        placeholder = "Notes for this stop...",
        onApplySharedNotes = onApplySharedNotes,
        modifier = modifier
    )
}
