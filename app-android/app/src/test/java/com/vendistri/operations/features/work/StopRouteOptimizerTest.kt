package com.vendistri.operations.features.work

import com.vendistri.operations.features.map.LocationCoordinate
import org.junit.Assert.assertEquals
import org.junit.Test

class StopRouteOptimizerTest {
    @Test
    fun orderStopsSortsByTitleWithoutCurrentCoordinate() {
        val stops = listOf(stop(id = "b", title = "Beta"), stop(id = "a", title = "Alpha"))

        val ordered = StopRouteOptimizer.orderStops(currentCoordinate = null, stops = stops)

        assertEquals(listOf("a", "b"), ordered.map { it.id })
    }

    @Test
    fun orderStopsPrefersNearestStopWithCurrentCoordinate() {
        val origin = LocationCoordinate(latitude = 40.7128, longitude = -74.0060)
        val stops = listOf(
            stop(id = "far", title = "Philadelphia", coordinate = LocationCoordinate(39.9526, -75.1652)),
            stop(id = "near", title = "Brooklyn", coordinate = LocationCoordinate(40.6782, -73.9442))
        )

        val ordered = StopRouteOptimizer.orderStops(currentCoordinate = origin, stops = stops)

        assertEquals(listOf("near", "far"), ordered.map { it.id })
    }

    private fun stop(
        id: String,
        title: String,
        coordinate: LocationCoordinate = LocationCoordinate(40.0, -74.0)
    ): GoStopPlan {
        return GoStopPlan(
            id = id,
            targetLocationId = id,
            title = title,
            addressStreetLine = null,
            addressCityStateZipLine = null,
            tasks = emptyList(),
            nodes = listOf(
                GoNode(
                    id = "node-$id",
                    type = GoNodeType.Location,
                    title = title,
                    subtitle = null,
                    coordinate = coordinate,
                    locationId = id,
                    taskIds = emptyList()
                )
            ),
            machineGroups = emptyList(),
            gross = 0.0,
            refunds = 0.0,
            commission = 0.0,
            net = 0.0
        )
    }
}
