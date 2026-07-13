package com.vendistri.operations.features.main

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.vendistri.operations.R
import com.vendistri.operations.components.RevenueChip
import com.vendistri.operations.components.RevenueChipTextSize
import com.vendistri.operations.components.VendistriIconButton
import com.vendistri.operations.design.AppColors
import com.vendistri.operations.design.LocalVendistriPalette
import com.vendistri.operations.design.LocalVendistriResponsiveLayout
import com.vendistri.operations.features.tasks.add_stop.AddStopPanelView
import com.vendistri.operations.features.tasks.add_stop.AddStopUiState
import com.vendistri.operations.features.location.LocationUiState
import com.vendistri.operations.features.location.LocationsPanelView
import com.vendistri.operations.features.location_contact.AppModeUiState
import com.vendistri.operations.features.location_contact.AppViewMode
import com.vendistri.operations.features.location_contact.ContactLocationDetailView
import com.vendistri.operations.features.location_contact.ContactUiState
import com.vendistri.operations.features.map.LocationCoordinate
import com.vendistri.operations.features.map.LocationStop
import com.vendistri.operations.features.map.LocationStopSummaryCard
import com.vendistri.operations.features.map.MapHomeView
import com.vendistri.operations.features.map.MapNavigationProgress
import com.vendistri.operations.features.map.MapNavigationRoute
import com.vendistri.operations.features.map.bestInitialMapLocationCoordinate
import com.vendistri.operations.features.navigation.NavigationChromeView
import com.vendistri.operations.features.navigation.NavigationCurrentSpeedDisplay
import com.vendistri.operations.features.navigation.NavigationSessionState
import com.vendistri.operations.features.notifications.NotificationsState
import com.vendistri.operations.features.notifications.AppNotificationItem
import com.vendistri.operations.features.notifications.NotificationsPanelView
import com.vendistri.operations.features.settings.AppAppearancePreference
import com.vendistri.operations.features.settings.AppSettingsState
import com.vendistri.operations.features.settings.NavigationAudioPreference
import com.vendistri.operations.features.settings.SettingsPanelView
import com.vendistri.operations.features.settings.TimeFormatPreference
import com.vendistri.operations.features.tasks.CollectionFinancialDraft
import com.vendistri.operations.features.tasks.RefillInventorySourceMode
import com.vendistri.operations.features.tasks.TaskCardActions
import com.vendistri.operations.features.tasks.TaskDateFormatters
import com.vendistri.operations.features.tasks.TaskFinancialHelpers
import com.vendistri.operations.features.tasks.TaskStateHelpers
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.TasksUiState
import com.vendistri.operations.features.tasks.TasksPanelView
import com.vendistri.operations.features.tasks.TasksHomePanelTab
import com.vendistri.operations.features.tasks.VendiTask
import com.vendistri.operations.features.tasks.money
import com.vendistri.operations.features.tasks.panelWorkDate
import com.vendistri.operations.features.tasks.TaskBulkSelectionRules
import com.vendistri.operations.features.tasks.actions.TaskActionKind
import com.vendistri.operations.features.tasks.actions.TaskActionSheet
import com.vendistri.operations.features.tasks.actions.TaskActionState
import com.vendistri.operations.features.work.GoSummaryPanel
import com.vendistri.operations.features.work.RefillDecisionAction
import com.vendistri.operations.features.work.RefillDecisionView
import com.vendistri.operations.features.work.RefillDecisionUiState
import com.vendistri.operations.features.work.RoutePreviewEstimator
import com.vendistri.operations.features.work.RouteStartDecisionView
import com.vendistri.operations.features.work.RouteStartScopeChoice
import com.vendistri.operations.features.work.AtLocationExecutionView
import com.vendistri.operations.features.work.AtWarehouseExecutionView
import com.vendistri.operations.features.work.WorkPanelView
import com.vendistri.operations.features.work.WorkDestinationKind
import com.vendistri.operations.features.work.WorkPhase
import com.vendistri.operations.features.work.WorkUiState
import com.vendistri.operations.features.work.isAtDestination
import com.vendistri.operations.features.work.isNavigating
import com.vendistri.operations.features.refill.RefillInventoryUiState
import com.vendistri.operations.features.pickup.PickupInventoryUiState
import java.time.LocalDate

private const val MinimumTaskSheetScreenFraction = 0.58f
private const val SideMenuScrimZ = 130f
private const val SideMenuDrawerZ = 131f
private const val EdgeSwipeOpenWidthDp = 24
private const val EdgeSwipeOpenThresholdPx = 72f
private const val EdgeSwipeVerticalTolerancePx = 36f
private const val ArrivalGpsDistanceThresholdMiles = 100.0 / 5280.0
private const val ArrivalHoldDistanceThresholdMiles = 150.0 / 5280.0
private const val ArrivalRouteCompleteDistanceThresholdMiles = 15.0 / 5280.0
private const val ArrivalRouteCompleteDurationThresholdSeconds = 45.0
private const val ArrivalMaxSpeedMilesPerHour = 5.0

private fun taskSheetHeight(maxHeight: Dp, topClearance: Dp): Dp {
    return (maxHeight - topClearance).coerceAtLeast(maxHeight * MinimumTaskSheetScreenFraction)
}

private fun navigationArrivalCandidate(
    previousCandidate: Boolean,
    userCoordinate: LocationCoordinate?,
    destinationCoordinate: LocationCoordinate?,
    distanceRemainingMiles: Double?,
    durationRemainingSeconds: Double?,
    currentSpeed: NavigationCurrentSpeedDisplay?
): Boolean {
    val currentSpeedMilesPerHour = currentSpeed?.value?.let { speed ->
        if (currentSpeed.unitText.equals("KM/H", ignoreCase = true)) speed / 1.609344 else speed
    }
    if (currentSpeedMilesPerHour != null &&
        currentSpeedMilesPerHour.isFinite() &&
        currentSpeedMilesPerHour > ArrivalMaxSpeedMilesPerHour
    ) {
        return false
    }

    val straightLineDistanceMiles = if (userCoordinate != null && destinationCoordinate != null) {
        RoutePreviewEstimator.distanceMiles(userCoordinate, destinationCoordinate)
    } else {
        null
    }
    val gpsThreshold = if (previousCandidate) ArrivalHoldDistanceThresholdMiles else ArrivalGpsDistanceThresholdMiles
    if (straightLineDistanceMiles != null && straightLineDistanceMiles <= gpsThreshold) {
        return true
    }

    return straightLineDistanceMiles == null &&
        (distanceRemainingMiles ?: Double.MAX_VALUE) <= ArrivalRouteCompleteDistanceThresholdMiles &&
        (durationRemainingSeconds ?: Double.MAX_VALUE) <= ArrivalRouteCompleteDurationThresholdSeconds
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainView(
    uiState: MainUiState,
    locationStops: List<LocationStop>,
    visibleTasks: List<VendiTask>,
    tasksUiState: TasksUiState,
    locationsUiState: LocationUiState,
    contactState: ContactUiState,
    appModeState: AppModeUiState,
    workState: WorkUiState,
    navigationState: NavigationSessionState,
    refillDecisionState: RefillDecisionUiState,
    refillInventoryState: RefillInventoryUiState,
    pickupInventoryState: PickupInventoryUiState,
    addStopState: AddStopUiState,
    notificationsState: NotificationsState,
    settingsState: AppSettingsState,
    isMapReady: Boolean,
    currentUserId: String?,
    userDisplayName: String,
    onRefresh: () -> Unit,
    onTabSelected: (MainTab) -> Unit,
    onBulkTaskAction: (TaskActionKind, List<VendiTask>) -> Unit,
    onApplySharedNotes: suspend (List<String>, String?) -> Boolean,
    taskActions: TaskCardActions,
    activeTaskActions: TaskCardActions,
    onPrepareAddStop: () -> Unit,
    onResetAddStop: () -> Unit,
    onAddStopDateChanged: (LocalDate) -> Unit,
    onAddStopNotesChanged: (String) -> Unit,
    onAddStopLocationToggle: (String) -> Unit,
    onAddStopMachineToggle: (String) -> Unit,
    onAddStopTaskTypeToggle: (String, TaskType) -> Unit,
    onAddStopAssigneeSelected: (String, String?) -> Unit,
    onSaveAddStop: (() -> Unit) -> Unit,
    onConfirmAddStopPrecheck: (() -> Unit) -> Unit,
    onConfirmAddStopRescheduleExisting: (() -> Unit) -> Unit,
    onDismissAddStopPrecheckAlert: () -> Unit,
    onMapStopSelected: (String?) -> Unit,
    onClearMapStop: () -> Unit,
    onMapUserLocationChanged: (LocationCoordinate) -> Unit,
    onNavigationProgress: (MapNavigationProgress) -> Unit,
    onPresentGoSummary: () -> Unit,
    onGoStopSelected: (String) -> Unit,
    onDismissGoSummary: () -> Unit,
    onStartSelectedGoRoute: () -> Unit,
    onGoScopeChoiceSelected: (RouteStartScopeChoice) -> Unit,
    onGoScopeChoiceChanged: (RouteStartScopeChoice) -> Unit,
    onGoLaterRefillTaskToggle: (String) -> Unit,
    onDismissGoScopeChoice: () -> Unit,
    onDismissRefillDecision: () -> Unit,
    onStartWorkNavigation: () -> Unit,
    isDebugRouteSimulationEnabled: Boolean,
    onSimulateCurrentWorkRouteForDebug: () -> Unit,
    onRefillDecisionActionSelected: (RefillDecisionAction) -> Unit,
    onRefillDecisionWarehouseSelected: (String) -> Unit,
    onRefillDecisionTaskToggle: (String) -> Unit,
    onApplyRefillDecision: () -> Unit,
    onArriveAtWorkLocation: () -> Unit,
    onPrepareCurrentInventoryTask: () -> Unit,
    onMarkCurrentWorkTaskDone: () -> Unit,
    onCompleteCurrentInventoryTask: () -> Unit,
    onAdvanceCurrentWorkTask: () -> Unit,
    onRefillQuantityChanged: (String, String) -> Unit,
    onRefillFinalStockChanged: (String, String) -> Unit,
    onRefillSourceSelected: (RefillInventorySourceMode, String?) -> Unit,
    onPickupQuantityChanged: (String, String) -> Unit,
    onFinishCurrentWorkVisit: () -> Unit,
    onCancelCurrentWorkTasks: (List<VendiTask>) -> Unit,
    onAddTaskPhoto: (VendiTask) -> Unit,
    onRemoveTaskPhoto: (VendiTask) -> Unit,
    onStopCurrentWorkSession: () -> Unit,
    onAutoCalcCommissionChanged: (Boolean) -> Unit,
    onAutoFillRefillFinalStockChanged: (Boolean) -> Unit,
    onAppearancePreferenceChanged: (AppAppearancePreference) -> Unit,
    onNavigationAudioPreferenceChanged: (NavigationAudioPreference) -> Unit,
    onCurrentNavigationAudioPreferenceChanged: (NavigationAudioPreference) -> Unit,
    onTimeFormatPreferenceChanged: (TimeFormatPreference) -> Unit,
    onViewModeSelected: (AppViewMode) -> Unit,
    onLoadContactLocationMachines: (String) -> Unit,
    onMarkAllNotificationsRead: () -> Unit,
    onNotificationSelected: (AppNotificationItem) -> Unit,
    onSummaryDateSelected: (LocalDate) -> Unit,
    onTasksPanelDateVisible: (LocalDate) -> Unit,
    onMapReadyChanged: (Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val palette = LocalVendistriPalette.current
    val responsiveLayout = LocalVendistriResponsiveLayout.current
    var isBottomPanelOpen by remember { mutableStateOf(false) }
    var isMenuOpen by remember { mutableStateOf(false) }
    var isSummaryExpanded by remember { mutableStateOf(false) }
    var selectedSummaryDate by remember { mutableStateOf(LocalDate.now()) }
    var overviewInitialDate by remember { mutableStateOf(LocalDate.now()) }
    var homePanelTab by remember { mutableStateOf(TasksHomePanelTab.Tasks) }
    var recenterRequest by remember { mutableStateOf(0) }
    var isAddStopOpen by remember { mutableStateOf(false) }
    var refillSheetDragY by remember { mutableStateOf(0f) }
    var routeStartSheetDragY by remember { mutableStateOf(0f) }
    var isRouteStartSheetExpanded by remember { mutableStateOf(false) }
    var currentMapCoordinate by remember { mutableStateOf(context.bestInitialMapLocationCoordinate()) }
    var isRouteOverviewMode by remember { mutableStateOf(false) }
    var isNavigationCameraDetached by remember { mutableStateOf(false) }
    var debugRouteSimulationRequest by remember { mutableStateOf(0) }
    var isNavigationCancelSheetPresented by remember { mutableStateOf(false) }
    var navigationCancelSelectionTaskIds by remember { mutableStateOf(emptySet<String>()) }
    var selectedContactLocationId by remember(appModeState.mode) { mutableStateOf<String?>(null) }
    val isContactMode = appModeState.isContactMode
    val activeLocationsById = if (isContactMode) contactState.locationsById else locationsUiState.locationsById
    LaunchedEffect(uiState.canManageScheduledTasks) {
        if (!uiState.canManageScheduledTasks) {
            isAddStopOpen = false
        }
    }
    val selectedStop = locationStops.firstOrNull { it.id == uiState.selectedMapStopId }
    val showsGoSummaryPanel = workState.phase == WorkPhase.Summary
    val showsRouteStartDecisionPanel = workState.routeStartScopeDecision != null
    val showsRefillDecisionPanel = workState.phase == WorkPhase.PreparingRoute && refillDecisionState.isVisible
    val showsWorkSheet = (workState.phase.isAtDestination || workState.phase == WorkPhase.Completing) &&
        !showsGoSummaryPanel &&
        !showsRefillDecisionPanel
    val showsPanel = isBottomPanelOpen || showsWorkSheet
    val showsOverviewSheet = uiState.selectedTab == MainTab.Tasks && homePanelTab == TasksHomePanelTab.Overview
    val daySummaries = remember(visibleTasks) { TaskDaySummary.fromTasks(visibleTasks) }
    val selectedSummary = daySummaries.firstOrNull { it.date == selectedSummaryDate }
        ?: TaskDaySummary.empty(selectedSummaryDate)
    val activeNavigationRoute = remember(workState, currentMapCoordinate) {
        val origin = currentMapCoordinate
        val destination = workState.selectedStop?.coordinate ?: workState.activeSession?.coordinate
        if (workState.phase.isNavigating && origin != null && destination != null) {
            MapNavigationRoute(
                id = "navigation:${workState.selectedStop?.id ?: workState.activeSession?.id}:${workState.phase.name}",
                origin = origin,
                destination = destination,
                destinationTitle = workState.selectedStop?.title
                    ?: workState.activeSession?.title
                    ?: "Destination"
            )
        } else {
            null
        }
    }
    var isActiveNavigationMapReady by remember(activeNavigationRoute?.id) {
        mutableStateOf(activeNavigationRoute == null)
    }
    val isNavigationStartResolving =
        workState.routeStartScopeDecision == null &&
            (
                workState.isLoading ||
                    (refillDecisionState.isApplying && refillDecisionState.canApply)
            )
    val isNavigationStarting = (workState.phase.isNavigating && !isActiveNavigationMapReady) ||
        isNavigationStartResolving
    val navigationStartupDestinationTitle = when {
        refillDecisionState.isApplying && refillDecisionState.selectedAction == RefillDecisionAction.RouteToWarehouse ->
            refillDecisionState.selectedWarehouse?.name
        else -> null
    } ?: workState.selectedStop?.title
        ?: workState.activeSession?.title
        ?: "destination"
    fun currentNavigationRemainingTasks(): List<VendiTask> {
        val sourceTasks = workState.activeExecution?.displayTasks
            ?: workState.selectedStop?.tasks
            ?: emptyList()
        return sourceTasks
            .map { task -> tasksUiState.tasksById[task.id] ?: task }
            .filter { task -> !TaskStateHelpers.isFinal(task.status) }
            .distinctBy { task -> task.id }
    }
    val navigationCancelTasks = currentNavigationRemainingTasks()
    val navigationCancelSheetState = TaskActionState(
        activeAction = if (isNavigationCancelSheetPresented) TaskActionKind.Cancel else null,
        tasks = navigationCancelTasks,
        selectedTaskIds = navigationCancelSelectionTaskIds
    )
    var isNavigationArrivalCandidate by remember(workState.activeSession?.id, workState.selectedStop?.id) {
        mutableStateOf(false)
    }
    val canConfirmNavigationArrival =
        (navigationState.distanceRemainingMiles ?: Double.MAX_VALUE) <= ArrivalRouteCompleteDistanceThresholdMiles
    val showsNavigationArrivalControls = isNavigationArrivalCandidate || canConfirmNavigationArrival
    LaunchedEffect(
        workState.phase,
        workState.activeSession?.id,
        workState.selectedStop?.coordinate,
        workState.activeSession?.coordinate,
        currentMapCoordinate,
        navigationState.distanceRemainingMiles,
        navigationState.durationRemainingSeconds,
        navigationState.currentSpeed
    ) {
        if (!workState.phase.isNavigating) {
            isNavigationArrivalCandidate = false
            return@LaunchedEffect
        }
        isNavigationArrivalCandidate = navigationArrivalCandidate(
            previousCandidate = isNavigationArrivalCandidate,
            userCoordinate = currentMapCoordinate,
            destinationCoordinate = workState.selectedStop?.coordinate ?: workState.activeSession?.coordinate,
            distanceRemainingMiles = navigationState.distanceRemainingMiles,
            durationRemainingSeconds = navigationState.durationRemainingSeconds,
            currentSpeed = navigationState.currentSpeed
        )
    }
    LaunchedEffect(
        workState.phase,
        workState.activeSession?.id,
        tasksUiState.hasLoadedOnce,
        tasksUiState.tasks
    ) {
        val activeTaskIds = workState.activeSession?.activeTaskIds.orEmpty()
        if (!workState.phase.isNavigating || activeTaskIds.isEmpty() || !tasksUiState.hasLoadedOnce) {
            return@LaunchedEffect
        }
        val hasRemainingRouteTasks = tasksUiState.tasks.any { task ->
            task.id in activeTaskIds && !TaskStateHelpers.isFinal(task.status)
        }
        if (!hasRemainingRouteTasks) {
            onStopCurrentWorkSession()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
    ) {
        val goSummaryPanelMaxHeight = maxHeight * 0.74f
        val refillDecisionPanelMaxHeight = maxHeight * 0.88f
        val routeStartDecisionCollapsedHeight = if ((workState.routeStartScopeDecision?.laterRefillTasks?.size ?: 0) > 2 ||
            workState.routeStartScopeDecision?.requiresChoice == true
        ) {
            maxHeight * 0.58f
        } else {
            maxHeight * 0.48f
        }
        val routeStartDecisionPanelHeight = if (isRouteStartSheetExpanded) {
            maxHeight * 0.88f
        } else {
            routeStartDecisionCollapsedHeight
        }
        val layoutMaxHeight = maxHeight

        LaunchedEffect(workState.routeStartScopeDecision?.stopTitle) {
            isRouteStartSheetExpanded = false
            routeStartSheetDragY = 0f
        }
        LaunchedEffect(workState.phase, workState.selectedStop?.id) {
            if (!workState.phase.isNavigating) {
                isRouteOverviewMode = false
                isNavigationCameraDetached = false
            }
        }

        MapHomeView(
            stops = locationStops,
            activeRoute = null,
            activeNavigationRoute = activeNavigationRoute,
            recenterRequest = recenterRequest,
            onStopSelected = onMapStopSelected,
            onMapClicked = {
                if (selectedStop != null) onClearMapStop()
            },
            onUserLocationChanged = { coordinate ->
                currentMapCoordinate = coordinate
                onMapUserLocationChanged(coordinate)
            },
            onNavigationProgress = onNavigationProgress,
            onActiveNavigationMapReadyChanged = { ready ->
                isActiveNavigationMapReady = ready
            },
            onNavigationCameraInteraction = {
                isNavigationCameraDetached = true
                isRouteOverviewMode = false
            },
            isRouteOverviewMode = isRouteOverviewMode,
            isNavigationCameraDetached = isNavigationCameraDetached,
            isNavigationArrivalCandidate = showsNavigationArrivalControls,
            followNavigationCameraRequest = recenterRequest,
            debugRouteSimulationRequest = debugRouteSimulationRequest,
            onMapReadyChanged = onMapReadyChanged,
            modifier = Modifier.matchParentSize()
        )

        // Bottom surfaces draw edge-to-edge and apply system-navigation insets
        // to their content individually. A global inset would lift the entire
        // surface and expose the map on gesture and three-button devices.
        Box(
            modifier = Modifier
                .matchParentSize()
        ) {

        if (!isMenuOpen && !isNavigationStarting) {
            EdgeSwipeOpenLayer(onOpen = { isMenuOpen = true })
        }

        if (isNavigationStarting) {
            NavigationStartupOverlay(
                destinationTitle = navigationStartupDestinationTitle,
                modifier = Modifier
                    .matchParentSize()
                    .zIndex(30f)
            )
        } else if (isMapReady && workState.phase.isNavigating && isActiveNavigationMapReady) {
            NavigationChromeView(
                workState = workState,
                navigationState = navigationState,
                audioPreference = navigationState.audioPreference,
                timeFormatPreference = settingsState.timeFormatPreference,
                systemUses24Hour = DateFormat.is24HourFormat(context),
                allTasks = tasksUiState.tasks,
                locationsById = activeLocationsById,
                currentInstructionText = navigationState.currentInstructionText,
                isDebugRouteSimulationEnabled = isDebugRouteSimulationEnabled,
                isArrivalCandidate = showsNavigationArrivalControls,
                onApplySharedNotes = onApplySharedNotes,
                onAudioPreferenceChanged = onCurrentNavigationAudioPreferenceChanged,
                onSimulateRouteForDebug = {
                    debugRouteSimulationRequest += 1
                    onSimulateCurrentWorkRouteForDebug()
                },
                onRecenter = {
                    isSummaryExpanded = false
                    isRouteOverviewMode = false
                    isNavigationCameraDetached = false
                    recenterRequest += 1
                },
                isRouteOverviewMode = isRouteOverviewMode || isNavigationCameraDetached,
                onToggleRouteOverview = {
                    isRouteOverviewMode = !isRouteOverviewMode
                    isNavigationCameraDetached = false
                },
                onArrived = onArriveAtWorkLocation,
                onCancelNavigation = {
                    val remainingTasks = currentNavigationRemainingTasks()
                    if (remainingTasks.isNotEmpty()) {
                        navigationCancelSelectionTaskIds = TaskBulkSelectionRules.normalizedSelection(
                            allTasks = remainingTasks,
                            selectedTaskIds = remainingTasks.map { task -> task.id }.toSet()
                        )
                        isNavigationCancelSheetPresented = true
                    } else {
                        onStopCurrentWorkSession()
                    }
                },
                modifier = Modifier
                    .matchParentSize()
                    .zIndex(18f)
            )
        } else if (isMapReady && !isNavigationStarting) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .zIndex(14f)
            ) {
                if (!isSummaryExpanded) {
                    MapIconButton(
                        iconRes = R.drawable.ic_menu_lines,
                        contentDescription = "Menu",
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        isMenuOpen = true
                    }
                    Column(
                        modifier = Modifier.align(Alignment.TopEnd),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MapIconButton(
                                iconRes = R.drawable.ic_bell,
                                contentDescription = "Notifications",
                                badgeCount = notificationsState.unreadCount,
                                onClick = {
                                    onTabSelected(MainTab.Notifications)
                                    isBottomPanelOpen = true
                                }
                            )
                            MapIconButton(iconRes = R.drawable.ic_location_north, contentDescription = "Recenter") {
                                isSummaryExpanded = false
                                recenterRequest += 1
                            }
                        }
                        if (uiState.canManageScheduledTasks && !isContactMode) {
                            MapIconButton(iconRes = R.drawable.ic_plus, contentDescription = "Add stop") {
                                onResetAddStop()
                                isAddStopOpen = true
                            }
                        }
                    }
                }
                    SummaryChip(
                        amount = if (isContactMode) {
                            daySummaries.first().commission
                        } else if (isSummaryExpanded) {
                            selectedSummary.grossRevenue
                        } else {
                            daySummaries.first().grossRevenue
                        },
                        onClick = {
                        if (isContactMode) return@SummaryChip
                        if (showsPanel) {
                            isBottomPanelOpen = false
                            isSummaryExpanded = false
                        } else {
                            isSummaryExpanded = !isSummaryExpanded
                        }
                    },
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }

            if (isSummaryExpanded && !showsPanel) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            isSummaryExpanded = false
                        }
                )
                SummaryExpandedPanel(
                    summary = selectedSummary,
                    onWeeklySummary = {
                        isSummaryExpanded = false
                        overviewInitialDate = selectedSummary.date
                        homePanelTab = TasksHomePanelTab.Overview
                        onTabSelected(MainTab.Tasks)
                        isBottomPanelOpen = true
                    },
                    onPreviousDay = {
                        val nextDate = selectedSummary.date.minusDays(1)
                        selectedSummaryDate = nextDate
                        onSummaryDateSelected(nextDate)
                    },
                    onNextDay = {
                        val nextDate = selectedSummary.date.plusDays(1)
                        if (!nextDate.isAfter(LocalDate.now())) {
                            selectedSummaryDate = nextDate
                            onSummaryDateSelected(nextDate)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 2.dp, start = 16.dp, end = 16.dp)
                        .zIndex(1f)
                )
            }

            if (!showsPanel) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    if (!isContactMode) {
                        MapGoButton(
                            isPaused = workState.phase == WorkPhase.Summary,
                            onClick = onPresentGoSummary
                        )
                    }
                    MapBottomBar(
                        centerTitle = if (isContactMode) "Locations" else "Tasks",
                        onLeft = {
                            if (isContactMode) {
                                overviewInitialDate = LocalDate.now()
                                homePanelTab = TasksHomePanelTab.Overview
                                onTabSelected(MainTab.Tasks)
                            } else {
                                overviewInitialDate = LocalDate.now()
                                homePanelTab = TasksHomePanelTab.Overview
                                onTabSelected(MainTab.Tasks)
                            }
                            isBottomPanelOpen = true
                        },
                        onCenter = {
                            if (isContactMode) {
                                selectedContactLocationId = null
                                onTabSelected(MainTab.Locations)
                            } else {
                                homePanelTab = TasksHomePanelTab.Tasks
                                onTabSelected(MainTab.Tasks)
                            }
                            isBottomPanelOpen = true
                        },
                        onRight = {
                            homePanelTab = if (isContactMode) TasksHomePanelTab.Tasks else TasksHomePanelTab.CompletedToday
                            onTabSelected(MainTab.Tasks)
                            isBottomPanelOpen = true
                        },
                        onSwipeUp = {
                            if (isContactMode) {
                                selectedContactLocationId = null
                                onTabSelected(MainTab.Locations)
                            } else {
                                homePanelTab = TasksHomePanelTab.Tasks
                                onTabSelected(MainTab.Tasks)
                            }
                            isBottomPanelOpen = true
                        }
                    )
                }
            }

            selectedStop?.let { stop ->
                LocationStopSummaryCard(
                    stop = stop,
                    appLocation = activeLocationsById[stop.id],
                    routePreview = RoutePreviewEstimator.previewRoute(currentMapCoordinate, stop.coordinate),
                    timeFormatPreference = settingsState.timeFormatPreference,
                    onOpenTasks = {
                        homePanelTab = if (stop.hasPending || stop.unassignedCount > 0) {
                            TasksHomePanelTab.Tasks
                        } else {
                            TasksHomePanelTab.Overview
                        }
                        onTabSelected(MainTab.Tasks)
                        isBottomPanelOpen = true
                    },
                    onOpenLocation = if (isContactMode) {
                        {
                            selectedContactLocationId = stop.id
                            onTabSelected(MainTab.Locations)
                            isBottomPanelOpen = true
                        }
                    } else {
                        null
                    },
                    onGo = if (isContactMode) ({}) else onPresentGoSummary,
                    onClose = onClearMapStop,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(12f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 82.dp)
                )
            }

            if (showsGoSummaryPanel) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .zIndex(18f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onDismissGoSummary()
                        }
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {}
                    ) {
                        GoSummaryPanel(
                            state = workState,
                            currentUserId = currentUserId,
                            locationsById = activeLocationsById,
                            timeFormatPreference = settingsState.timeFormatPreference,
                            onStopSelected = onGoStopSelected,
                            onStart = onStartSelectedGoRoute,
                            onScopeChoiceSelected = onGoScopeChoiceSelected,
                            onDismissScopeChoice = onDismissGoScopeChoice,
                            onClose = onDismissGoSummary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = goSummaryPanelMaxHeight)
                        )
                    }
                }
            }

            if (showsRouteStartDecisionPanel) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .zIndex(18.5f)
                        .background(Color.Black.copy(alpha = 0.20f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (!workState.isLoading) {
                                onDismissGoScopeChoice()
                            }
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(routeStartDecisionPanelHeight)
                            .imePadding()
                            .pointerInput(workState.isLoading) {
                                detectDragGestures(
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        routeStartSheetDragY += dragAmount.y
                                    },
                                    onDragEnd = {
                                        if (!workState.isLoading) {
                                            when {
                                                routeStartSheetDragY < -70f -> {
                                                    isRouteStartSheetExpanded = true
                                                }
                                                routeStartSheetDragY > 90f && isRouteStartSheetExpanded -> {
                                                    isRouteStartSheetExpanded = false
                                                }
                                                routeStartSheetDragY > 90f -> {
                                                    onDismissGoScopeChoice()
                                                }
                                            }
                                        }
                                        routeStartSheetDragY = 0f
                                    },
                                    onDragCancel = {
                                        routeStartSheetDragY = 0f
                                    }
                                )
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {},
                        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                        tonalElevation = 2.dp,
                        shadowElevation = 8.dp,
                        color = palette.mapPanelSurface,
                        contentColor = palette.mapPanelForeground
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp)
                        ) {
                            RouteStartDecisionView(
                                state = workState,
                                onChoiceSelected = onGoScopeChoiceChanged,
                                onLaterRefillTaskToggle = onGoLaterRefillTaskToggle,
                                onContinue = {
                                    val choice = workState.selectedRouteStartScopeChoice
                                        ?: workState.routeStartScopeDecision?.defaultChoice
                                        ?: RouteStartScopeChoice.FullStop
                                    onGoScopeChoiceSelected(choice)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            if (showsRefillDecisionPanel) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .zIndex(19f)
                        .background(Color.Black.copy(alpha = 0.20f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (!refillDecisionState.isApplying) {
                                onDismissRefillDecision()
                            }
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(refillDecisionPanelMaxHeight)
                            .imePadding()
                            .pointerInput(refillDecisionState.isApplying) {
                                detectDragGestures(
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        refillSheetDragY = (refillSheetDragY + dragAmount.y).coerceAtLeast(0f)
                                    },
                                    onDragEnd = {
                                        if (!refillDecisionState.isApplying && refillSheetDragY > 90f) {
                                            onDismissRefillDecision()
                                        }
                                        refillSheetDragY = 0f
                                    },
                                    onDragCancel = {
                                        refillSheetDragY = 0f
                                    }
                                )
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {},
                        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                        tonalElevation = 2.dp,
                        shadowElevation = 8.dp,
                        color = palette.mapPanelSurface,
                        contentColor = palette.mapPanelForeground
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(20.dp)
                        ) {
                            SheetGrabber()
                            RefillDecisionView(
                                state = refillDecisionState,
                                destinationTitle = workState.selectedStop?.title,
                                destinationAddress = listOfNotNull(
                                    workState.selectedStop?.addressStreetLine,
                                    workState.selectedStop?.addressCityStateZipLine
                                ).joinToString(", ").ifBlank { null },
                                routePreview = refillDecisionState.routePreview ?: workState.routePreview,
                                onActionSelected = onRefillDecisionActionSelected,
                                onWarehouseSelected = onRefillDecisionWarehouseSelected,
                                onTaskInclusionToggle = onRefillDecisionTaskToggle,
                                onApply = onApplyRefillDecision
                            )
                        }
                    }
                }
            }

            if (showsPanel) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .zIndex(20f)
                        .background(Color.Black.copy(alpha = 0.22f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            isBottomPanelOpen = false
                        }
                )
                val usesWorkSheetLayout = showsWorkSheet
                val usesTasksSheetLayout = !usesWorkSheetLayout && uiState.selectedTab == MainTab.Tasks
                val usesNotificationsSheetLayout = !usesWorkSheetLayout && uiState.selectedTab == MainTab.Notifications
                val usesAtDestinationLayout = usesWorkSheetLayout &&
                    workState.phase.isAtDestination &&
                    workState.activeExecution != null
                val usesContactLocationSheetLayout = !usesWorkSheetLayout && isContactMode && uiState.selectedTab == MainTab.Locations
                val usesBoundedSheetContent = usesAtDestinationLayout || usesTasksSheetLayout || usesNotificationsSheetLayout || usesContactLocationSheetLayout
                val sheetHorizontalPadding = if (usesBoundedSheetContent) 0.dp else 12.dp
                val sheetContentHorizontalPadding = if (usesBoundedSheetContent) 0.dp else 14.dp
                val sheetContentTopPadding = if (usesAtDestinationLayout || usesNotificationsSheetLayout || usesContactLocationSheetLayout) 0.dp else 14.dp
                val sheetHeightModifier = when {
                    // At-destination is a page, rather than a partial sheet. Use the
                    // parent's complete edge-to-edge bounds so the surface continues
                    // behind gesture and three-button navigation bars; the content
                    // below applies the navigation-bar inset exactly once.
                    usesAtDestinationLayout -> Modifier.fillMaxSize()
                    usesTasksSheetLayout -> Modifier.height(
                        taskSheetHeight(layoutMaxHeight, responsiveLayout.taskPanelTopClearance)
                    )
                    usesContactLocationSheetLayout -> Modifier.height(layoutMaxHeight * 0.78f)
                    usesNotificationsSheetLayout -> Modifier.height(layoutMaxHeight * 0.82f)
                    else -> Modifier.heightIn(max = layoutMaxHeight * 0.72f)
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(21f)
                        .fillMaxWidth()
                        .then(sheetHeightModifier)
                        .padding(horizontal = sheetHorizontalPadding)
                        .imePadding()
                        .imeNestedScroll(),
                    shape = if (usesAtDestinationLayout) RoundedCornerShape(0.dp) else RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                    tonalElevation = 2.dp,
                    shadowElevation = 8.dp,
                    color = palette.mapPanelSurface,
                    contentColor = palette.mapPanelForeground
                ) {
                    val sheetContentModifier = Modifier
                        .then(if (usesBoundedSheetContent) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                        .then(if (usesBoundedSheetContent) Modifier else Modifier.verticalScroll(rememberScrollState()))
                        .padding(
                            start = sheetContentHorizontalPadding,
                            end = sheetContentHorizontalPadding,
                            top = sheetContentTopPadding,
                            bottom = if (usesAtDestinationLayout) 0.dp else 14.dp
                        )
                    val sheetBody: @Composable () -> Unit = {
                        if (usesWorkSheetLayout && workState.phase.isAtDestination && workState.activeExecution != null) {
                            when (workState.activeExecution.destinationKind) {
                                WorkDestinationKind.Warehouse -> AtWarehouseExecutionView(
                                    execution = workState.activeExecution,
                                    refillInventoryState = refillInventoryState,
                                    pickupInventoryState = pickupInventoryState,
                                    warehouses = locationsUiState.warehouses,
                                    allTasks = tasksUiState.tasks,
                                    locationsById = activeLocationsById,
                                    postPickupDestination = workState.postPickupDestination,
                                    pendingMutationTaskIds = tasksUiState.pendingMutationTaskIds,
                                    errorMessage = workState.errorMessage,
                                    taskActions = activeTaskActions,
                                    autoCalcCommission = settingsState.autoCalcCommission,
                                    onPrepareCurrentInventoryTask = onPrepareCurrentInventoryTask,
                                    onMarkCurrentTaskDone = onMarkCurrentWorkTaskDone,
                                    onCompleteCurrentInventoryTask = onCompleteCurrentInventoryTask,
                                    onAdvanceTask = onAdvanceCurrentWorkTask,
                                    onRefillQuantityChanged = onRefillQuantityChanged,
                                    onRefillFinalStockChanged = onRefillFinalStockChanged,
                                    onRefillSourceSelected = onRefillSourceSelected,
                                    onPickupQuantityChanged = onPickupQuantityChanged,
                                    onFinishVisit = onFinishCurrentWorkVisit,
                                    onCancelTasks = onCancelCurrentWorkTasks,
                                    onAddPhoto = onAddTaskPhoto,
                                    onRemovePhoto = onRemoveTaskPhoto,
                                    onApplySharedNotes = onApplySharedNotes
                                )
                                WorkDestinationKind.Location -> AtLocationExecutionView(
                                    execution = workState.activeExecution,
                                    refillInventoryState = refillInventoryState,
                                    pickupInventoryState = pickupInventoryState,
                                    warehouses = locationsUiState.warehouses,
                                    allTasks = tasksUiState.tasks,
                                    locationsById = activeLocationsById,
                                    postPickupDestination = workState.postPickupDestination,
                                    pendingMutationTaskIds = tasksUiState.pendingMutationTaskIds,
                                    errorMessage = workState.errorMessage,
                                    taskActions = activeTaskActions,
                                    autoCalcCommission = settingsState.autoCalcCommission,
                                    onPrepareCurrentInventoryTask = onPrepareCurrentInventoryTask,
                                    onMarkCurrentTaskDone = onMarkCurrentWorkTaskDone,
                                    onCompleteCurrentInventoryTask = onCompleteCurrentInventoryTask,
                                    onAdvanceTask = onAdvanceCurrentWorkTask,
                                    onRefillQuantityChanged = onRefillQuantityChanged,
                                    onRefillFinalStockChanged = onRefillFinalStockChanged,
                                    onRefillSourceSelected = onRefillSourceSelected,
                                    onPickupQuantityChanged = onPickupQuantityChanged,
                                    onFinishVisit = onFinishCurrentWorkVisit,
                                    onCancelTasks = onCancelCurrentWorkTasks,
                                    onAddPhoto = onAddTaskPhoto,
                                    onRemovePhoto = onRemoveTaskPhoto,
                                    onApplySharedNotes = onApplySharedNotes
                                )
                            }
                        } else if (usesWorkSheetLayout) {
                            WorkPanelView(
                                state = workState,
                                taskSummary = tasksUiState.summary,
                                refillDecisionState = refillDecisionState,
                                refillInventoryState = refillInventoryState,
                                pickupInventoryState = pickupInventoryState,
                                warehouses = locationsUiState.warehouses,
                                allTasks = tasksUiState.tasks,
                                pendingMutationTaskIds = tasksUiState.pendingMutationTaskIds,
                                taskActions = activeTaskActions,
                                autoCalcCommission = settingsState.autoCalcCommission,
                                onStartNavigation = onStartWorkNavigation,
                                onRefillDecisionActionSelected = onRefillDecisionActionSelected,
                                onRefillDecisionWarehouseSelected = onRefillDecisionWarehouseSelected,
                                onApplyRefillDecision = onApplyRefillDecision,
                                onArriveAtLocation = onArriveAtWorkLocation,
                                onPrepareCurrentInventoryTask = onPrepareCurrentInventoryTask,
                                onMarkCurrentTaskDone = onMarkCurrentWorkTaskDone,
                                onCompleteCurrentInventoryTask = onCompleteCurrentInventoryTask,
                                onAdvanceTask = onAdvanceCurrentWorkTask,
                                onRefillQuantityChanged = onRefillQuantityChanged,
                                onRefillFinalStockChanged = onRefillFinalStockChanged,
                                onRefillSourceSelected = onRefillSourceSelected,
                                onPickupQuantityChanged = onPickupQuantityChanged,
                                onFinishVisit = onFinishCurrentWorkVisit,
                                onCancelTasks = onCancelCurrentWorkTasks,
                                onAddPhoto = onAddTaskPhoto,
                                onRemovePhoto = onRemoveTaskPhoto,
                                onApplySharedNotes = onApplySharedNotes,
                                onStopSession = onStopCurrentWorkSession
                            )
                        } else {
                            when (uiState.selectedTab) {
                                MainTab.Tasks -> TasksPanelView(
                                tab = homePanelTab,
                                tasks = visibleTasks,
                                locationsById = activeLocationsById,
                                autoCalcCommission = settingsState.autoCalcCommission,
                                initialDate = if (homePanelTab == TasksHomePanelTab.Overview) {
                                    overviewInitialDate
                                } else {
                                    LocalDate.now()
                                },
                                selectedLocationId = selectedStop?.id,
                                pendingMutationTaskIds = tasksUiState.pendingMutationTaskIds,
                                isLoading = tasksUiState.isLoading,
                                isRefreshing = tasksUiState.isRefreshing,
                                errorMessage = tasksUiState.lastLoadError,
                                onBulkTaskAction = onBulkTaskAction,
                                onApplySharedNotes = onApplySharedNotes,
                                taskActions = taskActions,
                                onDateVisible = onTasksPanelDateVisible,
                                onOverviewDateVisible = onSummaryDateSelected,
                                isReadOnly = isContactMode,
                                useContactVisibility = isContactMode,
                                onClose = { isBottomPanelOpen = false }
                                )
                                MainTab.Locations -> if (isContactMode) {
                                    ContactLocationDetailView(
                                        state = contactState,
                                        tasks = visibleTasks,
                                        selectedLocationId = selectedContactLocationId,
                                        onLocationSelected = { selectedContactLocationId = it },
                                        onLoadMachines = onLoadContactLocationMachines,
                                        onOpenTasks = { date, locationId ->
                                            onMapStopSelected(locationId)
                                            overviewInitialDate = date
                                            onSummaryDateSelected(date)
                                            homePanelTab = TasksHomePanelTab.Tasks
                                            onTabSelected(MainTab.Tasks)
                                            isBottomPanelOpen = true
                                        },
                                        onClose = { isBottomPanelOpen = false }
                                    )
                                } else {
                                    LocationsPanelView(
                                        state = locationsUiState,
                                        onRefresh = onRefresh
                                    )
                                }
                                MainTab.Notifications -> NotificationsPanelView(
                                state = notificationsState,
                                onClose = { isBottomPanelOpen = false },
                                onMarkAllRead = onMarkAllNotificationsRead,
                                onSelect = {
                                    onNotificationSelected(it)
                                    homePanelTab = if (it.taskStatus == TaskStatus.Done) {
                                        TasksHomePanelTab.CompletedToday
                                    } else {
                                        TasksHomePanelTab.Tasks
                                    }
                                    onTabSelected(MainTab.Tasks)
                                }
                                )
                                MainTab.Settings -> SettingsPanelView(
                                state = settingsState,
                                onAutoCalcCommissionChanged = onAutoCalcCommissionChanged,
                                onAutoFillRefillFinalStockChanged = onAutoFillRefillFinalStockChanged
                                )
                            }
                        }
                    }
                    if (usesBoundedSheetContent) {
                        Box(modifier = sheetContentModifier) {
                            sheetBody()
                        }
                    } else {
                        Column(modifier = sheetContentModifier) {
                            if (!usesAtDestinationLayout && !showsOverviewSheet && uiState.selectedTab != MainTab.Tasks && !usesNotificationsSheetLayout && !usesContactLocationSheetLayout) {
                                SheetGrabber()
                                Spacer(modifier = Modifier.height(14.dp))
                            }
                            sheetBody()
                            if (!showsOverviewSheet && notificationsState.unreadCount > 0) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "${notificationsState.unreadCount} unread notifications",
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
                                style = MaterialTheme.typography.bodySmall
                            )
                            }
                        }
                    }
                }
            }

            if (isMenuOpen) {
                SideMenuDrawer(
                    userDisplayName = userDisplayName,
                    organizationLabel = uiState.organizationTitle,
                    settingsState = settingsState,
                    appModeState = appModeState,
                    contactState = contactState,
                    canSwitchViewModes = contactState.hasLocations && uiState.canUseOrganizationView,
                    onViewModeSelected = onViewModeSelected,
                    onContactLocationSelected = {
                        selectedContactLocationId = it
                        onTabSelected(MainTab.Locations)
                        isBottomPanelOpen = true
                        isMenuOpen = false
                    },
                    onAutoCalcCommissionChanged = onAutoCalcCommissionChanged,
                    onAutoFillRefillFinalStockChanged = onAutoFillRefillFinalStockChanged,
                    onAppearancePreferenceChanged = onAppearancePreferenceChanged,
                    onNavigationAudioPreferenceChanged = onNavigationAudioPreferenceChanged,
                    onTimeFormatPreferenceChanged = onTimeFormatPreferenceChanged,
                    onClose = { isMenuOpen = false },
                    onSignOut = onSignOut
                )
            }

            if (isAddStopOpen) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .zIndex(40f)
                        .background(Color.Black.copy(alpha = 0.24f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (!addStopState.isSaving) isAddStopOpen = false
                        }
                )
                AddStopPanelView(
                    state = addStopState,
                    locationsById = locationsUiState.locationsById,
                    onPrepare = onPrepareAddStop,
                    onClose = { isAddStopOpen = false },
                    onDateChanged = onAddStopDateChanged,
                    onNotesChanged = onAddStopNotesChanged,
                    onLocationToggle = onAddStopLocationToggle,
                    onMachineToggle = onAddStopMachineToggle,
                    onTaskTypeToggle = onAddStopTaskTypeToggle,
                    onAssigneeSelected = onAddStopAssigneeSelected,
                    onConfirmPrecheck = {
                        onConfirmAddStopPrecheck {
                            isAddStopOpen = false
                        }
                    },
                    onConfirmRescheduleExisting = {
                        onConfirmAddStopRescheduleExisting {
                            isAddStopOpen = false
                        }
                    },
                    onDismissPrecheckAlert = onDismissAddStopPrecheckAlert,
                    onSave = {
                        onSaveAddStop {
                            isAddStopOpen = false
                        }
                    },
                    modifier = Modifier
                        .matchParentSize()
                        .zIndex(41f)
                )
            }
        } else {
            LaunchedEffect(Unit) {
                isBottomPanelOpen = false
                isMenuOpen = false
                isSummaryExpanded = false
            }
        }
        }
    }

    TaskActionSheet(
        state = navigationCancelSheetState,
        onDismiss = { isNavigationCancelSheetPresented = false },
        onConfirm = {
            val selectedTasks = navigationCancelSheetState.selectedTasks
            if (selectedTasks.isNotEmpty()) {
                isNavigationCancelSheetPresented = false
                onCancelCurrentWorkTasks(selectedTasks)
            }
        },
        onTaskAssigneeSelected = { _, _ -> },
        onDateSelected = {},
        onQuickDateSelected = {},
        onTaskSelectionToggle = { taskId ->
            val nextSelection = navigationCancelSelectionTaskIds.toMutableSet().also { ids ->
                if (!ids.add(taskId)) ids.remove(taskId)
            }
            navigationCancelSelectionTaskIds = TaskBulkSelectionRules.normalizedSelection(
                allTasks = navigationCancelSheetState.actionableTasks,
                selectedTaskIds = nextSelection
            )
        }
    )
}

@Composable
private fun BoxScope.EdgeSwipeOpenLayer(onOpen: () -> Unit) {
    var dragX by remember { mutableStateOf(0f) }
    var dragY by remember { mutableStateOf(0f) }
    var didOpen by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .width(EdgeSwipeOpenWidthDp.dp)
            .fillMaxHeight()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragX += dragAmount.x
                        dragY += dragAmount.y
                        if (!didOpen &&
                            dragX > EdgeSwipeOpenThresholdPx &&
                            kotlin.math.abs(dragY) < EdgeSwipeVerticalTolerancePx
                        ) {
                            didOpen = true
                            onOpen()
                        }
                    },
                    onDragEnd = {
                        if (!didOpen &&
                            dragX > EdgeSwipeOpenThresholdPx &&
                            kotlin.math.abs(dragY) < EdgeSwipeVerticalTolerancePx
                        ) {
                            onOpen()
                        }
                        dragX = 0f
                        dragY = 0f
                        didOpen = false
                    },
                    onDragCancel = {
                        dragX = 0f
                        dragY = 0f
                        didOpen = false
                    }
                )
            }
    )
}

@Composable
private fun BoxScope.SideMenuDrawer(
    userDisplayName: String,
    organizationLabel: String,
    settingsState: AppSettingsState,
    appModeState: AppModeUiState,
    contactState: ContactUiState,
    canSwitchViewModes: Boolean,
    onViewModeSelected: (AppViewMode) -> Unit,
    onContactLocationSelected: (String) -> Unit,
    onAutoCalcCommissionChanged: (Boolean) -> Unit,
    onAutoFillRefillFinalStockChanged: (Boolean) -> Unit,
    onAppearancePreferenceChanged: (AppAppearancePreference) -> Unit,
    onNavigationAudioPreferenceChanged: (NavigationAudioPreference) -> Unit,
    onTimeFormatPreferenceChanged: (TimeFormatPreference) -> Unit,
    onClose: () -> Unit,
    onSignOut: () -> Unit
) {
    var dragX by remember { mutableStateOf(0f) }
    Box(
        modifier = Modifier
            .matchParentSize()
            .zIndex(SideMenuScrimZ)
            .background(Color.Black.copy(alpha = 0.25f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClose() }
    )
    Surface(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .zIndex(SideMenuDrawerZ)
            .width(280.dp)
            .fillMaxHeight()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragX += dragAmount.x
                    },
                    onDragEnd = {
                        if (dragX < -56f) onClose()
                        dragX = 0f
                    },
                    onDragCancel = {
                        dragX = 0f
                    }
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {},
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = userDisplayName.ifBlank { "Signed In" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (!appModeState.isContactMode) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    SideMenuSwitchRow(
                        title = "Auto-calc commission",
                        checked = settingsState.autoCalcCommission,
                        onCheckedChange = onAutoCalcCommissionChanged
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
                    SideMenuSwitchRow(
                        title = "Auto-fill refill final stock",
                        checked = settingsState.autoFillRefillFinalStock,
                        onCheckedChange = onAutoFillRefillFinalStockChanged
                    )
                }
            }

            SideMenuSegmentSection(
                title = "Appearance",
                options = AppAppearancePreference.entries,
                selected = settingsState.appearancePreference,
                label = { it.label },
                icon = {
                    when (it) {
                        AppAppearancePreference.System -> R.drawable.ic_system_theme
                        AppAppearancePreference.Light -> R.drawable.ic_sun
                        AppAppearancePreference.Dark -> R.drawable.ic_moon
                    }
                },
                onSelected = onAppearancePreferenceChanged
            )

            SideMenuSegmentSection(
                title = "Time Format",
                options = TimeFormatPreference.entries,
                selected = settingsState.timeFormatPreference,
                label = {
                    when (it) {
                        TimeFormatPreference.System -> it.label
                        TimeFormatPreference.TwelveHour -> "12-hour"
                        TimeFormatPreference.TwentyFourHour -> "24-hour"
                    }
                },
                icon = {
                    when (it) {
                        TimeFormatPreference.System -> R.drawable.ic_clock_filled
                        TimeFormatPreference.TwelveHour -> null
                        TimeFormatPreference.TwentyFourHour -> null
                    }
                },
                onSelected = onTimeFormatPreferenceChanged
            )

            SideMenuSegmentSection(
                title = "Navigation Sound",
                options = NavigationAudioPreference.entries,
                selected = settingsState.navigationAudioPreference,
                label = { it.label },
                icon = {
                    when (it) {
                        NavigationAudioPreference.Sound -> R.drawable.ic_speaker
                        NavigationAudioPreference.Silent -> R.drawable.ic_speaker_off
                    }
                },
                onSelected = onNavigationAudioPreferenceChanged
            )

            if (appModeState.isContactMode && contactState.sortedLocations.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = "Locations",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = LocalVendistriPalette.current.textSecondary
                    )
                    contactState.sortedLocations.forEach { location ->
                        SideMenuSelectionRow(
                            title = location.name,
                            onClick = { onContactLocationSelected(location.id) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (canSwitchViewModes) {
                SideMenuViewSelector(
                    organizationLabel = organizationLabel,
                    selectedMode = appModeState.mode,
                    onSelected = onViewModeSelected
                )
            }

            Surface(
                onClick = {
                    onClose()
                    onSignOut()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
            ) {
                Text(
                    text = "Logout",
                    modifier = Modifier.padding(vertical = 8.dp),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun SideMenuSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SideMenuViewSelector(
    organizationLabel: String,
    selectedMode: AppViewMode,
    onSelected: (AppViewMode) -> Unit
) {
    val palette = LocalVendistriPalette.current
    var isOpen by remember { mutableStateOf(false) }
    val selectedLabel = when (selectedMode) {
        AppViewMode.Organization -> organizationLabel
        AppViewMode.LocationContact -> "Location Contact"
    }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = "View",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = palette.textSecondary
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val menuWidth = maxWidth
            Surface(
                onClick = { isOpen = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        selectedLabel,
                        modifier = Modifier.weight(1f),
                        color = palette.textPrimary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(if (isOpen) "⌃" else "⌄", color = palette.textSecondary, fontWeight = FontWeight.SemiBold)
                }
            }
            DropdownMenu(
                expanded = isOpen,
                onDismissRequest = { isOpen = false },
                modifier = Modifier.width(menuWidth)
            ) {
                SideMenuViewDropdownItem(
                    title = organizationLabel,
                    isSelected = selectedMode == AppViewMode.Organization,
                    onClick = {
                        isOpen = false
                        onSelected(AppViewMode.Organization)
                    }
                )
                SideMenuViewDropdownItem(
                    title = "Location Contact",
                    isSelected = selectedMode == AppViewMode.LocationContact,
                    onClick = {
                        isOpen = false
                        onSelected(AppViewMode.LocationContact)
                    }
                )
            }
        }
    }
}

@Composable
private fun SideMenuViewDropdownItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold
                )
                if (isSelected) {
                    Text("✓", color = AppColors.vendBlue, fontWeight = FontWeight.Bold)
                }
            }
        },
        onClick = onClick
    )
}

@Composable
private fun SideMenuSelectionRow(
    title: String,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                color = LocalVendistriPalette.current.textPrimary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            if (isSelected) {
                Text("✓", color = AppColors.vendBlue, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun <T> SideMenuSegmentSection(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    icon: (T) -> Int?,
    onSelected: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                    RoundedCornerShape(16.dp)
                )
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                Surface(
                    onClick = { onSelected(option) },
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        icon(option)?.let { iconRes ->
                            Icon(
                                painter = painterResource(iconRes),
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = if (isSelected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                                }
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                        }
                        Text(
                            text = label(option),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryChip(amount: Double, onClick: () -> Unit, modifier: Modifier = Modifier) {
    RevenueChip(
        amount = amount,
        modifier = modifier.clickable(onClick = onClick),
        showsShadow = true,
        textSize = RevenueChipTextSize.Large
    )
}

@Composable
private fun SummaryExpandedPanel(
    summary: TaskDaySummary,
    onWeeklySummary: () -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dragX by remember(summary.id) { mutableStateOf(0f) }
    val palette = LocalVendistriPalette.current
    Surface(
        modifier = modifier
            .pointerInput(summary.id) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragX += dragAmount.x
                    },
                    onDragEnd = {
                        if (dragX > 40f) onPreviousDay()
                        if (dragX < -40f) onNextDay()
                        dragX = 0f
                    },
                    onDragCancel = {
                        dragX = 0f
                    }
                )
        },
        shape = RoundedCornerShape(18.dp),
        color = palette.mapPanelSurface,
        contentColor = palette.mapPanelForeground,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    summary.locationsText,
                    color = palette.mapPanelForeground,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (summary.isToday) {
                        Surface(modifier = Modifier.size(6.dp), shape = CircleShape, color = AppColors.vendBlue) {}
                    }
                    Text(summary.dateLabel, color = AppColors.muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                SummaryValueRow(label = "Machines", value = summary.machinesText)
                SummaryValueRow(label = "Commission", value = summary.commissionText)
                SummaryValueRow(label = "Net Revenue", value = summary.netRevenueText)
            }


            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onPreviousDay, modifier = Modifier.size(34.dp)) {
                    Text("<", color = AppColors.muted, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onWeeklySummary) {
                    Text(
                        "View weekly summary",
                        color = palette.mapPanelForeground,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (!summary.isToday) {
                    TextButton(onClick = onNextDay, modifier = Modifier.size(34.dp)) {
                        Text(">", color = AppColors.muted, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Spacer(modifier = Modifier.size(34.dp))
                }
            }
        }
    }
}

@Composable
private fun SummaryValueRow(label: String, value: String) {
    val palette = LocalVendistriPalette.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AppColors.muted, fontWeight = FontWeight.SemiBold)
        Text(value, color = palette.mapPanelForeground, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MapIconButton(
    iconRes: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
    onClick: () -> Unit
) {
    val palette = LocalVendistriPalette.current
    VendistriIconButton(
        iconRes = iconRes,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        background = palette.mapChromeSurface,
        foreground = palette.mapChromeForeground,
        badgeCount = badgeCount
    )
}

@Composable
private fun MapGoButton(
    isPaused: Boolean,
    onClick: () -> Unit
) {
    val responsiveLayout = LocalVendistriResponsiveLayout.current
    Box(
        modifier = Modifier.size(responsiveLayout.primaryMapActionSize),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = AppColors.vendBlue,
            shadowElevation = 10.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "GO",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
        PulsingRing(
            isPaused = isPaused,
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
        )
    }
}

@Composable
private fun PulsingRing(
    isPaused: Boolean,
    modifier: Modifier = Modifier
) {
    var phase by remember { mutableStateOf(0f) }
    val currentIsPaused by rememberUpdatedState(isPaused)
    LaunchedEffect(Unit) {
        var previousFrameNanos = 0L
        while (true) {
            val frameNanos = withFrameNanos { it }
            if (previousFrameNanos != 0L && !currentIsPaused) {
                val deltaSeconds = (frameNanos - previousFrameNanos) / 1_000_000_000f
                phase = (phase + (deltaSeconds / 1.7f)) % 1f
            }
            previousFrameNanos = frameNanos
        }
    }
    val wave = if (phase < 0.5f) {
        phase * 2f
    } else {
        (1f - phase) * 2f
    }
    Canvas(modifier = modifier) {
        val strokeWidth = 2.6.dp.toPx()
        val scale = 0.86f + (0.14f * wave)
        val radius = ((size.minDimension - strokeWidth) / 2f) * scale
        drawCircle(
            color = Color.White.copy(alpha = 0.75f - (0.5f * wave)),
            radius = radius,
            style = Stroke(width = strokeWidth)
        )
    }
}

@Composable
private fun MapBottomBar(
    centerTitle: String,
    onLeft: () -> Unit,
    onCenter: () -> Unit,
    onRight: () -> Unit,
    onSwipeUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalVendistriPalette.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {},
                    onDrag = { change, dragAmount ->
                        if (dragAmount.y < -28f) {
                            change.consume()
                            onSwipeUp()
                        }
                    }
                )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = palette.mapBottomBarSurface,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .padding(top = 8.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.size(width = 58.dp, height = 5.dp),
                shape = CircleShape,
                color = AppColors.muted.copy(alpha = 0.35f)
            ) {}
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(onClick = onLeft, color = Color.Transparent, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_sliders),
                        contentDescription = "Overview",
                        tint = palette.mapChromeForeground,
                        modifier = Modifier.padding(6.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onCenter) {
                    Text(
                        centerTitle,
                        color = AppColors.vendBlue,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Surface(onClick = onRight, color = Color.Transparent, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_list_bullets),
                        contentDescription = "Completed today",
                        tint = palette.mapChromeForeground,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationStartupOverlay(
    destinationTitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.92f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CircularProgressIndicator(
                    color = AppColors.vendBlue,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(34.dp)
                )
                Text(
                    text = "Getting Route...",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = destinationTitle,
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SheetGrabber() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(width = 48.dp, height = 5.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
        ) {}
    }
}

private data class TaskDaySummary(
    val id: String,
    val date: LocalDate,
    val isToday: Boolean,
    val locationsCount: Int,
    val machinesCount: Int,
    val grossRevenue: Double,
    val commission: Double,
    val netRevenue: Double
) {
    val dateLabel: String = date.format(TaskDateFormatters.shortDay)
    val locationsText: String = "$locationsCount ${if (locationsCount == 1) "Location" else "Locations"}"
    val machinesText: String = machinesCount.toString()
    val commissionText: String = formatCurrency(commission)
    val netRevenueText: String = formatCurrency(netRevenue)

    companion object {
        fun empty(date: LocalDate, today: LocalDate = LocalDate.now()): TaskDaySummary {
            return TaskDaySummary(
                id = date.toString(),
                date = date,
                isToday = date == today,
                locationsCount = 0,
                machinesCount = 0,
                grossRevenue = 0.0,
                commission = 0.0,
                netRevenue = 0.0
            )
        }

        fun fromTasks(tasks: List<VendiTask>, today: LocalDate = LocalDate.now()): List<TaskDaySummary> {
            val completedTasks = tasks.filter { TaskStateHelpers.isFinal(it.status) }
            val grouped = completedTasks
                .mapNotNull { task -> task.summaryDate()?.takeIf { !it.isAfter(today) }?.let { it to task } }
                .groupBy(keySelector = { it.first }, valueTransform = { it.second })
                .toMutableMap()

            grouped.putIfAbsent(today, emptyList())

            return grouped.keys
                .sortedDescending()
                .map { day ->
                    val dayTasks = grouped[day].orEmpty()
                    val financials = TaskFinancialHelpers.sumTaskFinancials(dayTasks)
                    TaskDaySummary(
                        id = day.toString(),
                        date = day,
                        isToday = day == today,
                        locationsCount = dayTasks.mapNotNull { it.location }.distinct().size,
                        machinesCount = dayTasks.mapNotNull { it.machine }.distinct().size,
                        grossRevenue = financials.gross,
                        commission = financials.commission,
                        netRevenue = financials.net
                    )
                }
        }
    }
}

private fun VendiTask.summaryDate(): LocalDate? {
    return panelWorkDate()
}

private fun formatCurrency(value: Double): String {
    return "$ ${money(value)}"
}
