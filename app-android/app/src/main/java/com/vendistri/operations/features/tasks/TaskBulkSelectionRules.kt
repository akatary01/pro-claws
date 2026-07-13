package com.vendistri.operations.features.tasks

object TaskBulkSelectionRules {
    const val ServiceBundleRequirementMessage = "Service bundled task requires at least one child task."

    fun normalizedSelection(
        allTasks: List<VendiTask>,
        selectedTaskIds: Set<String>,
        displayStatus: (VendiTask) -> TaskStatus = { it.status }
    ): Set<String> {
        val normalizedIds = selectedTaskIds.toMutableSet()
        allTasks.forEach { serviceTask ->
            if (serviceTask.type != TaskType.MachineService || !isActionable(serviceTask, displayStatus)) return@forEach
            val children = actionableChildren(serviceTask, allTasks, displayStatus)
            val onlyChild = children.singleOrNull() ?: return@forEach
            if (normalizedIds.contains(serviceTask.id) || normalizedIds.contains(onlyChild.id)) {
                normalizedIds += serviceTask.id
                normalizedIds += onlyChild.id
            }
        }
        return normalizedIds
    }

    fun serviceBundleInfoMessage(
        allTasks: List<VendiTask>,
        selectedTaskIds: Set<String>,
        displayStatus: (VendiTask) -> TaskStatus = { it.status }
    ): String? {
        allTasks.forEach { serviceTask ->
            if (serviceTask.type != TaskType.MachineService || !selectedTaskIds.contains(serviceTask.id)) return@forEach
            val children = actionableChildren(serviceTask, allTasks, displayStatus)
            if (children.size == 1 && selectedTaskIds.contains(children[0].id)) {
                return ServiceBundleRequirementMessage
            }
        }
        return null
    }

    fun serviceBundleValidationMessage(
        allTasks: List<VendiTask>,
        selectedTaskIds: Set<String>,
        displayStatus: (VendiTask) -> TaskStatus = { it.status }
    ): String? {
        allTasks.forEach { serviceTask ->
            if (serviceTask.type != TaskType.MachineService || !isActionable(serviceTask, displayStatus)) return@forEach
            if (selectedTaskIds.contains(serviceTask.id)) return@forEach
            val hasRemainingChild = allTasks.any { task ->
                task.id != serviceTask.id &&
                    task.serviceTaskId == serviceTask.id &&
                    !selectedTaskIds.contains(task.id) &&
                    isActionable(task, displayStatus)
            }
            if (!hasRemainingChild) {
                return "Service must keep at least one bundled task. Cancel or delete the service task too, or keep one bundled task."
            }
        }
        return null
    }

    private fun actionableChildren(
        serviceTask: VendiTask,
        allTasks: List<VendiTask>,
        displayStatus: (VendiTask) -> TaskStatus
    ): List<VendiTask> {
        return allTasks.filter { task ->
            task.id != serviceTask.id &&
                task.serviceTaskId == serviceTask.id &&
                isActionable(task, displayStatus)
        }
    }

    private fun isActionable(task: VendiTask, displayStatus: (VendiTask) -> TaskStatus): Boolean {
        return TaskStateHelpers.isActionable(displayStatus(task))
    }
}
