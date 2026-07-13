package com.vendistri.operations.features.work

import com.vendistri.operations.features.map.LocationCoordinate
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object RoutePreviewEstimator {
    private const val EarthRadiusMiles = 3958.8
    private const val EstimatedMetersPerSecond = 13.4
    private const val MetersPerMile = 1609.344
    private const val MinimumTravelSeconds = 60.0

    fun previewRoute(origin: LocationCoordinate?, destination: GoStopPlan): RoutePreview? {
        val stopCoordinate = destination.coordinate ?: return null
        return previewRoute(origin = origin, destination = stopCoordinate)
    }

    fun previewRoute(origin: LocationCoordinate?, destination: LocationCoordinate): RoutePreview? {
        return previewRoute(origin = origin, destinations = listOf(destination))
    }

    fun previewRoute(origin: LocationCoordinate?, destinations: List<LocationCoordinate>): RoutePreview? {
        val current = origin ?: return null
        val validDestinations = destinations.filter { it.latitude.isFinite() && it.longitude.isFinite() }
        if (validDestinations.isEmpty()) return null
        var previous = current
        var preview: RoutePreview? = null
        validDestinations.forEach { destination ->
            val distanceMiles = distanceMiles(previous, destination).coerceAtLeast(0.0)
            val legPreview = RoutePreview(
                distanceMiles = distanceMiles,
                expectedTravelSeconds = (distanceMiles * MetersPerMile / EstimatedMetersPerSecond)
                    .coerceAtLeast(MinimumTravelSeconds)
            )
            preview = preview?.adding(legPreview) ?: legPreview
            previous = destination
        }
        return preview
    }

    fun distanceMiles(from: LocationCoordinate, to: LocationCoordinate): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val deltaLat = Math.toRadians(to.latitude - from.latitude)
        val deltaLng = Math.toRadians(to.longitude - from.longitude)
        val a = sin(deltaLat / 2).pow(2.0) +
            cos(lat1) * cos(lat2) * sin(deltaLng / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EarthRadiusMiles * c
    }
}
