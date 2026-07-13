package com.vendistri.operations.features.tasks.actions

import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskBulkSelectionRules
import com.vendistri.operations.features.tasks.TaskBundleHelpers
import com.vendistri.operations.features.tasks.TaskStateHelpers
import com.vendistri.operations.features.tasks.TasksApi
import com.vendistri.operations.features.tasks.TasksStore
import com.vendistri.operations.features.tasks.VendiTask
import com.vendistri.operations.features.tasks.toLocalDatePrefix
import com.vendistri.operations.features.tasks.reschedule.RescheduleOutcome
import com.vendistri.operations.features.tasks.reschedule.TaskRescheduleStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

class TaskActionStore(
    private val tasksStore: TasksStore,
    private val assigneesApi: TaskAssigneesApi? = null,
    tasksApi: TasksApi? = null
) {
    private val rescheduleStore = tasksApi?.let { TaskRescheduleStore(tasksStore, it) }
    private val _state = MutableStateFlow(TaskActionState())
    val state: StateFlow<TaskActionState> = _state.asStateFlow()

    fun present(action: TaskActionKind, tasks: List<VendiTask>) {
        val actionableTasks = tasks
            .filter { TaskStateHelpers.isActionable(it.status) }
            .sortedWith(taskActionComparator)
        val initiallySelectedTaskIds = if (action == TaskActionKind.Reschedule) {
            emptySet()
        } else {
            actionableTasks.map { it.id }.toSet()
        }
        val selectedTaskIds = TaskBulkSelectionRules.normalizedSelection(
            allTasks = actionableTasks,
            selectedTaskIds = initiallySelectedTaskIds
        )
        val sourceDate = actionableTasks.firstOrNull()?.scheduledFor.toLocalDatePrefix() ?: LocalDate.now()
        val initialAssignees = actionableTasks.associate { it.id to it.assignee }
        _state.value = TaskActionState(
            activeAction = action,
            tasks = actionableTasks,
            selectedTaskIds = selectedTaskIds,
            assigneeByTaskId = initialAssignees,
            initialAssigneeByTaskId = initialAssignees,
            selectedDate = if (action == TaskActionKind.Reschedule) {
                maxOf(LocalDate.now(), sourceDate).toString()
            } else {
                actionableTasks.firstOrNull()?.scheduledFor?.takeIf { it.isNotBlank() }
            },
            sourceDate = sourceDate
        )
    }

    fun dismiss() {
        _state.value = TaskActionState()
    }

    fun selectTaskAssignee(taskId: String, assigneeId: String?) {
        _state.update { current ->
            val task = current.actionableTasks.firstOrNull { it.id == taskId }
            val nextAssignees = current.assigneeByTaskId.toMutableMap()
            val linkedTasks = task?.let {
                TaskBundleHelpers.unifiedServiceBundleTasks(current.actionableTasks, it)
                    .filter { linkedTask -> TaskStateHelpers.isActionable(linkedTask.status) }
            }.orEmpty()
            if (linkedTasks.isEmpty()) {
                nextAssignees[taskId] = assigneeId
            } else {
                linkedTasks.forEach { nextAssignees[it.id] = assigneeId }
            }
            current.copy(
                assigneeByTaskId = nextAssignees,
                errorMessage = null
            )
        }
    }

    fun selectDate(value: String) {
        _state.update { it.copy(selectedDate = value, errorMessage = null) }
    }

    fun selectQuickRescheduleDate(date: LocalDate) {
        _state.update {
            it.copy(
                selectedDate = date.toString(),
                selectedTaskIds = it.actionableTasks.map { task -> task.id }.toSet(),
                errorMessage = null
            )
        }
    }

    fun toggleTask(taskId: String) {
        _state.update { current ->
            if (current.activeAction == TaskActionKind.Reschedule && rescheduleStore != null) {
                return@update current.copy(
                    selectedTaskIds = rescheduleStore.selectedIdsAfterToggle(
                        tasks = current.actionableTasks,
                        selectedTaskIds = current.selectedTaskIds,
                        taskId = taskId
                    ),
                    errorMessage = null
                )
            }
            val nextIds = current.selectedTaskIds.toMutableSet().also { ids ->
                if (!ids.add(taskId)) ids.remove(taskId)
            }
            current.copy(
                selectedTaskIds = TaskBulkSelectionRules.normalizedSelection(
                    allTasks = current.actionableTasks,
                    selectedTaskIds = nextIds
                ),
                errorMessage = null
            )
        }
    }

    suspend fun loadAssigneesIfNeeded() {
        val api = assigneesApi ?: return
        val current = _state.value
        if (current.activeAction != TaskActionKind.Reassign || current.assignees.isNotEmpty() || current.isLoadingAssignees) {
            return
        }
        _state.update { it.copy(isLoadingAssignees = true, errorMessage = null) }
        try {
            _state.update {
                it.copy(
                    assignees = api.fetchAssignees(),
                    isLoadingAssignees = false
                )
            }
        } catch (error: Exception) {
            _state.update {
                it.copy(
                    isLoadingAssignees = false,
                    errorMessage = error.message ?: "Could not load assignees."
                )
            }
        }
    }

    suspend fun loadNextServiceCadenceDateIfNeeded() {
        val store = rescheduleStore ?: return
        val current = _state.value
        if (current.activeAction != TaskActionKind.Reschedule ||
            current.nextServiceCadenceDate != null ||
            current.isLoadingNextServiceCadenceDate
        ) {
            return
        }
        val locationId = current.actionableTasks.firstOrNull()?.location
        val sourceDate = current.sourceDate ?: return
        _state.update { it.copy(isLoadingNextServiceCadenceDate = true) }
        val nextDate = store.nextServiceCadenceDate(locationId, sourceDate)
        _state.update {
            it.copy(
                nextServiceCadenceDate = nextDate,
                isLoadingNextServiceCadenceDate = false
            )
        }
    }

    suspend fun confirmCurrentAction() {
        val current = _state.value
        if (current.isSaving) return
        val action = current.activeAction ?: return
        val selectedTasks = if (action == TaskActionKind.Reassign) current.reassignChangedTasks else current.selectedTasks
        selectedTasks.firstOrNull() ?: return

        if (action == TaskActionKind.Cancel || action == TaskActionKind.Delete) {
            TaskBulkSelectionRules.serviceBundleValidationMessage(
                allTasks = current.actionableTasks,
                selectedTaskIds = selectedTasks.map { it.id }.toSet()
            )?.let { message ->
                _state.update { it.copy(errorMessage = message) }
                return
            }
        }

        _state.update { it.copy(isSaving = true, errorMessage = null) }
        try {
            when (action) {
                TaskActionKind.Cancel -> tasksStore.cancelTasks(selectedTasks.map { it.id })
                TaskActionKind.Delete -> tasksStore.deleteTasks(selectedTasks.map { it.id })
                TaskActionKind.Reassign -> {
                    selectedTasks
                        .groupBy { current.assigneeByTaskId[it.id] }
                        .forEach { (assigneeId, tasks) ->
                            tasksStore.assignTasks(tasks.map { it.id }, assigneeId)
                        }
                }
                TaskActionKind.Reschedule -> {
                    val scheduledFor = current.selectedDate
                    if (scheduledFor.isNullOrBlank()) {
                        _state.update {
                            it.copy(
                                isSaving = false,
                                errorMessage = "Choose a date before saving this schedule."
                            )
                        }
                        return
                    }
                    val targetDate = scheduledFor.toLocalDatePrefix()
                    if (targetDate == null) {
                        _state.update {
                            it.copy(
                                isSaving = false,
                                errorMessage = "Choose a valid date before saving this schedule."
                            )
                        }
                        return
                    }
                    when (val outcome = rescheduleStore?.reschedule(current.actionableTasks, current.selectedTaskIds, targetDate)) {
                        RescheduleOutcome.Saved -> Unit
                        RescheduleOutcome.NoChanges -> {
                            _state.update { it.copy(isSaving = false, errorMessage = "Change task schedules to save.") }
                            return
                        }
                        is RescheduleOutcome.Blocked -> {
                            _state.update { it.copy(isSaving = false, errorMessage = outcome.message) }
                            return
                        }
                        null -> tasksStore.rescheduleTasks(selectedTasks.map { it.id }, scheduledFor)
                    }
                }
            }
            dismiss()
        } catch (error: Exception) {
            _state.update {
                it.copy(
                    isSaving = false,
                    errorMessage = error.message ?: "The task action failed."
                )
            }
        }
    }
}

private val taskActionComparator = compareBy<VendiTask>(
    { it.machineName.orEmpty().lowercase() },
    { com.vendistri.operations.features.tasks.taskTypeSortRank(it.type) },
    { it.createdAt.orEmpty() },
    { it.id }
)
