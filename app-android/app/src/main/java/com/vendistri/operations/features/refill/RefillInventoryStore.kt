package com.vendistri.operations.features.refill

import com.vendistri.operations.features.tasks.TasksApi
import com.vendistri.operations.features.tasks.RefillInventorySourceMode
import com.vendistri.operations.features.tasks.VendiTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class RefillInventoryStore(
    private val tasksApi: TasksApi
) {
    /** Refreshes server-owned stock context without destroying quantities already typed by the user. */
    suspend fun refreshOpenWarehouseAvailability(tasksById: Map<String, VendiTask>, allTasks: List<VendiTask>) {
        val standalone = _state.value
        standalone.taskId?.let { taskId ->
            val task = tasksById[taskId]
            if (task != null && standalone.lines.isNotEmpty() && standalone.sourceMode == RefillInventorySourceMode.Warehouse) {
                refreshAvailability(task, standalone, allTasks) { _state.value = it }
            }
        }
        _taskStates.value.toMap().forEach { (taskId, editorState) ->
            val task = tasksById[taskId]
            if (task != null && editorState.lines.isNotEmpty() && editorState.sourceMode == RefillInventorySourceMode.Warehouse) {
                refreshAvailability(task, editorState, allTasks) { refreshed -> setTaskState(taskId, refreshed) }
            }
        }
    }

    private suspend fun refreshAvailability(
        task: VendiTask,
        editorState: RefillInventoryUiState,
        allTasks: List<VendiTask>,
        publish: (RefillInventoryUiState) -> Unit
    ) {
        try {
            val warehouseId = editorState.selectedWarehouseId ?: task.inventorySourceWarehouseId
            val suggestions = tasksApi.fetchRefillInventorySuggestions(task.id, warehouseId)
            val draftsByItemId = editorState.lines.associateBy { it.itemId }
            val refreshedLines = suggestions.items.map { item ->
                val draft = draftsByItemId[item.itemId]
                RefillInventoryLine(
                    itemId = item.itemId,
                    product = item.product,
                    currentStock = item.currentStock,
                    capacity = item.capacity,
                    suggestedRefill = item.suggestedRefill,
                    pickedUpQuantity = item.pickedUpQuantity,
                    warehouseAvailableStock = item.warehouseAvailableStock,
                    refilledText = draft?.refilledText ?: item.pickedUpQuantity?.toString().orEmpty(),
                    finalStockText = draft?.finalStockText ?: (item.currentStock + (item.pickedUpQuantity ?: 0)).toString()
                )
            }
            publish(editorState.copy(
                selectedWarehouseId = warehouseId ?: suggestions.warehouseId,
                selectedWarehouseName = editorState.selectedWarehouseName
                    ?: task.inventorySourceWarehouseName
                    ?: suggestions.warehouseName,
                lines = refreshedLines,
                errorMessage = null,
                invalidRefilledItemIds = emptySet(),
                invalidFinalStockItemIds = emptySet(),
                pickupSourceSummary = RefillInventorySourceSummaryResolver.pickupSourceSummary(
                    task.id, refreshedLines, allTasks, suggestions.warehouseName ?: task.inventorySourceWarehouseName
                )
            ))
        } catch (error: Exception) {
            publish(editorState.copy(errorMessage = error.message ?: "Could not refresh warehouse stock."))
        }
    }

    private val _state = MutableStateFlow(RefillInventoryUiState())
    val state: StateFlow<RefillInventoryUiState> = _state.asStateFlow()
    private val _taskStates = MutableStateFlow<Map<String, RefillInventoryUiState>>(emptyMap())
    val taskStates: StateFlow<Map<String, RefillInventoryUiState>> = _taskStates.asStateFlow()

    fun reset() {
        _state.value = RefillInventoryUiState()
    }

    fun resetTask(taskId: String) {
        _taskStates.update { states -> states - taskId }
    }

    suspend fun prepare(task: VendiTask, allTasks: List<VendiTask> = emptyList(), force: Boolean = false) {
        if (!force && _state.value.taskId == task.id && _state.value.lines.isNotEmpty()) return
        val sourceMode = task.inventorySourceMode ?: RefillInventorySourceMode.Warehouse
        val previousState = _state.value.takeIf { it.taskId == task.id }
        _state.value = if (force && previousState?.lines?.isNotEmpty() == true) {
            previousState.copy(
                sourceMode = sourceMode,
                selectedWarehouseId = task.inventorySourceWarehouseId,
                selectedWarehouseName = task.inventorySourceWarehouseName,
                isLoading = false,
                errorMessage = null
            )
        } else {
            RefillInventoryUiState(
                taskId = task.id,
                sourceMode = sourceMode,
                selectedWarehouseId = task.inventorySourceWarehouseId,
                selectedWarehouseName = task.inventorySourceWarehouseName,
                isLoading = true
            )
        }
        try {
            val suggestions = tasksApi.fetchRefillInventorySuggestions(
                taskId = task.id,
                warehouseId = task.inventorySourceWarehouseId.takeIf { sourceMode == RefillInventorySourceMode.Warehouse }
            )
            val selectedWarehouseId = task.inventorySourceWarehouseId ?: suggestions.warehouseId
            val selectedWarehouseName = task.inventorySourceWarehouseName ?: suggestions.warehouseName
            val lines = suggestions.items.map { item ->
                val refilled = item.pickedUpQuantity
                RefillInventoryLine(
                    itemId = item.itemId,
                    product = item.product,
                    currentStock = item.currentStock,
                    capacity = item.capacity,
                    suggestedRefill = item.suggestedRefill,
                    pickedUpQuantity = item.pickedUpQuantity,
                    warehouseAvailableStock = item.warehouseAvailableStock,
                    refilledText = refilled?.toString().orEmpty(),
                    finalStockText = (item.currentStock + (refilled ?: 0)).toString()
                )
            }
            _state.value = RefillInventoryUiState(
                taskId = task.id,
                sourceMode = sourceMode,
                selectedWarehouseId = selectedWarehouseId,
                selectedWarehouseName = selectedWarehouseName,
                lines = lines,
                isSavingSource = previousState?.isSavingSource == true,
                pickupSourceSummary = RefillInventorySourceSummaryResolver.pickupSourceSummary(
                    refillTaskId = task.id,
                    refillLines = lines,
                    allTasks = allTasks,
                    fallbackWarehouseName = suggestions.warehouseName ?: task.inventorySourceWarehouseName
                )
            )
        } catch (error: Exception) {
            _state.value = previousState?.takeIf { force && it.lines.isNotEmpty() }?.copy(
                sourceMode = sourceMode,
                selectedWarehouseId = task.inventorySourceWarehouseId,
                selectedWarehouseName = task.inventorySourceWarehouseName,
                isLoading = false,
                errorMessage = error.message ?: "Could not refresh refill inventory."
            ) ?: RefillInventoryUiState(
                taskId = task.id,
                sourceMode = sourceMode,
                selectedWarehouseId = task.inventorySourceWarehouseId,
                selectedWarehouseName = task.inventorySourceWarehouseName,
                errorMessage = error.message ?: "Could not load refill inventory."
            )
        }
    }

    suspend fun prepareTask(task: VendiTask, allTasks: List<VendiTask> = emptyList(), force: Boolean = false) {
        if (!force && _taskStates.value[task.id]?.lines?.isNotEmpty() == true) return
        val sourceMode = task.inventorySourceMode ?: RefillInventorySourceMode.Warehouse
        val previousState = _taskStates.value[task.id]
        val loadingState = if (force && previousState?.lines?.isNotEmpty() == true) {
            previousState.copy(
                sourceMode = sourceMode,
                selectedWarehouseId = task.inventorySourceWarehouseId,
                selectedWarehouseName = task.inventorySourceWarehouseName,
                isLoading = false,
                errorMessage = null
            )
        } else {
            RefillInventoryUiState(
                taskId = task.id,
                sourceMode = sourceMode,
                selectedWarehouseId = task.inventorySourceWarehouseId,
                selectedWarehouseName = task.inventorySourceWarehouseName,
                isLoading = true
            )
        }
        setTaskState(task.id, loadingState)
        try {
            val suggestions = tasksApi.fetchRefillInventorySuggestions(
                taskId = task.id,
                warehouseId = task.inventorySourceWarehouseId.takeIf { sourceMode == RefillInventorySourceMode.Warehouse }
            )
            val selectedWarehouseId = task.inventorySourceWarehouseId ?: suggestions.warehouseId
            val selectedWarehouseName = task.inventorySourceWarehouseName ?: suggestions.warehouseName
            val lines = suggestions.items.map { item ->
                val refilled = item.pickedUpQuantity
                RefillInventoryLine(
                    itemId = item.itemId,
                    product = item.product,
                    currentStock = item.currentStock,
                    capacity = item.capacity,
                    suggestedRefill = item.suggestedRefill,
                    pickedUpQuantity = item.pickedUpQuantity,
                    warehouseAvailableStock = item.warehouseAvailableStock,
                    refilledText = refilled?.toString().orEmpty(),
                    finalStockText = (item.currentStock + (refilled ?: 0)).toString()
                )
            }
            setTaskState(
                task.id,
                RefillInventoryUiState(
                    taskId = task.id,
                    sourceMode = sourceMode,
                    selectedWarehouseId = selectedWarehouseId,
                    selectedWarehouseName = selectedWarehouseName,
                    lines = lines,
                    isSavingSource = previousState?.isSavingSource == true,
                    pickupSourceSummary = RefillInventorySourceSummaryResolver.pickupSourceSummary(
                        refillTaskId = task.id,
                        refillLines = lines,
                        allTasks = allTasks,
                        fallbackWarehouseName = suggestions.warehouseName ?: task.inventorySourceWarehouseName
                    )
                )
            )
        } catch (error: Exception) {
            setTaskState(
                task.id,
                previousState?.takeIf { force && it.lines.isNotEmpty() }?.copy(
                    sourceMode = sourceMode,
                    selectedWarehouseId = task.inventorySourceWarehouseId,
                    selectedWarehouseName = task.inventorySourceWarehouseName,
                    isLoading = false,
                    errorMessage = error.message ?: "Could not refresh refill inventory."
                ) ?: RefillInventoryUiState(
                    taskId = task.id,
                    sourceMode = sourceMode,
                    selectedWarehouseId = task.inventorySourceWarehouseId,
                    selectedWarehouseName = task.inventorySourceWarehouseName,
                    errorMessage = error.message ?: "Could not load refill inventory."
                )
            )
        }
    }

    fun setSavingSource(isSavingSource: Boolean) {
        _state.update { it.copy(isSavingSource = isSavingSource) }
    }

    fun updateRefilledQuantity(itemId: String, value: String, autoFillFinalStock: Boolean) {
        _state.update { state ->
            state.copy(
                errorMessage = null,
                invalidRefilledItemIds = state.invalidRefilledItemIds - itemId,
                lines = state.lines.map { line ->
                    if (line.itemId != itemId) return@map line
                    val refilledQuantity = value.toIntOrNull()
                    val finalStockText = if (autoFillFinalStock && refilledQuantity != null && refilledQuantity >= 0) {
                        (line.currentStock + refilledQuantity).toString()
                    } else {
                        line.finalStockText
                    }
                    line.copy(refilledText = value.digitsOnly(), finalStockText = finalStockText)
                }
            )
        }
    }

    fun updateTaskRefilledQuantity(taskId: String, itemId: String, value: String, autoFillFinalStock: Boolean) {
        updateTaskState(taskId) { state ->
            state.copy(
                errorMessage = null,
                invalidRefilledItemIds = state.invalidRefilledItemIds - itemId,
                lines = state.lines.map { line ->
                    if (line.itemId != itemId) return@map line
                    val refilledQuantity = value.toIntOrNull()
                    val finalStockText = if (autoFillFinalStock && refilledQuantity != null && refilledQuantity >= 0) {
                        (line.currentStock + refilledQuantity).toString()
                    } else {
                        line.finalStockText
                    }
                    line.copy(refilledText = value.digitsOnly(), finalStockText = finalStockText)
                }
            )
        }
    }

    fun updateFinalStock(itemId: String, value: String) {
        _state.update { state ->
            state.copy(
                errorMessage = null,
                invalidFinalStockItemIds = state.invalidFinalStockItemIds - itemId,
                lines = state.lines.map { line ->
                    if (line.itemId == itemId) line.copy(finalStockText = value.digitsOnly()) else line
                }
            )
        }
    }

    fun updateTaskFinalStock(taskId: String, itemId: String, value: String) {
        updateTaskState(taskId) { state ->
            state.copy(
                errorMessage = null,
                invalidFinalStockItemIds = state.invalidFinalStockItemIds - itemId,
                lines = state.lines.map { line ->
                    if (line.itemId == itemId) line.copy(finalStockText = value.digitsOnly()) else line
                }
            )
        }
    }

    fun setCompleting(isCompleting: Boolean) {
        _state.update { it.copy(isCompleting = isCompleting) }
    }

    fun setTaskCompleting(taskId: String, isCompleting: Boolean) {
        updateTaskState(taskId) { it.copy(isCompleting = isCompleting) }
    }

    fun setError(message: String?) {
        _state.update { it.copy(errorMessage = message) }
    }

    fun setTaskError(taskId: String, message: String?) {
        updateTaskState(taskId) { it.copy(errorMessage = message) }
    }

    fun validatedCompletionLines(): List<RefillInventoryCompletionLine>? {
        return validateCompletionLines(state = _state.value, onError = ::setError)
    }

    fun validatedTaskCompletionLines(taskId: String): List<RefillInventoryCompletionLine>? {
        val state = _taskStates.value[taskId] ?: return null
        return validateCompletionLines(
            state = state,
            onError = { message -> setTaskError(taskId, message) }
        )
    }

    fun setTaskSavingSource(taskId: String, isSavingSource: Boolean) {
        updateTaskState(taskId) { it.copy(isSavingSource = isSavingSource) }
    }

    private fun setTaskState(taskId: String, state: RefillInventoryUiState) {
        _taskStates.update { states -> states + (taskId to state) }
    }

    private fun updateTaskState(taskId: String, transform: (RefillInventoryUiState) -> RefillInventoryUiState) {
        _taskStates.update { states ->
            val current = states[taskId] ?: return@update states
            states + (taskId to transform(current))
        }
    }

    private fun validateCompletionLines(
        state: RefillInventoryUiState,
        onError: (String?) -> Unit
    ): List<RefillInventoryCompletionLine>? {
        fun validationError(
            message: String,
            refilledIds: Set<String> = emptySet(),
            finalStockIds: Set<String> = emptySet()
        ): List<RefillInventoryCompletionLine>? {
            val updated = state.copy(
                errorMessage = message,
                invalidRefilledItemIds = refilledIds,
                invalidFinalStockItemIds = finalStockIds
            )
            if (state.taskId != null && _taskStates.value.containsKey(state.taskId)) {
                setTaskState(state.taskId, updated)
            } else {
                _state.value = updated
            }
            return null
        }
        if (state.isLoading) {
            return validationError("Inventory is still loading.")
        }
        if (state.sourceMode == RefillInventorySourceMode.Warehouse && state.selectedWarehouseId.isNullOrBlank()) {
            return validationError("Select a warehouse before completing this refill.")
        }
        if (state.errorMessage != null && state.lines.isEmpty()) return null
        val lines = mutableListOf<RefillInventoryCompletionLine>()
        for (line in state.lines) {
            val refilledQuantity = line.refilledText.toIntOrNull()
            val finalStock = line.finalStockText.toIntOrNull()
            if (refilledQuantity == null || finalStock == null) {
                return validationError(
                    message = "Enter refilled and final stock for each item.",
                    refilledIds = state.lines.filter { it.refilledText.toIntOrNull() == null }.map { it.itemId }.toSet(),
                    finalStockIds = state.lines.filter { it.finalStockText.toIntOrNull() == null }.map { it.itemId }.toSet()
                )
            }
            if (refilledQuantity < 0) {
                return validationError("Refilled quantity cannot be negative.", refilledIds = setOf(line.itemId))
            }
            if (finalStock < 0 || finalStock > line.capacity) {
                return validationError("Final stock must be between 0 and ${line.capacity}.", finalStockIds = setOf(line.itemId))
            }
            line.warehouseAvailableStock?.takeIf { state.sourceMode == RefillInventorySourceMode.Warehouse }?.let { available ->
                val warehouseQuantity = maxOf(refilledQuantity - (line.pickedUpQuantity ?: 0), 0)
                if (warehouseQuantity > available) {
                    return validationError("Refilled quantity exceeds available warehouse stock.", refilledIds = setOf(line.itemId))
                }
            }
            lines += RefillInventoryCompletionLine(
                itemId = line.itemId,
                refilledQuantity = refilledQuantity,
                finalStock = finalStock
            )
        }
        val cleared = state.copy(errorMessage = null, invalidRefilledItemIds = emptySet(), invalidFinalStockItemIds = emptySet())
        if (state.taskId != null && _taskStates.value.containsKey(state.taskId)) setTaskState(state.taskId, cleared) else _state.value = cleared
        onError(null)
        return lines
    }
}

private fun String.digitsOnly(): String = filter(Char::isDigit)
