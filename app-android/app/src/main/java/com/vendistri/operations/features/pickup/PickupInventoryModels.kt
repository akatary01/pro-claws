package com.vendistri.operations.features.pickup

import com.vendistri.operations.features.tasks.TaskPickupLine

data class PickupInventoryLine(
    val lineId: String,
    val source: TaskPickupLine,
    val pickedUpText: String
)

data class PickupInventoryUiState(
    val taskId: String? = null,
    val lines: List<PickupInventoryLine> = emptyList(),
    val isLoading: Boolean = false,
    val isCompleting: Boolean = false,
    val errorMessage: String? = null,
    val invalidLineIds: Set<String> = emptySet()
)

data class PickupInventoryCompletionLine(
    val lineId: String,
    val pickedUpQuantity: Int
)
