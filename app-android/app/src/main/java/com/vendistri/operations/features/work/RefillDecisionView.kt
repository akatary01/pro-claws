package com.vendistri.operations.features.work

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.vendistri.operations.design.AppColors
import com.vendistri.operations.features.tasks.InventoryStockFormatters
import com.vendistri.operations.features.tasks.oneDecimal
import com.vendistri.operations.utils.AddressFormatter
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun RefillDecisionView(
    state: RefillDecisionUiState,
    onActionSelected: (RefillDecisionAction) -> Unit,
    onWarehouseSelected: (String) -> Unit,
    onApply: () -> Unit,
    onTaskInclusionToggle: (String) -> Unit = {},
    destinationTitle: String? = null,
    destinationAddress: String? = null,
    routePreview: RoutePreview? = null,
    modifier: Modifier = Modifier
) {
    if (!state.isVisible) return

    val destinationName = destinationTitle ?: state.anchorTask?.locationName ?: "location"
    val resolvedDestinationAddress = destinationAddress
        ?: AddressFormatter.singleLineWithoutCountry(state.anchorTask?.locationAddress)
    var expandedOptions by remember(state.selectedAction, state.isPickupAlreadyCovered) {
        mutableStateOf(setOf(state.selectedAction))
    }
    var expandedLocationIds by remember { mutableStateOf(emptySet<String>()) }
    var expandedMachineIds by remember { mutableStateOf(emptySet<String>()) }
    val canApply = state.canApply

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Refill Inventory",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = refillDecisionSubtitle(state),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onApply,
                enabled = !state.isApplying,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canApply) AppColors.vendBlue else MaterialTheme.colorScheme.outline,
                    contentColor = Color.White,
                    disabledContainerColor = MaterialTheme.colorScheme.outline,
                    disabledContentColor = Color.White
                )
            ) {
                if (state.isApplying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text("Go", fontWeight = FontWeight.Bold)
                }
            }
        }

        RefillRouteHeader(
            destinationName = destinationName,
            destinationAddress = resolvedDestinationAddress,
            routePreview = routePreview
        )

        if (state.isPickupAlreadyCovered) {
            CoveredPickupSection(state = state)
        } else {
            RecommendedPickupSection(
                state = state,
                onWarehouseSelected = onWarehouseSelected
            )

            RefillCoverageNotice(state = state)
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.currentStopSelectionRequiredMessage?.let { message ->
                Text(
                    text = message,
                    color = AppColors.statusError,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
            if (state.isPickupAlreadyCovered) {
                RefillDecisionOptionCard(
                    action = RefillDecisionAction.RouteToLocation,
                    selectedAction = state.selectedAction,
                    title = "Route to $destinationName",
                    subtitle = "Route directly to $destinationName.",
                    expandedOptions = expandedOptions,
                    onExpandedOptionsChange = { expandedOptions = it },
                    enabled = true,
                    onActionSelected = onActionSelected
                )
            } else {
                RefillDecisionOptionCard(
                    action = RefillDecisionAction.RouteToWarehouse,
                    selectedAction = state.selectedAction,
                    title = "Route to Warehouse",
                    subtitle = "Create one pickup stop at ${state.selectedWarehouse?.name ?: "the warehouse"}, pick up stock, then continue to $destinationName.",
                    expandedOptions = expandedOptions,
                    onExpandedOptionsChange = { expandedOptions = it },
                    enabled = state.warehouses.isNotEmpty(),
                    onActionSelected = onActionSelected
                )
                RefillDecisionOptionCard(
                    action = RefillDecisionAction.UseWarehouseStock,
                    selectedAction = state.selectedAction,
                    title = "Use Warehouse Stock",
                    subtitle = "Route directly to $destinationName and deduct these refills from warehouse stock.",
                    expandedOptions = expandedOptions,
                    onExpandedOptionsChange = { expandedOptions = it },
                    enabled = state.warehouses.isNotEmpty(),
                    onActionSelected = onActionSelected
                )
                RefillDecisionOptionCard(
                    action = RefillDecisionAction.UseUntrackedStock,
                    selectedAction = state.selectedAction,
                    title = "Use Untracked Stock",
                    subtitle = "Route directly to $destinationName and update only machine inventory.",
                    expandedOptions = expandedOptions,
                    onExpandedOptionsChange = { expandedOptions = it },
                    enabled = true,
                    onActionSelected = onActionSelected
                )
            }
        }

        WarehouseStockSection(lines = state.warehouseAvailabilityLines)

        RefillBreakdownSection(
            state = state,
            expandedLocationIds = expandedLocationIds,
            expandedMachineIds = expandedMachineIds,
            onLocationToggle = { locationId -> expandedLocationIds = toggleStringSet(expandedLocationIds, locationId) },
            onMachineToggle = { machineId -> expandedMachineIds = toggleStringSet(expandedMachineIds, machineId) },
            onTaskInclusionToggle = onTaskInclusionToggle
        )

        state.errorMessage?.let { message ->
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
private fun RefillRouteHeader(
    destinationName: String,
    destinationAddress: String?,
    routePreview: RoutePreview?
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = "ROUTE TO ${destinationName.uppercase()}",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        destinationAddress?.let { address ->
            Text(
                text = address,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
        routePreview?.let { preview ->
            Row(modifier = Modifier.fillMaxWidth()) {
                RouteMetric("Arrival", refillArrivalText(preview.expectedTravelSeconds), Modifier.weight(1f))
                RouteMetric("Time", refillDurationText(preview.expectedTravelSeconds), Modifier.weight(1f))
                RouteMetric("Distance", "${oneDecimal(preview.distanceMiles)} mi", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RouteMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = AppColors.muted, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RecommendedPickupSection(
    state: RefillDecisionUiState,
    onWarehouseSelected: (String) -> Unit
) {
    if (state.warehouses.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "RECOMMENDED PICKUP",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                RefillDecisionWarehousePicker(
                    state = state,
                    onWarehouseSelected = onWarehouseSelected,
                    modifier = Modifier.fillMaxWidth()
                )
                AddressFormatter.singleLineWithoutCountry(state.selectedWarehouse?.address)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { address ->
                        Text(
                            text = address,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
            }
            Column(
                modifier = Modifier.width(104.dp),
                horizontalAlignment = Alignment.End
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else if (state.warehouseRoutePreview != null) {
                    val preview = state.warehouseRoutePreview
                    Text(
                        text = refillDurationText(preview.expectedTravelSeconds),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End
                    )
                    Text(
                        text = "${refillArrivalText(preview.expectedTravelSeconds)} • ${oneDecimal(preview.distanceMiles)} mi",
                        color = AppColors.muted,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End
                    )
                } else {
                    Text(
                        text = "--",
                        color = AppColors.muted,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

@Composable
private fun CoveredPickupSection(state: RefillDecisionUiState) {
    val coveredTask = state.coveredPickupPlan?.task
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "COVERED PICKUP",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        coveredTask?.locationName?.takeIf { it.isNotBlank() }?.let { locationName ->
            Text(
                text = locationName,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        AddressFormatter.singleLineWithoutCountry(coveredTask?.locationAddress)
            ?.takeIf { it.isNotBlank() }
            ?.let { address ->
                Text(
                    text = address,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        Text(
            text = "Pickup inventory is already covered for this stop.",
            color = AppColors.statusDone,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun RefillCoverageNotice(state: RefillDecisionUiState) {
    val units = state.remainingUncoveredUnits
    if (!state.hasRemainingPlans || units <= 0) return
    Text(
        text = "Some inventory is already picked up. These options only apply to the remaining $units ${plural("unit", units)} not already picked up.",
        color = AppColors.muted,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun RefillDecisionOptionCard(
    action: RefillDecisionAction,
    selectedAction: RefillDecisionAction,
    title: String,
    subtitle: String,
    expandedOptions: Set<RefillDecisionAction>,
    onExpandedOptionsChange: (Set<RefillDecisionAction>) -> Unit,
    enabled: Boolean,
    onActionSelected: (RefillDecisionAction) -> Unit
) {
    val isSelected = selectedAction == action
    val isExpanded = action in expandedOptions
    Surface(
        onClick = {
            if (isSelected) {
                onExpandedOptionsChange(toggleActionSet(expandedOptions, action))
            } else {
                onActionSelected(action)
                onExpandedOptionsChange(expandedOptions + action)
            }
        },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) AppColors.vendBlue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) AppColors.vendBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = if (isSelected) AppColors.vendBlue else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (isExpanded) "^" else "v",
                    color = if (isSelected) AppColors.vendBlue else AppColors.muted,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            if (isExpanded) {
                Text(
                    text = subtitle,
                    color = if (isSelected) AppColors.vendBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun RefillDecisionWarehousePicker(
    state: RefillDecisionUiState,
    onWarehouseSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = state.selectedWarehouse?.name ?: "Select warehouse"

    BoxWithConstraints(modifier = modifier) {
        val menuWidth = maxWidth
        OutlinedButton(
            onClick = { expanded = true },
            enabled = state.warehouses.isNotEmpty() && !state.isApplying,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    selectedLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start
                )
                Text("v", color = AppColors.muted, fontWeight = FontWeight.Bold)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(menuWidth)
        ) {
            state.warehouses.forEach { warehouse ->
                val isSuggested = warehouse.id == state.recommendedWarehouseId
                DropdownMenuItem(
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = suggestedPrefixText(warehouse.name, isSuggested),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Start,
                                fontWeight = FontWeight.SemiBold
                            )
                            warehouse.address?.let { address ->
                                val addressLine = AddressFormatter.singleLineWithoutCountry(address)
                                if (!addressLine.isNullOrBlank()) {
                                    Text(
                                        text = addressLine,
                                        color = AppColors.muted,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onWarehouseSelected(warehouse.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun WarehouseStockSection(lines: List<WarehouseAvailabilityLine>) {
    if (lines.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Warehouse Stock")
        lines.forEach { line ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(line.productName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Available ${line.available} • Needed ${line.needed}",
                        color = AppColors.muted,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = line.status.label,
                    color = line.status.color,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun RefillBreakdownSection(
    state: RefillDecisionUiState,
    expandedLocationIds: Set<String>,
    expandedMachineIds: Set<String>,
    onLocationToggle: (String) -> Unit,
    onMachineToggle: (String) -> Unit,
    onTaskInclusionToggle: (String) -> Unit
) {
    val locations = state.breakdownLocations
    if (locations.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Refill Breakdown")
        Row(modifier = Modifier.fillMaxWidth()) {
            BreakdownMetric("Machines", state.breakdownMachineCount.toString(), Modifier.weight(1f))
            BreakdownMetric("Products", state.breakdownProductCount.toString(), Modifier.weight(1f))
            BreakdownMetric("Quantity", state.breakdownQuantity.toString(), Modifier.weight(1f))
        }
        locations.forEach { location ->
            RefillBreakdownLocationRow(
                location = location,
                isExpanded = location.id in expandedLocationIds,
                expandedMachineIds = expandedMachineIds,
                onLocationToggle = onLocationToggle,
                onMachineToggle = onMachineToggle,
                onTaskInclusionToggle = onTaskInclusionToggle
            )
        }
        if (state.hasRemainingPlans && state.includedPlans.isEmpty() && !state.canContinueWithoutPickup) {
            Text(
                text = "Add at least one machine to continue.",
                color = AppColors.statusError,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun RefillBreakdownLocationRow(
    location: RefillBreakdownLocation,
    isExpanded: Boolean,
    expandedMachineIds: Set<String>,
    onLocationToggle: (String) -> Unit,
    onMachineToggle: (String) -> Unit,
    onTaskInclusionToggle: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onLocationToggle(location.id) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(location.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Text(
                    text = location.subtitle,
                    color = AppColors.muted,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(if (isExpanded) "^" else "v", color = AppColors.muted, fontWeight = FontWeight.Bold)
        }
        if (isExpanded) {
            Column(modifier = Modifier.padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                location.machines.forEach { machine ->
                    RefillBreakdownMachineRow(
                        machine = machine,
                        isExpanded = machine.id in expandedMachineIds,
                        onMachineToggle = onMachineToggle,
                        onTaskInclusionToggle = onTaskInclusionToggle
                    )
                }
            }
        }
    }
}

@Composable
private fun RefillBreakdownMachineRow(
    machine: RefillBreakdownMachine,
    isExpanded: Boolean,
    onMachineToggle: (String) -> Unit,
    onTaskInclusionToggle: (String) -> Unit
) {
    val subtitle = machine.subtitle
    val subtitleColor = when {
        subtitle == "Covered" -> AppColors.statusDone
        !machine.isIncluded && machine.isRemaining -> AppColors.statusError
        else -> AppColors.muted
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onMachineToggle(machine.id) }
            ) {
                Text(
                    text = machine.name,
                    color = if (machine.isIncluded || !machine.isRemaining) MaterialTheme.colorScheme.onSurface else AppColors.muted,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = subtitleColor,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = if (isExpanded) "^" else "v",
                color = AppColors.muted,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .clickable { onMachineToggle(machine.id) }
            )
            if (machine.isRemaining) {
                Text(
                    text = if (machine.isIncluded) "Remove" else "Add back",
                    color = if (machine.isIncluded) AppColors.statusError else AppColors.vendBlue,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onTaskInclusionToggle(machine.taskId) }
                )
            }
        }
        if (isExpanded) {
            machine.items.forEach { item ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = item.productName,
                        color = if (machine.isIncluded || !machine.isRemaining) MaterialTheme.colorScheme.onSurface else AppColors.muted,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = refillQuantityText(item),
                        color = AppColors.muted,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(Locale.US),
        color = AppColors.muted,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun BreakdownMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = AppColors.muted, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

private val RefillDecisionAction.label: String
    get() = when (this) {
        RefillDecisionAction.RouteToLocation -> "Go to location"
        RefillDecisionAction.RouteToWarehouse -> "Pickup first"
        RefillDecisionAction.UseWarehouseStock -> "Warehouse stock"
        RefillDecisionAction.UseUntrackedStock -> "Untracked stock"
    }

private val RefillDecisionAction.selectedLabel: String
    get() = when (this) {
        RefillDecisionAction.RouteToLocation -> "Location selected"
        RefillDecisionAction.RouteToWarehouse -> "Pickup selected"
        RefillDecisionAction.UseWarehouseStock -> "Warehouse selected"
        RefillDecisionAction.UseUntrackedStock -> "Untracked selected"
    }

private val RefillDecisionAction.applyLabel: String
    get() = when (this) {
        RefillDecisionAction.RouteToLocation -> "Start route"
        RefillDecisionAction.RouteToWarehouse -> "Create pickup route"
        RefillDecisionAction.UseWarehouseStock -> "Use warehouse and start"
        RefillDecisionAction.UseUntrackedStock -> "Use untracked and start"
    }

private val RefillDecisionAction.requiresWarehouse: Boolean
    get() = this == RefillDecisionAction.RouteToWarehouse || this == RefillDecisionAction.UseWarehouseStock

private fun refillDecisionSubtitle(state: RefillDecisionUiState): String {
    val tasks = state.metricPlans.map { it.task }
    val locationCount = tasks.mapNotNull { it.location ?: it.locationName }.toSet().size.coerceAtLeast(1)
    val machineCount = tasks.mapNotNull { it.machine ?: it.machineName }.toSet().size.coerceAtLeast(1)
    val taskCount = tasks.size
    return "$locationCount ${plural("location", locationCount)} • $machineCount ${plural("machine", machineCount)} • $taskCount refill ${plural("task", taskCount)}"
}

private fun plural(word: String, count: Int): String = if (count == 1) word else "${word}s"

private fun refillDurationText(seconds: Double): String {
    val minutes = (seconds / 60.0).toInt().coerceAtLeast(0)
    val hours = minutes / 60
    val remainder = minutes % 60
    return if (hours > 0) "${hours}h ${remainder}m" else "${minutes}m"
}

private fun refillArrivalText(seconds: Double): String {
    return LocalTime.now()
        .plusSeconds(seconds.toLong().coerceAtLeast(0L))
        .format(DateTimeFormatter.ofPattern("H:mm", Locale.US))
}

private fun toggleStringSet(values: Set<String>, value: String): Set<String> {
    return if (value in values) values - value else values + value
}

private fun toggleActionSet(
    values: Set<RefillDecisionAction>,
    value: RefillDecisionAction
): Set<RefillDecisionAction> {
    return if (value in values) values - value else values + value
}

private val WarehouseStockStatus.label: String
    get() = when (this) {
        WarehouseStockStatus.Available -> "Available"
        WarehouseStockStatus.Partial -> "Partial"
        WarehouseStockStatus.None -> "No stock"
    }

private val WarehouseStockStatus.color: Color
    get() = when (this) {
        WarehouseStockStatus.Available -> AppColors.statusDone
        WarehouseStockStatus.Partial -> AppColors.statusPending
        WarehouseStockStatus.None -> AppColors.statusError
    }

private val RefillBreakdownLocation.subtitle: String
    get() {
        val machineText = "$machineCount ${plural("machine", machineCount)}"
        return when {
            needed > 0 -> "$machineText • Needed $needed"
            pickedUp > 0 -> "$machineText • Picked up $pickedUp"
            else -> machineText
        }
    }

private val RefillBreakdownMachine.subtitle: String
    get() = when {
        !isIncluded && isRemaining -> "Removed"
        items.isEmpty() -> "No refill items"
        needed > 0 && pickedUp > 0 -> "Picked up $pickedUp • Remaining $needed"
        needed > 0 -> "Needed $needed"
        pickedUp > 0 -> "Picked up $pickedUp"
        isRemaining -> "Needed 0"
        else -> "Covered"
    }

private fun suggestedPrefixText(name: String, isSuggested: Boolean) = buildAnnotatedString {
    if (isSuggested) {
        withStyle(SpanStyle(color = AppColors.vendBlue)) {
            append("(Suggested)")
        }
        append(" ")
    }
    append(name)
}

private fun refillQuantityText(item: RefillBreakdownItem) = buildAnnotatedString {
    val suggestedQuantity = if (item.pickedUp > 0) item.pickedUp else item.needed
    append("${InventoryStockFormatters.stockText(item.currentStock, item.capacity)} • Suggested ")
    withStyle(SpanStyle(color = AppColors.statusDone)) {
        append("+$suggestedQuantity")
    }
}
