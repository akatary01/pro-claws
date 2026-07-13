package com.vendistri.operations.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SkeletonLine(
    modifier: Modifier = Modifier,
    width: Dp? = null,
    height: Dp = 14.dp
) {
    SkeletonBlock(
        modifier = modifier.then(if (width == null) Modifier.fillMaxWidth() else Modifier.width(width)),
        height = height,
        radius = height / 2
    )
}

@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    height: Dp,
    radius: Dp = 12.dp
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
        label = "skeleton-shift"
    )
    val brush = Brush.linearGradient(
        colors = listOf(
            Color(0xFFE7E7EC),
            Color(0xFFF7F7FA),
            Color(0xFFE7E7EC)
        ),
        start = Offset(shift - 900f, 0f),
        end = Offset(shift, 0f)
    )

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(brush)
    )
}

@Composable
fun SkeletonList(
    rows: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(rows) {
            SkeletonBlock(height = 70.dp)
        }
    }
}
