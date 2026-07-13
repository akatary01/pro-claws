package com.vendistri.operations.features.tasks.add_stop

import com.vendistri.operations.features.tasks.TaskCreateRequest
import com.vendistri.operations.features.tasks.TaskBulkPrecheckItem
import com.vendistri.operations.features.tasks.TaskBulkPrecheckResult
import com.vendistri.operations.features.tasks.TasksApi
import com.vendistri.operations.features.tasks.VendiTask
import com.vendistri.operations.features.tasks.actions.TaskAssignee
import com.vendistri.operations.features.tasks.actions.TaskAssigneesApi

interface AddStopDataSource {
    suspend fun fetchMachines(): List<AddStopMachine>
    suspend fun fetchAssignees(): List<TaskAssignee>
    suspend fun bulkPrecheckTasks(items: List<TaskBulkPrecheckItem>): List<TaskBulkPrecheckResult>
    suspend fun bulkCreateTasks(items: List<TaskCreateRequest>): List<VendiTask>
}

class AddStopRepository(
    private val addStopApi: AddStopApi,
    private val taskAssigneesApi: TaskAssigneesApi,
    private val tasksApi: TasksApi
) : AddStopDataSource {
    override suspend fun fetchMachines(): List<AddStopMachine> {
        return addStopApi.fetchMachines()
    }

    override suspend fun fetchAssignees(): List<TaskAssignee> {
        return taskAssigneesApi.fetchAssignees()
    }

    override suspend fun bulkPrecheckTasks(items: List<TaskBulkPrecheckItem>): List<TaskBulkPrecheckResult> {
        return tasksApi.bulkPrecheck(items)
    }

    override suspend fun bulkCreateTasks(items: List<TaskCreateRequest>): List<VendiTask> {
        return tasksApi.bulkCreate(items)
    }
}
