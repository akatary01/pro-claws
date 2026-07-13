package com.vendistri.operations.features.tasks

object TaskPickupLineFormatters {
    fun stockText(line: TaskPickupLine): String {
        return InventoryStockFormatters.stockText(
            currentStock = line.currentStock,
            capacity = line.capacity,
            warehouseAvailableStock = line.warehouseAvailableStock
        )
    }

    fun suggestedText(line: TaskPickupLine): String {
        return "Suggested ${signedQuantity(line.suggestedQuantity)}"
    }

    fun stockAndSuggestedText(line: TaskPickupLine): String {
        return listOf(stockText(line), suggestedText(line))
            .filter { it.isNotBlank() }
            .joinToString(" • ")
    }
}
