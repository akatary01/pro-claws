package com.vendistri.operations.features.navigation

import com.vendistri.operations.features.location.Address
import com.vendistri.operations.features.map.LocationCoordinate
import com.vendistri.operations.features.tasks.CollectionInputMode
import com.vendistri.operations.features.tasks.TaskMachineGroup
import com.vendistri.operations.features.tasks.TaskPickupLine
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.VendiTask
import com.vendistri.operations.features.work.GoNode
import com.vendistri.operations.features.work.GoNodeType
import com.vendistri.operations.features.work.GoStopPlan
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationTripOverviewContextTest {
    @Test
    fun pickupStopOverviewUsesDestinationLocationTasksWithoutPickupMachines() {
        val refill = task(
            id = "refill-1",
            type = TaskType.MachineRefill,
            machine = "machine-1",
            machineName = "Snack Machine"
        )
        val collection = task(
            id = "collection-1",
            type = TaskType.MachineCollection,
            machine = "machine-2",
            machineName = "Token Dispenser",
            gross = 634.35,
            commission = 189.0,
            net = 445.35
        )
        val pickup = task(
            id = "pickup-1",
            type = TaskType.MachinePickupInventory,
            machine = "pickup-machine",
            machineName = "Pickup Inventory Machine",
            refillTaskIds = listOf(refill.id),
            warehouseName = "Warehouse 2",
            warehouseAddress = warehouseAddress()
        )
        val otherLocation = task(
            id = "other-location-refill",
            type = TaskType.MachineRefill,
            location = "location-2",
            locationName = "Other Location",
            machine = "machine-3",
            machineName = "Other Machine"
        )
        val completedToday = task(
            id = "completed-today",
            type = TaskType.MachineClean,
            machine = "machine-1",
            machineName = "Snack Machine",
            status = TaskStatus.Done
        )
        val completedYesterday = task(
            id = "completed-yesterday",
            type = TaskType.MachineService,
            machine = "machine-1",
            machineName = "Snack Machine",
            status = TaskStatus.Done,
            scheduledFor = "2026-07-02"
        )
        val newlyAssigned = task(
            id = "newly-assigned",
            type = TaskType.MachineRepair,
            machine = "machine-1",
            machineName = "Snack Machine"
        )

        val context = navigationTripOverviewContext(
            stop = pickupStop(pickup),
            execution = null,
            postPickupDestinationStop = null,
            postPickupDestinationTaskIds = setOf(refill.id, collection.id),
            allTasks = listOf(
                refill,
                collection,
                pickup,
                otherLocation,
                completedToday,
                completedYesterday,
                newlyAssigned
            )
        )

        assertEquals("Arealicious Express", context.title)
        assertEquals(setOf(refill.id, collection.id), context.tasks.map { it.id }.toSet())
        assertEquals(634.35, context.tasks.sumOf { it.gross ?: 0.0 }, 0.0001)
        assertEquals(setOf("machine-1", "machine-2"), context.machineGroups.map { it.id }.toSet())
    }

    private fun pickupStop(pickup: VendiTask): GoStopPlan {
        return GoStopPlan(
            id = "warehouse:warehouse-1",
            targetLocationId = "warehouse-1",
            title = "Warehouse 2",
            addressStreetLine = "1529 86th St",
            addressCityStateZipLine = "Brooklyn, NY 11228",
            tasks = listOf(pickup),
            nodes = listOf(
                GoNode(
                    id = "warehouse-warehouse-1",
                    type = GoNodeType.Pickup,
                    title = "Warehouse 2",
                    subtitle = "1529 86th St",
                    coordinate = LocationCoordinate(40.0, -74.0),
                    locationId = null,
                    taskIds = listOf(pickup.id)
                )
            ),
            machineGroups = listOf(
                TaskMachineGroup(
                    id = "pickup-machine",
                    name = "Pickup Inventory Machine",
                    tasks = listOf(pickup),
                    durationMinutes = 0.0,
                    distanceMiles = 0.0
                )
            ),
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
        location: String = "location-1",
        locationName: String = "Arealicious Express",
        gross: Double? = null,
        commission: Double? = null,
        net: Double? = null,
        refillTaskIds: List<String> = emptyList(),
        warehouseName: String? = null,
        warehouseAddress: Address? = null,
        status: TaskStatus = TaskStatus.Pending,
        scheduledFor: String = "2026-07-03"
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
            locationAddress = locationAddress(),
            scheduledFor = scheduledFor,
            createdAt = "2026-07-03T10:00:00Z",
            startedAt = null,
            doneAt = null,
            isLive = false,
            duration = null,
            notes = null,
            distance = null,
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

    private fun locationAddress(): Address {
        return Address(
            street = "300 Adams Ave",
            city = "Staten Island",
            state = "NY",
            zipCode = "10306",
            latitude = 40.1,
            longitude = -74.1
        )
    }

    private fun warehouseAddress(): Address {
        return Address(
            street = "1529 86th St",
            city = "Brooklyn",
            state = "NY",
            zipCode = "11228",
            latitude = 40.0,
            longitude = -74.0
        )
    }
}
