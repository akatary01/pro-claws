package com.vendistri.operations.features.work

import com.vendistri.operations.features.location.Address
import com.vendistri.operations.features.tasks.CollectionInputMode
import com.vendistri.operations.features.tasks.TaskPickupLine
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.VendiTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WarehouseDestinationContextTest {
    @Test
    fun destinationBlockExcludesPickupMachinesOutsideDestinationLocation() {
        val refill = task(
            id = "refill-1",
            type = TaskType.MachineRefill,
            machine = "texas-refill-machine",
            machineName = "City Tire Auto Shop Snack Machine"
        )
        val collection = task(
            id = "collection-1",
            type = TaskType.MachineCollection,
            machine = "texas-collection-machine",
            machineName = "Arcadia Kings Plaza Card Kiosk",
            scheduledFor = "2026-07-04",
            status = TaskStatus.Done,
            gross = 1298.90,
            commission = 192.0,
            net = 1106.90
        )
        val pickup = task(
            id = "pickup-1",
            type = TaskType.MachinePickupInventory,
            machine = "pickup-machine",
            machineName = "Green Valley Hospital Drink Machine",
            refillTaskIds = listOf(refill.id, "other-location-refill"),
            warehouseName = "Extra Space Storage Unit",
            warehouseAddress = warehouseAddress()
        )
        val otherLocationPickupRefill = task(
            id = "other-location-refill",
            type = TaskType.MachineRefill,
            machine = "other-location-machine",
            machineName = "Maplewood Office Center Coffee Machine",
            location = "other-location",
            locationName = "Green Valley Hospital"
        )
        val execution = executionForPickup(pickup)

        val context = execution.warehouseDestinationContext(
            allTasks = listOf(refill, collection, pickup, otherLocationPickupRefill),
            locationsById = emptyMap(),
            postPickupDestination = PostPickupDestination(
                refillTaskId = refill.id,
                stopId = "texas-roadhouse",
                sessionTaskIds = setOf(refill.id)
            )
        )

        assertNotNull(context)
        requireNotNull(context)
        assertEquals("Texas Roadhouse", context.title)
        assertEquals(setOf(refill.id, collection.id), context.tasks.map { it.id }.toSet())
        assertEquals(1298.90, context.tasks.sumOf { it.gross ?: 0.0 }, 0.0001)
        assertEquals(
            setOf("texas-refill-machine"),
            context.machineGroups.map { it.id }.toSet()
        )
        assertEquals(
            setOf(refill.id, pickup.id),
            context.machineGroups.first { it.id == "texas-refill-machine" }.tasks.map { it.id }.toSet()
        )
        assertEquals(
            false,
            context.machineGroups.any { group -> group.tasks.any { it.id == otherLocationPickupRefill.id } }
        )
    }

    @Test
    fun destinationBlockExcludesCurrentPickupWhenItIsNotLinkedToDestinationRefillTaskId() {
        val refill = task(
            id = "refill-1",
            type = TaskType.MachineRefill,
            machine = "destination-machine",
            machineName = "I.S. 201 Gym Drink Machine"
        )
        val collection = task(
            id = "collection-1",
            type = TaskType.MachineCollection,
            machine = "destination-machine",
            machineName = "I.S. 201 Gym Drink Machine"
        )
        val service = task(
            id = "service-1",
            type = TaskType.MachineService,
            machine = "destination-machine",
            machineName = "I.S. 201 Gym Drink Machine"
        )
        val clean = task(
            id = "clean-1",
            type = TaskType.MachineClean,
            machine = "destination-machine",
            machineName = "I.S. 201 Gym Drink Machine"
        )
        val pickup = task(
            id = "pickup-1",
            type = TaskType.MachinePickupInventory,
            machine = "pickup-machine",
            machineName = "Warehouse Pickup",
            refillTaskIds = listOf("unrelated-refill"),
            warehouseName = "Extra Space Storage Unit",
            warehouseAddress = warehouseAddress()
        )
        val execution = executionForPickup(pickup)

        val context = execution.warehouseDestinationContext(
            allTasks = listOf(refill, collection, service, clean, pickup),
            locationsById = emptyMap(),
            postPickupDestination = PostPickupDestination(
                refillTaskId = refill.id,
                stopId = "texas-roadhouse",
                sessionTaskIds = setOf(refill.id)
            )
        )

        assertNotNull(context)
        requireNotNull(context)
        val machineGroup = context.machineGroups.single { it.id == "destination-machine" }
        assertEquals(setOf(refill.id, collection.id, service.id, clean.id), machineGroup.tasks.map { it.id }.toSet())
        assertEquals(false, machineGroup.tasks.any { it.id == pickup.id })
        assertEquals(4, context.pendingCount)
    }

    @Test
    fun activeWarehousePickupDoesNotRenderCurrentTaskAsCompletedLinkedWork() {
        val pickup = task(
            id = "pickup-1",
            type = TaskType.MachinePickupInventory,
            machine = "pickup-machine",
            machineName = "City Tire Auto Shop Snack Machine",
            status = TaskStatus.Cancelled,
            refillTaskIds = listOf("refill-1"),
            warehouseName = "Extra Space Storage Unit",
            warehouseAddress = warehouseAddress()
        )
        val execution = executionForPickup(pickup, currentTaskId = pickup.id)
        val scope = ExecutionScopeResolver.resolve(
            execution = execution,
            allTasks = listOf(pickup),
            nowEpochMillis = 0L
        )

        assertEquals(listOf(pickup.id), scope.completedPickupTasks.map { it.id })
        assertEquals(
            emptyList<VendiTask>(),
            completedPickupTasksForDisplay(
                execution = execution,
                scope = scope,
                isWarehousePickupVisit = true
            )
        )
    }

    @Test
    fun warehousePickupHeaderMetricsKeepCompletedPickupDuration() {
        val pickup = task(
            id = "pickup-1",
            type = TaskType.MachinePickupInventory,
            machine = "pickup-machine",
            machineName = "City Tire Auto Shop Snack Machine",
            status = TaskStatus.Done,
            duration = 172.0,
            distance = 0.3,
            refillTaskIds = listOf("refill-1")
        )
        val destinationTask = task(
            id = "collection-1",
            type = TaskType.MachineCollection,
            machine = "destination-machine",
            machineName = "Destination Machine",
            status = TaskStatus.Pending
        )
        val execution = executionForPickup(pickup, currentTaskId = null).copy(
            displayTasks = listOf(pickup, destinationTask)
        )

        val metrics = warehousePickupHeaderMetrics(
            pickupTasks = listOf(pickup),
            execution = execution,
            nowEpochMillis = 0L
        )

        assertEquals(172.0 / 60.0, metrics.durationMinutes, 0.0001)
        assertEquals(0.3, metrics.distanceMiles, 0.0001)
    }

    @Test
    fun warehouseHeaderMetricsUseFinalHydratedPickupEvenWhenExecutionStillHasCurrentTask() {
        val pendingPickup = task(
            id = "pickup-1",
            type = TaskType.MachinePickupInventory,
            machine = "pickup-machine",
            machineName = "City Tire Auto Shop Snack Machine",
            status = TaskStatus.Pending,
            refillTaskIds = listOf("refill-1")
        )
        val donePickup = pendingPickup.copy(
            status = TaskStatus.Done,
            duration = 732.0,
            distance = 0.12
        )
        val execution = executionForPickup(pendingPickup, currentTaskId = pendingPickup.id)
        val scope = ExecutionScopeResolver.resolve(
            execution = execution,
            allTasks = listOf(donePickup),
            nowEpochMillis = 0L
        )

        val metrics = atDestinationHeaderMetrics(
            execution = execution,
            scope = scope,
            isWarehousePickupVisit = true,
            nowEpochMillis = 0L
        )

        assertEquals(732.0 / 60.0, metrics.durationMinutes, 0.0001)
        assertEquals(0.12, metrics.distanceMiles, 0.0001)
    }

    @Test
    fun locationHeaderMetricsExcludeCompletedPickupFromCurrentVisitMetrics() {
        val refill = task(
            id = "refill-1",
            type = TaskType.MachineRefill,
            machine = "destination-machine",
            machineName = "Destination Machine",
            status = TaskStatus.Done,
            duration = 300.0,
            distance = 0.2
        )
        val pickup = task(
            id = "pickup-1",
            type = TaskType.MachinePickupInventory,
            machine = "pickup-machine",
            machineName = "Warehouse Pickup",
            status = TaskStatus.Done,
            duration = 732.0,
            distance = 0.12,
            refillTaskIds = listOf(refill.id)
        )
        val execution = ActiveTaskExecution(
            stopId = "location:texas-roadhouse",
            title = "Texas Roadhouse",
            locationId = "texas-roadhouse",
            destinationKind = WorkDestinationKind.Location,
            taskIds = listOf(refill.id),
            wrapperTaskId = null,
            displayTasks = listOf(refill),
            tasks = listOf(
                ExecutionTaskItem(
                    id = refill.id,
                    type = refill.type,
                    status = refill.status,
                    machineId = refill.machine,
                    machineName = refill.machineName,
                    startedAt = refill.startedAt,
                    doneAt = refill.doneAt,
                    isWrapper = false
                )
            ),
            machineGroups = TaskExecutionResolver.orderedMachineGroups(listOf(refill)),
            currentTaskId = null,
            currentTaskIndex = 1,
            totalTaskCount = 1,
            gross = 0.0,
            refunds = 0.0,
            commission = 0.0,
            net = 0.0
        )
        val scope = ExecutionScopeResolver.resolve(
            execution = execution,
            allTasks = listOf(refill, pickup),
            nowEpochMillis = 0L
        )

        val metrics = atDestinationHeaderMetrics(
            execution = execution,
            scope = scope,
            isWarehousePickupVisit = false,
            nowEpochMillis = 0L
        )

        assertEquals(300.0 / 60.0, metrics.durationMinutes, 0.0001)
        assertEquals(0.2, metrics.distanceMiles, 0.0001)
        assertEquals(listOf(pickup.id), scope.completedPickupTasks.map { it.id })
    }

    private fun executionForPickup(pickup: VendiTask, currentTaskId: String? = pickup.id): ActiveTaskExecution {
        return ActiveTaskExecution(
            stopId = "warehouse:warehouse-1",
            title = "Extra Space Storage Unit",
            locationId = "warehouse-1",
            destinationKind = WorkDestinationKind.Warehouse,
            taskIds = listOf(pickup.id),
            wrapperTaskId = null,
            displayTasks = listOf(pickup),
            tasks = listOf(
                ExecutionTaskItem(
                    id = pickup.id,
                    type = pickup.type,
                    status = pickup.status,
                    machineId = pickup.machine,
                    machineName = pickup.machineName,
                    startedAt = pickup.startedAt,
                    doneAt = pickup.doneAt,
                    isWrapper = false
                )
            ),
            machineGroups = TaskExecutionResolver.orderedMachineGroups(listOf(pickup)),
            currentTaskId = currentTaskId,
            currentTaskIndex = 0,
            totalTaskCount = 1,
            gross = 0.0,
            refunds = 0.0,
            commission = 0.0,
            net = 0.0
        )
    }

    private fun task(
        id: String,
        type: TaskType,
        machine: String,
        machineName: String,
        location: String = "texas-roadhouse",
        locationName: String = "Texas Roadhouse",
        gross: Double? = null,
        commission: Double? = null,
        net: Double? = null,
        refillTaskIds: List<String> = emptyList(),
        warehouseName: String? = null,
        warehouseAddress: Address? = null,
        status: TaskStatus = TaskStatus.Pending,
        scheduledFor: String = "2026-07-03",
        duration: Double? = null,
        distance: Double? = null
    ): VendiTask {
        return VendiTask(
            id = id,
            type = type,
            status = status,
            isPublic = false,
            assignee = "user-1",
            assigneeName = "Operator",
            assigneeEmail = "operator@vendistri.com",
            machine = machine,
            machineName = machineName,
            collectionInputMode = CollectionInputMode.Dollars,
            creditsPerDollar = null,
            location = location,
            locationName = locationName,
            locationAddress = Address(
                street = "1995 Hylan Blvd",
                city = "Staten Island",
                state = "NY",
                zipCode = "10306",
                latitude = 40.1,
                longitude = -74.1
            ),
            scheduledFor = scheduledFor,
            createdAt = "2026-07-03T10:00:00Z",
            startedAt = null,
            doneAt = null,
            isLive = false,
            duration = duration,
            notes = null,
            distance = distance,
            gross = gross,
            grossCash = gross,
            grossCard = 0.0,
            refunds = 0.0,
            commission = commission,
            net = net,
            serviceTaskId = null,
            refillTaskId = null,
            refillTaskIds = refillTaskIds,
            pickupLines = emptyList<TaskPickupLine>(),
            warehouseId = "warehouse-1",
            warehouseName = warehouseName,
            warehouseAddress = warehouseAddress
        )
    }

    private fun warehouseAddress(): Address {
        return Address(
            street = "2380 Hylan Blvd",
            city = "Staten Island",
            state = "NY",
            zipCode = "10306",
            latitude = 40.0,
            longitude = -74.0
        )
    }
}
