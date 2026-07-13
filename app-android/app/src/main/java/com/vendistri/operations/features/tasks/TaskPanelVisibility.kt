package com.vendistri.operations.features.tasks

import java.time.LocalDate

internal object TaskPanelVisibility {
    fun actionableTasksForDate(tasks: List<VendiTask>, date: LocalDate): List<VendiTask> {
        return tasks.filter { task ->
            TaskStateHelpers.isActionable(task.status) && task.scheduledDate() == date
        }
    }

    fun finalTasksForDate(tasks: List<VendiTask>, date: LocalDate): List<VendiTask> {
        return tasks.filter { task ->
            TaskStateHelpers.isCompleted(task.status) && task.panelWorkDate() == date
        }
    }

    fun finalTasksForWeek(tasks: List<VendiTask>, containing: LocalDate): List<VendiTask> {
        return tasks.filter { task ->
            val workDate = task.panelWorkDate()
            TaskStateHelpers.isCompleted(task.status) && workDate?.isInWeekOf(containing) == true
        }
    }
}

internal fun VendiTask.scheduledDate(): LocalDate? = scheduledFor.toLocalDatePrefix()

internal fun VendiTask.panelWorkDate(): LocalDate? {
    return scheduledFor.toLocalDatePrefix()
}

internal fun LocalDate.isInWeekOf(date: LocalDate): Boolean {
    val start = date.minusDays((date.dayOfWeek.value - 1).toLong())
    val end = start.plusDays(6)
    return !isBefore(start) && !isAfter(end)
}
