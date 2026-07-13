package com.vendistri.operations.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RestoreStateStore {
    private val _state = MutableStateFlow(RestoreState())
    val state: StateFlow<RestoreState> = _state.asStateFlow()

    constructor()

    constructor(snapshotStorage: RestoreSnapshotStorage) {
        storage = snapshotStorage
    }

    private var storage: RestoreSnapshotStorage? = null

    fun markRestoreAttempted() {
        _state.value = _state.value.copy(hasAttemptedRestore = true)
    }

    suspend fun restoreSnapshot(): RestoreSnapshot {
        val snapshot = storage?.read() ?: RestoreSnapshot()
        _state.value = RestoreState(
            hasAttemptedRestore = true,
            activeWorkSessionId = snapshot.activeWorkSession?.id,
            activeNavigationStopId = snapshot.activeNavigationStopId
        )
        return snapshot
    }

    suspend fun saveSnapshot(snapshot: RestoreSnapshot) {
        storage?.write(snapshot)
        _state.value = _state.value.copy(
            activeWorkSessionId = snapshot.activeWorkSession?.id,
            activeNavigationStopId = snapshot.activeNavigationStopId
        )
    }

    suspend fun clearSnapshot() {
        storage?.clear()
        _state.value = RestoreState(hasAttemptedRestore = _state.value.hasAttemptedRestore)
    }
}

data class RestoreState(
    val hasAttemptedRestore: Boolean = false,
    val activeWorkSessionId: String? = null,
    val activeNavigationStopId: String? = null
)
