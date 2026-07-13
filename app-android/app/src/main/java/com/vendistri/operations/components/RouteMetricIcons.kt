package com.vendistri.operations.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vendistri.operations.R
import com.vendistri.operations.design.AppColors
import com.vendistri.operations.design.LocalVendistriPalette

enum class RouteMetricIconKind {
    Time,
    Distance,
    Checklist
}

@Composable
fun RouteMetricIcon(
    kind: RouteMetricIconKind,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    tint: Color = Color.Unspecified
) {
    Icon(
        painter = painterResource(
            when (kind) {
                RouteMetricIconKind.Time -> R.drawable.ic_clock_filled
                RouteMetricIconKind.Distance -> R.drawable.ic_route_arrow_filled
                RouteMetricIconKind.Checklist -> R.drawable.ic_checklist
            }
        ),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size)
    )
}

@Composable
fun RouteMetricText(
    kind: RouteMetricIconKind,
    text: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 15.dp,
    compact: Boolean = false,
    large: Boolean = false,
    textColor: Color = LocalVendistriPalette.current.textPrimary
) {
    val resolvedIconSize = if (compact) 13.dp else iconSize
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RouteMetricIcon(kind = kind, size = resolvedIconSize)
        Text(
            text,
            color = textColor,
            style = when {
                large -> MaterialTheme.typography.titleMedium
                compact -> MaterialTheme.typography.labelLarge
                else -> MaterialTheme.typography.bodyMedium
            },
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun RouteTravelSummaryRow(
    durationText: String,
    arrivalDistanceText: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalVendistriPalette.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RouteMetricText(
            kind = RouteMetricIconKind.Time,
            text = durationText,
            iconSize = 14.dp,
            compact = true,
            textColor = palette.textPrimary
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = arrivalDistanceText,
            color = AppColors.muted,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}
