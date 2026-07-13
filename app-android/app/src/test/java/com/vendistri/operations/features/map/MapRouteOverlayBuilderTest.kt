package com.vendistri.operations.features.map

import com.vendistri.operations.features.work.ActiveWorkSession
import com.vendistri.operations.features.work.GoNode
import com.vendistri.operations.features.work.GoNodeType
import com.vendistri.operations.features.work.GoStopPlan
import com.vendistri.operations.features.work.WorkPhase
import com.vendistri.operations.features.work.WorkUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapRouteOverlayBuilderTest {
    @Test
    fun idleStateHasNoActiveRoute() {
        val route = MapRouteOverlayBuilder.fromWorkState(
            WorkUiState(
                phase = WorkPhase.Idle,
                selectedStop = stopPlan(
                    nodes = listOf(node("one", LocationCoordinate(40.0, -73.0)))
                )
            )
        )

        assertNull(route)
    }

    @Test
    fun singlePointRouteIsNotDrawn() {
        val route = MapRouteOverlayBuilder.fromWorkState(
            WorkUiState(
                phase = WorkPhase.NavigatingToLocation,
                selectedStop = stopPlan(
                    nodes = listOf(node("one", LocationCoordinate(40.0, -73.0)))
                )
            )
        )

        assertNull(route)
    }

    @Test
    fun activeRouteUsesSessionOriginAndStopNodes() {
        val origin = LocationCoordinate(40.0, -73.0)
        val destination = LocationCoordinate(41.0, -74.0)
        val route = MapRouteOverlayBuilder.fromWorkState(
            WorkUiState(
                phase = WorkPhase.NavigatingToLocation,
                activeSession = ActiveWorkSession(
                    id = "session-1",
                    title = "Downtown",
                    locationId = "location-1",
                    activeTaskIds = setOf("task-1"),
                    coordinate = origin
                ),
                selectedStop = stopPlan(
                    nodes = listOf(node("one", destination))
                )
            )
        )

        assertEquals(listOf(origin, destination), route?.points)
    }

    @Test
    fun duplicateNeighborCoordinatesAreCollapsed() {
        val first = LocationCoordinate(40.0, -73.0)
        val second = LocationCoordinate(41.0, -74.0)
        val route = MapRouteOverlayBuilder.fromWorkState(
            WorkUiState(
                phase = WorkPhase.PreparingRoute,
                activeSession = ActiveWorkSession(
                    id = "session-1",
                    title = "Downtown",
                    locationId = "location-1",
                    activeTaskIds = setOf("task-1"),
                    coordinate = first
                ),
                selectedStop = stopPlan(
                    nodes = listOf(
                        node("one", first),
                        node("two", second)
                    )
                )
            )
        )

        assertEquals(listOf(first, second), route?.points)
    }

    private fun stopPlan(nodes: List<GoNode>): GoStopPlan {
        return GoStopPlan(
            id = "location-1",
            targetLocationId = "location-1",
            title = "Downtown",
            addressStreetLine = null,
            addressCityStateZipLine = null,
            tasks = emptyList(),
            nodes = nodes,
            machineGroups = emptyList(),
            gross = 0.0,
            refunds = 0.0,
            commission = 0.0,
            net = 0.0
        )
    }

    private fun node(id: String, coordinate: LocationCoordinate): GoNode {
        return GoNode(
            id = id,
            type = GoNodeType.Location,
            title = id,
            subtitle = null,
            coordinate = coordinate,
            locationId = "location-1",
            taskIds = emptyList()
        )
    }
}
