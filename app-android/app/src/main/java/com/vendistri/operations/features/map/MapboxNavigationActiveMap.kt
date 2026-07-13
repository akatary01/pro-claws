package com.vendistri.operations.features.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.mapbox.api.directions.v5.models.BannerComponents
import com.mapbox.api.directions.v5.models.BannerText
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.api.directions.v5.models.Incident
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.common.location.Location
import com.mapbox.common.location.LocationObserver as CommonLocationObserver
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.ImageHolder
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.ViewAnnotationAnchor
import com.mapbox.maps.ViewAnnotationAnchorConfig
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.attribution.attribution
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.locationcomponent.LocationConsumer
import com.mapbox.maps.plugin.locationcomponent.LocationProvider as MapsLocationProvider
import com.mapbox.maps.plugin.logo.logo
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.OnRotateListener
import com.mapbox.maps.plugin.gestures.OnScaleListener
import com.mapbox.maps.plugin.gestures.OnShoveListener
import com.mapbox.maps.plugin.gestures.addOnMoveListener
import com.mapbox.maps.plugin.gestures.addOnRotateListener
import com.mapbox.maps.plugin.gestures.addOnScaleListener
import com.mapbox.maps.plugin.gestures.addOnShoveListener
import com.mapbox.maps.plugin.gestures.removeOnMoveListener
import com.mapbox.maps.plugin.gestures.removeOnRotateListener
import com.mapbox.maps.plugin.gestures.removeOnScaleListener
import com.mapbox.maps.plugin.gestures.removeOnShoveListener
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.MapboxNavigationProvider
import com.mapbox.navigation.core.reroute.RerouteController
import com.mapbox.navigation.core.reroute.RerouteState
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.replay.ReplayLocationProvider
import com.mapbox.navigation.core.replay.route.ReplayRouteMapper
import com.mapbox.navigation.core.replay.route.ReplayRouteOptions
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowApi
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowView
import com.mapbox.navigation.ui.maps.route.arrow.model.RouteArrowOptions
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import com.mapbox.navigation.ui.maps.camera.transition.NavigationCameraTransitionOptions
import com.vendistri.operations.R
import com.vendistri.operations.features.navigation.NavigationCurrentSpeedDisplay
import com.vendistri.operations.features.navigation.NavigationInstructionComponent
import com.vendistri.operations.features.navigation.MapboxRerouteObserverBridge
import com.vendistri.operations.features.navigation.NavigationInstructionStep
import com.vendistri.operations.features.navigation.NavigationSpeedLimitDisplay
import com.vendistri.operations.features.navigation.NavigationTrafficAlert
import com.vendistri.operations.features.navigation.NavigationTravelTimeTrafficLevel
import com.vendistri.operations.features.navigation.DebugRouteSimulationConfig
import kotlinx.coroutines.delay
import java.util.Locale
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.android.gestures.RotateGestureDetector
import com.mapbox.android.gestures.ShoveGestureDetector
import com.mapbox.android.gestures.StandardScaleGestureDetector
import kotlin.math.roundToInt

data class MapNavigationRoute(
    val id: String,
    val origin: LocationCoordinate,
    val destination: LocationCoordinate,
    val destinationTitle: String
)

data class MapNavigationProgress(
    val instructionText: String?,
    val distanceRemainingMiles: Double?,
    val durationRemainingSeconds: Double?,
    val travelTimeTrafficLevel: NavigationTravelTimeTrafficLevel = NavigationTravelTimeTrafficLevel.Clear,
    val trafficAlert: NavigationTrafficAlert? = null,
    val roadNameText: String? = null,
    val currentSpeed: NavigationCurrentSpeedDisplay? = null,
    val speedLimit: NavigationSpeedLimitDisplay? = null,
    val currentInstruction: NavigationInstructionStep? = null,
    val futureInstructionSteps: List<NavigationInstructionStep> = emptyList(),
    val isRerouting: Boolean? = null
)

@OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
@SuppressLint("MissingPermission")
@Composable
fun MapboxNavigationActiveMap(
    route: MapNavigationRoute,
    hasLocationPermission: Boolean,
    onMapReadyChanged: (Boolean) -> Unit,
    onUserLocationChanged: (LocationCoordinate) -> Unit,
    onNavigationProgress: (MapNavigationProgress) -> Unit,
    onUserCameraInteraction: () -> Unit,
    isRouteOverviewMode: Boolean,
    isCameraDetached: Boolean,
    isArrivalCandidate: Boolean,
    followCameraRequest: Int,
    debugRouteSimulationRequest: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val mapViewState = remember { mutableStateOf<MapView?>(null) }
    val destinationPillViewState = remember(route.id) { mutableStateOf<View?>(null) }
    val viewportDataSourceState = remember { mutableStateOf<MapboxNavigationViewportDataSource?>(null) }
    val navigationCameraState = remember { mutableStateOf<NavigationCamera?>(null) }
    val hasLoadedMap = remember(route.id) { mutableStateOf(false) }
    val hasRenderedRouteLine = remember(route.id) { mutableStateOf(false) }
    val hasAppliedInitialRouteCamera = remember(route.id) { mutableStateOf(false) }
    val hasSettledInitialRouteRender = remember(route.id) { mutableStateOf(false) }
    val hasNavigationReadinessFallback = remember(route.id) { mutableStateOf(false) }
    val latestNavigationRoutes = remember(route.id) { mutableStateOf<List<NavigationRoute>>(emptyList()) }
    val latestDirectionsRoute = remember(route.id) { mutableStateOf<DirectionsRoute?>(null) }
    val latestSpeedMetersPerSecond = remember(route.id) { mutableStateOf<Double?>(null) }
    val latestEnhancedLocationPoint = remember(route.id) { mutableStateOf<Point?>(null) }
    val isUserCameraControlled = remember(route.id) { mutableStateOf(false) }
    val requestedCameraMode = remember(route.id) { mutableStateOf<Pair<Boolean, Boolean>?>(null) }
    val isDebugRouteSimulationActive = remember(route.id) { mutableStateOf(false) }
    val debugMatchedLocationLogCount = remember(route.id) { mutableStateOf(0) }
    val isNavigationDisposed = remember { mutableStateOf(false) }
    val preferredSpeedUnit = remember { preferredSpeedUnitForLocale() }
    val navigationLocationProvider = remember { NavigationLocationProvider() }
    val routeLineApi = remember {
        MapboxRouteLineApi(
            MapboxRouteLineApiOptions.Builder()
                .vanishingRouteLineEnabled(true)
                .isRouteCalloutsEnabled(false)
                .build()
        )
    }
    val routeLineView = remember { MapboxRouteLineView(MapboxRouteLineViewOptions.Builder(context).build()) }
    val routeArrowApi = remember { MapboxRouteArrowApi() }
    val routeArrowView = remember {
        MapboxRouteArrowView(
            RouteArrowOptions.Builder(context)
                .withArrowColor(AndroidColor.WHITE)
                .withArrowCasingColor(AndroidColor.rgb(11, 107, 239))
                .build()
        )
    }
    val mapboxNavigation = remember {
        MapboxNavigationProvider.create(NavigationOptions.Builder(context).build())
    }
    val replayLocationProvider = remember(mapboxNavigation) {
        ReplayLocationProvider(mapboxNavigation.mapboxReplayer)
    }
    val replayPuckLocationProvider = remember(replayLocationProvider) {
        ReplayPuckLocationProvider(replayLocationProvider)
    }
    val onMapReadyChangedState = rememberUpdatedState(onMapReadyChanged)
    val isRouteOverviewModeState = rememberUpdatedState(isRouteOverviewMode)
    val isCameraDetachedState = rememberUpdatedState(isCameraDetached)
    val isArrivalCandidateState = rememberUpdatedState(isArrivalCandidate)
    val hasLocationPermissionState = rememberUpdatedState(hasLocationPermission)
    fun isNavigationMapReadyNow(): Boolean {
        return hasNavigationReadinessFallback.value || (hasLoadedMap.value &&
            hasRenderedRouteLine.value &&
            hasAppliedInitialRouteCamera.value &&
            hasSettledInitialRouteRender.value)
    }
    val isNavigationMapReady = isNavigationMapReadyNow()
    val cameraTransitionOptions = remember {
        NavigationCameraTransitionOptions.Builder()
            .maxDuration(650L)
            .build()
    }

    fun applyCameraMode(isOverview: Boolean) {
        if (isCameraDetachedState.value) return
        val viewportDataSource = viewportDataSourceState.value ?: return
        val navigationCamera = navigationCameraState.value ?: return
        val cameraModeKey = isOverview to isArrivalCandidateState.value
        if (requestedCameraMode.value == cameraModeKey && !isUserCameraControlled.value) return
        requestedCameraMode.value = cameraModeKey
        if (isArrivalCandidateState.value) {
            val mapView = mapViewState.value ?: return
            val center = latestEnhancedLocationPoint.value
                ?: Point.fromLngLat(route.origin.longitude, route.origin.latitude)
            isUserCameraControlled.value = false
            navigationCamera.requestNavigationCameraToIdle()
            viewportDataSource.clearOverviewOverrides()
            viewportDataSource.clearFollowingOverrides()
            mapView.camera.easeTo(
                CameraOptions.Builder()
                    .center(center)
                    .padding(navigationEdgeInsets(density, top = 110.dp, bottom = 180.dp))
                    .zoom(NavigationArrivalZoom)
                    .bearing(0.0)
                    .pitch(0.0)
                    .build(),
                MapAnimationOptions.Builder()
                    .duration(650L)
                    .build()
            )
            return
        }
        if (isOverview) {
            isUserCameraControlled.value = false
            viewportDataSource.clearFollowingOverrides()
            viewportDataSource.clearOverviewOverrides()
            viewportDataSource.overviewPitchPropertyOverride(0.0)
            viewportDataSource.overviewBearingPropertyOverride(0.0)
            viewportDataSource.evaluate()
            navigationCamera.requestNavigationCameraToOverview(cameraTransitionOptions)
        } else {
            isUserCameraControlled.value = false
            viewportDataSource.clearOverviewOverrides()
            viewportDataSource.clearFollowingOverrides()
            viewportDataSource.followingPitchPropertyOverride(NavigationFollowingPitch)
            viewportDataSource.followingZoomPropertyOverride(NavigationFollowingZoom)
            viewportDataSource.evaluate()
            navigationCamera.requestNavigationCameraToFollowing(cameraTransitionOptions)
        }
    }

    fun renderTraveledRouteLine(point: Point) {
        if (!hasRenderedRouteLine.value) return
        val style = mapViewState.value?.mapboxMap?.style ?: return
        routeLineView.renderRouteLineUpdate(
            style,
            routeLineApi.updateTraveledRouteLine(point)
        )
    }

    fun renderNavigationRoutes(mapView: MapView, routes: List<NavigationRoute>) {
        if (routes.isEmpty()) {
            hasRenderedRouteLine.value = false
            hasAppliedInitialRouteCamera.value = false
            hasSettledInitialRouteRender.value = false
            routeLineApi.clearRouteLine { value ->
                mapView.mapboxMap.style?.let { style ->
                    routeLineView.renderClearRouteLineValue(style, value)
                    routeArrowView.render(style, routeArrowApi.clearArrows())
                }
            }
            return
        }
        val style = mapView.mapboxMap.style ?: return
        routeLineApi.setNavigationRoutes(routes) { value ->
            routeLineView.renderRouteDrawData(style, value)
            routeLineView.hideOriginAndDestinationPoints(style)
            hasRenderedRouteLine.value = true
            hasSettledInitialRouteRender.value = false
            latestEnhancedLocationPoint.value?.let(::renderTraveledRouteLine)
        }
    }

    fun refreshVisibleNavigationRoute() {
        val mapView = mapViewState.value ?: return
        val routes = latestNavigationRoutes.value
        if (routes.isEmpty()) return
        renderNavigationRoutes(mapView, routes)
    }

    fun markNavigationMapUsable() {
        hasNavigationReadinessFallback.value = true
        hasAppliedInitialRouteCamera.value = true
        hasSettledInitialRouteRender.value = true
        if (!isUserCameraControlled.value && !isCameraDetachedState.value) {
            applyCameraMode(isRouteOverviewModeState.value)
        }
    }

    val routesObserver = remember {
        RoutesObserver { routeUpdate ->
            val mapView = mapViewState.value ?: return@RoutesObserver
            val routes = routeUpdate.navigationRoutes
            latestNavigationRoutes.value = routes
            latestDirectionsRoute.value = routes.firstOrNull()?.directionsRoute
            if (routes.isEmpty()) {
                renderNavigationRoutes(mapView, routes)
                return@RoutesObserver
            }
            renderNavigationRoutes(mapView, routes)
            viewportDataSourceState.value?.onRouteChanged(routes.first())
            viewportDataSourceState.value?.evaluate()
            if (!isUserCameraControlled.value && !isCameraDetachedState.value) {
                applyCameraMode(isRouteOverviewModeState.value || isArrivalCandidateState.value)
            }
            hasAppliedInitialRouteCamera.value = true
        }
    }
    val rerouteStateObserver = remember {
        object : RerouteController.RerouteStateObserver {
            override fun onRerouteStateChanged(rerouteState: RerouteState) {
                onNavigationProgress(
                    MapNavigationProgress(
                        instructionText = null,
                        distanceRemainingMiles = null,
                        durationRemainingSeconds = null,
                        isRerouting = rerouteState is RerouteState.FetchingRoute
                    )
                )
            }
        }
    }

    val locationObserver = remember {
        object : LocationObserver {
            override fun onNewRawLocation(rawLocation: Location) {
                if (isDebugRouteSimulationActive.value) return
                onUserLocationChanged(
                    LocationCoordinate(
                        latitude = rawLocation.latitude,
                        longitude = rawLocation.longitude
                    )
                )
                viewportDataSourceState.value?.onLocationChanged(rawLocation)
                viewportDataSourceState.value?.evaluate()
                if (!isRouteOverviewModeState.value && !isUserCameraControlled.value) {
                    viewportDataSourceState.value?.evaluate()
                }
            }

            override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
                val enhancedLocation = locationMatcherResult.enhancedLocation
                if (isDebugRouteSimulationActive.value && debugMatchedLocationLogCount.value < 8) {
                    debugMatchedLocationLogCount.value += 1
                    Log.d(
                        DebugRouteSimulationLogTag,
                        "matched location #${debugMatchedLocationLogCount.value} " +
                            "lat=${enhancedLocation.latitude} lng=${enhancedLocation.longitude} " +
                            "speed=${enhancedLocation.speed}"
                        )
                }
                if (isDebugRouteSimulationActive.value) {
                    onUserLocationChanged(
                        LocationCoordinate(
                            latitude = enhancedLocation.latitude,
                            longitude = enhancedLocation.longitude
                        )
                    )
                }
                navigationLocationProvider.changePosition(enhancedLocation, locationMatcherResult.keyPoints)
                viewportDataSourceState.value?.onLocationChanged(enhancedLocation)
                viewportDataSourceState.value?.evaluate()
                latestSpeedMetersPerSecond.value = enhancedLocation.speed?.takeIf { it.isFinite() && it >= 0.0 }
                val enhancedPoint = Point.fromLngLat(enhancedLocation.longitude, enhancedLocation.latitude)
                latestEnhancedLocationPoint.value = enhancedPoint
                renderTraveledRouteLine(enhancedPoint)
            }
        }
    }

    val routeProgressObserver = remember {
        RouteProgressObserver { routeProgress ->
            val instruction = currentInstructionStep(routeProgress)
            if (!hasRenderedRouteLine.value && latestNavigationRoutes.value.isNotEmpty()) {
                refreshVisibleNavigationRoute()
            }
            if (hasRenderedRouteLine.value) {
                routeLineApi.updateWithRouteProgress(routeProgress) { value ->
                    mapViewState.value?.mapboxMap?.style?.let { style ->
                        routeLineView.renderRouteLineUpdate(style, value)
                        routeArrowView.renderManeuverUpdate(
                            style,
                            routeArrowApi.addUpcomingManeuverArrow(routeProgress)
                        )
                        latestEnhancedLocationPoint.value?.let(::renderTraveledRouteLine)
                    }
                }
            }
            viewportDataSourceState.value?.onRouteProgressChanged(routeProgress)
            viewportDataSourceState.value?.evaluate()
            markNavigationMapUsable()
            onNavigationProgress(
                navigationProgressFromRouteProgress(
                    routeProgress = routeProgress,
                    instruction = instruction,
                    currentSpeedMetersPerSecond = latestSpeedMetersPerSecond.value,
                    preferredSpeedUnit = preferredSpeedUnit
                )
            )
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(
                ctx,
                MapInitOptions(
                    context = ctx,
                    cameraOptions = CameraOptions.Builder()
                        .center(Point.fromLngLat(route.origin.longitude, route.origin.latitude))
                        .zoom(15.0)
                        .pitch(45.0)
                        .build()
                )
            ).apply {
                scalebar.enabled = false
                visibility = View.INVISIBLE
                compass.enabled = false
                // Navigation chrome is intentionally layered over the map. Keep
                // Mapbox ornaments at the map's true bottom edge so the trip panel
                // covers that corner instead of lifting them into the route view.
                logo.marginBottom = 0f
                attribution.marginBottom = 0f
                val userGestureStarted = {
                    isUserCameraControlled.value = true
                    requestedCameraMode.value = null
                    navigationCameraState.value?.requestNavigationCameraToIdle()
                    onUserCameraInteraction()
                }
                val moveListener = object : OnMoveListener {
                    override fun onMoveBegin(detector: MoveGestureDetector) {
                        userGestureStarted()
                    }
                    override fun onMove(detector: MoveGestureDetector): Boolean = false
                    override fun onMoveEnd(detector: MoveGestureDetector) = Unit
                }
                val scaleListener = object : OnScaleListener {
                    override fun onScaleBegin(detector: StandardScaleGestureDetector) {
                        userGestureStarted()
                    }
                    override fun onScale(detector: StandardScaleGestureDetector) = Unit
                    override fun onScaleEnd(detector: StandardScaleGestureDetector) = Unit
                }
                val rotateListener = object : OnRotateListener {
                    override fun onRotateBegin(detector: RotateGestureDetector) {
                        userGestureStarted()
                    }
                    override fun onRotate(detector: RotateGestureDetector) = Unit
                    override fun onRotateEnd(detector: RotateGestureDetector) = Unit
                }
                val shoveListener = object : OnShoveListener {
                    override fun onShoveBegin(detector: ShoveGestureDetector) {
                        userGestureStarted()
                    }
                    override fun onShove(detector: ShoveGestureDetector) = Unit
                    override fun onShoveEnd(detector: ShoveGestureDetector) = Unit
                }
                mapboxMap.addOnMoveListener(moveListener)
                mapboxMap.addOnScaleListener(scaleListener)
                mapboxMap.addOnRotateListener(rotateListener)
                mapboxMap.addOnShoveListener(shoveListener)
                location.apply {
                    setLocationProvider(navigationLocationProvider)
                    locationPuck = LocationPuck2D(
                        topImage = ImageHolder.from(R.drawable.ic_user_location_arrow),
                        bearingImage = ImageHolder.from(R.drawable.ic_user_location_arrow)
                    )
                    puckBearing = PuckBearing.HEADING
                    puckBearingEnabled = true
                    pulsingEnabled = false
                    showAccuracyRing = false
                    enabled = true
                }
                val mapLoaded = mapboxMap.subscribeMapLoaded {
                    hasLoadedMap.value = true
                }
                addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(view: android.view.View) = Unit

                    override fun onViewDetachedFromWindow(view: android.view.View) {
                        mapLoaded.cancel()
                        mapboxMap.removeOnMoveListener(moveListener)
                        mapboxMap.removeOnScaleListener(scaleListener)
                        mapboxMap.removeOnRotateListener(rotateListener)
                        mapboxMap.removeOnShoveListener(shoveListener)
                        removeOnAttachStateChangeListener(this)
                    }
                })
                mapboxMap.loadStyle(Style.STANDARD) {
                    hasLoadedMap.value = true
                    renderNavigationRoutes(this, latestNavigationRoutes.value)
                    destinationPillViewState.value?.let { viewAnnotationManager.removeViewAnnotation(it) }
                    destinationPillViewState.value = addDestinationPill(
                        mapView = this,
                        title = route.destinationTitle,
                        point = Point.fromLngLat(route.destination.longitude, route.destination.latitude)
                    )
                }
                mapViewState.value = this
                viewportDataSourceState.value = MapboxNavigationViewportDataSource(mapboxMap).also { viewport ->
                    viewport.followingPadding = navigationEdgeInsets(density, top = 140.dp, bottom = 250.dp)
                    viewport.overviewPadding = navigationEdgeInsets(density, top = 188.dp, bottom = 250.dp)
                }
                navigationCameraState.value = NavigationCamera(
                    mapboxMap,
                    camera,
                    viewportDataSourceState.value!!
                )
            }
        },
        update = { mapView ->
            mapView.visibility = if (isNavigationMapReady) View.VISIBLE else View.INVISIBLE
        }
    )

    LaunchedEffect(hasLoadedMap.value, latestNavigationRoutes.value) {
        val mapView = mapViewState.value ?: return@LaunchedEffect
        if (hasLoadedMap.value && latestNavigationRoutes.value.isNotEmpty()) {
            renderNavigationRoutes(mapView, latestNavigationRoutes.value)
        }
    }

    LaunchedEffect(
        route.id,
        hasLoadedMap.value,
        hasRenderedRouteLine.value,
        hasAppliedInitialRouteCamera.value
    ) {
        hasSettledInitialRouteRender.value = false
        if (!hasLoadedMap.value || !hasRenderedRouteLine.value || !hasAppliedInitialRouteCamera.value) {
            return@LaunchedEffect
        }
        delay(900L)
        hasSettledInitialRouteRender.value = true
    }

    LaunchedEffect(isNavigationMapReady) {
        if (isNavigationMapReady) {
            onMapReadyChangedState.value(true)
        }
    }

    LaunchedEffect(route.id) {
        if (isNavigationMapReadyNow()) return@LaunchedEffect
        delay(8_000L)
        if (!isNavigationMapReadyNow()) {
            markNavigationMapUsable()
        }
    }

    LaunchedEffect(isRouteOverviewMode, isCameraDetached) {
        if (isCameraDetached) return@LaunchedEffect
        applyCameraMode(isRouteOverviewMode || isArrivalCandidateState.value)
        refreshVisibleNavigationRoute()
    }

    LaunchedEffect(isArrivalCandidate, isCameraDetached) {
        if (isCameraDetached) return@LaunchedEffect
        applyCameraMode(isRouteOverviewModeState.value || isArrivalCandidate)
        refreshVisibleNavigationRoute()
    }

    LaunchedEffect(followCameraRequest) {
        if (followCameraRequest <= 0) return@LaunchedEffect
        isUserCameraControlled.value = false
        applyCameraMode(isOverview = false)
        refreshVisibleNavigationRoute()
    }

    LaunchedEffect(debugRouteSimulationRequest) {
        if (debugRouteSimulationRequest <= 0) return@LaunchedEffect
        if (!DebugRouteSimulationConfig.isEnabled) {
            Log.d(DebugRouteSimulationLogTag, "ignored request=$debugRouteSimulationRequest disabled")
            return@LaunchedEffect
        }
        var directionsRoute = latestDirectionsRoute.value
            ?: latestNavigationRoutes.value.firstOrNull()?.directionsRoute
        var routeWaitAttempts = 0
        while (directionsRoute == null && routeWaitAttempts < DebugRouteWaitAttempts) {
            delay(DebugRouteWaitDelayMillis)
            routeWaitAttempts += 1
            directionsRoute = latestDirectionsRoute.value
                ?: latestNavigationRoutes.value.firstOrNull()?.directionsRoute
        }
        if (directionsRoute == null) {
            Log.d(DebugRouteSimulationLogTag, "ignored request=$debugRouteSimulationRequest no route")
            return@LaunchedEffect
        }
        val replayEvents = ReplayRouteMapper(
            ReplayRouteOptions.Builder()
                .maxSpeedMps(DebugSimulationSpeedLimitMetersPerSecond)
                .turnSpeedMps(DebugSimulationTurnSpeedMetersPerSecond)
                .uTurnSpeedMps(DebugSimulationUTurnSpeedMetersPerSecond)
                .build()
        ).mapDirectionsRouteGeometry(directionsRoute)
        if (replayEvents.isEmpty()) {
            Log.d(DebugRouteSimulationLogTag, "ignored request=$debugRouteSimulationRequest no replay events")
            return@LaunchedEffect
        }
        Log.d(
            DebugRouteSimulationLogTag,
                "starting request=$debugRouteSimulationRequest events=${replayEvents.size} " +
                "duration=${mapboxNavigation.mapboxReplayer.durationSeconds()} " +
                "isReplay=${mapboxNavigation.isReplayEnabled()}"
        )
        isDebugRouteSimulationActive.value = true
        debugMatchedLocationLogCount.value = 0
        isUserCameraControlled.value = false
        applyCameraMode(isOverview = false)
        refreshVisibleNavigationRoute()
        mapViewState.value?.location?.setLocationProvider(replayPuckLocationProvider)
        mapboxNavigation.mapboxReplayer.stop()
        mapboxNavigation.mapboxReplayer.clearEvents()
        mapboxNavigation.mapboxReplayer.pushEvents(replayEvents)
        mapboxNavigation.mapboxReplayer.seekTo(replayEvents.first())
        mapboxNavigation.mapboxReplayer.playFirstLocation()
        mapboxNavigation.startReplayTripSession()
        mapboxNavigation.mapboxReplayer.playbackSpeed(DebugSimulationPlaybackSpeed)
        mapboxNavigation.mapboxReplayer.play()
        Log.d(
            DebugRouteSimulationLogTag,
                "playing request=$debugRouteSimulationRequest duration=${mapboxNavigation.mapboxReplayer.durationSeconds()} " +
                "isReplay=${mapboxNavigation.isReplayEnabled()}"
        )
    }

    LaunchedEffect(route.id, hasLocationPermission) {
        val origin = Point.fromLngLat(route.origin.longitude, route.origin.latitude)
        val destination = Point.fromLngLat(route.destination.longitude, route.destination.latitude)
        val routeOptionsBuilder = RouteOptions.builder()
            .applyDefaultNavigationOptions()
            .coordinatesList(listOf(origin, destination))
        if (hasLocationPermissionState.value) {
            routeOptionsBuilder.layersList(listOf(mapboxNavigation.getZLevel(), null))
        }
        mapboxNavigation.requestRoutes(
            routeOptionsBuilder.build(),
            object : NavigationRouterCallback {
                override fun onCanceled(routeOptions: RouteOptions, routerOrigin: String) {
                    markNavigationMapUsable()
                }

                override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                    markNavigationMapUsable()
                }

                override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: String) {
                    if (isNavigationDisposed.value) return
                    if (routes.isEmpty()) {
                        latestDirectionsRoute.value = null
                        markNavigationMapUsable()
                        return
                    }
                    latestDirectionsRoute.value = routes.firstOrNull()?.directionsRoute
                    mapboxNavigation.setNavigationRoutes(routes)
                    val firstRoute = routes.firstOrNull()?.directionsRoute
                    onNavigationProgress(
                        MapNavigationProgress(
                            instructionText = null,
                            distanceRemainingMiles = firstRoute?.distance()?.div(MetersPerMile),
                            durationRemainingSeconds = firstRoute?.duration()
                        )
                    )
                }
            }
        )
    }

    DisposableEffect(mapboxNavigation) {
        isNavigationDisposed.value = false
        mapboxNavigation.registerRoutesObserver(routesObserver)
        MapboxRerouteObserverBridge.register(mapboxNavigation, rerouteStateObserver)
        mapboxNavigation.registerLocationObserver(locationObserver)
        mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)
        mapboxNavigation.startTripSession()
        onDispose {
            isNavigationDisposed.value = true
            isDebugRouteSimulationActive.value = false
            mapboxNavigation.mapboxReplayer.stop()
            mapboxNavigation.mapboxReplayer.clearEvents()
            mapboxNavigation.unregisterRoutesObserver(routesObserver)
            MapboxRerouteObserverBridge.unregister(mapboxNavigation, rerouteStateObserver)
            mapboxNavigation.unregisterLocationObserver(locationObserver)
            mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
            mapboxNavigation.setNavigationRoutes(emptyList())
            mapboxNavigation.stopTripSession()
            routeLineApi.cancel()
            routeLineView.cancel()
            destinationPillViewState.value?.let { view ->
                mapViewState.value?.viewAnnotationManager?.removeViewAnnotation(view)
            }
            destinationPillViewState.value = null
            MapboxNavigationProvider.destroy()
            mapViewState.value = null
            viewportDataSourceState.value = null
            navigationCameraState.value = null
        }
    }
}

private const val MetersPerMile = 1609.344
private const val NavigationFollowingPitch = 68.0
private const val NavigationFollowingZoom = 16.4
private const val NavigationArrivalZoom = 14.8
private const val DebugSimulationPlaybackSpeed = 1.0
private const val DebugSimulationSpeedLimitMetersPerSecond = 11.176
private const val DebugSimulationTurnSpeedMetersPerSecond = 4.47
private const val DebugSimulationUTurnSpeedMetersPerSecond = 2.24
private const val DebugRouteSimulationLogTag = "VendiDebugRoute"
private const val DebugRouteWaitAttempts = 20
private const val DebugRouteWaitDelayMillis = 250L

private fun navigationEdgeInsets(
    density: Density,
    top: Dp,
    bottom: Dp,
    left: Dp = 24.dp,
    right: Dp = 24.dp
): EdgeInsets {
    return with(density) {
        EdgeInsets(
            top.toPx().toDouble(),
            right.toPx().toDouble(),
            bottom.toPx().toDouble(),
            left.toPx().toDouble()
        )
    }
}

private class ReplayPuckLocationProvider(
    private val replayLocationProvider: ReplayLocationProvider
) : MapsLocationProvider {
    private val consumers = linkedSetOf<LocationConsumer>()
    private val replayObserver = object : CommonLocationObserver {
        override fun onLocationUpdateReceived(locations: List<Location>) {
            val location = locations.lastOrNull() ?: return
            val point = location.altitude?.let { altitude ->
                Point.fromLngLat(location.longitude, location.latitude, altitude)
            } ?: Point.fromLngLat(location.longitude, location.latitude)
            consumers.toList().forEach { consumer ->
                consumer.onLocationUpdated(point)
                location.bearing?.let { bearing -> consumer.onBearingUpdated(bearing) }
                location.horizontalAccuracy?.let { accuracy ->
                    consumer.onHorizontalAccuracyRadiusUpdated(accuracy)
                }
            }
        }
    }

    override fun registerLocationConsumer(locationConsumer: LocationConsumer) {
        val wasEmpty = consumers.isEmpty()
        consumers += locationConsumer
        if (wasEmpty) {
            replayLocationProvider.addLocationObserver(replayObserver)
        }
        replayLocationProvider.getLastLocation { location ->
            if (location != null) {
                replayObserver.onLocationUpdateReceived(listOf(location))
            }
        }
    }

    override fun unRegisterLocationConsumer(locationConsumer: LocationConsumer) {
        consumers -= locationConsumer
        if (consumers.isEmpty()) {
            replayLocationProvider.removeLocationObserver(replayObserver)
        }
    }
}

private fun addDestinationPill(
    mapView: MapView,
    title: String,
    point: Point
): View {
    val density = mapView.resources.displayMetrics.density
    val textView = TextView(mapView.context).apply {
        text = title.trim().ifBlank { "Destination" }
        setTextColor(AndroidColor.rgb(28, 28, 30))
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        maxLines = 2
        gravity = android.view.Gravity.CENTER
        includeFontPadding = false
        setPadding(
            (12f * density).toInt(),
            (7f * density).toInt(),
            (12f * density).toInt(),
            (7f * density).toInt()
        )
        maxWidth = (244f * density).toInt()
        minWidth = (68f * density).toInt()
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 18f * density
            setColor(AndroidColor.argb(245, 255, 255, 255))
            setStroke((1f * density).coerceAtLeast(1f).toInt(), AndroidColor.argb(115, 142, 142, 147))
        }
        elevation = 3f * density
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    mapView.viewAnnotationManager.addViewAnnotation(
        textView,
        viewAnnotationOptions {
            geometry(point)
            allowOverlap(true)
            allowOverlapWithPuck(true)
            visible(true)
            priority(10)
            variableAnchors(
                listOf(
                    ViewAnnotationAnchorConfig.Builder()
                        .anchor(ViewAnnotationAnchor.BOTTOM)
                        .offsetY(20.0 * density)
                        .build()
                )
            )
        }
    )
    return textView
}

private fun currentInstructionStep(
    routeProgress: RouteProgress
): NavigationInstructionStep? {
    val stepProgress = routeProgress.currentLegProgress?.currentStepProgress ?: return null
    val bannerPrimary = routeProgress.bannerInstructions?.primary()
    val bannerSecondary = routeProgress.bannerInstructions?.secondary()
    val step = stepProgress.step
    val maneuver = step?.maneuver()
    val primaryLine = bannerPrimary?.navigationInstructionLine()
    val text = primaryLine?.text
        ?: maneuver?.instruction()
        ?: step?.name()
        ?: return null
    return NavigationInstructionStep(
        primaryText = NavigationRoadNameFormatter.abbreviated(text),
        secondaryText = bannerSecondary?.text()?.let(NavigationRoadNameFormatter::abbreviated),
        distanceMeters = stepProgress.distanceRemaining.toDouble(),
        maneuverType = bannerPrimary?.type() ?: maneuver?.type(),
        maneuverModifier = bannerPrimary?.modifier() ?: maneuver?.modifier(),
        exitCode = primaryLine?.exitCode,
        primaryComponents = primaryLine?.components.orEmpty(),
        secondaryComponents = bannerSecondary?.navigationInstructionLine()?.components.orEmpty()
    )
}

private fun futureInstructionSteps(
    routeProgress: RouteProgress
): List<NavigationInstructionStep> {
    val currentLegProgress = routeProgress.currentLegProgress ?: return emptyList()
    val currentStepIndex = currentLegProgress.currentStepProgress?.stepIndex ?: return emptyList()
    return currentLegProgress.routeLeg
        ?.steps()
        ?.drop(currentStepIndex + 1)
        ?.dropLast(1)
        ?.mapNotNull(::instructionStep)
        ?.take(6)
        .orEmpty()
}

private fun navigationProgressFromRouteProgress(
    routeProgress: RouteProgress,
    instruction: NavigationInstructionStep?,
    currentSpeedMetersPerSecond: Double?,
    preferredSpeedUnit: String
): MapNavigationProgress {
    val speedLimit = speedLimitDisplay(routeProgress, preferredSpeedUnit)
    return MapNavigationProgress(
        instructionText = instruction?.primaryText,
        distanceRemainingMiles = routeProgress.distanceRemaining / MetersPerMile,
        durationRemainingSeconds = routeProgress.durationRemaining,
        travelTimeTrafficLevel = travelTimeTrafficLevel(routeProgress),
        trafficAlert = trafficAlert(routeProgress),
        roadNameText = roadNameText(routeProgress),
        currentSpeed = currentSpeedDisplay(currentSpeedMetersPerSecond, preferredSpeedUnit),
        speedLimit = speedLimit,
        currentInstruction = instruction,
        futureInstructionSteps = futureInstructionSteps(routeProgress)
    )
}

private fun travelTimeTrafficLevel(routeProgress: RouteProgress): NavigationTravelTimeTrafficLevel {
    val legProgress = routeProgress.currentLegProgress ?: return NavigationTravelTimeTrafficLevel.Clear
    val annotation = legProgress.routeLeg?.annotation()
    val congestionNumeric = annotation?.congestionNumeric()
        ?.drop(legProgress.geometryIndex.coerceAtLeast(0))
        ?.filterNotNull()
        .orEmpty()
    val maxCongestion = congestionNumeric.maxOrNull()
    if (maxCongestion != null) {
        return when {
            maxCongestion >= 70 -> NavigationTravelTimeTrafficLevel.HeavyDelay
            maxCongestion >= 40 -> NavigationTravelTimeTrafficLevel.SomeDelay
            else -> NavigationTravelTimeTrafficLevel.Clear
        }
    }

    val congestion = annotation?.congestion()
        ?.drop(legProgress.geometryIndex.coerceAtLeast(0))
        ?.filterNotNull()
        .orEmpty()
    return when {
        congestion.any { it.equals("heavy", ignoreCase = true) || it.equals("severe", ignoreCase = true) } ->
            NavigationTravelTimeTrafficLevel.HeavyDelay
        congestion.any { it.equals("moderate", ignoreCase = true) } ->
            NavigationTravelTimeTrafficLevel.SomeDelay
        routeDelayMinutes(routeProgress.route) >= 5 ->
            NavigationTravelTimeTrafficLevel.HeavyDelay
        routeDelayMinutes(routeProgress.route) >= 2 ->
            NavigationTravelTimeTrafficLevel.SomeDelay
        else ->
            NavigationTravelTimeTrafficLevel.Clear
    }
}

private fun trafficAlert(routeProgress: RouteProgress): NavigationTrafficAlert? {
    val legProgress = routeProgress.currentLegProgress ?: return null
    val currentGeometryIndex = legProgress.geometryIndex
    val incidentAlert = legProgress.routeLeg
        ?.incidents()
        ?.asSequence()
        ?.filterNotNull()
        ?.filter { it.isAheadOf(currentGeometryIndex) && it.isMeaningfulIncident() }
        ?.map { incident ->
            val priority = incidentPriority(incident)
            val distance = (incident.geometryIndexStart() ?: currentGeometryIndex) - currentGeometryIndex
            Triple(incident, priority, distance.coerceAtLeast(0))
        }
        ?.sortedWith(compareByDescending<Triple<Incident, Int, Int>> { it.second }.thenBy { it.third })
        ?.firstOrNull()
        ?.first
        ?.let { incident ->
            trafficAlertFromIncident(
                incident = incident,
                routeProgress = routeProgress
            )
        }
    if (incidentAlert != null) return incidentAlert

    val closureAlert = legProgress.routeLeg
        ?.closures()
        ?.filterNotNull()
        ?.firstOrNull { closure ->
            val end = closure.geometryIndexEnd() ?: Int.MAX_VALUE
            end >= currentGeometryIndex
        }
        ?.let { closure ->
            NavigationTrafficAlert(
                id = "traffic-closure-${legProgress.legIndex}-${closure.geometryIndexStart() ?: 0}",
                kind = NavigationTrafficAlert.Kind.Closure,
                title = "Road closure ahead",
                subtitle = roadNameText(routeProgress)?.let { "On $it" } ?: "Traffic reported ahead."
            )
        }
    if (closureAlert != null) return closureAlert

    return if (travelTimeTrafficLevel(routeProgress) == NavigationTravelTimeTrafficLevel.HeavyDelay) {
        val delay = routeDelayMinutes(routeProgress.route)
        NavigationTrafficAlert(
            id = "traffic-congestion-${legProgress.legIndex}",
            kind = NavigationTrafficAlert.Kind.Congestion,
            title = "Heavy traffic ahead",
            subtitle = if (delay > 0) "$delay-min route delay expected" else "Expect slower traffic on your route."
        )
    } else {
        null
    }
}

private fun trafficAlertFromIncident(
    incident: Incident,
    routeProgress: RouteProgress
): NavigationTrafficAlert {
    val roadName = incident.affectedRoadNames()
        ?.filterNotNull()
        ?.firstOrNull { it.isNotBlank() }
        ?.let(NavigationRoadNameFormatter::abbreviated)
    val delay = routeDelayMinutes(routeProgress.route)
    val longDescription = incident.longDescription()?.takeIf { it.isNotBlank() }
    val description = incident.description()?.takeIf { it.isNotBlank() }
    return NavigationTrafficAlert(
        id = "traffic-incident-${incident.id() ?: incident.geometryIndexStart() ?: 0}",
        kind = incident.alertKind(),
        title = incident.alertTitle(),
        subtitle = when {
            delay > 0 && roadName != null -> "$delay-min delay on $roadName"
            roadName != null -> "On $roadName"
            longDescription != null -> longDescription
            description != null -> description
            else -> "Traffic reported ahead."
        }
    )
}

private fun Incident.isAheadOf(currentGeometryIndex: Int): Boolean {
    val end = geometryIndexEnd() ?: Int.MAX_VALUE
    return end >= currentGeometryIndex
}

private fun Incident.isMeaningfulIncident(): Boolean {
    if (closed() == true) return true
    return when (type()) {
        Incident.INCIDENT_ACCIDENT,
        Incident.INCIDENT_CONSTRUCTION,
        Incident.INCIDENT_DISABLED_VEHICLE,
        Incident.INCIDENT_LANE_RESTRICTION,
        Incident.INCIDENT_ROAD_CLOSURE,
        Incident.INCIDENT_ROAD_HAZARD,
        Incident.INCIDENT_WEATHER -> true
        Incident.INCIDENT_CONGESTION ->
            impact() == Incident.IMPACT_CRITICAL || impact() == Incident.IMPACT_MAJOR || (congestion()?.value() ?: 0) >= 70
        else ->
            impact() == Incident.IMPACT_CRITICAL || impact() == Incident.IMPACT_MAJOR
    }
}

private fun Incident.alertKind(): NavigationTrafficAlert.Kind {
    if (closed() == true) return NavigationTrafficAlert.Kind.Closure
    return when (type()) {
        Incident.INCIDENT_ACCIDENT -> NavigationTrafficAlert.Kind.Accident
        Incident.INCIDENT_CONSTRUCTION -> NavigationTrafficAlert.Kind.Construction
        Incident.INCIDENT_CONGESTION -> NavigationTrafficAlert.Kind.Congestion
        Incident.INCIDENT_ROAD_CLOSURE -> NavigationTrafficAlert.Kind.Closure
        Incident.INCIDENT_DISABLED_VEHICLE,
        Incident.INCIDENT_LANE_RESTRICTION,
        Incident.INCIDENT_ROAD_HAZARD -> NavigationTrafficAlert.Kind.Hazard
        Incident.INCIDENT_WEATHER -> NavigationTrafficAlert.Kind.Weather
        else -> NavigationTrafficAlert.Kind.General
    }
}

private fun Incident.alertTitle(): String {
    if (closed() == true) return "Road closure ahead"
    return when (type()) {
        Incident.INCIDENT_ACCIDENT -> "Accident ahead"
        Incident.INCIDENT_CONSTRUCTION -> "Construction ahead"
        Incident.INCIDENT_CONGESTION -> "Traffic ahead"
        Incident.INCIDENT_DISABLED_VEHICLE -> "Disabled vehicle ahead"
        Incident.INCIDENT_LANE_RESTRICTION -> "Lane restriction ahead"
        Incident.INCIDENT_ROAD_CLOSURE -> "Road closure ahead"
        Incident.INCIDENT_ROAD_HAZARD -> "Road hazard ahead"
        Incident.INCIDENT_WEATHER -> "Weather ahead"
        else -> "Traffic alert ahead"
    }
}

private fun incidentPriority(incident: Incident): Int {
    if (incident.closed() == true) return 50
    return when (incident.impact()) {
        Incident.IMPACT_CRITICAL -> 40
        Incident.IMPACT_MAJOR -> 30
        Incident.IMPACT_MINOR -> 20
        Incident.IMPACT_LOW -> 10
        else -> 0
    }
}

private fun routeDelayMinutes(route: com.mapbox.api.directions.v5.models.DirectionsRoute): Int {
    val duration = route.duration() ?: return 0
    val typical = route.durationTypical() ?: return 0
    return ((duration - typical) / 60.0).roundToInt().coerceAtLeast(0)
}

private fun speedLimitDisplay(
    routeProgress: RouteProgress,
    preferredUnit: String
): NavigationSpeedLimitDisplay? {
    val legProgress = routeProgress.currentLegProgress ?: return null
    val maxSpeed = legProgress.routeLeg
        ?.annotation()
        ?.maxspeed()
        ?.drop(legProgress.geometryIndex.coerceAtLeast(0))
        ?.filterNotNull()
        ?.firstOrNull { it.speed() != null && it.unknown() != true && it.none() != true }
        ?: return null
    val speed = maxSpeed.speed()?.toDouble() ?: return null
    val sourceUnit = normalizedSpeedUnit(maxSpeed.unit())
    val unit = normalizedSpeedUnit(preferredUnit)
    val convertedSpeed = convertSpeedLimit(speed, sourceUnit, unit)
    return NavigationSpeedLimitDisplay(
        value = convertedSpeed,
        valueText = convertedSpeed.roundToInt().toString(),
        unitText = unit
    )
}

private fun currentSpeedDisplay(
    currentSpeedMetersPerSecond: Double?,
    preferredUnit: String?
): NavigationCurrentSpeedDisplay? {
    val metersPerSecond = currentSpeedMetersPerSecond?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
    val unit = normalizedSpeedUnit(preferredUnit)
    val value = if (unit == "KM/H") metersPerSecond * 3.6 else metersPerSecond * 2.2369362921
    return NavigationCurrentSpeedDisplay(
        value = value,
        valueText = value.roundToInt().toString(),
        unitText = unit
    )
}

private fun normalizedSpeedUnit(unit: String?): String {
    return when (unit?.lowercase()) {
        "km/h", "kmh", "kph", "kilometers per hour" -> "KM/H"
        else -> "MPH"
    }
}

private fun convertSpeedLimit(value: Double, sourceUnit: String, targetUnit: String): Double {
    if (sourceUnit == targetUnit) return value
    return if (targetUnit == "KM/H") value * 1.609344 else value / 1.609344
}

private fun preferredSpeedUnitForLocale(locale: Locale = Locale.getDefault()): String {
    return when (locale.country.uppercase(Locale.US)) {
        "US", "LR", "MM", "GB" -> "MPH"
        else -> "KM/H"
    }
}

private fun roadNameText(routeProgress: RouteProgress): String? {
    return routeProgress.currentLegProgress?.currentStepProgress
        ?.step
        ?.name()
        ?.takeIf { it.isNotBlank() }
        ?.let(NavigationRoadNameFormatter::abbreviated)
}

private fun instructionStep(step: com.mapbox.api.directions.v5.models.LegStep): NavigationInstructionStep? {
    val banner = step.bannerInstructions()?.lastOrNull()
    val primary = banner?.primary()
    val secondary = banner?.secondary()
    val maneuver = step.maneuver()
    val primaryLine = primary?.navigationInstructionLine()
    val text = primaryLine?.text
        ?: maneuver?.instruction()
        ?: step.name()
        ?: return null
    return NavigationInstructionStep(
        primaryText = NavigationRoadNameFormatter.abbreviated(text),
        secondaryText = secondary?.text()?.let(NavigationRoadNameFormatter::abbreviated),
        distanceMeters = step.distance(),
        maneuverType = primary?.type() ?: maneuver?.type(),
        maneuverModifier = primary?.modifier() ?: maneuver?.modifier(),
        exitCode = primaryLine?.exitCode,
        primaryComponents = primaryLine?.components.orEmpty(),
        secondaryComponents = secondary?.navigationInstructionLine()?.components.orEmpty()
    )
}

private data class NavigationInstructionLine(
    val text: String,
    val exitCode: String?,
    val components: List<NavigationInstructionComponent>
)

private fun BannerText.navigationInstructionLine(): NavigationInstructionLine {
    var exitCode: String? = null
    val visualComponents = components()
        .orEmpty()
        .mapNotNull { component ->
            when (component.type()) {
                BannerComponents.EXIT_NUMBER -> {
                    exitCode = component.text()?.takeIf { it.isNotBlank() }
                    null
                }
                BannerComponents.EXIT,
                BannerComponents.LANE -> null
                else -> component.navigationInstructionComponent()
            }
        }
    val componentText = visualComponents
        .filter { it.kind == NavigationInstructionComponent.Kind.Text || it.kind == NavigationInstructionComponent.Kind.Delimiter }
        .joinToString(" ") { it.text }
        .replace(Regex("\\s+"), " ")
        .trim()
        .takeIf { it.isNotBlank() }
        ?: visualComponents.joinToString(" ") { it.text }.replace(Regex("\\s+"), " ").trim()
    return NavigationInstructionLine(
        text = componentText.takeIf { it.isNotBlank() } ?: text(),
        exitCode = exitCode,
        components = visualComponents
    )
}

private fun BannerComponents.navigationInstructionComponent(): NavigationInstructionComponent? {
    val shieldText = mapboxShield()?.displayRef()?.takeIf { it.isNotBlank() }
    if (shieldText != null) {
        return NavigationInstructionComponent(
            text = shieldText,
            kind = NavigationInstructionComponent.Kind.Shield
        )
    }

    val componentText = abbreviation()
        ?.takeIf { it.isNotBlank() }
        ?: text()?.takeIf { it.isNotBlank() }
        ?: imageUrl()?.substringAfterLast('/')?.substringBefore('.')?.takeIf { it.isNotBlank() }
        ?: imageBaseUrl()?.substringAfterLast('/')?.substringBefore('.')?.takeIf { it.isNotBlank() }

    val kind = when (type()) {
        BannerComponents.ICON,
        BannerComponents.GUIDANCE_VIEW,
        BannerComponents.SIGNBOARD -> NavigationInstructionComponent.Kind.Image
        BannerComponents.DELIMITER -> NavigationInstructionComponent.Kind.Delimiter
        else -> NavigationInstructionComponent.Kind.Text
    }

    return componentText?.let { NavigationInstructionComponent(text = it, kind = kind) }
}

private object NavigationRoadNameFormatter {
    private val suffixes = mapOf(
        "Avenue" to "Ave",
        "Street" to "St",
        "Boulevard" to "Blvd",
        "Road" to "Rd",
        "Drive" to "Dr",
        "Lane" to "Ln",
        "Place" to "Pl",
        "Court" to "Ct",
        "Parkway" to "Pkwy",
        "Highway" to "Hwy",
        "Expressway" to "Expy"
    )
    private val trailingDirections = setOf("N", "S", "E", "W", "North", "South", "East", "West")

    fun abbreviated(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return trimmed
        val parts = trimmed.split(Regex("\\s+")).toMutableList()
        val suffixIndex = suffixIndex(parts) ?: return trimmed
        val abbreviation = suffixes[normalized(parts[suffixIndex])] ?: return trimmed
        parts[suffixIndex] = abbreviation
        return parts.joinToString(" ")
    }

    private fun suffixIndex(parts: List<String>): Int? {
        val lastIndex = parts.lastIndex
        if (lastIndex < 0) return null
        if (suffixes.containsKey(normalized(parts[lastIndex]))) return lastIndex
        if (lastIndex > 0 &&
            trailingDirections.contains(normalized(parts[lastIndex])) &&
            suffixes.containsKey(normalized(parts[lastIndex - 1]))
        ) {
            return lastIndex - 1
        }
        return null
    }

    private fun normalized(value: String): String = value.trim('.')
}
