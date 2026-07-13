package com.vendistri.operations.features.location_contact

import com.vendistri.operations.features.auth.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppViewMode {
    Organization,
    LocationContact
}

data class AppModeUiState(
    val mode: AppViewMode = AppViewMode.Organization
) {
    val isContactMode: Boolean
        get() = mode == AppViewMode.LocationContact
}

class AppModeStore {
    private val _state = MutableStateFlow(AppModeUiState())
    val state: StateFlow<AppModeUiState> = _state.asStateFlow()

    fun reset() {
        _state.value = AppModeUiState()
    }

    fun setMode(mode: AppViewMode) {
        _state.value = AppModeUiState(mode)
    }

    fun syncDefaultMode(user: User?, hasContactLocations: Boolean) {
        val hasOrgMode = user?.let { it.isOwner || it.isAdmin || it.isOperator } == true
        if (!hasOrgMode && hasContactLocations) {
            setMode(AppViewMode.LocationContact)
        } else if (!hasContactLocations && state.value.isContactMode) {
            setMode(AppViewMode.Organization)
        }
    }
}
