package com.vendistri.operations.features.tasks

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskLiveTargetResolverTest {
    @Test
    fun targetUsesServiceWrapperAsNavigateTaskForLiveChild() {
        val service = task("service", TaskType.MachineService)
        val refill = task(
            id = "refill",
            type = TaskType.MachineRefill,
            serviceTaskId = "service",
            startedAt = "2026-07-03T11:00:00Z"
        )
        val collection = task(
            id = "collection",
            type = TaskType.MachineCollection,
            serviceTaskId = "service"
        )

        val target = TaskLiveTargetResolver.target(
            scopedTasks = listOf(service, refill, collection),
            allTasks = listOf(service, refill, collection)
        )

        assertEquals("refill", target?.activeTask?.id)
        assertEquals("service", target?.navigateTask?.id)
        assertEquals("2026-07-03T11:00:00Z", target?.timerStartedAt)
        assertEquals(setOf("service", "refill", "collection"), TaskLiveTargetResolver.effectiveLiveTaskIds(listOf(service, refill, collection)))
    }

    @Test
    fun pickupLiveScopeIncludesLinkedRefillBundleInScopedIds() {
        val pickup = task(
            id = "pickup",
            type = TaskType.MachinePickupInventory,
            startedAt = "2026-07-03T11:00:00Z",
            refillTaskIds = listOf("refill")
        )
        val service = task("service", TaskType.MachineService)
        val refill = task(
            id = "refill",
            type = TaskType.MachineRefill,
            serviceTaskId = "service"
        )
        val clean = task(
            id = "clean",
            type = TaskType.MachineClean,
            serviceTaskId = "service"
        )

        val liveIds = TaskLiveTargetResolver.effectiveLiveTaskIds(
            scopedTasks = listOf(refill, clean),
            allTasks = listOf(pickup, service, refill, clean)
        )

        assertEquals(setOf("refill", "clean"), liveIds)
    }

    private fun task(
        id: String,
        type: TaskType,
        status: TaskStatus = TaskStatus.Pending,
        startedAt: String? = null,
        serviceTaskId: String? = null,
        refillTaskIds: List<String> = emptyList()
    ): VendiTask {
        return VendiTask(
            id = id,
            type = type,
            status = status,
            isPublic = false,
            assignee = "user-1",
            assigneeName = "Operator",
            assigneeEmail = "operator@vendistri.com",
            machine = "machine-1",
            machineName = "Machine",
            collectionInputMode = CollectionInputMode.Dollars,
            creditsPerDollar = null,
            location = "location-1",
            locationName = "Location",
            locationAddress = null,
            scheduledFor = "2026-07-03",
            createdAt = "2026-07-03T10:00:00Z",
            startedAt = startedAt,
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
