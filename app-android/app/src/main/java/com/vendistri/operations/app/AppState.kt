package com.vendistri.operations.app

import android.app.Application
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vendistri.operations.features.tasks.add_stop.AddStopApi
import com.vendistri.operations.features.tasks.add_stop.AddStopRepository
import com.vendistri.operations.features.tasks.add_stop.AddStopStore
import com.vendistri.operations.features.tasks.add_stop.AddStopUiState
import com.vendistri.operations.features.auth.AuthUiState
import com.vendistri.operations.features.auth.AuthApi
import com.vendistri.operations.features.auth.OrganizationSummary
import com.vendistri.operations.features.auth.User
import com.vendistri.operations.features.auth.UserStore
import com.vendistri.operations.features.location.LocationApi
import com.vendistri.operations.features.location.LocationStore
import com.vendistri.operations.features.location.LocationUiState
import com.vendistri.operations.features.location_contact.AppModeStore
import com.vendistri.operations.features.location_contact.AppModeUiState
import com.vendistri.operations.features.location_contact.AppViewMode
import com.vendistri.operations.features.location_contact.ContactStore
import com.vendistri.operations.features.location_contact.ContactUiState
import com.vendistri.operations.features.location_contact.ContactVisibilityRules
import com.vendistri.operations.features.live_status.LiveStatusCoordinator
import com.vendistri.operations.features.map.LocationCoordinate
import com.vendistri.operations.features.map.LocationStop
import com.vendistri.operations.features.map.LocationStopsBuilder
import com.vendistri.operations.features.map.MapNavigationProgress
import com.vendistri.operations.features.main.MainUiState
import com.vendistri.operations.features.main.MainTab
import com.vendistri.operations.features.navigation.DebugRouteSimulationConfig
import com.vendistri.operations.features.navigation.AndroidNavigationVoiceSpeaker
import com.vendistri.operations.features.navigation.NavigationSessionStore
import com.vendistri.operations.features.navigation.NavigationSessionState
import com.vendistri.operations.features.navigation.NavigationVoiceSpeaker
import com.vendistri.operations.features.notifications.AppNotificationItem
import com.vendistri.operations.features.notifications.NotificationsState
import com.vendistri.operations.features.notifications.NotificationsStore
import com.vendistri.operations.features.pickup.PickupInventoryUiState
import com.vendistri.operations.features.pickup.PickupInventoryStore
import com.vendistri.operations.features.refill.RefillInventoryUiState
import com.vendistri.operations.features.refill.RefillInventoryStore
import com.vendistri.operations.features.settings.AppAppearancePreference
import com.vendistri.operations.features.settings.AppSettingsState
import com.vendistri.operations.features.settings.AppSettingsStore
import com.vendistri.operations.features.settings.NavigationAudioPreference
import com.vendistri.operations.features.settings.SharedPreferencesAppSettingsStorage
import com.vendistri.operations.features.settings.TimeFormatPreference
import com.vendistri.operations.features.tasks.CollectionFinancialDraft
import com.vendistri.operations.features.tasks.TaskActionContext
import com.vendistri.operations.features.tasks.TaskPermissions
import com.vendistri.operations.features.tasks.TaskRowActionPolicy
import com.vendistri.operations.features.tasks.TasksApi
import com.vendistri.operations.features.tasks.TaskScheduleDate
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.RefillInventorySourceMode
import com.vendistri.operations.features.tasks.TaskStateHelpers
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.TasksStore
import com.vendistri.operations.features.tasks.TasksUiState
import com.vendistri.operations.features.tasks.VendiTask
import com.vendistri.operations.features.tasks.actions.TaskActionKind
import com.vendistri.operations.features.tasks.actions.TaskActionState
import com.vendistri.operations.features.tasks.actions.TaskAssigneesApi
import com.vendistri.operations.features.tasks.actions.TaskActionStore
import com.vendistri.operations.features.work.ActiveWorkSession
import com.vendistri.operations.features.work.WorkFlowStore
import com.vendistri.operations.features.work.GoStopPlan
import com.vendistri.operations.features.work.RefillDecisionAction
import com.vendistri.operations.features.work.RefillDecisionStore
import com.vendistri.operations.features.work.RefillDecisionUiState
import com.vendistri.operations.features.work.PickupInventoryRouteContext
import com.vendistri.operations.features.work.RouteRefillInventoryDecision
import com.vendistri.operations.features.work.RoutePreviewEstimator
import com.vendistri.operations.features.work.RouteStartScopeChoice
import com.vendistri.operations.features.work.RouteStartScopeOption
import com.vendistri.operations.features.work.RouteStartScopeResolver
import com.vendistri.operations.features.work.TaskExecutionPlanner
import com.vendistri.operations.features.work.TaskExecutionResolver
import com.vendistri.operations.features.work.WorkPhase
import com.vendistri.operations.features.work.WorkUiState
import com.vendistri.operations.features.work.isAtDestination
import com.vendistri.operations.features.work.isNavigating
import com.vendistri.operations.network.ApiClient
import com.vendistri.operations.network.BackendCookieJar
import com.vendistri.operations.network.EncryptedSharedPreferencesBackendCookieStorage
import com.vendistri.operations.network.NetworkConfig
import com.vendistri.operations.realtime.RealtimeStore
import com.vendistri.operations.storage.RestoreSnapshot
import com.vendistri.operations.storage.RestoreStateStore
import com.vendistri.operations.storage.SharedPreferencesRestoreSnapshotStorage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate

private enum class ActiveRouteTaskState {
    NotLoaded,
    HasActive,
    AllFinal
}

private data class InferredActiveWorkCandidate(
    val session: ActiveWorkSession,
    val phase: WorkPhase,
    val stop: GoStopPlan
)

class AppState(application: Application) : AndroidViewModel(application) {
    private companion object {
        const val LogTag = "VendistriAppState"
    }

    private val apiClient = ApiClient(
        cookieJar = BackendCookieJar(
            EncryptedSharedPreferencesBackendCookieStorage(application)
        )
    )
    private val authApi = AuthApi(apiClient)
    private val tasksApi = TasksApi(apiClient)
    private val userStore = UserStore(authApi)
    private val tasksStore = TasksStore(tasksApi)
    private val taskActionStore = TaskActionStore(tasksStore, TaskAssigneesApi(apiClient), tasksApi)
    private val addStopStore = AddStopStore(
        AddStopRepository(
            addStopApi = AddStopApi(apiClient),
            taskAssigneesApi = TaskAssigneesApi(apiClient),
            tasksApi = tasksApi
        ),
        tasksStore
    )
    private val locationApi = LocationApi(apiClient)
    private val locationStore = LocationStore(locationApi)
    private val contactStore = ContactStore(locationApi)
    private val appModeStore = AppModeStore()
    private val workFlowStore = WorkFlowStore()
    private val refillDecisionStore = RefillDecisionStore()
    private val navigationSessionStore = NavigationSessionStore()
    private val navigationVoiceSpeaker: NavigationVoiceSpeaker = AndroidNavigationVoiceSpeaker(application)
    private var announceNextReroutedInstruction = false
    private val refillInventoryStore = RefillInventoryStore(tasksApi)
    private val pickupInventoryStore = PickupInventoryStore()
    private var isRefreshingRefillDecisionContext = false
    private val notificationsStore = NotificationsStore()
    private val settingsStore = AppSettingsStore(SharedPreferencesAppSettingsStorage(application))
    private val restoreStateStore = RestoreStateStore(SharedPreferencesRestoreSnapshotStorage(application))
    @Suppress("unused")
    private val liveStatusCoordinator = LiveStatusCoordinator(
        context = application,
        scope = viewModelScope,
        work = workFlowStore.state,
        navigation = navigationSessionStore.state,
        settings = settingsStore.state
    )
    private val mainState = MutableStateFlow(MainUiState())
    private val sessionState = MutableStateFlow(AppSessionState())
    private var currentMapCoordinate: LocationCoordinate? = null
    private var hasPersistedActiveWorkSnapshot = false
    private val machineStartMutex = Mutex()
    private val realtimeStore = RealtimeStore(
        apiClient = apiClient,
        scope = viewModelScope,
        userStore = userStore,
        tasksStore = tasksStore,
        locationStore = locationStore,
        contactStore = contactStore,
        notificationsStore = notificationsStore,
        workFlowStore = workFlowStore,
        onInventoryChanged = {
            refillInventoryStore.refreshOpenWarehouseAvailability(
                tasksById = tasksStore.state.value.tasksById,
                allTasks = tasksStore.state.value.tasks
            )
            pickupInventoryStore.state.value.taskId
                ?.let(tasksStore.state.value.tasksById::get)
                ?.let(pickupInventoryStore::refreshAvailability)
        }
    )

    val uiState: StateFlow<VendistriUiState> = combine(
        userStore.state,
        tasksStore.state,
        taskActionStore.state,
        addStopStore.state,
        locationStore.state,
        contactStore.state,
        appModeStore.state,
        workFlowStore.state,
        refillDecisionStore.state,
        refillInventoryStore.state,
        refillInventoryStore.taskStates,
        pickupInventoryStore.state,
        navigationSessionStore.state,
        notificationsStore.state,
        settingsStore.state,
        mainState,
        sessionState
    ) { values ->
        val auth = values[0] as AuthUiState
        val tasks = values[1] as TasksUiState
        val taskActions = values[2] as TaskActionState
        val addStop = values[3] as AddStopUiState
        val locations = values[4] as LocationUiState
        val contact = values[5] as ContactUiState
        val appMode = values[6] as AppModeUiState
        val work = values[7] as WorkUiState
        val refillDecision = values[8] as RefillDecisionUiState
        val refillInventory = values[9] as RefillInventoryUiState
        @Suppress("UNCHECKED_CAST")
        val taskRefillInventory = values[10] as Map<String, RefillInventoryUiState>
        val pickupInventory = values[11] as PickupInventoryUiState
        val navigation = values[12] as NavigationSessionState
        val notifications = values[13] as NotificationsState
        val settings = values[14] as AppSettingsState
        val main = values[15] as MainUiState
        val session = values[16] as AppSessionState
        VendistriUiState(
            auth = auth,
            tasks = tasks,
            taskActions = taskActions,
            addStop = addStop,
            locations = locations,
            contact = contact,
            appMode = appMode,
            work = work,
            refillDecision = refillDecision,
            refillInventory = refillInventory,
            taskRefillInventory = taskRefillInventory,
            pickupInventory = pickupInventory,
            navigation = navigation,
            notifications = notifications,
            settings = settings,
            main = main,
            isSyncingUserSession = session.isSyncingUserSession
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VendistriUiState()
    )

    val isDebugRouteSimulationEnabled: Boolean
        get() = DebugRouteSimulationConfig.isEnabled

    init {
        bootstrapSession()
        observeGoPlanSourceOfTruth()
        observeRefillDecisionSourceOfTruth()
        observeActiveWorkRestoreSnapshot()
    }

    private fun observeActiveWorkRestoreSnapshot() {
        viewModelScope.launch {
            combine(
                workFlowStore.state,
                navigationSessionStore.state
            ) { work, navigation -> work to navigation }
                .collect { (work, navigation) ->
                    val activeSession = work.activeSession
                    if (activeSession == null) {
                        if (hasPersistedActiveWorkSnapshot) {
                            hasPersistedActiveWorkSnapshot = false
                            restoreStateStore.saveSnapshot(RestoreSnapshot())
                        }
                        return@collect
                    }
                    hasPersistedActiveWorkSnapshot = true
                    restoreStateStore.saveSnapshot(
                        RestoreSnapshot(
                            activeWorkSession = activeSession,
                            activeWorkPhase = work.phase.takeIf { phase -> phase != WorkPhase.Idle },
                            activeNavigationStopId = navigation.activeStopId,
                            localActiveExecutionSession = work.localActiveExecutionSession,
                            postPickupDestination = work.postPickupDestination
                        )
                    )
                }
        }
    }

    private fun observeGoPlanSourceOfTruth() {
        viewModelScope.launch {
            combine(
                tasksStore.state,
                locationStore.state,
                userStore.state,
                mainState
            ) { _, _, _, _ -> Unit }
                .collect {
                    rehydrateGoPlanFromStores()
                }
        }
    }

    private fun observeRefillDecisionSourceOfTruth() {
        viewModelScope.launch {
            combine(
                tasksStore.state,
                locationStore.state,
                userStore.state
            ) { _, _, _ -> Unit }
                .collect {
                    refreshRefillDecisionContextFromStores()
                }
        }
    }

    private fun buildCurrentGoPlan() = TaskExecutionPlanner.buildPlan(
        tasks = tasksStore.state.value.tasks,
        currentUserId = userStore.currentUser?.id,
        includeClaimableUnassigned = TaskPermissions.canIncludeClaimableUnassignedTasks(
            user = userStore.currentUser,
            operatorTaskClaimingEnabled = mainState.value.operatorTaskClaimingEnabled
        ),
        currentCoordinate = currentMapCoordinate,
        locationsById = locationStore.state.value.locationsById
    )

    private suspend fun loadOrganizationContext(user: User?): MainUiState {
        val canUseOrganizationView = user?.let { it.isOwner || it.isAdmin || it.isOperator } == true
        val canManageScheduledTasks = TaskPermissions.canManageScheduledTasks(user)
        val organization = if (canUseOrganizationView) {
            runCatching { OrganizationSummary.fromJson(authApi.organizationSummary()) }.getOrNull()
        } else {
            null
        }
        return MainUiState(
            organizationTitle = organization?.title ?: "Vendistri Operations",
            operatorTaskClaimingEnabled = organization?.operatorTaskClaimingEnabled ?: false,
            canUseOrganizationView = canUseOrganizationView,
            canManageScheduledTasks = canManageScheduledTasks,
            weekLabel = "This week",
            taskSummary = tasksStore.state.value.summary
        )
    }

    private fun rehydrateGoPlanFromStores() {
        val workState = workFlowStore.state.value
        if (workState.phase != WorkPhase.Summary) return
        if (workState.isLoading || workState.routeStartScopeDecision != null) return
        workFlowStore.rehydrateGoPlan(buildCurrentGoPlan())
        refreshGoRoutePreviewIfNeeded()
    }

    private suspend fun refreshRefillDecisionContextFromStores() {
        val state = refillDecisionStore.state.value
        if (!state.isVisible || isRefreshingRefillDecisionContext) return
        // A current-location refill with no configured items remains visible as context,
        // but has no inventory decision to refresh. Avoid an invalid/pointless API call.
        val taskIds = state.plans
            .filter { it.isRemaining || it.hasRecommendedRefill }
            .map { it.task.id }
            .distinct()
        if (taskIds.isEmpty()) return
        isRefreshingRefillDecisionContext = true
        try {
            refillDecisionStore.setWarehouses(locationStore.state.value.warehouses)
            refreshRefillDecisionRoutePreviews()
            val context = tasksStore.loadRefillInventoryContext(taskIds, state.selectedWarehouseId)
            refillDecisionStore.replaceContext(context)
            refreshRefillDecisionRoutePreviews()
        } catch (error: Exception) {
            Log.e(
                LogTag,
                "go.refillDecision.refreshContext.failed taskIds=${taskIds.joinToString(",")} warehouseId=${state.selectedWarehouseId}",
                error
            )
            refillDecisionStore.setError("Could not refresh refill inventory.")
        } finally {
            isRefreshingRefillDecisionContext = false
        }
    }

    private fun bootstrapSession() {
        viewModelScope.launch {
            userStore.initUser()
            if (userStore.currentUser != null) {
                realtimeStore.updateConnection(userStore.currentUser)
                syncUserSessionScope()
            }
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            userStore.signIn(email, password)
            if (userStore.currentUser != null) {
                realtimeStore.updateConnection(userStore.currentUser)
                syncUserSessionScope()
            }
        }
    }

    private suspend fun syncUserSessionScope() {
        sessionState.update { it.copy(isSyncingUserSession = true) }
        try {
            userStore.refreshSubscriptionStatusIfAvailable(force = true)
            tasksStore.resetUserScopedState()
            locationStore.resetUserScopedState()
            contactStore.resetUserScopedState()
            val restoreSnapshot = restoreStateStore.restoreSnapshot()
            val currentUserId = userStore.currentUser?.id
            val restoreDecision = ActiveWorkRestoreResolver.resolve(
                snapshot = restoreSnapshot,
                currentDeviceId = deviceId(),
                currentUserId = currentUserId
            )
            if (restoreDecision.shouldClearSnapshot) {
                restoreStateStore.saveSnapshot(RestoreSnapshot())
            }
            val localSession = restoreDecision.localSession
            var restorableWorkSession = restoreSnapshot.activeWorkSession.takeIf {
                restoreDecision.shouldRestorePersistedWork
            }
            var restoredPhase = restoreDecision.restoredPhase
                ?: restorableWorkSession?.destinationKind?.navigatingPhase
            workFlowStore.restoreActiveSession(
                session = restorableWorkSession,
                phase = restoredPhase,
                postPickupDestination = localSession?.postPickupDestination,
                localActiveExecutionSession = localSession
            )
            contactStore.loadLocations(force = true)
            appModeStore.syncDefaultMode(userStore.currentUser, contactStore.state.value.hasLocations)
            val today = LocalDate.now()
            if (appModeStore.state.value.isContactMode) {
                tasksStore.loadContactTasks(force = true)
                contactStore.loadMachinesForLocations(contactStore.state.value.locationsById.keys)
            } else {
                tasksStore.loadTasksForWeek(date = today, force = true)
            }
            restorableWorkSession?.activeTaskIds?.let { taskIds ->
                tasksStore.refreshTasks(taskIds)
            }
            locationStore.loadLocations(force = true)
            var inferredCandidate: InferredActiveWorkCandidate? = null
            if (restorableWorkSession == null && localSession != null) {
                inferredCandidate = inferActiveWorkCandidateFromStartedTasks()
                inferredCandidate?.let { candidate ->
                    restorableWorkSession = candidate.session
                    restoredPhase = candidate.phase
                    workFlowStore.restoreActiveSession(
                        session = candidate.session,
                        phase = candidate.phase,
                        postPickupDestination = restoreSnapshot.postPickupDestination,
                        localActiveExecutionSession = null
                    )
                }
            }
            val restoreTaskState = restorableWorkSession?.activeTaskIds?.let(::activeRouteTaskState)
            val restorableSessionWithActiveTasks = restorableWorkSession?.takeIf {
                restoreTaskState != ActiveRouteTaskState.AllFinal
            }
            if (restorableWorkSession != null && restoreTaskState == ActiveRouteTaskState.AllFinal) {
                workFlowStore.stopCurrentSession()
                navigationSessionStore.reset()
                refillDecisionStore.reset()
                restoreStateStore.saveSnapshot(RestoreSnapshot())
            }
            restorableSessionWithActiveTasks?.let { session ->
                val stop = inferredCandidate?.stop ?: TaskExecutionPlanner.buildStopFromSession(
                    session = session,
                    tasks = tasksStore.state.value.tasks,
                    locationsById = locationStore.state.value.locationsById
                )
                if (stop != null) {
                    val phase = restoredPhase ?: session.destinationKind.navigatingPhase
                    workFlowStore.restoreActiveRoute(session = session, phase = phase, stop = stop)
                    val restoredRoutePhase = workFlowStore.state.value.phase
                    bindCurrentLocalExecutionSession()
                    restoreActiveNavigationIfNeeded(
                        phase = restoredRoutePhase,
                        navigationStopId = restoreSnapshot.activeNavigationStopId,
                        stop = stop
                    )
                }
            }
            mainState.value = loadOrganizationContext(userStore.currentUser)
        } catch (error: Exception) {
            Log.e(LogTag, "Failed to sync user session scope", error)
            workFlowStore.resetUserScopedState()
            refillDecisionStore.reset()
            navigationSessionStore.reset()
            contactStore.resetUserScopedState()
            mainState.value = loadOrganizationContext(userStore.currentUser)
        } finally {
            sessionState.update { it.copy(isSyncingUserSession = false) }
        }
    }

    private fun inferActiveWorkCandidateFromStartedTasks(): InferredActiveWorkCandidate? {
        val currentUserId = userStore.currentUser?.id ?: return null
        val tasks = tasksStore.state.value.tasks
        val startedTask = tasks
            .filter { task ->
                task.assignee == currentUserId &&
                    task.startedAt != null &&
                    !TaskStateHelpers.isFinal(task.status)
            }
            .maxByOrNull { it.startedAt.orEmpty() }
            ?: return null

        val stop = if (startedTask.type == TaskType.MachinePickupInventory) {
            TaskExecutionPlanner.buildWarehousePickupStop(startedTask)
        } else {
            val locationId = startedTask.location ?: return null
            val activeLocationTasks = tasks.filter { task ->
                task.location == locationId &&
                    task.assignee == currentUserId &&
                    task.startedAt != null &&
                    !TaskStateHelpers.isFinal(task.status)
            }
            TaskExecutionPlanner.buildStop(
                locationId = locationId,
                tasks = activeLocationTasks,
                locationsById = locationStore.state.value.locationsById
            )
        } ?: return null

        val session = ActiveWorkSession(
            id = "restore:${stop.id}",
            title = stop.title,
            locationId = stop.targetLocationId.takeIf { stop.destinationKind != com.vendistri.operations.features.work.WorkDestinationKind.Warehouse },
            activeTaskIds = stop.tasks.map { it.id }.toSet(),
            addressStreetLine = stop.addressStreetLine,
            addressCityStateZipLine = stop.addressCityStateZipLine,
            coordinate = stop.coordinate,
            destinationKind = stop.destinationKind
        )
        return InferredActiveWorkCandidate(
            session = session,
            phase = stop.destinationKind.arrivedPhase,
            stop = stop
        )
    }

    fun signOut() {
        viewModelScope.launch {
            userStore.signOut()
            realtimeStore.disconnect()
            tasksStore.resetUserScopedState()
            locationStore.resetUserScopedState()
            contactStore.resetUserScopedState()
            notificationsStore.resetUserScopedState()
            workFlowStore.resetUserScopedState()
            refillDecisionStore.reset()
            navigationSessionStore.reset()
            refillInventoryStore.reset()
            pickupInventoryStore.reset()
            addStopStore.reset()
            appModeStore.reset()
            mainState.value = MainUiState()
        }
    }

    suspend fun requestPasswordReset(email: String): Boolean {
        return userStore.requestPasswordReset(email)
    }

    fun handleAppBecameActive() {
        realtimeStore.updateConnection(userStore.currentUser)
        viewModelScope.launch {
            userStore.refreshSubscriptionStatusIfAvailable(force = true)
            if (appModeStore.state.value.isContactMode) {
                tasksStore.loadContactTasks(force = true)
            } else {
                tasksStore.loadTasksForWeek(date = LocalDate.now(), force = true)
            }
        }
    }

    fun handleAppMovedToBackground() {
        realtimeStore.disconnect()
        viewModelScope.launch {
            restoreStateStore.saveSnapshot(
                RestoreSnapshot(
                    activeWorkSession = workFlowStore.state.value.activeSession,
                    activeWorkPhase = workFlowStore.state.value.phase.takeIf { phase ->
                        workFlowStore.state.value.activeSession != null && phase != WorkPhase.Idle
                    },
                    activeNavigationStopId = navigationSessionStore.state.value.activeStopId,
                    localActiveExecutionSession = workFlowStore.state.value.localActiveExecutionSession,
                    postPickupDestination = workFlowStore.state.value.postPickupDestination
                )
            )
        }
    }

    fun refreshSessionScope() {
        viewModelScope.launch {
            syncUserSessionScope()
        }
    }

    fun switchViewMode(mode: AppViewMode) {
        if (appModeStore.state.value.mode == mode) return
        viewModelScope.launch {
            sessionState.update { it.copy(isSyncingUserSession = true) }
            try {
                appModeStore.setMode(mode)
                mainState.update { it.copy(selectedTab = MainTab.Tasks, selectedMapStopId = null) }
                tasksStore.resetUserScopedState()
                when (mode) {
                    AppViewMode.Organization -> {
                        locationStore.loadLocations(force = true)
                        tasksStore.loadTasksForWeek(date = LocalDate.now(), force = true)
                    }
                    AppViewMode.LocationContact -> {
                        contactStore.loadLocations(force = true)
                        tasksStore.loadContactTasks(force = true)
                        contactStore.loadMachinesForLocations(contactStore.state.value.locationsById.keys)
                    }
                }
            } finally {
                sessionState.update { it.copy(isSyncingUserSession = false) }
            }
        }
    }

    fun loadContactLocationMachines(locationId: String) {
        viewModelScope.launch {
            contactStore.loadMachines(locationId)
        }
    }

    fun selectMainTab(tab: MainTab) {
        mainState.update { it.copy(selectedTab = tab) }
    }

    fun selectMapStop(stopId: String?) {
        mainState.update {
            val nextStopId = if (it.selectedMapStopId == stopId) null else stopId
            it.copy(selectedMapStopId = nextStopId, selectedTab = MainTab.Tasks)
        }
    }

    fun clearSelectedMapStop() {
        mainState.update { it.copy(selectedMapStopId = null) }
    }

    fun updateMapUserLocation(coordinate: LocationCoordinate) {
        currentMapCoordinate = coordinate
        navigationSessionStore.recordLocation(coordinate)?.let { distanceMiles ->
            workFlowStore.recordActiveExecutionDistanceSnapshot(distanceMiles)
        }
        refreshGoRoutePreviewIfNeeded()
    }

    fun updateNavigationProgress(progress: MapNavigationProgress) {
        val previousNavigationState = navigationSessionStore.state.value
        progress.isRerouting?.let(navigationSessionStore::setRerouting)
        navigationSessionStore.updateRouteProgress(
            instructionText = progress.instructionText,
            distanceRemainingMiles = progress.distanceRemainingMiles,
            durationRemainingSeconds = progress.durationRemainingSeconds,
            travelTimeTrafficLevel = progress.travelTimeTrafficLevel,
            trafficAlert = progress.trafficAlert,
            roadNameText = progress.roadNameText,
            currentSpeed = progress.currentSpeed,
            speedLimit = progress.speedLimit,
            currentInstruction = progress.currentInstruction,
            futureInstructionSteps = progress.futureInstructionSteps
        )?.let { distanceMiles ->
            workFlowStore.recordActiveExecutionDistanceSnapshot(distanceMiles)
        }
        // A completed reroute can replace the maneuver without producing the normal
        // voice event. Read the freshly installed instruction so the driver immediately
        // hears the complete new step.
        if (previousNavigationState.isRerouting && progress.isRerouting == false) {
            announceNextReroutedInstruction = true
        }
        if (announceNextReroutedInstruction && progress.currentInstruction != null) {
            announceNextReroutedInstruction = false
            speakCurrentNavigationStepIfSoundIsOn()
        }
    }

    fun presentGoSummary() {
        val plan = buildCurrentGoPlan()
        val selectedStopId = mainState.value.selectedMapStopId?.takeIf { stopId ->
            plan.stops.any { it.id == stopId }
        } ?: plan.suggestedStopId ?: plan.stops.firstOrNull()?.id
        workFlowStore.showSummary(plan = plan, selectedStopId = selectedStopId)
        refreshGoRoutePreview()
        refillDecisionStore.reset()
        mainState.update { it.copy(selectedTab = MainTab.Tasks, selectedMapStopId = null) }
    }

    fun selectGoStop(stopId: String) {
        workFlowStore.selectStop(stopId)
        refreshGoRoutePreview()
        refillDecisionStore.reset()
    }

    fun dismissGoSummary() {
        workFlowStore.stopCurrentSession()
        refillDecisionStore.reset()
    }

    fun dismissRefillDecision() {
        refillDecisionStore.reset()
        workFlowStore.returnToSummary()
        refreshGoRoutePreview()
    }

    fun startSelectedGoRoute() {
        val stop = workFlowStore.state.value.selectedStop ?: return
        viewModelScope.launch {
            workFlowStore.setLoading(true)
            try {
                val decision = RouteStartScopeResolver.decision(
                    stop = stop,
                    selectedTask = TaskExecutionResolver.currentExecutableTask(stop.tasks),
                    allTasks = tasksStore.state.value.tasks,
                    currentUserId = userStore.currentUser?.id
                ) ?: return@launch prepareGoRoute(stop, startImmediately = true)

                val defaultOption = decision.option(decision.defaultChoice)
                val laterRefillTasks = laterRefillStopTasks(
                    stop = stop,
                    excludingTaskIds = defaultOption.taskIds
                )
                val decisionWithLaterRefills = decision.copy(laterRefillTasks = laterRefillTasks)
                if (decision.requiresConfirmation || laterRefillTasks.isNotEmpty()) {
                    workFlowStore.showRouteStartScopeDecision(decisionWithLaterRefills)
                } else {
                    startGoRouteWithScopeOptionNow(stop, defaultOption)
                }
            } finally {
                workFlowStore.setLoading(false)
            }
        }
    }

    fun dismissGoRouteScopeChoice() {
        workFlowStore.clearRouteStartScopeDecision()
    }

    fun selectGoRouteScopeChoice(choice: RouteStartScopeChoice) {
        workFlowStore.selectRouteStartScopeChoice(choice)
    }

    fun toggleGoRouteLaterRefillTask(taskId: String) {
        workFlowStore.toggleLaterRefillTask(taskId)
    }

    private fun refreshGoRoutePreviewIfNeeded() {
        if (workFlowStore.state.value.phase == WorkPhase.Summary) {
            refreshGoRoutePreview()
        }
    }

    private fun refreshGoRoutePreview() {
        val stop = workFlowStore.state.value.selectedStop
        workFlowStore.setRoutePreview(
            routePreview = stop?.let {
                RoutePreviewEstimator.previewRoute(origin = currentMapCoordinate, destination = it)
            }
        )
    }

    fun confirmGoRouteScope(choice: RouteStartScopeChoice) {
        val decision = workFlowStore.state.value.routeStartScopeDecision ?: return
        val option = decision.option(choice)
        val laterTaskIds = workFlowStore.state.value.selectedLaterRefillTaskIds
            .intersect(decision.laterRefillTasks.map { it.id }.toSet())
        val optionWithLaterTasks = option.copy(
            claimTaskIds = option.claimTaskIds + laterTaskIds
        )
        val stop = workFlowStore.state.value.selectedStop ?: return
        workFlowStore.setLoading(true)
        workFlowStore.clearRouteStartScopeDecision()
        startGoRouteWithScopeOption(stop, optionWithLaterTasks)
    }

    private fun startGoRouteWithScopeOption(
        stop: GoStopPlan,
        option: RouteStartScopeOption
    ) {
        viewModelScope.launch {
            workFlowStore.setLoading(true)
            try {
                startGoRouteWithScopeOptionNow(stop, option)
            } finally {
                workFlowStore.setLoading(false)
            }
        }
    }

    private suspend fun startGoRouteWithScopeOptionNow(
        stop: GoStopPlan,
        option: RouteStartScopeOption
    ) {
        if (option.claimTaskIds.isNotEmpty()) {
            tasksStore.claimTasks(option.claimTaskIds.toList())
        }
        val hydratedStop = stop.copy(
            tasks = TaskExecutionResolver.hydratedTasks(
                stopTasks = stop.tasks,
                allTasks = tasksStore.state.value.tasks
            )
        )
        val scopedStop = TaskExecutionPlanner.scopedStop(hydratedStop, option.taskIds) ?: return
        prepareGoRoute(
            stop = scopedStop,
            startImmediately = true,
            additionalClaimTaskIds = option.claimTaskIds
        )
    }

    fun startWorkNavigation() {
        startCurrentWorkNavigation()
        refillDecisionStore.reset()
    }

    fun arriveAtWorkLocation() {
        val capturedDistance = maxOf(
            workFlowStore.state.value.activeExecution?.distanceMiles ?: 0.0,
            navigationSessionStore.state.value.traveledDistanceMiles
        )
        viewModelScope.launch {
            // Publish arrival and optimistically start/hydrate the destination owner in
            // one main-thread transaction. This prevents Compose from observing the
            // transient arrived execution with unstarted tasks and zero metrics.
            workFlowStore.arriveAtLocation(distanceMiles = capturedDistance)
            startCurrentMachineWorkIfNeeded()
            navigationSessionStore.reset()
        }
    }

    fun simulateCurrentWorkRouteForDebug() {
        if (!DebugRouteSimulationConfig.isEnabled) return
    }

    fun prepareCurrentInventoryTask() {
        val task = workFlowStore.state.value.activeExecution?.currentTaskId
            ?.let { tasksStore.state.value.tasksById[it] }
            ?: workFlowStore.state.value.activeExecution?.displayTasks
                ?.firstOrNull { it.id == workFlowStore.state.value.activeExecution?.currentTaskId }
            ?: return

        when (task.type) {
            TaskType.MachineRefill -> viewModelScope.launch {
                refillInventoryStore.prepare(task, allTasks = tasksStore.state.value.tasks)
            }
            TaskType.MachinePickupInventory -> pickupInventoryStore.prepare(task)
            else -> {
                refillInventoryStore.reset()
                pickupInventoryStore.reset()
            }
        }
    }

    fun updateRefillQuantity(itemId: String, value: String) {
        refillInventoryStore.updateRefilledQuantity(
            itemId = itemId,
            value = value,
            autoFillFinalStock = settingsStore.state.value.autoFillRefillFinalStock
        )
    }

    fun updateRefillFinalStock(itemId: String, value: String) {
        refillInventoryStore.updateFinalStock(itemId, value)
    }

    fun updatePickupQuantity(lineId: String, value: String) {
        pickupInventoryStore.updatePickedUpQuantity(lineId, value)
    }

    fun setCurrentRefillInventorySource(sourceMode: RefillInventorySourceMode, warehouseId: String?) {
        val task = workFlowStore.state.value.activeExecution?.currentTaskId
            ?.let { tasksStore.state.value.tasksById[it] }
            ?: return
        if (task.type != TaskType.MachineRefill) return
        if (refillInventoryStore.state.value.hasCompletedPickupCoverage) return

        viewModelScope.launch {
            refillInventoryStore.setSavingSource(true)
            try {
                val resolvedWarehouseId = warehouseId.takeIf { sourceMode == RefillInventorySourceMode.Warehouse }
                val didSave = tasksStore.setRefillInventorySource(
                    taskId = task.id,
                    warehouseId = resolvedWarehouseId,
                    sourceMode = sourceMode
                )
                val refreshedTask = tasksStore.state.value.tasksById[task.id] ?: task
                if (didSave) {
                    refillInventoryStore.prepare(
                        refreshedTask,
                        allTasks = tasksStore.state.value.tasks,
                        force = true
                    )
                    workFlowStore.rehydrateActiveExecution(tasksStore.state.value.tasks)
                } else {
                    refillInventoryStore.setError(tasksStore.state.value.lastMutationError)
                }
            } finally {
                refillInventoryStore.setSavingSource(false)
            }
        }
    }

    fun prepareTaskRefillInventory(task: VendiTask) {
        if (task.type != TaskType.MachineRefill || TaskStateHelpers.isFinal(task.status)) return
        viewModelScope.launch {
            refillInventoryStore.prepareTask(task, allTasks = tasksStore.state.value.tasks)
        }
    }

    fun updateTaskRefillQuantity(taskId: String, itemId: String, value: String) {
        refillInventoryStore.updateTaskRefilledQuantity(
            taskId = taskId,
            itemId = itemId,
            value = value,
            autoFillFinalStock = settingsStore.state.value.autoFillRefillFinalStock
        )
    }

    fun updateTaskRefillFinalStock(taskId: String, itemId: String, value: String) {
        refillInventoryStore.updateTaskFinalStock(taskId, itemId, value)
    }

    fun setTaskRefillInventorySource(task: VendiTask, sourceMode: RefillInventorySourceMode, warehouseId: String?) {
        if (task.type != TaskType.MachineRefill || TaskStateHelpers.isFinal(task.status)) return
        if (refillInventoryStore.taskStates.value[task.id]?.hasCompletedPickupCoverage == true) return

        viewModelScope.launch {
            refillInventoryStore.setTaskSavingSource(task.id, true)
            try {
                val resolvedWarehouseId = warehouseId.takeIf { sourceMode == RefillInventorySourceMode.Warehouse }
                val didSave = tasksStore.setRefillInventorySource(
                    taskId = task.id,
                    warehouseId = resolvedWarehouseId,
                    sourceMode = sourceMode
                )
                val refreshedTask = tasksStore.state.value.tasksById[task.id] ?: task
                if (didSave) {
                    refillInventoryStore.prepareTask(
                        refreshedTask,
                        allTasks = tasksStore.state.value.tasks,
                        force = true
                    )
                } else {
                    refillInventoryStore.setTaskError(task.id, tasksStore.state.value.lastMutationError)
                }
            } finally {
                refillInventoryStore.setTaskSavingSource(task.id, false)
            }
        }
    }

    fun completeTaskRefillInventory(task: VendiTask) {
        if (task.type != TaskType.MachineRefill || TaskStateHelpers.isFinal(task.status)) return
        viewModelScope.launch {
            val lines = refillInventoryStore.validatedTaskCompletionLines(task.id) ?: return@launch
            refillInventoryStore.setTaskCompleting(task.id, true)
            try {
                val didComplete = tasksStore.completeRefillTaskWithInventory(task.id, lines)
                if (didComplete) {
                    refillInventoryStore.resetTask(task.id)
                } else {
                    refillInventoryStore.setTaskError(task.id, tasksStore.state.value.lastMutationError)
                }
            } finally {
                refillInventoryStore.setTaskCompleting(task.id, false)
            }
        }
    }

    fun markCurrentWorkTaskDone() {
        val taskId = workFlowStore.state.value.activeExecution?.currentTaskId ?: return
        viewModelScope.launch {
            val task = tasksStore.state.value.tasksById[taskId]
                ?: workFlowStore.state.value.activeExecution?.displayTasks?.firstOrNull { it.id == taskId }
                ?: return@launch
            val distanceMiles = workFlowStore.distanceToSendForTask(task, TaskStatus.Done)
            val didUpdate = if (task.machine != null || task.type == TaskType.MachinePickupInventory) {
                tasksStore.updateMachineStatus(task, TaskStatus.Done, distanceMiles)
            } else {
                tasksStore.updateStatus(taskId, TaskStatus.Done)
            }
            if (!didUpdate) {
                workFlowStore.setError(tasksStore.state.value.lastMutationError ?: "Could not update task.")
                return@launch
            }
            workFlowStore.rehydrateActiveExecution(tasksStore.state.value.tasks)
            finalizeCompletedServiceTasksForActiveExecution()
            startCurrentMachineWorkIfNeeded()
        }
    }

    fun completeCurrentInventoryTask() {
        val task = workFlowStore.state.value.activeExecution?.currentTaskId
            ?.let { tasksStore.state.value.tasksById[it] }
            ?: return

        viewModelScope.launch {
            when (task.type) {
                TaskType.MachineRefill -> {
                    val lines = refillInventoryStore.validatedCompletionLines() ?: return@launch
                    val distanceMiles = workFlowStore.distanceToSendForTask(task, TaskStatus.Done)
                    refillInventoryStore.setCompleting(true)
                    try {
                        val didComplete = tasksStore.completeRefillTaskWithInventory(task.id, lines, distanceMiles)
                        if (didComplete) {
                            workFlowStore.rehydrateActiveExecution(tasksStore.state.value.tasks)
                            finalizeCompletedServiceTasksForActiveExecution()
                            startCurrentMachineWorkIfNeeded()
                            refillInventoryStore.reset()
                        } else {
                            refillInventoryStore.setError(tasksStore.state.value.lastMutationError)
                        }
                    } finally {
                        refillInventoryStore.setCompleting(false)
                    }
                }
                TaskType.MachinePickupInventory -> {
                    val lines = pickupInventoryStore.validatedCompletionLines() ?: return@launch
                    val distanceMiles = workFlowStore.distanceToSendForTask(task, TaskStatus.Done)
                    pickupInventoryStore.setCompleting(true)
                    try {
                        val didComplete = tasksStore.completePickupInventoryTask(task.id, lines, distanceMiles)
                        if (didComplete) {
                            workFlowStore.rehydrateActiveExecution(tasksStore.state.value.tasks)
                            finalizeCompletedServiceTasksForActiveExecution()
                            pickupInventoryStore.reset()
                        } else {
                            pickupInventoryStore.setError(tasksStore.state.value.lastMutationError)
                        }
                    } finally {
                        pickupInventoryStore.setCompleting(false)
                    }
                }
                else -> markCurrentWorkTaskDone()
            }
        }
    }

    fun advanceCurrentWorkTask() {
        workFlowStore.advanceToNextTask()
        viewModelScope.launch {
            startCurrentMachineWorkIfNeeded()
        }
    }

    fun stopCurrentWorkSession() {
        workFlowStore.stopCurrentSession()
        navigationSessionStore.reset()
        refillDecisionStore.reset()
    }

    fun finishCurrentWorkVisit() {
        val execution = workFlowStore.state.value.activeExecution ?: return
        viewModelScope.launch {
            val tasksById = tasksStore.state.value.tasksById
            val wrapperTask = execution.wrapperTaskId?.let { taskId ->
                tasksById[taskId] ?: execution.displayTasks.firstOrNull { it.id == taskId }
            }
            if (wrapperTask != null && !TaskStateHelpers.isFinal(wrapperTask.status)) {
                val distanceMiles = workFlowStore.distanceToSendForTask(wrapperTask, TaskStatus.Done)
                    ?: execution.distanceMiles.coerceAtLeast(0.0)
                val didUpdate = tasksStore.updateMachineStatus(wrapperTask, TaskStatus.Done, distanceMiles)
                if (!didUpdate) {
                    workFlowStore.setError(tasksStore.state.value.lastMutationError ?: "Could not finish visit.")
                    return@launch
                }
            }
            if (execution.destinationKind == com.vendistri.operations.features.work.WorkDestinationKind.Warehouse) {
                val pickupTask = execution.displayTasks
                    .firstOrNull { it.type == TaskType.MachinePickupInventory }
                    ?.let { task -> tasksStore.state.value.tasksById[task.id] ?: task }
                if (pickupTask != null) {
                    if (prepareRouteAfterPickupInventory(pickupTask)) {
                        return@launch
                    }
                    workFlowStore.setError("Could not prepare the route to the location.")
                    return@launch
                }
            }
            workFlowStore.stopCurrentSession()
            navigationSessionStore.reset()
            refillDecisionStore.reset()
        }
    }

    fun cancelCurrentWorkTasks(tasks: List<VendiTask>) {
        viewModelScope.launch {
            if (tasks.isEmpty()) return@launch
            for (task in tasks) {
                val didCancel = if (task.machine != null || task.type == TaskType.MachinePickupInventory) {
                    tasksStore.updateMachineStatus(
                        task = task,
                        status = TaskStatus.Cancelled,
                        distanceMiles = workFlowStore.distanceToSendForTask(task, TaskStatus.Cancelled)
                    )
                } else {
                    tasksStore.cancelTasks(listOf(task.id))
                }
                if (!didCancel) {
                    workFlowStore.setError(tasksStore.state.value.lastMutationError ?: "Could not cancel remaining tasks.")
                    return@launch
                }
            }
            workFlowStore.rehydrateActiveExecution(tasksStore.state.value.tasks)
            finalizeCompletedServiceTasksForActiveExecution()
            startCurrentMachineWorkIfNeeded()
        }
    }

    private suspend fun finalizeCompletedServiceTasksForActiveExecution() {
        val execution = workFlowStore.state.value.activeExecution ?: return
        val tasksById = tasksStore.state.value.tasksById
        val stopTasks = execution.displayTasks.map { task -> tasksById[task.id] ?: task }
        val serviceTasks = stopTasks.filter { task ->
            task.type == TaskType.MachineService &&
                !TaskStateHelpers.isFinal(task.status)
        }
        if (serviceTasks.isEmpty()) return

        var didFinalize = false
        for (serviceTask in serviceTasks) {
            val childTasks = stopTasks.filter { candidate ->
                candidate.id != serviceTask.id &&
                    candidate.type != TaskType.MachineService &&
                    candidate.serviceTaskId == serviceTask.id
            }
            if (childTasks.isEmpty() || childTasks.any { !TaskStateHelpers.isFinal(it.status) }) continue
            val didUpdate = tasksStore.updateMachineStatus(
                task = serviceTask,
                status = TaskStatus.Done,
                distanceMiles = workFlowStore.distanceToSendForTask(serviceTask, TaskStatus.Done)
            )
            if (!didUpdate) {
                workFlowStore.setError(tasksStore.state.value.lastMutationError ?: "Could not finalize service task.")
                return
            }
            didFinalize = true
        }
        if (didFinalize) {
            workFlowStore.rehydrateActiveExecution(tasksStore.state.value.tasks)
        }
    }

    private fun activeRouteTaskState(activeTaskIds: Set<String>): ActiveRouteTaskState {
        if (activeTaskIds.isEmpty()) return ActiveRouteTaskState.NotLoaded
        val matchingTasks = tasksStore.state.value.tasks.filter { task ->
            task.id in activeTaskIds
        }
        if (matchingTasks.isEmpty()) return ActiveRouteTaskState.NotLoaded
        return if (matchingTasks.any { task ->
            task.id in activeTaskIds && !TaskStateHelpers.isFinal(task.status)
        }) {
            ActiveRouteTaskState.HasActive
        } else {
            ActiveRouteTaskState.AllFinal
        }
    }

    fun uploadTaskPhotoConfirmation(
        taskId: String,
        fileName: String,
        mimeType: String,
        fileData: ByteArray
    ) {
        viewModelScope.launch {
            val didUpload = tasksStore.uploadPhotoConfirmation(taskId, fileName, mimeType, fileData)
            if (didUpload) {
                workFlowStore.setError(null)
                workFlowStore.rehydrateActiveExecution(tasksStore.state.value.tasks)
            } else {
                workFlowStore.setError(tasksStore.state.value.lastMutationError ?: "Could not upload photo.")
            }
        }
    }

    fun removeTaskPhotoConfirmation(taskId: String) {
        viewModelScope.launch {
            val didRemove = tasksStore.removePhotoConfirmation(taskId)
            if (didRemove) {
                workFlowStore.setError(null)
                workFlowStore.rehydrateActiveExecution(tasksStore.state.value.tasks)
            } else {
                workFlowStore.setError(tasksStore.state.value.lastMutationError ?: "Could not remove photo.")
            }
        }
    }

    fun showWorkError(message: String) {
        workFlowStore.setError(message)
    }

    fun selectRefillDecisionAction(action: RefillDecisionAction) {
        refillDecisionStore.selectAction(action)
        refreshRefillDecisionRoutePreviews()
    }

    fun selectRefillDecisionWarehouse(warehouseId: String) {
        refillDecisionStore.selectWarehouse(warehouseId)
        refreshRefillDecisionRoutePreviews()
        val taskIds = refillDecisionStore.state.value.plans.map { it.task.id }
        if (taskIds.isEmpty()) return
        if (refillDecisionStore.state.value.errorMessage != null &&
            refillDecisionStore.state.value.plans.all { it.suggestions.items.isEmpty() }
        ) {
            return
        }
        viewModelScope.launch {
            refillDecisionStore.setLoading(true)
            try {
                val context = tasksStore.loadRefillInventoryContext(taskIds, warehouseId)
                refillDecisionStore.replaceContext(context)
            } catch (error: Exception) {
                Log.e(
                    LogTag,
                    "go.refillDecision.warehouseContext.failed taskIds=${taskIds.joinToString(",")} warehouseId=$warehouseId",
                    error
                )
                if (refillDecisionStore.state.value.errorMessage == null) {
                    refillDecisionStore.setError("Could not load warehouse stock.")
                }
            } finally {
                refillDecisionStore.setLoading(false)
            }
        }
    }

    fun toggleRefillDecisionTask(taskId: String) {
        refillDecisionStore.toggleTaskInclusion(taskId)
    }

    fun applyRefillDecision() {
        val decision = refillDecisionStore.state.value
        if (!decision.isVisible) {
            startWorkNavigation()
            return
        }
        decision.currentStopSelectionRequiredMessage?.let { message ->
            refillDecisionStore.setError(message)
            return
        }
        viewModelScope.launch {
            refillDecisionStore.setApplying(true)
            try {
                val tasks = decision.includedTasks
                if (tasks.isEmpty() && decision.canContinueWithoutPickup) {
                    refillDecisionStore.reset()
                    startCurrentWorkNavigation()
                    return@launch
                }
                if (tasks.isEmpty()) {
                    refillDecisionStore.setError("Add at least one machine to continue.")
                    return@launch
                }
                when (decision.selectedAction) {
                    RefillDecisionAction.RouteToLocation -> {
                        refillDecisionStore.reset()
                        startCurrentWorkNavigation()
                    }
                    RefillDecisionAction.UseUntrackedStock -> {
                        if (setRefillSourceForTasks(tasks, RefillInventorySourceMode.Untracked, null)) {
                            refillDecisionStore.reset()
                            startCurrentWorkNavigation()
                        }
                    }
                    RefillDecisionAction.UseWarehouseStock -> {
                        val warehouseId = decision.selectedWarehouseId
                        if (warehouseId.isNullOrBlank()) {
                            refillDecisionStore.setError("Select a warehouse.")
                            return@launch
                        }
                        if (setRefillSourceForTasks(tasks, RefillInventorySourceMode.Warehouse, warehouseId)) {
                            refillDecisionStore.reset()
                            startCurrentWorkNavigation()
                        }
                    }
                    RefillDecisionAction.RouteToWarehouse -> {
                        val warehouseId = decision.selectedWarehouseId
                        if (warehouseId.isNullOrBlank()) {
                            refillDecisionStore.setError("Select a warehouse.")
                            return@launch
                        }
                        val destinationTask = decision.includedTasks.firstOrNull {
                            it.location == workFlowStore.state.value.selectedStop?.targetLocationId
                        } ?: decision.anchorTask ?: decision.includedTasks.firstOrNull()
                        val selectedSessionTasks = tasksStore.state.value.tasks
                            .filter { it.id in decision.selectedTaskIds }
                            .ifEmpty { decision.includedTasks }
                        val expandedSessionTaskIds = RouteStartScopeResolver.expandedTaskIds(
                            tasks = selectedSessionTasks,
                            allTasks = tasksStore.state.value.tasks
                        )
                        val postPickupSessionTaskIds = selectedSessionTasks.map { it.id }.toSet() +
                            expandedSessionTaskIds
                        workFlowStore.setPostPickupDestination(
                            refillTaskId = destinationTask?.id,
                            stopId = workFlowStore.state.value.selectedStop?.targetLocationId ?: destinationTask?.location,
                            sessionTaskIds = postPickupSessionTaskIds
                        )
                        val pickupTask = tasksStore.createPickupInventoryTaskForRefills(
                            taskIds = tasks.map { it.id },
                            warehouseId = warehouseId
                        )
                        val pickupStop = pickupTask?.let(TaskExecutionPlanner::buildWarehousePickupStop)
                        if (pickupStop == null) {
                            refillDecisionStore.setError(tasksStore.state.value.lastMutationError ?: "Could not create warehouse pickup.")
                            return@launch
                        }
                        refillDecisionStore.reset()
                        workFlowStore.prepareRoute(pickupStop)
                        bindCurrentLocalExecutionSession()
                        startCurrentWorkNavigation()
                    }
                }
            } finally {
                refillDecisionStore.setApplying(false)
            }
        }
    }

    private suspend fun setRefillSourceForTasks(
        tasks: List<VendiTask>,
        sourceMode: RefillInventorySourceMode,
        warehouseId: String?
    ): Boolean {
        for (task in tasks) {
            val didSave = tasksStore.setRefillInventorySource(
                taskId = task.id,
                warehouseId = warehouseId,
                sourceMode = sourceMode
            )
            if (!didSave) {
                refillDecisionStore.setError(tasksStore.state.value.lastMutationError ?: "Could not set inventory source.")
                return false
            }
        }
        return true
    }

    private suspend fun prepareRouteAfterPickupInventory(pickupTask: VendiTask): Boolean {
        if (pickupTask.type != TaskType.MachinePickupInventory) return false
        val allTasks = tasksStore.state.value.tasks
        val pickupTasks = listOf(pickupTask) + workFlowStore.state.value.activeExecution
            ?.displayTasks
            .orEmpty()
            .filter { it.type == TaskType.MachinePickupInventory && it.id != pickupTask.id }
        val destination = workFlowStore.state.value.postPickupDestination
        val route = PickupInventoryRouteContext.postPickupRoute(
            pickupTasks = pickupTasks,
            allTasks = allTasks,
            preferredRefillTaskId = destination?.refillTaskId,
            savedStopId = destination?.stopId,
            savedSessionTaskIds = destination?.sessionTaskIds.orEmpty()
        ) ?: return false
        val pickupScheduledDate = TaskScheduleDate.parse(pickupTask.scheduledFor)
        val scopedTasks = route.taskIds
            .takeIf { it.isNotEmpty() }
            ?.let { ids -> allTasks.filter { it.id in ids } }
            ?.takeIf { it.isNotEmpty() }
            ?: allTasks.filter { task ->
                task.location == route.stopId &&
                    pickupScheduledDate?.let { TaskScheduleDate.isSameDay(task.scheduledFor, it) } == true &&
                    !TaskStateHelpers.isFinal(task.status)
            }
        val stop = TaskExecutionPlanner.buildStop(
            locationId = route.stopId,
            tasks = scopedTasks,
            locationsById = locationStore.state.value.locationsById
        ) ?: return false

        workFlowStore.prepareRoute(stop)
        bindCurrentLocalExecutionSession()
        mainState.update { it.copy(selectedTab = MainTab.Tasks, selectedMapStopId = null) }
        val didStart = startCurrentWorkNavigationNow()
        if (didStart) {
            workFlowStore.clearPostPickupDestination()
        }
        return didStart
    }

    private suspend fun prepareGoRoute(
        stop: GoStopPlan,
        startImmediately: Boolean = false,
        additionalClaimTaskIds: Set<String> = emptySet()
    ) {
        val routeTaskIds = stop.tasks.map { it.id }.toSet()
        val selectedTaskIds = routeTaskIds + additionalClaimTaskIds
        val allTasks = tasksStore.state.value.tasks
        val currentUserId = userStore.currentUser?.id
        val routePreview = RoutePreviewEstimator.previewRoute(origin = currentMapCoordinate, destination = stop)
        val refillTasks = routeRefillTasksNeedingInventoryDecision(
            stop = stop,
            routeTaskIds = selectedTaskIds,
            allTasks = allTasks,
            currentUserId = currentUserId
        )
        if (refillTasks.isEmpty()) {
            refillDecisionStore.reset()
            workFlowStore.prepareRoute(stop)
            bindCurrentLocalExecutionSession()
        } else {
            val warehouseRoutePreview = refillWarehouseRoutePreview(
                warehouseId = null,
                warehouses = locationStore.state.value.warehouses
            )
            try {
                val context = tasksStore.loadRefillInventoryContext(refillTasks.map { it.id })
                val selectedWarehousePreview = refillWarehouseRoutePreview(
                    warehouseId = context.aggregateSuggestion.warehouseId,
                    warehouses = locationStore.state.value.warehouses
                ) ?: warehouseRoutePreview
                refillDecisionStore.prepare(
                    stop = stop,
                    plan = workFlowStore.state.value.goPlan,
                    allTasks = tasksStore.state.value.tasks,
                    currentUserId = currentUserId,
                    warehouses = locationStore.state.value.warehouses,
                    selectedTaskIds = selectedTaskIds,
                    context = context,
                    pendingStop = stop,
                    routePreview = routePreview,
                    warehouseRoutePreview = selectedWarehousePreview
                )
                refreshRefillDecisionRoutePreviews()
            } catch (error: Exception) {
                Log.e(
                    LogTag,
                    "go.refillDecision.context.failed taskIds=${refillTasks.joinToString(",") { it.id }} warehouseId=null",
                    error
                )
                refillDecisionStore.prepareFallback(
                    anchorTask = refillTasks.firstOrNull { it.id in routeTaskIds } ?: refillTasks.firstOrNull(),
                    tasks = stop.tasks.filter { it.type == TaskType.MachineRefill }.ifEmpty { refillTasks.take(1) },
                    warehouses = locationStore.state.value.warehouses,
                    pendingStop = stop,
                    routePreview = routePreview,
                    warehouseRoutePreview = warehouseRoutePreview,
                    errorMessage = "Could not load warehouse recommendations: ${error.message ?: error::class.java.simpleName}"
                )
                refreshRefillDecisionRoutePreviews()
                workFlowStore.setError(null)
            }
            workFlowStore.prepareRoute(stop)
            bindCurrentLocalExecutionSession()
        }
        if (startImmediately && !refillDecisionStore.state.value.isVisible) {
            startCurrentWorkNavigation()
        }
        mainState.update { it.copy(selectedTab = MainTab.Tasks, selectedMapStopId = null) }
    }

    private fun refillWarehouseRoutePreview(
        warehouseId: String?,
        warehouses: List<com.vendistri.operations.features.location.WarehouseOption>
    ): com.vendistri.operations.features.work.RoutePreview? {
        val coordinate = warehouseCoordinate(warehouseId, warehouses) ?: return null
        return RoutePreviewEstimator.previewRoute(origin = currentMapCoordinate, destination = coordinate)
    }

    private fun refreshRefillDecisionRoutePreviews() {
        val state = refillDecisionStore.state.value
        refillDecisionStore.setRoutePreviews(
            routePreview = selectedRefillDecisionRoutePreview(
                action = state.selectedAction,
                stop = state.pendingStop,
                warehouseId = state.selectedWarehouseId,
                warehouses = locationStore.state.value.warehouses
            ),
            warehouseRoutePreview = refillWarehouseRoutePreview(
                warehouseId = state.selectedWarehouseId,
                warehouses = locationStore.state.value.warehouses
            )
        )
    }

    private fun selectedRefillDecisionRoutePreview(
        action: RefillDecisionAction,
        stop: GoStopPlan?,
        warehouseId: String?,
        warehouses: List<com.vendistri.operations.features.location.WarehouseOption>
    ): com.vendistri.operations.features.work.RoutePreview? {
        val locationCoordinate = stop?.coordinate ?: return null
        val destinations = when (action) {
            RefillDecisionAction.RouteToLocation,
            RefillDecisionAction.UseWarehouseStock,
            RefillDecisionAction.UseUntrackedStock -> listOf(locationCoordinate)
            RefillDecisionAction.RouteToWarehouse -> {
                val warehouseCoordinate = warehouseCoordinate(warehouseId, warehouses) ?: return null
                listOf(warehouseCoordinate, locationCoordinate)
            }
        }
        return RoutePreviewEstimator.previewRoute(origin = currentMapCoordinate, destinations = destinations)
    }

    private fun warehouseCoordinate(
        warehouseId: String?,
        warehouses: List<com.vendistri.operations.features.location.WarehouseOption>
    ): LocationCoordinate? {
        val warehouse = warehouseId
            ?.let { id -> warehouses.firstOrNull { it.id == id } }
            ?: warehouses.firstOrNull()
        val address = warehouse?.address ?: return null
        val latitude = address.latitude
        val longitude = address.longitude
        return if (latitude != null && longitude != null) {
            LocationCoordinate(latitude = latitude, longitude = longitude)
        } else {
            null
        }
    }

    private fun routeRefillTasksNeedingInventoryDecision(
        stop: GoStopPlan,
        routeTaskIds: Set<String>,
        allTasks: List<VendiTask>,
        currentUserId: String?
    ): List<VendiTask> {
        val routeTasks = allTasks.filter { it.id in routeTaskIds }
        val routeStop = stop.copy(tasks = routeTasks.ifEmpty { stop.tasks })
        val anchorTask = RouteRefillInventoryDecision.anchorTask(
            stop = routeStop,
            allTasks = allTasks,
            currentUserId = currentUserId,
            bypassedTaskIds = emptySet(),
            selectedTaskIds = routeTaskIds
        ) ?: return emptyList()
        return RouteRefillInventoryDecision.eligibleTasks(
            plan = workFlowStore.state.value.goPlan,
            anchorTask = anchorTask,
            allTasks = allTasks,
            currentUserId = currentUserId,
            bypassedTaskIds = emptySet(),
            selectedTaskIds = routeTaskIds
        )
    }

    private fun laterRefillStopTasks(
        stop: GoStopPlan,
        excludingTaskIds: Set<String>
    ): List<VendiTask> {
        val plan = workFlowStore.state.value.goPlan ?: return emptyList()
        val selectedLocationId = stop.targetLocationId
        val currentUserId = userStore.currentUser?.id
        val selectedDate = TaskScheduleDate.parse(stop.tasks.firstOrNull()?.scheduledFor) ?: return emptyList()
        val seenTaskIds = mutableSetOf<String>()
        return plan.tasks.mapNotNull { planTask ->
            val liveTask = tasksStore.state.value.tasksById[planTask.id] ?: planTask
            if (!seenTaskIds.add(liveTask.id)) return@mapNotNull null
            if (liveTask.type != TaskType.MachineRefill) return@mapNotNull null
            if (liveTask.id in excludingTaskIds) return@mapNotNull null
            if (liveTask.location == selectedLocationId) return@mapNotNull null
            if (!TaskScheduleDate.isSameDay(liveTask.scheduledFor, selectedDate)) return@mapNotNull null
            if (TaskStateHelpers.isFinal(liveTask.status)) return@mapNotNull null
            if (liveTask.inventoryCompletion != null) return@mapNotNull null
            if (!liveTask.assignee.isNullOrBlank() && liveTask.status != TaskStatus.Unassigned) return@mapNotNull null
            if (liveTask.assignee == currentUserId && liveTask.status != TaskStatus.Unassigned) return@mapNotNull null
            liveTask
        }.sortedWith(
            compareBy<VendiTask> { it.locationName ?: "" }
                .thenBy { it.machineName ?: it.id }
        )
    }

    fun prepareAddStop() {
        if (!canManageScheduledTasks()) return
        viewModelScope.launch {
            addStopStore.prepare(locationStore.state.value.locationsById)
        }
    }

    fun resetAddStop() {
        if (!canManageScheduledTasks()) return
        addStopStore.reset()
    }

    fun setAddStopDate(date: LocalDate) {
        if (!canManageScheduledTasks()) return
        addStopStore.setDate(date)
    }

    fun setAddStopNotes(notes: String) {
        if (!canManageScheduledTasks()) return
        addStopStore.setNotes(notes)
    }

    fun toggleAddStopLocation(locationId: String) {
        if (!canManageScheduledTasks()) return
        addStopStore.toggleLocation(locationId, locationStore.state.value.locationsById)
    }

    fun toggleAddStopMachine(machineId: String) {
        if (!canManageScheduledTasks()) return
        addStopStore.toggleMachine(machineId)
    }

    fun toggleAddStopTaskType(machineId: String, type: TaskType) {
        if (!canManageScheduledTasks()) return
        addStopStore.toggleTaskType(machineId, type)
    }

    fun setAddStopAssignee(machineId: String, assigneeId: String?) {
        if (!canManageScheduledTasks()) return
        addStopStore.setAssignee(machineId, assigneeId)
    }

    fun saveAddStop(onSaved: () -> Unit) {
        if (!canManageScheduledTasks()) return
        viewModelScope.launch {
            if (!billingAllowsWrite()) return@launch
            if (addStopStore.save()) {
                onSaved()
            }
        }
    }

    fun confirmAddStopPrecheck(onSaved: () -> Unit) {
        if (!canManageScheduledTasks()) return
        viewModelScope.launch {
            if (!billingAllowsWrite()) return@launch
            if (addStopStore.confirmPrecheckAndSave()) {
                onSaved()
            }
        }
    }

    fun confirmAddStopRescheduleExisting(onSaved: () -> Unit) {
        if (!canManageScheduledTasks()) return
        viewModelScope.launch {
            if (!billingAllowsWrite()) return@launch
            if (addStopStore.confirmRescheduleExistingAndSave()) {
                onSaved()
            }
        }
    }

    fun dismissAddStopPrecheckAlert() {
        if (!canManageScheduledTasks()) return
        addStopStore.dismissPrecheckAlert()
    }

    private fun canManageScheduledTasks(): Boolean {
        return TaskPermissions.canManageScheduledTasks(userStore.currentUser)
    }

    private suspend fun billingAllowsWrite(): Boolean {
        userStore.refreshSubscriptionStatusIfAvailable()
        if (!userStore.isBillingBlocked) return true
        userStore.presentPaymentRequired(NetworkConfig.appWebUrl)
        return false
    }

    fun clearPaymentRequired() {
        userStore.clearPaymentRequired()
    }

    fun presentTaskAction(action: TaskActionKind, task: VendiTask) {
        if (userStore.isBillingBlocked) {
            userStore.presentPaymentRequired(NetworkConfig.appWebUrl)
            return
        }
        if (taskActionStore.state.value.isSaving) return
        if (isTaskMutationPending(listOf(task.id))) return
        if (!canManageScheduledTasks()) return
        taskActionStore.present(action, listOf(task))
        if (action == TaskActionKind.Reassign) {
            viewModelScope.launch { taskActionStore.loadAssigneesIfNeeded() }
        }
        if (action == TaskActionKind.Reschedule) {
            viewModelScope.launch { taskActionStore.loadNextServiceCadenceDateIfNeeded() }
        }
    }

    fun presentBulkTaskAction(action: TaskActionKind, tasks: List<VendiTask>) {
        if (userStore.isBillingBlocked) {
            userStore.presentPaymentRequired(NetworkConfig.appWebUrl)
            return
        }
        if (taskActionStore.state.value.isSaving) return
        if (!canManageScheduledTasks()) return
        val selectedTasks = tasks.distinctBy { it.id }
        if (selectedTasks.isEmpty()) return
        if (isTaskMutationPending(selectedTasks.map { it.id })) return
        taskActionStore.present(action, selectedTasks)
        if (action == TaskActionKind.Reassign) {
            viewModelScope.launch { taskActionStore.loadAssigneesIfNeeded() }
        }
        if (action == TaskActionKind.Reschedule) {
            viewModelScope.launch { taskActionStore.loadNextServiceCadenceDateIfNeeded() }
        }
    }

    fun claimTask(task: VendiTask) {
        claimTasks(listOf(task))
    }

    fun claimTasks(tasks: List<VendiTask>) {
        if (userStore.isBillingBlocked) {
            userStore.presentPaymentRequired(NetworkConfig.appWebUrl)
            return
        }
        val taskIds = tasks.filter(::canClaimTask).map { it.id }.distinct().filter { it.isNotBlank() }
        if (taskIds.isEmpty()) return
        if (isTaskMutationPending(taskIds)) return
        viewModelScope.launch {
            tasksStore.claimTasks(taskIds)
        }
    }

    fun markTasksDone(tasks: List<VendiTask>) {
        if (userStore.isBillingBlocked) {
            userStore.presentPaymentRequired(NetworkConfig.appWebUrl)
            return
        }
        val taskIds = tasks.map { it.id }.distinct().filter { it.isNotBlank() }
        if (taskIds.isEmpty()) return
        if (isTaskMutationPending(taskIds)) return
        if (!canChangeTaskStatus(taskIds)) return
        viewModelScope.launch {
            if (workFlowStore.state.value.phase.isAtDestination &&
                taskIds.all { it in workFlowStore.state.value.activeExecution?.taskIds.orEmpty() }
            ) {
                val currentTaskId = workFlowStore.state.value.activeExecution?.currentTaskId
                val currentTask = currentTaskId?.let { taskId ->
                    tasksStore.state.value.tasksById[taskId]
                        ?: workFlowStore.state.value.activeExecution?.displayTasks?.firstOrNull { it.id == taskId }
                }
                if (currentTask != null && currentTask.id in taskIds) {
                    when (currentTask.type) {
                        TaskType.MachineRefill -> {
                            if (refillInventoryStore.validatedCompletionLines() == null) {
                                workFlowStore.setError(refillInventoryStore.state.value.errorMessage ?: "Complete refill inventory first.")
                                return@launch
                            }
                        }
                        TaskType.MachinePickupInventory -> {
                            if (pickupInventoryStore.validatedCompletionLines() == null) {
                                workFlowStore.setError(pickupInventoryStore.state.value.errorMessage ?: "Complete pickup inventory first.")
                                return@launch
                            }
                        }
                        else -> Unit
                    }
                }
                val distancesByTaskId = tasks.associate { task ->
                    task.id to workFlowStore.distanceToSendForTask(task, TaskStatus.Done)
                }
                val didUpdate = tasksStore.markActiveExecutionTasksDone(tasks, distancesByTaskId)
                if (!didUpdate) {
                    workFlowStore.setError(tasksStore.state.value.lastMutationError ?: "Could not update task.")
                    return@launch
                }
                workFlowStore.setError(null)
                workFlowStore.rehydrateActiveExecution(tasksStore.state.value.tasks)
                finalizeCompletedServiceTasksForActiveExecution()
            } else {
                tasksStore.bulkUpdateStatus(taskIds, TaskStatus.Done)
            }
        }
    }

    fun updateTaskStatus(task: VendiTask, status: TaskStatus) {
        if (userStore.isBillingBlocked) {
            userStore.presentPaymentRequired(NetworkConfig.appWebUrl)
            return
        }
        if (task.status == status) return
        if (isTaskMutationPending(listOf(task.id))) return
        if (!canChangeTaskStatus(listOf(task.id))) return
        viewModelScope.launch {
            val didUpdate = tasksStore.updateStatus(task.id, status)
            if (!didUpdate && workFlowStore.state.value.phase.isAtDestination) {
                workFlowStore.setError(tasksStore.state.value.lastMutationError ?: "Could not update task.")
            } else if (didUpdate && workFlowStore.state.value.phase.isAtDestination) {
                workFlowStore.setError(null)
            }
        }
    }

    suspend fun applySharedNotes(taskIds: List<String>, notes: String?): Boolean {
        if (!billingAllowsWrite()) return false
        val normalizedTaskIds = taskIds.distinct().filter { it.isNotBlank() }
        if (normalizedTaskIds.isEmpty()) return true
        if (isTaskMutationPending(normalizedTaskIds)) return false
        return tasksStore.applySharedNotes(normalizedTaskIds, notes, trackTaskLoading = false)
    }

    fun updateCollectionFinancials(task: VendiTask, draft: CollectionFinancialDraft) {
        if (userStore.isBillingBlocked) {
            userStore.presentPaymentRequired(NetworkConfig.appWebUrl)
            return
        }
        if (isTaskMutationPending(listOf(task.id))) return
        if (!canChangeTaskStatus(listOf(task.id))) return
        viewModelScope.launch {
            tasksStore.updateCollectionFinancials(
                task = task,
                gross = draft.gross,
                grossCash = draft.grossCash,
                grossCard = draft.grossCard,
                refunds = draft.refunds,
                commission = draft.commission,
                commissionPaymentType = draft.commissionPaymentType,
                net = draft.net,
                includeRefundsInCommission = draft.includeRefundsInCommission
            )
        }
    }

    fun updateRefundFinancials(task: VendiTask, refunds: Double) {
        if (userStore.isBillingBlocked) {
            userStore.presentPaymentRequired(NetworkConfig.appWebUrl)
            return
        }
        if (isTaskMutationPending(listOf(task.id))) return
        if (!canChangeTaskStatus(listOf(task.id))) return
        viewModelScope.launch {
            tasksStore.updateRefundFinancials(task, refunds)
        }
    }

    fun dismissTaskAction() {
        taskActionStore.dismiss()
    }

    fun confirmTaskAction() {
        if (taskActionStore.state.value.isSaving) return
        viewModelScope.launch {
            if (!billingAllowsWrite()) return@launch
            taskActionStore.confirmCurrentAction()
        }
    }

    fun selectTaskActionAssignee(taskId: String, assigneeId: String?) {
        if (taskActionStore.state.value.isSaving) return
        taskActionStore.selectTaskAssignee(taskId, assigneeId)
    }

    fun selectTaskActionDate(value: String) {
        if (taskActionStore.state.value.isSaving) return
        taskActionStore.selectDate(value)
    }

    fun selectQuickTaskActionDate(date: LocalDate) {
        if (taskActionStore.state.value.isSaving) return
        taskActionStore.selectQuickRescheduleDate(date)
    }

    fun toggleTaskActionSelection(taskId: String) {
        if (taskActionStore.state.value.isSaving) return
        taskActionStore.toggleTask(taskId)
    }

    private fun isTaskMutationPending(taskIds: List<String>): Boolean {
        val pending = tasksStore.state.value.pendingMutationTaskIds
        return taskIds.any { it in pending }
    }

    private fun canClaimTask(task: VendiTask): Boolean {
        val user = userStore.currentUser ?: return false
        if (!TaskPermissions.canAssignToSelf(user) || !TaskRowActionPolicy.canClaim(task)) return false
        if (TaskPermissions.canManageScheduledTasks(user)) return true
        return TaskPermissions.canIncludeClaimableUnassignedTasks(
            user = user,
            operatorTaskClaimingEnabled = mainState.value.operatorTaskClaimingEnabled
        )
    }

    private fun canChangeTaskStatus(taskIds: List<String>): Boolean {
        val context = taskActionContext(taskIds)
        return TaskPermissions.canChangeTaskStatus(userStore.currentUser, context)
    }

    private fun taskActionContext(taskIds: List<String>): TaskActionContext {
        val workState = workFlowStore.state.value
        val activeTaskIds = workState.activeExecution?.taskIds.orEmpty()
        return if (workState.phase.isAtDestination && taskIds.isNotEmpty() && taskIds.all { it in activeTaskIds }) {
            TaskActionContext.ActiveExecution
        } else {
            TaskActionContext.Scheduled
        }
    }

    private fun bindCurrentLocalExecutionSession() {
        workFlowStore.bindLocalActiveExecutionSession(
            deviceId = deviceId(),
            userId = userStore.currentUser?.id.orEmpty()
        )
    }

    private fun startCurrentWorkNavigation() {
        viewModelScope.launch {
            startCurrentWorkNavigationNow()
        }
    }

    private suspend fun startCurrentWorkNavigationNow(): Boolean {
        if (!startCurrentRouteOwnerTaskIfNeeded()) return false
        workFlowStore.startNavigation()
        val audioPreference = settingsStore.state.value.navigationAudioPreference
        val instructionText = currentNavigationInstructionText()
        navigationSessionStore.start(
            stopId = workFlowStore.state.value.selectedStop?.id
                ?: workFlowStore.state.value.activeSession?.id,
            audioPreference = audioPreference,
            currentInstructionText = instructionText,
            traveledDistanceMiles = workFlowStore.state.value.activeExecution?.distanceMiles
                ?.coerceAtLeast(0.0)
                ?: 0.0
        )
        if (audioPreference == NavigationAudioPreference.Sound) {
            speakNavigationInstructionIfNeeded(instructionText)
        }
        bindCurrentLocalExecutionSession()
        return true
    }

    private fun restoreActiveNavigationIfNeeded(
        phase: WorkPhase,
        navigationStopId: String?,
        stop: GoStopPlan
    ) {
        if (!phase.isNavigating) return
        val instructionText = navigationInstructionText(stop)
        navigationSessionStore.start(
            stopId = navigationStopId ?: stop.id,
            audioPreference = settingsStore.state.value.navigationAudioPreference,
            currentInstructionText = instructionText,
            traveledDistanceMiles = restoredActiveExecutionDistanceMiles()
        )
    }

    private fun restoredActiveExecutionDistanceMiles(): Double {
        val workState = workFlowStore.state.value
        return maxOf(
            workState.activeExecution?.distanceMiles ?: 0.0,
            workState.localActiveExecutionSession?.distanceMiles ?: 0.0,
            navigationSessionStore.state.value.traveledDistanceMiles
        )
    }

    private suspend fun startCurrentMachineWorkIfNeeded() {
        machineStartMutex.withLock {
            val workState = workFlowStore.state.value
            if (!workState.phase.isAtDestination) return@withLock
            val coordinate = currentMapCoordinate
                ?: workState.selectedStop?.coordinate
                ?: workState.activeSession?.coordinate
            if (coordinate == null) {
                workFlowStore.setError("Waiting for your location before starting the next machine.")
                return@withLock
            }
            val tasks = workFlowStore.currentMachineStartTasks()
            if (tasks.isEmpty()) return@withLock
            val startedAt = Instant.now()
            workFlowStore.recordTaskStartBaselines(tasks.map { it.id })
            tasks.forEach { task ->
                tasksStore.markMachineTaskStartedOptimistically(task, startedAt = startedAt)
            }
            workFlowStore.setError(null)
            workFlowStore.rehydrateActiveExecution(tasksStore.state.value.tasks)
            bindCurrentLocalExecutionSession()
            syncCurrentMachineWorkStarts(tasks = tasks, coordinate = coordinate, startedAt = startedAt)
        }
    }

    private suspend fun syncCurrentMachineWorkStarts(
        tasks: List<VendiTask>,
        coordinate: LocationCoordinate,
        startedAt: Instant
    ) {
        tasks.forEach { task ->
            val didStart = tasksStore.startMachineTask(task, coordinate, startedAt = startedAt)
            if (!didStart) {
                workFlowStore.setError(tasksStore.state.value.lastMutationError ?: "Could not start task.")
                return
            }
        }
        workFlowStore.setError(null)
        workFlowStore.rehydrateActiveExecution(tasksStore.state.value.tasks)
        bindCurrentLocalExecutionSession()
    }

    private suspend fun startCurrentRouteOwnerTaskIfNeeded(): Boolean {
        val coordinate = currentMapCoordinate ?: workFlowStore.state.value.selectedStop?.coordinate
        if (coordinate == null) {
            workFlowStore.setError("Waiting for your location.")
            return false
        }
        val tasks = workFlowStore.currentRouteStartTasks()
        if (tasks.isEmpty()) return true
        val routeStartedAt = Instant.now()
        workFlowStore.recordTaskStartBaselines(tasks.map { it.id }, baselineDistanceMiles = 0.0)
        tasks.forEach { task ->
            tasksStore.markMachineTaskStartedOptimistically(task, startedAt = routeStartedAt)
        }
        workFlowStore.setError(null)
        workFlowStore.rehydrateActiveExecution(tasksStore.state.value.tasks)
        bindCurrentLocalExecutionSession()
        viewModelScope.launch {
            syncRouteOwnerTaskStarts(tasks = tasks, coordinate = coordinate, startedAt = routeStartedAt)
        }
        return true
    }

    private suspend fun syncRouteOwnerTaskStarts(
        tasks: List<VendiTask>,
        coordinate: LocationCoordinate,
        startedAt: Instant
    ) {
        tasks.forEach { task ->
            val didStart = tasksStore.startMachineTask(task, coordinate, startedAt = startedAt)
            if (!didStart) {
                workFlowStore.setError(tasksStore.state.value.lastMutationError ?: "Could not start task.")
                return
            }
        }
        workFlowStore.setError(null)
        workFlowStore.rehydrateActiveExecution(tasksStore.state.value.tasks)
        bindCurrentLocalExecutionSession()
    }

    private fun deviceId(): String {
        return Settings.Secure.getString(
            getApplication<Application>().contentResolver,
            Settings.Secure.ANDROID_ID
        ).orEmpty()
    }

    fun markAllNotificationsRead() {
        notificationsStore.markAllRead()
    }

    fun markNotificationRead(itemId: String) {
        notificationsStore.markRead(itemId)
    }

    fun selectNotification(item: AppNotificationItem) {
        notificationsStore.markRead(item.id)
    }

    fun loadSummaryWeek(date: LocalDate) {
        viewModelScope.launch {
            tasksStore.loadTasksForWeek(date = date, force = false)
        }
    }

    fun loadTasksPanelDate(date: LocalDate) {
        viewModelScope.launch {
            tasksStore.loadTasksForDate(date = date, force = false)
        }
    }

    fun setAutoCalcCommission(enabled: Boolean) {
        settingsStore.setAutoCalcCommission(enabled)
    }

    fun setAutoFillRefillFinalStock(enabled: Boolean) {
        settingsStore.setAutoFillRefillFinalStock(enabled)
    }

    fun setAppearancePreference(preference: AppAppearancePreference) {
        settingsStore.setAppearancePreference(preference)
    }

    fun setNavigationAudioPreference(preference: NavigationAudioPreference) {
        settingsStore.setNavigationAudioPreference(preference)
    }

    fun setCurrentNavigationAudioPreference(preference: NavigationAudioPreference) {
        val change = navigationSessionStore.applyAudioPreference(preference)
        if (change.shouldAnnounceCurrentInstruction) {
            speakCurrentNavigationStepIfSoundIsOn()
        }
    }

    fun setTimeFormatPreference(preference: TimeFormatPreference) {
        settingsStore.setTimeFormatPreference(preference)
    }

    override fun onCleared() {
        navigationVoiceSpeaker.shutdown()
        super.onCleared()
    }

    private fun currentNavigationInstructionText(): String? {
        val state = workFlowStore.state.value
        val stop = state.selectedStop
        if (stop != null) return navigationInstructionText(stop)
        val session = state.activeSession ?: return null
        val destination = when (session.destinationKind) {
            com.vendistri.operations.features.work.WorkDestinationKind.Warehouse -> "warehouse"
            com.vendistri.operations.features.work.WorkDestinationKind.Location -> "location"
        }
        return "Head to the $destination, ${session.title}."
    }

    private fun navigationInstructionText(stop: GoStopPlan): String {
        val destination = when (stop.destinationKind) {
            com.vendistri.operations.features.work.WorkDestinationKind.Warehouse -> "warehouse"
            com.vendistri.operations.features.work.WorkDestinationKind.Location -> "location"
        }
        return "Head to the $destination, ${stop.title}."
    }

    private fun speakNavigationInstructionIfNeeded(instructionText: String?) {
        val cleanText = instructionText?.trim().orEmpty()
        if (cleanText.isBlank()) return
        navigationVoiceSpeaker.speak(cleanText)
    }

    private fun speakCurrentNavigationStepIfSoundIsOn() {
        val state = navigationSessionStore.state.value
        if (state.audioPreference != NavigationAudioPreference.Sound) return
        val step = state.currentInstruction
        val fullStep = listOfNotNull(
            step?.primaryText?.trim()?.takeIf { it.isNotBlank() },
            step?.secondaryText?.trim()?.takeIf { it.isNotBlank() }
        ).distinct().joinToString(". ")
        speakNavigationInstructionIfNeeded(fullStep.ifBlank { state.currentInstructionText.orEmpty() })
    }
}

private data class AppSessionState(
    val isSyncingUserSession: Boolean = false
)

data class VendistriUiState(
    val auth: AuthUiState = AuthUiState(),
    val tasks: TasksUiState = TasksUiState(),
    val taskActions: TaskActionState = TaskActionState(),
    val addStop: AddStopUiState = AddStopUiState(),
    val locations: LocationUiState = LocationUiState(),
    val contact: ContactUiState = ContactUiState(),
    val appMode: AppModeUiState = AppModeUiState(),
    val work: WorkUiState = WorkUiState(),
    val refillDecision: RefillDecisionUiState = RefillDecisionUiState(),
    val refillInventory: RefillInventoryUiState = RefillInventoryUiState(),
    val taskRefillInventory: Map<String, RefillInventoryUiState> = emptyMap(),
    val pickupInventory: PickupInventoryUiState = PickupInventoryUiState(),
    val navigation: NavigationSessionState = NavigationSessionState(),
    val notifications: NotificationsState = NotificationsState(),
    val settings: AppSettingsState = AppSettingsState(),
    val main: MainUiState = MainUiState(),
    val isSyncingUserSession: Boolean = false
) {
    val isAuthenticated: Boolean = auth.user != null

    val visibleTasks: List<VendiTask>
        get() {
            if (appMode.isContactMode) {
                return ContactVisibilityRules.visibleTasks(
                    tasks = tasks.tasks,
                    locationIds = contact.locationsById.keys
                )
            }
            val user = auth.user ?: return emptyList()
            if (TaskPermissions.canViewAllScheduledTasks(user)) return tasks.tasks
            return tasks.tasks.filter { it.assignee == user.id }
        }

    private val mapVisibleTasks: List<VendiTask>
        get() {
            val user = auth.user ?: return emptyList()
            if (TaskPermissions.canViewAllScheduledTasks(user)) return tasks.tasks
            val assignedTasks = visibleTasks
            val claimableTasks = if (TaskPermissions.canIncludeClaimableUnassignedTasks(user, main.operatorTaskClaimingEnabled)) {
                tasks.tasks.filter { it.status == TaskStatus.Unassigned }
            } else {
                emptyList()
            }
            return (assignedTasks + claimableTasks).distinctBy { it.id }
        }

    val locationStops: List<LocationStop>
        get() = if (appMode.isContactMode) {
            LocationStopsBuilder.buildContactStops(
                locations = contact.sortedLocations,
                tasks = visibleTasks
            )
        } else {
            LocationStopsBuilder.buildStops(
                tasks = mapVisibleTasks,
                locationsById = locations.locationsById,
                currentUserId = auth.user?.id
            )
        }
}
