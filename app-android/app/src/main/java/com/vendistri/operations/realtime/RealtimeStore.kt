package com.vendistri.operations.realtime

import com.vendistri.operations.features.auth.User
import com.vendistri.operations.features.auth.UserStore
import com.vendistri.operations.features.location.LocationStore
import com.vendistri.operations.features.location_contact.ContactStore
import com.vendistri.operations.features.notifications.AppNotificationFactory
import com.vendistri.operations.features.notifications.NotificationsStore
import com.vendistri.operations.features.notifications.RealtimeEventPayload
import com.vendistri.operations.features.tasks.TasksStore
import com.vendistri.operations.features.work.WorkFlowStore
import com.vendistri.operations.network.ApiClient
import com.vendistri.operations.network.NetworkConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class RealtimeStore(
    private val apiClient: ApiClient,
    private val scope: CoroutineScope,
    private val userStore: UserStore,
    private val tasksStore: TasksStore,
    private val locationStore: LocationStore,
    private val contactStore: ContactStore,
    private val notificationsStore: NotificationsStore,
    private val workFlowStore: WorkFlowStore,
    private val onInventoryChanged: suspend () -> Unit = {},
    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .build()
) {
    private val _connectionState = MutableStateFlow(RealtimeConnectionState.Disconnected)
    val connectionState: StateFlow<RealtimeConnectionState> = _connectionState.asStateFlow()

    private var connectedUserId: String? = null
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var flushJob: Job? = null
    private var isFlushingEvents = false
    private val pendingEvents = mutableListOf<RealtimeEventPayload>()

    fun updateConnection(user: User?) {
        val userId = user?.id?.trim().orEmpty()
        if (userId.isBlank()) {
            disconnect()
            return
        }
        if (connectedUserId == userId && webSocket != null) return

        disconnect()
        connectedUserId = userId
        connect(userId)
    }

    fun disconnect() {
        val socket = webSocket
        reconnectJob?.cancel()
        reconnectJob = null
        flushJob?.cancel()
        flushJob = null
        isFlushingEvents = false
        pendingEvents.clear()
        webSocket = null
        connectedUserId = null
        socket?.close(1000, "disconnect")
        _connectionState.value = RealtimeConnectionState.Disconnected
    }

    private fun connect(userId: String) {
        val cookieHeader = apiClient.cookieHeader()
        if (cookieHeader.isNullOrBlank()) {
            _connectionState.value = RealtimeConnectionState.Disconnected
            return
        }

        _connectionState.value = RealtimeConnectionState.Connecting
        val request = Request.Builder()
            .url(NetworkConfig.backendWebSocketUrl(userId))
            .header("Cookie", cookieHeader)
            .build()
        webSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (connectedUserId == userId) {
                        _connectionState.value = RealtimeConnectionState.Connected
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    queueEvent(text)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (connectedUserId == userId) {
                        scheduleReconnect(userId)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (connectedUserId == userId) {
                        scheduleReconnect(userId)
                    }
                }
            }
        )
    }

    private fun scheduleReconnect(userId: String) {
        webSocket = null
        _connectionState.value = RealtimeConnectionState.Disconnected
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(2_000)
            if (connectedUserId == userId) {
                connect(userId)
            }
        }
    }

    private fun queueEvent(rawJson: String) {
        val event = RealtimeEventPayload.fromJson(rawJson) ?: return
        scope.launch(Dispatchers.Main) {
            appendNotificationIfNeeded(event)
            pendingEvents.add(event)
            scheduleFlush()
        }
    }

    private fun scheduleFlush() {
        if (flushJob?.isActive == true || isFlushingEvents) return
        flushJob = scope.launch {
            delay(150)
            flushEvents()
        }
    }

    private suspend fun flushEvents() {
        if (isFlushingEvents) return
        val events = pendingEvents.toList()
        pendingEvents.clear()
        if (events.isEmpty()) return

        isFlushingEvents = true
        try {
            val invalidation = RealtimeInvalidationBatch.from(events)
            if (invalidation.shouldReloadAuth) {
                userStore.initUser()
            }
            if (userStore.currentUser == null) return
            if (invalidation.shouldReloadTasks) {
            tasksStore.refreshRealtimeChanges(
                changedTaskIds = invalidation.changedTaskIds,
                deletedTaskIds = invalidation.deletedTaskIds,
                changedMachineIds = invalidation.changedMachineIds,
                changedLocationIds = invalidation.changedLocationIds,
                requiresFullReload = invalidation.requiresFullTaskReload
            )
            workFlowStore.rehydrateActiveExecution(tasksStore.state.value.tasks)
        }
            if (invalidation.shouldReloadLocations) {
                locationStore.loadLocations(force = true)
                contactStore.loadLocations(force = true)
                val contactLocationIds = contactStore.state.value.locationsById.keys
                val changedContactLocationIds = invalidation.changedLocationIds.filter { it in contactLocationIds }
                contactStore.loadMachinesForLocations(
                    if (changedContactLocationIds.isEmpty()) contactLocationIds else changedContactLocationIds,
                    force = true
                )
            }
            if (invalidation.shouldRefreshInventoryEditors) {
                onInventoryChanged()
            }
        } finally {
            isFlushingEvents = false
            if (pendingEvents.isNotEmpty()) {
                scheduleFlush()
            }
        }
    }

    private fun appendNotificationIfNeeded(event: RealtimeEventPayload) {
        val task = event.resolvedTaskId?.let { tasksStore.state.value.tasksById[it] }
        val locationName = event.locationId?.let { locationStore.state.value.locationsById[it]?.name }
            ?: task?.locationName
        val item = AppNotificationFactory.make(
            event = event,
            task = task,
            locationName = locationName,
            currentUserId = userStore.currentUser?.id
        ) ?: return
        notificationsStore.append(item)
    }
}

enum class RealtimeConnectionState {
    Disconnected,
    Connecting,
    Connected
}
