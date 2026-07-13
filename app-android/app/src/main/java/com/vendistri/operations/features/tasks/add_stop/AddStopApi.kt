package com.vendistri.operations.features.tasks.add_stop

import com.vendistri.operations.network.ApiClient
import com.vendistri.operations.network.HttpMethod

class AddStopApi(
    private val apiClient: ApiClient
) {
    suspend fun fetchMachines(): List<AddStopMachine> {
        return AddStopMachine.listFromJson(apiClient.request(HttpMethod.Get, "/machine/all").body)
    }
}
