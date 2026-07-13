package com.vendistri.operations.features.location_contact

import com.vendistri.operations.features.location.AppLocation
import com.vendistri.operations.features.location.LocationApi
import com.vendistri.operations.features.location.PortalLocationMachine
import com.vendistri.operations.network.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ContactUiState(
    val locationsById: Map<String, AppLocation> = emptyMap(),
    val machinesByLocationId: Map<String, List<PortalLocationMachine>> = emptyMap(),
    val isLoading: Boolean = false,
    val lastLoadError: String? = null,
    val revision: Int = 0
) {
    val hasLocations: Boolean
        get() = locationsById.isNotEmpty()

    val sortedLocations: List<AppLocation>
        get() = locationsById.values.sortedBy { it.name.lowercase() }
}

class ContactStore(
    private val locationApi: LocationApi = LocationApi(ApiClient())
) {
    private val _state = MutableStateFlow(ContactUiState())
    val state: StateFlow<ContactUiState> = _state.asStateFlow()

    fun resetUserScopedState() {
        _state.value = ContactUiState()
    }

    suspend fun loadLocations(force: Boolean = false) {
        if (!force && state.value.hasLocations) return
        _state.update { it.copy(isLoading = true, lastLoadError = null) }
        try {
            val locations = locationApi.fetchPortalLocations()
            _state.update {
                it.copy(
                    locationsById = locations.associateBy { location -> location.id },
                    isLoading = false,
                    lastLoadError = null,
                    revision = it.revision + 1
                )
            }
        } catch (error: Exception) {
            _state.update {
                it.copy(
                    isLoading = false,
                    lastLoadError = error.message ?: "Failed to load contact locations."
                )
            }
        }
    }

    suspend fun loadMachines(locationId: String, force: Boolean = false) {
        if (locationId.isBlank()) return
        if (!force && state.value.machinesByLocationId.containsKey(locationId)) return
        try {
            val machines = locationApi.fetchPortalLocationMachines(locationId)
            _state.update {
                it.copy(
                    machinesByLocationId = it.machinesByLocationId + (locationId to machines),
                    lastLoadError = null,
                    revision = it.revision + 1
                )
            }
        } catch (error: Exception) {
            _state.update {
                it.copy(lastLoadError = error.message ?: "Failed to load contact machines.")
            }
        }
    }

    suspend fun loadMachinesForLocations(locationIds: Collection<String>, force: Boolean = false) {
        locationIds.distinct().forEach { loadMachines(it, force) }
    }
}
