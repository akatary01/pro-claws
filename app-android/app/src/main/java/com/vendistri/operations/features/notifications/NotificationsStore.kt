package com.vendistri.operations.features.notifications

import java.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class NotificationsStore {
    private val _state = MutableStateFlow(NotificationsState())
    val state: StateFlow<NotificationsState> = _state.asStateFlow()

    fun resetUserScopedState() {
        _state.value = NotificationsState()
    }

    fun append(item: AppNotificationItem) {
        _state.update { current ->
            val mutableItems = current.items.toMutableList()
            val replaceIndex = mutableItems.indexOfFirst { shouldReplace(it, item) }
            if (replaceIndex >= 0) {
                mutableItems.removeAt(replaceIndex)
            } else if (mutableItems.any { shouldDropIncoming(it, item) }) {
                return@update current
            }
            mutableItems.add(0, item)
            current.copy(items = mutableItems.take(100))
        }
    }

    fun markAllRead() {
        _state.update { current ->
            if (current.items.none { !it.isRead }) {
                current
            } else {
                current.copy(items = current.items.map { it.copy(isRead = true) })
            }
        }
    }

    fun markRead(itemId: String) {
        _state.update { current ->
            current.copy(
                items = current.items.map {
                    if (it.id == itemId) it.copy(isRead = true) else it
                }
            )
        }
    }

    private fun shouldReplace(existing: AppNotificationItem, incoming: AppNotificationItem): Boolean {
        return existing.taskId == incoming.taskId &&
            existing.taskId != null &&
            existing.createdAt.isCloseTo(incoming.createdAt) &&
            existing.kind == AppNotificationKind.TaskEdited &&
            incoming.kind != AppNotificationKind.TaskEdited
    }

    private fun shouldDropIncoming(existing: AppNotificationItem, incoming: AppNotificationItem): Boolean {
        return existing.taskId == incoming.taskId &&
            existing.taskId != null &&
            existing.createdAt.isCloseTo(incoming.createdAt) &&
            incoming.kind == AppNotificationKind.TaskEdited &&
            existing.kind != AppNotificationKind.TaskEdited
    }
}

data class NotificationsState(
    val items: List<AppNotificationItem> = emptyList()
) {
    val unreadCount: Int
        get() = items.count { !it.isRead }
}

private fun java.time.Instant.isCloseTo(other: java.time.Instant): Boolean {
    return kotlin.math.abs(Duration.between(this, other).toMillis()) <= 2_000
}
