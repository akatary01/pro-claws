package com.vendistri.operations.features.work

import com.vendistri.operations.features.map.LocationCoordinate
import com.vendistri.operations.features.tasks.CollectionInputMode
import com.vendistri.operations.features.tasks.TaskMachineGroup
import com.vendistri.operations.features.tasks.TaskPickupLine
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.VendiTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WorkFlowStoreTest {
    @Test
    fun routeLifecycleMovesThroughExpectedPhases() {
        val store = WorkFlowStore()

        store.prepareRoute(stopPlan())
        assertEquals(WorkPhase.PreparingRoute, store.state.value.phase)
        assertNotNull(store.state.value.activeSession)
        assertNotNull(store.state.value.activeExecution)
        assertEquals(WorkDestinationKind.Location, store.state.value.activeSession?.destinationKind)
        assertEquals(WorkDestinationKind.Location, store.state.value.activeExecution?.destinationKind)

        store.startNavigation()
        assertEquals(WorkPhase.NavigatingToLocation, store.state.value.phase)

        store.arriveAtLocation()
        assertEquals(WorkPhase.AtLocation, store.state.value.phase)
        assertNotNull(store.state.value.activeExecution)

        store.stopCurrentSession()
        assertEquals(WorkPhase.Idle, store.state.value.phase)
    }

    @Test
    fun rehydrateActiveExecutionAdvancesAfterCurrentTaskDone() {
        val store = WorkFlowStore()
        store.prepareRoute(
            stopPlan(
                listOf(
                    TestWorkTaskFactory.task("task-1", type = TaskType.MachineRefill),
                    TestWorkTaskFactory.task("task-2", type = TaskType.MachineCollection)
                )
            )
        )
        store.arriveAtLocation()
        assertEquals("task-1", store.state.value.activeExecution?.currentTaskId)

        store.rehydrateActiveExecution(
            listOf(
                TestWorkTaskFactory.task("task-1", type = TaskType.MachineRefill, status = TaskStatus.Done),
                TestWorkTaskFactory.task("task-2", type = TaskType.MachineCollection)
            )
        )

        assertEquals("task-2", store.state.value.activeExecution?.currentTaskId)
        assertEquals(2, store.state.value.activeExecution?.totalTaskCount)
    }

    @Test
    fun restoreActiveSessionKeepsPostPickupDestination() {
        val store = WorkFlowStore()
        val destination = PostPickupDestination(
            refillTaskId = "refill-1",
            stopId = "location-1",
            sessionTaskIds = setOf("refill-1", "refill-2")
        )

        store.restoreActiveSession(
            session = ActiveWorkSession(
                id = "warehouse:warehouse-1",
                title = "Warehouse",
                locationId = null,
                activeTaskIds = setOf("pickup-1")
            ),
            postPickupDestination = destination
        )

        assertEquals(WorkPhase.NavigatingToLocation, store.state.value.phase)
        assertEquals(destination, store.state.value.postPickupDestination)
    }

    @Test
    fun restoreActiveSessionKeepsPreparingRouteUnstarted() {
        val store = WorkFlowStore()
        val localSession = LocalActiveExecutionSession(
            deviceId = "device-1",
            userId = "user-1",
            stopId = "location-1",
            locationId = "location-1",
            taskIds = setOf("task-1"),
            currentTaskId = "task-1",
            phase = WorkPhase.PreparingRoute,
            startedAtEpochMillis = 10L,
            distanceMiles = 1.25
        )

        store.restoreActiveSession(
            session = ActiveWorkSession(
                id = "location:location-1",
                title = "Clean Laundry",
                locationId = "location-1",
                activeTaskIds = setOf("task-1"),
                coordinate = LocationCoordinate(40.7128, -74.0060)
            ),
            phase = WorkPhase.PreparingRoute,
            localActiveExecutionSession = localSession
        )

        assertEquals(WorkPhase.PreparingRoute, store.state.value.phase)
        assertEquals(WorkPhase.PreparingRoute, store.state.value.localActiveExecutionSession?.phase)
    }

    @Test
    fun restoreActiveRouteKeepsPreparingRouteUnstarted() {
        val store = WorkFlowStore()
        val session = ActiveWorkSession(
            id = "location:location-1",
            title = "Clean Laundry",
            locationId = "location-1",
            activeTaskIds = setOf("task-1"),
            coordinate = LocationCoordinate(40.7128, -74.0060)
        )
        val localSession = LocalActiveExecutionSession(
            deviceId = "device-1",
            userId = "user-1",
            stopId = "location-1",
            locationId = "location-1",
            taskIds = setOf("task-1"),
            currentTaskId = "task-1",
            phase = WorkPhase.PreparingRoute,
            startedAtEpochMillis = 10L,
            distanceMiles = 1.25
        )
        store.restoreActiveSession(
            session = session,
            phase = WorkPhase.PreparingRoute,
            localActiveExecutionSession = localSession
        )

        store.restoreActiveRoute(session = session, phase = WorkPhase.PreparingRoute, stop = stopPlan())

        assertEquals(WorkPhase.PreparingRoute, store.state.value.phase)
        assertEquals(WorkPhase.PreparingRoute, store.state.value.localActiveExecutionSession?.phase)
        assertEquals(1.25, store.state.value.localActiveExecutionSession?.distanceMiles ?: 0.0, 0.0001)
    }

    @Test
    fun restoreActiveRouteDemotesNavigationWhenRouteOwnerWasNotStarted() {
        val store = WorkFlowStore()
        val session = ActiveWorkSession(
            id = "location:location-1",
            title = "Clean Laundry",
            locationId = "location-1",
            activeTaskIds = setOf("task-1"),
            coordinate = LocationCoordinate(40.7128, -74.0060)
        )

        store.restoreActiveRoute(
            session = session,
            phase = WorkPhase.NavigatingToLocation,
            stop = stopPlan(listOf(TestWorkTaskFactory.task("task-1")))
        )

        assertEquals(WorkPhase.PreparingRoute, store.state.value.phase)
    }

    @Test
    fun restoreActiveRouteKeepsNavigationWhenRouteOwnerWasStarted() {
        val store = WorkFlowStore()
        val session = ActiveWorkSession(
            id = "location:location-1",
            title = "Clean Laundry",
            locationId = "location-1",
            activeTaskIds = setOf("task-1"),
            coordinate = LocationCoordinate(40.7128, -74.0060)
        )

        store.restoreActiveRoute(
            session = session,
            phase = WorkPhase.NavigatingToLocation,
            stop = stopPlan(listOf(TestWorkTaskFactory.task("task-1", startedAt = "2026-07-03T12:34:00Z")))
        )

        assertEquals(WorkPhase.NavigatingToLocation, store.state.value.phase)
    }

    @Test
    fun restoreActiveRouteRebuildsAtLocationExecution() {
        val store = WorkFlowStore()
        val session = ActiveWorkSession(
            id = "location:location-1",
            title = "Downtown Office",
            locationId = "location-1",
            activeTaskIds = setOf("task-1", "task-2"),
            coordinate = LocationCoordinate(40.7128, -74.0060)
        )
        val stop = stopPlan(
            listOf(
                TestWorkTaskFactory.task("task-1", type = TaskType.MachineRefill, status = TaskStatus.Done),
                TestWorkTaskFactory.task("task-2", type = TaskType.MachineCollection)
            )
        )

        store.restoreActiveRoute(session = session, phase = WorkPhase.AtLocation, stop = stop)

        assertEquals(WorkPhase.AtLocation, store.state.value.phase)
        assertEquals("task-2", store.state.value.activeExecution?.currentTaskId)
        assertEquals(2, store.state.value.activeExecution?.totalTaskCount)
    }

    @Test
    fun restoreActiveRouteCarriesLocalDistanceAndBaselines() {
        val store = WorkFlowStore()
        val session = ActiveWorkSession(
            id = "location:location-1",
            title = "Downtown Office",
            locationId = "location-1",
            activeTaskIds = setOf("task-1"),
            coordinate = LocationCoordinate(40.7128, -74.0060)
        )
        val localSession = LocalActiveExecutionSession(
            deviceId = "device-1",
            userId = "user-1",
            stopId = "location-1",
            locationId = "location-1",
            taskIds = setOf("task-1"),
            currentTaskId = "task-1",
            phase = WorkPhase.AtLocation,
            startedAtEpochMillis = 10L,
            distanceMiles = 4.5,
            taskStartDistanceMilesByTaskId = mapOf("task-1" to 2.0)
        )
        store.restoreActiveSession(session = session, phase = WorkPhase.AtLocation, localActiveExecutionSession = localSession)

        store.restoreActiveRoute(session = session, phase = WorkPhase.AtLocation, stop = stopPlan())

        assertEquals(4.5, store.state.value.activeExecution?.distanceMiles ?: 0.0, 0.0001)
        assertEquals(2.0, store.state.value.activeExecution?.taskStartDistanceMilesByTaskId?.get("task-1") ?: 0.0, 0.0001)
    }

    @Test
    fun restoreActiveRoutePrefersLocalCurrentTask() {
        val store = WorkFlowStore()
        val session = ActiveWorkSession(
            id = "location:location-1",
            title = "Downtown Office",
            locationId = "location-1",
            activeTaskIds = setOf("task-1", "task-2"),
            coordinate = LocationCoordinate(40.7128, -74.0060)
        )
        val localSession = LocalActiveExecutionSession(
            deviceId = "device-1",
            userId = "user-1",
            stopId = "location-1",
            locationId = "location-1",
            taskIds = setOf("task-1", "task-2"),
            currentTaskId = "task-2",
            phase = WorkPhase.AtLocation,
            startedAtEpochMillis = 10L
        )
        val stop = stopPlan(
            listOf(
                TestWorkTaskFactory.task("task-1", type = TaskType.MachineRefill),
                TestWorkTaskFactory.task("task-2", type = TaskType.MachineCollection)
            )
        )
        store.restoreActiveSession(session = session, phase = WorkPhase.AtLocation, localActiveExecutionSession = localSession)

        store.restoreActiveRoute(session = session, phase = WorkPhase.AtLocation, stop = stop)

        assertEquals("task-2", store.state.value.activeExecution?.currentTaskId)
    }

    @Test
    fun newSameMachineTaskAfterRouteStartDoesNotEnterActiveExecution() {
        val store = WorkFlowStore()
        val includedTask = TestWorkTaskFactory.task("task-1", type = TaskType.MachineRefill)
        val laterCreatedTask = TestWorkTaskFactory.task("task-extra", type = TaskType.MachineCollection)
        store.prepareRoute(stopPlan(listOf(includedTask)))
        store.startNavigation()
        store.arriveAtLocation()

        store.rehydrateActiveExecution(listOf(includedTask, laterCreatedTask))

        assertEquals(setOf("task-1"), store.state.value.activeSession?.activeTaskIds)
        assertEquals(listOf("task-1"), store.state.value.activeExecution?.displayTasks?.map { it.id })
        assertEquals(1, store.state.value.activeExecution?.totalTaskCount)
    }

    @Test
    fun restoreFromSavedSessionExcludesNewMatchingTask() {
        val session = ActiveWorkSession(
            id = "location:location-1",
            title = "Downtown Office",
            locationId = "location-1",
            activeTaskIds = setOf("task-1"),
            coordinate = LocationCoordinate(40.7128, -74.0060)
        )
        val includedTask = TestWorkTaskFactory.task("task-1", type = TaskType.MachineRefill)
        val laterCreatedTask = TestWorkTaskFactory.task("task-extra", type = TaskType.MachineCollection)

        val restoredStop = TaskExecutionPlanner.buildStopFromSession(
            session = session,
            tasks = listOf(includedTask, laterCreatedTask)
        )

        assertEquals(listOf("task-1"), restoredStop?.tasks?.map { it.id })
        assertEquals(listOf("task-1"), restoredStop?.nodes?.flatMap { it.taskIds })
    }

    @Test
    fun realtimeRehydrateUpdatesIncludedTasksOnly() {
        val store = WorkFlowStore()
        val includedTask = TestWorkTaskFactory.task("task-1", type = TaskType.MachineRefill)
        store.prepareRoute(stopPlan(listOf(includedTask)))
        store.arriveAtLocation()

        val updatedIncludedTask = TestWorkTaskFactory.task(
            "task-1",
            type = TaskType.MachineRefill,
            status = TaskStatus.Done
        )
        val laterCreatedTask = TestWorkTaskFactory.task("task-extra", type = TaskType.MachineCollection)
        store.rehydrateActiveExecution(listOf(updatedIncludedTask, laterCreatedTask))

        assertEquals(listOf("task-1"), store.state.value.activeExecution?.displayTasks?.map { it.id })
        assertEquals(listOf(TaskStatus.Done), store.state.value.activeExecution?.displayTasks?.map { it.status })
        assertEquals(null, store.state.value.activeExecution?.currentTaskId)
    }

    @Test
    fun rehydratePreservesTaskOrderInsideMachineAfterStatusChange() {
        val store = WorkFlowStore()
        val refill = TestWorkTaskFactory.task("refill", type = TaskType.MachineRefill)
        val collection = TestWorkTaskFactory.task("collection", type = TaskType.MachineCollection)
        store.prepareRoute(stopPlan(listOf(refill, collection)))
        store.arriveAtLocation()

        store.rehydrateActiveExecution(
            listOf(
                refill.copy(status = TaskStatus.Done),
                collection
            )
        )

        assertEquals(listOf("refill", "collection"), store.state.value.activeExecution?.displayTasks?.map { it.id })
        assertEquals(
            listOf("refill", "collection"),
            store.state.value.activeExecution?.machineGroups?.firstOrNull()?.tasks?.map { it.id }
        )
    }

    @Test
    fun rehydrateKeepsCurrentMachineUntilExecutableTasksAreFinal() {
        val store = WorkFlowStore()
        val machineOneRefill = TestWorkTaskFactory.task("machine-1-refill", type = TaskType.MachineRefill, machine = "machine-1")
        val machineOneCollection = TestWorkTaskFactory.task("machine-1-collection", type = TaskType.MachineCollection, machine = "machine-1")
        val machineTwoRefill = TestWorkTaskFactory.task("machine-2-refill", type = TaskType.MachineRefill, machine = "machine-2")
        store.prepareRoute(stopPlan(listOf(machineOneRefill, machineOneCollection, machineTwoRefill)))
        store.arriveAtLocation()
        assertEquals("machine-1-refill", store.state.value.activeExecution?.currentTaskId)

        store.rehydrateActiveExecution(
            listOf(
                machineOneRefill.copy(status = TaskStatus.Done),
                machineOneCollection,
                machineTwoRefill
            )
        )

        assertEquals("machine-1-collection", store.state.value.activeExecution?.currentTaskId)
        assertEquals(listOf("machine-1", "machine-2"), store.state.value.activeExecution?.machineGroups?.map { it.id })
    }

    @Test
    fun rehydrateMovesToNextMachineOnlyAfterCurrentMachineIsFinal() {
        val store = WorkFlowStore()
        val machineOneRefill = TestWorkTaskFactory.task("machine-1-refill", type = TaskType.MachineRefill, machine = "machine-1")
        val machineOneCollection = TestWorkTaskFactory.task("machine-1-collection", type = TaskType.MachineCollection, machine = "machine-1")
        val machineTwoRefill = TestWorkTaskFactory.task("machine-2-refill", type = TaskType.MachineRefill, machine = "machine-2")
        store.prepareRoute(stopPlan(listOf(machineOneRefill, machineOneCollection, machineTwoRefill)))
        store.arriveAtLocation()

        store.rehydrateActiveExecution(
            listOf(
                machineOneRefill.copy(status = TaskStatus.Done),
                machineOneCollection.copy(status = TaskStatus.Done),
                machineTwoRefill
            )
        )

        assertEquals("machine-2-refill", store.state.value.activeExecution?.currentTaskId)
        assertEquals(
            listOf("machine-2-refill"),
            store.currentMachineStartTasks().map { it.id }
        )
        assertEquals(listOf("machine-1", "machine-2"), store.state.value.activeExecution?.machineGroups?.map { it.id })
        assertEquals(
            listOf("machine-1-refill", "machine-1-collection", "machine-2-refill"),
            store.state.value.activeExecution?.displayTasks?.map { it.id }
        )

        store.rehydrateActiveExecution(
            listOf(
                machineOneRefill.copy(status = TaskStatus.Done),
                machineOneCollection.copy(status = TaskStatus.Done),
                machineTwoRefill.copy(status = TaskStatus.Done)
            )
        )

        assertEquals(null, store.state.value.activeExecution?.currentTaskId)
        assertEquals(listOf("machine-1", "machine-2"), store.state.value.activeExecution?.machineGroups?.map { it.id })
        assertEquals(
            listOf("machine-1-refill", "machine-1-collection", "machine-2-refill"),
            store.state.value.activeExecution?.displayTasks?.map { it.id }
        )
    }

    @Test
    fun rehydratePreservesWrapperBaselinesAndRouteScope() {
        val store = WorkFlowStore()
        val service = TestWorkTaskFactory.task("service", type = TaskType.MachineService)
        val refill = TestWorkTaskFactory.task("refill", type = TaskType.MachineRefill, serviceTaskId = "service")
        val extra = TestWorkTaskFactory.task("extra", type = TaskType.MachineCollection)
        store.prepareRoute(stopPlan(listOf(service, refill)))
        store.arriveAtLocation(distanceMiles = 1.5)
        store.recordTaskStartBaselines(listOf("service"), baselineDistanceMiles = 0.0)

        store.rehydrateActiveExecution(
            listOf(
                service.copy(startedAt = "2026-07-03T11:00:00Z"),
                refill.copy(startedAt = "2026-07-03T11:05:00Z"),
                extra
            )
        )

        assertEquals(setOf("service", "refill"), store.state.value.activeSession?.activeTaskIds)
        assertEquals(listOf("service", "refill"), store.state.value.activeExecution?.displayTasks?.map { it.id })
        assertEquals("service", store.state.value.activeExecution?.wrapperTaskId)
        assertEquals(0.0, store.state.value.activeExecution?.taskStartDistanceMilesByTaskId?.get("service") ?: -1.0, 0.0001)
        assertEquals(1.5, store.state.value.activeExecution?.taskStartDistanceMilesByTaskId?.get("refill") ?: 0.0, 0.0001)
    }

    @Test
    fun arriveAtLocationCapturesDistanceAndCurrentTaskBaseline() {
        val store = WorkFlowStore()
        store.prepareRoute(stopPlan())

        store.arriveAtLocation(distanceMiles = 3.4)

        val execution = store.state.value.activeExecution
        assertEquals(3.4, execution?.distanceMiles ?: 0.0, 0.0001)
        assertEquals(3.4, execution?.taskStartDistanceMilesByTaskId?.get("task-1") ?: 0.0, 0.0001)
    }

    @Test
    fun distanceSnapshotOnlyMovesForwardAndFinalDistanceUsesBaseline() {
        val store = WorkFlowStore()
        val task = TestWorkTaskFactory.task("task-1", type = TaskType.MachineRefill)
        val startedTask = task.copy(startedAt = "2026-07-03T11:00:00Z")
        store.prepareRoute(stopPlan(listOf(task)))
        store.arriveAtLocation(distanceMiles = 2.0)

        store.recordActiveExecutionDistanceSnapshot(1.0)
        assertEquals(2.0, store.state.value.activeExecution?.distanceMiles ?: 0.0, 0.0001)

        store.recordActiveExecutionDistanceSnapshot(2.75)

        assertEquals(2.75, store.state.value.activeExecution?.distanceMiles ?: 0.0, 0.0001)
        assertEquals(0.75, store.distanceToSendForTask(startedTask, TaskStatus.Done) ?: 0.0, 0.0001)
        assertEquals(null, store.distanceToSendForTask(startedTask, TaskStatus.Pending))
    }

    @Test
    fun distanceToSendUsesServiceWrapperAsDistanceOwner() {
        val store = WorkFlowStore()
        val service = TestWorkTaskFactory.task(
            "service",
            type = TaskType.MachineService,
            startedAt = "2026-07-03T11:00:00Z"
        )
        val refill = TestWorkTaskFactory.task(
            "refill",
            type = TaskType.MachineRefill,
            startedAt = "2026-07-03T11:00:00Z",
            serviceTaskId = "service"
        )
        store.prepareRoute(stopPlan(listOf(service, refill)))
        store.arriveAtLocation(distanceMiles = 1.0)
        store.recordActiveExecutionDistanceSnapshot(3.5)

        assertEquals(2.5, store.distanceToSendForTask(service, TaskStatus.Done) ?: 0.0, 0.0001)
        assertEquals(null, store.distanceToSendForTask(refill, TaskStatus.Done))
    }

    @Test
    fun currentMachineStartTasksReturnsUnstartedCurrentMachineTasks() {
        val store = WorkFlowStore()
        val refill = TestWorkTaskFactory.task("task-1", type = TaskType.MachineRefill)
        val collection = TestWorkTaskFactory.task("task-2", type = TaskType.MachineCollection)
        store.prepareRoute(stopPlan(listOf(refill, collection)))

        assertEquals(emptyList<String>(), store.currentMachineStartTasks().map { it.id })

        store.startNavigation()
        assertEquals(emptyList<String>(), store.currentMachineStartTasks().map { it.id })

        store.arriveAtLocation()
        assertEquals(listOf("task-1"), store.currentMachineStartTasks().map { it.id })

        store.rehydrateActiveExecution(
            listOf(
                TestWorkTaskFactory.task("task-1", type = TaskType.MachineRefill, startedAt = "2026-07-03T11:00:00Z"),
                collection
            )
        )

        assertEquals(emptyList<String>(), store.currentMachineStartTasks().map { it.id })
    }

    @Test
    fun currentRouteStartTasksReturnsServiceWrapperUntilStarted() {
        val store = WorkFlowStore()
        val service = TestWorkTaskFactory.task("service", type = TaskType.MachineService)
        val refill = TestWorkTaskFactory.task(
            "refill",
            type = TaskType.MachineRefill,
            serviceTaskId = "service"
        )
        store.prepareRoute(stopPlan(listOf(service, refill)))

        assertEquals(listOf("service"), store.currentRouteStartTasks().map { it.id })

        store.startNavigation()
        assertEquals(listOf("service"), store.currentRouteStartTasks().map { it.id })

        store.rehydrateActiveExecution(
            listOf(
                service.copy(startedAt = "2026-07-08T18:00:00Z"),
                refill
            )
        )
        assertEquals(emptyList<String>(), store.currentRouteStartTasks().map { it.id })
    }

    @Test
    fun arriveAtLocationPreservesRouteOwnerBaselineAndAddsChildBaseline() {
        val store = WorkFlowStore()
        val service = TestWorkTaskFactory.task(
            "service",
            type = TaskType.MachineService,
            startedAt = "2026-07-03T11:00:00Z"
        )
        val refill = TestWorkTaskFactory.task(
            "refill",
            type = TaskType.MachineRefill,
            serviceTaskId = "service"
        )
        store.prepareRoute(stopPlan(listOf(service, refill)))
        store.recordTaskStartBaselines(listOf("service"), baselineDistanceMiles = 0.0)

        store.arriveAtLocation(distanceMiles = 2.75)

        val baselines = store.state.value.activeExecution?.taskStartDistanceMilesByTaskId.orEmpty()
        assertEquals(0.0, baselines["service"] ?: -1.0, 0.0001)
        assertEquals(2.75, baselines["refill"] ?: 0.0, 0.0001)
    }

    @Test
    fun prepareWarehouseRouteUsesWarehouseDestinationKind() {
        val store = WorkFlowStore()

        store.prepareRoute(
            stopPlan(
                tasks = listOf(TestWorkTaskFactory.task("pickup-1", type = TaskType.MachinePickupInventory)),
                nodeType = GoNodeType.Pickup
            )
        )

        assertEquals(WorkDestinationKind.Warehouse, store.state.value.activeSession?.destinationKind)
        assertEquals(WorkDestinationKind.Warehouse, store.state.value.activeExecution?.destinationKind)

        store.startNavigation()
        assertEquals(WorkPhase.NavigatingToWarehouse, store.state.value.phase)

        store.arriveAtLocation()
        assertEquals(WorkPhase.AtWarehouse, store.state.value.phase)
    }

    @Test
    fun completedWarehousePickupStaysAtWarehouseUntilFinished() {
        val store = WorkFlowStore()
        val pickup = TestWorkTaskFactory.task("pickup-1", type = TaskType.MachinePickupInventory)
        store.prepareRoute(stopPlan(tasks = listOf(pickup), nodeType = GoNodeType.Pickup))
        store.startNavigation()
        store.arriveAtLocation()

        store.rehydrateActiveExecution(listOf(pickup.copy(status = TaskStatus.Done)))

        assertEquals(WorkPhase.AtWarehouse, store.state.value.phase)
        assertEquals(null, store.state.value.activeExecution?.currentTaskId)
        assertEquals(true, store.state.value.activeExecution?.let(TaskExecutionDisplay::canFinishVisit))
    }

    @Test
    fun rehydratePreservesLocallyActiveWarehousePickupWhenRemoteCopyIsFinal() {
        val store = WorkFlowStore()
        val pickup = TestWorkTaskFactory.task("pickup-1", type = TaskType.MachinePickupInventory)
        store.prepareRoute(stopPlan(tasks = listOf(pickup), nodeType = GoNodeType.Pickup))
        store.startNavigation()
        store.arriveAtLocation()
        store.bindLocalActiveExecutionSession(deviceId = "device-1", userId = "user-1")

        store.rehydrateActiveExecution(
            listOf(
                pickup.copy(
                    status = TaskStatus.Cancelled,
                    startedAt = "2026-07-03T10:00:00Z",
                    doneAt = "2026-07-03T15:00:00Z",
                    duration = 18_000.0,
                    distance = 7.7
                )
            )
        )

        val execution = store.state.value.activeExecution
        val currentTask = execution?.displayTasks?.firstOrNull { it.id == "pickup-1" }
        assertEquals("pickup-1", execution?.currentTaskId)
        assertEquals(TaskStatus.Pending, currentTask?.status)
        assertEquals(null, currentTask?.startedAt)
        assertEquals(null, currentTask?.duration)
    }

    @Test
    fun warehouseArrivalKeepsPickupDistanceBaselineAtRouteStart() {
        val store = WorkFlowStore()
        val pickup = TestWorkTaskFactory.task("pickup-1", type = TaskType.MachinePickupInventory)
        store.prepareRoute(stopPlan(tasks = listOf(pickup), nodeType = GoNodeType.Pickup))
        store.startNavigation()

        store.arriveAtLocation(distanceMiles = 0.7)

        assertEquals(0.0, store.state.value.activeExecution?.taskStartDistanceMilesByTaskId?.get("pickup-1") ?: -1.0, 0.0001)
        assertEquals(0.7, store.distanceToSendForTask(pickup.copy(startedAt = "2026-07-03T11:00:00Z"), TaskStatus.Done) ?: 0.0, 0.0001)
    }

    @Test
    fun preparingPostPickupRouteStartsFreshDistanceExecution() {
        val store = WorkFlowStore()
        val pickup = TestWorkTaskFactory.task("pickup-1", type = TaskType.MachinePickupInventory)
        store.prepareRoute(stopPlan(tasks = listOf(pickup), nodeType = GoNodeType.Pickup))
        store.recordTaskStartBaselines(listOf(pickup.id), baselineDistanceMiles = 0.0)
        store.bindLocalActiveExecutionSession(deviceId = "device-1", userId = "user-1")
        store.startNavigation()
        store.recordActiveExecutionDistanceSnapshot(4.25)
        store.arriveAtLocation(distanceMiles = 4.25)

        val refill = TestWorkTaskFactory.task("refill-1", type = TaskType.MachineRefill)
        store.prepareRoute(stopPlan(tasks = listOf(refill)))

        val nextExecution = requireNotNull(store.state.value.activeExecution)
        assertEquals(0.0, nextExecution.distanceMiles, 0.0001)
        assertEquals(emptyMap<String, Double>(), nextExecution.taskStartDistanceMilesByTaskId)
        assertEquals(null, store.state.value.localActiveExecutionSession)

        store.recordTaskStartBaselines(listOf(refill.id), baselineDistanceMiles = 0.0)
        assertEquals(
            0.0,
            store.state.value.activeExecution?.taskStartDistanceMilesByTaskId?.get(refill.id) ?: -1.0,
            0.0001
        )
    }

    @Test
    fun warehouseArrivalUsesActualExecutionDistanceWithoutPlannerDistance() {
        val store = WorkFlowStore()
        val pickup = TestWorkTaskFactory.task(
            "pickup-1",
            type = TaskType.MachinePickupInventory
        ).copy(distance = 35.4)
        store.prepareRoute(stopPlan(tasks = listOf(pickup), nodeType = GoNodeType.Pickup))
        store.recordTaskStartBaselines(listOf(pickup.id), baselineDistanceMiles = 0.0)
        store.recordActiveExecutionDistanceSnapshot(0.7)

        store.arriveAtLocation(distanceMiles = 0.7)

        assertEquals(0.7, store.state.value.activeExecution?.distanceMiles ?: 0.0, 0.0001)
    }

    @Test
    fun warehouseArrivalMetricsKeepRouteOwnerDistanceAfterStartBaselineRefresh() {
        val store = WorkFlowStore()
        val pickup = TestWorkTaskFactory.task("pickup-1", type = TaskType.MachinePickupInventory)
        store.prepareRoute(stopPlan(tasks = listOf(pickup), nodeType = GoNodeType.Pickup))
        store.recordTaskStartBaselines(listOf("pickup-1"), baselineDistanceMiles = 0.0)
        store.startNavigation()

        store.arriveAtLocation(distanceMiles = 0.7)
        store.recordTaskStartBaselines(listOf("pickup-1"))
        store.rehydrateActiveExecution(listOf(pickup.copy(startedAt = "2026-07-03T11:00:00Z")))

        val execution = requireNotNull(store.state.value.activeExecution)
        val scope = ExecutionScopeResolver.resolve(
            execution = execution,
            allTasks = execution.displayTasks,
            nowEpochMillis = java.time.Instant.parse("2026-07-03T11:05:00Z").toEpochMilli()
        )

        assertEquals(0.0, execution.taskStartDistanceMilesByTaskId["pickup-1"] ?: -1.0, 0.0001)
        assertEquals(0.7, scope.totalMetrics.distanceMiles, 0.0001)
        assertEquals(0.7, scope.machineSections.first().machineMetrics.distanceMiles, 0.0001)
        assertEquals(0.7, scope.machineSections.first().childCards.first().metrics.distanceMiles, 0.0001)
    }

    @Test
    fun recordTaskStartBaselinesStoresExecutionDistanceForStartedTasks() {
        val store = WorkFlowStore()
        store.prepareRoute(stopPlan(listOf(TestWorkTaskFactory.task("task-1", type = TaskType.MachineRefill))))
        store.arriveAtLocation(distanceMiles = 1.25)
        store.recordActiveExecutionDistanceSnapshot(2.5)

        store.recordTaskStartBaselines(listOf("task-1"))

        assertEquals(2.5, store.state.value.activeExecution?.taskStartDistanceMilesByTaskId?.get("task-1") ?: 0.0, 0.0001)
    }

    @Test
    fun remainingTasksForCurrentStopReturnsNonFinalExecutionTasks() {
        val store = WorkFlowStore()
        store.prepareRoute(
            stopPlan(
                listOf(
                    TestWorkTaskFactory.task("task-1", type = TaskType.MachineRefill, status = TaskStatus.Done),
                    TestWorkTaskFactory.task("task-2", type = TaskType.MachineCollection),
                    TestWorkTaskFactory.task("task-3", type = TaskType.MachineClean, status = TaskStatus.Cancelled)
                )
            )
        )
        store.arriveAtLocation()

        assertEquals(listOf("task-2"), store.remainingTasksForCurrentStop().map { it.id })
    }

    @Test
    fun bindLocalActiveExecutionSessionStoresDeviceUserAndTaskScope() {
        val store = WorkFlowStore()
        store.prepareRoute(stopPlan())

        store.bindLocalActiveExecutionSession(deviceId = "device-1", userId = "user-1")

        val localSession = store.state.value.localActiveExecutionSession
        assertNotNull(localSession)
        assertEquals("device-1", localSession?.deviceId)
        assertEquals("user-1", localSession?.userId)
        assertEquals("location-1", localSession?.stopId)
        assertEquals(setOf("task-1"), localSession?.taskIds)
        assertEquals(WorkPhase.PreparingRoute, localSession?.phase)
    }

    @Test
    fun bindLocalActiveExecutionSessionStoresDistanceAndBaselines() {
        val store = WorkFlowStore()
        store.prepareRoute(stopPlan())
        store.arriveAtLocation(distanceMiles = 1.0)
        store.recordActiveExecutionDistanceSnapshot(3.0)
        store.recordTaskStartBaselines(listOf("task-1"))

        store.bindLocalActiveExecutionSession(deviceId = "device-1", userId = "user-1")

        val localSession = store.state.value.localActiveExecutionSession
        assertEquals(3.0, localSession?.distanceMiles ?: 0.0, 0.0001)
        assertEquals(3.0, localSession?.taskStartDistanceMilesByTaskId?.get("task-1") ?: 0.0, 0.0001)
    }

    @Test
    fun distanceSnapshotUpdatesBoundLocalActiveExecutionSession() {
        val store = WorkFlowStore()
        store.prepareRoute(stopPlan())
        store.bindLocalActiveExecutionSession(deviceId = "device-1", userId = "user-1")

        store.recordActiveExecutionDistanceSnapshot(0.3)

        assertEquals(0.3, store.state.value.activeExecution?.distanceMiles ?: 0.0, 0.0001)
        assertEquals(0.3, store.state.value.localActiveExecutionSession?.distanceMiles ?: 0.0, 0.0001)
    }

    @Test
    fun showSummarySelectsStopAndClearsScopeDecisionOnStopChange() {
        val store = WorkFlowStore()
        val plan = GoPlan(
            generatedAtEpochMillis = 1L,
            tasks = listOf(TestWorkTaskFactory.task("task-1")),
            stops = listOf(stopPlan(), stopPlan(listOf(TestWorkTaskFactory.task("task-2"))).copy(id = "location-2")),
            suggestedStopId = "location-1"
        )
        store.showSummary(plan)

        assertEquals(WorkPhase.Summary, store.state.value.phase)
        assertEquals("location-1", store.state.value.selectedStopId)

        store.showRouteStartScopeDecision(scopeDecision())
        assertNotNull(store.state.value.routeStartScopeDecision)

        store.selectStop("location-2")

        assertEquals("location-2", store.state.value.selectedStopId)
        assertEquals(null, store.state.value.routeStartScopeDecision)
    }

    @Test
    fun summaryAndStopSelectionClearStaleRoutePreview() {
        val store = WorkFlowStore()
        val plan = GoPlan(
            generatedAtEpochMillis = 1L,
            tasks = listOf(TestWorkTaskFactory.task("task-1")),
            stops = listOf(stopPlan(), stopPlan(listOf(TestWorkTaskFactory.task("task-2"))).copy(id = "location-2")),
            suggestedStopId = "location-1"
        )

        store.showSummary(plan)
        store.setRoutePreview(RoutePreview(distanceMiles = 4.0, expectedTravelSeconds = 480.0))
        store.selectStop("location-2")

        assertEquals(null, store.state.value.routePreview)

        store.setRoutePreview(RoutePreview(distanceMiles = 4.0, expectedTravelSeconds = 480.0))
        store.showSummary(plan)

        assertEquals(null, store.state.value.routePreview)
    }

    private fun stopPlan(
        tasks: List<VendiTask> = listOf(TestWorkTaskFactory.task("task-1")),
        nodeType: GoNodeType = GoNodeType.Location
    ): GoStopPlan {
        return GoStopPlan(
            id = if (nodeType == GoNodeType.Pickup) "warehouse:warehouse-1" else "location-1",
            targetLocationId = if (nodeType == GoNodeType.Pickup) "warehouse-1" else "location-1",
            title = if (nodeType == GoNodeType.Pickup) "Main Warehouse" else "Downtown Office",
            addressStreetLine = "123 Main St",
            addressCityStateZipLine = "New York, NY 10001",
            tasks = tasks,
            nodes = listOf(
                GoNode(
                    id = if (nodeType == GoNodeType.Pickup) "warehouse-warehouse-1" else "location-location-1",
                    type = nodeType,
                    title = if (nodeType == GoNodeType.Pickup) "Main Warehouse" else "Downtown Office",
                    subtitle = "123 Main St",
                    coordinate = LocationCoordinate(40.7128, -74.0060),
                    locationId = if (nodeType == GoNodeType.Pickup) null else "location-1",
                    taskIds = tasks.map { it.id }
                )
            ),
            machineGroups = listOf(
                TaskMachineGroup(
                    id = "machine-1",
                    name = "Machine",
                    tasks = emptyList(),
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
}

private fun scopeDecision(): RouteStartScopeDecision {
    val selected = RouteStartScopeOption(
        choice = RouteStartScopeChoice.SelectedMachine,
        title = "Start assigned tasks only",
        subtitle = "1 task for Machine",
        taskIds = setOf("task-1"),
        claimTaskIds = emptySet()
    )
    val full = RouteStartScopeOption(
        choice = RouteStartScopeChoice.FullStop,
        title = "Start all tasks",
        subtitle = "1 task for Machine",
        taskIds = setOf("task-1"),
        claimTaskIds = emptySet()
    )
    return RouteStartScopeDecision(
        stopTitle = "Downtown Office",
        selectedMachineOption = selected,
        fullStopOption = full,
        defaultChoice = RouteStartScopeChoice.FullStop,
        requiresChoice = false,
        requiresConfirmation = false
    )
}

private object TestWorkTaskFactory {
    fun task(
        id: String,
        type: TaskType = TaskType.MachineRefill,
        status: TaskStatus = TaskStatus.Pending,
        startedAt: String? = null,
        serviceTaskId: String? = null,
        machine: String = "machine-1"
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
            machineName = machine.replaceFirstChar { it.uppercase() },
            collectionInputMode = CollectionInputMode.Dollars,
            creditsPerDollar = null,
            location = "location-1",
            locationName = "Downtown Office",
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
            refillTaskIds = emptyList(),
            pickupLines = emptyList<TaskPickupLine>()
        )
    }
}
