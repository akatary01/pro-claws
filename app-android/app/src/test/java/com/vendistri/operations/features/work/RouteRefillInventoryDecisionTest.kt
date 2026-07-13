package com.vendistri.operations.features.work

import com.vendistri.operations.features.location.WarehouseOption
import com.vendistri.operations.features.refill.AggregateRefillInventorySuggestion
import com.vendistri.operations.features.refill.RefillInventoryContext
import com.vendistri.operations.features.refill.RefillInventoryCoverage
import com.vendistri.operations.features.refill.RefillInventorySuggestionLine
import com.vendistri.operations.features.refill.RefillInventorySuggestions
import com.vendistri.operations.features.tasks.CollectionInputMode
import com.vendistri.operations.features.tasks.TaskInventoryProduct
import com.vendistri.operations.features.tasks.TaskInventoryCompletion
import com.vendistri.operations.features.tasks.TaskPickupLine
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.VendiTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteRefillInventoryDecisionTest {
    @Test
    fun anchorTaskUsesCurrentExecutableEligibleRefill() {
        val refill = task(id = "refill")
        val stop = stop(refill, task(id = "collection", type = TaskType.MachineCollection))

        val anchor = RouteRefillInventoryDecision.anchorTask(
            stop = stop,
            allTasks = stop.tasks,
            currentUserId = "user-1",
            bypassedTaskIds = emptySet()
        )

        assertEquals("refill", anchor?.id)
    }

    @Test
    fun anchorTaskSkipsCompletedInventoryRefill() {
        val refill = task(id = "refill", inventoryCompletion = TaskInventoryCompletion(emptyList()))

        val anchor = RouteRefillInventoryDecision.anchorTask(
            stop = stop(refill),
            allTasks = listOf(refill),
            currentUserId = "user-1",
            bypassedTaskIds = emptySet()
        )

        assertNull(anchor)
    }

    @Test
    fun eligibleTasksIncludesSelectedUnassignedSameDayRefills() {
        val anchor = task(id = "anchor")
        val selected = task(id = "selected", assignee = null, status = TaskStatus.Unassigned)
        val nextDay = task(id = "next-day", scheduledFor = "2026-07-04")

        val eligible = RouteRefillInventoryDecision.eligibleTasks(
            plan = stop(anchor, selected, nextDay),
            anchorTask = anchor,
            allTasks = listOf(anchor, selected, nextDay),
            currentUserId = "user-1",
            bypassedTaskIds = emptySet(),
            selectedTaskIds = setOf("selected")
        )

        assertEquals(listOf("anchor", "selected"), eligible.map { it.id })
    }

    @Test
    fun refillDecisionPreservesOriginalSelectedRouteTaskIds() {
        val refill = task(id = "refill")
        val collection = task(id = "collection", type = TaskType.MachineCollection)
        val store = RefillDecisionStore()

        store.prepare(
            stop = stop(refill, collection),
            plan = goPlan(stop(refill, collection)),
            allTasks = listOf(refill, collection),
            currentUserId = "user-1",
            warehouses = listOf(warehouse()),
            selectedTaskIds = setOf("refill", "collection"),
            context = RefillInventoryContext(
                tasks = listOf(refill),
                suggestions = listOf(suggestions(refill, suggestedRefill = 3, pickedUpQuantity = 0)),
                aggregateSuggestion = AggregateRefillInventorySuggestion(
                    warehouseId = "warehouse-1",
                    warehouseName = "Warehouse 1",
                    warehouseAddress = null
                ),
                coverage = RefillInventoryCoverage(
                    coveredTaskIds = emptyList(),
                    remainingTaskIds = listOf("refill")
                )
            ),
            pendingStop = stop(refill, collection),
            routePreview = null,
            warehouseRoutePreview = null
        )

        assertEquals(setOf("refill", "collection"), store.state.value.selectedTaskIds)
    }

    @Test
    fun currentLocationWithNoRefillItemsRoutesDirectlyWhileKeepingLaterOpportunities() {
        val current = task(id = "current", location = "location-1", locationName = "Test")
        val later = task(id = "later", location = "location-2", locationName = "Later")
        val store = RefillDecisionStore()

        store.prepare(
            stop = stop(current),
            plan = goPlan(stop(current), stop(later)),
            allTasks = listOf(current, later),
            currentUserId = "user-1",
            warehouses = listOf(warehouse()),
            selectedTaskIds = setOf("current", "later"),
            context = RefillInventoryContext(
                tasks = listOf(current, later),
                suggestions = listOf(
                    suggestions(current, suggestedRefill = 0, pickedUpQuantity = 0),
                    suggestions(later, suggestedRefill = 3, pickedUpQuantity = 0)
                ),
                aggregateSuggestion = AggregateRefillInventorySuggestion(
                    warehouseId = "warehouse-1",
                    warehouseName = "Warehouse 1",
                    warehouseAddress = null
                ),
                // The server may conservatively include the zero-item task here.
                coverage = RefillInventoryCoverage(
                    coveredTaskIds = emptyList(),
                    remainingTaskIds = listOf("current", "later")
                )
            ),
            pendingStop = stop(current),
            routePreview = null,
            warehouseRoutePreview = null
        )

        val state = store.state.value
        assertFalse(state.plans.first { it.task.id == "current" }.isRemaining)
        assertTrue(state.plans.first { it.task.id == "later" }.isRemaining)
        assertTrue(state.canContinueWithoutPickup)

        store.toggleTaskInclusion("later")

        assertTrue(store.state.value.isPickupAlreadyCovered)
        assertEquals(RefillDecisionAction.RouteToLocation, store.state.value.selectedAction)
    }

    @Test
    fun removingLaterRefillSelectsRouteToLocationWhenCurrentStopPickupIsCovered() {
        val coveredCurrentStopRefill = task(id = "current", location = "location-1", locationName = "I.S. 201")
        val laterRefill = task(id = "later", location = "location-2", locationName = "Texas Roadhouse")
        val store = RefillDecisionStore()

        store.prepare(
            stop = stop(coveredCurrentStopRefill),
            plan = goPlan(stop(coveredCurrentStopRefill), stop(laterRefill)),
            allTasks = listOf(coveredCurrentStopRefill, laterRefill),
            currentUserId = "user-1",
            warehouses = listOf(warehouse()),
            selectedTaskIds = emptySet(),
            context = RefillInventoryContext(
                tasks = listOf(coveredCurrentStopRefill, laterRefill),
                suggestions = listOf(
                    suggestions(coveredCurrentStopRefill, suggestedRefill = 1, pickedUpQuantity = 1),
                    suggestions(laterRefill, suggestedRefill = 3, pickedUpQuantity = 0)
                ),
                aggregateSuggestion = AggregateRefillInventorySuggestion(
                    warehouseId = "warehouse-1",
                    warehouseName = "Warehouse 1",
                    warehouseAddress = null
                ),
                coverage = RefillInventoryCoverage(
                    coveredTaskIds = listOf("current"),
                    remainingTaskIds = listOf("later")
                )
            ),
            pendingStop = stop(coveredCurrentStopRefill),
            routePreview = null,
            warehouseRoutePreview = null
        )

        assertEquals(RefillDecisionAction.RouteToWarehouse, store.state.value.selectedAction)
        assertTrue(store.state.value.includedPlans.any { it.task.id == "later" })

        store.toggleTaskInclusion("later")

        val state = store.state.value
        assertFalse(state.includedPlans.any { it.task.id == "later" })
        assertTrue(state.canContinueWithoutPickup)
        assertTrue(state.isPickupAlreadyCovered)
        assertEquals(RefillDecisionAction.RouteToLocation, state.selectedAction)

        store.selectAction(RefillDecisionAction.RouteToWarehouse)

        assertEquals(RefillDecisionAction.RouteToLocation, store.state.value.selectedAction)
    }

    @Test
    fun removingCurrentStopRefillRequiresCurrentLocationMachineEvenWhenLaterRefillIsIncluded() {
        val currentRefill = task(id = "current", location = "location-1", locationName = "I.S. 201")
        val laterRefill = task(id = "later", location = "location-2", locationName = "Texas Roadhouse")
        val store = RefillDecisionStore()

        store.prepare(
            stop = stop(currentRefill),
            plan = goPlan(stop(currentRefill), stop(laterRefill)),
            allTasks = listOf(currentRefill, laterRefill),
            currentUserId = "user-1",
            warehouses = listOf(warehouse()),
            selectedTaskIds = emptySet(),
            context = RefillInventoryContext(
                tasks = listOf(currentRefill, laterRefill),
                suggestions = listOf(
                    suggestions(currentRefill, suggestedRefill = 2, pickedUpQuantity = 0),
                    suggestions(laterRefill, suggestedRefill = 3, pickedUpQuantity = 0)
                ),
                aggregateSuggestion = AggregateRefillInventorySuggestion(
                    warehouseId = "warehouse-1",
                    warehouseName = "Warehouse 1",
                    warehouseAddress = null
                ),
                coverage = RefillInventoryCoverage(
                    coveredTaskIds = emptyList(),
                    remainingTaskIds = listOf("current", "later")
                )
            ),
            pendingStop = stop(currentRefill),
            routePreview = null,
            warehouseRoutePreview = null
        )

        store.toggleTaskInclusion("current")

        val state = store.state.value
        assertTrue(state.includedPlans.any { it.task.id == "later" })
        assertFalse(state.includedPlans.any { it.task.id == "current" })
        assertTrue(state.currentStopSelectionRequired)
        assertFalse(state.canApply)
        assertEquals(
            "Select at least one machine from I.S. 201 to start this route.",
            state.currentStopSelectionRequiredMessage
        )
    }

    private fun stop(vararg tasks: VendiTask): GoStopPlan {
        val locationId = tasks.firstOrNull()?.location ?: "location-1"
        return GoStopPlan(
            id = locationId,
            targetLocationId = locationId,
            title = tasks.firstOrNull()?.locationName ?: "Downtown Office",
            addressStreetLine = null,
            addressCityStateZipLine = null,
            tasks = tasks.toList(),
            nodes = emptyList(),
            machineGroups = emptyList(),
            gross = 0.0,
            refunds = 0.0,
            commission = 0.0,
            net = 0.0
        )
    }

    private fun goPlan(vararg stops: GoStopPlan): GoPlan {
        val tasks = stops.flatMap { it.tasks }
        return GoPlan(
            generatedAtEpochMillis = 0L,
            tasks = tasks,
            stops = stops.toList(),
            suggestedStopId = stops.firstOrNull()?.id
        )
    }

    private fun task(
        id: String,
        type: TaskType = TaskType.MachineRefill,
        status: TaskStatus = TaskStatus.Pending,
        assignee: String? = "user-1",
        scheduledFor: String = "2026-07-03",
        inventoryCompletion: TaskInventoryCompletion? = null,
        location: String = "location-1",
        locationName: String = "Downtown Office"
    ): VendiTask {
        return VendiTask(
            id = id,
            type = type,
            status = status,
            isPublic = false,
            assignee = assignee,
            assigneeName = "Operator",
            assigneeEmail = "operator@vendistri.com",
            machine = "machine-$id",
            machineName = "Machine",
            collectionInputMode = CollectionInputMode.Dollars,
            creditsPerDollar = null,
            location = location,
            locationName = locationName,
            locationAddress = null,
            scheduledFor = scheduledFor,
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
            refillTaskId = null,
            refillTaskIds = emptyList(),
            pickupLines = emptyList<TaskPickupLine>(),
            inventoryCompletion = inventoryCompletion
        )
    }

    private fun suggestions(
        task: VendiTask,
        suggestedRefill: Int,
        pickedUpQuantity: Int?
    ): RefillInventorySuggestions {
        return RefillInventorySuggestions(
            taskId = task.id,
            machineId = task.machine,
            warehouseId = "warehouse-1",
            warehouseName = "Warehouse 1",
            warehouseAddress = null,
            items = listOf(
                RefillInventorySuggestionLine(
                    itemId = "item-${task.id}",
                    product = TaskInventoryProduct(
                        id = "product-${task.id}",
                        name = "Big Prizes",
                        brand = null,
                        code = null,
                        size = null
                    ),
                    currentStock = 0,
                    capacity = 10,
                    suggestedRefill = suggestedRefill,
                    warehouseAvailableStock = 10,
                    pickedUpQuantity = pickedUpQuantity
                )
            )
        )
    }

    private fun warehouse(): WarehouseOption {
        return WarehouseOption(
            id = "warehouse-1",
            name = "Warehouse 1",
            inventoryId = "inventory-1",
            address = null,
            organization = null,
            isPublic = false,
            isActive = true
        )
    }
}
