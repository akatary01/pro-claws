package com.vendistri.operations.features.tasks

import com.vendistri.operations.features.auth.User
import com.vendistri.operations.features.map.LocationCoordinate
import com.vendistri.operations.features.pickup.PickupInventoryCompletionLine
import com.vendistri.operations.features.refill.AggregateRefillInventorySuggestion
import com.vendistri.operations.features.refill.RefillInventoryCompletionLine
import com.vendistri.operations.features.refill.RefillInventoryContext
import com.vendistri.operations.network.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private data class LoadedScheduledTaskScope(
    val fromKey: String,
    val toKey: String
)

private val backendDateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

class TasksStore(
    private val tasksApi: TasksApi = TasksApi(ApiClient())
) {
    private val _state = MutableStateFlow(TasksUiState())
    val state: StateFlow<TasksUiState> = _state.asStateFlow()
    private var loadedScheduledScopes = emptyList<LoadedScheduledTaskScope>()
    private var hasLoadedFullScope = false
    private var needsReloadAfterCurrentLoad = false
    private var pendingReloadFilters: TaskFetchFilters? = null

    fun resetUserScopedState() {
        _state.value = TasksUiState()
        loadedScheduledScopes = emptyList()
        hasLoadedFullScope = false
        needsReloadAfterCurrentLoad = false
        pendingReloadFilters = null
    }

    fun setPreviewTasks(tasks: List<VendiTask>) {
        _state.value = TasksUiState(
            tasks = tasks,
            tasksById = tasks.associateBy { it.id },
            summary = TaskSummary.fromTasks(tasks),
            hasLoadedOnce = true
        )
        loadedScheduledScopes = emptyList()
        hasLoadedFullScope = true
    }

    suspend fun loadTasks(force: Boolean = false, filters: TaskFetchFilters = TaskFetchFilters()) {
        val current = _state.value
        if ((current.isLoading || current.isRefreshing) && !force) {
            if (!isScheduledScopeLoaded(filters)) {
                needsReloadAfterCurrentLoad = true
                pendingReloadFilters = filters
            }
            return
        }
        if (!force && isScheduledScopeLoaded(filters)) return

        _state.update {
            if (!it.hasLoadedOnce) it.copy(isLoading = true, lastLoadError = null)
            else it.copy(isRefreshing = true, lastLoadError = null)
        }

        try {
            val tasks = tasksApi.fetchTasks(filters)
            if (filters.isScoped) {
                mergeTasks(tasks, replacingScopeMatching = filters)
            } else {
                replaceTasks(tasks)
            }
            _state.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    hasLoadedOnce = true,
                    lastLoadError = null
                )
            }
            markScheduledScopeLoaded(filters)
        } catch (error: Exception) {
            _state.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    hasLoadedOnce = true,
                    lastLoadError = error.message ?: "Failed to load tasks."
                )
            }
        }

        if (needsReloadAfterCurrentLoad) {
            val nextFilters = pendingReloadFilters ?: TaskFetchFilters()
            needsReloadAfterCurrentLoad = false
            pendingReloadFilters = null
            loadTasks(force = false, filters = nextFilters)
        }
    }

    suspend fun loadTasksForDate(date: LocalDate = LocalDate.now(), force: Boolean = true) {
        val dayKey = date.format(backendDateFormatter)
        loadTasks(
            force = force,
            filters = TaskFetchFilters(
                scheduledFrom = dayKey,
                scheduledTo = dayKey
            )
        )
    }

    suspend fun loadTasksForWeek(date: LocalDate = LocalDate.now(), force: Boolean = true) {
        val weekStart = date.minusDays((date.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
        val weekEnd = weekStart.plusDays(6)
        loadTasks(
            force = force,
            filters = TaskFetchFilters(
                scheduledFrom = weekStart.format(backendDateFormatter),
                scheduledTo = weekEnd.format(backendDateFormatter)
            )
        )
    }

    suspend fun loadContactTasks(force: Boolean = true, filters: TaskFetchFilters = TaskFetchFilters()) {
        val current = _state.value
        if ((current.isLoading || current.isRefreshing) && !force) return
        if (!force && hasLoadedFullScope && !filters.isScoped) return

        _state.update {
            if (!it.hasLoadedOnce) it.copy(isLoading = true, lastLoadError = null)
            else it.copy(isRefreshing = true, lastLoadError = null)
        }

        try {
            val tasks = tasksApi.fetchContactTasks(filters)
            if (filters.isScoped) {
                mergeTasks(tasks, replacingScopeMatching = filters)
            } else {
                replaceTasks(tasks)
                hasLoadedFullScope = true
                loadedScheduledScopes = emptyList()
            }
            _state.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    hasLoadedOnce = true,
                    lastLoadError = null
                )
            }
        } catch (error: Exception) {
            _state.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    hasLoadedOnce = true,
                    lastLoadError = error.message ?: "Failed to load tasks."
                )
            }
        }
    }

    suspend fun refreshRealtimeChanges(
        changedTaskIds: Set<String>,
        deletedTaskIds: Set<String>,
        changedMachineIds: Set<String>,
        changedLocationIds: Set<String>,
        requiresFullReload: Boolean
    ) {
        if (requiresFullReload) {
            loadTasksForWeek(force = true)
            return
        }

        removeTasks(deletedTaskIds.toList())
        val expandedTaskIds = TaskBundleHelpers.expandedRefreshTaskIds(_state.value.tasks, changedTaskIds)
        if (expandedTaskIds.isNotEmpty()) {
            refreshTasks(taskIds = expandedTaskIds)
        }
        try {
            refreshScopedTaskEntities(
                machineIds = changedMachineIds,
                locationIds = changedLocationIds
            )
            _state.update { it.copy(lastLoadError = null) }
        } catch (error: Exception) {
            _state.update { it.copy(lastLoadError = error.message ?: "Failed to refresh scoped tasks.") }
            loadTasksForWeek(force = true)
        }
        if (expandedTaskIds.isEmpty() && changedMachineIds.isEmpty() && changedLocationIds.isEmpty() && deletedTaskIds.isEmpty()) {
            loadTasksForWeek(force = true)
        }
    }

    suspend fun refreshTasks(taskIds: Set<String>) {
        val ids = taskIds.filter(String::isNotBlank).distinct()
        if (ids.isEmpty()) return
        try {
            val refreshedTasks = if (ids.size == 1) {
                listOf(tasksApi.fetchTask(ids.first()))
            } else {
                tasksApi.fetchTasks(TaskFetchFilters(taskIds = ids))
            }
            mergeTasks(refreshedTasks)
            removeTasks(ids.filter { id -> refreshedTasks.none { it.id == id } })
            _state.update { it.copy(lastLoadError = null) }
        } catch (error: Exception) {
            _state.update { it.copy(lastLoadError = error.message ?: "Failed to refresh tasks.") }
            loadTasksForWeek(force = true)
        }
    }

    private suspend fun refreshScopedTaskEntities(machineIds: Set<String>, locationIds: Set<String>) {
        machineIds.filter(String::isNotBlank).distinct().forEach { machineId ->
            val filters = TaskFetchFilters(machineId = machineId)
            mergeTasks(
                updatedTasks = tasksApi.fetchTasks(filters),
                replacingScopeMatching = filters
            )
        }
        locationIds.filter(String::isNotBlank).distinct().forEach { locationId ->
            val filters = TaskFetchFilters(locationId = locationId)
            mergeTasks(
                updatedTasks = tasksApi.fetchTasks(filters),
                replacingScopeMatching = filters
            )
        }
    }

    fun visibleTasks(user: User?): List<VendiTask> {
        val tasks = _state.value.tasks
        if (user == null) return emptyList()
        if (TaskPermissions.canViewAllScheduledTasks(user)) return tasks
        return tasks.filter { it.assignee == user.id }
    }

    fun mergeServerTasks(tasks: List<VendiTask>) {
        mergeTasks(tasks)
    }

    fun markMachineTaskStartedOptimistically(task: VendiTask, startedAt: Instant = Instant.now()) {
        mergeTask(task.copy(startedAt = task.startedAt ?: startedAt.toString()))
    }

    suspend fun updateStatus(taskId: String, status: TaskStatus): Boolean {
        return bulkUpdateStatus(listOf(taskId), status)
    }

    suspend fun startMachineTask(
        task: VendiTask,
        coordinate: LocationCoordinate,
        startedAt: Instant = Instant.now()
    ): Boolean {
        val startedAtIso = startedAt.toString()
        return mutateTaskIds(listOf(task.id), optimisticStatus = null) {
            mergeTask(task.copy(startedAt = task.startedAt ?: startedAtIso))
            tasksApi.startMachineTask(task, coordinate)
            mergeTask(tasksApi.fetchTask(task.id))
        }
    }

    suspend fun updateMachineStatus(
        task: VendiTask,
        status: TaskStatus,
        distanceMiles: Double? = null
    ): Boolean {
        return mutateTaskIds(listOf(task.id), optimisticStatus = status) {
            tasksApi.updateMachineTaskStatus(task, status, distanceMiles)
            mergeTask(tasksApi.fetchTask(task.id))
        }
    }

    suspend fun markActiveExecutionTasksDone(
        tasks: List<VendiTask>,
        distanceMilesByTaskId: Map<String, Double?>
    ): Boolean {
        val orderedTasks = tasks.distinctBy { it.id }.filter { it.id.isNotBlank() }
        return mutateTaskIds(orderedTasks.map { it.id }, optimisticStatus = null) {
            orderedTasks.forEach { task ->
                if (task.machine != null || task.type == TaskType.MachinePickupInventory) {
                    tasksApi.updateMachineTaskStatus(
                        task = task,
                        status = TaskStatus.Done,
                        distanceMiles = distanceMilesByTaskId[task.id]
                    )
                } else {
                    tasksApi.bulkUpdateStatus(listOf(task.id), TaskStatus.Done)
                }
            }
            orderedTasks.forEach { task ->
                mergeTask(tasksApi.fetchTask(task.id))
            }
        }
    }

    suspend fun completeRefillTaskWithInventory(
        taskId: String,
        lines: List<RefillInventoryCompletionLine>,
        distanceMiles: Double? = null
    ): Boolean {
        return mutateTaskIds(listOf(taskId), optimisticStatus = TaskStatus.Done) {
            mergeTask(tasksApi.completeRefillTaskWithInventory(taskId, lines, distanceMiles))
            bumpInventoryInvalidationVersion()
        }
    }

    suspend fun completePickupInventoryTask(
        taskId: String,
        lines: List<PickupInventoryCompletionLine>,
        distanceMiles: Double? = null
    ): Boolean {
        val pickedUpByLineId = lines.associate { it.lineId to it.pickedUpQuantity }
        return mutateTaskIds(listOf(taskId), optimisticStatus = null) {
            _state.value.tasksById[taskId]?.let { task ->
                mergeTask(
                    task.copy(
                        status = TaskStatus.Done,
                        distance = distanceMiles ?: task.distance,
                        pickupLines = task.pickupLines.map { line ->
                            pickedUpByLineId[line.id]?.let { quantity ->
                                line.copy(pickedUpQuantity = quantity)
                            } ?: line
                        }
                    )
                )
            }
            mergeTask(tasksApi.completePickupInventoryTask(taskId, lines, distanceMiles))
            bumpInventoryInvalidationVersion()
        }
    }

    suspend fun uploadPhotoConfirmation(
        taskId: String,
        fileName: String,
        mimeType: String,
        fileData: ByteArray
    ): Boolean {
        return mutateTaskIds(listOf(taskId), optimisticStatus = null) {
            tasksApi.uploadTaskPhotoConfirmation(taskId, fileName, mimeType, fileData)
            mergeTask(tasksApi.fetchTask(taskId))
        }
    }

    suspend fun removePhotoConfirmation(taskId: String): Boolean {
        val assetId = _state.value.tasksById[taskId]?.photoConfirmationAsset?.id ?: return true
        return mutateTaskIds(listOf(taskId), optimisticStatus = null) {
            tasksApi.removeTaskPhotoConfirmation(assetId)
            mergeTask(tasksApi.fetchTask(taskId))
        }
    }

    suspend fun setRefillInventorySource(
        taskId: String,
        warehouseId: String?,
        sourceMode: RefillInventorySourceMode
    ): Boolean {
        return mutateTaskIds(listOf(taskId), optimisticStatus = null) {
            mergeTask(tasksApi.setRefillInventorySource(taskId, warehouseId, sourceMode))
            bumpInventoryInvalidationVersion()
        }
    }

    suspend fun createPickupInventoryTaskForRefill(taskId: String, warehouseId: String): VendiTask? {
        var pickupTask: VendiTask? = null
        val didCreate = mutateTaskIds(listOf(taskId), optimisticStatus = null) {
            pickupTask = tasksApi.createPickupInventoryTaskForRefill(taskId, warehouseId)
            pickupTask?.let(::upsertTask)
            bumpInventoryInvalidationVersion()
        }
        return pickupTask.takeIf { didCreate }
    }

    suspend fun createPickupInventoryTaskForRefills(taskIds: List<String>, warehouseId: String): VendiTask? {
        var pickupTask: VendiTask? = null
        val didCreate = mutateTaskIds(taskIds, optimisticStatus = null) {
            pickupTask = tasksApi.createPickupInventoryTaskForRefills(taskIds, warehouseId)
            pickupTask?.let(::upsertTask)
            bumpInventoryInvalidationVersion()
        }
        return pickupTask.takeIf { didCreate }
    }

    suspend fun fetchAggregateRefillInventorySuggestion(taskIds: List<String>): AggregateRefillInventorySuggestion {
        return tasksApi.fetchAggregateRefillInventorySuggestion(taskIds)
    }

    suspend fun loadRefillInventoryContext(
        taskIds: List<String>,
        warehouseId: String? = null
    ): RefillInventoryContext {
        val context = tasksApi.fetchRefillInventoryContext(taskIds, warehouseId)
        mergeTasks(context.tasks)
        return context
    }

    suspend fun bulkUpdateStatus(taskIds: List<String>, status: TaskStatus): Boolean {
        return mutateTaskIds(taskIds, optimisticStatus = status) {
            mergeTasks(tasksApi.bulkUpdateStatus(taskIds, status))
        }
    }

    suspend fun claimTasks(taskIds: List<String>) {
        mutateTaskIds(taskIds, optimisticStatus = TaskStatus.Pending) {
            taskIds.distinct().filter { it.isNotBlank() }.forEach { taskId ->
                tasksApi.claimTask(taskId)
            }
            loadTasksForWeek(force = true)
        }
    }

    suspend fun cancelTasks(taskIds: List<String>): Boolean {
        return mutateTaskIds(taskIds, optimisticStatus = TaskStatus.Cancelled) {
            mergeTasks(tasksApi.bulkCancel(taskIds))
        }
    }

    suspend fun deleteTasks(taskIds: List<String>) {
        mutateTaskIds(taskIds, optimisticStatus = null) {
            tasksApi.bulkDelete(taskIds)
            removeTasks(taskIds)
        }
    }

    suspend fun assignTasks(taskIds: List<String>, assigneeId: String?) {
        mutateTaskIds(taskIds, optimisticStatus = null) {
            mergeTasks(tasksApi.bulkAssign(taskIds, assigneeId))
            mergeTasks(
                tasksApi.bulkUpdateStatus(
                    taskIds = taskIds,
                    status = if (assigneeId == null) TaskStatus.Unassigned else TaskStatus.Pending
                )
            )
        }
    }

    suspend fun applySharedNotes(taskIds: List<String>, notes: String?, trackTaskLoading: Boolean = true): Boolean {
        val normalizedNotes = SharedTaskNotes.normalizedValue(notes)
        return mutateTaskIds(taskIds, optimisticStatus = null, trackLoading = trackTaskLoading) {
            taskIds.distinct().filter { it.isNotBlank() }.forEach { taskId ->
                _state.value.tasksById[taskId]?.let { mergeTask(it.copy(notes = normalizedNotes)) }
            }
            mergeTasks(tasksApi.bulkUpdateTaskNotes(taskIds, normalizedNotes))
        }
    }

    suspend fun updateCollectionFinancials(
        task: VendiTask,
        gross: Double?,
        grossCash: Double?,
        grossCard: Double?,
        refunds: Double?,
        commission: Double?,
        commissionPaymentType: CommissionPaymentType?,
        net: Double?,
        includeRefundsInCommission: Boolean?
    ): Boolean {
        return mutateTaskIds(listOf(task.id), optimisticStatus = null) {
            mergeTask(
                task.copy(
                    gross = gross,
                    grossCash = grossCash,
                    grossCard = grossCard,
                    refunds = refunds,
                    commission = commission,
                    commissionPaymentType = commissionPaymentType,
                    net = net,
                    includeRefundsInCommission = includeRefundsInCommission
                )
            )
            tasksApi.updateCollectionFinancials(
                taskId = task.id,
                gross = gross,
                grossCash = grossCash,
                grossCard = grossCard,
                refunds = refunds,
                commission = commission,
                commissionPaymentType = commissionPaymentType,
                net = net,
                includeRefundsInCommission = includeRefundsInCommission
            )
            refreshTasks(setOf(task.id))
        }
    }

    suspend fun updateRefundFinancials(task: VendiTask, refunds: Double?): Boolean {
        return mutateTaskIds(listOf(task.id), optimisticStatus = null) {
            mergeTask(task.copy(refunds = refunds))
            tasksApi.updateRefundFinancials(task.id, refunds)
            refreshTasks(setOf(task.id))
        }
    }

    suspend fun rescheduleTasks(taskIds: List<String>, scheduledFor: String, assigneeId: String? = null) {
        mutateTaskIds(taskIds, optimisticStatus = null) {
            mergeTasks(tasksApi.bulkReschedule(taskIds, scheduledFor, assigneeId))
        }
    }

    private fun mergeTask(task: VendiTask) {
        _state.update { state ->
            val existingTask = state.tasks.firstOrNull { it.id == task.id }
            val nextTask = task.withPreservedLocalTaskState(existingTask)
            val hasExistingTask = existingTask != null
            val nextTasks = if (hasExistingTask) {
                state.tasks.map { if (it.id == task.id) nextTask else it }
            } else {
                listOf(nextTask) + state.tasks
            }
            state.copy(
                tasks = nextTasks,
                tasksById = nextTasks.associateBy { it.id },
                summary = TaskSummary.fromTasks(nextTasks)
            )
        }
    }

    private fun mergeTasks(updatedTasks: List<VendiTask>) {
        updatedTasks.forEach(::mergeTask)
    }

    private fun replaceTasks(tasks: List<VendiTask>) {
        _state.update { state ->
            val existingTasksById = state.tasksById
            val nextTasks = tasks.map { task -> task.withPreservedLocalTaskState(existingTasksById[task.id]) }
            state.copy(
                tasks = nextTasks,
                tasksById = nextTasks.associateBy { task -> task.id },
                summary = TaskSummary.fromTasks(nextTasks)
            )
        }
    }

    private fun mergeTasks(updatedTasks: List<VendiTask>, replacingScopeMatching: TaskFetchFilters) {
        _state.update { state ->
            val updatedIds = updatedTasks.map { it.id }.toSet()
            val retainedTasks = state.tasks.filter { task ->
                task.id !in updatedIds && !task.matches(replacingScopeMatching)
            }
            val nextTasks = updatedTasks.map { task ->
                task.withPreservedLocalTaskState(state.tasksById[task.id])
            } + retainedTasks
            state.copy(
                tasks = nextTasks,
                tasksById = nextTasks.associateBy { it.id },
                summary = TaskSummary.fromTasks(nextTasks)
            )
        }
    }

    private fun upsertTask(task: VendiTask) {
        _state.update { state ->
            val existingIndex = state.tasks.indexOfFirst { it.id == task.id }
            val existingTask = state.tasks.getOrNull(existingIndex)
            val nextTask = task.withPreservedLocalTaskState(existingTask)
            val nextTasks = if (existingIndex >= 0) {
                state.tasks.map { if (it.id == task.id) nextTask else it }
            } else {
                listOf(nextTask) + state.tasks
            }
            state.copy(
                tasks = nextTasks,
                tasksById = nextTasks.associateBy { it.id },
                summary = TaskSummary.fromTasks(nextTasks)
            )
        }
    }

    private fun bumpInventoryInvalidationVersion() {
        _state.update { state ->
            state.copy(inventoryInvalidationVersion = state.inventoryInvalidationVersion + 1)
        }
    }

    fun removeTasks(taskIds: List<String>) {
        val ids = taskIds.toSet()
        if (ids.isEmpty()) return
        _state.update { state ->
            val nextTasks = state.tasks.filterNot { ids.contains(it.id) }
            state.copy(
                tasks = nextTasks,
                tasksById = nextTasks.associateBy { it.id },
                summary = TaskSummary.fromTasks(nextTasks)
            )
        }
    }

    private fun VendiTask.matches(filters: TaskFetchFilters): Boolean {
        filters.machineId?.let { if (machine != it) return false }
        filters.locationId?.let { if (location != it) return false }
        filters.taskIds.takeIf { it.isNotEmpty() }?.let { if (id !in it) return false }
        filters.refillTaskIds.takeIf { it.isNotEmpty() }?.let { refillIds ->
            val linkedRefillIds = refillTaskIds + listOfNotNull(refillTaskId) + pickupLines.mapNotNull { it.refillTaskId }
            if (id !in refillIds && linkedRefillIds.none { it in refillIds }) return false
        }
        filters.statuses.takeIf { it.isNotEmpty() }?.let { if (status !in it) return false }
        filters.scheduledFrom?.let { if (scheduledFor < it) return false }
        filters.scheduledTo?.let { if (scheduledFor > it) return false }
        return true
    }

    private fun isScheduledScopeLoaded(filters: TaskFetchFilters): Boolean {
        if (!filters.isScoped) return hasLoadedFullScope
        val from = filters.scheduledFrom ?: return false
        val to = filters.scheduledTo ?: return false
        return loadedScheduledScopes.any { scope ->
            scope.fromKey <= from && scope.toKey >= to
        }
    }

    private fun markScheduledScopeLoaded(filters: TaskFetchFilters) {
        if (!filters.isScoped) {
            loadedScheduledScopes = emptyList()
            hasLoadedFullScope = true
            return
        }
        val from = filters.scheduledFrom ?: return
        val to = filters.scheduledTo ?: return
        loadedScheduledScopes = (loadedScheduledScopes + LoadedScheduledTaskScope(from, to))
            .distinct()
            .sortedBy { it.fromKey }
    }

    private suspend fun mutateTaskIds(
        taskIds: List<String>,
        optimisticStatus: TaskStatus?,
        trackLoading: Boolean = true,
        mutation: suspend () -> Unit
    ): Boolean {
        val ids = taskIds.distinct().filter { it.isNotBlank() }
        if (ids.isEmpty()) return true
        if (trackLoading) {
            _state.update { it.copy(pendingMutationTaskIds = it.pendingMutationTaskIds + ids) }
        }
        if (optimisticStatus != null) {
            ids.forEach { taskId ->
                _state.value.tasksById[taskId]?.let { mergeTask(it.copy(status = optimisticStatus)) }
            }
        }
        try {
            mutation()
            _state.update { it.copy(lastMutationError = null) }
            return true
        } catch (error: Exception) {
            _state.update { it.copy(lastMutationError = error.message ?: "Failed to update tasks.") }
            loadTasksForWeek(force = true)
            return false
        } finally {
            if (trackLoading) {
                _state.update { it.copy(pendingMutationTaskIds = it.pendingMutationTaskIds - ids.toSet()) }
            }
        }
    }
}

private fun VendiTask.withPreservedLocalTaskState(existingTask: VendiTask?): VendiTask {
    if (existingTask == null) return this
    val preservedStartedAt = when {
        startedAt != null -> startedAt
        existingTask.startedAt != null && !TaskStateHelpers.isFinal(status) -> existingTask.startedAt
        else -> startedAt
    }
    val preservedDuration = duration.takeIf { (it ?: 0.0) > 0.0 } ?: existingTask.duration.takeIf { (it ?: 0.0) > 0.0 }
    val preservedDistance = distance.takeIf { (it ?: 0.0) > 0.0 } ?: existingTask.distance.takeIf { (it ?: 0.0) > 0.0 }
    if (preservedStartedAt == startedAt && preservedDuration == duration && preservedDistance == distance) return this
    return copy(
        startedAt = preservedStartedAt,
        duration = preservedDuration,
        distance = preservedDistance
    )
}

data class TasksUiState(
    val tasks: List<VendiTask> = emptyList(),
    val tasksById: Map<String, VendiTask> = emptyMap(),
    val summary: TaskSummary = TaskSummary(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasLoadedOnce: Boolean = false,
    val lastLoadError: String? = null,
    val lastMutationError: String? = null,
    val pendingMutationTaskIds: Set<String> = emptySet(),
    val inventoryInvalidationVersion: Int = 0
)
