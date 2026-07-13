package com.vendistri.operations.design

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class VendistriPalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val elevatedSurface: Color,
    val surfaceVariant: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val brand: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val mapChromeSurface: Color,
    val mapChromeForeground: Color,
    val mapBottomBarSurface: Color,
    val mapPanelSurface: Color,
    val mapPanelForeground: Color
)

object VendistriDesign {
    val lightPalette = VendistriPalette(
        isDark = false,
        background = AppColors.background,
        surface = AppColors.surface,
        elevatedSurface = Color.White,
        surfaceVariant = AppColors.chipBackground,
        border = AppColors.border,
        textPrimary = Color(0xFF111111),
        textSecondary = AppColors.muted,
        brand = AppColors.vendBlue,
        success = AppColors.statusDone,
        warning = AppColors.statusPending,
        error = AppColors.statusError,
        mapChromeSurface = Color.White.copy(alpha = 0.96f),
        mapChromeForeground = Color(0xFF1C1C1E),
        mapBottomBarSurface = Color.White.copy(alpha = 0.94f),
        mapPanelSurface = Color.White.copy(alpha = 0.98f),
        mapPanelForeground = Color(0xFF111111)
    )

    val darkPalette = VendistriPalette(
        isDark = true,
        background = Color.Black,
        surface = Color.Black,
        elevatedSurface = Color(0xFF1C1C1E),
        surfaceVariant = Color(0xFF1C1C1E),
        border = Color(0xFF3A3A3C),
        textPrimary = Color.White,
        textSecondary = Color(0xFF9A9AA0),
        brand = AppColors.vendBlue,
        success = AppColors.statusDone,
        warning = AppColors.statusPending,
        error = AppColors.statusError,
        mapChromeSurface = Color.Black.copy(alpha = 0.88f),
        mapChromeForeground = Color.White,
        mapBottomBarSurface = Color.Black.copy(alpha = 0.90f),
        mapPanelSurface = Color.Black.copy(alpha = 0.96f),
        mapPanelForeground = Color.White
    )
}

val LocalVendistriPalette = staticCompositionLocalOf { VendistriDesign.lightPalette }
