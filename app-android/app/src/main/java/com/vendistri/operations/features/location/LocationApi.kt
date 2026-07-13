package com.vendistri.operations.features.location

import com.vendistri.operations.network.ApiClient
import com.vendistri.operations.network.HttpMethod

class LocationApi(
    private val apiClient: ApiClient
) {
    suspend fun fetchLocations(): List<AppLocation> {
        return AppLocation.listFromJson(apiClient.request(HttpMethod.Get, "/location/all").body)
    }

    suspend fun fetchWarehouses(): List<WarehouseOption> {
        return WarehouseOption.listFromJson(apiClient.request(HttpMethod.Get, "/location/warehouse/all").body)
    }

    suspend fun fetchPortalLocations(): List<AppLocation> {
        return AppLocation.listFromJson(apiClient.request(HttpMethod.Get, "/portal/locations").body)
    }

    suspend fun fetchPortalLocationMachines(locationId: String): List<PortalLocationMachine> {
        return PortalLocationMachine.listFromJson(
            apiClient.request(HttpMethod.Get, "/portal/location/machines?id=${locationId.urlEncoded()}").body
        )
    }
}

private fun String.urlEncoded(): String = java.net.URLEncoder.encode(this, "UTF-8")
