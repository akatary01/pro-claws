package com.vendistri.operations.features.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vendistri.operations.R
import com.vendistri.operations.components.BackButton
import com.vendistri.operations.components.CompactCalendarPicker
import com.vendistri.operations.components.RevenueChip
import com.vendistri.operations.design.AppColors
import com.vendistri.operations.features.location.AppLocation
import com.vendistri.operations.features.location_contact.ContactVisibilityRules
import com.vendistri.operations.features.tasks.actions.TaskActionKind
import com.vendistri.operations.features.tasks.overview.OverviewPanelView
import java.time.LocalDate
import java.time.YearMonth

enum class TasksHomePanelTab {
    Overview,
    Tasks,
    CompletedToday
}

@Composable
fun TasksPanelView(
    tab: TasksHomePanelTab,
    tasks: List<VendiTask>,
    locationsById: Map<String, AppLocation>,
    autoCalcCommission: Boolean,
    initialDate: LocalDate = LocalDate.now(),
    selectedLocationId: String?,
    pendingMutationTaskIds: Set<String>,
    isLoading: Boolean,
    isRefreshing: Boolean,
    errorMessage: String?,
    onBulkTaskAction: (TaskActionKind, List<VendiTask>) -> Unit,
    onApplySharedNotes: suspend (List<String>, String?) -> Boolean,
    taskActions: TaskCardActions,
    onDateVisible: (LocalDate) -> Unit,
    onOverviewDateVisible: (LocalDate) -> Unit,
    isReadOnly: Boolean = false,
    useContactVisibility: Boolean = false,
    onClose: () -> Unit
) {
    val today = LocalDate.now()
    var selectedDate by remember { mutableStateOf(initialDate) }
    var calendarMonth by remember { mutableStateOf(YearMonth.from(initialDate)) }
    var showsCalendar by remember { mutableStateOf(false) }
    val effectiveDate = if (tab == TasksHomePanelTab.CompletedToday) today else selectedDate
    val liveLockedTaskIds = remember(tasks) {
        TaskLiveTargetResolver.effectiveLiveTaskIds(scopedTasks = tasks, allTasks = tasks)
    }
    val panelTaskActions = remember(taskActions, liveLockedTaskIds) {
        taskActions.locking(liveLockedTaskIds)
    }
    var panelLocationId by remember(tab) { mutableStateOf(selectedLocationId) }
    LaunchedEffect(tab, initialDate) {
        if (tab != TasksHomePanelTab.CompletedToday) {
            selectedDate = initialDate
            calendarMonth = YearMonth.from(initialDate)
        }
    }
    LaunchedEffect(selectedLocationId) {
        panelLocationId = selectedLocationId
    }
    LaunchedEffect(tab, effectiveDate) {
        if (tab == TasksHomePanelTab.Overview) return@LaunchedEffect
        onDateVisible(effectiveDate)
    }
    if (tab == TasksHomePanelTab.Overview) {
        OverviewPanelView(
            tasks = tasks,
            locationsById = locationsById,
            selectedLocationId = selectedLocationId,
            autoCalcCommission = autoCalcCommission,
            initialDate = initialDate,
            isLoading = isLoading,
            isRefreshing = isRefreshing,
            errorMessage = errorMessage,
            onDateVisible = onOverviewDateVisible,
            onApplySharedNotes = onApplySharedNotes,
            useContactVisibility = useContactVisibility,
            onClose = onClose
        )
        return
    }

    val datedTasks = when (tab) {
        TasksHomePanelTab.Tasks -> TaskPanelVisibility.actionableTasksForDate(tasks, effectiveDate)
        TasksHomePanelTab.CompletedToday -> TaskPanelVisibility.finalTasksForDate(tasks, effectiveDate)
        TasksHomePanelTab.Overview -> emptyList()
    }.let { panelTasks ->
        if (useContactVisibility) ContactVisibilityRules.visibleTasks(panelTasks) else panelTasks
    }
    val scopedTasks = panelLocationId?.let { locationId ->
        datedTasks.filter { it.location == locationId }
    } ?: datedTasks
    val locationFilterGroups = TaskGroupingHelpers.groupByLocation(datedTasks, lookupTasks = tasks)
    val groups = TaskGroupingHelpers.groupByLocation(scopedTasks, lookupTasks = tasks)
    val summary = TaskSummary.fromTasks(scopedTasks)
    val expandedLocationIds = remember(tab, selectedLocationId) { mutableStateMapOf<String, Boolean>() }
    val isPanelLoading = isLoading || isRefreshing

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TasksPanelHeader(
                tab = tab,
                summary = summary,
                tasks = scopedTasks,
                selectedDate = effectiveDate,
                onClose = onClose,
                onCalendarClick = if (tab == TasksHomePanelTab.CompletedToday) {
                    null
                } else {
                    { showsCalendar = !showsCalendar }
                }
            )

            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            if (isPanelLoading && groups.isEmpty()) {
                TasksPanelSkeletonView(tab = tab)
                return@Column
            }

            when (tab) {
                TasksHomePanelTab.Overview -> Unit
                TasksHomePanelTab.Tasks -> {
                    LocationFilterMenu(
                        locations = locationFilterGroups,
                        selectedLocationId = panelLocationId,
                        onLocationSelected = { panelLocationId = it }
                    )
                    if (groups.isEmpty() && !isLoading) EmptyPanelText("No pending work for this day")
                    groups.forEachIndexed { index, group ->
                        TaskLocationCard(
                            locationGroup = group,
                            isExpanded = expandedLocationIds[group.id] ?: false,
                            showCompletedMetrics = false,
                            onToggle = { expandedLocationIds[group.id] = !(expandedLocationIds[group.id] ?: false) },
                            onBulkTaskAction = onBulkTaskAction,
                            onApplySharedNotes = onApplySharedNotes,
                            pendingMutationTaskIds = pendingMutationTaskIds,
                            taskActions = panelTaskActions,
                            liveTaskTarget = liveTaskTarget(
                                locationId = group.id,
                                scopedTasks = scopedTasks,
                                allTasks = tasks
                            ),
                            allowBulkActions = !isReadOnly,
                            showTaskMetrics = !useContactVisibility || ContactVisibilityRules.canSeeTaskMetrics(locationsById[group.id]),
                            financialDisplay = if (useContactVisibility) {
                                ContactVisibilityRules.financialDisplay(locationsById[group.id])
                            } else {
                                TaskFinancialDisplayMode.Full
                            },
                            appLocation = locationsById[group.id],
                            autoCalcCommission = autoCalcCommission
                        )
                    }
                }
                TasksHomePanelTab.CompletedToday -> {
                    if (groups.isEmpty() && !isLoading) EmptyPanelText("No completed work for this day")
                    groups.forEachIndexed { index, group ->
                        TaskLocationCard(
                            locationGroup = group,
                            isExpanded = expandedLocationIds[group.id] ?: false,
                            showCompletedMetrics = true,
                            onToggle = { expandedLocationIds[group.id] = !(expandedLocationIds[group.id] ?: false) },
                            onBulkTaskAction = onBulkTaskAction,
                            onApplySharedNotes = onApplySharedNotes,
                            pendingMutationTaskIds = pendingMutationTaskIds,
                            taskActions = panelTaskActions,
                            allowBulkActions = !isReadOnly,
                            showTaskMetrics = !useContactVisibility || ContactVisibilityRules.canSeeTaskMetrics(locationsById[group.id]),
                            financialDisplay = if (useContactVisibility) {
                                ContactVisibilityRules.financialDisplay(locationsById[group.id])
                            } else {
                                TaskFinancialDisplayMode.Full
                            },
                            appLocation = locationsById[group.id],
                            autoCalcCommission = autoCalcCommission
                        )
                    }
                }
            }
        }

        if (showsCalendar && tab != TasksHomePanelTab.CompletedToday) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { showsCalendar = false }
            )
            CompactCalendarPicker(
                selectedDate = selectedDate,
                visibleMonth = calendarMonth,
                onDateSelected = {
                    selectedDate = it
                    calendarMonth = YearMonth.from(it)
                    showsCalendar = false
                },
                onVisibleMonthChanged = { calendarMonth = it },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 20.dp)
                    .offset(y = 54.dp)
            )
        }
    }
}

private fun liveTaskTarget(
    locationId: String,
    scopedTasks: List<VendiTask>,
    allTasks: List<VendiTask>
): LiveTaskTarget? {
    return TaskLiveTargetResolver.target(
        scopedTasks = scopedTasks.filter { it.location == locationId },
        allTasks = allTasks
    )
}

@Composable
private fun EmptyPanelText(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 42.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}
