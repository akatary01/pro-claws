package com.vendistri.operations.features.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vendistri.operations.design.AppColors
import com.vendistri.operations.design.LocalVendistriPalette

data class CollectionFinancialDraft(
    val gross: Double,
    val grossCash: Double,
    val grossCard: Double,
    val refunds: Double,
    val commission: Double,
    val commissionPaymentType: CommissionPaymentType?,
    val net: Double,
    val includeRefundsInCommission: Boolean?
)

@Composable
internal fun TaskFinancialSectionView(
    task: VendiTask,
    isEditing: Boolean,
    grossText: String,
    grossCashText: String,
    grossCardText: String,
    refundsText: String,
    commissionText: String,
    commissionPaymentType: CommissionPaymentType,
    netValueText: String,
    grossBreakdownValid: Boolean,
    commissionPercentText: String?,
    canRecalculate: Boolean,
    onGrossChange: (String) -> Unit,
    onGrossCashChange: (String) -> Unit,
    onGrossCardChange: (String) -> Unit,
    onRefundsChange: (String) -> Unit,
    onCommissionChange: (String) -> Unit,
    onCommissionPaymentTypeChange: (CommissionPaymentType) -> Unit,
    onRecalculateCommission: () -> Unit,
    onRecalculateGrossBreakdown: () -> Unit
) {
    var showGrossBreakdown by remember(task.id) { mutableStateOf(false) }
    var showCommissionDetails by remember(task.id) { mutableStateOf(false) }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            showGrossBreakdown = true
            showCommissionDetails = true
        }
    }

    Column(
        modifier = Modifier.padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (task.type == TaskType.MachineCollection) {
            FinancialRow(
                label = "Gross ${if (showGrossBreakdown) "⌃" else "⌄"}",
                value = grossText,
                isEditing = isEditing,
                onLabelClick = { showGrossBreakdown = !showGrossBreakdown },
                onValueChange = onGrossChange
            )
            if (showGrossBreakdown) {
                if (isEditing) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        FinancialInput(
                            label = if (task.collectionInputMode == CollectionInputMode.Credits) "Credits" else "Cash",
                            value = grossCashText,
                            isEditing = true,
                            onValueChange = onGrossCashChange,
                            modifier = Modifier.weight(1f)
                        )
                        FinancialInput(
                            label = "Card",
                            value = grossCardText,
                            isEditing = true,
                            onValueChange = onGrossCardChange,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    GrossBreakdownLine(
                        cashLabel = if (task.collectionInputMode == CollectionInputMode.Credits) "Cash Credits" else "Cash",
                        cashText = grossCashText,
                        cardText = grossCardText,
                        isCredits = task.collectionInputMode == CollectionInputMode.Credits
                    )
                }
            }
            if (!grossBreakdownValid) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Cash plus card must equal gross.",
                        color = AppColors.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(
                        onClick = onRecalculateGrossBreakdown,
                        enabled = isEditing,
                        modifier = Modifier.heightIn(min = 28.dp)
                    ) {
                        Text("Recalculate", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        FinancialRow(
            label = "Refunds",
            value = refundsText,
            isEditing = isEditing,
            onValueChange = onRefundsChange
        )

        if (task.type == TaskType.MachineCollection) {
            FinancialRow(
                label = if (isEditing) "Commission ${if (showCommissionDetails) "⌃" else "⌄"}" else "Commission",
                value = commissionText,
                isEditing = isEditing,
                trailingLabel = commissionPercentText,
                trailingValueLabel = if (!isEditing) commissionPaymentType.label else null,
                canRecalculate = canRecalculate,
                onLabelClick = if (isEditing) {
                    { showCommissionDetails = !showCommissionDetails }
                } else {
                    null
                },
                onValueChange = onCommissionChange,
                onRecalculate = onRecalculateCommission
            )
            if (isEditing && showCommissionDetails) {
                CommissionPaymentTypeRow(
                    paymentType = commissionPaymentType,
                    onPaymentTypeChange = onCommissionPaymentTypeChange
                )
            }
            FinancialReadOnlyRow(label = "Net", value = netValueText)
        }
    }
}

@Composable
private fun FinancialRow(
    label: String,
    value: String,
    isEditing: Boolean,
    trailingLabel: String? = null,
    trailingValueLabel: String? = null,
    canRecalculate: Boolean = false,
    onLabelClick: (() -> Unit)? = null,
    onValueChange: (String) -> Unit,
    onRecalculate: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                label,
                modifier = if (onLabelClick != null) Modifier.clickable(onClick = onLabelClick) else Modifier,
                color = LocalVendistriPalette.current.textSecondary,
                fontWeight = FontWeight.SemiBold
            )
            if (trailingLabel != null || (isEditing && canRecalculate)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    trailingLabel?.let {
                        Text(it, color = LocalVendistriPalette.current.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    if (isEditing && canRecalculate) {
                        TextButton(onClick = onRecalculate, modifier = Modifier.heightIn(min = 24.dp)) {
                            Text("Recalculate", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        if (isEditing) {
            FinancialTextField(value = value, onValueChange = onValueChange, modifier = Modifier.width(128.dp))
        } else {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("$${money(parseMoney(value) ?: 0.0)}", color = LocalVendistriPalette.current.textPrimary, fontWeight = FontWeight.SemiBold)
                trailingValueLabel?.let {
                    Text(it, color = LocalVendistriPalette.current.textSecondary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun GrossBreakdownLine(
    cashLabel: String,
    cashText: String,
    cardText: String,
    isCredits: Boolean
) {
    val cashValue = if (isCredits) {
        (parseMoney(cashText) ?: 0.0).toInt().toString()
    } else {
        "$${money(parseMoney(cashText) ?: 0.0)}"
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            "${cashLabel.uppercase()} $cashValue",
            color = LocalVendistriPalette.current.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "CARD $${money(parseMoney(cardText) ?: 0.0)}",
            color = LocalVendistriPalette.current.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CommissionPaymentTypeRow(
    paymentType: CommissionPaymentType,
    onPaymentTypeChange: (CommissionPaymentType) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Payment Type", color = LocalVendistriPalette.current.textSecondary, fontWeight = FontWeight.SemiBold)
        CommissionPaymentTypeDropdown(paymentType = paymentType, onPaymentTypeChange = onPaymentTypeChange)
    }
}

@Composable
private fun CommissionPaymentTypeDropdown(
    paymentType: CommissionPaymentType,
    onPaymentTypeChange: (CommissionPaymentType) -> Unit
) {
    val palette = LocalVendistriPalette.current
    var isOpen by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.width(170.dp),
        shape = RoundedCornerShape(10.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.border),
        onClick = { isOpen = !isOpen }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                paymentType.label,
                modifier = Modifier.weight(1f),
                color = palette.textPrimary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(if (isOpen) "⌃" else "⌄", color = palette.textSecondary, fontWeight = FontWeight.SemiBold)
        }
        DropdownMenu(expanded = isOpen, onDismissRequest = { isOpen = false }) {
            CommissionPaymentType.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label, fontWeight = FontWeight.SemiBold) },
                    onClick = {
                        onPaymentTypeChange(option)
                        isOpen = false
                    }
                )
            }
        }
    }
}

@Composable
private fun FinancialInput(
    label: String,
    value: String,
    isEditing: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = LocalVendistriPalette.current.textSecondary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        if (isEditing) {
            FinancialTextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth())
        } else {
            Text("$${money(parseMoney(value) ?: 0.0)}", color = LocalVendistriPalette.current.textPrimary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FinancialReadOnlyRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = LocalVendistriPalette.current.textSecondary, fontWeight = FontWeight.SemiBold)
        Text(value, color = LocalVendistriPalette.current.textPrimary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FinancialTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalVendistriPalette.current
    OutlinedTextField(
        value = value,
        onValueChange = { next ->
            if (next.all { it.isDigit() || it == '.' }) onValueChange(next)
        },
        modifier = modifier.heightIn(min = 44.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = palette.textPrimary,
            fontWeight = FontWeight.SemiBold
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = palette.brand,
            unfocusedBorderColor = palette.border,
            focusedContainerColor = palette.background,
            unfocusedContainerColor = palette.background
        )
    )
}

internal fun parseMoney(text: String): Double? {
    return text.trim().replace(",", "").takeIf { it.isNotBlank() }?.toDoubleOrNull()
}

internal fun formatMoneyInput(value: Double): String = String.format(java.util.Locale.US, "%.2f", value)

internal fun collectionFinancialDraftFromInputs(
    task: VendiTask,
    grossText: String,
    grossCashText: String,
    grossCardText: String,
    refundsText: String,
    commissionText: String,
    commissionPaymentType: CommissionPaymentType = task.commissionPaymentType ?: CommissionPaymentType.Cash,
    includeRefundsInCommission: Boolean? = task.includeRefundsInCommission
): CollectionFinancialDraft {
    val gross = parseMoney(grossText) ?: (task.gross ?: 0.0)
    val creditsPerDollar = maxOf(0.01, task.creditsPerDollar ?: 4.0)
    val grossCashInput = parseMoney(grossCashText) ?: (task.grossCash ?: 0.0)
    val grossCash = if (task.collectionInputMode == CollectionInputMode.Credits) grossCashInput / creditsPerDollar else grossCashInput
    val grossCard = parseMoney(grossCardText) ?: (task.grossCard ?: 0.0)
    val refunds = parseMoney(refundsText) ?: (task.refunds ?: 0.0)
    val commission = parseMoney(commissionText) ?: (task.commission ?: 0.0)
    val net = roundCurrency(gross - refunds - commission)
    return CollectionFinancialDraft(
        gross = gross,
        grossCash = grossCash,
        grossCard = grossCard,
        refunds = refunds,
        commission = commission,
        commissionPaymentType = commissionPaymentType,
        net = net,
        includeRefundsInCommission = includeRefundsInCommission
    )
}

internal fun roundCurrency(value: Double): Double = kotlin.math.round(value * 100.0) / 100.0
