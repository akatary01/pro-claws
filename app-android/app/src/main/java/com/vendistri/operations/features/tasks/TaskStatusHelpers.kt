package com.vendistri.operations.features.tasks

data class TaskStatusCounts(
    val pending: Int,
    val unassigned: Int,
    val done: Int,
    val cancelled: Int,
    val total: Int
)

object TaskStatusHelpers {
    fun statusColor(
        hasPending: Boolean,
        hasUnassigned: Boolean = false,
        hasDone: Boolean,
        hasCancelled: Boolean,
        hasError: Boolean = false
    ): androidx.compose.ui.graphics.Color {
        return when {
            hasUnassigned -> TaskStatusPresentation.indicatorColor(TaskStatus.Unassigned)
            hasPending -> TaskStatusPresentation.indicatorColor(TaskStatus.Pending)
            hasDone -> TaskStatusPresentation.indicatorColor(TaskStatus.Done)
            hasCancelled -> TaskStatusPresentation.indicatorColor(TaskStatus.Cancelled)
            hasError -> TaskStatusPresentation.indicatorColor(TaskStatus.Error)
            else -> com.vendistri.operations.design.AppColors.muted
        }
    }

    fun statusFlags(tasks: List<VendiTask>): TaskStatusFlags {
        return TaskStatusFlags(
            hasPending = tasks.any { it.status == TaskStatus.Pending },
            hasUnassigned = tasks.any { it.status == TaskStatus.Unassigned },
            hasDone = tasks.any { it.status == TaskStatus.Done },
            hasCancelled = tasks.any { it.status == TaskStatus.Cancelled },
            hasError = tasks.any { it.status == TaskStatus.Error }
        )
    }

    fun statusCounts(tasks: List<VendiTask>): TaskStatusCounts {
        val unassigned = tasks.count { it.status == TaskStatus.Unassigned }
        val done = tasks.count { it.status == TaskStatus.Done }
        val cancelled = tasks.count { it.status == TaskStatus.Cancelled || it.status == TaskStatus.Error }
        val pending = (tasks.size - unassigned - done - cancelled).coerceAtLeast(0)
        return TaskStatusCounts(
            pending = pending,
            unassigned = unassigned,
            done = done,
            cancelled = cancelled,
            total = tasks.size
        )
    }

    fun primaryStatus(tasks: List<VendiTask>): TaskStatus {
        val counts = statusCounts(tasks)
        return when {
            counts.unassigned > 0 -> TaskStatus.Unassigned
            counts.pending > 0 -> TaskStatus.Pending
            counts.done > 0 -> TaskStatus.Done
            counts.cancelled > 0 -> TaskStatus.Cancelled
            else -> TaskStatus.Pending
        }
    }

    fun indicatorStatuses(tasks: List<VendiTask>): List<TaskStatus> {
        return TaskStatusPresentation.visibleStatuses(statusCounts(tasks))
    }

    fun indicatorColors(tasks: List<VendiTask>): List<androidx.compose.ui.graphics.Color> {
        return indicatorStatuses(tasks).map(TaskStatusPresentation::indicatorColor)
    }
}

data class TaskStatusFlags(
    val hasPending: Boolean,
    val hasUnassigned: Boolean,
    val hasDone: Boolean,
    val hasCancelled: Boolean,
    val hasError: Boolean
)
