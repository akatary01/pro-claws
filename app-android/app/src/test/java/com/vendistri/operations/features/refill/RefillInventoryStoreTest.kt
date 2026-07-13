package com.vendistri.operations.features.refill

import com.vendistri.operations.features.tasks.CollectionInputMode
import com.vendistri.operations.features.tasks.RefillInventorySourceMode
import com.vendistri.operations.features.tasks.TaskInventoryProduct
import com.vendistri.operations.features.tasks.TaskPickupLine
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.TasksApi
import com.vendistri.operations.features.tasks.VendiTask
import com.vendistri.operations.network.ApiResponse
import com.vendistri.operations.network.ApiTransport
import com.vendistri.operations.network.HttpMethod
import com.vendistri.operations.network.MultipartFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RefillInventoryStoreTest {
    @Test
    fun fullCompletedPickupCoverageLocksInventorySource() {
        val refillLine = refillLine(productId = "plush", suggestedRefill = 28)
        val pickup = pickupTask(
            lines = listOf(pickupLine(productId = "plush", refillTaskId = "refill-1", pickedUpQuantity = 28))
        )

        val summary = RefillInventorySourceSummaryResolver.pickupSourceSummary(
            refillTaskId = "refill-1",
            refillLines = listOf(refillLine),
            allTasks = listOf(pickup),
            fallbackWarehouseName = "Extra Space Storage Unit"
        )

        assertEquals("Pickup: Extra Space Storage Unit", summary)
    }

    @Test
    fun partialCompletedPickupCoverageKeepsInventorySourceEditable() {
        val refillLine = refillLine(productId = "plush", suggestedRefill = 28)
        val pickup = pickupTask(
            lines = listOf(pickupLine(productId = "plush", refillTaskId = "refill-1", pickedUpQuantity = 10))
        )

        val summary = RefillInventorySourceSummaryResolver.pickupSourceSummary(
            refillTaskId = "refill-1",
            refillLines = listOf(refillLine),
            allTasks = listOf(pickup),
            fallbackWarehouseName = "Extra Space Storage Unit"
        )

        assertNull(summary)
    }

    @Test
    fun prepareUsesPickedUpQuantityAndAutoFillRecalculatesUnclampedFinalStock() = runBlocking {
        val store = RefillInventoryStore(TasksApi(FakeTransport(suggestionsJson(pickedUpQuantity = 28))))
        val refillTask = refillTask()
        val pickup = pickupTask(
            lines = listOf(pickupLine(productId = "plush", refillTaskId = refillTask.id, pickedUpQuantity = 28))
        )

        store.prepare(refillTask, allTasks = listOf(refillTask, pickup), force = true)

        val preparedState = store.state.value
        assertTrue(preparedState.hasCompletedPickupCoverage)
        assertEquals("28", preparedState.lines.single().refilledText)
        assertEquals("200", preparedState.lines.single().finalStockText)

        store.updateRefilledQuantity(itemId = "item-1", value = "40", autoFillFinalStock = true)

        val updatedLine = store.state.value.lines.single()
        assertEquals("40", updatedLine.refilledText)
        assertEquals("212", updatedLine.finalStockText)
    }

    @Test
    fun prepareLeavesDraftBlankWhenNoPickupQuantityExists() = runBlocking {
        val store = RefillInventoryStore(TasksApi(FakeTransport(suggestionsJson(pickedUpQuantity = null))))

        store.prepare(refillTask(), allTasks = emptyList(), force = true)

        val state = store.state.value
        assertFalse(state.hasCompletedPickupCoverage)
        assertEquals("", state.lines.single().refilledText)
        assertEquals("172", state.lines.single().finalStockText)
    }

    @Test
    fun taskScopedPrepareKeepsActiveWorkflowStateSeparate() = runBlocking {
        val store = RefillInventoryStore(TasksApi(FakeTransport(suggestionsJson(pickedUpQuantity = null))))
        val task = refillTask()

        store.prepareTask(task, allTasks = emptyList(), force = true)
        store.updateTaskRefilledQuantity(task.id, itemId = "item-1", value = "12", autoFillFinalStock = true)

        val taskState = store.taskStates.value.getValue(task.id)
        assertNull(store.state.value.taskId)
        assertEquals("warehouse-2", taskState.selectedWarehouseId)
        assertEquals("Extra Space Storage Unit", taskState.selectedWarehouseName)
        assertEquals("12", taskState.lines.single().refilledText)
        assertEquals("184", taskState.lines.single().finalStockText)

        val lines = store.validatedTaskCompletionLines(task.id)
        assertEquals(1, lines?.size)
        assertEquals(12, lines?.single()?.refilledQuantity)
        assertEquals(184, lines?.single()?.finalStock)
    }

    private fun refillLine(productId: String, suggestedRefill: Int): RefillInventoryLine {
        return RefillInventoryLine(
            itemId = "item-1",
            product = product(productId),
            currentStock = 172,
            capacity = 200,
            suggestedRefill = suggestedRefill,
            pickedUpQuantity = null,
            warehouseAvailableStock = 50,
            refilledText = "",
            finalStockText = "172"
        )
    }

    private fun refillTask(): VendiTask {
        return task(
            id = "refill-1",
            type = TaskType.MachineRefill,
            status = TaskStatus.Pending,
            warehouseName = "Extra Space Storage Unit"
        )
    }

    private fun pickupTask(lines: List<TaskPickupLine>): VendiTask {
        return task(
            id = "pickup-1",
            type = TaskType.MachinePickupInventory,
            status = TaskStatus.Done,
            warehouseName = "Extra Space Storage Unit",
            pickupLines = lines
        )
    }

    private fun task(
        id: String,
        type: TaskType,
        status: TaskStatus,
        warehouseName: String? = null,
        pickupLines: List<TaskPickupLine> = emptyList()
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
            machineName = "Arcadia Kings Plaza Card Kiosk",
            collectionInputMode = CollectionInputMode.Dollars,
            creditsPerDollar = null,
            location = "location-1",
            locationName = "Texas Roadhouse",
            locationAddress = null,
            scheduledFor = "2026-07-08",
            createdAt = null,
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
            refillTaskId = null,
            refillTaskIds = emptyList(),
            pickupLines = pickupLines,
            warehouseName = warehouseName
        )
    }

    private fun pickupLine(productId: String, refillTaskId: String, pickedUpQuantity: Int): TaskPickupLine {
        return TaskPickupLine(
            id = "line-$productId",
            refillTaskId = refillTaskId,
            machineName = "Arcadia Kings Plaza Card Kiosk",
            product = product(productId),
            currentStock = 172,
            capacity = 200,
            suggestedQuantity = 28,
            warehouseAvailableStock = 50,
            pickedUpQuantity = pickedUpQuantity
        )
    }

    private fun product(id: String): TaskInventoryProduct {
        return TaskInventoryProduct(
            id = id,
            name = id.replaceFirstChar { it.uppercase() },
            brand = "Test Plush",
            code = null,
            size = "5in"
        )
    }

    private fun suggestionsJson(pickedUpQuantity: Int?): String {
        val pickedUpField = pickedUpQuantity?.let { ""","pickedUpQuantity":$it""" }.orEmpty()
        return """
            {
              "taskId": "refill-1",
              "warehouseId": "warehouse-2",
              "warehouseName": "Extra Space Storage Unit",
              "items": [
                {
                  "itemId": "item-1",
                  "product": {"id": "plush", "name": "Plush", "brand": "Test Plush", "size": "5in"},
                  "currentStock": 172,
                  "capacity": 200,
                  "suggestedRefill": 28,
                  "warehouseAvailableStock": 50
                  $pickedUpField
                }
              ]
            }
        """.trimIndent()
    }

    private class FakeTransport(private val responseBody: String) : ApiTransport {
        override suspend fun request(
            method: HttpMethod,
            path: String,
            body: String?,
            headers: Map<String, String>
        ): ApiResponse {
            return ApiResponse(statusCode = 200, body = responseBody)
        }

        override suspend fun requestMultipart(
            method: HttpMethod,
            path: String,
            file: MultipartFile,
            headers: Map<String, String>
        ): ApiResponse {
            return ApiResponse(statusCode = 200, body = responseBody)
        }
    }
}
