package com.vendistri.operations.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import com.vendistri.operations.design.AppColors
import com.vendistri.operations.design.LocalVendistriPalette
import com.vendistri.operations.design.LocalVendistriResponsiveLayout
import com.vendistri.operations.design.VendistriDesign
import com.vendistri.operations.design.vendistriResponsiveLayout

private val DarkColorScheme = darkColorScheme(
    primary = AppColors.vendBlue,
    secondary = AppColors.vendBlue,
    tertiary = AppColors.statusPending,
    background = VendBackgroundDark,
    surface = VendSurfaceDark,
    surfaceVariant = VendSurfaceDark,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color.White,
    outline = VendBorderDark,
    error = AppColors.statusError
)

private val LightColorScheme = lightColorScheme(
    primary = AppColors.vendBlue,
    secondary = AppColors.vendBlue,
    tertiary = AppColors.statusPending,
    background = AppColors.background,
    surface = AppColors.surface,
    surfaceVariant = AppColors.chipBackground,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = Color(0xFF111111),
    onSurface = Color(0xFF111111),
    onSurfaceVariant = Color(0xFF111111),
    outline = AppColors.border,
    error = AppColors.statusError
)

@Composable
fun VendistriTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val vendistriPalette = if (darkTheme) VendistriDesign.darkPalette else VendistriDesign.lightPalette
    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val screenWidthDp = with(density) { containerSize.width.toDp().value.toInt() }
    val screenHeightDp = with(density) { containerSize.height.toDp().value.toInt() }
    val compactDevice = screenWidthDp <= 360 || screenHeightDp <= 640
    val responsiveLayout = vendistriResponsiveLayout(
        screenWidthDp = screenWidthDp,
        screenHeightDp = screenHeightDp
    )
    val responsiveTypography = if (compactDevice) Typography.scaled(0.80f) else Typography
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = responsiveTypography,
    ) {
        CompositionLocalProvider(
            LocalVendistriPalette provides vendistriPalette,
            LocalVendistriResponsiveLayout provides responsiveLayout,
            content = content
        )
    }
}
