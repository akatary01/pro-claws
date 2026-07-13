package com.vendistri.operations.app

import com.vendistri.operations.features.work.LocalActiveExecutionSession
import com.vendistri.operations.features.work.WorkPhase
import com.vendistri.operations.storage.CurrentRestoreSnapshotSchemaVersion
import com.vendistri.operations.storage.RestoreSnapshot

data class ActiveWorkRestoreDecision(
    val localSession: LocalActiveExecutionSession?,
    val shouldRestorePersistedWork: Boolean,
    val shouldClearSnapshot: Boolean
) {
    val restoredPhase: WorkPhase?
        get() = localSession?.phase
}

object ActiveWorkRestoreResolver {
    fun resolve(
        snapshot: RestoreSnapshot,
        currentDeviceId: String,
        currentUserId: String?
    ): ActiveWorkRestoreDecision {
        val rawLocalSession = snapshot.localActiveExecutionSession
        val isTrustedSnapshot = snapshot.schemaVersion >= CurrentRestoreSnapshotSchemaVersion
        val matchingLocalSession = rawLocalSession?.takeIf {
            isTrustedSnapshot && it.deviceId == currentDeviceId && it.userId == currentUserId
        }
        val restorableLocalSession = matchingLocalSession?.takeIf { it.phase != WorkPhase.PreparingRoute }
        val hasRejectedLocalSession = rawLocalSession != null && matchingLocalSession == null
        val hasUnstartedLocalSession = matchingLocalSession?.phase == WorkPhase.PreparingRoute
        val hasPersistedActiveWork = snapshot.activeWorkSession != null

        return ActiveWorkRestoreDecision(
            localSession = restorableLocalSession,
            shouldRestorePersistedWork = restorableLocalSession != null,
            shouldClearSnapshot = hasPersistedActiveWork && (!isTrustedSnapshot || hasRejectedLocalSession || hasUnstartedLocalSession)
        )
    }
}
