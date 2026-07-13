package com.vendistri.operations.features.pickup

import com.vendistri.operations.features.tasks.VendiTask
import com.vendistri.operations.features.refill.WarehouseStockValidation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PickupInventoryStore {
    private val _state = MutableStateFlow(PickupInventoryUiState())
    val state: StateFlow<PickupInventoryUiState> = _state.asStateFlow()

    fun reset() {
        _state.value = PickupInventoryUiState()
    }

    fun prepare(task: VendiTask) {
        if (_state.value.taskId == task.id && _state.value.lines.isNotEmpty()) return
        _state.value = PickupInventoryUiState(
            taskId = task.id,
            lines = task.pickupLines.map { line ->
                PickupInventoryLine(
                    lineId = line.id,
                    source = line,
                    pickedUpText = line.pickedUpQuantity?.toString()
                        ?: line.suggestedQuantity.takeIf { it > 0 }?.toString().orEmpty()
                )
            }
        )
    }

    fun refreshAvailability(task: VendiTask) {
        val state = _state.value
        if (state.taskId != task.id || state.lines.isEmpty()) return
        val draftsByLineId = state.lines.associateBy { it.lineId }
        _state.value = state.copy(
            lines = task.pickupLines.map { source ->
                PickupInventoryLine(
                    lineId = source.id,
                    source = source,
                    pickedUpText = draftsByLineId[source.id]?.pickedUpText
                        ?: source.pickedUpQuantity?.toString()
                        ?: source.suggestedQuantity.takeIf { it > 0 }?.toString().orEmpty()
                )
            },
            errorMessage = null,
            invalidLineIds = emptySet()
        )
    }

    fun updatePickedUpQuantity(lineId: String, value: String) {
        _state.update { state ->
            state.copy(
                errorMessage = null,
                invalidLineIds = state.invalidLineIds - lineId,
                lines = state.lines.map { line ->
                    if (line.lineId == lineId) line.copy(pickedUpText = value.digitsOnly()) else line
                }
            )
        }
    }

    fun setCompleting(isCompleting: Boolean) {
        _state.update { it.copy(isCompleting = isCompleting) }
    }

    fun setError(message: String?) {
        _state.update { it.copy(errorMessage = message) }
    }

    fun validatedCompletionLines(): List<PickupInventoryCompletionLine>? {
        val pickedUpByProductId = mutableMapOf<String, Int>()
        val availableByProductId = mutableMapOf<String, Int>()
        val lines = mutableListOf<PickupInventoryCompletionLine>()
        for (line in _state.value.lines) {
            val pickedUpQuantity = line.pickedUpText.toIntOrNull()
            if (pickedUpQuantity == null) {
                _state.update { it.copy(errorMessage = "Enter a picked up quantity for each item.", invalidLineIds = setOf(line.lineId)) }
                return null
            }
            if (pickedUpQuantity < 0) {
                _state.update { it.copy(errorMessage = "Picked up quantity cannot be negative.", invalidLineIds = setOf(line.lineId)) }
                return null
            }
            val productId = line.source.product.id
            pickedUpByProductId[productId] = (pickedUpByProductId[productId] ?: 0) + pickedUpQuantity
            line.source.warehouseAvailableStock?.let { available ->
                availableByProductId[productId] = maxOf(availableByProductId[productId] ?: 0, available)
            }
            lines += PickupInventoryCompletionLine(
                lineId = line.lineId,
                pickedUpQuantity = pickedUpQuantity
            )
        }
        pickedUpByProductId.forEach { (productId, pickedUpQuantity) ->
            val available = availableByProductId[productId] ?: return@forEach
            if (pickedUpQuantity > available) {
                _state.update { state -> state.copy(
                    errorMessage = WarehouseStockValidation.overageMessage(quantityLabel = "Picked up", available = available),
                    invalidLineIds = state.lines.filter { it.source.product.id == productId }.map { it.lineId }.toSet()
                ) }
                return null
            }
        }
        _state.update { it.copy(errorMessage = null, invalidLineIds = emptySet()) }
        return lines
    }
}

private fun String.digitsOnly(): String = filter(Char::isDigit)
