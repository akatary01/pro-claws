package com.vendistri.operations.features.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AppSettingsStore(
    private val storage: AppSettingsStorage? = null,
    initialState: AppSettingsState = storage?.read() ?: AppSettingsState()
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<AppSettingsState> = _state.asStateFlow()

    fun setAutoCalcCommission(enabled: Boolean) {
        updateSettings { it.copy(autoCalcCommission = enabled) }
    }

    fun setAutoFillRefillFinalStock(enabled: Boolean) {
        updateSettings { it.copy(autoFillRefillFinalStock = enabled) }
    }

    fun setAppearancePreference(preference: AppAppearancePreference) {
        updateSettings { it.copy(appearancePreference = preference) }
    }

    fun setNavigationAudioPreference(preference: NavigationAudioPreference) {
        updateSettings { it.copy(navigationAudioPreference = preference) }
    }

    fun setTimeFormatPreference(preference: TimeFormatPreference) {
        updateSettings { it.copy(timeFormatPreference = preference) }
    }

    private fun updateSettings(transform: (AppSettingsState) -> AppSettingsState) {
        _state.update { current ->
            transform(current).also { storage?.write(it) }
        }
    }
}
