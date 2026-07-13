package com.vendistri.operations.features.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vendistri.operations.design.AppColors

@Composable
internal fun TaskStatusTotalsRow(summary: TaskSummary) {
    val items = listOf(
        Triple(summary.open, TaskStatus.Pending, TaskStatusPresentation.label(TaskStatus.Pending)),
        Triple(summary.unassigned, TaskStatus.Unassigned, TaskStatusPresentation.label(TaskStatus.Unassigned)),
        Triple(summary.completed, TaskStatus.Done, TaskStatusPresentation.label(TaskStatus.Done)),
        Triple(summary.cancelled, TaskStatus.Cancelled, TaskStatusPresentation.label(TaskStatus.Cancelled))
    ).filter { it.first > 0 }
    if (items.isEmpty()) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, (count, status, label) ->
            if (index > 0) Spacer(modifier = Modifier.size(12.dp))
            Surface(
                modifier = Modifier.size(8.dp),
                shape = CircleShape,
                color = TaskStatusPresentation.indicatorColor(status)
            ) {}
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = "$count $label",
                color = AppColors.muted,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
