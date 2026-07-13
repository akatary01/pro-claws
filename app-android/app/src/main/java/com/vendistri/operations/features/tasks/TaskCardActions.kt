package com.vendistri.operations.features.tasks

import com.vendistri.operations.features.auth.User
import com.vendistri.operations.features.location.WarehouseOption
import com.vendistri.operations.features.refill.RefillInventoryUiState

data class RefillTaskEditorActions(
    val states: Map<String, RefillInventoryUiState>,
    val warehouses: List<WarehouseOption>,
    val onPrepare: (VendiTask) -> Unit,
    val onRefilledChanged: (String, String, String) -> Unit,
    val onFinalStockChanged: (String, String, String) -> Unit,
    val onSourceSelected: (VendiTask, RefillInventorySourceMode, String?) -> Unit,
    val onComplete: (VendiTask) -> Unit
)

data class TaskCardActions(
    val canAssignToSelf: (VendiTask) -> Boolean,
    val canChangeStatus: (VendiTask) -> Boolean,
    val canEditFinancials: (VendiTask) -> Boolean,
    val onAssignToSelf: (VendiTask) -> Unit,
    val onAssignAllToSelf: (List<VendiTask>) -> Unit,
    val onMarkDone: (List<VendiTask>) -> Unit,
    val onStatusChange: (VendiTask, TaskStatus) -> Unit,
    val onCollectionFinancialUpdate: (VendiTask, CollectionFinancialDraft) -> Unit,
    val onRefundFinancialUpdate: (VendiTask, Double) -> Unit,
    val refillEditor: RefillTaskEditorActions? = null
) {
    fun locking(taskIds: Set<String>): TaskCardActions {
        if (taskIds.isEmpty()) return this
        fun unlocked(task: VendiTask) = task.id !in taskIds
        fun unlocked(tasks: List<VendiTask>) = tasks.filter(::unlocked)
        return copy(
            canAssignToSelf = { unlocked(it) && canAssignToSelf(it) },
            canChangeStatus = { unlocked(it) && canChangeStatus(it) },
            canEditFinancials = { unlocked(it) && canEditFinancials(it) },
            onAssignToSelf = { if (unlocked(it)) onAssignToSelf(it) },
            onAssignAllToSelf = { tasks ->
                unlocked(tasks).takeIf { it.isNotEmpty() }?.let(onAssignAllToSelf)
            },
            onMarkDone = { tasks ->
                unlocked(tasks).takeIf { it.isNotEmpty() }?.let(onMarkDone)
            },
            onStatusChange = { task, status -> if (unlocked(task)) onStatusChange(task, status) },
            onCollectionFinancialUpdate = { task, draft ->
                if (unlocked(task)) onCollectionFinancialUpdate(task, draft)
            },
            onRefundFinancialUpdate = { task, amount ->
                if (unlocked(task)) onRefundFinancialUpdate(task, amount)
            }
        )
    }

    companion object {
        fun readOnly(): TaskCardActions {
            return TaskCardActions(
                canAssignToSelf = { false },
                canChangeStatus = { false },
                canEditFinancials = { false },
                onAssignToSelf = {},
                onAssignAllToSelf = {},
                onMarkDone = {},
                onStatusChange = { _, _ -> },
                onCollectionFinancialUpdate = { _, _ -> },
                onRefundFinancialUpdate = { _, _ -> }
            )
        }

        fun scheduled(
            user: User?,
            onAssignToSelf: (VendiTask) -> Unit,
            onAssignAllToSelf: (List<VendiTask>) -> Unit,
            onBulkMarkDone: (List<VendiTask>) -> Unit,
            onTaskStatusChange: (VendiTask, TaskStatus) -> Unit,
            onCollectionFinancialUpdate: (VendiTask, CollectionFinancialDraft) -> Unit,
            onRefundFinancialUpdate: (VendiTask, Double) -> Unit,
            refillEditor: RefillTaskEditorActions? = null
        ): TaskCardActions {
            val canCurrentUserAssignToSelf = TaskPermissions.canAssignToSelf(user)
            val canCurrentUserChangeStatus = TaskPermissions.canChangeTaskStatus(user, TaskActionContext.Scheduled)
            return TaskCardActions(
                canAssignToSelf = { task ->
                    canCurrentUserAssignToSelf && TaskRowActionPolicy.canClaim(task)
                },
                canChangeStatus = { task ->
                    canCurrentUserChangeStatus &&
                        task.assignee != null &&
                        task.status != TaskStatus.Unassigned &&
                        !TaskStateHelpers.isFinal(task.status)
                },
                canEditFinancials = { task ->
                    canCurrentUserChangeStatus &&
                        task.status == TaskStatus.Pending &&
                        task.assignee != null &&
                        !TaskStateHelpers.isFinal(task.status)
                },
                onAssignToSelf = onAssignToSelf,
                onAssignAllToSelf = onAssignAllToSelf,
                onMarkDone = onBulkMarkDone,
                onStatusChange = onTaskStatusChange,
                onCollectionFinancialUpdate = onCollectionFinancialUpdate,
                onRefundFinancialUpdate = onRefundFinancialUpdate,
                refillEditor = refillEditor
            )
        }

        fun activeExecution(
            user: User?,
            onAssignToSelf: (VendiTask) -> Unit,
            onAssignAllToSelf: (List<VendiTask>) -> Unit,
            onBulkMarkDone: (List<VendiTask>) -> Unit,
            onTaskStatusChange: (VendiTask, TaskStatus) -> Unit,
            onCollectionFinancialUpdate: (VendiTask, CollectionFinancialDraft) -> Unit,
            onRefundFinancialUpdate: (VendiTask, Double) -> Unit
        ): TaskCardActions {
            val canCurrentUserChangeStatus = TaskPermissions.canChangeTaskStatus(user, TaskActionContext.ActiveExecution)
            return TaskCardActions(
                canAssignToSelf = { false },
                canChangeStatus = { task ->
                    canCurrentUserChangeStatus &&
                        task.assignee != null &&
                        task.status != TaskStatus.Unassigned &&
                        !TaskStateHelpers.isFinal(task.status)
                },
                canEditFinancials = { task ->
                    canCurrentUserChangeStatus &&
                        task.status == TaskStatus.Pending &&
                        task.assignee != null &&
                        !TaskStateHelpers.isFinal(task.status)
                },
                onAssignToSelf = onAssignToSelf,
                onAssignAllToSelf = onAssignAllToSelf,
                onMarkDone = onBulkMarkDone,
                onStatusChange = onTaskStatusChange,
                onCollectionFinancialUpdate = onCollectionFinancialUpdate,
                onRefundFinancialUpdate = onRefundFinancialUpdate
            )
        }
    }
}
