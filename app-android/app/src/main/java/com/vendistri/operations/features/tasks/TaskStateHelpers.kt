package com.vendistri.operations.features.tasks

object TaskStateHelpers {
    fun isFinal(status: TaskStatus): Boolean {
        return status == TaskStatus.Done || status == TaskStatus.Cancelled || status == TaskStatus.Error
    }

    fun isActionable(status: TaskStatus): Boolean {
        return status == TaskStatus.Pending || status == TaskStatus.Unassigned
    }

    fun isCompleted(status: TaskStatus): Boolean {
        return isFinal(status)
    }

    fun assigneeLine(task: VendiTask): String? {
        return when {
            !task.assigneeName.isNullOrBlank() -> "Assignee: ${task.assigneeName}"
            !task.assigneeEmail.isNullOrBlank() -> "Assignee: ${task.assigneeEmail}"
            task.assignee == null -> "Unassigned"
            else -> null
        }
    }
}
