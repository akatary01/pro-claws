package com.vendistri.operations.features.tasks.add_stop

import com.vendistri.operations.features.location.AppLocation
import com.vendistri.operations.features.tasks.TaskBulkPrecheckExistingTask
import com.vendistri.operations.features.tasks.TaskBulkPrecheckItem
import com.vendistri.operations.features.tasks.TaskBulkPrecheckResult
import com.vendistri.operations.features.tasks.TaskCreateRequest
import com.vendistri.operations.features.tasks.TaskDateFormatters
import com.vendistri.operations.features.tasks.TaskScheduleDate
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.TasksStore
import com.vendistri.operations.features.tasks.taskTypeLabel
import com.vendistri.operations.features.tasks.taskTypeSortRank
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

class AddStopStore(
    private val api: AddStopDataSource,
    private val tasksStore: TasksStore
) {
    private val _state = MutableStateFlow(AddStopUiState())
    val state: StateFlow<AddStopUiState> = _state.asStateFlow()

    suspend fun prepare(locationsById: Map<String, AppLocation>) {
        _state.update {
            it.copy(
                selectedDate = maxOf(LocalDate.now(), it.selectedDate),
                errorMessage = null,
                warningMessage = null,
                untouchedMachinesConfirmed = false,
                precheckAlert = null
            )
        }
        // Locations and machines can change while the app is open; the modal must never
        // combine a fresh location record with a stale machine catalog.
        loadCatalogIfNeeded(force = true)
        pruneSelections(locationsById)
        applyDefaultAssignees(locationsById)
    }

    fun reset() {
        _state.value = AddStopUiState(
            machines = _state.value.machines,
            assignees = _state.value.assignees,
            didLoadCatalog = _state.value.didLoadCatalog
        )
    }

    suspend fun loadCatalogIfNeeded(force: Boolean = false) {
        val current = _state.value
        if (current.isLoading) return
        if (current.didLoadCatalog && !force) return

        _state.update { it.copy(isLoading = true, errorMessage = null) }
        try {
            val machines = api.fetchMachines()
            val assignees = api.fetchAssignees()
            _state.update {
                it.copy(
                    machines = machines,
                    assignees = assignees,
                    isLoading = false,
                    didLoadCatalog = true
                )
            }
        } catch (error: Exception) {
            _state.update {
                it.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Could not load add stop options."
                )
            }
        }
    }

    fun setDate(date: LocalDate) {
        _state.update {
            it.copy(
                selectedDate = maxOf(LocalDate.now(), date),
                warningMessage = null,
                untouchedMachinesConfirmed = false,
                precheckAlert = null
            )
        }
    }

    fun setNotes(notes: String) {
        _state.update {
            it.copy(
                sharedNotes = notes,
                warningMessage = null,
                untouchedMachinesConfirmed = false,
                precheckAlert = null
            )
        }
    }

    fun toggleLocation(locationId: String, locationsById: Map<String, AppLocation>) {
        _state.update { state ->
            val nextLocations = state.selectedLocationIds.toMutableSet().also { ids ->
                if (!ids.add(locationId)) ids.remove(locationId)
            }
            val visibleMachineIds = state.machines
                .filter { it.locationId in nextLocations }
                .map { it.id }
                .toSet()
            state.copy(
                selectedLocationIds = nextLocations,
                selectedMachineIds = state.selectedMachineIds.filterTo(mutableSetOf()) { it in visibleMachineIds },
                selectedTaskTypesByMachineId = state.selectedTaskTypesByMachineId.filterKeys { it in visibleMachineIds },
                selectedAssigneeIdByMachineId = state.selectedAssigneeIdByMachineId.filterKeys { it in visibleMachineIds },
                errorMessage = null,
                warningMessage = null,
                untouchedMachinesConfirmed = false,
                precheckAlert = null
            )
        }
        applyDefaultAssignees(locationsById)
    }

    fun toggleMachine(machineId: String) {
        _state.update { state ->
            val nextMachines = state.selectedMachineIds.toMutableSet()
            val nextTypes = state.selectedTaskTypesByMachineId.toMutableMap()
            val nextAssignees = state.selectedAssigneeIdByMachineId.toMutableMap()
            if (nextMachines.add(machineId)) {
                val machine = state.machines.firstOrNull { it.id == machineId }
                if (machine != null && nextTypes[machineId] == null) {
                    nextTypes[machineId] = normalizedTypes(machine.defaultTaskTypes, machine)
                }
            } else {
                nextMachines.remove(machineId)
                nextTypes.remove(machineId)
                nextAssignees.remove(machineId)
            }
            state.copy(
                selectedMachineIds = nextMachines,
                selectedTaskTypesByMachineId = nextTypes,
                selectedAssigneeIdByMachineId = nextAssignees,
                errorMessage = null,
                warningMessage = null,
                untouchedMachinesConfirmed = false,
                precheckAlert = null
            )
        }
    }

    fun toggleTaskType(machineId: String, type: TaskType) {
        _state.update { state ->
            val machine = state.machines.firstOrNull { it.id == machineId } ?: return@update state
            val nextTypes = state.selectedTaskTypesByMachineId.toMutableMap()
            val current = nextTypes[machineId].orEmpty().toMutableSet()
            if (!current.add(type)) current.remove(type)
            nextTypes[machineId] = normalizedTypes(current, machine)
            state.copy(
                selectedMachineIds = state.selectedMachineIds + machineId,
                selectedTaskTypesByMachineId = nextTypes,
                errorMessage = null,
                warningMessage = null,
                untouchedMachinesConfirmed = false,
                precheckAlert = null
            )
        }
    }

    fun setAssignee(machineId: String, assigneeId: String?) {
        _state.update { state ->
            val nextAssignees = state.selectedAssigneeIdByMachineId.toMutableMap()
            nextAssignees[machineId] = assigneeId
            state.copy(
                selectedMachineIds = state.selectedMachineIds + machineId,
                selectedAssigneeIdByMachineId = nextAssignees,
                errorMessage = null,
                warningMessage = null,
                untouchedMachinesConfirmed = false,
                precheckAlert = null
            )
        }
    }

    suspend fun save(): Boolean {
        val validation = validate()
        if (validation != null) {
            _state.update { it.copy(errorMessage = validation, warningMessage = null) }
            return false
        }

        val items = createPayloadItems()
        if (items.isEmpty()) {
            _state.update { it.copy(errorMessage = "Select at least one task type before saving.", warningMessage = null) }
            return false
        }

        val untouchedMachineCount = untouchedMachineCount(_state.value)
        if (untouchedMachineCount > 0 && !_state.value.untouchedMachinesConfirmed) {
            val message = if (untouchedMachineCount == 1) {
                "1 machine will be left untouched. Press Save again to continue."
            } else {
                "$untouchedMachineCount machines will be left untouched. Press Save again to continue."
            }
            _state.update {
                it.copy(
                    errorMessage = null,
                    warningMessage = message,
                    untouchedMachinesConfirmed = true,
                    precheckAlert = null
                )
            }
            return false
        }

        _state.update { it.copy(isSaving = true, errorMessage = null, precheckAlert = null) }
        return try {
            val precheckResults = api.bulkPrecheckTasks(items.toPrecheckItems())
            precheckAlert(precheckResults, items)?.let { alert ->
                _state.update { it.copy(isSaving = false, precheckAlert = alert) }
                return false
            }
            executeSave(items)
        } catch (error: Exception) {
            _state.update {
                it.copy(
                    isSaving = false,
                    errorMessage = error.message ?: "Could not add stop."
                )
            }
            false
        }
    }

    suspend fun confirmPrecheckAndSave(): Boolean {
        val alert = _state.value.precheckAlert
        if (alert !is AddStopPrecheckAlertState.Confirm) return false
        _state.update { it.copy(isSaving = true, precheckAlert = null, errorMessage = null) }
        return try {
            executeSave(alert.items)
        } catch (error: Exception) {
            _state.update {
                it.copy(
                    isSaving = false,
                    errorMessage = error.message ?: "Could not add stop."
                )
            }
            false
        }
    }

    suspend fun confirmRescheduleExistingAndSave(): Boolean {
        val alert = _state.value.precheckAlert
        if (alert !is AddStopPrecheckAlertState.RescheduleExisting) return false
        val intendedItem = alert.items.firstOrNull { it.isHandledBy(alert.existingTask) }
        _state.update { it.copy(isSaving = true, precheckAlert = null, errorMessage = null) }
        return try {
            // Revalidate the move itself. Excluding the task being moved lets the
            // backend detect any other same-day service/collection/refill conflict,
            // including completed work, using its canonical per-type rules.
            val rescheduleResults = api.bulkPrecheckTasks(
                listOf(
                    TaskBulkPrecheckItem(
                        type = alert.existingTask.type,
                        machineId = alert.existingTask.machineId,
                        scheduledFor = _state.value.selectedDate,
                        taskId = alert.existingTask.id
                    )
                )
            )
            blockedPrecheckAlert(
                results = rescheduleResults,
                items = alert.items,
                allowsRescheduleExisting = false
            )?.let { blocked ->
                _state.update { it.copy(isSaving = false, precheckAlert = blocked) }
                return false
            }
            tasksStore.rescheduleTasks(
                taskIds = listOf(alert.existingTask.id),
                scheduledFor = _state.value.selectedDate.toString(),
                assigneeId = intendedItem?.assigneeId
            )
            val remainingItems = alert.items.filterNot { it.isHandledBy(alert.existingTask) }
            if (remainingItems.isEmpty()) {
                reset()
                true
            } else {
                val remainingResults = api.bulkPrecheckTasks(remainingItems.toPrecheckItems())
                precheckAlert(remainingResults, remainingItems)?.let { nextAlert ->
                    _state.update { it.copy(isSaving = false, precheckAlert = nextAlert) }
                    return false
                }
                executeSave(remainingItems)
            }
        } catch (error: Exception) {
            _state.update {
                it.copy(
                    isSaving = false,
                    errorMessage = error.message ?: "Could not add stop."
                )
            }
            false
        }
    }

    fun dismissPrecheckAlert() {
        _state.update { it.copy(precheckAlert = null) }
    }

    private fun validate(): String? {
        val state = _state.value
        if (state.selectedLocationIds.isEmpty()) return "Select at least one location before saving."
        if (state.taskableVisibleMachines.isEmpty()) return "There are no available machines to create tasks for."
        val selectedMachines = state.taskableVisibleMachines.filter { it.id in state.selectedMachineIds }
        if (selectedMachines.isEmpty()) return "Select at least one machine and task type before saving."

        selectedMachines.forEach { machine ->
            val selectedTypes = state.selectedTaskTypesByMachineId[machine.id].orEmpty()
            if (selectedTypes.isEmpty()) return "Select a task type for ${machine.name}."
            if (TaskType.MachineCollection in selectedTypes && !machine.hasPaymentMethod) {
                return "Collection requires a machine with an enabled payment method."
            }
            if (TaskType.MachineService in selectedTypes &&
                selectedTypes.intersect(AddStopTypeCatalog.serviceBundleChildTypes).isEmpty()
            ) {
                return "Select at least one bundled task for ${machine.name}."
            }
        }
        return null
    }

    private suspend fun executeSave(items: List<TaskCreateRequest>): Boolean {
        tasksStore.mergeServerTasks(api.bulkCreateTasks(items))
        reset()
        return true
    }

    private fun precheckAlert(
        results: List<TaskBulkPrecheckResult>,
        items: List<TaskCreateRequest>
    ): AddStopPrecheckAlertState? {
        blockedPrecheckAlert(results, items)?.let { return it }
        val existingTasks = results.mapNotNull { it.existingTask }
        if (existingTasks.isEmpty()) return null
        val lines = existingTasks.map { existing ->
            val machineName = _state.value.machines.firstOrNull { it.id == existing.machineId }?.name ?: "Machine"
            "${taskTypeLabel(existing.type)} - $machineName"
        }
        return AddStopPrecheckAlertState.Confirm(
            title = "Confirm Tasks",
            message = (listOf("Existing tasks scheduled for this day:") + lines).joinToString("\n"),
            items = items
        )
    }

    private fun blockedPrecheckAlert(
        results: List<TaskBulkPrecheckResult>,
        items: List<TaskCreateRequest>,
        allowsRescheduleExisting: Boolean = true
    ): AddStopPrecheckAlertState? {
        val blocked = results.firstOrNull { !it.ok } ?: return null
        val machineName = _state.value.machines.firstOrNull { it.id == blocked.machineId }?.name ?: "Machine"
        val existingTask = blocked.existingTask
        val requestedDate = _state.value.selectedDate
        val existingDate = TaskScheduleDate.parse(existingTask?.scheduledFor) ?: _state.value.selectedDate
        val requestedDateText = requestedDate.format(TaskDateFormatters.abbreviatedWeekdayShortDay)
        val existingDateText = existingDate.format(TaskDateFormatters.abbreviatedWeekdayShortDay)
        val reason = blocked.reason.orEmpty().lowercase()
        if (existingTask != null) {
            if (allowsRescheduleExisting && existingDate != requestedDate) {
                return AddStopPrecheckAlertState.RescheduleExisting(
                    title = "Reschedule Existing Task?",
                    message = "$machineName has a ${taskTypeLabel(existingTask.type).lowercase()} task scheduled for $existingDateText. Reschedule it to $requestedDateText before creating this stop.",
                    existingTask = existingTask,
                    items = items
                )
            }
            return AddStopPrecheckAlertState.Blocked(
                title = "${taskTypeLabel(existingTask.type)} Task Already Scheduled",
                message = "$machineName already has a ${taskTypeLabel(existingTask.type).lowercase()} task scheduled for $existingDateText.",
                existingTask = existingTask,
                items = items
            )
        }
        val message = when {
            reason.contains("payment") ->
                "Collection tasks require a machine with at least one enabled payment method. Update $machineName and try again."
            reason.contains("inactive") ->
                "$machineName is inactive. Activate the machine before creating tasks."
            reason.contains("matching query does not exist") || reason.contains("unassigned") ->
                "$machineName is not assigned to a location. Assign it before creating tasks."
            else ->
                "This machine cannot be scheduled right now. Review its setup and try again."
        }
        return AddStopPrecheckAlertState.Blocked(
            title = "Task Blocked",
            message = message,
            existingTask = null,
            items = items
        )
    }

    private fun createPayloadItems(): List<TaskCreateRequest> {
        val state = _state.value
        val notes = state.sharedNotes.trim().ifBlank { null }
        return state.taskableVisibleMachines
            .filter { it.id in state.selectedMachineIds }
            .flatMap { machine ->
                val selectedTypes = state.selectedTaskTypesByMachineId[machine.id].orEmpty()
                val serviceBundleTypes = selectedTypes.intersect(AddStopTypeCatalog.serviceBundleChildTypes)
                val effectiveTypes = if (TaskType.MachineService in selectedTypes) {
                    selectedTypes - AddStopTypeCatalog.serviceBundleChildTypes
                } else {
                    selectedTypes
                }
                effectiveTypes.map { type ->
                    TaskCreateRequest(
                        type = type,
                        machineId = machine.id,
                        scheduledFor = state.selectedDate,
                        assigneeId = state.selectedAssigneeIdByMachineId[machine.id]
                            ?.takeUnless { it == AddStopAssigneeValue.Unassigned },
                        notes = notes,
                        childTaskTypes = if (type == TaskType.MachineService) {
                            serviceBundleTypes.sortedBy(::taskTypeSortRank)
                        } else {
                            null
                        }
                    )
                }
            }
            .sortedWith(compareBy({ it.machineId }, { taskTypeSortRank(it.type) }))
    }

    private fun untouchedMachineCount(state: AddStopUiState): Int {
        val selectedTaskableCount = state.taskableVisibleMachines.count { it.id in state.selectedMachineIds }
        return state.taskableVisibleMachines.size - selectedTaskableCount
    }

    private fun pruneSelections(locationsById: Map<String, AppLocation>) {
        _state.update { state ->
            val validLocationIds = state.selectedLocationIds.filterTo(mutableSetOf()) { it in locationsById }
            val visibleMachineIds = state.machines.filter { it.locationId in validLocationIds }.map { it.id }.toSet()
            state.copy(
                selectedLocationIds = validLocationIds,
                selectedMachineIds = state.selectedMachineIds.filterTo(mutableSetOf()) { it in visibleMachineIds },
                selectedTaskTypesByMachineId = state.selectedTaskTypesByMachineId.filterKeys { it in visibleMachineIds },
                selectedAssigneeIdByMachineId = state.selectedAssigneeIdByMachineId.filterKeys { it in visibleMachineIds },
                untouchedMachinesConfirmed = false
            )
        }
    }

    private fun applyDefaultAssignees(locationsById: Map<String, AppLocation>) {
        _state.update { state ->
            val nextAssignees = state.selectedAssigneeIdByMachineId.toMutableMap()
            state.taskableVisibleMachines.forEach { machine ->
                val defaultAssigneeId = machine.locationId
                    ?.let(locationsById::get)
                    ?.defaultAssigneeId
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
                if (defaultAssigneeId != null && nextAssignees[machine.id] == null) {
                    nextAssignees[machine.id] = defaultAssigneeId
                }
            }
            state.copy(selectedAssigneeIdByMachineId = nextAssignees)
        }
    }

    private fun normalizedTypes(types: Set<TaskType>, machine: AddStopMachine): Set<TaskType> {
        return if (machine.hasPaymentMethod) types else types - TaskType.MachineCollection
    }
}

private fun List<TaskCreateRequest>.toPrecheckItems(): List<TaskBulkPrecheckItem> {
    return map {
        TaskBulkPrecheckItem(
            type = it.type,
            machineId = it.machineId,
            scheduledFor = it.scheduledFor
        )
    }
}

private fun TaskCreateRequest.isHandledBy(existingTask: TaskBulkPrecheckExistingTask): Boolean {
    if (machineId != existingTask.machineId) return false
    if (type == existingTask.type) return true
    return existingTask.type == TaskType.MachineService && type in AddStopTypeCatalog.defaultTaskTypes
}
