package com.vendistri.operations.features.map

import com.vendistri.operations.features.work.WorkPhase
import com.vendistri.operations.features.work.WorkUiState

data class MapRouteOverlay(
    val id: String,
    val points: List<LocationCoordinate>
)

object MapRouteOverlayBuilder {
    fun fromWorkState(state: WorkUiState): MapRouteOverlay? {
        if (state.phase == WorkPhase.Idle) return null

        val stop = state.selectedStop ?: return null
        val points = buildList {
            state.activeSession?.coordinate?.let(::add)
            stop.nodes.forEach { node -> add(node.coordinate) }
            if (isEmpty()) {
                stop.coordinate?.let(::add)
            }
        }.distinctConsecutive()

        if (points.size < 2) return null
        return MapRouteOverlay(
            id = "work-route:${stop.id}:${state.phase.name}",
            points = points
        )
    }
}

private fun List<LocationCoordinate>.distinctConsecutive(): List<LocationCoordinate> {
    return fold(emptyList()) { acc, coordinate ->
        if (acc.lastOrNull() == coordinate) acc else acc + coordinate
    }
}
