package com.vendistri.operations.features.map

import androidx.compose.ui.graphics.Color

data class LocationCoordinate(
    val latitude: Double,
    val longitude: Double
)

enum class LocationStopAction {
    OpenTasks,
    Go,
    ClaimTasks
}

data class LocationStop(
    val id: String,
    val name: String,
    val addressStreetLine: String?,
    val addressCityStateZipLine: String?,
    val coordinate: LocationCoordinate,
    val color: Color,
    val hasPending: Boolean,
    val hasDone: Boolean,
    val hasCancelled: Boolean,
    val pendingCount: Int,
    val unassignedCount: Int,
    val doneCount: Int,
    val cancelledCount: Int,
    val totalCount: Int,
    val machineCount: Int,
    val gross: Double,
    val refunds: Double,
    val commission: Double,
    val net: Double,
    val action: LocationStopAction,
    val assigneeSummary: String?,
    val commissionPaymentSummary: String?
)
