package com.vendistri.operations.features.map

import androidx.compose.ui.graphics.Color
import com.vendistri.operations.features.location.Address
import com.vendistri.operations.features.location.AppLocation
import com.vendistri.operations.features.location_contact.ContactVisibilityRules
import com.vendistri.operations.features.tasks.TaskFinancialHelpers
import com.vendistri.operations.features.tasks.TaskScheduleDate
import com.vendistri.operations.features.tasks.TaskStateHelpers
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskStatusHelpers
import com.vendistri.operations.features.tasks.VendiTask
import com.vendistri.operations.utils.AddressFormatter
import java.time.LocalDate

object LocationStopsBuilder {
    fun buildStops(
        tasks: List<VendiTask>,
        locationsById: Map<String, AppLocation> = emptyMap(),
        today: LocalDate = LocalDate.now(),
        currentUserId: String? = null
    ): List<LocationStop> {
        val normalizedUserId = currentUserId?.trim().orEmpty()
        return tasks
            .filter { TaskScheduleDate.isSameDay(it.scheduledFor, today) }
            .groupBy { it.location ?: "unknown" }
            .mapNotNull { (locationId, groupTasks) ->
                val first = groupTasks.firstOrNull() ?: return@mapNotNull null
                val displayTask = preferredLocationTask(groupTasks) ?: first
                val coordinateAddress = preferredLocationAddress(groupTasks) ?: locationsById[locationId]?.address
                val displayAddress = locationsById[locationId]?.address ?: coordinateAddress
                val latitude = coordinateAddress?.latitude ?: return@mapNotNull null
                val longitude = coordinateAddress.longitude ?: return@mapNotNull null
                val counts = TaskStatusHelpers.statusCounts(groupTasks)
                val flags = TaskStatusHelpers.statusFlags(groupTasks)
                val totals = TaskFinancialHelpers.sumTaskFinancials(groupTasks)
                val actionableTasks = groupTasks.filter { TaskStateHelpers.isActionable(it.status) }
                val assignedToCurrentUser = actionableTasks.count { task ->
                    normalizedUserId.isNotBlank() &&
                        task.status == TaskStatus.Pending &&
                        task.assignee?.trim() == normalizedUserId
                }
                val unassignedTasks = actionableTasks.count { it.status == TaskStatus.Unassigned }

                LocationStop(
                    id = locationId,
                    name = displayTask.locationName ?: locationsById[locationId]?.name ?: "Location",
                    addressStreetLine = displayAddress?.street,
                    addressCityStateZipLine = AddressFormatter.cityStateZipLine(displayAddress),
                    coordinate = LocationCoordinate(latitude = latitude, longitude = longitude),
                    color = TaskStatusHelpers.statusColor(
                        hasPending = flags.hasPending,
                        hasUnassigned = flags.hasUnassigned,
                        hasDone = flags.hasDone,
                        hasCancelled = flags.hasCancelled,
                        hasError = flags.hasError
                    ),
                    hasPending = counts.pending > 0 || counts.unassigned > 0,
                    hasDone = counts.done > 0,
                    hasCancelled = counts.cancelled > 0,
                    pendingCount = counts.pending,
                    unassignedCount = counts.unassigned,
                    doneCount = counts.done,
                    cancelledCount = counts.cancelled,
                    totalCount = groupTasks.size,
                    machineCount = groupTasks.mapNotNull { it.machine }.distinct().size,
                    gross = totals.gross,
                    refunds = totals.refunds,
                    commission = totals.commission,
                    net = totals.net,
                    action = when {
                        assignedToCurrentUser > 0 -> LocationStopAction.Go
                        unassignedTasks > 0 -> LocationStopAction.ClaimTasks
                        else -> LocationStopAction.OpenTasks
                    },
                    assigneeSummary = assigneeSummary(actionableTasks, normalizedUserId),
                    commissionPaymentSummary = commissionPaymentSummary(groupTasks)
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    fun buildContactStops(
        locations: List<AppLocation>,
        tasks: List<VendiTask>,
        displayStatus: TaskStatus? = null
    ): List<LocationStop> {
        val visibleTasks = ContactVisibilityRules.visibleTasks(tasks)
        return locations.mapNotNull { location ->
            val address = location.address ?: return@mapNotNull null
            val latitude = address.latitude ?: return@mapNotNull null
            val longitude = address.longitude ?: return@mapNotNull null
            val locationTasks = contactDisplayTasks(location.id, visibleTasks, displayStatus)
            val counts = TaskStatusHelpers.statusCounts(locationTasks)
            val flags = TaskStatusHelpers.statusFlags(locationTasks)
            val totals = TaskFinancialHelpers.sumTaskFinancials(locationTasks)
            LocationStop(
                id = location.id,
                name = location.name,
                addressStreetLine = address.street,
                addressCityStateZipLine = AddressFormatter.cityStateZipLine(address),
                coordinate = LocationCoordinate(latitude = latitude, longitude = longitude),
                color = TaskStatusHelpers.statusColor(
                    hasPending = flags.hasPending,
                    hasUnassigned = flags.hasUnassigned,
                    hasDone = flags.hasDone,
                    hasCancelled = false,
                    hasError = false
                ),
                hasPending = counts.pending > 0 || counts.unassigned > 0,
                hasDone = counts.done > 0,
                hasCancelled = false,
                pendingCount = counts.pending,
                unassignedCount = counts.unassigned,
                doneCount = counts.done,
                cancelledCount = 0,
                totalCount = locationTasks.size,
                machineCount = locationTasks.mapNotNull { it.machine }.distinct().size,
                gross = totals.commission,
                refunds = totals.refunds,
                commission = totals.commission,
                net = totals.net,
                action = LocationStopAction.OpenTasks,
                assigneeSummary = null,
                commissionPaymentSummary = commissionPaymentSummary(locationTasks)
            )
        }.sortedBy { it.name.lowercase() }
    }

    fun contactDisplayTasks(
        locationId: String,
        tasks: List<VendiTask>,
        displayStatus: TaskStatus? = null,
        today: LocalDate = LocalDate.now()
    ): List<VendiTask> {
        val locationTasks = ContactVisibilityRules.visibleTasks(tasks).filter { it.location == locationId }
        val statusTasks = displayStatus?.let { status -> locationTasks.filter { it.status == status } } ?: locationTasks
        val todayTasks = statusTasks.filter { TaskScheduleDate.isSameDay(it.scheduledFor, today) }
        if (todayTasks.isNotEmpty()) return todayTasks
        val nextDate = statusTasks.mapNotNull { TaskScheduleDate.parse(it.scheduledFor) }
            .filter { !it.isBefore(today) }
            .minOrNull()
            ?: return emptyList()
        return statusTasks.filter { TaskScheduleDate.isSameDay(it.scheduledFor, nextDate) }
    }

    fun contactDisplayDate(
        locationId: String,
        tasks: List<VendiTask>,
        today: LocalDate = LocalDate.now()
    ): LocalDate? = contactDisplayTasks(locationId, tasks, today = today)
        .firstNotNullOfOrNull { TaskScheduleDate.parse(it.scheduledFor) }

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

    private fun assigneeSummary(tasks: List<VendiTask>, currentUserId: String): String? {
        val otherAssignedTasks = tasks.filter { task ->
            val assigneeId = task.assignee?.trim().orEmpty()
            task.status == TaskStatus.Pending &&
                assigneeId.isNotBlank() &&
                (currentUserId.isBlank() || assigneeId != currentUserId)
        }
        if (otherAssignedTasks.isEmpty()) return null
        val labels = otherAssignedTasks
            .map { task -> task.assigneeName ?: task.assigneeEmail ?: task.assignee }
            .filterNot { it.isNullOrBlank() }
            .map { it!!.trim() }
            .distinct()
        return when (labels.size) {
            0 -> "Assigned"
            1 -> "Assignee: ${labels.first()}"
            else -> "Assignee: Mixed"
        }
    }

    private fun commissionPaymentSummary(tasks: List<VendiTask>): String? {
        val labels = tasks
            .filter { it.commission != null && kotlin.math.abs(it.commission) >= 0.01 }
            .mapNotNull { it.commissionPaymentType?.label }
            .distinct()
        return when (labels.size) {
            0 -> null
            1 -> labels.first()
            else -> "Mixed"
        }
    }

}
