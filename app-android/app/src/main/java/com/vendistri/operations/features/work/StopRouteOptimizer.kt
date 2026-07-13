package com.vendistri.operations.features.work

import com.vendistri.operations.features.map.LocationCoordinate

object StopRouteOptimizer {
    fun orderStops(
        currentCoordinate: LocationCoordinate?,
        stops: List<GoStopPlan>
    ): List<GoStopPlan> {
        if (currentCoordinate == null) {
            return stops.sortedBy { it.title.lowercase() }
        }
        return stops.sortedWith(
            compareBy<GoStopPlan> { stop ->
                stop.coordinate?.let { RoutePreviewEstimator.distanceMiles(currentCoordinate, it) } ?: Double.MAX_VALUE
            }.thenBy { it.title.lowercase() }
        )
    }
}
