package com.vendistri.operations.features.tasks

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskBundleHelpersTest {
    @Test
    fun expandedRefreshTaskIdsIncludesServiceBundleForChildTask() {
        val service = task(id = "service", type = TaskType.MachineService)
        val collection = task(id = "collection", type = TaskType.MachineCollection, serviceTaskId = "service")
        val refill = task(id = "refill", type = TaskType.MachineRefill, serviceTaskId = "service")

        val ids = TaskBundleHelpers.expandedRefreshTaskIds(
            tasks = listOf(service, collection, refill),
            taskIds = setOf("collection")
        )

        assertEquals(setOf("collection", "service", "refill"), ids)
    }

    @Test
    fun expandedRefreshTaskIdsIncludesLinkedRefillForPickupTask() {
        val service = task(id = "service", type = TaskType.MachineService)
        val refill = task(id = "refill", type = TaskType.MachineRefill, serviceTaskId = "service")
        val pickup = task(
            id = "pickup",
            type = TaskType.MachinePickupInventory,
            refillTaskIds = listOf("refill")
        )

        val ids = TaskBundleHelpers.expandedRefreshTaskIds(
            tasks = listOf(service, refill, pickup),
            taskIds = setOf("pickup")
        )

        assertEquals(setOf("pickup", "refill", "service"), ids)
    }

    private fun task(
        id: String,
        type: TaskType,
        serviceTaskId: String? = null,
        refillTaskIds: List<String> = emptyList()
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
            pickupLines = emptyList()
        )
    }
}
