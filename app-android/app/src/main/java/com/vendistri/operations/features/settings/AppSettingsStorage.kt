package com.vendistri.operations.features.settings

interface AppSettingsStorage {
    fun read(): AppSettingsState
    fun write(state: AppSettingsState)
}
