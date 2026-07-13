package com.vendistri.operations.features.live_status

enum class LiveStatusMode {
    Navigating,
    Rerouting,
    Approaching,
    Arrived,
    AtLocation,
    AtWarehouse
}

data class LiveStatusSnapshot(
    val sessionId: String,
    val stopId: String,
    val mode: LiveStatusMode,
    val title: String,
    val destination: String,
    val address: String? = null,
    val primaryStatus: String,
    val secondaryStatus: String? = null,
    val nextInstruction: String? = null,
    val etaText: String? = null,
    val distanceRemainingText: String? = null,
    val progressCurrent: Int? = null,
    val progressTotal: Int? = null,
    val isRerouting: Boolean = false
)
