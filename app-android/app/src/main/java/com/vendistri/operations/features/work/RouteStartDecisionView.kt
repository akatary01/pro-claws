package com.vendistri.operations.features.work

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vendistri.operations.components.PrimaryActionButton
import com.vendistri.operations.design.AppColors
import com.vendistri.operations.features.tasks.VendiTask

@Composable
fun RouteStartDecisionView(
    state: WorkUiState,
    onChoiceSelected: (RouteStartScopeChoice) -> Unit,
    onLaterRefillTaskToggle: (String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val decision = state.routeStartScopeDecision ?: return
    val selectedChoice = state.selectedRouteStartScopeChoice ?: decision.defaultChoice
    val selectedOption = decision.option(selectedChoice)
    val laterRefillTasks = decision.laterRefillTasks
    val showsOnlyLaterRefills = !decision.requiresConfirmation && laterRefillTasks.isNotEmpty()
    val showsConfirmationOnly = decision.requiresConfirmation && !decision.requiresChoice && laterRefillTasks.isEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = if (showsOnlyLaterRefills) "Later Refill Stops" else "Start Route",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = when {
                    showsOnlyLaterRefills -> "Pick unassigned future refill work that should be included in this pickup inventory task."
                    showsConfirmationOnly -> "Confirm these tasks before starting route."
                    else -> "Choose what to work at ${decision.stopTitle}."
                },
                color = AppColors.muted,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            routeStartClaimInfoText(
                routeTaskIds = selectedOption.claimTaskIds,
                laterTaskIds = state.selectedLaterRefillTaskIds.intersect(laterRefillTasks.map { it.id }.toSet()),
                allTasks = selectedOption.claimTasks + laterRefillTasks,
                locationName = decision.stopTitle
            )?.let {
                Text(
                    text = it,
                    color = AppColors.statusUnassigned,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (!showsConfirmationOnly) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (decision.requiresChoice) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        RouteStartOptionRow(
                            option = decision.selectedMachineOption,
                            isSelected = selectedChoice == RouteStartScopeChoice.SelectedMachine,
                            onSelected = onChoiceSelected
                        )
                        RouteStartOptionRow(
                            option = decision.fullStopOption,
                            isSelected = selectedChoice == RouteStartScopeChoice.FullStop,
                            onSelected = onChoiceSelected
                        )
                    }
                }

                if (laterRefillTasks.isNotEmpty()) {
                    LaterRefillStopsSection(
                        tasks = laterRefillTasks,
                        selectedTaskIds = state.selectedLaterRefillTaskIds,
                        showsHeader = !showsOnlyLaterRefills,
                        onToggle = onLaterRefillTaskToggle
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        PrimaryActionButton(
            text = "Continue",
            onClick = onContinue,
            enabled = !state.isLoading,
            isLoading = state.isLoading,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun RouteStartOptionRow(
    option: RouteStartScopeOption,
    isSelected: Boolean,
    onSelected: (RouteStartScopeChoice) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onSelected(option.choice) },
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) AppColors.vendBlue.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (isSelected) AppColors.vendBlue.copy(alpha = 0.45f) else AppColors.border)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            SelectionDot(isSelected = isSelected)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(option.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    option.subtitle,
                    color = AppColors.muted,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun LaterRefillStopsSection(
    tasks: List<VendiTask>,
    selectedTaskIds: Set<String>,
    showsHeader: Boolean,
    onToggle: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showsHeader) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "LATER REFILL STOPS",
                    color = AppColors.muted,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Pick unassigned future refill work that should be included in this pickup inventory task.",
                    color = AppColors.muted,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        tasks.forEach { task ->
            LaterRefillStopRow(
                task = task,
                isSelected = task.id in selectedTaskIds,
                onToggle = onToggle
            )
        }
    }
}

@Composable
private fun LaterRefillStopRow(
    task: VendiTask,
    isSelected: Boolean,
    onToggle: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = task.machineName ?: "Refill",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = task.locationName ?: "Later stop",
                    color = AppColors.muted,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = if (isSelected) "Assigned" else "Unassigned",
                    color = if (isSelected) AppColors.statusDone else AppColors.statusUnassigned,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (isSelected) "Assigned" else "Assign",
                    color = AppColors.vendBlue,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onToggle(task.id) }
                )
            }
        }
        androidx.compose.material3.HorizontalDivider(color = AppColors.border.copy(alpha = 0.45f))
    }
}

@Composable
private fun SelectionDot(isSelected: Boolean) {
    Surface(
        modifier = Modifier.size(20.dp),
        shape = CircleShape,
        color = if (isSelected) AppColors.vendBlue else MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, if (isSelected) AppColors.vendBlue else AppColors.muted.copy(alpha = 0.7f))
    ) {
        if (isSelected) {
            Text(
                text = "✓",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

private fun routeStartClaimInfoText(
    routeTaskIds: Set<String>,
    laterTaskIds: Set<String>,
    allTasks: List<VendiTask>,
    locationName: String
): String? {
    val currentCount = allTasks.count { it.id in routeTaskIds && (it.assignee.isNullOrBlank() || it.status == com.vendistri.operations.features.tasks.TaskStatus.Unassigned) }
    val laterCount = allTasks.count { it.id in laterTaskIds && (it.assignee.isNullOrBlank() || it.status == com.vendistri.operations.features.tasks.TaskStatus.Unassigned) }
    if (currentCount + laterCount == 0) return null
    val currentText = if (currentCount > 0) "$currentCount unassigned ${if (currentCount == 1) "task" else "tasks"} at $locationName will be assigned to you" else null
    val laterText = if (laterCount > 0) "$laterCount selected later refill ${if (laterCount == 1) "task" else "tasks"} will also be assigned" else null
    return listOfNotNull(currentText, laterText).joinToString(". ") + "."
}
