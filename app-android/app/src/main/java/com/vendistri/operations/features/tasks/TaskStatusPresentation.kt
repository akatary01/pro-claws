package com.vendistri.operations.features.tasks

import androidx.compose.ui.graphics.Color
import com.vendistri.operations.design.AppColors

object TaskStatusPresentation {
    val priorityOrder = listOf(
        TaskStatus.Unassigned,
        TaskStatus.Pending,
        TaskStatus.Done,
        TaskStatus.Cancelled,
        TaskStatus.Error
    )

    fun label(status: TaskStatus): String {
        return when (status) {
            TaskStatus.Pending -> "Pending"
            TaskStatus.Done -> "Done"
            TaskStatus.Cancelled -> "Cancelled"
            TaskStatus.Unassigned -> "Unassigned"
            TaskStatus.Error -> "Error"
        }
    }

    fun actionLabel(status: TaskStatus): String {
        return if (status == TaskStatus.Cancelled) "Cancel" else label(status)
    }

    fun indicatorColor(status: TaskStatus): Color {
        return when (status) {
            TaskStatus.Pending -> AppColors.statusPending
            TaskStatus.Unassigned -> AppColors.statusUnassigned
            TaskStatus.Done -> AppColors.statusDone
            TaskStatus.Cancelled, TaskStatus.Error -> AppColors.statusError
        }
    }

    fun visibleStatuses(counts: TaskStatusCounts): List<TaskStatus> {
        return priorityOrder.filter { status ->
            when (status) {
                TaskStatus.Unassigned -> counts.unassigned > 0
                TaskStatus.Pending -> counts.pending > 0
                TaskStatus.Done -> counts.done > 0
                TaskStatus.Cancelled -> counts.cancelled > 0
                TaskStatus.Error -> false
            }
        }
    }

    fun textColor(status: TaskStatus): Color {
        return when (status) {
            TaskStatus.Pending -> Color(0xFF925703)
            TaskStatus.Done -> Color(0xFF067347)
            TaskStatus.Unassigned -> AppColors.statusUnassigned
            TaskStatus.Error -> Color(0xFFB91C1C)
            TaskStatus.Cancelled -> Color(0xFF515151)
        }
    }

    fun fillColor(status: TaskStatus): Color {
        return when (status) {
            TaskStatus.Pending -> Color(0xFFFEF3C7)
            TaskStatus.Done -> Color(0xFFD1F6E4)
            TaskStatus.Unassigned -> AppColors.statusUnassigned.copy(alpha = 0.12f)
            TaskStatus.Error -> Color(0xFFFEE5E5)
            TaskStatus.Cancelled -> Color(0xFFF5F5F5)
        }
    }

    fun borderColor(status: TaskStatus): Color {
        return when (status) {
            TaskStatus.Pending -> Color(0xFFFDE28D)
            TaskStatus.Done -> Color(0xFFA7ECB4)
            TaskStatus.Unassigned -> AppColors.statusUnassigned.copy(alpha = 0.28f)
            TaskStatus.Error -> Color(0xFFFCCACA)
            TaskStatus.Cancelled -> Color(0xFFE5E5E5)
        }
    }

    fun sortRank(status: TaskStatus): Int {
        return priorityOrder.indexOf(status).takeIf { it >= 0 } ?: priorityOrder.size
    }
}
