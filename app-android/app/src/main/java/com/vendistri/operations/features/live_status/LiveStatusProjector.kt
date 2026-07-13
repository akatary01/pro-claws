package com.vendistri.operations.features.live_status

import com.vendistri.operations.features.navigation.NavigationSessionState
import com.vendistri.operations.features.settings.AppTimeFormatter
import com.vendistri.operations.features.settings.TimeFormatPreference
import com.vendistri.operations.features.tasks.TaskStateHelpers
import com.vendistri.operations.features.tasks.taskTypeLabel
import com.vendistri.operations.features.work.TaskExecutionDisplay
import com.vendistri.operations.features.work.WorkPhase
import com.vendistri.operations.features.work.WorkUiState
import com.vendistri.operations.features.work.isAtDestination
import com.vendistri.operations.features.work.isNavigating
import java.time.LocalDateTime
import java.util.Locale

object LiveStatusProjector {
    fun project(
        work: WorkUiState,
        navigation: NavigationSessionState,
        timeFormatPreference: TimeFormatPreference,
        systemUses24Hour: Boolean? = null,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): LiveStatusSnapshot? {
        val session = work.activeSession ?: return null
        val execution = work.activeExecution
        val stopId = execution?.stopId ?: navigation.activeStopId ?: work.selectedStop?.id ?: return null
        val destination = execution?.title ?: work.selectedStop?.title ?: session.title
        val address = listOfNotNull(session.addressStreetLine, session.addressCityStateZipLine)
            .filter { it.isNotBlank() }
            .joinToString(", ")
            .takeIf { it.isNotBlank() }

        if (work.phase.isAtDestination && execution != null) {
            val finalCount = execution.displayTasks.count { TaskStateHelpers.isFinal(it.status) }
                .coerceAtMost(execution.displayTasks.size)
            val total = execution.displayTasks.size
            val place = if (work.phase == WorkPhase.AtWarehouse) "At warehouse" else "At location"
            val currentTask = execution.currentTaskId
                ?.let { id -> execution.displayTasks.firstOrNull { it.id == id } }
            val currentTitle = currentTask?.let { taskTypeLabel(it.type) } ?: "Work in progress"
            val progress = "$finalCount/$total tasks completed"
            return LiveStatusSnapshot(
                sessionId = session.id,
                stopId = stopId,
                mode = if (work.phase == WorkPhase.AtWarehouse) LiveStatusMode.AtWarehouse else LiveStatusMode.AtLocation,
                title = currentTitle,
                destination = destination,
                address = address,
                primaryStatus = "$place • $progress",
                secondaryStatus = "${TaskExecutionDisplay.timeText(execution, nowEpochMillis)} • ${TaskExecutionDisplay.distanceText(execution, nowEpochMillis)}",
                progressCurrent = finalCount,
                progressTotal = total
            )
        }

        if (!work.phase.isNavigating || !navigation.isNavigating) return null
        val distance = navigation.distanceRemainingMiles?.let(::distanceText) ?: "--"
        val eta = navigation.durationRemainingSeconds?.let { seconds ->
            AppTimeFormatter.arrivalTime(
                afterSeconds = seconds.coerceAtLeast(0.0),
                preference = timeFormatPreference,
                systemUses24Hour = systemUses24Hour,
                from = LocalDateTime.now()
            )
        }
        val duration = navigation.durationRemainingSeconds?.let(::durationText) ?: "--"
        val isRerouting = navigation.isRerouting
        val instruction = navigation.currentInstructionText?.takeIf { it.isNotBlank() }
        return LiveStatusSnapshot(
            sessionId = session.id,
            stopId = stopId,
            mode = if (isRerouting) LiveStatusMode.Rerouting else LiveStatusMode.Navigating,
            title = if (isRerouting) "Rerouting" else instruction ?: "Navigating",
            destination = destination,
            address = address,
            primaryStatus = if (isRerouting) "Updating your route" else "Navigating to $destination",
            secondaryStatus = listOfNotNull(eta?.let { "ETA $it" }, distance).joinToString(" • "),
            nextInstruction = instruction,
            etaText = eta,
            distanceRemainingText = distance,
            isRerouting = isRerouting
        )
    }

    private fun distanceText(miles: Double): String {
        val clamped = miles.coerceAtLeast(0.0)
        val feet = clamped * 5280.0
        return when {
            feet < 1000 -> "${feet.toInt()} ft"
            clamped < 10 -> String.format(Locale.US, "%.1f mi", clamped)
            else -> "${clamped.toInt()} mi"
        }
    }

    private fun durationText(seconds: Double): String {
        val minutes = (seconds.coerceAtLeast(0.0) / 60.0).toInt()
        val hours = minutes / 60
        val remainder = minutes % 60
        return if (hours > 0) "${hours}h ${remainder}m" else "${minutes}m"
    }
}
