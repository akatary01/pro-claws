package com.vendistri.operations.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vendistri.operations.design.LocalVendistriPalette
import com.vendistri.operations.features.tasks.money

@Composable
fun RevenueChip(
    amount: Double,
    modifier: Modifier = Modifier,
    showsShadow: Boolean = false,
    textSize: RevenueChipTextSize = RevenueChipTextSize.Small
) {
    val palette = LocalVendistriPalette.current
    val textStyle = when (textSize) {
        RevenueChipTextSize.Small -> MaterialTheme.typography.labelLarge
        RevenueChipTextSize.Large -> MaterialTheme.typography.titleSmall
    }
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = palette.surfaceVariant,
        shadowElevation = if (showsShadow) 6.dp else 0.dp
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(
                text = "$",
                color = Color.Green,
                style = textStyle,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = " ${money(amount)}",
                color = palette.textPrimary,
                style = textStyle,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

enum class RevenueChipTextSize {
    Small,
    Large
}
