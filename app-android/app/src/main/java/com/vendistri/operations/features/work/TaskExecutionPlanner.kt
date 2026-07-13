package com.vendistri.operations.features.work

import com.vendistri.operations.features.location.Address
import com.vendistri.operations.features.location.AppLocation
import com.vendistri.operations.features.location.LocationHours
import com.vendistri.operations.features.map.LocationCoordinate
import com.vendistri.operations.features.tasks.TaskFinancialHelpers
import com.vendistri.operations.features.tasks.TaskLiveTargetResolver
import com.vendistri.operations.features.tasks.TaskScheduleDate
import com.vendistri.operations.features.tasks.TaskStateHelpers
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.VendiTask
import com.vendistri.operations.utils.AddressFormatter
import java.time.LocalDate
import java.time.ZonedDateTime

object TaskExecutionPlanner {
    fun buildPlan(
        tasks: List<VendiTask>,
        currentUserId: String?,
        includeClaimableUnassigned: Boolean = true,
        currentCoordinate: LocationCoordinate? = null,
        locationsById: Map<String, AppLocation> = emptyMap(),
        date: LocalDate = LocalDate.now(),
        availabilityTime: ZonedDateTime = ZonedDateTime.now()
    ): GoPlan {
        // A live task belongs to an already-running workflow. Its entire linked
        // work scope must stay out of a new route, regardless of which device is
        // displaying the summary. Same-device continuation is handled by restore.
        val liveTaskIds = TaskLiveTargetResolver.effectiveLiveTaskIds(
            scopedTasks = tasks,
            allTasks = tasks
        )
        val actionableTasks = tasks.filter {
            it.id !in liveTaskIds &&
                TaskScheduleDate.isSameDay(it.scheduledFor, date) &&
                (
                    (it.status == TaskStatus.Pending && it.assignee == currentUserId) ||
                        (includeClaimableUnassigned && it.status == TaskStatus.Unassigned)
                    )
        }
        val stops = actionableTasks
            .groupBy { stopKey(it) ?: "unknown" }
            .mapNotNull { (stopId, stopTasks) ->
                buildStopPlan(locationId = stopId, tasks = stopTasks, locationsById = locationsById)
            }
            .let { StopRouteOptimizer.orderStops(currentCoordinate = currentCoordinate, stops = it) }
        return GoPlan(
            generatedAtEpochMillis = System.currentTimeMillis(),
            tasks = actionableTasks,
            stops = stops,
            suggestedStopId = suggestedStopId(
                stops = stops,
                locationsById = locationsById,
                currentCoordinate = currentCoordinate,
                availabilityTime = availabilityTime
            )
        )
    }

    fun buildStop(
        locationId: String,
        tasks: List<VendiTask>,
        locationsById: Map<String, AppLocation> = emptyMap()
    ): GoStopPlan? {
        return buildStopPlan(
            locationId = locationId,
            tasks = tasks
                .filter { it.location == locationId }
                .filter { TaskStateHelpers.isActionable(it.status) },
            locationsById = locationsById
        )
    }

    fun stopKey(task: VendiTask): String? {
        if (task.type == TaskType.MachinePickupInventory) {
            return task.warehouseId?.let { "warehouse-$it" }
        }
        return task.location
    }

    private fun buildStopPlan(
        locationId: String,
        tasks: List<VendiTask>,
        locationsById: Map<String, AppLocation> = emptyMap()
    ): GoStopPlan? {
        val actionableTasks = tasks
            .filter { it.location == locationId }
            .ifEmpty { tasks.filter { it.type == TaskType.MachinePickupInventory } }
        if (actionableTasks.isEmpty()) return null

        val first = actionableTasks.first()
        if (first.type == TaskType.MachinePickupInventory) {
            return buildWarehousePickupStopPlan(actionableTasks)
        }

        val displayTask = preferredLocationTask(actionableTasks) ?: actionableTasks.first()
        val address = preferredLocationAddress(actionableTasks) ?: locationsById[locationId]?.address
        val latitude = address?.latitude ?: return null
        val longitude = address.longitude ?: return null
        val machineGroups = TaskExecutionResolver.orderedMachineGroups(actionableTasks)
        val financials = TaskFinancialHelpers.sumTaskFinancials(actionableTasks)
        val node = GoNode(
            id = "location-$locationId",
            type = GoNodeType.Location,
            title = displayTask.locationName ?: locationsById[locationId]?.name ?: "Location",
            subtitle = AddressFormatter.singleLineWithoutCountry(address),
            coordinate = LocationCoordinate(latitude = latitude, longitude = longitude),
            locationId = locationId,
            taskIds = actionableTasks.map { it.id }
        )

        return GoStopPlan(
            id = locationId,
            targetLocationId = locationId,
            title = displayTask.locationName ?: locationsById[locationId]?.name ?: "Location",
            addressStreetLine = address.street,
            addressCityStateZipLine = AddressFormatter.cityStateZipLine(address),
            tasks = actionableTasks,
            nodes = listOf(node),
            machineGroups = machineGroups,
            gross = financials.gross,
            refunds = financials.refunds,
            commission = financials.commission,
            net = financials.net
        )
    }

    fun activeExecution(stop: GoStopPlan, preferredTaskId: String? = null): ActiveTaskExecution {
        val orderedTasks = TaskExecutionResolver.orderedDisplayTasks(stop.tasks)
        val currentTask = TaskExecutionResolver.currentExecutableTask(
            tasks = orderedTasks,
            preferredTaskId = preferredTaskId
        )
        val startTask = TaskExecutionResolver.startTask(
            tasks = orderedTasks,
            preferredTaskId = currentTask?.id
        )
        val wrapperTask = TaskExecutionResolver.wrapperTask(currentTask, orderedTasks)
        val machineGroups = TaskExecutionResolver.orderedMachineGroups(
            tasks = orderedTasks,
            preferredMachineId = currentTask?.machine ?: startTask?.machine
        )
        val displayTasks = machineGroups.flatMap { it.tasks }
        val progress = TaskExecutionResolver.progress(displayTasks, currentTask?.id)
        return ActiveTaskExecution(
            stopId = stop.id,
            title = stop.title,
            locationId = stop.targetLocationId,
            destinationKind = stop.destinationKind,
            taskIds = displayTasks.map { it.id },
            wrapperTaskId = wrapperTask?.id?.takeIf { it != currentTask?.id },
            displayTasks = displayTasks,
            tasks = displayTasks.map { task ->
                ExecutionTaskItem(
                    id = task.id,
                    type = task.type,
                    status = task.status,
                    machineId = task.machine,
                    machineName = task.machineName,
                    startedAt = task.startedAt,
                    doneAt = task.doneAt,
                    isWrapper = task.type == TaskType.MachineService
                )
            },
            machineGroups = machineGroups,
            currentTaskId = currentTask?.id ?: startTask?.id,
            currentTaskIndex = progress.current,
            totalTaskCount = progress.total,
            gross = stop.gross,
            refunds = stop.refunds,
            commission = stop.commission,
            net = stop.net
        )
    }

    fun scopedStop(stop: GoStopPlan, taskIds: Set<String>): GoStopPlan? {
        if (taskIds.isEmpty()) return null
        val scopedTasks = stop.tasks.filter { it.id in taskIds }
        if (scopedTasks.isEmpty()) return null
        val financials = TaskFinancialHelpers.sumTaskFinancials(scopedTasks)
        return stop.copy(
            tasks = scopedTasks,
            nodes = stop.nodes.map { node ->
                node.copy(taskIds = node.taskIds.filter { it in taskIds })
            },
            machineGroups = TaskExecutionResolver.orderedMachineGroups(scopedTasks),
            gross = financials.gross,
            refunds = financials.refunds,
            commission = financials.commission,
            net = financials.net
        )
    }

    fun buildWarehousePickupStop(task: VendiTask): GoStopPlan? {
        return buildWarehousePickupStopPlan(listOf(task))
    }

    private fun buildWarehousePickupStopPlan(tasks: List<VendiTask>): GoStopPlan? {
        val task = tasks.firstOrNull { it.type == TaskType.MachinePickupInventory } ?: return null
        if (task.type != TaskType.MachinePickupInventory) return null
        val address = task.warehouseAddress ?: return null
        val latitude = address.latitude ?: return null
        val longitude = address.longitude ?: return null
        val title = task.warehouseName ?: "Warehouse"
        val financials = TaskFinancialHelpers.sumTaskFinancials(tasks)
        val node = GoNode(
            id = "warehouse-${task.warehouseId ?: task.id}",
            type = GoNodeType.Pickup,
            title = title,
            subtitle = AddressFormatter.singleLineWithoutCountry(address),
            coordinate = LocationCoordinate(latitude = latitude, longitude = longitude),
            locationId = null,
            taskIds = tasks.map { it.id }
        )

        return GoStopPlan(
            id = "warehouse:${task.warehouseId ?: task.id}",
            targetLocationId = task.warehouseId ?: task.id,
            title = title,
            addressStreetLine = address.street,
            addressCityStateZipLine = AddressFormatter.cityStateZipLine(address),
            tasks = tasks,
            nodes = listOf(node),
            machineGroups = TaskExecutionResolver.orderedMachineGroups(tasks),
            gross = financials.gross,
            refunds = financials.refunds,
            commission = financials.commission,
            net = financials.net
        )
    }

    fun buildStopFromSession(
        session: ActiveWorkSession,
        tasks: List<VendiTask>,
        locationsById: Map<String, AppLocation> = emptyMap()
    ): GoStopPlan? {
        val sessionTasks = tasks.filter { it.id in session.activeTaskIds }
        if (sessionTasks.isEmpty()) return null

        val pickupTask = sessionTasks.firstOrNull { it.type == TaskType.MachinePickupInventory }
        if (session.locationId == null && pickupTask != null) {
            return buildWarehousePickupStop(pickupTask)
        }

        val locationId = session.locationId ?: sessionTasks.firstNotNullOfOrNull { it.location } ?: return null
        val displayTask = preferredLocationTask(sessionTasks) ?: sessionTasks.first()
        val location = locationsById[locationId]
        val address = preferredLocationAddress(sessionTasks) ?: location?.address
        val coordinate = session.coordinate
            ?: address?.let { locationAddress ->
                val latitude = locationAddress.latitude
                val longitude = locationAddress.longitude
                if (latitude != null && longitude != null) LocationCoordinate(latitude, longitude) else null
            }
            ?: return null
        val title = session.title.ifBlank { displayTask.locationName ?: location?.name ?: "Location" }
        val financials = TaskFinancialHelpers.sumTaskFinancials(sessionTasks)
        val node = GoNode(
            id = "location-$locationId",
            type = GoNodeType.Location,
            title = title,
            subtitle = listOfNotNull(session.addressStreetLine, session.addressCityStateZipLine)
                .filter { it.isNotBlank() }
                .joinToString(", ")
                .ifBlank { address?.singleLine?.ifBlank { null } },
            coordinate = coordinate,
            locationId = locationId,
            taskIds = sessionTasks.map { it.id }
        )

        return GoStopPlan(
            id = locationId,
            targetLocationId = locationId,
            title = title,
            addressStreetLine = session.addressStreetLine ?: address?.street,
            addressCityStateZipLine = session.addressCityStateZipLine ?: AddressFormatter.cityStateZipLine(address),
            tasks = sessionTasks,
            nodes = listOf(node),
            machineGroups = TaskExecutionResolver.orderedMachineGroups(sessionTasks),
            gross = financials.gross,
            refunds = financials.refunds,
            commission = financials.commission,
            net = financials.net
        )
    }

    private fun preferredLocationTask(tasks: List<VendiTask>): VendiTask? {
        return tasks
            .filter { it.locationAddress != null || it.locationName != null }
            .sortedWith(
                compareByDescending<VendiTask> { it.createdAt.orEmpty() }
                    .thenBy { it.id }
            )
            .firstOrNull()
    }

    private fun preferredLocationAddress(tasks: List<VendiTask>): Address? {
        return preferredLocationTask(
            tasks.filter { task ->
                val address = task.locationAddress ?: return@filter false
                address.latitude != null && address.longitude != null
            }
        )?.locationAddress
    }

    private fun suggestedStopId(
        stops: List<GoStopPlan>,
        locationsById: Map<String, AppLocation>,
        currentCoordinate: LocationCoordinate?,
        availabilityTime: ZonedDateTime
    ): String? {
        if (currentCoordinate == null) return null
        return stops.firstOrNull { stop ->
            LocationHours.isOpenOrUnconfigured(locationsById[stop.targetLocationId], at = availabilityTime)
        }?.id ?: stops.firstOrNull()?.id
    }
}
