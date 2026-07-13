package com.vendistri.operations.features.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vendistri.operations.design.AppColors
import com.vendistri.operations.design.LocalVendistriPalette
import com.vendistri.operations.features.location.AppLocation
import com.vendistri.operations.features.refill.RefillInventoryCompletionView
import com.vendistri.operations.features.refill.RefillInventoryUiState

@Composable
internal fun TaskRowView(
    task: VendiTask,
    showAssignee: Boolean,
    showCompletedMetrics: Boolean,
    isUpdating: Boolean,
    taskActions: TaskCardActions,
    financialDisplay: TaskFinancialDisplayMode = TaskFinancialDisplayMode.Full,
    appLocation: AppLocation?,
    autoCalcCommission: Boolean,
    showsTaskIdentity: Boolean = true,
    showsStatusControl: Boolean = true
) {
    val palette = LocalVendistriPalette.current
    var isEditingFinancials by remember(task.id) { mutableStateOf(false) }
    var grossText by remember(task.id) { mutableStateOf(formatMoneyInput(task.gross ?: 0.0)) }
    var grossCashText by remember(task.id) {
        mutableStateOf(
            if (task.collectionInputMode == CollectionInputMode.Credits) {
                formatMoneyInput(((task.grossCash ?: 0.0) * (task.creditsPerDollar ?: 4.0)).roundToWhole())
            } else {
                formatMoneyInput(task.grossCash ?: 0.0)
            }
        )
    }
    var grossCardText by remember(task.id) { mutableStateOf(formatMoneyInput(task.grossCard ?: 0.0)) }
    var refundsText by remember(task.id) { mutableStateOf(formatMoneyInput(task.refunds ?: 0.0)) }
    var commissionText by remember(task.id) { mutableStateOf(formatMoneyInput(task.commission ?: 0.0)) }
    var commissionPaymentType by remember(task.id) {
        mutableStateOf(task.commissionPaymentType ?: CommissionPaymentType.Cash)
    }

    fun syncFinancialInputs() {
        grossText = formatMoneyInput(task.gross ?: 0.0)
        grossCashText = if (task.collectionInputMode == CollectionInputMode.Credits) {
            formatMoneyInput(((task.grossCash ?: 0.0) * (task.creditsPerDollar ?: 4.0)).roundToWhole())
        } else {
            formatMoneyInput(task.grossCash ?: 0.0)
        }
        grossCardText = formatMoneyInput(task.grossCard ?: 0.0)
        refundsText = formatMoneyInput(task.refunds ?: 0.0)
        commissionText = formatMoneyInput(task.commission ?: 0.0)
        commissionPaymentType = task.commissionPaymentType ?: CommissionPaymentType.Cash
    }

    fun applyAutoCalculatedCommissionIfNeeded(nextGrossText: String = grossText, nextRefundsText: String = refundsText) {
        if (!autoCalcCommission || task.type != TaskType.MachineCollection) return
        val gross = parseMoney(nextGrossText) ?: (task.gross ?: 0.0)
        val refunds = parseMoney(nextRefundsText) ?: (task.refunds ?: 0.0)
        val commission = TaskCommissionCalculator.calculatedCommission(
            gross = gross,
            refunds = refunds,
            location = appLocation
        ) ?: return
        commissionText = formatMoneyInput(commission)
    }

    LaunchedEffect(task.gross, task.grossCash, task.grossCard, task.refunds, task.commission, task.commissionPaymentType, isUpdating) {
        if (!isEditingFinancials && !isUpdating) syncFinancialInputs()
    }
    val refillEditor = taskActions.refillEditor
    val canEditRefill = task.type == TaskType.MachineRefill &&
        !showCompletedMetrics &&
        !TaskStateHelpers.isFinal(task.status) &&
        taskActions.canChangeStatus(task) &&
        refillEditor != null
    val refillState = refillEditor?.states?.get(task.id)
    LaunchedEffect(task.id, canEditRefill) {
        if (canEditRefill && refillState == null) {
            refillEditor?.onPrepare?.invoke(task)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (showsTaskIdentity) {
                    Text(taskTypeLabel(task.type), color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
                }
                if (showAssignee) {
                    Text(
                        TaskStateHelpers.assigneeLine(task) ?: task.displayTitle,
                        color = palette.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            if (showsStatusControl) {
                when {
                    !showCompletedMetrics && taskActions.canAssignToSelf(task) -> {
                        CompactTaskButton(text = "Claim") {
                            taskActions.onAssignToSelf(task)
                        }
                    }
                    !showCompletedMetrics &&
                        taskActions.canChangeStatus(task) &&
                        TaskRowActionPolicy.canUseSimpleDoneAction(task) -> {
                        TaskStatusMenu(
                            status = task.status,
                            isUpdating = isUpdating,
                            onSelect = { status ->
                                if (status == TaskStatus.Done) taskActions.onMarkDone(listOf(task))
                                else taskActions.onStatusChange(task, status)
                            }
                        )
                    }
                    else -> TaskStatusBadge(task.status)
                }
            }
        }

        if (task.type == TaskType.MachineCollection || task.type == TaskType.MachineRefund) {
            val canEditFinancials = !showCompletedMetrics && taskActions.canEditFinancials(task)
            val isEditingActive = isEditingFinancials && canEditFinancials && !isUpdating
            val draft = collectionFinancialDraftFromInputs(
                task = task,
                grossText = grossText,
                grossCashText = grossCashText,
                grossCardText = grossCardText,
                refundsText = refundsText,
                commissionText = commissionText,
                commissionPaymentType = commissionPaymentType,
                includeRefundsInCommission = TaskCommissionCalculator.includeRefundsInCommission(appLocation)
                    ?: task.includeRefundsInCommission
            )
            val grossBreakdownValid = task.type != TaskType.MachineCollection ||
                TaskCommissionCalculator.grossBreakdownValid(
                    gross = draft.gross,
                    grossCash = draft.grossCash,
                    grossCard = draft.grossCard
                )
            val financialsDirty = if (task.type == TaskType.MachineCollection) {
                kotlin.math.abs(draft.gross - (task.gross ?: 0.0)) >= 0.01 ||
                    kotlin.math.abs(draft.grossCash - (task.grossCash ?: 0.0)) >= 0.01 ||
                    kotlin.math.abs(draft.grossCard - (task.grossCard ?: 0.0)) >= 0.01 ||
                    kotlin.math.abs(draft.refunds - (task.refunds ?: 0.0)) >= 0.01 ||
                    kotlin.math.abs(draft.commission - (task.commission ?: 0.0)) >= 0.01 ||
                    draft.commissionPaymentType != (task.commissionPaymentType ?: CommissionPaymentType.Cash)
            } else {
                kotlin.math.abs((parseMoney(refundsText) ?: (task.refunds ?: 0.0)) - (task.refunds ?: 0.0)) >= 0.01
            }
            val displayedFinancials = TaskFinancialHelpers.displayedFinancials(task)
            val displayedGrossText = if (isEditingActive) grossText else formatMoneyInput(displayedFinancials.gross)
            val displayedGrossCashText = if (isEditingActive) {
                grossCashText
            } else if (task.collectionInputMode == CollectionInputMode.Credits) {
                formatMoneyInput((displayedFinancials.grossCash * (task.creditsPerDollar ?: 4.0)).roundToWhole())
            } else {
                formatMoneyInput(displayedFinancials.grossCash)
            }
            val displayedGrossCardText = if (isEditingActive) grossCardText else formatMoneyInput(displayedFinancials.grossCard)
            val displayedRefundsText = if (isEditingActive) refundsText else formatMoneyInput(displayedFinancials.refunds)
            val displayedCommissionText = if (isEditingActive) commissionText else formatMoneyInput(displayedFinancials.commission)

            if (canEditFinancials) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (isEditingFinancials) {
                        CompactTaskButton(
                            text = "Save",
                            enabled = financialsDirty && grossBreakdownValid && !isUpdating,
                            primary = financialsDirty && grossBreakdownValid && !isUpdating
                        ) {
                            if (!financialsDirty || !grossBreakdownValid || isUpdating) return@CompactTaskButton
                            isEditingFinancials = false
                            if (task.type == TaskType.MachineCollection) {
                                taskActions.onCollectionFinancialUpdate(task, draft)
                            } else {
                                taskActions.onRefundFinancialUpdate(task, parseMoney(refundsText) ?: 0.0)
                            }
                        }
                        CompactTaskButton(text = "Cancel") {
                            isEditingFinancials = false
                            syncFinancialInputs()
                        }
                    } else {
                        CompactTaskButton(text = "Edit") {
                            syncFinancialInputs()
                            isEditingFinancials = true
                        }
                    }
                }
            }

            if (financialDisplay == TaskFinancialDisplayMode.CommissionOnly) {
                FinancialLabelValueRowForTask(label = "Commission", value = "$${money(displayedFinancials.commission)}")
            } else {
                TaskFinancialSectionView(
                    task = task,
                    isEditing = isEditingActive,
                    grossText = displayedGrossText,
                    grossCashText = displayedGrossCashText,
                    grossCardText = displayedGrossCardText,
                    refundsText = displayedRefundsText,
                    commissionText = displayedCommissionText,
                    commissionPaymentType = commissionPaymentType,
                    netValueText = "$${money(if (isEditingActive) draft.net else displayedFinancials.net)}",
                    grossBreakdownValid = grossBreakdownValid,
                    commissionPercentText = TaskCommissionCalculator.commissionPercentText(
                        gross = if (isEditingActive) draft.gross else displayedFinancials.gross,
                        commission = if (isEditingActive) draft.commission else displayedFinancials.commission
                    ),
                    canRecalculate = TaskCommissionCalculator.canManuallyRecalculateCommission(
                        autoCalcCommission = autoCalcCommission,
                        gross = draft.gross,
                        refunds = draft.refunds,
                        commission = draft.commission,
                        location = appLocation
                    ),
                    onGrossChange = {
                        grossText = it
                        applyAutoCalculatedCommissionIfNeeded(nextGrossText = it)
                    },
                    onGrossCashChange = { grossCashText = it },
                    onGrossCardChange = { grossCardText = it },
                    onRefundsChange = {
                        refundsText = it
                        applyAutoCalculatedCommissionIfNeeded(nextRefundsText = it)
                    },
                    onCommissionChange = { commissionText = it },
                    onCommissionPaymentTypeChange = { commissionPaymentType = it },
                    onRecalculateCommission = {
                        val commission = TaskCommissionCalculator.calculatedCommission(
                            gross = draft.gross,
                            refunds = draft.refunds,
                            location = appLocation
                        )
                        if (commission != null) {
                            commissionText = formatMoneyInput(commission)
                        }
                    },
                    onRecalculateGrossBreakdown = {
                        val gross = TaskCommissionCalculator.recalculatedGrossFromBreakdown(
                            grossCash = draft.grossCash,
                            grossCard = draft.grossCard
                        )
                        val nextGrossText = formatMoneyInput(gross)
                        grossText = nextGrossText
                        applyAutoCalculatedCommissionIfNeeded(nextGrossText = nextGrossText)
                    }
                )
            }
        }
        val activeRefillEditor = refillEditor.takeIf { canEditRefill }
        if (activeRefillEditor != null) {
            RefillInventoryCompletionView(
                state = refillState ?: RefillInventoryUiState(
                    taskId = task.id,
                    sourceMode = task.inventorySourceMode ?: RefillInventorySourceMode.Warehouse,
                    selectedWarehouseId = task.inventorySourceWarehouseId,
                    selectedWarehouseName = task.inventorySourceWarehouseName,
                    isLoading = true
                ),
                warehouses = activeRefillEditor.warehouses,
                canComplete = refillState?.taskId == task.id && !isUpdating,
                onRefilledChanged = { itemId, value ->
                    activeRefillEditor.onRefilledChanged(task.id, itemId, value)
                },
                onFinalStockChanged = { itemId, value ->
                    activeRefillEditor.onFinalStockChanged(task.id, itemId, value)
                },
                onSourceSelected = { sourceMode, warehouseId ->
                    activeRefillEditor.onSourceSelected(task, sourceMode, warehouseId)
                },
                onComplete = {
                    activeRefillEditor.onComplete(task)
                }
            )
        }
    }
}

@Composable
private fun FinancialLabelValueRowForTask(label: String, value: String) {
    val palette = LocalVendistriPalette.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = palette.textSecondary, fontWeight = FontWeight.SemiBold)
        Text(value, color = palette.textPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun CompactTaskButton(
    text: String,
    enabled: Boolean = true,
    primary: Boolean = false,
    onClick: () -> Unit
) {
    val palette = LocalVendistriPalette.current
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = 34.dp),
        shape = RoundedCornerShape(8.dp),
        colors = if (primary) {
            ButtonDefaults.textButtonColors(
                containerColor = AppColors.vendBlue,
                contentColor = AppColors.surface,
                disabledContainerColor = palette.surfaceVariant,
                disabledContentColor = palette.textSecondary.copy(alpha = 0.55f)
            )
        } else {
            ButtonDefaults.textButtonColors(
                contentColor = palette.brand,
                disabledContentColor = palette.textSecondary.copy(alpha = 0.55f)
            )
        }
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

private fun Double.roundToWhole(): Double = kotlin.math.round(this)
