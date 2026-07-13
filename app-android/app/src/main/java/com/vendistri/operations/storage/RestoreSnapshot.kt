package com.vendistri.operations.storage

import com.vendistri.operations.features.work.ActiveWorkSession
import com.vendistri.operations.features.work.LocalActiveExecutionSession
import com.vendistri.operations.features.work.PostPickupDestination
import com.vendistri.operations.features.work.WorkPhase

const val CurrentRestoreSnapshotSchemaVersion = 2

data class RestoreSnapshot(
    val schemaVersion: Int = CurrentRestoreSnapshotSchemaVersion,
    val activeWorkSession: ActiveWorkSession? = null,
    val activeWorkPhase: WorkPhase? = null,
    val activeNavigationStopId: String? = null,
    val localActiveExecutionSession: LocalActiveExecutionSession? = null,
    val postPickupDestination: PostPickupDestination? = null
)
