package com.vendistri.operations.design

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class VendistriResponsiveLayout(
    val compactDevice: Boolean,
    val primaryMapActionSize: Dp,
    val taskPanelTopClearance: Dp,
    val calendarWidth: Dp,
    val calendarDayRowHeight: Dp
)

val LocalVendistriResponsiveLayout = staticCompositionLocalOf {
    VendistriResponsiveLayout(
        compactDevice = false,
        primaryMapActionSize = 70.dp,
        taskPanelTopClearance = 224.dp,
        calendarWidth = 250.dp,
        calendarDayRowHeight = 24.dp
    )
}

fun vendistriResponsiveLayout(screenWidthDp: Int, screenHeightDp: Int): VendistriResponsiveLayout {
    val compactDevice = screenWidthDp <= 360 || screenHeightDp <= 720
    // Scale against the shortest screen edge, then clamp for accessibility and
    // to avoid oversized map chrome on narrow, low-resolution phones.
    val primaryActionDp = (minOf(screenWidthDp, screenHeightDp) * 0.17f)
        .coerceIn(56f, 70f)
    return VendistriResponsiveLayout(
        compactDevice = compactDevice,
        primaryMapActionSize = primaryActionDp.dp,
        taskPanelTopClearance = if (compactDevice) 176.dp else 224.dp,
        calendarWidth = if (compactDevice) 232.dp else 250.dp,
        calendarDayRowHeight = if (compactDevice) 22.dp else 24.dp
    )
}
