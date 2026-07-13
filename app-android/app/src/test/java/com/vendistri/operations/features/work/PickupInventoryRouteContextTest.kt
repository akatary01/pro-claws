package com.vendistri.operations.features.work

import com.vendistri.operations.features.tasks.CollectionInputMode
import com.vendistri.operations.features.tasks.TaskPickupLine
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.VendiTask
import org.junit.Assert.assertEquals
import org.junit.Test

class PickupInventoryRouteContextTest {
    @Test
    fun linkedRefillTaskIdsPreferExplicitPickupLinks() {
        val pickup = task(
            id = "pickup",
            type = TaskType.MachinePickupInventory,
            refillTaskIds = listOf("refill-b", "refill-a")
        )

        val linked = PickupInventoryRouteContext.linkedRefillTaskIds(
            pickupTasks = listOf(pickup),
            allTasks = listOf(task("refill-a"), task("refill-b"))
        )

        assertEquals(listOf("refill-b", "refill-a"), linked)
    }

    @Test
    fun linkedRefillTaskIdsFallsBackToSameWarehouseSameDayRefills() {
        val pickup = task(
            id = "pickup",
            type = TaskType.MachinePickupInventory,
            warehouseId = "warehouse-1"
        )
        val refill = task(
            id = "refill",
            inventorySourceWarehouseId = "warehouse-1"
        )
        val otherWarehouse = task(
            id = "other",
            inventorySourceWarehouseId = "warehouse-2"
        )

        val linked = PickupInventoryRouteContext.linkedRefillTaskIds(
            pickupTasks = listOf(pickup),
            allTasks = listOf(refill, otherWarehouse)
        )

        assertEquals(listOf("refill"), linked)
    }

    @Test
    fun postPickupRouteUnionsSavedSessionWithExpandedServiceBundle() {
        val pickup = task(
            id = "pickup",
            type = TaskType.MachinePickupInventory,
            refillTaskIds = listOf("refill")
        )
        val service = task(id = "service", type = TaskType.MachineService)
        val refill = task(id = "refill", serviceTaskId = "service")

        val route = PickupInventoryRouteContext.postPickupRoute(
            pickupTasks = listOf(pickup),
            allTasks = listOf(pickup, service, refill),
            preferredRefillTaskId = "refill",
            savedStopId = "location-1",
            savedSessionTaskIds = setOf("refill")
        )

        requireNotNull(route)
        assertEquals("location-1", route.stopId)
        assertEquals(setOf("refill", "service"), route.taskIds)
    }

    private fun task(
        id: String,
        type: TaskType = TaskType.MachineRefill,
        refillTaskIds: List<String> = emptyList(),
        warehouseId: String? = null,
        inventorySourceWarehouseId: String? = null,
        serviceTaskId: String? = null
    ): VendiTask {
        return VendiTask(
            id = id,
            type = type,
            status = TaskStatus.Pending,
            isPublic = false,
            assignee = "user-1",
            assigneeName = "Operator",
            assigneeEmail = "operator@vendistri.com",
            machine = "machine-1",
            machineName = "Machine",
            collectionInputMode = CollectionInputMode.Dollars,
            creditsPerDollar = null,
            location = "location-1",
            locationName = "Downtown Office",
            locationAddress = null,
            scheduledFor = "2026-07-03",
            createdAt = "2026-07-03T10:00:00Z",
            startedAt = null,
            doneAt = null,
            isLive = false,
            duration = null,
            notes = null,
            distance = null,
            gross = null,
            grossCash = null,
            grossCard = null,
            refunds = null,
            commission = null,
            net = null,
            serviceTaskId = serviceTaskId,
            refillTaskId = null,
            refillTaskIds = refillTaskIds,
            pickupLines = emptyList<TaskPickupLine>(),
            inventorySourceWarehouseId = inventorySourceWarehouseId,
            warehouseId = warehouseId
        )
    }
}
