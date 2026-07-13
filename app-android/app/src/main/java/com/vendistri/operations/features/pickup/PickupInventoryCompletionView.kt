package com.vendistri.operations.features.pickup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.vendistri.operations.components.PrimaryActionButton
import com.vendistri.operations.design.AppColors
import com.vendistri.operations.features.refill.InventoryLineSkeleton
import com.vendistri.operations.features.refill.InventoryNumberField
import com.vendistri.operations.features.refill.PickupInventoryStockSummary
import com.vendistri.operations.features.refill.PickupWarehouseStockStatus
import com.vendistri.operations.features.refill.productTitle
import com.vendistri.operations.features.tasks.TaskPickupLineFormatters
import com.vendistri.operations.features.tasks.signedQuantity

private data class PickupInventoryProductGroup(
    val productId: String,
    val title: String,
    val lines: List<PickupInventoryLine>
)

@Composable
fun PickupInventoryCompletionView(
    state: PickupInventoryUiState,
    canComplete: Boolean,
    onPickedUpChanged: (String, String) -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.isLoading) {
            InventoryLineSkeleton(rows = 3)
        } else if (state.lines.isEmpty()) {
            Text(
                text = state.errorMessage ?: "No pickup items.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            val groups = state.lines.productGroups()
            groups.forEach { group ->
                PickupInventoryProductGroupRows(
                    group = group,
                    enabled = canComplete && !state.isCompleting,
                    invalidLineIds = state.invalidLineIds,
                    onPickedUpChanged = onPickedUpChanged
                )
            }
        }
        state.errorMessage?.takeIf { state.lines.isNotEmpty() }?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
        if (canComplete && state.lines.isNotEmpty()) {
            PrimaryActionButton(
                text = if (state.isCompleting) "Saving..." else "Complete pickup",
                onClick = onComplete,
                enabled = !state.isCompleting,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PickupInventoryProductGroupRows(
    group: PickupInventoryProductGroup,
    enabled: Boolean,
    invalidLineIds: Set<String>,
    onPickedUpChanged: (String, String) -> Unit
) {
    val summary = group.stockSummary()
    val validationError = summary.overageMessage
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = group.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            summary.status?.let { status ->
                Text(
                    text = pickupAvailabilityLabel(status),
                    color = pickupAvailabilityColor(status),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        group.lines.forEach { line ->
            PickupInventoryMachineLineRow(
                line = line,
                enabled = enabled,
                isInvalid = validationError != null || line.lineId in invalidLineIds,
                onPickedUpChanged = onPickedUpChanged
            )
        }
        validationError?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun PickupInventoryMachineLineRow(
    line: PickupInventoryLine,
    enabled: Boolean,
    isInvalid: Boolean,
    onPickedUpChanged: (String, String) -> Unit
) {
    val secondaryTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = line.source.machineName ?: "Machine",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = buildAnnotatedString {
                    val stockText = TaskPickupLineFormatters.stockText(line.source)
                    if (stockText.isNotBlank()) {
                        append(stockText)
                        append(" • ")
                    }
                    append("Suggested ")
                    withStyle(SpanStyle(color = if (line.source.suggestedQuantity > 0) AppColors.statusDone else secondaryTextColor)) {
                        append(signedQuantity(line.source.suggestedQuantity))
                    }
                },
                color = secondaryTextColor,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        InventoryNumberField(
            label = null,
            value = line.pickedUpText,
            enabled = enabled,
            onValueChanged = { onPickedUpChanged(line.lineId, it) },
            isError = isInvalid,
            modifier = Modifier.weight(0.42f)
        )
    }
}

private fun List<PickupInventoryLine>.productGroups(): List<PickupInventoryProductGroup> {
    return groupBy { it.source.product.id }
        .values
        .map { lines ->
            val product = lines.first().source.product
            PickupInventoryProductGroup(
                productId = product.id,
                title = productTitle(product.name, product.brand, product.size),
                lines = lines
            )
        }
}

private fun PickupInventoryProductGroup.stockSummary(): PickupInventoryStockSummary {
    return PickupInventoryStockSummary(
        available = lines.mapNotNull { it.source.warehouseAvailableStock }.maxOrNull(),
        needed = lines.sumOf { it.source.suggestedQuantity.coerceAtLeast(0) },
        pickedUp = lines.sumOf { parsedPickedUpQuantity(it.pickedUpText) ?: 0 }
    )
}

private fun parsedPickedUpQuantity(value: String): Int? {
    return value.trim().takeIf { it.isNotBlank() }?.toIntOrNull()
}

private fun pickupAvailabilityLabel(status: PickupWarehouseStockStatus): String {
    return when (status) {
        PickupWarehouseStockStatus.Available -> "Available"
        PickupWarehouseStockStatus.Partial -> "Partial"
        PickupWarehouseStockStatus.None -> "No stock"
    }
}

@Composable
private fun pickupAvailabilityColor(status: PickupWarehouseStockStatus) = when (status) {
    PickupWarehouseStockStatus.Available -> AppColors.statusDone
    PickupWarehouseStockStatus.Partial -> AppColors.statusPending
    PickupWarehouseStockStatus.None -> MaterialTheme.colorScheme.error
}
