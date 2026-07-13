package com.vendistri.operations.features.tasks

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vendistri.operations.components.SearchableDropdown
import com.vendistri.operations.components.SearchableDropdownOption

@Composable
internal fun LocationFilterMenu(
    locations: List<TaskLocationGroup>,
    selectedLocationId: String?,
    onLocationSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    SearchableDropdown(
        allLabel = "All locations",
        options = locations.map(::searchableOption),
        selectedId = selectedLocationId,
        onSelected = onLocationSelected,
        modifier = modifier
    )
}

private fun searchableOption(location: TaskLocationGroup): SearchableDropdownOption {
    val machineNames = location.machineGroups.map { it.name }
    val taskNames = location.tasks.map { taskTypeLabel(it.type) }
    val searchText = (listOf(location.name) + machineNames + taskNames).joinToString(" ")

    return SearchableDropdownOption(
        id = location.id,
        title = location.name,
        subtitle = locationSummaryText(location),
        searchText = searchText,
        statusIndicatorColors = TaskStatusHelpers.indicatorColors(location.tasks)
    )
}
