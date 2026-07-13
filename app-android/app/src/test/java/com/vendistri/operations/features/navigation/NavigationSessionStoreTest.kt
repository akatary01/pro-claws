package com.vendistri.operations.features.navigation

import com.vendistri.operations.features.map.LocationCoordinate
import com.vendistri.operations.features.settings.NavigationAudioPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationSessionStoreTest {
    @Test
    fun recordLocationAccumulatesDistanceForActiveNavigation() {
        val store = NavigationSessionStore()
        store.start("location-1")

        assertNull(store.recordLocation(LocationCoordinate(40.7128, -74.0060), timestampEpochMillis = 1_000L))
        val distance = store.recordLocation(LocationCoordinate(40.7138, -74.0060), timestampEpochMillis = 11_000L)

        checkNotNull(distance)
        assertEquals(distance, store.state.value.traveledDistanceMiles, 0.0001)
    }

    @Test
    fun recordLocationIgnoresTinyMovement() {
        val store = NavigationSessionStore()
        store.start("location-1")

        store.recordLocation(LocationCoordinate(40.7128, -74.0060), timestampEpochMillis = 1_000L)
        val distance = store.recordLocation(LocationCoordinate(40.71281, -74.0060), timestampEpochMillis = 11_000L)

        assertNull(distance)
        assertEquals(0.0, store.state.value.traveledDistanceMiles, 0.0001)
    }

    @Test
    fun recordLocationResetsPreviousPointAfterLargeTimeGap() {
        val store = NavigationSessionStore()
        store.start("location-1")

        store.recordLocation(LocationCoordinate(40.7128, -74.0060), timestampEpochMillis = 1_000L)
        val distance = store.recordLocation(LocationCoordinate(40.7300, -74.0060), timestampEpochMillis = 40_000L)

        assertNull(distance)
        assertEquals(0.0, store.state.value.traveledDistanceMiles, 0.0001)
        assertEquals(LocationCoordinate(40.7300, -74.0060), store.state.value.lastOdometerCoordinate)
    }

    @Test
    fun resetClearsActiveNavigationAndOdometer() {
        val store = NavigationSessionStore()
        store.start("location-1")
        store.recordLocation(LocationCoordinate(40.7128, -74.0060), timestampEpochMillis = 1_000L)
        store.recordLocation(LocationCoordinate(40.7138, -74.0060), timestampEpochMillis = 11_000L)

        store.reset()

        assertEquals(NavigationSessionState(), store.state.value)
    }

    @Test
    fun startCopiesDefaultAudioPreferenceIntoCurrentNavigation() {
        val store = NavigationSessionStore()

        store.start("location-1", audioPreference = NavigationAudioPreference.Silent)

        assertEquals(NavigationAudioPreference.Silent, store.state.value.audioPreference)
    }

    @Test
    fun applyAudioPreferenceChangesOnlyCurrentNavigationSession() {
        val store = NavigationSessionStore()
        store.start(
            stopId = "location-1",
            audioPreference = NavigationAudioPreference.Silent,
            currentInstructionText = "Head to the location."
        )

        store.applyAudioPreference(NavigationAudioPreference.Sound)

        assertEquals(NavigationAudioPreference.Sound, store.state.value.audioPreference)
        assertEquals("location-1", store.state.value.activeStopId)
        assertEquals(true, store.state.value.isNavigating)
    }

    @Test
    fun applyAudioPreferenceReportsAnnouncementWhenTurningSoundOn() {
        val store = NavigationSessionStore()
        store.start(
            stopId = "location-1",
            audioPreference = NavigationAudioPreference.Silent,
            currentInstructionText = "Head to the location."
        )

        val change = store.applyAudioPreference(NavigationAudioPreference.Sound)

        assertEquals(true, change.shouldAnnounceCurrentInstruction)
    }

    @Test
    fun updateCurrentInstructionStoresActiveNavigationInstruction() {
        val store = NavigationSessionStore()
        store.start("location-1")

        store.updateCurrentInstruction("Continue to the location.")

        assertEquals("Continue to the location.", store.state.value.currentInstructionText)
    }

    @Test
    fun updateRouteProgressStoresMapboxInstructionAndRemainingTripMetrics() {
        val store = NavigationSessionStore()
        store.start("location-1")

        store.updateRouteProgress(
            instructionText = "Turn right on Hylan Boulevard.",
            distanceRemainingMiles = 0.42,
            durationRemainingSeconds = 180.0
        )

        assertEquals("Turn right on Hylan Boulevard.", store.state.value.currentInstructionText)
        assertEquals(0.42, store.state.value.distanceRemainingMiles ?: 0.0, 0.0001)
        assertEquals(180.0, store.state.value.durationRemainingSeconds ?: 0.0, 0.0001)
    }
}
