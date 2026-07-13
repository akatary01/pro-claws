package com.vendistri.operations.features.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mapbox.geojson.Point
import com.mapbox.common.MapboxOptions
import com.mapbox.maps.ImageHolder
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotationGroup
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotationGroupState
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.rememberCircleAnnotationGroupInteractionsState
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.style.standard.LightPresetValue
import com.mapbox.maps.extension.compose.style.standard.MapboxStandardStyle
import com.vendistri.operations.design.AppColors
import com.mapbox.maps.extension.compose.style.standard.rememberStandardStyleState
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.Plugin
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.locationcomponent.LocationComponentPlugin
import com.mapbox.maps.plugin.gestures.OnMapClickListener
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.vendistri.operations.R
import com.vendistri.operations.design.LocalVendistriPalette
import kotlinx.coroutines.awaitCancellation

@OptIn(MapboxExperimental::class)
@Composable
fun MapHomeView(
    stops: List<LocationStop>,
    activeRoute: MapRouteOverlay?,
    activeNavigationRoute: MapNavigationRoute? = null,
    recenterRequest: Int,
    onStopSelected: (String) -> Unit,
    onMapClicked: () -> Unit = {},
    onUserLocationChanged: (LocationCoordinate) -> Unit = {},
    onNavigationProgress: (MapNavigationProgress) -> Unit = {},
    onActiveNavigationMapReadyChanged: (Boolean) -> Unit = {},
    onNavigationCameraInteraction: () -> Unit = {},
    isRouteOverviewMode: Boolean = false,
    isNavigationCameraDetached: Boolean = false,
    isNavigationArrivalCandidate: Boolean = false,
    followNavigationCameraRequest: Int = 0,
    debugRouteSimulationRequest: Int = 0,
    onMapReadyChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val accessToken = stringResource(R.string.mapbox_access_token)
    if (accessToken.isNotBlank() && MapboxOptions.accessToken != accessToken) {
        MapboxOptions.accessToken = accessToken
    }

    val context = LocalContext.current
    val palette = LocalVendistriPalette.current
    var isActiveNavigationMapReady by remember(activeNavigationRoute?.id) { mutableStateOf(false) }
    var isMapReady by remember(accessToken) { mutableStateOf(accessToken.isBlank()) }
    var hasLocationPermission by remember {
        mutableStateOf(context.hasMapLocationPermission())
    }
    var userLocationPoint by remember {
        mutableStateOf(context.bestInitialLocationPoint())
    }
    var hasCenteredOnUserLocation by remember { mutableStateOf(false) }
    var lastStopClickAtMillis by remember { mutableStateOf(0L) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasLocationPermission = grants.values.any { it } || context.hasMapLocationPermission()
    }

    LaunchedEffect(hasLocationPermission) {
        if (!hasLocationPermission) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
    LaunchedEffect(isMapReady) {
        onMapReadyChanged(isMapReady)
    }
    LaunchedEffect(activeNavigationRoute?.id) {
        onActiveNavigationMapReadyChanged(activeNavigationRoute == null)
    }
    LaunchedEffect(userLocationPoint) {
        userLocationPoint?.let { point ->
            onUserLocationChanged(LocationCoordinate(latitude = point.latitude(), longitude = point.longitude()))
        }
    }
    DisposableEffect(hasLocationPermission) {
        if (!hasLocationPermission) {
            onDispose {}
        } else {
            val listener = context.requestMapLocationUpdates { location ->
                userLocationPoint = location.toPoint()
                context.persistMapLocation(location)
            }
            onDispose {
                context.removeMapLocationUpdates(listener)
            }
        }
    }

    val routePoints = remember(activeRoute) {
        activeRoute?.points.orEmpty().map { Point.fromLngLat(it.longitude, it.latitude) }
    }
    val cameraPoint = routePoints.lastOrNull()
        ?: userLocationPoint
        ?: stops.firstOrNull()?.let { Point.fromLngLat(it.coordinate.longitude, it.coordinate.latitude) }
    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(cameraPoint ?: Point.fromLngLat(-98.0, 39.5))
            zoom(if (cameraPoint == null) 3.0 else if (cameraPoint == userLocationPoint) 14.8 else 10.5)
            pitch(0.0)
            bearing(0.0)
        }
    }
    LaunchedEffect(activeRoute?.id, routePoints, userLocationPoint) {
        if (routePoints.size >= 2) {
            val camera = mapViewportState.cameraForCoordinates(
                coordinates = routePoints,
                camera = CameraOptions.Builder().build(),
                coordinatesPadding = EdgeInsets(120.0, 64.0, 320.0, 64.0),
                maxZoom = 14.5,
                offset = null
            )
            mapViewportState.setCameraOptions(camera)
        } else {
            val point = cameraPoint ?: return@LaunchedEffect
            if (point == userLocationPoint && hasCenteredOnUserLocation) {
                return@LaunchedEffect
            }
            mapViewportState.setCameraOptions {
                center(point)
                zoom(if (point == userLocationPoint) 14.8 else 10.5)
                pitch(0.0)
                bearing(0.0)
            }
            if (point == userLocationPoint) {
                hasCenteredOnUserLocation = true
            }
        }
    }
    LaunchedEffect(recenterRequest) {
        if (recenterRequest <= 0) return@LaunchedEffect
        val point = userLocationPoint ?: return@LaunchedEffect
        mapViewportState.setCameraOptions {
            center(point)
            zoom(14.8)
            pitch(0.0)
            bearing(0.0)
        }
        hasCenteredOnUserLocation = true
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
    ) {
        if (accessToken.isBlank()) {
            FallbackMapBackground(onClick = onMapClicked)
        } else {
            MapboxMap(
                modifier = Modifier.fillMaxSize(),
                mapViewportState = mapViewportState,
                compass = {},
                scaleBar = {},
                onMapClickListener = OnMapClickListener {
                    if (SystemClock.elapsedRealtime() - lastStopClickAtMillis > StopClickMapClickSuppressionMillis) {
                        onMapClicked()
                    }
                    false
                },
                style = {
                    val standardStyleState = rememberStandardStyleState {
                        configurationsState.lightPreset = if (palette.isDark) {
                            LightPresetValue.NIGHT
                        } else {
                            LightPresetValue.DAY
                        }
                    }
                    MapboxStandardStyle(standardStyleState = standardStyleState)
                }
            ) {
                MapEffect(Unit) { mapView ->
                    var hasRenderedMap = false
                    val mapLoaded = mapView.mapboxMap.subscribeMapLoaded {
                        hasRenderedMap = true
                        isMapReady = true
                    }
                    val mapIdle = mapView.mapboxMap.subscribeMapIdle {
                        if (!hasRenderedMap) {
                            hasRenderedMap = true
                            isMapReady = true
                        }
                    }
                    try {
                        awaitCancellation()
                    } finally {
                        mapLoaded.cancel()
                        mapIdle.cancel()
                    }
                }
                MapEffect(hasLocationPermission, activeNavigationRoute?.id) { mapView ->
                    if (!hasLocationPermission) return@MapEffect
                    val locationPlugin = mapView.getPlugin<LocationComponentPlugin>(
                        Plugin.MAPBOX_LOCATION_COMPONENT_PLUGIN_ID
                    )
                    locationPlugin?.updateSettings {
                        enabled = activeNavigationRoute == null
                        locationPuck = LocationPuck2D(
                            bearingImage = ImageHolder.from(R.drawable.ic_user_location_arrow)
                        )
                        puckBearing = PuckBearing.HEADING
                        puckBearingEnabled = true
                        pulsingEnabled = false
                        showAccuracyRing = false
                    }
                }
                if (activeNavigationRoute == null && routePoints.size >= 2) {
                    ActiveRoutePolyline(points = routePoints)
                }
                if (activeNavigationRoute == null) {
                    stops.forEach { stop ->
                        key(stop.id) {
                            val interactionsState = rememberCircleAnnotationGroupInteractionsState {
                                onClicked {
                                    lastStopClickAtMillis = SystemClock.elapsedRealtime()
                                    onStopSelected(stop.id)
                                    true
                                }
                            }
                            val circleState = remember(stop.id, stop.color) { CircleAnnotationGroupState() }.apply {
                                this.interactionsState = interactionsState
                                circleRadius = 9.0
                                circleColor = stop.color
                                // Standard night lighting otherwise shades annotation colors
                                // toward black. Status markers must retain their semantic color.
                                circleEmissiveStrength = 1.0
                                circleColorUseTheme = "none"
                                circleStrokeColorUseTheme = "none"
                                circleStrokeColor = Color.White
                                circleStrokeWidth = 3.0
                                circleStrokeOpacity = 1.0
                                circleOpacity = 1.0
                            }
                            CircleAnnotationGroup(
                                annotations = listOf(
                                    CircleAnnotationOptions().withPoint(
                                        Point.fromLngLat(stop.coordinate.longitude, stop.coordinate.latitude)
                                    )
                                ),
                                circleAnnotationGroupState = circleState
                            )
                        }
                    }
                }
            }
        }
        if (stops.isEmpty() && accessToken.isBlank()) {
            Text(
                text = "Map",
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 120.dp),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.16f),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (accessToken.isBlank() && activeNavigationRoute == null) {
            if (activeRoute != null) {
                FallbackRouteLine(route = activeRoute)
            }
            stops.forEachIndexed { index, stop ->
                val point = stop.mapPoint(index = index, total = stops.size)
                MapStopPin(
                    stop = stop,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(
                            x = (maxWidth - 64.dp) * point.x + 24.dp,
                            y = (maxHeight - 360.dp) * point.y + 128.dp
                        ),
                    onClick = {
                        lastStopClickAtMillis = SystemClock.elapsedRealtime()
                        onStopSelected(stop.id)
                    }
                )
            }
        }

        activeNavigationRoute?.let { navigationRoute ->
            MapboxNavigationActiveMap(
                route = navigationRoute,
                hasLocationPermission = hasLocationPermission,
                onMapReadyChanged = { ready ->
                    isActiveNavigationMapReady = ready
                    onActiveNavigationMapReadyChanged(ready)
                    if (ready) {
                        onMapReadyChanged(true)
                    }
                },
                onUserLocationChanged = onUserLocationChanged,
                onNavigationProgress = onNavigationProgress,
                onUserCameraInteraction = onNavigationCameraInteraction,
                isRouteOverviewMode = isRouteOverviewMode,
                isCameraDetached = isNavigationCameraDetached,
                isArrivalCandidate = isNavigationArrivalCandidate,
                followCameraRequest = followNavigationCameraRequest,
                debugRouteSimulationRequest = debugRouteSimulationRequest,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (isActiveNavigationMapReady) 1f else 0f
                    }
            )
        }
    }
}

private const val StopClickMapClickSuppressionMillis = 250L

@Composable
private fun ActiveRoutePolyline(points: List<Point>) {
    PolylineAnnotation(points = points) {
        lineColor = Color(0xFF0B6BEF)
        lineWidth = 7.0
        lineBorderColor = Color.White
        lineBorderWidth = 2.0
        lineJoin = LineJoin.ROUND
        lineOpacity = 0.96
    }
}

@Composable
private fun FallbackRouteLine(route: MapRouteOverlay) {
    if (route.points.size < 2) return
    Canvas(modifier = Modifier.fillMaxSize()) {
        val projected = route.points.map { coordinate ->
            androidx.compose.ui.geometry.Offset(
                x = (((coordinate.longitude + 180.0) / 360.0).toFloat().coerceIn(0.08f, 0.88f)) * size.width,
                y = (((90.0 - coordinate.latitude) / 180.0).toFloat().coerceIn(0.16f, 0.62f)) * size.height
            )
        }
        for (index in 0 until projected.lastIndex) {
            drawLine(
                color = Color.White,
                start = projected[index],
                end = projected[index + 1],
                strokeWidth = 12f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF0B6BEF),
                start = projected[index],
                end = projected[index + 1],
                strokeWidth = 7f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun FallbackMapBackground(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFE8EEF6),
                        Color(0xFFF7F9FC)
                    )
                )
            )
    )
}

@Composable
private fun MapStopPin(
    stop: LocationStop,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(30.dp),
        color = Color.Transparent,
        shadowElevation = 4.dp
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scale = size.width / 28f
            val headRadius = 12f * scale
            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, 14f * scale)
            val innerRadius = 8f * scale
            val tail = Path().apply {
                moveTo(size.width / 2f, 27f * scale)
                lineTo(9.5f * scale, 22f * scale)
                lineTo(18.5f * scale, 22f * scale)
                close()
            }
            drawPath(tail, Color.White)
            drawCircle(Color.White, radius = headRadius, center = center)
            drawCircle(stop.color, radius = innerRadius, center = center)
            drawCircle(Color.White, radius = innerRadius, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f * scale))
        }
    }
}

private data class MapPoint(val x: Float, val y: Float)

private fun android.content.Context.hasMapLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

@SuppressLint("MissingPermission")
private fun android.content.Context.bestInitialLocationPoint(): Point? {
    return bestLastKnownMapLocation()?.also(::persistMapLocation)?.toPoint()
        ?: persistedMapLocationPoint()
}

internal fun android.content.Context.bestInitialMapLocationCoordinate(): LocationCoordinate? {
    return bestInitialLocationPoint()?.let { point ->
        LocationCoordinate(latitude = point.latitude(), longitude = point.longitude())
    }
}

@SuppressLint("MissingPermission")
private fun android.content.Context.bestLastKnownMapLocation(): Location? {
    if (!hasMapLocationPermission()) return null
    val locationManager = getSystemService(LocationManager::class.java) ?: return null
    return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }
        .filter(Location::isUsableForInitialMapCamera)
        .maxByOrNull { it.time }
}

@SuppressLint("MissingPermission")
private fun android.content.Context.requestMapLocationUpdates(onLocation: (Location) -> Unit): LocationListener? {
    if (!hasMapLocationPermission()) return null
    val locationManager = getSystemService(LocationManager::class.java) ?: return null
    val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (location.isUsableForInitialMapCamera()) {
                onLocation(location)
                locationManager.removeUpdates(this)
            }
        }
    }
    listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        .firstOrNull { provider -> runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false) }
        ?.let { provider ->
            runCatching {
                locationManager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            }
        }
    return listener
}

private fun android.content.Context.removeMapLocationUpdates(listener: LocationListener?) {
    if (listener == null) return
    runCatching {
        getSystemService(LocationManager::class.java)?.removeUpdates(listener)
    }
}

private fun Location.isUsableForInitialMapCamera(): Boolean {
    return latitude in -90.0..90.0 &&
        longitude in -180.0..180.0 &&
        accuracy >= 0f &&
        accuracy <= 500f
}

private fun Location.toPoint(): Point = Point.fromLngLat(longitude, latitude)

private fun android.content.Context.persistMapLocation(location: Location) {
    if (!location.isUsableForInitialMapCamera()) return
    getSharedPreferences("vendistri_map", android.content.Context.MODE_PRIVATE)
        .edit()
        .putFloat("last_latitude", location.latitude.toFloat())
        .putFloat("last_longitude", location.longitude.toFloat())
        .putLong("last_location_time", location.time)
        .apply()
}

private fun android.content.Context.persistedMapLocationPoint(): Point? {
    val preferences = getSharedPreferences("vendistri_map", android.content.Context.MODE_PRIVATE)
    if (!preferences.contains("last_latitude") || !preferences.contains("last_longitude")) return null
    val ageMillis = kotlin.math.abs(System.currentTimeMillis() - preferences.getLong("last_location_time", 0L))
    if (ageMillis > 24L * 60L * 60L * 1000L) return null
    return Point.fromLngLat(
        preferences.getFloat("last_longitude", 0f).toDouble(),
        preferences.getFloat("last_latitude", 0f).toDouble()
    )
}

private fun LocationStop.mapPoint(index: Int, total: Int): MapPoint {
    val lngNormalized = ((coordinate.longitude + 180.0) / 360.0).toFloat().coerceIn(0.08f, 0.88f)
    val latNormalized = ((90.0 - coordinate.latitude) / 180.0).toFloat().coerceIn(0.16f, 0.62f)
    if (total <= 1) return MapPoint(0.48f, 0.36f)
    val spread = ((index % 5) - 2) * 0.035f
    return MapPoint(
        x = (lngNormalized + spread).coerceIn(0.08f, 0.88f),
        y = (latNormalized + (index % 3) * 0.045f).coerceIn(0.16f, 0.62f)
    )
}
