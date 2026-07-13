package com.vendistri.operations.features.live_status

import com.vendistri.operations.features.navigation.NavigationSessionState
import com.vendistri.operations.features.settings.TimeFormatPreference
import com.vendistri.operations.features.tasks.CollectionInputMode
import com.vendistri.operations.features.tasks.TaskMachineGroup
import com.vendistri.operations.features.tasks.TaskPickupLine
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.VendiTask
import com.vendistri.operations.features.work.ActiveTaskExecution
import com.vendistri.operations.features.work.ActiveWorkSession
import com.vendistri.operations.features.work.WorkDestinationKind
import com.vendistri.operations.features.work.WorkPhase
import com.vendistri.operations.features.work.WorkUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class LiveStatusProjectorTest {
    @Test
    fun navigationIncludesInstructionDestinationEtaAndDistance() {
        val snapshot = LiveStatusProjector.project(
            work = workState(phase = WorkPhase.NavigatingToLocation),
            navigation = NavigationSessionState(
                activeStopId = "stop-1",
                isNavigating = true,
                currentInstructionText = "Turn right on Bay Parkway",
                distanceRemainingMiles = 1.2,
                durationRemainingSeconds = 360.0
            ),
            timeFormatPreference = TimeFormatPreference.TwelveHour,
            systemUses24Hour = false
        )

        assertNotNull(snapshot)
        assertEquals(LiveStatusMode.Navigating, snapshot?.mode)
        assertEquals("Turn right on Bay Parkway", snapshot?.title)
        assertEquals("Tino's Deli", snapshot?.destination)
        assertEquals("1.2 mi", snapshot?.distanceRemainingText)
        assertFalse(snapshot?.etaText.isNullOrBlank())
    }

    @Test
    fun workProgressCountsDoneCancelledAndErrorAsCompleted() {
        val tasks = listOf(
            task("done", TaskStatus.Done),
            task("cancelled", TaskStatus.Cancelled),
            task("error", TaskStatus.Error),
            task("pending", TaskStatus.Pending)
        )
        val execution = execution(tasks = tasks, currentTaskId = "pending")
        val snapshot = LiveStatusProjector.project(
            work = workState(phase = WorkPhase.AtLocation, execution = execution),
            navigation = NavigationSessionState(),
            timeFormatPreference = TimeFormatPreference.TwelveHour
        )

        assertEquals(3, snapshot?.progressCurrent)
        assertEquals(4, snapshot?.progressTotal)
        assertEquals("At location • 3/4 tasks completed", snapshot?.primaryStatus)
        assertEquals("Clean", snapshot?.title)
    }

    @Test
    fun codecRoundTripPreservesVendistriOwnedSessionIdentity() {
        val original = LiveStatusSnapshot(
            sessionId = "session-1",
            stopId = "stop-1",
            mode = LiveStatusMode.Rerouting,
            title = "Rerouting",
            destination = "Tino's Deli",
            primaryStatus = "Updating your route",
            isRerouting = true
        )

        assertEquals(original, LiveStatusSnapshotCodec.decode(LiveStatusSnapshotCodec.encode(original)))
    }

    private fun workState(
        phase: WorkPhase,
        execution: ActiveTaskExecution? = null
    ) = WorkUiState(
        phase = phase,
        activeSession = ActiveWorkSession(
            id = "session-1",
            title = "Tino's Deli",
            locationId = "location-1",
            activeTaskIds = execution?.taskIds?.toSet().orEmpty(),
            addressStreetLine = "7120 Bay Parkway",
            addressCityStateZipLine = "Brooklyn, NY 11204"
        ),
        activeExecution = execution
    )

    private fun execution(tasks: List<VendiTask>, currentTaskId: String) = ActiveTaskExecution(
        stopId = "stop-1",
        title = "Tino's Deli",
        locationId = "location-1",
        destinationKind = WorkDestinationKind.Location,
        taskIds = tasks.map { it.id },
        wrapperTaskId = null,
        displayTasks = tasks,
        tasks = emptyList(),
        machineGroups = listOf(
            TaskMachineGroup("machine-1", "Machine", tasks, 0.0, 0.0)
        ),
        currentTaskId = currentTaskId,
        currentTaskIndex = 3,
        totalTaskCount = 4,
        gross = 0.0,
        refunds = 0.0,
        commission = 0.0,
        net = 0.0
    )

    private fun task(id: String, status: TaskStatus) = VendiTask(
        id = id,
        type = TaskType.MachineClean,
        status = status,
        isPublic = false,
        assignee = "user-1",
        assigneeName = "Operator",
        assigneeEmail = null,
        machine = "machine-1",
        machineName = "Machine",
        collectionInputMode = CollectionInputMode.Dollars,
        creditsPerDollar = null,
        location = "location-1",
        locationName = "Tino's Deli",
        locationAddress = null,
        scheduledFor = "2026-07-10",
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
        pickupLines = emptyList<TaskPickupLine>()
    )
}
