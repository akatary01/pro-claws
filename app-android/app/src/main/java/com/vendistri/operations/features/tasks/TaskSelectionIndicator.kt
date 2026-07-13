package com.vendistri.operations.features.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vendistri.operations.design.AppColors

@Composable
internal fun TaskSelectionIndicator(
    isSelected: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = if (isSelected) tint else Color.Transparent,
        border = BorderStroke(2.dp, if (isSelected) tint else AppColors.muted.copy(alpha = 0.65f))
    ) {
        if (isSelected) {
            Box(contentAlignment = Alignment.Center) {
                Text("✓", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}
