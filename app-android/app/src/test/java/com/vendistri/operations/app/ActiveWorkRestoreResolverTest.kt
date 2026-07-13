package com.vendistri.operations.app

import com.vendistri.operations.features.work.ActiveWorkSession
import com.vendistri.operations.features.work.LocalActiveExecutionSession
import com.vendistri.operations.features.work.WorkDestinationKind
import com.vendistri.operations.features.work.WorkPhase
import com.vendistri.operations.storage.CurrentRestoreSnapshotSchemaVersion
import com.vendistri.operations.storage.RestoreSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveWorkRestoreResolverTest {
    @Test
    fun matchingDeviceAndUserRestoresLocalSessionAndPersistedWork() {
        val localSession = localSession(deviceId = "android-device", userId = "demo-user")
        val snapshot = RestoreSnapshot(
            schemaVersion = CurrentRestoreSnapshotSchemaVersion,
            activeWorkSession = activeWorkSession(),
            activeWorkPhase = WorkPhase.NavigatingToLocation,
            localActiveExecutionSession = localSession
        )

        val decision = ActiveWorkRestoreResolver.resolve(
            snapshot = snapshot,
            currentDeviceId = "android-device",
            currentUserId = "demo-user"
        )

        assertSame(localSession, decision.localSession)
        assertTrue(decision.shouldRestorePersistedWork)
        assertFalse(decision.shouldClearSnapshot)
        assertEquals(WorkPhase.AtLocation, decision.restoredPhase)
    }

    @Test
    fun mismatchedDeviceRejectsAndClearsPersistedActiveWork() {
        val snapshot = RestoreSnapshot(
            schemaVersion = CurrentRestoreSnapshotSchemaVersion,
            activeWorkSession = activeWorkSession(),
            activeWorkPhase = WorkPhase.NavigatingToLocation,
            localActiveExecutionSession = localSession(deviceId = "ios-simulator", userId = "demo-user")
        )

        val decision = ActiveWorkRestoreResolver.resolve(
            snapshot = snapshot,
            currentDeviceId = "android-device",
            currentUserId = "demo-user"
        )

        assertNull(decision.localSession)
        assertFalse(decision.shouldRestorePersistedWork)
        assertTrue(decision.shouldClearSnapshot)
        assertNull(decision.restoredPhase)
    }

    @Test
    fun missingLocalSessionDoesNotRestorePersistedActiveWork() {
        val snapshot = RestoreSnapshot(
            schemaVersion = CurrentRestoreSnapshotSchemaVersion,
            activeWorkSession = activeWorkSession(),
            activeWorkPhase = WorkPhase.NavigatingToLocation,
            localActiveExecutionSession = null
        )

        val decision = ActiveWorkRestoreResolver.resolve(
            snapshot = snapshot,
            currentDeviceId = "android-device",
            currentUserId = "demo-user"
        )

        assertNull(decision.localSession)
        assertFalse(decision.shouldRestorePersistedWork)
        assertFalse(decision.shouldClearSnapshot)
    }

    @Test
    fun mismatchedUserRejectsAndClearsPersistedActiveWork() {
        val snapshot = RestoreSnapshot(
            schemaVersion = CurrentRestoreSnapshotSchemaVersion,
            activeWorkSession = activeWorkSession(),
            activeWorkPhase = WorkPhase.NavigatingToLocation,
            localActiveExecutionSession = localSession(deviceId = "android-device", userId = "other-user")
        )

        val decision = ActiveWorkRestoreResolver.resolve(
            snapshot = snapshot,
            currentDeviceId = "android-device",
            currentUserId = "demo-user"
        )

        assertNull(decision.localSession)
        assertFalse(decision.shouldRestorePersistedWork)
        assertTrue(decision.shouldClearSnapshot)
    }

    @Test
    fun legacySnapshotRejectsEvenMatchingLocalSessionAndClearsPersistedActiveWork() {
        val snapshot = RestoreSnapshot(
            schemaVersion = 0,
            activeWorkSession = activeWorkSession(),
            activeWorkPhase = WorkPhase.NavigatingToLocation,
            localActiveExecutionSession = localSession(deviceId = "android-device", userId = "demo-user")
        )

        val decision = ActiveWorkRestoreResolver.resolve(
            snapshot = snapshot,
            currentDeviceId = "android-device",
            currentUserId = "demo-user"
        )

        assertNull(decision.localSession)
        assertFalse(decision.shouldRestorePersistedWork)
        assertTrue(decision.shouldClearSnapshot)
        assertNull(decision.restoredPhase)
    }

    @Test
    fun preparingRouteSessionDoesNotRestoreAndClearsPersistedActiveWork() {
        val snapshot = RestoreSnapshot(
            schemaVersion = CurrentRestoreSnapshotSchemaVersion,
            activeWorkSession = activeWorkSession(),
            activeWorkPhase = WorkPhase.PreparingRoute,
            localActiveExecutionSession = localSession(
                deviceId = "android-device",
                userId = "demo-user",
                phase = WorkPhase.PreparingRoute
            )
        )

        val decision = ActiveWorkRestoreResolver.resolve(
            snapshot = snapshot,
            currentDeviceId = "android-device",
            currentUserId = "demo-user"
        )

        assertNull(decision.localSession)
        assertFalse(decision.shouldRestorePersistedWork)
        assertTrue(decision.shouldClearSnapshot)
        assertNull(decision.restoredPhase)
    }

    private fun activeWorkSession(): ActiveWorkSession {
        return ActiveWorkSession(
            id = "location:texas-roadhouse",
            title = "Texas Roadhouse",
            locationId = "texas-roadhouse",
            activeTaskIds = setOf("task-1"),
            destinationKind = WorkDestinationKind.Location
        )
    }

    private fun localSession(
        deviceId: String,
        userId: String,
        phase: WorkPhase = WorkPhase.AtLocation
    ): LocalActiveExecutionSession {
        return LocalActiveExecutionSession(
            deviceId = deviceId,
            userId = userId,
            stopId = "location:texas-roadhouse",
            locationId = "texas-roadhouse",
            taskIds = setOf("task-1"),
            currentTaskId = "task-1",
            phase = phase,
            startedAtEpochMillis = 1L
        )
    }
}
