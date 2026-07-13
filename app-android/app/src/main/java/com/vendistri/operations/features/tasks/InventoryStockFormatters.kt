package com.vendistri.operations.features.tasks

object InventoryStockFormatters {
    fun currentCapacityText(currentStock: Int?, capacity: Int?): String? {
        if (currentStock == null && capacity == null) return null
        return "Current ${currentStock ?: 0} / Capacity ${capacity ?: 0}"
    }

    fun warehouseText(warehouseAvailableStock: Int?): String? {
        return warehouseAvailableStock?.let { "Warehouse $it" }
    }

    fun stockText(
        currentStock: Int?,
        capacity: Int?,
        warehouseAvailableStock: Int? = null
    ): String {
        return listOfNotNull(
            currentCapacityText(currentStock, capacity),
            warehouseText(warehouseAvailableStock)
        )
            .filter { it.isNotBlank() }
            .joinToString(" • ")
    }
}
