package com.vendistri.operations.features.work

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vendistri.operations.components.RemoteImagePreview
import com.vendistri.operations.design.AppColors
import com.vendistri.operations.design.AppShapes
import com.vendistri.operations.design.LocalVendistriPalette
import com.vendistri.operations.features.refill.productTitle
import com.vendistri.operations.features.tasks.TaskGroupingHelpers
import com.vendistri.operations.features.tasks.TaskPickupLine
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskStatusPresentation
import com.vendistri.operations.features.tasks.VendiTask
import com.vendistri.operations.features.tasks.formatTaskDuration
import com.vendistri.operations.features.tasks.oneDecimal
import com.vendistri.operations.utils.AddressFormatter

data class CompletedPickupTaskSection(
    val task: VendiTask,
    val productGroups: List<CompletedPickupProductGroup>
)

data class CompletedPickupProductGroup(
    val productId: String,
    val productTitle: String,
    val lines: List<TaskPickupLine>
)

object CompletedPickupDisplay {
    fun sections(
        tasks: List<VendiTask>,
        primaryRefillTaskIds: Set<String> = emptySet(),
        primaryTaskIds: List<String> = emptyList()
    ): List<CompletedPickupTaskSection> {
        return stableTasks(tasks, primaryTaskIds).mapNotNull { task ->
            val lines = if (primaryRefillTaskIds.isEmpty()) {
                task.pickupLines
            } else {
                task.pickupLines.filter { line -> line.refillTaskId != null && line.refillTaskId in primaryRefillTaskIds }
            }
            if (primaryRefillTaskIds.isNotEmpty() && lines.isEmpty()) return@mapNotNull null
            CompletedPickupTaskSection(
                task = task,
                productGroups = lines
                    .groupBy { it.product.id }
                    .map { (productId, groupLines) ->
                        val product = groupLines.first().product
                        CompletedPickupProductGroup(
                            productId = productId,
                            productTitle = productTitle(product.name, product.brand, product.size),
                            lines = groupLines
                        )
                    }
            )
        }
    }

    fun title(tasks: List<VendiTask>): String {
        val uniqueTasks = TaskGroupingHelpers.uniqueTasksById(tasks)
        return if (uniqueTasks.all { it.status == TaskStatus.Done }) "Completed Pickup Inventory" else "Linked Pickup Inventory"
    }

    fun metricText(task: VendiTask): String {
        val durationMinutes = TaskGroupingHelpers.totalDurationMinutes(listOf(task))
        val distanceMiles = TaskGroupingHelpers.totalDistanceMiles(listOf(task))
        return "${formatTaskDuration(durationMinutes * 60.0)} • ${oneDecimal(distanceMiles.coerceAtLeast(0.0))} mi"
    }

    private fun stableTasks(tasks: List<VendiTask>, primaryTaskIds: List<String>): List<VendiTask> {
        val primaryOrder = primaryTaskIds.withIndex().associate { it.value to it.index }
        val seen = mutableSetOf<String>()
        return tasks
            .mapIndexed { index, task -> index to task }
            .filter { (_, task) -> seen.add(task.id) }
            .sortedWith(
                compareBy<Pair<Int, VendiTask>> { (_, task) -> primaryOrder[task.id] ?: Int.MAX_VALUE }
                    .thenBy { it.first }
            )
            .map { it.second }
    }
}

data class CompletedPickupPhotoActions(
    val pendingMutationTaskIds: Set<String>,
    val onAddPhoto: (VendiTask) -> Unit,
    val onRemovePhoto: (VendiTask) -> Unit
)

@Composable
fun CompletedPickupWorkBlock(
    tasks: List<VendiTask>,
    primaryRefillTaskIds: Set<String> = emptySet(),
    primaryTaskIds: List<String> = emptyList(),
    modifier: Modifier = Modifier,
    photoActions: CompletedPickupPhotoActions? = null
) {
    if (tasks.isEmpty()) return
    val sections = CompletedPickupDisplay.sections(tasks, primaryRefillTaskIds, primaryTaskIds)
    if (sections.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = CompletedPickupDisplay.title(tasks),
            color = LocalVendistriPalette.current.textSecondary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
        sections.forEach { section ->
            CompletedPickupTaskSectionView(section = section, photoActions = photoActions)
        }
    }
}

@Composable
private fun CompletedPickupTaskSectionView(
    section: CompletedPickupTaskSection,
    photoActions: CompletedPickupPhotoActions?
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = section.task.warehouseName ?: "Warehouse",
                    color = LocalVendistriPalette.current.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                section.task.warehouseAddress
                    ?.let(AddressFormatter::singleLineWithoutCountry)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { address ->
                        Text(
                            text = address,
                            color = LocalVendistriPalette.current.textSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = CompletedPickupDisplay.metricText(section.task),
                    color = LocalVendistriPalette.current.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                CompletedPickupStatusPill(status = section.task.status)
            }
        }
        section.productGroups.forEach { group ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = group.productTitle,
                    color = LocalVendistriPalette.current.textPrimary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                group.lines.forEach { line ->
                    CompletedPickupLineRow(task = section.task, line = line)
                }
            }
        }
        photoActions?.let { CompletedPickupPhotoButton(task = section.task, actions = it) }
    }
}

@Composable
private fun CompletedPickupStatusPill(status: TaskStatus) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(7.dp),
            shape = CircleShape,
            color = TaskStatusPresentation.indicatorColor(status)
        ) {}
        Text(
            text = TaskStatusPresentation.label(status),
            color = LocalVendistriPalette.current.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun CompletedPickupLineRow(task: VendiTask, line: TaskPickupLine) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Text(
            text = line.machineName ?: task.displayMachine,
            color = LocalVendistriPalette.current.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "Picked up ${line.pickedUpQuantity ?: 0}",
            color = if ((line.pickedUpQuantity ?: 0) > 0) AppColors.statusDone else AppColors.statusError,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CompletedPickupPhotoButton(task: VendiTask, actions: CompletedPickupPhotoActions) {
    val isUpdating = task.id in actions.pendingMutationTaskIds
    val photo = task.photoConfirmationAsset
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        photo?.url?.let { url ->
            RemoteImagePreview(
                url = url,
                contentDescription = "Task confirmation photo",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .clip(AppShapes.card),
                placeholder = {
                    PhotoPlaceholder(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(132.dp)
                    )
                }
            )
        }
        OutlinedButton(
            onClick = {
                if (photo == null) {
                    actions.onAddPhoto(task)
                } else {
                    actions.onRemovePhoto(task)
                }
            },
            enabled = !isUpdating
        ) {
            Text(if (photo == null) "Add Photo" else "Remove Photo")
        }
    }
}
