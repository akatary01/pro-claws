package com.vendistri.operations.features.work

import com.vendistri.operations.features.tasks.CollectionInputMode
import com.vendistri.operations.features.tasks.TaskInventoryProduct
import com.vendistri.operations.features.tasks.TaskPickupLine
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.VendiTask
import org.junit.Assert.assertEquals
import org.junit.Test

class CompletedPickupDisplayTest {
    @Test
    fun sectionsFilterPickupLinesToPrimaryRefillTasks() {
        val pickup = pickupTask(
            status = TaskStatus.Done,
            lines = listOf(
                pickupLine(id = "line-1", refillTaskId = "refill-1", productId = "plush", pickedUpQuantity = 28),
                pickupLine(id = "line-2", refillTaskId = "refill-2", productId = "keychain", pickedUpQuantity = 4)
            )
        )

        val sections = CompletedPickupDisplay.sections(
            tasks = listOf(pickup),
            primaryRefillTaskIds = setOf("refill-1")
        )

        assertEquals(1, sections.size)
        assertEquals("pickup", sections.first().task.id)
        assertEquals(listOf("Plush"), sections.first().productGroups.map { it.productTitle })
        assertEquals(listOf("line-1"), sections.first().productGroups.flatMap { it.lines }.map { it.id })
        assertEquals("Completed Pickup Inventory", CompletedPickupDisplay.title(listOf(pickup)))
    }

    @Test
    fun metricTextUsesTaskDurationSeconds() {
        val pickup = pickupTask(
            status = TaskStatus.Done,
            duration = 5_400.0,
            distance = 0.3
        )

        assertEquals("1h 30m 0s • 0.3 mi", CompletedPickupDisplay.metricText(pickup))
    }

    @Test
    fun sectionsKeepPrimaryPickupTaskBeforePreviousLinkedPickups() {
        val previousPickup = pickupTask(id = "previous-pickup", status = TaskStatus.Cancelled)
        val activePickup = pickupTask(id = "active-pickup", status = TaskStatus.Done)

        val sections = CompletedPickupDisplay.sections(
            tasks = listOf(previousPickup, activePickup),
            primaryTaskIds = listOf("active-pickup")
        )

        assertEquals(listOf("active-pickup", "previous-pickup"), sections.map { it.task.id })
    }

    private fun pickupTask(
        id: String = "pickup",
        status: TaskStatus = TaskStatus.Pending,
        duration: Double? = null,
        distance: Double? = null,
        lines: List<TaskPickupLine> = emptyList()
    ): VendiTask {
        return VendiTask(
            id = id,
            type = TaskType.MachinePickupInventory,
            status = status,
            isPublic = false,
            assignee = "user-1",
            assigneeName = "Operator",
            assigneeEmail = "operator@vendistri.com",
            machine = "machine-1",
            machineName = "Arcadia Kings Plaza Card Kiosk",
            collectionInputMode = CollectionInputMode.Dollars,
            creditsPerDollar = null,
            location = "location-1",
            locationName = "Texas Roadhouse",
            locationAddress = null,
            scheduledFor = "2026-07-03",
            createdAt = "2026-07-03T10:00:00Z",
            startedAt = "2026-07-03T11:00:00Z",
            doneAt = "2026-07-03T12:30:00Z",
            isLive = false,
            duration = duration,
            notes = null,
            distance = distance,
            gross = null,
            grossCash = null,
            grossCard = null,
            refunds = null,
            commission = null,
            net = null,
            serviceTaskId = null,
            refillTaskId = null,
            refillTaskIds = lines.mapNotNull { it.refillTaskId },
            pickupLines = lines,
            warehouseName = "Extra Space Storage Unit"
        )
    }

    private fun pickupLine(
        id: String,
        refillTaskId: String,
        productId: String,
        pickedUpQuantity: Int
    ): TaskPickupLine {
        return TaskPickupLine(
            id = id,
            refillTaskId = refillTaskId,
            machineName = "Arcadia Kings Plaza Card Kiosk",
            product = TaskInventoryProduct(id = productId, name = productId.replaceFirstChar { it.uppercase() }, brand = null, code = null, size = null),
            currentStock = 172,
            capacity = 200,
            suggestedQuantity = 28,
            warehouseAvailableStock = 46,
            pickedUpQuantity = pickedUpQuantity
        )
    }
}
