package com.vendistri.operations.features.tasks

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskPickupLineFormattersTest {
    @Test
    fun stockFormatterUsesExplicitCurrentCapacityLabels() {
        assertEquals(
            "Current 18 / Capacity 200",
            InventoryStockFormatters.stockText(currentStock = 18, capacity = 200)
        )
    }

    @Test
    fun stockTextUsesExplicitCurrentCapacityAndWarehouseLabels() {
        val line = pickupLine(currentStock = 18, capacity = 200, warehouseAvailableStock = 9)

        assertEquals("Current 18 / Capacity 200 • Warehouse 9", TaskPickupLineFormatters.stockText(line))
    }

    @Test
    fun stockAndSuggestedTextUsesSharedStockFormat() {
        val line = pickupLine(currentStock = 18, capacity = 200, warehouseAvailableStock = 9, suggestedQuantity = 50)

        assertEquals("Current 18 / Capacity 200 • Warehouse 9 • Suggested +50", TaskPickupLineFormatters.stockAndSuggestedText(line))
    }

    private fun pickupLine(
        currentStock: Int?,
        capacity: Int?,
        warehouseAvailableStock: Int?,
        suggestedQuantity: Int = 0
    ): TaskPickupLine {
        return TaskPickupLine(
            id = "line-1",
            refillTaskId = "refill-1",
            machineName = "Machine",
            product = TaskInventoryProduct(id = "product-1", name = "Plush", brand = null, code = null, size = null),
            currentStock = currentStock,
            capacity = capacity,
            suggestedQuantity = suggestedQuantity,
            warehouseAvailableStock = warehouseAvailableStock,
            pickedUpQuantity = null
        )
    }
}
