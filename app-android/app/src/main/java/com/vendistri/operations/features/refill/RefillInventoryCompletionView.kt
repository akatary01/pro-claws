package com.vendistri.operations.features.refill

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.vendistri.operations.components.PrimaryActionButton
import com.vendistri.operations.components.SkeletonLine
import com.vendistri.operations.design.AppColors
import com.vendistri.operations.features.location.WarehouseOption
import com.vendistri.operations.features.tasks.InventoryStockFormatters
import com.vendistri.operations.features.tasks.RefillInventorySourceMode

@Composable
fun RefillInventoryCompletionView(
    state: RefillInventoryUiState,
    warehouses: List<WarehouseOption>,
    canComplete: Boolean,
    onRefilledChanged: (String, String) -> Unit,
    onFinalStockChanged: (String, String) -> Unit,
    onSourceSelected: (RefillInventorySourceMode, String?) -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Refill inventory",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        if (state.pickupSourceSummary != null) {
            Text(
                text = state.pickupSourceSummary,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            RefillInventorySourceControls(
                state = state,
                warehouses = warehouses,
                enabled = canComplete && !state.isCompleting && !state.isSavingSource,
                showRemainingSourceLabel = state.lines.any { (it.pickedUpQuantity ?: 0) > 0 },
                onSourceSelected = onSourceSelected
            )
        }
        when {
            state.isLoading -> InventoryLineSkeleton(rows = 3)
            state.lines.isEmpty() -> Text(
                text = state.errorMessage ?: "No active machine inventory items.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                style = MaterialTheme.typography.bodySmall
            )
            else -> state.lines.forEach { line ->
                RefillInventoryLineRow(
                    line = line,
                    enabled = canComplete && !state.isCompleting,
                    isRefilledInvalid = line.itemId in state.invalidRefilledItemIds,
                    isFinalStockInvalid = line.itemId in state.invalidFinalStockItemIds,
                    onRefilledChanged = onRefilledChanged,
                    onFinalStockChanged = onFinalStockChanged
                )
            }
        }
        val mismatchedLines = state.lines.filter { it.canRecalculateFinalStock }
        if (mismatchedLines.isNotEmpty() && state.errorMessage == null) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Final stock does not match current stock + refilled.",
                    color = AppColors.statusPending,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                TextButton(
                    onClick = {
                        mismatchedLines.forEach { line ->
                            line.refilledText.toIntOrNull()?.let { refilledQuantity ->
                                onFinalStockChanged(line.itemId, (line.currentStock + refilledQuantity).toString())
                            }
                        }
                    },
                    enabled = canComplete && !state.isCompleting && !state.isSavingSource
                ) {
                    Text("Recalculate", style = MaterialTheme.typography.labelSmall)
                }
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
                text = if (state.isCompleting || state.isSavingSource) "Saving..." else "Complete refill",
                onClick = onComplete,
                enabled = !state.isCompleting && !state.isSavingSource,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun InventoryLineSkeleton(rows: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(rows) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SkeletonLine(width = 190.dp)
                SkeletonLine(width = 240.dp, height = 12.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SkeletonLine(modifier = Modifier.weight(1f), height = 44.dp)
                    SkeletonLine(modifier = Modifier.weight(1f), height = 44.dp)
                }
            }
        }
    }
}

@Composable
private fun RefillInventorySourceControls(
    state: RefillInventoryUiState,
    warehouses: List<WarehouseOption>,
    enabled: Boolean,
    showRemainingSourceLabel: Boolean,
    onSourceSelected: (RefillInventorySourceMode, String?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        if (showRemainingSourceLabel) {
            Text(
                text = "Inventory source for remaining refill items",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            val warehouseSelected = state.sourceMode == RefillInventorySourceMode.Warehouse
            val untrackedSelected = state.sourceMode == RefillInventorySourceMode.Untracked
            OutlinedButton(
                onClick = {
                    val warehouseId = state.selectedWarehouseId ?: warehouses.firstOrNull()?.id
                    if (warehouseId != null) onSourceSelected(RefillInventorySourceMode.Warehouse, warehouseId)
                },
                enabled = enabled && warehouses.isNotEmpty(),
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, if (warehouseSelected) AppColors.vendBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (warehouseSelected) AppColors.vendBlue.copy(alpha = 0.10f) else androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text(
                    "Use Warehouse Stock",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    textAlign = TextAlign.Center
                )
            }
            OutlinedButton(
                onClick = { onSourceSelected(RefillInventorySourceMode.Untracked, null) },
                enabled = enabled,
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, if (untrackedSelected) AppColors.vendBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (untrackedSelected) AppColors.vendBlue.copy(alpha = 0.10f) else androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text(
                    "Use Untracked Stock",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    textAlign = TextAlign.Center
                )
            }
        }
        if (state.sourceMode == RefillInventorySourceMode.Warehouse) {
            WarehousePicker(
                warehouses = warehouses,
                selectedWarehouseId = state.selectedWarehouseId,
                selectedWarehouseName = state.selectedWarehouseName,
                enabled = enabled,
                onWarehouseSelected = { onSourceSelected(RefillInventorySourceMode.Warehouse, it) }
            )
        }
    }
}

@Composable
private fun WarehousePicker(
    warehouses: List<WarehouseOption>,
    selectedWarehouseId: String?,
    selectedWarehouseName: String?,
    enabled: Boolean,
    onWarehouseSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = warehouses.firstOrNull { it.id == selectedWarehouseId }?.name
        ?: selectedWarehouseName
        ?: "Select warehouse"

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled && warehouses.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
        ) {
            Text(
                selectedName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            warehouses.forEach { warehouse ->
                DropdownMenuItem(
                    text = { Text(warehouse.name) },
                    onClick = {
                        expanded = false
                        onWarehouseSelected(warehouse.id)
                    }
                )
            }
        }
        if (warehouses.isEmpty()) {
            Text(
                text = "No active warehouses.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun RefillInventoryLineRow(
    line: RefillInventoryLine,
    enabled: Boolean,
    isRefilledInvalid: Boolean,
    isFinalStockInvalid: Boolean,
    onRefilledChanged: (String, String) -> Unit,
    onFinalStockChanged: (String, String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = productTitle(line.product.name, line.product.brand, line.product.size),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                RefillContextText(line)
            }
        }
        RefillValueRow(
            title = "Refilled",
            value = line.refilledText,
            enabled = enabled,
            isError = isRefilledInvalid,
            onValueChanged = { onRefilledChanged(line.itemId, it) }
        )
        RefillValueRow(
            title = "Final",
            value = line.finalStockText,
            enabled = enabled,
            isError = isFinalStockInvalid,
            onValueChanged = { onFinalStockChanged(line.itemId, it) }
        )
        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    }
}

@Composable
private fun RefillContextText(line: RefillInventoryLine) {
    val pickedUpQuantity = line.pickedUpQuantity
    val text = if (pickedUpQuantity != null) buildAnnotatedString {
        append("${InventoryStockFormatters.stockText(line.currentStock, line.capacity)} • picked up $pickedUpQuantity of ")
        withStyle(SpanStyle(color = AppColors.statusDone)) {
            append("+${line.suggestedRefill}")
        }
    } else {
        buildAnnotatedString {
            append("${InventoryStockFormatters.stockText(line.currentStock, line.capacity)} • suggested ")
            withStyle(SpanStyle(color = AppColors.statusDone)) {
                append("+${line.suggestedRefill}")
            }
        }
    }
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun RefillValueRow(
    title: String,
    value: String,
    enabled: Boolean,
    isError: Boolean = false,
    onValueChanged: (String) -> Unit,
    actionTitle: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            if (actionTitle != null && onAction != null) {
                TextButton(onClick = onAction, enabled = enabled) {
                    Text(actionTitle, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        InventoryNumberField(
            label = null,
            value = value,
            enabled = enabled,
            isError = isError,
            onValueChanged = onValueChanged,
            modifier = Modifier.width(92.dp)
        )
    }
}

@Composable
internal fun InventoryNumberField(
    label: String?,
    value: String,
    enabled: Boolean,
    onValueChanged: (String) -> Unit,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChanged,
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        label = label?.let { fieldLabel -> { Text(fieldLabel) } },
        textStyle = MaterialTheme.typography.bodySmall.copy(
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End
        ),
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

private val RefillInventoryLine.canRecalculateFinalStock: Boolean
    get() {
        val refilledQuantity = refilledText.toIntOrNull() ?: return false
        val finalStock = finalStockText.toIntOrNull() ?: return false
        return currentStock + refilledQuantity != finalStock
    }

internal fun productTitle(name: String, brand: String?, size: String?): String {
    return listOf(name, brand, size)
        .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        .joinToString(" • ")
}
