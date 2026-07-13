package com.vendistri.operations.features.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class LocationStatusLineItem(
    val color: Color,
    val count: Int,
    val title: String
)

object LocationStatusRows {
    fun items(counts: TaskStatusCounts): List<LocationStatusLineItem> {
        return TaskStatusPresentation.visibleStatuses(counts).mapNotNull { status ->
            when (status) {
                TaskStatus.Pending -> LocationStatusLineItem(
                    color = TaskStatusPresentation.indicatorColor(TaskStatus.Pending),
                    count = counts.pending,
                    title = TaskStatusPresentation.label(TaskStatus.Pending)
                )
                TaskStatus.Unassigned -> LocationStatusLineItem(
                    color = TaskStatusPresentation.indicatorColor(TaskStatus.Unassigned),
                    count = counts.unassigned,
                    title = TaskStatusPresentation.label(TaskStatus.Unassigned)
                )
                TaskStatus.Done -> LocationStatusLineItem(
                    color = TaskStatusPresentation.indicatorColor(TaskStatus.Done),
                    count = counts.done,
                    title = TaskStatusPresentation.label(TaskStatus.Done)
                )
                TaskStatus.Cancelled, TaskStatus.Error -> LocationStatusLineItem(
                    color = TaskStatusPresentation.indicatorColor(TaskStatus.Cancelled),
                    count = counts.cancelled,
                    title = TaskStatusPresentation.label(TaskStatus.Cancelled)
                )
            }.takeIf { it.count > 0 }
        }
    }

    fun joinedStatusText(items: List<LocationStatusLineItem>): String? {
        if (items.isEmpty()) return null
        return items.joinToString(" • ") { "${it.count} ${it.title.uppercase()}" }
    }
}

@Composable
internal fun LocationStatusDotsView(colors: List<Color>) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        colors.forEach { color ->
            Surface(modifier = Modifier.size(8.dp), shape = CircleShape, color = color) {}
        }
    }
}

@Composable
internal fun LocationStatusLineView(item: LocationStatusLineItem, showDot: Boolean = true) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showDot) {
            Surface(modifier = Modifier.size(8.dp), shape = CircleShape, color = item.color) {}
        }
        Text(
            text = "${item.count} ${item.title.uppercase()}",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
