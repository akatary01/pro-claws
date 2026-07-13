package com.vendistri.operations.features.tasks

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

@Composable
internal fun TaskTypeIcon(
    type: TaskType,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Text(
        text = taskTypeIconSymbol(type),
        modifier = modifier,
        color = tint,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold
    )
}

private fun taskTypeIconSymbol(type: TaskType): String {
    return when (type) {
        TaskType.MachineService -> "◢"
        TaskType.MachineCollection -> "▣"
        TaskType.MachineRefill -> "↑"
        TaskType.MachineClean -> "✦"
        TaskType.MachineRepair -> "◇"
        TaskType.MachineRefund -> "$"
        TaskType.MachineInstall -> "+"
        TaskType.MachineRemove -> "-"
        TaskType.MachinePickupInventory -> "□"
        TaskType.Default, TaskType.Other -> "✓"
    }
}
