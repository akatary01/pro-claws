package com.vendistri.operations.features.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vendistri.operations.design.LocalVendistriPalette

@Composable
internal fun TaskStatusMenu(
    status: TaskStatus,
    isUpdating: Boolean,
    onSelect: (TaskStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalVendistriPalette.current
    var isOpen by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.width(128.dp),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, palette.border),
        color = palette.surface,
        onClick = { if (!isUpdating) isOpen = !isOpen },
        enabled = !isUpdating
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = TaskStatusPresentation.label(status),
                modifier = Modifier.weight(1f),
                color = palette.textPrimary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Surface(
                modifier = Modifier.size(8.dp),
                shape = CircleShape,
                color = TaskStatusPresentation.indicatorColor(status)
            ) {}
            Text(if (isOpen) "⌃" else "⌄", color = palette.textSecondary, fontWeight = FontWeight.SemiBold)
        }
        DropdownMenu(
            expanded = isOpen,
            onDismissRequest = { isOpen = false },
            modifier = Modifier.width(160.dp)
        ) {
            statusOptions.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = TaskStatusPresentation.actionLabel(option),
                                modifier = Modifier.weight(1f),
                                color = palette.textPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (option == status) {
                                Text("✓", color = palette.brand, fontWeight = FontWeight.Bold)
                            }
                            Surface(
                                modifier = Modifier.size(8.dp),
                                shape = CircleShape,
                                color = TaskStatusPresentation.indicatorColor(option)
                            ) {}
                        }
                    },
                    onClick = {
                        isOpen = false
                        if (option != status) onSelect(option)
                    }
                )
            }
        }
    }
}

private val statusOptions = listOf(
    TaskStatus.Pending,
    TaskStatus.Done,
    TaskStatus.Cancelled
)
