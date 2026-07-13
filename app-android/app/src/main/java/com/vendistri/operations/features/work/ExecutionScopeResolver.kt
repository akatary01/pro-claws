package com.vendistri.operations.features.work

import com.vendistri.operations.features.tasks.TaskFinancialHelpers
import com.vendistri.operations.features.tasks.TaskGroupingHelpers
import com.vendistri.operations.features.tasks.TaskStateHelpers
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.VendiTask

enum class ExecutionScopeCardState {
    Current,
    Locked,
    Done,
    Cancelled,
    Error
}

enum class ExecutionScopeServiceBadgeState {
    Pending,
    Saving,
    Done,
    Cancelled,
    Error
}

data class ExecutionScopeMetrics(
    val durationMinutes: Double,
    val distanceMiles: Double,
    val gross: Double,
    val refunds: Double,
    val commission: Double,
    val net: Double
) {
    fun adding(next: ExecutionScopeMetrics): ExecutionScopeMetrics {
        return copy(
            durationMinutes = durationMinutes + next.durationMinutes,
            distanceMiles = distanceMiles + next.distanceMiles,
            gross = gross + next.gross,
            refunds = refunds + next.refunds,
            commission = commission + next.commission,
            net = net + next.net
        )
    }

    companion object {
        val Zero = ExecutionScopeMetrics(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    }
}

data class ExecutionScopeTaskCard(
    val task: VendiTask,
    val displayStatus: TaskStatus,
    val state: ExecutionScopeCardState,
    val metrics: ExecutionScopeMetrics,
    val isCurrent: Boolean
)

data class ExecutionScopeMachineSection(
    val id: String,
    val name: String,
    val serviceTask: VendiTask?,
    val serviceDisplayStatus: TaskStatus?,
    val serviceBadgeState: ExecutionScopeServiceBadgeState?,
    val serviceCompletedChildCount: Int,
    val serviceTotalChildCount: Int,
    val serviceMetrics: ExecutionScopeMetrics?,
    val childCards: List<ExecutionScopeTaskCard>,
    val machineMetrics: ExecutionScopeMetrics,
    val isActive: Boolean
)

data class ExecutionScopeDisplayModel(
    val machineSections: List<ExecutionScopeMachineSection>,
    val currentExecutionTasks: List<VendiTask>,
    val completedPickupTasks: List<VendiTask>,
    val completedPickupRefillTaskIds: Set<String>,
    val previousWorkCandidates: List<VendiTask>,
    val warehouseStockTasks: List<VendiTask>,
    val totalMetrics: ExecutionScopeMetrics,
    val allTasksAreFinal: Boolean
)

object ExecutionScopeResolver {
    fun resolve(
        execution: ActiveTaskExecution,
        allTasks: List<VendiTask>,
        previousWorkScope: PreviousWorkScope = PreviousWorkScope(currentUserId = null, canViewAllAssignees = true),
        nowEpochMillis: Long = System.currentTimeMillis(),
        includesFinalPickupInventorySections: Boolean = false
    ): ExecutionScopeDisplayModel {
        val currentExecutionTasks = TaskExecutionResolver.hydratedTasks(execution.displayTasks, allTasks)
        val completedPickupTasks = completedPickupTasks(execution, currentExecutionTasks, allTasks)
        val completedPickupRefillTaskIds = TaskExecutionResolver.linkedRefillTaskIds(completedPickupTasks)
        val previousWorkCandidates = PreviousWorkResolver.previousWork(
            currentTasks = currentExecutionTasks,
            allTasks = allTasks,
            completedPickupTasks = completedPickupTasks,
            scope = previousWorkScope
        )
        val warehouseStockTasks = warehouseStockTasks(
            executionTasks = currentExecutionTasks,
            allTasks = allTasks,
            excludedRefillTaskIds = completedPickupRefillTaskIds
        )
        val shouldIncludeFinalPickupInventorySections =
            includesFinalPickupInventorySections ||
                execution.destinationKind == WorkDestinationKind.Warehouse ||
                completedPickupTasks.isNotEmpty()
        val sectionTasks = TaskGroupingHelpers.uniqueTasksById(currentExecutionTasks + completedPickupTasks)
        val sectionMachineGroups = TaskExecutionResolver.stableMachineGroups(
            groups = TaskGroupingHelpers.groupByMachine(
                tasks = sectionTasks,
                lookupTasks = allTasks
            ),
            previousGroups = execution.machineGroups
        )
        val sections = visibleMachineSections(
            execution = execution.copy(
                displayTasks = sectionTasks,
                machineGroups = sectionMachineGroups
            ),
            allTasks = allTasks,
            nowEpochMillis = nowEpochMillis,
            includesFinalPickupInventorySections = shouldIncludeFinalPickupInventorySections
        )
        return ExecutionScopeDisplayModel(
            machineSections = sections,
            currentExecutionTasks = currentExecutionTasks,
            completedPickupTasks = completedPickupTasks,
            completedPickupRefillTaskIds = completedPickupRefillTaskIds,
            previousWorkCandidates = previousWorkCandidates,
            warehouseStockTasks = warehouseStockTasks,
            totalMetrics = metrics(
                tasks = currentExecutionTasks + completedPickupTasks,
                execution = execution,
                nowEpochMillis = nowEpochMillis
            ),
            allTasksAreFinal = currentExecutionTasks.all { TaskStateHelpers.isFinal(it.status) }
        )
    }

    fun metrics(
        tasks: List<VendiTask>,
        execution: ActiveTaskExecution?,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): ExecutionScopeMetrics {
        val taskMetrics = TaskExecutionMetrics.aggregateMetrics(
            tasks = tasks,
            execution = execution,
            nowEpochMillis = nowEpochMillis
        )
        val financials = TaskFinancialHelpers.sumTaskFinancials(tasks)
        return ExecutionScopeMetrics(
            durationMinutes = taskMetrics.durationMinutes,
            distanceMiles = taskMetrics.distanceMiles,
            gross = financials.gross,
            refunds = financials.refunds,
            commission = financials.commission,
            net = financials.net
        )
    }

    fun visibleMachineSections(
        execution: ActiveTaskExecution,
        allTasks: List<VendiTask>,
        nowEpochMillis: Long = System.currentTimeMillis(),
        includesFinalPickupInventorySections: Boolean = false
    ): List<ExecutionScopeMachineSection> {
        return machineSections(execution, allTasks, nowEpochMillis).mapNotNull { section ->
            visibleSection(section, execution, nowEpochMillis, includesFinalPickupInventorySections)
        }
    }

    fun machineSections(
        execution: ActiveTaskExecution,
        allTasks: List<VendiTask>,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): List<ExecutionScopeMachineSection> {
        return execution.machineGroups.map { group ->
            val groupTasks = TaskExecutionResolver.hydratedTasks(group.tasks, allTasks)
            val ordered = TaskExecutionResolver.orderedDisplayTasks(groupTasks) { it.status }
            val serviceTasks = ordered.filter { it.type == TaskType.MachineService }
            val childTasks = ordered.filter { it.type != TaskType.MachineService }
            val serviceTask = currentServiceTask(serviceTasks, childTasks)
            val childCards = childTasks.map { task ->
                ExecutionScopeTaskCard(
                    task = task,
                    displayStatus = task.status,
                    state = cardState(task, execution.currentTaskId, task.status),
                    metrics = metrics(listOf(task), execution, nowEpochMillis),
                    isCurrent = task.id == execution.currentTaskId
                )
            }
            val serviceStatus = serviceTask?.status
            ExecutionScopeMachineSection(
                id = group.id,
                name = group.name,
                serviceTask = serviceTask,
                serviceDisplayStatus = serviceStatus,
                serviceBadgeState = serviceTask?.let { serviceBadgeState(it, childTasks) },
                serviceCompletedChildCount = childTasks.count { TaskStateHelpers.isFinal(it.status) },
                serviceTotalChildCount = childTasks.size,
                serviceMetrics = serviceTask?.let { metrics(listOf(it), execution, nowEpochMillis) },
                childCards = childCards,
                machineMetrics = metrics(ordered, execution, nowEpochMillis),
                isActive = childTasks.any { it.id == execution.currentTaskId } || serviceTask?.id == execution.currentTaskId
            )
        }
    }

    private fun completedPickupTasks(
        execution: ActiveTaskExecution,
        currentExecutionTasks: List<VendiTask>,
        allTasks: List<VendiTask>
    ): List<VendiTask> {
        if (execution.destinationKind == WorkDestinationKind.Warehouse) {
            return TaskGroupingHelpers.uniqueTasksById(currentExecutionTasks).filter { task ->
                task.type == TaskType.MachinePickupInventory && TaskStateHelpers.isFinal(task.status)
            }
        }
        return TaskExecutionResolver.completedPickupTasks(
            linkedToTasks = currentExecutionTasks,
            candidates = allTasks
        )
    }

    private fun warehouseStockTasks(
        executionTasks: List<VendiTask>,
        allTasks: List<VendiTask>,
        excludedRefillTaskIds: Set<String>
    ): List<VendiTask> {
        val currentTaskIds = executionTasks.map { it.id }.toSet()
        val locationIds = executionTasks.mapNotNull { it.location }.toSet()
        if (locationIds.isEmpty()) return emptyList()
        return TaskGroupingHelpers.uniqueTasksById(allTasks).filter { task ->
            task.id !in currentTaskIds &&
                task.id !in excludedRefillTaskIds &&
                task.location in locationIds &&
                task.type == TaskType.MachineRefill &&
                !TaskStateHelpers.isFinal(task.status)
        }
    }

    private fun currentServiceTask(
        serviceTasks: List<VendiTask>,
        childTasks: List<VendiTask>
    ): VendiTask? {
        if (serviceTasks.isEmpty()) return null
        val actionableChildServiceTaskIds = childTasks
            .filterNot { TaskStateHelpers.isFinal(it.status) }
            .mapNotNull { it.serviceTaskId }
            .toSet()
        return serviceTasks.firstOrNull { it.id in actionableChildServiceTaskIds }
            ?: serviceTasks.firstOrNull { !TaskStateHelpers.isFinal(it.status) }
            ?: serviceTasks.firstOrNull()
    }

    private fun visibleSection(
        section: ExecutionScopeMachineSection,
        execution: ActiveTaskExecution,
        nowEpochMillis: Long,
        includesFinalPickupInventorySections: Boolean
    ): ExecutionScopeMachineSection? {
        if (!includesFinalPickupInventorySections && isStandaloneCompletedPickupSection(section)) {
            return null
        }
        val visibleChildCards = section.childCards.filter { card ->
            card.task.type != TaskType.MachinePickupInventory ||
                !TaskStateHelpers.isFinal(card.displayStatus) ||
                includesFinalPickupInventorySections
        }
        if (visibleChildCards.isEmpty() && section.serviceTask == null) return null
        if (visibleChildCards.size == section.childCards.size) return section
        val visibleTasks = listOfNotNull(section.serviceTask) + visibleChildCards.map { it.task }
        return section.copy(
            serviceBadgeState = section.serviceTask?.let { serviceBadgeState(it, visibleChildCards.map { card -> card.task }) },
            serviceCompletedChildCount = visibleChildCards.count { TaskStateHelpers.isFinal(it.displayStatus) },
            serviceTotalChildCount = visibleChildCards.size,
            childCards = visibleChildCards,
            machineMetrics = metrics(visibleTasks, execution, nowEpochMillis)
        )
    }

    private fun isStandaloneCompletedPickupSection(section: ExecutionScopeMachineSection): Boolean {
        return section.serviceTask == null &&
            section.childCards.isNotEmpty() &&
            section.childCards.all {
                it.task.type == TaskType.MachinePickupInventory && TaskStateHelpers.isFinal(it.displayStatus)
            }
    }

    private fun cardState(
        task: VendiTask,
        currentTaskId: String?,
        displayStatus: TaskStatus
    ): ExecutionScopeCardState {
        return when {
            displayStatus == TaskStatus.Done -> ExecutionScopeCardState.Done
            displayStatus == TaskStatus.Cancelled -> ExecutionScopeCardState.Cancelled
            displayStatus == TaskStatus.Error -> ExecutionScopeCardState.Error
            task.id == currentTaskId -> ExecutionScopeCardState.Current
            else -> ExecutionScopeCardState.Locked
        }
    }

    private fun serviceBadgeState(
        serviceTask: VendiTask,
        childTasks: List<VendiTask>
    ): ExecutionScopeServiceBadgeState {
        return when {
            serviceTask.status == TaskStatus.Done -> ExecutionScopeServiceBadgeState.Done
            serviceTask.status == TaskStatus.Cancelled -> ExecutionScopeServiceBadgeState.Cancelled
            serviceTask.status == TaskStatus.Error -> ExecutionScopeServiceBadgeState.Error
            childTasks.any { !TaskStateHelpers.isFinal(it.status) } -> ExecutionScopeServiceBadgeState.Pending
            childTasks.isNotEmpty() -> ExecutionScopeServiceBadgeState.Saving
            else -> ExecutionScopeServiceBadgeState.Pending
        }
    }
}
