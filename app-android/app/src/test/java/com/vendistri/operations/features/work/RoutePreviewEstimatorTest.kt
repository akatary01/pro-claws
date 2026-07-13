package com.vendistri.operations.features.work

import com.vendistri.operations.features.map.LocationCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePreviewEstimatorTest {
    @Test
    fun previewRouteReturnsNullWithoutCurrentCoordinate() {
        val preview = RoutePreviewEstimator.previewRoute(
            origin = null,
            destination = LocationCoordinate(40.7128, -74.0060)
        )

        assertNull(preview)
    }

    @Test
    fun previewRouteEstimatesDistanceAndMinimumDriveTime() {
        val preview = RoutePreviewEstimator.previewRoute(
            origin = LocationCoordinate(40.7128, -74.0060),
            destination = LocationCoordinate(40.7306, -73.9352)
        )

        assertTrue((preview?.distanceMiles ?: 0.0) > 3.0)
        assertTrue((preview?.expectedTravelSeconds ?: 0.0) >= 60.0)
    }

    @Test
    fun distanceMilesMatchesKnownNearbyDistance() {
        val distance = RoutePreviewEstimator.distanceMiles(
            from = LocationCoordinate(40.7128, -74.0060),
            to = LocationCoordinate(40.7306, -73.9352)
        )

        assertEquals(3.9, distance, 0.3)
    }
}
