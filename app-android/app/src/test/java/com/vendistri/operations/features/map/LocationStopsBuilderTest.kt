package com.vendistri.operations.features.map

import com.vendistri.operations.features.location.Address
import com.vendistri.operations.features.location.AppLocation
import com.vendistri.operations.features.tasks.CollectionInputMode
import com.vendistri.operations.features.tasks.CommissionPaymentType
import com.vendistri.operations.features.tasks.TaskPickupLine
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.VendiTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LocationStopsBuilderTest {
    @Test
    fun buildStopsUsesTodayTasksAndLocationAddressFallback() {
        val today = LocalDate.of(2026, 7, 3)
        val tasks = listOf(
            task(
                id = "pending",
                type = TaskType.MachineCollection,
                status = TaskStatus.Pending,
                gross = 12.0,
                net = 8.0
            ),
            task(
                id = "done",
                type = TaskType.MachineCollection,
                status = TaskStatus.Done,
                machine = "machine-2",
                gross = 20.0,
                net = 15.0
            ),
            task(
                id = "tomorrow",
                status = TaskStatus.Pending,
                scheduledFor = "2026-07-04"
            )
        )
        val locations = mapOf(
            "location-1" to location(
                id = "location-1",
                address = address(latitude = 40.7128, longitude = -74.0060)
            )
        )

        val stops = LocationStopsBuilder.buildStops(tasks, locations, today)

        assertEquals(1, stops.size)
        val stop = stops.first()
        assertEquals("location-1", stop.id)
        assertEquals(40.7128, stop.coordinate.latitude, 0.0001)
        assertEquals(-74.0060, stop.coordinate.longitude, 0.0001)
        assertEquals(1, stop.pendingCount)
        assertEquals(1, stop.doneCount)
        assertEquals(2, stop.totalCount)
        assertEquals(2, stop.machineCount)
        assertEquals(32.0, stop.gross, 0.0001)
        assertEquals(23.0, stop.net, 0.0001)
    }

    @Test
    fun buildStopsPrefersTaskAddressWhenAvailable() {
        val today = LocalDate.of(2026, 7, 3)
        val taskAddress = address(latitude = 33.7490, longitude = -84.3880)
        val locations = mapOf(
            "location-1" to location(
                id = "location-1",
                address = address(latitude = 40.7128, longitude = -74.0060)
            )
        )

        val stops = LocationStopsBuilder.buildStops(
            tasks = listOf(task(id = "task-1", locationAddress = taskAddress)),
            locationsById = locations,
            today = today
        )

        assertEquals(1, stops.size)
        assertEquals(33.7490, stops.first().coordinate.latitude, 0.0001)
        assertEquals(-84.3880, stops.first().coordinate.longitude, 0.0001)
    }

    @Test
    fun buildStopsSkipsLocationsWithoutCoordinates() {
        val stops = LocationStopsBuilder.buildStops(
            tasks = listOf(task(id = "task-1")),
            locationsById = emptyMap(),
            today = LocalDate.of(2026, 7, 3)
        )

        assertTrue(stops.isEmpty())
    }

    @Test
    fun contactDisplayTasksPreferTodayThenNearestFutureDay() {
        val today = LocalDate.of(2026, 7, 9)
        val tasks = listOf(
            task(id = "past", scheduledFor = "2026-07-08"),
            task(id = "today", scheduledFor = "2026-07-09"),
            task(id = "tomorrow", scheduledFor = "2026-07-10"),
            task(id = "later", scheduledFor = "2026-07-11")
        )

        assertEquals(
            listOf("today"),
            LocationStopsBuilder.contactDisplayTasks("location-1", tasks, today = today).map { it.id }
        )
        assertEquals(
            listOf("tomorrow"),
            LocationStopsBuilder.contactDisplayTasks(
                "location-1",
                tasks.filterNot { it.id == "today" },
                today = today
            ).map { it.id }
        )
        assertEquals(
            LocalDate.of(2026, 7, 10),
            LocationStopsBuilder.contactDisplayDate(
                "location-1",
                tasks.filterNot { it.id == "today" },
                today
            )
        )
    }

    @Test
    fun buildStopsDerivesMapCardActionAndCommissionPaymentSummary() {
        val today = LocalDate.of(2026, 7, 3)
        val locations = mapOf(
            "location-1" to location(
                id = "location-1",
                address = address(latitude = 40.7128, longitude = -74.0060)
            )
        )

        val assignedToMe = LocationStopsBuilder.buildStops(
            tasks = listOf(task(id = "mine", assignee = "user-1")),
            locationsById = locations,
            today = today,
            currentUserId = "user-1"
        ).first()
        assertEquals(LocationStopAction.Go, assignedToMe.action)

        val unassigned = LocationStopsBuilder.buildStops(
            tasks = listOf(task(id = "claimable", status = TaskStatus.Unassigned, assignee = null)),
            locationsById = locations,
            today = today,
            currentUserId = "user-1"
        ).first()
        assertEquals(LocationStopAction.ClaimTasks, unassigned.action)

        val assignedToOther = LocationStopsBuilder.buildStops(
            tasks = listOf(
                task(
                    id = "other",
                    assignee = "user-2",
                    assigneeName = "Other Operator",
                    commission = 12.0,
                    commissionPaymentType = CommissionPaymentType.Check
                )
            ),
            locationsById = locations,
            today = today,
            currentUserId = "user-1"
        ).first()
        assertEquals(LocationStopAction.OpenTasks, assignedToOther.action)
        assertEquals("Assignee: Other Operator", assignedToOther.assigneeSummary)
        assertEquals("Check", assignedToOther.commissionPaymentSummary)
    }

    private fun task(
        id: String,
        status: TaskStatus = TaskStatus.Pending,
        type: TaskType = TaskType.MachineRefill,
        machine: String = "machine-1",
        assignee: String? = "user-1",
        assigneeName: String? = "Operator",
        assigneeEmail: String? = "operator@vendistri.com",
        locationAddress: Address? = null,
        scheduledFor: String = "2026-07-03",
        gross: Double? = null,
        commission: Double? = null,
        commissionPaymentType: CommissionPaymentType? = null,
        net: Double? = null
    ): VendiTask {
        return VendiTask(
            id = id,
            type = type,
            status = status,
            isPublic = false,
            assignee = assignee,
            assigneeName = assigneeName,
            assigneeEmail = assigneeEmail,
            machine = machine,
            machineName = "Machine",
            collectionInputMode = CollectionInputMode.Dollars,
            creditsPerDollar = null,
            location = "location-1",
            locationName = "Downtown Office",
            locationAddress = locationAddress,
            scheduledFor = scheduledFor,
            createdAt = "2026-07-03T10:00:00Z",
            startedAt = null,
            doneAt = null,
            isLive = false,
            duration = null,
            notes = null,
            distance = null,
            gross = gross,
            grossCash = null,
            grossCard = null,
            refunds = null,
            commission = commission,
            commissionPaymentType = commissionPaymentType,
            net = net,
            refillTaskId = null,
            refillTaskIds = emptyList(),
            pickupLines = emptyList<TaskPickupLine>()
        )
    }

    private fun location(id: String, address: Address?): AppLocation {
        return AppLocation(
            id = id,
            name = "Downtown Office",
            timeZone = "America/New_York",
            address = address,
            defaultAssigneeId = null,
            discontinued = false
        )
    }

    private fun address(latitude: Double, longitude: Double): Address {
        return Address(
            street = "123 Main St",
            city = "New York",
            state = "NY",
            zipCode = "10001",
            latitude = latitude,
            longitude = longitude
        )
    }
}
