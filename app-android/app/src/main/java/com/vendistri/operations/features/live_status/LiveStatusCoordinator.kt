package com.vendistri.operations.features.live_status

import android.content.Context
import android.text.format.DateFormat
import com.vendistri.operations.features.navigation.NavigationSessionState
import com.vendistri.operations.features.settings.AppSettingsState
import com.vendistri.operations.features.work.WorkUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LiveStatusCoordinator(
    private val context: Context,
    scope: CoroutineScope,
    work: Flow<WorkUiState>,
    navigation: Flow<NavigationSessionState>,
    settings: Flow<AppSettingsState>
) {
    private var lastSnapshot: LiveStatusSnapshot? = null
    private var lastPublishedAt = 0L

    init {
        scope.launch {
            combine(work, navigation, settings, ticker()) { workState, navigationState, settingsState, now ->
                LiveStatusProjector.project(
                    work = workState,
                    navigation = navigationState,
                    timeFormatPreference = settingsState.timeFormatPreference,
                    systemUses24Hour = DateFormat.is24HourFormat(context),
                    nowEpochMillis = now
                )
            }.collect(::handle)
        }
    }

    private fun handle(snapshot: LiveStatusSnapshot?) {
        if (snapshot == null) {
            if (lastSnapshot != null) NavigationForegroundService.stop(context)
            lastSnapshot = null
            lastPublishedAt = 0L
            return
        }
        val previous = lastSnapshot
        if (snapshot == previous) return
        val now = System.currentTimeMillis()
        val important = previous == null ||
            previous.stopId != snapshot.stopId ||
            previous.mode != snapshot.mode ||
            previous.isRerouting != snapshot.isRerouting ||
            previous.progressCurrent != snapshot.progressCurrent ||
            previous.progressTotal != snapshot.progressTotal ||
            previous.title != snapshot.title
        if (!important && now - lastPublishedAt < MinimumRoutineUpdateMillis) {
            lastSnapshot = snapshot
            return
        }
        runCatching { NavigationForegroundService.update(context, snapshot) }
        lastSnapshot = snapshot
        lastPublishedAt = now
    }

    companion object {
        private const val MinimumRoutineUpdateMillis = 15_000L

        private fun ticker(): Flow<Long> = flow {
            while (true) {
                emit(System.currentTimeMillis())
                delay(MinimumRoutineUpdateMillis)
            }
        }
    }
}
