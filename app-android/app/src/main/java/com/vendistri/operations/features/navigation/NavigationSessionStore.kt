package com.vendistri.operations.features.navigation

import com.vendistri.operations.features.map.LocationCoordinate
import com.vendistri.operations.features.settings.NavigationAudioPreference
import com.vendistri.operations.features.work.RoutePreviewEstimator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val OdometerMinimumDeltaMeters = 3.0
private const val OdometerMaximumDeltaSeconds = 30.0
private const val OdometerMaximumSpeedMetersPerSecond = 70.0
private const val MetersPerMile = 1609.344

class NavigationSessionStore {
    private val _state = MutableStateFlow(NavigationSessionState())
    val state: StateFlow<NavigationSessionState> = _state.asStateFlow()

    fun start(
        stopId: String?,
        audioPreference: NavigationAudioPreference = NavigationAudioPreference.Sound,
        currentInstructionText: String? = null,
        traveledDistanceMiles: Double = 0.0
    ) {
        if (stopId.isNullOrBlank()) return
        _state.value = NavigationSessionState(
            activeStopId = stopId,
            isNavigating = true,
            audioPreference = audioPreference,
            currentInstructionText = currentInstructionText,
            traveledDistanceMiles = traveledDistanceMiles.coerceAtLeast(0.0)
        )
    }

    fun applyAudioPreference(audioPreference: NavigationAudioPreference): NavigationAudioPreferenceChange {
        val state = _state.value
        if (!state.isNavigating || state.activeStopId == null) return NavigationAudioPreferenceChange()
        val shouldAnnounce = state.audioPreference == NavigationAudioPreference.Silent &&
            audioPreference == NavigationAudioPreference.Sound &&
            !state.currentInstructionText.isNullOrBlank()
        _state.value = state.copy(audioPreference = audioPreference)
        return NavigationAudioPreferenceChange(shouldAnnounceCurrentInstruction = shouldAnnounce)
    }

    fun updateCurrentInstruction(instructionText: String?) {
        val state = _state.value
        if (!state.isNavigating || state.activeStopId == null) return
        _state.value = state.copy(currentInstructionText = instructionText)
    }

    fun setRerouting(isRerouting: Boolean) {
        val state = _state.value
        if (!state.isNavigating || state.activeStopId == null || state.isRerouting == isRerouting) return
        _state.value = state.copy(isRerouting = isRerouting)
    }

    fun updateRouteProgress(
        instructionText: String?,
        distanceRemainingMiles: Double?,
        durationRemainingSeconds: Double?,
        travelTimeTrafficLevel: NavigationTravelTimeTrafficLevel = NavigationTravelTimeTrafficLevel.Clear,
        trafficAlert: NavigationTrafficAlert? = null,
        roadNameText: String? = null,
        currentSpeed: NavigationCurrentSpeedDisplay? = null,
        speedLimit: NavigationSpeedLimitDisplay? = null,
        currentInstruction: NavigationInstructionStep? = null,
        futureInstructionSteps: List<NavigationInstructionStep> = emptyList()
    ): Double? {
        val state = _state.value
        if (!state.isNavigating || state.activeStopId == null) return null
        val previousRemaining = state.distanceRemainingMiles
        val remainingDelta = if (
            state.lastOdometerCoordinate == null &&
            previousRemaining != null &&
            distanceRemainingMiles != null &&
            distanceRemainingMiles < previousRemaining
        ) {
            previousRemaining - distanceRemainingMiles
        } else {
            0.0
        }
        val traveledDistanceMiles = state.traveledDistanceMiles + remainingDelta.coerceAtLeast(0.0)
        _state.value = state.copy(
            currentInstructionText = instructionText?.takeIf { it.isNotBlank() } ?: state.currentInstructionText,
            distanceRemainingMiles = distanceRemainingMiles ?: state.distanceRemainingMiles,
            durationRemainingSeconds = durationRemainingSeconds ?: state.durationRemainingSeconds,
            travelTimeTrafficLevel = travelTimeTrafficLevel,
            trafficAlert = trafficAlert,
            roadNameText = roadNameText ?: state.roadNameText,
            currentSpeed = currentSpeed,
            speedLimit = speedLimit,
            currentInstruction = currentInstruction ?: state.currentInstruction,
            futureInstructionSteps = futureInstructionSteps,
            traveledDistanceMiles = traveledDistanceMiles
        )
        return traveledDistanceMiles.takeIf { it > state.traveledDistanceMiles }
    }

    fun recordLocation(
        coordinate: LocationCoordinate,
        timestampEpochMillis: Long = System.currentTimeMillis()
    ): Double? {
        val state = _state.value
        if (!state.isNavigating || state.activeStopId == null) return null
        val previousCoordinate = state.lastOdometerCoordinate
        val previousTimestamp = state.lastOdometerTimestampEpochMillis
        if (previousCoordinate == null || previousTimestamp == null) {
            _state.value = state.copy(
                lastOdometerCoordinate = coordinate,
                lastOdometerTimestampEpochMillis = timestampEpochMillis
            )
            return null
        }

        val elapsedSeconds = (timestampEpochMillis - previousTimestamp) / 1000.0
        if (elapsedSeconds <= 0.0 || elapsedSeconds > OdometerMaximumDeltaSeconds) {
            _state.value = state.copy(
                lastOdometerCoordinate = coordinate,
                lastOdometerTimestampEpochMillis = timestampEpochMillis
            )
            return null
        }

        val deltaMeters = RoutePreviewEstimator.distanceMiles(previousCoordinate, coordinate) * MetersPerMile
        if (deltaMeters < OdometerMinimumDeltaMeters) return null
        if (deltaMeters / elapsedSeconds > OdometerMaximumSpeedMetersPerSecond) {
            _state.value = state.copy(
                lastOdometerCoordinate = coordinate,
                lastOdometerTimestampEpochMillis = timestampEpochMillis
            )
            return null
        }

        val traveledDistanceMiles = state.traveledDistanceMiles + (deltaMeters / MetersPerMile)
        _state.value = state.copy(
            traveledDistanceMiles = traveledDistanceMiles,
            lastOdometerCoordinate = coordinate,
            lastOdometerTimestampEpochMillis = timestampEpochMillis
        )
        return traveledDistanceMiles
    }

    fun reset() {
        _state.value = NavigationSessionState()
    }
}

data class NavigationSessionState(
    val activeStopId: String? = null,
    val isNavigating: Boolean = false,
    val audioPreference: NavigationAudioPreference = NavigationAudioPreference.Sound,
    val currentInstructionText: String? = null,
    val distanceRemainingMiles: Double? = null,
    val durationRemainingSeconds: Double? = null,
    val travelTimeTrafficLevel: NavigationTravelTimeTrafficLevel = NavigationTravelTimeTrafficLevel.Clear,
    val trafficAlert: NavigationTrafficAlert? = null,
    val roadNameText: String? = null,
    val currentSpeed: NavigationCurrentSpeedDisplay? = null,
    val speedLimit: NavigationSpeedLimitDisplay? = null,
    val currentInstruction: NavigationInstructionStep? = null,
    val futureInstructionSteps: List<NavigationInstructionStep> = emptyList(),
    val isRerouting: Boolean = false,
    val traveledDistanceMiles: Double = 0.0,
    val lastOdometerCoordinate: LocationCoordinate? = null,
    val lastOdometerTimestampEpochMillis: Long? = null
)

enum class NavigationTravelTimeTrafficLevel {
    Clear,
    SomeDelay,
    HeavyDelay
}

data class NavigationTrafficAlert(
    val id: String,
    val kind: Kind,
    val title: String,
    val subtitle: String
) {
    enum class Kind {
        Accident,
        Closure,
        Construction,
        Congestion,
        Hazard,
        Weather,
        General
    }
}

data class NavigationSpeedLimitDisplay(
    val value: Double,
    val valueText: String,
    val unitText: String
)

data class NavigationCurrentSpeedDisplay(
    val value: Double,
    val valueText: String,
    val unitText: String
)

data class NavigationInstructionStep(
    val primaryText: String,
    val secondaryText: String? = null,
    val distanceMeters: Double,
    val maneuverType: String? = null,
    val maneuverModifier: String? = null,
    val exitCode: String? = null,
    val primaryComponents: List<NavigationInstructionComponent> = emptyList(),
    val secondaryComponents: List<NavigationInstructionComponent> = emptyList()
)

data class NavigationInstructionComponent(
    val text: String,
    val kind: Kind = Kind.Text
) {
    enum class Kind {
        Text,
        Delimiter,
        Shield,
        Image
    }
}

data class NavigationAudioPreferenceChange(
    val shouldAnnounceCurrentInstruction: Boolean = false
)
