package com.vendistri.operations.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vendistri.operations.components.VendistriBootstrapView
import com.vendistri.operations.components.InAppBrowserView
import com.vendistri.operations.components.sheets.VendistriActionSheet
import com.vendistri.operations.components.sheets.rememberVendistriActionSheetState
import com.vendistri.operations.features.auth.AuthView
import com.vendistri.operations.features.main.MainView
import com.vendistri.operations.features.tasks.actions.TaskActionSheet
import com.vendistri.operations.features.tasks.TaskCardActions
import com.vendistri.operations.features.tasks.RefillTaskEditorActions
import com.vendistri.operations.features.tasks.VendiTask
import com.vendistri.operations.features.work.isAtDestination
import com.vendistri.operations.features.work.isNavigating
import com.vendistri.operations.ui.theme.VendistriTheme
import java.io.File

private const val MaxTaskPhotoUploadBytes = 10 * 1024 * 1024

@Composable
fun VendistriApp(appState: AppState = viewModel()) {
    val uiState by appState.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val systemIsDark = isSystemInDarkTheme()
    val darkTheme = uiState.settings.appearancePreference.resolvesDarkTheme(systemIsDark)
    var isMapReady by remember(uiState.isAuthenticated) { mutableStateOf(false) }
    var pendingPhotoTask by remember(uiState.isAuthenticated) { mutableStateOf<VendiTask?>(null) }
    var pendingCameraUri by remember(uiState.isAuthenticated) { mutableStateOf<Uri?>(null) }
    var showPhotoSourceDialog by remember(uiState.isAuthenticated) { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(uiState.work.phase, uiState.work.activeSession?.id) {
        val needsLiveStatus = uiState.work.activeSession != null &&
            (uiState.work.phase.isNavigating || uiState.work.phase.isAtDestination)
        if (needsLiveStatus &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    fun uploadPhotoSelection(task: VendiTask?, uri: Uri?) {
        if (task == null || uri == null) return
        val selection = context.readTaskPhotoSelection(uri)
        if (selection == null) {
            appState.showWorkError("Choose a photo smaller than 10 MB.")
        } else {
            appState.uploadTaskPhotoConfirmation(
                taskId = task.id,
                fileName = selection.fileName,
                mimeType = selection.mimeType,
                fileData = selection.data
            )
        }
    }
    val cameraCapture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { didCapture ->
        val task = pendingPhotoTask
        val uri = pendingCameraUri
        pendingPhotoTask = null
        pendingCameraUri = null
        if (didCapture) {
            uploadPhotoSelection(task, uri)
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val task = pendingPhotoTask
        pendingPhotoTask = null
        uploadPhotoSelection(task, uri)
    }
    val showsBootstrap = uiState.auth.bootstrapState.isLoading ||
        uiState.isSyncingUserSession ||
        (uiState.isAuthenticated && !isMapReady)

    DisposableEffect(lifecycleOwner, appState) {
        val observer = AppLifecycleObserver(
            onForeground = appState::handleAppBecameActive,
            onBackground = appState::handleAppMovedToBackground
        )
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    VendistriTheme(darkTheme = darkTheme) {
        Box(
            modifier = androidx.compose.ui.Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // This is the single app viewport. Screens fill these bounds and never
            // need to know which system-navigation mode the device is using.
            Box(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
            ) {
            if (uiState.isAuthenticated) {
                val taskCardActions = TaskCardActions.scheduled(
                    user = uiState.auth.user,
                    onAssignToSelf = appState::claimTask,
                    onAssignAllToSelf = appState::claimTasks,
                    onBulkMarkDone = appState::markTasksDone,
                    onTaskStatusChange = appState::updateTaskStatus,
                    onCollectionFinancialUpdate = appState::updateCollectionFinancials,
                    onRefundFinancialUpdate = appState::updateRefundFinancials,
                    refillEditor = RefillTaskEditorActions(
                        states = uiState.taskRefillInventory,
                        warehouses = uiState.locations.warehouses,
                        onPrepare = appState::prepareTaskRefillInventory,
                        onRefilledChanged = appState::updateTaskRefillQuantity,
                        onFinalStockChanged = appState::updateTaskRefillFinalStock,
                        onSourceSelected = appState::setTaskRefillInventorySource,
                        onComplete = appState::completeTaskRefillInventory
                    )
                )
                val activeTaskActions = TaskCardActions.activeExecution(
                    user = uiState.auth.user,
                    onAssignToSelf = appState::claimTask,
                    onAssignAllToSelf = appState::claimTasks,
                    onBulkMarkDone = appState::markTasksDone,
                    onTaskStatusChange = appState::updateTaskStatus,
                    onCollectionFinancialUpdate = appState::updateCollectionFinancials,
                    onRefundFinancialUpdate = appState::updateRefundFinancials
                )
                val visibleTaskActions = if (uiState.appMode.isContactMode ||
                    uiState.auth.billingStatus?.subscriptionState in setOf(
                        com.vendistri.operations.features.auth.SubscriptionState.PastDue,
                        com.vendistri.operations.features.auth.SubscriptionState.Canceled
                    )
                ) {
                    TaskCardActions.readOnly()
                } else {
                    taskCardActions
                }
                MainView(
                    uiState = uiState.main,
                    locationStops = uiState.locationStops,
                    visibleTasks = uiState.visibleTasks,
                    tasksUiState = uiState.tasks,
                    locationsUiState = uiState.locations,
                    contactState = uiState.contact,
                    appModeState = uiState.appMode,
                    workState = uiState.work,
                    navigationState = uiState.navigation,
                    refillDecisionState = uiState.refillDecision,
                    refillInventoryState = uiState.refillInventory,
                    pickupInventoryState = uiState.pickupInventory,
                    addStopState = uiState.addStop,
                    notificationsState = uiState.notifications,
                    settingsState = uiState.settings,
                    isMapReady = isMapReady,
                    currentUserId = uiState.auth.user?.id,
                    userDisplayName = uiState.auth.user?.displayName ?: "Signed In",
                    onRefresh = appState::refreshSessionScope,
                    onTabSelected = appState::selectMainTab,
                    onBulkTaskAction = appState::presentBulkTaskAction,
                    onApplySharedNotes = appState::applySharedNotes,
                    taskActions = visibleTaskActions,
                    activeTaskActions = activeTaskActions,
                    onPrepareAddStop = appState::prepareAddStop,
                    onResetAddStop = appState::resetAddStop,
                    onAddStopDateChanged = appState::setAddStopDate,
                    onAddStopNotesChanged = appState::setAddStopNotes,
                    onAddStopLocationToggle = appState::toggleAddStopLocation,
                    onAddStopMachineToggle = appState::toggleAddStopMachine,
                    onAddStopTaskTypeToggle = appState::toggleAddStopTaskType,
                    onAddStopAssigneeSelected = appState::setAddStopAssignee,
                    onSaveAddStop = appState::saveAddStop,
                    onConfirmAddStopPrecheck = appState::confirmAddStopPrecheck,
                    onConfirmAddStopRescheduleExisting = appState::confirmAddStopRescheduleExisting,
                    onDismissAddStopPrecheckAlert = appState::dismissAddStopPrecheckAlert,
                    onMapStopSelected = appState::selectMapStop,
                    onClearMapStop = appState::clearSelectedMapStop,
                    onMapUserLocationChanged = appState::updateMapUserLocation,
                    onNavigationProgress = appState::updateNavigationProgress,
                    onPresentGoSummary = appState::presentGoSummary,
                    onGoStopSelected = appState::selectGoStop,
                    onDismissGoSummary = appState::dismissGoSummary,
                    onStartSelectedGoRoute = appState::startSelectedGoRoute,
                    onGoScopeChoiceSelected = appState::confirmGoRouteScope,
                    onGoScopeChoiceChanged = appState::selectGoRouteScopeChoice,
                    onGoLaterRefillTaskToggle = appState::toggleGoRouteLaterRefillTask,
                    onDismissGoScopeChoice = appState::dismissGoRouteScopeChoice,
                    onDismissRefillDecision = appState::dismissRefillDecision,
                    onStartWorkNavigation = appState::startWorkNavigation,
                    isDebugRouteSimulationEnabled = appState.isDebugRouteSimulationEnabled,
                    onSimulateCurrentWorkRouteForDebug = appState::simulateCurrentWorkRouteForDebug,
                    onRefillDecisionActionSelected = appState::selectRefillDecisionAction,
                    onRefillDecisionWarehouseSelected = appState::selectRefillDecisionWarehouse,
                    onRefillDecisionTaskToggle = appState::toggleRefillDecisionTask,
                    onApplyRefillDecision = appState::applyRefillDecision,
                    onArriveAtWorkLocation = appState::arriveAtWorkLocation,
                    onPrepareCurrentInventoryTask = appState::prepareCurrentInventoryTask,
                    onMarkCurrentWorkTaskDone = appState::markCurrentWorkTaskDone,
                    onCompleteCurrentInventoryTask = appState::completeCurrentInventoryTask,
                    onAdvanceCurrentWorkTask = appState::advanceCurrentWorkTask,
                    onRefillQuantityChanged = appState::updateRefillQuantity,
                    onRefillFinalStockChanged = appState::updateRefillFinalStock,
                    onRefillSourceSelected = appState::setCurrentRefillInventorySource,
                    onPickupQuantityChanged = appState::updatePickupQuantity,
                    onFinishCurrentWorkVisit = appState::finishCurrentWorkVisit,
                    onCancelCurrentWorkTasks = appState::cancelCurrentWorkTasks,
                    onAddTaskPhoto = { task ->
                        pendingPhotoTask = task
                        showPhotoSourceDialog = true
                    },
                    onRemoveTaskPhoto = { task -> appState.removeTaskPhotoConfirmation(task.id) },
                    onStopCurrentWorkSession = appState::stopCurrentWorkSession,
                    onAutoCalcCommissionChanged = appState::setAutoCalcCommission,
                    onAutoFillRefillFinalStockChanged = appState::setAutoFillRefillFinalStock,
                    onAppearancePreferenceChanged = appState::setAppearancePreference,
                    onNavigationAudioPreferenceChanged = appState::setNavigationAudioPreference,
                    onCurrentNavigationAudioPreferenceChanged = appState::setCurrentNavigationAudioPreference,
                    onTimeFormatPreferenceChanged = appState::setTimeFormatPreference,
                    onViewModeSelected = appState::switchViewMode,
                    onLoadContactLocationMachines = appState::loadContactLocationMachines,
                    onMarkAllNotificationsRead = appState::markAllNotificationsRead,
                    onNotificationSelected = appState::selectNotification,
                    onSummaryDateSelected = appState::loadSummaryWeek,
                    onTasksPanelDateVisible = appState::loadTasksPanelDate,
                    onMapReadyChanged = { isMapReady = it },
                    onSignOut = appState::signOut
                )
            } else {
                AuthView(
                    uiState = uiState.auth,
                    onSignIn = appState::signIn,
                    onPasswordResetRequested = appState::requestPasswordReset
                )
            }
            }

            TaskActionSheet(
                state = uiState.taskActions,
                onDismiss = appState::dismissTaskAction,
                onConfirm = appState::confirmTaskAction,
                onTaskAssigneeSelected = appState::selectTaskActionAssignee,
                onDateSelected = appState::selectTaskActionDate,
                onQuickDateSelected = appState::selectQuickTaskActionDate,
                onTaskSelectionToggle = appState::toggleTaskActionSelection
            )

            if (showsBootstrap) {
                VendistriBootstrapView()
            }

            uiState.auth.paymentRequiredUrl?.let { url ->
                InAppBrowserView(
                    url = url,
                    onClose = appState::clearPaymentRequired
                )
            }

            TaskPhotoSourceDialog(
                isVisible = showPhotoSourceDialog,
                onDismiss = {
                    showPhotoSourceDialog = false
                    pendingPhotoTask = null
                },
                onTakePhoto = {
                    val task = pendingPhotoTask
                    showPhotoSourceDialog = false
                    if (task == null) {
                        pendingPhotoTask = null
                        return@TaskPhotoSourceDialog
                    }
                    val uri = context.createTaskPhotoCaptureUri()
                    pendingCameraUri = uri
                    cameraCapture.launch(uri)
                },
                onChooseExisting = {
                    showPhotoSourceDialog = false
                    photoPicker.launch("image/*")
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskPhotoSourceDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onChooseExisting: () -> Unit
) {
    if (!isVisible) return
    val sheetState = rememberVendistriActionSheetState()
    VendistriActionSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        BoxWithConstraints(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val visibleContentHeight = if (sheetState.currentValue == SheetValue.Expanded) {
                maxHeight
            } else {
                maxHeight * 0.5f
            }
            Column(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .height(visibleContentHeight)
            ) {
                Text("Add Photo", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = androidx.compose.ui.Modifier.height(12.dp))
                Button(
                    onClick = onTakePhoto,
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                ) {
                    Text("Take Photo")
                }
                Spacer(modifier = androidx.compose.ui.Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onChooseExisting,
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                ) {
                    Text("Choose Existing")
                }
                Spacer(modifier = androidx.compose.ui.Modifier.weight(1f))
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

private data class TaskPhotoSelection(
    val fileName: String,
    val mimeType: String,
    val data: ByteArray
)

private fun Context.readTaskPhotoSelection(uri: Uri): TaskPhotoSelection? {
    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    if (bytes.size > MaxTaskPhotoUploadBytes) return null
    val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
    return TaskPhotoSelection(
        fileName = displayName(uri) ?: "photo-confirmation.jpg",
        mimeType = mimeType,
        data = bytes
    )
}

private fun Context.displayName(uri: Uri): String? {
    return contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        } else {
            null
        }
    }?.takeIf { it.isNotBlank() }
}

private fun Context.createTaskPhotoCaptureUri(): Uri {
    val directory = File(cacheDir, "task-photo-cache").apply { mkdirs() }
    val file = File(directory, "photo-confirmation-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
}
