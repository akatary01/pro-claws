package com.vendistri.operations.features.notifications

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vendistri.operations.design.AppColors
import java.time.Duration
import java.time.Instant

@Composable
fun NotificationsPanelView(
    state: NotificationsState,
    onClose: () -> Unit,
    onMarkAllRead: () -> Unit,
    onSelect: (AppNotificationItem) -> Unit
) {
    LaunchedEffect(Unit) {
        onMarkAllRead()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onClose, modifier = Modifier.size(44.dp)) {
                Text("<", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text("Notifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.size(44.dp))
        }

        if (state.items.isEmpty()) {
            Text(
                text = "Notifications will appear here.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 80.dp),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            state.items.forEach { item ->
                NotificationRow(item = item, onClick = { onSelect(item) })
            }
        }
    }
}

@Composable
private fun NotificationRow(item: AppNotificationItem, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, AppColors.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            NotificationKindIcon(kind = item.kind)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Text(
                        text = item.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!item.isRead) {
                        Surface(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .size(8.dp),
                            shape = CircleShape,
                            color = AppColors.vendBlue
                        ) {}
                    }
                }
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.muted
                )
                Text(
                    text = relativeTime(item.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.muted
                )
            }
        }
    }
}

@Composable
private fun NotificationKindIcon(kind: AppNotificationKind) {
    val tint = kind.tint
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(tint.copy(alpha = 0.14f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = kind.symbol,
            color = tint,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

private val AppNotificationKind.tint: Color
    get() = when (this) {
        AppNotificationKind.TaskCreated -> AppColors.vendBlue
        AppNotificationKind.TaskAssigned -> AppColors.statusUnassigned
        AppNotificationKind.TaskStatusChanged -> AppColors.statusDone
        AppNotificationKind.TaskEdited -> AppColors.statusPending
        AppNotificationKind.TaskDeleted -> AppColors.statusError
    }

private val AppNotificationKind.symbol: String
    get() = when (this) {
        AppNotificationKind.TaskCreated -> "+"
        AppNotificationKind.TaskAssigned -> "A"
        AppNotificationKind.TaskStatusChanged -> "D"
        AppNotificationKind.TaskEdited -> "E"
        AppNotificationKind.TaskDeleted -> "X"
    }

private fun relativeTime(createdAt: Instant): String {
    val seconds = Duration.between(createdAt, Instant.now()).seconds.coerceAtLeast(0)
    return when {
        seconds < 60 -> "now"
        seconds < 3_600 -> "${seconds / 60}m"
        seconds < 86_400 -> "${seconds / 3_600}h"
        else -> "${seconds / 86_400}d"
    }
}
