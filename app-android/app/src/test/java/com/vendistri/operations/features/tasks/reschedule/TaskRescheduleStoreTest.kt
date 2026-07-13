package com.vendistri.operations.features.tasks.reschedule

import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.VendiTask
import com.vendistri.operations.features.tasks.toLocalDatePrefix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TaskRescheduleStoreTest {
    @Test
    fun selectedIdsAfterToggleKeepsServiceBundleTogether() {
        val service = task(id = "service", type = TaskType.MachineService)
        val child = task(id = "refill", type = TaskType.MachineRefill, serviceTaskId = "service")
        val standalone = task(id = "clean", type = TaskType.MachineClean)

        val selected = TaskRescheduleStore.selectedIdsAfterToggle(
            tasks = listOf(service, child, standalone),
            selectedTaskIds = emptySet(),
            taskId = "refill"
        )

        assertEquals(setOf("service", "refill"), selected)
    }

    @Test
    fun selectedIdsAfterToggleRemovesWholeServiceBundleWhenAlreadySelected() {
        val service = task(id = "service", type = TaskType.MachineService)
        val child = task(id = "refill", type = TaskType.MachineRefill, serviceTaskId = "service")

        val selected = TaskRescheduleStore.selectedIdsAfterToggle(
            tasks = listOf(service, child),
            selectedTaskIds = setOf("service", "refill"),
            taskId = "service"
        )

        assertEquals(emptySet<String>(), selected)
    }

    @Test
    fun localDatePrefixParsesIsoDateAndDateTime() {
        assertEquals(LocalDate.of(2026, 7, 3), "2026-07-03".toLocalDatePrefix())
        assertEquals(LocalDate.of(2026, 7, 3), "2026-07-03T12:34:56Z".toLocalDatePrefix())
        assertNull("bad-date".toLocalDatePrefix())
    }

    private fun task(
        id: String,
        type: TaskType,
        status: TaskStatus = TaskStatus.Pending,
        serviceTaskId: String? = null
    ): VendiTask {
        return VendiTask(
            id = id,
            type = type,
            status = status,
            isPublic = false,
            assignee = "operator-1",
            assigneeName = null,
            assigneeEmail = null,
            machine = "machine-1",
            machineName = "Machine",
            collectionInputMode = null,
            creditsPerDollar = null,
            location = "location-1",
            locationName = "Location",
            locationAddress = null,
            scheduledFor = "2026-07-03",
            createdAt = null,
            startedAt = null,
            doneAt = null,
            isLive = null,
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
            refillTaskIds = emptyList(),
            pickupLines = emptyList(),
            inventoryCompletion = null,
            inventorySourceMode = null,
            inventorySourceWarehouseId = null,
            inventorySourceWarehouseName = null,
            warehouseId = null,
            warehouseName = null,
            warehouseAddress = null
        )
    }
}
