package com.vendistri.operations.features.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vendistri.operations.components.RouteMetricIconKind
import com.vendistri.operations.components.RouteMetricText

@Composable
internal fun TaskRollupMetricsRow(
    durationMinutes: Double,
    distanceMiles: Double,
    modifier: Modifier = Modifier,
    spacing: Dp = 14.dp,
    compact: Boolean = false
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RouteMetricText(
            kind = RouteMetricIconKind.Time,
            text = formatDuration(durationMinutes),
            compact = compact
        )
        RouteMetricText(
            kind = RouteMetricIconKind.Distance,
            text = "${oneDecimal(distanceMiles.coerceAtLeast(0.0))} mi",
            compact = compact
        )
    }
}
