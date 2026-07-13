package com.vendistri.operations.features.tasks.actions

import com.vendistri.operations.network.ApiClient
import com.vendistri.operations.network.HttpMethod

class TaskAssigneesApi(
    private val apiClient: ApiClient
) {
    suspend fun fetchAssignees(): List<TaskAssignee> {
        return TaskAssignee.listFromJson(
            apiClient.request(HttpMethod.Get, "/user/operators").body
        )
    }
}
