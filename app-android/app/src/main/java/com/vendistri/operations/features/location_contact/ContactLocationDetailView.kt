package com.vendistri.operations.features.location_contact

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vendistri.operations.components.BackButton
import com.vendistri.operations.components.InAppBrowserView
import com.vendistri.operations.components.RemoteImagePreview
import com.vendistri.operations.components.RevenueChip
import com.vendistri.operations.components.SearchableDropdown
import com.vendistri.operations.components.SearchableDropdownOption
import com.vendistri.operations.design.AppColors
import com.vendistri.operations.design.LocalVendistriPalette
import com.vendistri.operations.features.location.AppLocation
import com.vendistri.operations.features.location.PortalLocationMachine
import com.vendistri.operations.features.map.LocationStopsBuilder
import com.vendistri.operations.features.tasks.LiveTaskPill
import com.vendistri.operations.features.tasks.TaskFinancialHelpers
import com.vendistri.operations.features.tasks.TaskGroupingHelpers
import com.vendistri.operations.features.tasks.TaskScheduleDate
import com.vendistri.operations.features.tasks.TaskFinancialDisplayMode
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskLiveTargetResolver
import com.vendistri.operations.features.tasks.VendiTask
import com.vendistri.operations.features.tasks.formatDuration
import com.vendistri.operations.features.tasks.money
import com.vendistri.operations.features.tasks.oneDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ContactLocationDetailView(
    state: ContactUiState,
    tasks: List<VendiTask>,
    selectedLocationId: String?,
    onLocationSelected: (String?) -> Unit,
    onLoadMachines: (String) -> Unit,
    onOpenTasks: (LocalDate, String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedLocation = selectedLocationId?.let(state.locationsById::get)
    var webUrl by remember { mutableStateOf<String?>(null) }
    Box(modifier = modifier.fillMaxSize()) {
        if (selectedLocation == null) {
            ContactLocationList(
                locations = state.sortedLocations,
                machinesByLocationId = state.machinesByLocationId,
                tasks = tasks,
                onLocationSelected = onLocationSelected,
                onOpenTasks = onOpenTasks,
                onClose = onClose
            )
        } else {
            LaunchedEffect(selectedLocation.id) { onLoadMachines(selectedLocation.id) }
            ContactLocationDetail(
                location = selectedLocation,
                machines = state.machinesByLocationId[selectedLocation.id].orEmpty(),
                tasks = tasks.filter { it.location == selectedLocation.id },
                allTasks = tasks,
                onBack = { onLocationSelected(null) },
                onOpenTasks = { date -> onOpenTasks(date, selectedLocation.id) },
                onOpenServiceForm = { machineId ->
                    webUrl = ContactServiceFormUrl.forMachine(machineId)
                }
            )
        }
        webUrl?.let { url ->
            InAppBrowserView(url = url, onClose = { webUrl = null })
        }
    }
}

@Composable
private fun ContactLocationList(
    locations: List<AppLocation>,
    machinesByLocationId: Map<String, List<PortalLocationMachine>>,
    tasks: List<VendiTask>,
    onLocationSelected: (String?) -> Unit,
    onOpenTasks: (LocalDate, String) -> Unit,
    onClose: () -> Unit
) {
    val palette = LocalVendistriPalette.current
    var query by remember { mutableStateOf("") }
    var expandedLocationIds by remember { mutableStateOf(emptySet<String>()) }
    val visibleLocations = remember(locations, machinesByLocationId, query) {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) locations else locations.filter {
            it.name.lowercase().contains(normalized) ||
                it.address?.singleLine.orEmpty().lowercase().contains(normalized) ||
                machinesByLocationId[it.id].orEmpty().any { machine -> machine.name.lowercase().contains(normalized) }
        }
    }
    val visibleTasks = ContactVisibilityRules.visibleTasks(tasks)
    val totals = TaskFinancialHelpers.sumTaskFinancials(visibleTasks)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ContactLocationHeader(amount = totals.commission, onBack = onClose)
        }
        item {
            ContactMetricRow(
                values = listOf(
                    "Locations" to locations.size.toString(),
                    "Machines" to locations.sumOf { location ->
                        machineCount(location.id, machinesByLocationId, visibleTasks)
                    }.toString(),
                    "Tasks" to visibleTasks.size.toString()
                )
            )
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search locations") }
            )
        }
        items(visibleLocations, key = { it.id }) { location ->
            val locationTasks = visibleTasks.filter { it.location == location.id }
            val displayTasks = LocationStopsBuilder.contactDisplayTasks(location.id, tasks)
            val displayDate = LocationStopsBuilder.contactDisplayDate(location.id, tasks)
            val liveTarget = TaskLiveTargetResolver.target(locationTasks, visibleTasks)
            val isExpanded = location.id in expandedLocationIds
            Surface(
                onClick = {
                    expandedLocationIds = if (isExpanded) expandedLocationIds - location.id else expandedLocationIds + location.id
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = palette.surface,
                border = BorderStroke(1.dp, palette.border)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(location.name, color = palette.textPrimary, fontWeight = FontWeight.Bold)
                            Text(location.address?.singleLine.orEmpty(), color = palette.textSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            liveTarget?.let {
                                Box(modifier = Modifier.clickable {
                                    TaskScheduleDate.parse(it.navigateTask.scheduledFor)?.let { date -> onOpenTasks(date, location.id) }
                                }) { LiveTaskPill(it, ContactVisibilityRules.canSeeTaskMetrics(location)) }
                            }
                            Text(if (isExpanded) "⌃" else "⌄", color = palette.textSecondary)
                            val count = machineCount(location.id, machinesByLocationId, visibleTasks)
                            Text("$count ${if (count == 1) "machine" else "machines"}", color = palette.textSecondary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (isExpanded) {
                        ContactMetricRow(
                            listOf(
                                "Machines" to machineCount(location.id, machinesByLocationId, visibleTasks).toString(),
                                "Upcoming" to displayTasks.size.toString(),
                                "Visit" to visitLabel(displayDate)
                            )
                        )
                        Button(onClick = { onLocationSelected(location.id) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Open")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactLocationDetail(
    location: AppLocation,
    machines: List<PortalLocationMachine>,
    tasks: List<VendiTask>,
    allTasks: List<VendiTask>,
    onBack: () -> Unit,
    onOpenTasks: (LocalDate) -> Unit,
    onOpenServiceForm: (String) -> Unit
) {
    val palette = LocalVendistriPalette.current
    val visibleTasks = remember(tasks) { ContactVisibilityRules.visibleTasks(tasks) }
    var selectedMachineId by remember(location.id) { mutableStateOf<String?>(null) }
    var selectedRange by remember(location.id) { mutableStateOf(ContactCommissionRange.ThisWeek) }
    var customStart by remember(location.id) { mutableStateOf(LocalDate.now()) }
    var customEnd by remember(location.id) { mutableStateOf(LocalDate.now()) }
    val selectedBounds = if (selectedRange == ContactCommissionRange.Custom) {
        minOf(customStart, customEnd) to maxOf(customStart, customEnd)
    } else selectedRange.bounds()
    val rangedTasks = remember(visibleTasks, selectedRange, customStart, customEnd) {
        visibleTasks.filter { task ->
            TaskScheduleDate.parse(task.scheduledFor)?.let { date ->
                selectedBounds?.let { !date.isBefore(it.first) && !date.isAfter(it.second) } ?: true
            } ?: false
        }
    }
    val totals = TaskFinancialHelpers.sumTaskFinancials(rangedTasks)
    val boundsText = selectedBounds?.let { "${it.first.monthValue}/${it.first.dayOfMonth} - ${it.second.monthValue}/${it.second.dayOfMonth}, ${it.second.year}" }
        ?: selectedRange.label
    val displayTasks = LocationStopsBuilder.contactDisplayTasks(location.id, allTasks)
    val liveTarget = TaskLiveTargetResolver.target(visibleTasks, ContactVisibilityRules.visibleTasks(allTasks))
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ContactLocationHeader(amount = totals.commission, onBack = onBack)
        }
        item {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(location.name, color = palette.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(location.address?.singleLine.orEmpty(), color = palette.textSecondary)
                }
                liveTarget?.let {
                    Box(modifier = Modifier.clickable {
                        TaskScheduleDate.parse(it.navigateTask.scheduledFor)?.let(onOpenTasks)
                    }) { LiveTaskPill(it, ContactVisibilityRules.canSeeTaskMetrics(location)) }
                }
            }
        }
        item {
            ContactMetricRow(
                values = listOf(
                    "Machines" to machines.size.coerceAtLeast(visibleTasks.mapNotNull { it.machine }.distinct().size).toString(),
                    "Upcoming" to displayTasks.size.toString(),
                    "Visit" to visitLabel(LocationStopsBuilder.contactDisplayDate(location.id, allTasks))
                )
            )
        }
        if (ContactVisibilityRules.canSeeLocationPhoto(location)) {
            item {
                LocationPhoto(location)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Request Service", color = palette.textPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                SearchableDropdown(
                    allLabel = "Select machine",
                    options = machines.map {
                        SearchableDropdownOption(
                            id = it.id,
                            title = it.name.ifBlank { "Machine" },
                            subtitle = null,
                            searchText = it.name
                        )
                    },
                    selectedId = selectedMachineId,
                    onSelected = { selectedMachineId = it },
                    includesAllOption = false
                )
                Button(
                    onClick = { selectedMachineId?.let(onOpenServiceForm) },
                    enabled = selectedMachineId != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open service form")
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Commission", color = palette.textPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(boundsText, color = palette.textSecondary)
                    Text("$ ${money(totals.commission)}", color = palette.textPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                ContactCommissionRangePicker(selected = selectedRange, onSelected = { selectedRange = it }, modifier = Modifier.weight(1f))
            }
        }
        if (selectedRange == ContactCommissionRange.Custom) {
            item { CustomDateRangeFields(customStart, customEnd, { customStart = it }, { customEnd = it }) }
        }
        item {
            val fullFinancials = ContactVisibilityRules.financialDisplay(location) == TaskFinancialDisplayMode.Full
            if (fullFinancials) ContactMetricRow(listOf(
                "Gross" to "$${money(totals.gross)}",
                "Refunds" to "$${money(totals.refunds)}",
                "Net" to "$${money(totals.net)}"
            ))
        }
        if (ContactVisibilityRules.canSeeTaskMetrics(location)) {
            item {
                ContactMetricRow(
                    values = listOf(
                        "Time" to formatDuration(TaskGroupingHelpers.totalDurationMinutes(rangedTasks)),
                        "Distance" to "${oneDecimal(TaskGroupingHelpers.totalDistanceMiles(rangedTasks))} mi"
                    )
                )
            }
        }
    }
}

@Composable
private fun ContactLocationHeader(
    amount: Double,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(top = 10.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        BackButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart)
        )
        RevenueChip(amount = amount)
    }
}

@Composable
private fun LocationPhoto(location: AppLocation) {
    val assetUrl = location.assets
        .filter { it.type == null || it.type == "photo" }
        .sortedByDescending { it.createdAt.orEmpty() }
        .firstNotNullOfOrNull { it.url }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        shape = RoundedCornerShape(12.dp),
        color = LocalVendistriPalette.current.surfaceVariant
    ) {
        RemoteImagePreview(
            url = assetUrl,
            contentDescription = "${location.name} photo",
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No photo uploaded", color = LocalVendistriPalette.current.textSecondary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ContactMetricRow(values: List<Pair<String, String>>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        values.forEach { (label, value) ->
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, color = LocalVendistriPalette.current.textSecondary, style = MaterialTheme.typography.bodySmall)
                Text(value, color = LocalVendistriPalette.current.textPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun visitLabel(date: LocalDate?): String = when (date) {
    null -> "-"
    LocalDate.now() -> "Today"
    else -> date.format(DateTimeFormatter.ofPattern("MMM d"))
}

private fun machineCount(
    locationId: String,
    machinesByLocationId: Map<String, List<PortalLocationMachine>>,
    tasks: List<VendiTask>
): Int = machinesByLocationId[locationId]?.takeIf { it.isNotEmpty() }?.size
    ?: tasks.filter { it.location == locationId }.mapNotNull { it.machine }.distinct().size

@Composable
private fun CustomDateRangeFields(
    start: LocalDate,
    end: LocalDate,
    onStartChanged: (LocalDate) -> Unit,
    onEndChanged: (LocalDate) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        ContactDateButton(start, end, null, onStartChanged, Modifier.weight(1f))
        Text("to", color = LocalVendistriPalette.current.textSecondary)
        ContactDateButton(end, null, start, onEndChanged, Modifier.weight(1f))
    }
}

@Composable
private fun ContactDateButton(
    date: LocalDate,
    maximum: LocalDate?,
    minimum: LocalDate?,
    onChanged: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Surface(
        onClick = {
            android.app.DatePickerDialog(context, { _, year, month, day -> onChanged(LocalDate.of(year, month + 1, day)) }, date.year, date.monthValue - 1, date.dayOfMonth)
                .apply {
                    maximum?.let { datePicker.maxDate = it.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }
                    minimum?.let { datePicker.minDate = it.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }
                }.show()
        },
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = LocalVendistriPalette.current.surface
    ) {
        Text(date.format(DateTimeFormatter.ofPattern("MMM d, yyyy")), modifier = Modifier.padding(12.dp), fontWeight = FontWeight.SemiBold)
    }
}
