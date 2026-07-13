package com.vendistri.operations.features.work

import com.vendistri.operations.features.location.Address
import com.vendistri.operations.features.location.AppLocation
import com.vendistri.operations.features.location.LocationDayHours
import com.vendistri.operations.features.map.LocationCoordinate
import com.vendistri.operations.features.tasks.CollectionInputMode
import com.vendistri.operations.features.tasks.TaskPickupLine
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.VendiTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class TaskExecutionPlannerTest {
    @Test
    fun buildPlanUsesTodayAssignedAndClaimableTasks() {
        val plan = TaskExecutionPlanner.buildPlan(
            tasks = listOf(
                task(id = "assigned", type = TaskType.MachineCollection, gross = 20.0),
                task(id = "unassigned", status = TaskStatus.Unassigned, assignee = null),
                task(id = "other-user", assignee = "user-2"),
                task(id = "tomorrow", scheduledFor = "2026-07-04")
            ),
            currentUserId = "user-1",
            locationsById = mapOf("location-1" to location()),
            date = LocalDate.parse("2026-07-03")
        )

        assertEquals(listOf("assigned", "unassigned"), plan.tasks.map { it.id })
        assertEquals(1, plan.stops.size)
        assertEquals(2, plan.summary.tasks)
        assertEquals(20.0, plan.summary.gross, 0.0001)
    }

    @Test
    fun buildPlanExcludesClaimableTasksWhenClaimingDisabled() {
        val plan = TaskExecutionPlanner.buildPlan(
            tasks = listOf(
                task(id = "assigned", type = TaskType.MachineCollection),
                task(id = "unassigned", status = TaskStatus.Unassigned, assignee = null)
            ),
            currentUserId = "user-1",
            includeClaimableUnassigned = false,
            locationsById = mapOf("location-1" to location()),
            date = LocalDate.parse("2026-07-03")
        )

        assertEquals(listOf("assigned"), plan.tasks.map { it.id })
    }

    @Test
    fun buildPlanExcludesTasksAlreadyLiveInAnotherWorkflow() {
        val plan = TaskExecutionPlanner.buildPlan(
            tasks = listOf(
                task(id = "available"),
                task(id = "live-assigned").copy(
                    isLive = true,
                    startedAt = "2026-07-03T11:00:00Z"
                ),
                task(
                    id = "live-unassigned",
                    status = TaskStatus.Unassigned,
                    assignee = null
                ).copy(isLive = true)
            ),
            currentUserId = "user-1",
            locationsById = mapOf("location-1" to location()),
            date = LocalDate.parse("2026-07-03")
        )

        assertEquals(listOf("available"), plan.tasks.map { it.id })
    }

    @Test
    fun buildPlanSuggestedStopPrefersOpenOrUnconfiguredLocation() {
        val now = ZonedDateTime.of(2026, 7, 6, 12, 0, 0, 0, ZoneId.of("America/New_York"))
        val plan = TaskExecutionPlanner.buildPlan(
            tasks = listOf(
                task(id = "closed-task", location = "closed-location", locationName = "Closed Location"),
                task(id = "open-task", location = "open-location", locationName = "Open Location")
            ),
            currentUserId = "user-1",
            currentCoordinate = LocationCoordinate(40.0, -74.0),
            locationsById = mapOf(
                "closed-location" to location(
                    id = "closed-location",
                    latitude = 40.01,
                    longitude = -74.01,
                    hours = mapOf("monday" to day("monday", open = "08:00", close = "10:00"))
                ),
                "open-location" to location(
                    id = "open-location",
                    latitude = 41.0,
                    longitude = -75.0,
                    hours = mapOf("monday" to day("monday", open = "08:00", close = "17:00"))
                )
            ),
            date = LocalDate.parse("2026-07-03"),
            availabilityTime = now
        )

        assertEquals("open-location", plan.suggestedStopId)
    }

    @Test
    fun buildStopCreatesLocationNodeForActionableTasks() {
        val stop = TaskExecutionPlanner.buildStop(
            locationId = "location-1",
            tasks = listOf(
                task(id = "pending", type = TaskType.MachineCollection, status = TaskStatus.Pending, gross = 20.0),
                task(id = "done", status = TaskStatus.Done),
                task(id = "other-location", location = "location-2")
            ),
            locationsById = mapOf("location-1" to location())
        )

        assertNotNull(stop)
        requireNotNull(stop)
        assertEquals("location-1", stop.id)
        assertEquals(1, stop.tasks.size)
        assertEquals(1, stop.nodes.size)
        assertEquals(40.7128, stop.coordinate?.latitude ?: 0.0, 0.0001)
        assertEquals(20.0, stop.gross, 0.0001)
    }

    @Test
    fun buildStopReturnsNullWithoutCoordinate() {
        val stop = TaskExecutionPlanner.buildStop(
            locationId = "location-1",
            tasks = listOf(task(id = "pending")),
            locationsById = emptyMap()
        )

        assertNull(stop)
    }

    @Test
    fun activeExecutionUsesFirstActionableTask() {
        val stop = TaskExecutionPlanner.buildStop(
            locationId = "location-1",
            tasks = listOf(
                task(id = "done", status = TaskStatus.Done),
                task(id = "pending", status = TaskStatus.Pending)
            ),
            locationsById = mapOf("location-1" to location())
        )

        val execution = TaskExecutionPlanner.activeExecution(requireNotNull(stop))

        assertEquals("pending", execution.currentTaskId)
        assertEquals(0, execution.currentTaskIndex)
        assertEquals(1, execution.totalTaskCount)
    }

    @Test
    fun scopedStopRecomputesTasksGroupsAndFinancials() {
        val stop = TaskExecutionPlanner.buildStop(
            locationId = "location-1",
            tasks = listOf(
                task(id = "collection", type = TaskType.MachineCollection, gross = 30.0),
                task(id = "refill", type = TaskType.MachineRefill)
            ),
            locationsById = mapOf("location-1" to location())
        )

        val scoped = TaskExecutionPlanner.scopedStop(requireNotNull(stop), setOf("collection"))

        assertNotNull(scoped)
        requireNotNull(scoped)
        assertEquals(listOf("collection"), scoped.tasks.map { it.id })
        assertEquals(listOf("collection"), scoped.nodes.first().taskIds)
        assertEquals(30.0, scoped.gross, 0.0001)
        assertEquals(1, scoped.machineGroups.size)
    }

    private fun task(
        id: String,
        type: TaskType = TaskType.MachineRefill,
        status: TaskStatus = TaskStatus.Pending,
        location: String = "location-1",
        locationName: String = "Downtown Office",
        gross: Double? = null,
        assignee: String? = "user-1",
        scheduledFor: String = "2026-07-03"
    ): VendiTask {
        return VendiTask(
            id = id,
            type = type,
            status = status,
            isPublic = false,
            assignee = assignee,
            assigneeName = "Operator",
            assigneeEmail = "operator@vendistri.com",
            machine = "machine-1",
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
            gross = gross,
            grossCash = null,
            grossCard = null,
            refunds = null,
            commission = null,
            net = null,
            refillTaskId = null,
            refillTaskIds = emptyList(),
            pickupLines = emptyList<TaskPickupLine>()
        )
    }

    private fun location(
        id: String = "location-1",
        latitude: Double = 40.7128,
        longitude: Double = -74.0060,
        hours: Map<String, LocationDayHours>? = null
    ): AppLocation {
        return AppLocation(
            id = id,
            name = "Downtown Office",
            timeZone = "America/New_York",
            address = Address(
                street = "123 Main St",
                city = "New York",
                state = "NY",
                zipCode = "10001",
                latitude = latitude,
                longitude = longitude
            ),
            hours = hours,
            defaultAssigneeId = null,
            discontinued = false
        )
    }

    private fun day(day: String, open: String, close: String): LocationDayHours {
        return LocationDayHours(day = day, closed = false, open = open, close = close)
    }
}
