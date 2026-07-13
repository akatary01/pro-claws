package com.vendistri.operations.features.tasks.actions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vendistri.operations.design.AppColors

@Composable
internal fun TaskAssigneeDropdown(
    label: String,
    assignees: List<TaskAssignee>,
    enabled: Boolean,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = null
) {
    var isOpen by remember { mutableStateOf(false) }
    Surface(
        modifier = width?.let { modifier.size(width = it, height = 46.dp) } ?: modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, AppColors.border),
        enabled = enabled,
        onClick = { if (enabled) isOpen = !isOpen }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(if (isOpen) "⌃" else "⌄", color = AppColors.muted, fontWeight = FontWeight.SemiBold)
        }
        DropdownMenu(expanded = isOpen, onDismissRequest = { isOpen = false }) {
            DropdownMenuItem(
                text = { Text("Unassigned", fontWeight = FontWeight.SemiBold) },
                onClick = {
                    onSelected(null)
                    isOpen = false
                }
            )
            assignees.forEach { assignee ->
                DropdownMenuItem(
                    text = { Text(assignee.displayLabel, fontWeight = FontWeight.SemiBold) },
                    onClick = {
                        onSelected(assignee.id)
                        isOpen = false
                    }
                )
            }
        }
    }
}
