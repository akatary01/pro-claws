package com.vendistri.operations.features.location

import com.vendistri.operations.network.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class LocationStore(
    private val locationApi: LocationApi = LocationApi(ApiClient())
) {
    private val _state = MutableStateFlow(LocationUiState())
    val state: StateFlow<LocationUiState> = _state.asStateFlow()

    fun resetUserScopedState() {
        _state.value = LocationUiState()
    }

    suspend fun loadLocations(force: Boolean = false) {
        val current = _state.value
        if (current.isLoading && !force) return
        if (current.hasLoadedOnce && !force) return

        _state.update { it.copy(isLoading = true, errorMessage = null) }
        try {
            val locations = locationApi.fetchLocations()
            val warehouses = locationApi.fetchWarehouses()
            _state.update {
                it.copy(
                    locations = locations,
                    locationsById = locations.associateBy { location -> location.id },
                    warehouses = warehouses.filter(WarehouseOption::isActive),
                    isLoading = false,
                    hasLoadedOnce = true
                )
            }
        } catch (error: Exception) {
            _state.update {
                it.copy(
                    isLoading = false,
                    hasLoadedOnce = true,
                    errorMessage = error.message ?: "Failed to load locations."
                )
            }
        }
    }
}

data class LocationUiState(
    val locations: List<AppLocation> = emptyList(),
    val locationsById: Map<String, AppLocation> = emptyMap(),
    val warehouses: List<WarehouseOption> = emptyList(),
    val isLoading: Boolean = false,
    val hasLoadedOnce: Boolean = false,
    val errorMessage: String? = null
)
