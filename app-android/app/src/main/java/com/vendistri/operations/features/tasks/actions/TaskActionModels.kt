package com.vendistri.operations.features.tasks.actions

import com.vendistri.operations.features.tasks.VendiTask
import com.vendistri.operations.features.tasks.TaskStateHelpers
import java.time.LocalDate

enum class TaskActionKind {
    Reassign,
    Reschedule,
    Cancel,
    Delete
}

data class TaskActionState(
    val activeAction: TaskActionKind? = null,
    val tasks: List<VendiTask> = emptyList(),
    val selectedTaskIds: Set<String> = emptySet(),
    val assignees: List<TaskAssignee> = emptyList(),
    val assigneeByTaskId: Map<String, String?> = emptyMap(),
    val initialAssigneeByTaskId: Map<String, String?> = emptyMap(),
    val selectedDate: String? = null,
    val sourceDate: LocalDate? = null,
    val nextServiceCadenceDate: LocalDate? = null,
    val isLoadingNextServiceCadenceDate: Boolean = false,
    val isLoadingAssignees: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
) {
    val isPresented: Boolean
        get() = activeAction != null

    val actionableTasks: List<VendiTask>
        get() = tasks.filter { TaskStateHelpers.isActionable(it.status) }

    val selectedTasks: List<VendiTask>
        get() = actionableTasks.filter { selectedTaskIds.contains(it.id) }

    val hasReassignChanges: Boolean
        get() = actionableTasks.any { task ->
            assigneeByTaskId[task.id] != initialAssigneeByTaskId[task.id]
        }

    val reassignChangedTasks: List<VendiTask>
        get() = actionableTasks.filter { task ->
            assigneeByTaskId[task.id] != initialAssigneeByTaskId[task.id]
        }
}
