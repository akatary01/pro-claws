package com.vendistri.operations.features.location

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vendistri.operations.components.SkeletonList

@Composable
fun LocationsPanelView(
    state: LocationUiState,
    onRefresh: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Locations",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = onRefresh, enabled = !state.isLoading) {
                Text("Refresh")
            }
        }
        state.errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (state.isLoading && state.locations.isEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            SkeletonList(rows = 5)
            return@Column
        }
        if (state.locations.isEmpty() && !state.isLoading) {
            Text(
                text = "No locations loaded.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
            )
        }
        state.locations.take(24).forEach { location ->
            LocationRow(location)
        }
    }
}

@Composable
private fun LocationRow(location: AppLocation) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = location.name.ifBlank { "Unnamed location" },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
        val address = location.address?.singleLine.orEmpty()
        if (address.isNotBlank()) {
            Text(
                text = address,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider()
    }
}
