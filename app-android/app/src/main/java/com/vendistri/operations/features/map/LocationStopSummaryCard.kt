package com.vendistri.operations.features.map

import android.text.format.DateFormat
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vendistri.operations.components.RevenueChip
import com.vendistri.operations.components.RouteTravelSummaryRow
import com.vendistri.operations.design.AppColors
import com.vendistri.operations.design.LocalVendistriPalette
import com.vendistri.operations.features.location.AppLocation
import com.vendistri.operations.features.location.LocationHours
import com.vendistri.operations.features.location.LocationHoursLabel
import com.vendistri.operations.features.settings.AppTimeFormatter
import com.vendistri.operations.features.settings.TimeFormatPreference
import com.vendistri.operations.features.tasks.LocationStatusLineView
import com.vendistri.operations.features.tasks.LocationStatusRows
import com.vendistri.operations.features.tasks.TaskStatusCounts
import com.vendistri.operations.features.tasks.formatDuration
import com.vendistri.operations.features.tasks.money
import com.vendistri.operations.features.tasks.oneDecimal
import com.vendistri.operations.features.work.RoutePreview

@Composable
fun LocationStopSummaryCard(
    stop: LocationStop,
    appLocation: AppLocation?,
    routePreview: RoutePreview?,
    timeFormatPreference: TimeFormatPreference,
    onOpenTasks: () -> Unit,
    onOpenLocation: (() -> Unit)? = null,
    onGo: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalVendistriPalette.current
    val showsRouteEstimate = stop.action == LocationStopAction.Go || stop.action == LocationStopAction.ClaimTasks
    val systemUses24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val statusItems = LocationStatusRows.items(
        TaskStatusCounts(
            pending = stop.pendingCount,
            unassigned = stop.unassignedCount,
            done = stop.doneCount,
            cancelled = stop.cancelledCount,
            total = stop.totalCount
        )
    )
    val hoursDisplay = LocationHours.display(appLocation)
    val usesSingleLineAddress = statusItems.size <= 1 && hoursDisplay == null
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .pointerInput(Unit) {
                    var totalDragY = 0f
                    detectDragGestures(
                        onDragStart = { totalDragY = 0f },
                        onDrag = { _, dragAmount -> totalDragY += dragAmount.y },
                        onDragEnd = {
                            if (totalDragY > 30f) onClose()
                        }
                    )
                },
            shape = RoundedCornerShape(18.dp),
            color = palette.elevatedSurface.copy(alpha = 0.96f),
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, start = 16.dp, end = 16.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stop.name,
                            color = palette.textPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2
                        )
                        if (usesSingleLineAddress) {
                            singleLineAddress(stop)?.let {
                                Text(
                                    text = it,
                                    color = palette.textSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else {
                            stop.addressStreetLine?.let {
                                Text(
                                    text = it,
                                    color = palette.textSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            stop.addressCityStateZipLine?.let {
                                Text(
                                    text = it,
                                    color = palette.textSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        statusItems.forEach { item ->
                            LocationStatusLineView(item = item)
                        }
                        hoursDisplay?.let {
                            LocationHoursLabel(display = it)
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    StopValueRow("Machines", stop.machineCount.toString())
                    StopValueRow("Refunds", "$${money(stop.refunds)}", isFinancial = true)
                    StopValueRow(
                        label = "Commission",
                        value = "$${money(stop.commission)}",
                        labelSubtitle = commissionPercentText(stop),
                        valueSubtitle = stop.commissionPaymentSummary,
                        isFinancial = true
                    )
                    StopValueRow("Net Revenue", "$${money(stop.net)}", isFinancial = true)
                }

                if (showsRouteEstimate) {
                    RouteTravelSummaryRow(
                        durationText = routePreview?.let {
                            formatDuration(it.expectedTravelSeconds.coerceAtLeast(0.0) / 60.0)
                        } ?: "--",
                        arrivalDistanceText = routePreview?.let {
                            "${AppTimeFormatter.arrivalTime(it.expectedTravelSeconds.coerceAtLeast(0.0), timeFormatPreference, systemUses24Hour)} • ${oneDecimal(it.distanceMiles.coerceAtLeast(0.0))} mi"
                        } ?: "-- • -- mi"
                    )
                } else if (stop.assigneeSummary != null) {
                    Text(
                        text = stop.assigneeSummary,
                        color = AppColors.muted,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onOpenLocation != null) {
                        PanelButton(title = "Open location", onClick = onOpenLocation, modifier = Modifier.weight(1f))
                        PanelButton(title = "Open tasks", onClick = onOpenTasks, modifier = Modifier.weight(1f))
                    } else {
                        PanelButton(title = "Open tasks", onClick = onOpenTasks, modifier = Modifier.weight(1f))
                        when (stop.action) {
                            LocationStopAction.Go -> PanelButton(title = "GO", isPrimary = true, onClick = onGo, modifier = Modifier.weight(1f))
                            LocationStopAction.ClaimTasks -> PanelButton(title = "Claim tasks", isPrimary = true, onClick = onGo, modifier = Modifier.weight(1f))
                            LocationStopAction.OpenTasks -> Unit
                        }
                    }
                }
            }
        }
        RevenueChip(amount = stop.gross)
    }
}

@Composable
private fun StopValueRow(
    label: String,
    value: String,
    labelSubtitle: String? = null,
    valueSubtitle: String? = null,
    isFinancial: Boolean = false
) {
    val palette = LocalVendistriPalette.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Column {
            Text(
                label,
                color = palette.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isFinancial) FontWeight.SemiBold else FontWeight.Normal
            )
            labelSubtitle?.let {
                Text(
                    it,
                    color = AppColors.muted,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(value, color = palette.textPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            valueSubtitle?.let {
                Text(
                    it,
                    color = AppColors.muted,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun PanelButton(title: String, isPrimary: Boolean = false, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalVendistriPalette.current
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isPrimary) AppColors.vendBlue else palette.surfaceVariant,
        contentColor = if (isPrimary) Color.White else palette.textPrimary
    ) {
        Box(modifier = Modifier.padding(vertical = 11.dp), contentAlignment = Alignment.Center) {
            Text(title, color = if (isPrimary) Color.White else palette.textPrimary, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun commissionPercentText(stop: LocationStop): String? {
    if (kotlin.math.abs(stop.gross) < 0.01) return null
    val percent = (stop.commission / stop.gross) * 100
    return "(${String.format(java.util.Locale.US, "%.2f%%", percent)})"
}

private fun singleLineAddress(stop: LocationStop): String? {
    return listOfNotNull(
        stop.addressStreetLine?.trim()?.takeIf { it.isNotEmpty() },
        stop.addressCityStateZipLine?.trim()?.takeIf { it.isNotEmpty() }
    )
        .takeIf { it.isNotEmpty() }
        ?.joinToString(", ")
}
