package com.vendistri.operations.features.navigation

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vendistri.operations.R
import com.vendistri.operations.components.VendistriIconButton
import com.vendistri.operations.design.LocalVendistriPalette
import com.vendistri.operations.features.settings.AppTimeFormatter
import com.vendistri.operations.features.settings.NavigationAudioPreference
import com.vendistri.operations.features.settings.TimeFormatPreference
import com.vendistri.operations.features.location.AppLocation
import com.vendistri.operations.features.tasks.VendiTask
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.work.ActiveTaskExecution
import com.vendistri.operations.features.work.ActiveWorkSession
import com.vendistri.operations.features.work.ExecutionTaskItem
import com.vendistri.operations.features.work.RoutePreview
import com.vendistri.operations.features.work.TaskExecutionDisplay
import com.vendistri.operations.features.work.TaskExecutionResolver
import com.vendistri.operations.features.work.WorkDestinationKind
import com.vendistri.operations.features.work.WorkPhase
import com.vendistri.operations.features.work.WorkUiState
import com.vendistri.operations.features.work.isNavigating
import com.vendistri.operations.features.tasks.formatDuration
import com.vendistri.operations.features.tasks.oneDecimal
import kotlinx.coroutines.delay

private const val TrafficAlertDismissMillis = 5_000
private val NavigationMapControlsBottomGap = 10.dp
private val NavigationMapControlsHeight = 130.dp
private val NavigationTrafficAlertBottomOffset = 128.dp
private val NavigationMapControlSize = 42.dp
private val NavigationMapControlIconSize = 20.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NavigationChromeView(
    workState: WorkUiState,
    navigationState: NavigationSessionState,
    audioPreference: NavigationAudioPreference,
    timeFormatPreference: TimeFormatPreference,
    systemUses24Hour: Boolean?,
    allTasks: List<VendiTask>,
    locationsById: Map<String, AppLocation>,
    currentInstructionText: String?,
    isDebugRouteSimulationEnabled: Boolean,
    isArrivalCandidate: Boolean,
    onApplySharedNotes: suspend (List<String>, String?) -> Boolean,
    onAudioPreferenceChanged: (NavigationAudioPreference) -> Unit,
    onSimulateRouteForDebug: () -> Unit,
    onRecenter: () -> Unit,
    isRouteOverviewMode: Boolean,
    onToggleRouteOverview: () -> Unit,
    onArrived: () -> Unit,
    onCancelNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!workState.phase.isNavigating) return
    var areStepsExpanded by remember(navigationState.activeStopId) { mutableStateOf(false) }
    var isTripCardExpanded by remember(workState.activeSession?.id, workState.activeExecution?.stopId) { mutableStateOf(false) }
    var isAudioPreferenceExpanded by remember(navigationState.activeStopId) { mutableStateOf(false) }
    var dismissedTrafficAlertIds by remember(navigationState.activeStopId) { mutableStateOf(emptySet<String>()) }
    val isOffline = rememberNavigationOfflineState()
    LaunchedEffect(isArrivalCandidate) {
        if (isArrivalCandidate) {
            areStepsExpanded = false
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val expandedStepsMaxHeightTarget = if (isTripCardExpanded) {
            minOf(maxOf(maxHeight * 0.18f, 112.dp), 160.dp)
        } else {
            minOf(300.dp, maxHeight * 0.30f)
        }
        val expandedStepsMaxHeight by animateDpAsState(
            targetValue = expandedStepsMaxHeightTarget,
            animationSpec = tween(durationMillis = 240),
            label = "navigation-steps-expanded-height"
        )

        if (areStepsExpanded) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.001f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { areStepsExpanded = false }
            )
        }
        if (isTripCardExpanded) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.001f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { isTripCardExpanded = false }
            )
        }
        if (isAudioPreferenceExpanded) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.001f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { isAudioPreferenceExpanded = false }
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            NavigationManeuverPanel(
                phase = workState.phase,
                session = workState.activeSession,
                execution = workState.activeExecution,
                navigationState = navigationState,
                fallbackInstructionText = currentInstructionText,
                isArrivalCandidate = isArrivalCandidate,
                isStepsExpanded = areStepsExpanded,
                expandedStepsMaxHeight = expandedStepsMaxHeight,
                onStepsExpandedChanged = { areStepsExpanded = it },
                modifier = Modifier.fillMaxWidth()
            )
            if (isOffline) NavigationOfflineBanner(modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .imeNestedScroll(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(NavigationMapControlsHeight)
            ) {
                NavigationMapInfoRow(
                    navigationState = navigationState,
                    audioPreference = audioPreference,
                    isAudioPreferenceExpanded = isAudioPreferenceExpanded,
                    onAudioPreferenceExpandedChanged = { isAudioPreferenceExpanded = it },
                    isDebugRouteSimulationEnabled = isDebugRouteSimulationEnabled,
                    isArrivalCandidate = isArrivalCandidate,
                    onAudioPreferenceChanged = onAudioPreferenceChanged,
                    onSimulateRouteForDebug = onSimulateRouteForDebug,
                    onRecenter = onRecenter,
                    isRouteOverviewMode = isRouteOverviewMode,
                    onToggleRouteOverview = onToggleRouteOverview,
                    onArrived = onArrived,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 10.dp,
                            bottom = NavigationMapControlsBottomGap
                        )
                )

                navigationState.trafficAlert
                    ?.takeUnless { dismissedTrafficAlertIds.contains(it.id) }
                    ?.let { alert ->
                        NavigationTrafficAlertCard(
                            alert = alert,
                            onDismiss = { dismissedTrafficAlertIds = dismissedTrafficAlertIds + alert.id },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(y = -NavigationTrafficAlertBottomOffset)
                                .padding(horizontal = 16.dp)
                        )
                    }
            }

            NavigationTripCard(
                workState = workState,
                navigationState = navigationState,
                timeFormatPreference = timeFormatPreference,
                systemUses24Hour = systemUses24Hour,
                allTasks = allTasks,
                locationsById = locationsById,
                isArrivalCandidate = isArrivalCandidate,
                isExpanded = isTripCardExpanded,
                onExpandedChanged = { isTripCardExpanded = it },
                onApplySharedNotes = onApplySharedNotes,
                onCancelNavigation = onCancelNavigation
            )
        }
    }
}

@Composable
private fun rememberNavigationOfflineState(): Boolean {
    val context = LocalContext.current
    val connectivityManager = remember(context) {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    var isOffline by remember { mutableStateOf(!connectivityManager.hasValidatedInternet()) }
    DisposableEffect(connectivityManager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isOffline = !connectivityManager.hasValidatedInternet()
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                isOffline = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }

            override fun onLost(network: Network) {
                isOffline = !connectivityManager.hasValidatedInternet()
            }
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
        onDispose { connectivityManager.unregisterNetworkCallback(callback) }
    }
    return isOffline
}

private fun ConnectivityManager.hasValidatedInternet(): Boolean {
    val capabilities = getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

@Composable
private fun NavigationOfflineBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.88f),
        shape = RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp),
        shadowElevation = 4.dp
    ) {
        Text(
            text = "Offline • Return to the highlighted route",
            color = Color.White.copy(alpha = 0.92f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun NavigationManeuverPanel(
    phase: WorkPhase,
    session: ActiveWorkSession?,
    execution: ActiveTaskExecution?,
    navigationState: NavigationSessionState,
    fallbackInstructionText: String?,
    isArrivalCandidate: Boolean,
    isStepsExpanded: Boolean,
    expandedStepsMaxHeight: androidx.compose.ui.unit.Dp,
    onStepsExpandedChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val instruction = navigationState.currentInstruction
    val futureSteps = navigationState.futureInstructionSteps
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
        color = Color.Black.copy(alpha = 0.95f),
        shadowElevation = 10.dp
    ) {
        Column {
            Box {
                InstructionHeader(
                    phase = phase,
                    session = session,
                    execution = execution,
                    instruction = instruction,
                    fallbackInstructionText = fallbackInstructionText,
                    isArrivalCandidate = isArrivalCandidate,
                    canExpand = futureSteps.isNotEmpty(),
                    onToggleExpanded = { onStepsExpandedChanged(!isStepsExpanded) },
                    onDragVertical = { deltaY ->
                        if (deltaY < -24f && futureSteps.isNotEmpty()) {
                            onStepsExpandedChanged(true)
                        } else if (deltaY > 24f) {
                            onStepsExpandedChanged(false)
                        }
                    }
                )
            }
            if (isStepsExpanded && futureSteps.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .heightIn(max = expandedStepsMaxHeight)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (dragAmount.y > 18f) {
                                        onStepsExpandedChanged(false)
                                    }
                                }
                            )
                        }
                        .verticalScroll(rememberScrollState())
                ) {
                    futureSteps.forEach { step ->
                        InstructionStepRow(
                            step = step,
                            destinationTitle = session?.title ?: execution?.title
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstructionHeader(
    phase: WorkPhase,
    session: ActiveWorkSession?,
    execution: ActiveTaskExecution?,
    instruction: NavigationInstructionStep?,
    fallbackInstructionText: String?,
    isArrivalCandidate: Boolean,
    canExpand: Boolean,
    onToggleExpanded: () -> Unit,
    onDragVertical: (Float) -> Unit
) {
    val destinationTitle = session?.title ?: execution?.title ?: "Current stop"
    val primaryText = instruction?.primaryText
        ?.navigationArrivalText(destinationTitle)
        ?: fallbackInstructionText
        ?: destinationTitle
    val secondaryText = instruction?.secondaryText
        ?: if (instruction == null) {
            session?.title
                ?.takeIf { it != primaryText && fallbackInstructionText != null }
                ?: session?.addressText
        } else {
            null
        }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canExpand, onClick = onToggleExpanded)
            .pointerInput(canExpand) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        if (!canExpand) return@detectDragGestures
                        change.consume()
                        onDragVertical(dragAmount.y)
                    }
                )
            }
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isArrivalCandidate) {
                Text(
                    text = "↑",
                    color = Color.White,
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.width(44.dp)
                )
            } else {
                ManeuverGlyph(
                    type = instruction?.maneuverType,
                    modifier = instruction?.maneuverModifier,
                    layoutModifier = Modifier.width(44.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = if (instruction?.exitCode != null) 54.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isArrivalCandidate) {
                    Text(
                        text = "ARRIVED",
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                } else if (instruction != null) {
                    DistanceText(distanceMeters = instruction.distanceMeters)
                } else {
                    Text(
                        text = phase.navigationInstruction,
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                InstructionLineContent(
                    text = if (isArrivalCandidate) destinationTitle else primaryText,
                    components = if (isArrivalCandidate) emptyList() else instruction?.primaryComponents.orEmpty(),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (isArrivalCandidate) Int.MAX_VALUE else 2
                )
                if (!isArrivalCandidate) secondaryText?.let { text ->
                    InstructionLineContent(
                        text = text,
                        components = instruction?.secondaryComponents.orEmpty(),
                        color = Color.White.copy(alpha = 0.70f),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1
                    )
                }
            }
        }
        if (!isArrivalCandidate) instruction?.exitCode?.let { exitCode ->
            ExitBadge(
                text = exitCode,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}

@Composable
private fun InstructionStepRow(
    step: NavigationInstructionStep,
    destinationTitle: String?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.95f))
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = maneuverGlyph(step.maneuverType, step.maneuverModifier),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.width(44.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = if (step.exitCode != null) 54.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                DistanceText(distanceMeters = step.distanceMeters)
                InstructionLineContent(
                    text = step.primaryText.navigationArrivalText(destinationTitle),
                    components = step.primaryComponents,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
            }
        }
        step.exitCode?.let { exitCode ->
            ExitBadge(
                text = exitCode,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}

@Composable
private fun ManeuverGlyph(
    type: String?,
    modifier: String?,
    layoutModifier: Modifier = Modifier
) {
    val normalizedType = type.orEmpty().lowercase()
    val normalizedModifier = modifier.orEmpty().lowercase()
    val isArrive = normalizedType == "arrive"
    val side = when {
        !isArrive -> null
        normalizedModifier.contains("right") -> ArrivalSide.Right
        normalizedModifier.contains("left") -> ArrivalSide.Left
        else -> null
    }
    if (side == null) {
        Text(
            text = maneuverGlyph(type, modifier),
            color = Color.White,
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = layoutModifier
        )
        return
    }
    Row(
        modifier = layoutModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (side == ArrivalSide.Left) {
            Text(
                text = "•",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        Text(
            text = "↑",
            color = Color.White,
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        if (side == ArrivalSide.Right) {
            Text(
                text = "•",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

private enum class ArrivalSide {
    Left,
    Right
}

@Composable
private fun InstructionLineContent(
    text: String,
    components: List<NavigationInstructionComponent>,
    color: Color,
    style: TextStyle,
    fontWeight: FontWeight? = null,
    maxLines: Int
) {
    val visualComponents = components.filter {
        it.text.isNotBlank() &&
            (it.kind == NavigationInstructionComponent.Kind.Shield || it.kind == NavigationInstructionComponent.Kind.Image)
    }
    if (visualComponents.isEmpty()) {
        Text(
            text = text.arrivalSideDotText(),
            color = color,
            style = style,
            fontWeight = fontWeight,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
        return
    }

    val remainingText = components
        .filter { it.kind == NavigationInstructionComponent.Kind.Text || it.kind == NavigationInstructionComponent.Kind.Delimiter }
        .joinToString(" ") { it.text }
        .replace(Regex("\\s+"), " ")
        .trim()
        .arrivalSideDotText()
        .takeIf { it.isNotBlank() }
    val leadingVisualComponents = visualComponents.filter { it.kind == NavigationInstructionComponent.Kind.Shield }
    val trailingVisualComponents = visualComponents.filter { it.kind != NavigationInstructionComponent.Kind.Shield }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        leadingVisualComponents.forEach { component ->
            InstructionVisualBadge(component = component)
        }
        remainingText?.let {
            Text(
                text = it,
                color = color,
                style = style,
                fontWeight = fontWeight,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        trailingVisualComponents.forEach { component ->
            InstructionVisualBadge(component = component)
        }
    }
}

@Composable
private fun InstructionVisualBadge(component: NavigationInstructionComponent) {
    val isShield = component.kind == NavigationInstructionComponent.Kind.Shield
    Text(
        text = component.text,
        color = Color.White,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .then(
                if (isShield) {
                    Modifier.background(Color(0xFF2563EB), RoundedCornerShape(6.dp))
                } else {
                    Modifier
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                }
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun ExitBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .border(1.dp, Color.White, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun DistanceText(distanceMeters: Double) {
    val parts = formatDistanceParts(distanceMeters)
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = parts.first,
            color = Color.White,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = parts.second,
            color = Color.White.copy(alpha = 0.72f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
}

@Composable
private fun NavigationMapInfoRow(
    navigationState: NavigationSessionState,
    audioPreference: NavigationAudioPreference,
    isAudioPreferenceExpanded: Boolean,
    onAudioPreferenceExpandedChanged: (Boolean) -> Unit,
    isDebugRouteSimulationEnabled: Boolean,
    isArrivalCandidate: Boolean,
    onAudioPreferenceChanged: (NavigationAudioPreference) -> Unit,
    onSimulateRouteForDebug: () -> Unit,
    onRecenter: () -> Unit,
    isRouteOverviewMode: Boolean,
    onToggleRouteOverview: () -> Unit,
    onArrived: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        navigationState.speedLimit?.let { speedLimit ->
            NavigationSpeedStack(
                currentSpeed = navigationState.currentSpeed,
                speedLimit = speedLimit,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }

        if (isArrivalCandidate) {
            NavigationArrivedPill(
                onClick = onArrived,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        } else {
            navigationState.roadNameText?.takeIf { it.isNotBlank() }?.let { roadName ->
                NavigationRoadNamePill(
                    roadName = roadName,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 66.dp)
                )
            }
        }

        NavigationMapActions(
            audioPreference = audioPreference,
            isAudioPreferenceExpanded = isAudioPreferenceExpanded,
            onAudioPreferenceExpandedChanged = onAudioPreferenceExpandedChanged,
            isDebugRouteSimulationEnabled = isDebugRouteSimulationEnabled,
            onAudioPreferenceChanged = onAudioPreferenceChanged,
            onSimulateRouteForDebug = onSimulateRouteForDebug,
            onRecenter = onRecenter,
            isRouteOverviewMode = isRouteOverviewMode,
            onToggleRouteOverview = onToggleRouteOverview,
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}

@Composable
private fun NavigationSpeedStack(
    currentSpeed: NavigationCurrentSpeedDisplay?,
    speedLimit: NavigationSpeedLimitDisplay,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.width(58.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color.Black.copy(alpha = 0.82f),
        shadowElevation = 8.dp
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            currentSpeed?.let {
                Text(
                    text = it.valueText,
                    color = navigationSpeedColor(currentSpeed = it.value, speedLimit = speedLimit.value),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(top = 7.dp, bottom = 2.dp)
                )
            }
            Column(
                modifier = Modifier
                    .padding(3.dp)
                    .fillMaxWidth()
                    .height(68.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(2.dp, Color.Black, RoundedCornerShape(12.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = speedLimit.valueText,
                    color = Color.Black,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = speedLimit.unitText.lowercase(),
                    color = Color.Black.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun NavigationRoadNamePill(
    roadName: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = LocalVendistriPalette.current.brand,
        shadowElevation = 5.dp
    ) {
        Text(
            text = roadName,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun NavigationArrivedPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulse = rememberInfiniteTransition(label = "arrived-pill-pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 0.92f,
        targetValue = 0.98f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrived-pill-pulse-scale"
    )
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.68f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrived-pill-pulse-alpha"
    )
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = LocalVendistriPalette.current.success,
        shadowElevation = 6.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(3.dp)
                    .scale(pulseScale)
                    .border(2.dp, Color.White.copy(alpha = pulseAlpha), RoundedCornerShape(18.dp))
            )
            Text(
                text = "I've Arrived",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 9.dp)
            )
        }
    }
}

@Composable
private fun NavigationTrafficAlertCard(
    alert: NavigationTrafficAlert,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalVendistriPalette.current
    var isDismissProgressComplete by remember(alert.id) { mutableStateOf(false) }
    val dismissProgress by animateFloatAsState(
        targetValue = if (isDismissProgressComplete) 1f else 0f,
        animationSpec = tween(
            durationMillis = TrafficAlertDismissMillis,
            easing = LinearEasing
        ),
        label = "traffic-alert-dismiss-progress"
    )
    LaunchedEffect(alert.id) {
        isDismissProgressComplete = false
        withFrameNanos { }
        isDismissProgressComplete = true
        delay(TrafficAlertDismissMillis.toLong())
        onDismiss()
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = palette.surface.copy(alpha = 0.97f),
        shadowElevation = 14.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = RoundedCornerShape(17.dp),
                    color = palette.warning
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = alert.warningGlyph,
                            color = Color.Black,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = alert.title,
                        color = palette.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = alert.subtitle,
                        color = palette.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Surface(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
                shape = RoundedCornerShape(10.dp),
                color = Color.Black.copy(alpha = 0.78f)
            ) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(maxWidth * dismissProgress)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.brand)
                    )
                    Text(
                        text = "Dismiss",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationMapActions(
    audioPreference: NavigationAudioPreference,
    isAudioPreferenceExpanded: Boolean,
    onAudioPreferenceExpandedChanged: (Boolean) -> Unit,
    isDebugRouteSimulationEnabled: Boolean,
    onAudioPreferenceChanged: (NavigationAudioPreference) -> Unit,
    onSimulateRouteForDebug: () -> Unit,
    onRecenter: () -> Unit,
    isRouteOverviewMode: Boolean,
    onToggleRouteOverview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalVendistriPalette.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.End
    ) {
        NavigationAudioPreferenceControl(
            preference = audioPreference,
            isExpanded = isAudioPreferenceExpanded,
            onExpandedChanged = onAudioPreferenceExpandedChanged,
            onSelect = onAudioPreferenceChanged
        )
        if (isDebugRouteSimulationEnabled) {
            VendistriIconButton(
                iconRes = R.drawable.ic_play_filled,
                contentDescription = "Simulate route",
                onClick = onSimulateRouteForDebug,
                size = NavigationMapControlSize,
                iconSize = NavigationMapControlIconSize,
                background = palette.mapChromeSurface,
                foreground = palette.mapChromeForeground
            )
        }
        VendistriIconButton(
            iconRes = if (isRouteOverviewMode) R.drawable.ic_location_north else R.drawable.ic_layers_filled,
            contentDescription = if (isRouteOverviewMode) "Follow route" else "Show full route",
            onClick = {
                if (isRouteOverviewMode) {
                    onRecenter()
                } else {
                    onToggleRouteOverview()
                }
            },
            size = NavigationMapControlSize,
            iconSize = NavigationMapControlIconSize,
            background = palette.mapChromeSurface,
            foreground = palette.mapChromeForeground
        )
    }
}

@Composable
private fun NavigationAudioPreferenceControl(
    preference: NavigationAudioPreference,
    isExpanded: Boolean,
    onExpandedChanged: (Boolean) -> Unit,
    onSelect: (NavigationAudioPreference) -> Unit
) {
    val palette = LocalVendistriPalette.current
    Surface(
        modifier = if (isExpanded) {
            Modifier.width(NavigationMapControlSize)
        } else {
            Modifier.size(NavigationMapControlSize)
        },
        shape = if (isExpanded) RoundedCornerShape(22.dp) else CircleShape,
        color = palette.mapChromeSurface,
        shadowElevation = if (isExpanded) 6.dp else 6.dp
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val options = if (isExpanded) NavigationAudioPreference.entries else listOf(preference)
            options.forEach { option ->
                Surface(
                    onClick = {
                        if (isExpanded) {
                            onSelect(option)
                        }
                        onExpandedChanged(!isExpanded)
                    },
                    modifier = Modifier.size(NavigationMapControlSize - 8.dp),
                    shape = CircleShape,
                    color = Color.Transparent
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(option.iconRes),
                            contentDescription = option.label,
                            tint = if (option == preference) palette.brand else palette.mapChromeForeground,
                            modifier = Modifier.size(NavigationMapControlIconSize)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationTripCard(
    workState: WorkUiState,
    navigationState: NavigationSessionState,
    timeFormatPreference: TimeFormatPreference,
    systemUses24Hour: Boolean?,
    allTasks: List<VendiTask>,
    locationsById: Map<String, AppLocation>,
    isArrivalCandidate: Boolean,
    isExpanded: Boolean,
    onExpandedChanged: (Boolean) -> Unit,
    onApplySharedNotes: suspend (List<String>, String?) -> Boolean,
    onCancelNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalVendistriPalette.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .pointerInput(workState.activeSession?.id, workState.activeExecution?.stopId) {
                detectDragGestures { _, dragAmount ->
                    when {
                        dragAmount.y < -18f -> onExpandedChanged(true)
                        dragAmount.y > 18f -> onExpandedChanged(false)
                    }
                }
            },
        color = palette.mapPanelSurface,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            if (isExpanded) {
                NavigationTripExpandedPanel(
                    stop = workState.selectedStop,
                    execution = workState.activeExecution,
                    localSession = workState.localActiveExecutionSession,
                    allTasks = allTasks,
                    postPickupDestinationStop = workState.postPickupDestination?.stopId?.let { stopId ->
                        workState.goPlan?.stops?.firstOrNull { it.id == stopId || it.targetLocationId == stopId }
                    },
                    postPickupDestinationTaskIds = workState.postPickupDestination?.sessionTaskIds.orEmpty(),
                    locationsById = locationsById,
                    timeFormatPreference = timeFormatPreference,
                    systemUses24Hour = systemUses24Hour,
                    onApplySharedNotes = onApplySharedNotes,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onExpandedChanged(!isExpanded)
                    }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavigationStopMenuButton(onCancelNavigation = onCancelNavigation)
                Spacer(modifier = Modifier.weight(1f))
                TripMetricStack(
                    navigationState = navigationState,
                    routePreview = workState.routePreview,
                    execution = workState.activeExecution,
                    allTasks = allTasks,
                    session = workState.activeSession,
                    destinationKind = workState.destinationKind,
                    isArrivalCandidate = isArrivalCandidate,
                    timeFormatPreference = timeFormatPreference,
                    systemUses24Hour = systemUses24Hour
                )
                Spacer(modifier = Modifier.weight(1f))
                VendistriIconButton(
                    iconRes = R.drawable.ic_sliders,
                    contentDescription = if (isExpanded) "Hide route details" else "Route details",
                    onClick = { onExpandedChanged(!isExpanded) },
                    size = 44.dp,
                    background = palette.surfaceVariant,
                    foreground = palette.textPrimary,
                    showsShadow = false
                )
            }
        }
    }
}

@Composable
private fun NavigationStopMenuButton(onCancelNavigation: () -> Unit) {
    var isMenuOpen by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { isMenuOpen = true },
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(22.dp),
            color = LocalVendistriPalette.current.surfaceVariant
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                repeat(3) {
                    Surface(
                        modifier = Modifier
                            .padding(vertical = 1.5.dp)
                            .size(4.dp),
                        shape = RoundedCornerShape(2.dp),
                        color = LocalVendistriPalette.current.textPrimary
                    ) {}
                }
            }
        }
        DropdownMenu(
            expanded = isMenuOpen,
            onDismissRequest = { isMenuOpen = false }
        ) {
            DropdownMenuItem(
                text = { Text("Cancel stop") },
                onClick = {
                    isMenuOpen = false
                    onCancelNavigation()
                }
            )
        }
    }
}

@Composable
private fun TripMetricStack(
    navigationState: NavigationSessionState,
    routePreview: RoutePreview?,
    execution: ActiveTaskExecution?,
    allTasks: List<VendiTask>,
    session: ActiveWorkSession?,
    destinationKind: WorkDestinationKind,
    isArrivalCandidate: Boolean,
    timeFormatPreference: TimeFormatPreference,
    systemUses24Hour: Boolean?
) {
    val palette = LocalVendistriPalette.current
    var nowMillis by remember(execution?.stopId, execution?.currentTaskId) {
        mutableLongStateOf(System.currentTimeMillis())
    }
    LaunchedEffect(execution?.stopId, execution?.currentTaskId) {
        while (execution != null) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val hydratedExecution = remember(execution, allTasks) {
        execution?.hydratedFrom(allTasks)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (isArrivalCandidate) {
                "Arrived"
            } else {
                navigationState.durationRemainingSeconds?.let { formatDuration((it / 60.0).coerceAtLeast(0.0)) }
                    ?: routePreview?.durationText
                    ?: "Route"
            },
            color = if (isArrivalCandidate) palette.success else navigationState.travelTimeTrafficLevel.etaColor(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (isArrivalCandidate) {
                session?.addressText ?: routePreview?.secondaryText ?: destinationKind.tripSubtitle
            } else {
                navigationState.tripSubtitle(timeFormatPreference, systemUses24Hour)
                    ?: routePreview?.secondaryText
                    ?: hydratedExecution?.let(TaskExecutionDisplay::progressText)
                    ?: destinationKind.tripSubtitle
            },
            color = palette.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun ActiveTaskExecution.hydratedFrom(allTasks: List<VendiTask>): ActiveTaskExecution {
    val hydratedTasks = TaskExecutionResolver.hydratedTasks(displayTasks, allTasks)
    return copy(
        displayTasks = hydratedTasks,
        tasks = hydratedTasks.map(::navigationExecutionTaskItem),
        machineGroups = TaskExecutionResolver.orderedMachineGroups(hydratedTasks)
    )
}

private fun navigationExecutionTaskItem(task: VendiTask): ExecutionTaskItem {
    return ExecutionTaskItem(
        id = task.id,
        type = task.type,
        status = task.status,
        machineId = task.machine,
        machineName = task.machineName,
        startedAt = task.startedAt,
        doneAt = task.doneAt,
        isWrapper = task.type == TaskType.MachineService
    )
}

@Composable
private fun NavigationTravelTimeTrafficLevel.etaColor(): Color {
    val palette = LocalVendistriPalette.current
    return when (this) {
        NavigationTravelTimeTrafficLevel.Clear -> palette.success
        NavigationTravelTimeTrafficLevel.SomeDelay -> palette.warning
        NavigationTravelTimeTrafficLevel.HeavyDelay -> palette.error
    }
}

@Composable
private fun navigationSpeedColor(currentSpeed: Double, speedLimit: Double): Color {
    val palette = LocalVendistriPalette.current
    if (!currentSpeed.isFinite() || !speedLimit.isFinite() || speedLimit <= 0.0) {
        return palette.success
    }
    if (currentSpeed <= speedLimit) return palette.success
    val warningLimit = speedLimit + maxOf(5.0, speedLimit * 0.10)
    return if (currentSpeed <= warningLimit) palette.warning else palette.error
}

private val NavigationTrafficAlert.warningGlyph: String
    get() = when (kind) {
        NavigationTrafficAlert.Kind.Closure -> "×"
        NavigationTrafficAlert.Kind.Congestion -> "!"
        else -> "!"
    }

private fun NavigationSessionState.tripSubtitle(
    timeFormatPreference: TimeFormatPreference,
    systemUses24Hour: Boolean?
): String? {
    val distance = distanceRemainingMiles ?: return null
    val distanceText = "${oneDecimal(distance.coerceAtLeast(0.0))} mi"
    val duration = durationRemainingSeconds
    return if (duration != null) {
        "${AppTimeFormatter.arrivalTime(duration.coerceAtLeast(0.0), timeFormatPreference, systemUses24Hour)} • $distanceText"
    } else {
        distanceText
    }
}

private val WorkPhase.navigationInstruction: String
    get() = when (this) {
        WorkPhase.NavigatingToWarehouse -> "ROUTE TO WAREHOUSE"
        WorkPhase.NavigatingToLocation -> "ROUTE TO LOCATION"
        else -> "ROUTE"
    }

private val WorkUiState.destinationKind: WorkDestinationKind
    get() = activeExecution?.destinationKind
        ?: selectedStop?.destinationKind
        ?: activeSession?.destinationKind
        ?: WorkDestinationKind.Location

private val WorkDestinationKind.tripSubtitle: String
    get() = when (this) {
        WorkDestinationKind.Location -> "Navigating to location"
        WorkDestinationKind.Warehouse -> "Navigating to warehouse"
    }

private val ActiveWorkSession.addressText: String?
    get() = listOfNotNull(addressStreetLine, addressCityStateZipLine)
        .filter { it.isNotBlank() }
        .joinToString(", ")
        .ifBlank { null }

private fun maneuverGlyph(type: String?, modifier: String?): String {
    val normalizedType = type.orEmpty().lowercase()
    val normalizedModifier = modifier.orEmpty().lowercase()
    return when {
        normalizedType == "arrive" && normalizedModifier.contains("right") -> "↑•"
        normalizedType == "arrive" && normalizedModifier.contains("left") -> "•↑"
        normalizedType == "arrive" -> "↑"
        normalizedType == "depart" -> "↑"
        normalizedType.contains("roundabout") || normalizedType.contains("rotary") -> "↻"
        normalizedModifier.contains("left") && normalizedModifier.contains("sharp") -> "↰"
        normalizedModifier.contains("right") && normalizedModifier.contains("sharp") -> "↱"
        normalizedModifier.contains("left") -> "↰"
        normalizedModifier.contains("right") -> "↱"
        normalizedModifier.contains("uturn") -> "↶"
        normalizedType.contains("merge") -> "↗"
        normalizedType.contains("fork") -> "↗"
        else -> "↑"
    }
}

private val NavigationAudioPreference.iconRes: Int
    get() = when (this) {
        NavigationAudioPreference.Sound -> R.drawable.ic_speaker
        NavigationAudioPreference.Silent -> R.drawable.ic_speaker_off
    }

private fun String.navigationArrivalText(destinationTitle: String?): String {
    val title = destinationTitle?.takeIf { it.isNotBlank() } ?: return this
    return replace("Your destination", title, ignoreCase = true)
        .replace("your destination", title, ignoreCase = true)
}

private fun String.arrivalSideDotText(): String {
    val trimmed = trim()
    if (!trimmed.startsWith("•") && !trimmed.startsWith("·")) return trimmed
    if (!trimmed.contains(" on the right", ignoreCase = true) &&
        !trimmed.contains(" on the left", ignoreCase = true)
    ) {
        return trimmed
    }
    return trimmed.drop(1).trimStart().let { "$it •" }
}

private fun formatDistanceParts(distanceMeters: Double): Pair<String, String> {
    val meters = distanceMeters.coerceAtLeast(0.0)
    val feet = meters * 3.28084
    if (feet < 1_000) {
        val roundedFeet = when {
            feet < 100 -> (feet / 10.0).toInt() * 10
            else -> (feet / 50.0).toInt() * 50
        }.coerceAtLeast(1)
        return roundedFeet.toString() to "ft"
    }
    val miles = meters / 1609.344
    return if (miles < 10) {
        oneDecimal(miles) to "mi"
    } else {
        miles.toInt().toString() to "mi"
    }
}

private val RoutePreview.durationText: String
    get() = formatDuration((expectedTravelSeconds / 60.0).coerceAtLeast(0.0))

private val RoutePreview.secondaryText: String
    get() = "${oneDecimal(distanceMiles.coerceAtLeast(0.0))} mi"
