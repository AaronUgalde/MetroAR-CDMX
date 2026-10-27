package mx.metroar.cdmx.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.metroar.cdmx.R
import mx.metroar.cdmx.domain.geo.GeoMath
import mx.metroar.cdmx.domain.model.SensorAccuracy
import mx.metroar.cdmx.domain.model.Station
import mx.metroar.cdmx.domain.model.TransportSystem
import mx.metroar.cdmx.domain.usecase.DescribeDirection
import mx.metroar.cdmx.domain.usecase.FieldOfView
import mx.metroar.cdmx.domain.usecase.NearbyStation
import mx.metroar.cdmx.domain.usecase.ProjectToScreen
import mx.metroar.cdmx.presentation.MainUiState
import mx.metroar.cdmx.presentation.MainViewModel
import mx.metroar.cdmx.presentation.OrientationUi
import mx.metroar.cdmx.presentation.ViewMode
import mx.metroar.cdmx.presentation.ViewportInfo
import mx.metroar.cdmx.ui.ar.ArOverlay
import mx.metroar.cdmx.ui.ar.CameraOptics
import mx.metroar.cdmx.ui.ar.CameraPreview
import mx.metroar.cdmx.ui.ar.MarkerHeight
import mx.metroar.cdmx.ui.ar.MarkerWidth
import mx.metroar.cdmx.ui.components.CalibrationCard
import mx.metroar.cdmx.ui.components.FiltersSheet
import mx.metroar.cdmx.ui.components.Format
import mx.metroar.cdmx.ui.components.NavigationPanel
import mx.metroar.cdmx.ui.components.Quality
import mx.metroar.cdmx.ui.components.SafetyBanner
import mx.metroar.cdmx.ui.components.StationSheet
import mx.metroar.cdmx.ui.components.StatusChip
import mx.metroar.cdmx.ui.components.compassQuality
import mx.metroar.cdmx.ui.components.directionRes
import mx.metroar.cdmx.ui.components.labelRes
import mx.metroar.cdmx.ui.components.gpsQuality
import mx.metroar.cdmx.ui.map.MapCamera
import mx.metroar.cdmx.ui.map.StationMap
import java.util.Locale

/** Espacio reservado arriba (indicadores y avisos) y abajo (botones) para no tapar marcadores. */
private val TopReserved = 132.dp
private val BottomReserved = 112.dp

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    state: MainUiState,
    mapCamera: MapCamera,
    forceCalibration: Boolean,
    onCalibrationShown: () -> Unit,
    onRequestCamera: () -> Unit,
    onRequestLocation: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val orientation by viewModel.orientation.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val density = LocalDensity.current
    val units = state.settings.units
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var calibrationDismissed by rememberSaveable { mutableStateOf(false) }
    var optics by remember { mutableStateOf<CameraOptics?>(null) }
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
    val tts = rememberTts(context)

    if (orientation.accuracy == SensorAccuracy.High) calibrationDismissed = false
    val showCalibration = forceCalibration ||
        (!calibrationDismissed && orientation.received && compassQuality(orientation.accuracy) == Quality.Poor)

    // Vibración al llegar al destino.
    val arrived = state.navigation?.arrived == true
    LaunchedEffect(arrived) { if (arrived) vibrate(context) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (state.viewMode == ViewMode.Ar) {
            val frame by viewModel.arFrame.collectAsStateWithLifecycle()
            CameraPreview(Modifier.fillMaxSize()) { optics = it }
            val statusTop = WindowInsets.statusBars.getTop(density)
            val navBottom = WindowInsets.navigationBars.getBottom(density)
            val visibleFrame = state.navigation?.let { nav ->
                frame.copy(markers = frame.markers.filter { it.nearby.station.id == nav.target.id }, edges = emptyList())
            } ?: frame
            ArOverlay(
                frame = visibleFrame,
                units = units,
                onStationClick = viewModel::selectStation,
                edgeTopPadding = TopReserved,
                modifier = Modifier.onSizeChanged { overlaySize = it },
            )
            // El viewport se recalcula si cambia el tamaño o llega la óptica real de la cámara.
            LaunchedEffect(overlaySize, optics, statusTop, navBottom) {
                if (overlaySize.width == 0) return@LaunchedEffect
                val o = optics
                with(density) {
                    viewModel.onViewport(
                        ViewportInfo(
                            viewport = ProjectToScreen.Viewport(
                                widthPx = overlaySize.width.toFloat(),
                                heightPx = overlaySize.height.toFloat(),
                                markerWidthPx = MarkerWidth.toPx(),
                                markerHeightPx = MarkerHeight.toPx(),
                                topInsetPx = statusTop + TopReserved.toPx(),
                                bottomInsetPx = navBottom + BottomReserved.toPx(),
                            ),
                            fov = if (o == null) FieldOfView.DEFAULT else FieldOfView.fromCameraOptics(
                                o.focalLengthMm, o.sensorWidthMm, o.sensorHeightMm, overlaySize.width, overlaySize.height,
                            ),
                        ),
                    )
                }
            }
        } else {
            val user = state.location?.point
            StationMap(
                lines = state.lines.filter {
                    it.system in state.settings.enabledSystems &&
                        (state.settings.lineFilter.isEmpty() || it.id in state.settings.lineFilter)
                },
                stations = state.stations.filter { s ->
                    s.system in state.settings.enabledSystems &&
                        (state.settings.lineFilter.isEmpty() || s.lines.any { it.id in state.settings.lineFilter })
                },
                user = user,
                userAccuracyM = state.location?.accuracyM,
                heading = mapHeading(orientation),
                radiusM = state.settings.radiusM,
                highlightIds = state.nearby.take(8).map { it.station.id }.toSet() + listOfNotNull(state.navigation?.target?.id),
                selectedId = state.selectedStationId,
                camera = mapCamera,
                a11yDescription = stringResource(R.string.map_a11y, state.nearby.size),
                onStationClick = viewModel::selectStation,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Barra superior: precisión de GPS y brújula, voz y ajustes.
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(
                    icon = true,
                    label = stringResource(R.string.gps),
                    value = state.location?.let { "±${it.accuracyM.toInt()} m" } ?: stringResource(R.string.no_signal),
                    quality = gpsQuality(state.location?.accuracyM),
                )
                if (state.compassAvailable) {
                    StatusChip(
                        icon = false,
                        label = stringResource(R.string.compass),
                        value = stringResource(
                            when (compassQuality(orientation.accuracy)) {
                                Quality.Good -> R.string.quality_good
                                Quality.Fair -> R.string.quality_fair
                                Quality.Poor -> R.string.quality_poor
                            },
                        ),
                        quality = compassQuality(orientation.accuracy),
                    )
                }
                Spacer(Modifier.weight(1f))
                RoundIcon(Icons.Filled.RecordVoiceOver, stringResource(R.string.voice_announce)) {
                    tts.speak(context, voiceText(context, state, orientation))
                }
                RoundIcon(Icons.Filled.Settings, stringResource(R.string.settings), onOpenSettings)
            }
            if (state.viewMode == ViewMode.Ar) SafetyBanner()
            if (!state.preciseLocation) Notice(stringResource(R.string.approx_location_warning))
            if (!state.compassAvailable) Notice(stringResource(R.string.no_compass))
            if (state.autoMap) Notice(stringResource(R.string.auto_map_hint))
            if (showCalibration) {
                CalibrationCard(onDismiss = { calibrationDismissed = true; onCalibrationShown() }, modifier = Modifier.fillMaxWidth())
            }
        }

        // Estado sin ubicación.
        if (!state.locationGranted) {
            CenterMessage(stringResource(R.string.need_location), stringResource(R.string.allow), onRequestLocation)
        } else if (state.location == null) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.padding(6.dp))
                Text(stringResource(R.string.locating), color = Color.White, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(8.dp))
            }
        } else if (state.nearby.isEmpty() && state.navigation == null) {
            Text(
                stringResource(R.string.none_in_radius, Format.radius(state.settings.radiusM, units)),
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(32.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp)).padding(12.dp),
            )
        }

        // Navegación y barra inferior.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            state.navigation?.let { nav ->
                NavigationPanel(
                    nav = nav,
                    heading = if (state.viewMode == ViewMode.Ar) orientation.heading else mapHeading(orientation),
                    units = units,
                    onStop = viewModel::stopNavigation,
                )
                Spacer(Modifier.padding(6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val toAr = state.viewMode == ViewMode.Map
                ExtendedFloatingActionButton(
                    onClick = { if (toAr && !state.cameraGranted) onRequestCamera() else viewModel.toggleViewMode() },
                    icon = { Icon(if (toAr) Icons.Filled.CameraAlt else Icons.Filled.Map, null) },
                    text = { Text(stringResource(if (toAr) R.string.view_camera else R.string.view_map)) },
                )
                Spacer(Modifier.weight(1f))
                if (state.viewMode == ViewMode.Map && state.location != null) {
                    SmallFloatingActionButton(onClick = { mapCamera.recenter() }) {
                        Icon(Icons.Filled.MyLocation, stringResource(R.string.recenter))
                    }
                    Spacer(Modifier.width(8.dp))
                }
                ExtendedFloatingActionButton(
                    onClick = { showFilters = true },
                    icon = { Icon(if (state.settings.lineFilter.isEmpty()) Icons.Filled.Radar else Icons.Filled.FilterList, null) },
                    text = { Text(Format.radius(state.settings.radiusM, units)) },
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            }
        }
    }

    state.selectedStation?.let { station ->
        StationSheet(
            station = station,
            nearby = state.nearby.firstOrNull { it.station.id == station.id }
                ?: state.location?.let { loc ->
                    NearbyStation(
                        station, GeoMath.distance(loc.point, station.location), GeoMath.bearing(loc.point, station.location),
                    )
                },
            heading = orientation.heading,
            units = units,
            isFavorite = state.isFavorite(station.id),
            onDismiss = { viewModel.selectStation(null) },
            onNavigate = { viewModel.navigateTo(station) },
            onOpenMaps = { openWalkingRoute(context, station) },
            onToggleFavorite = { viewModel.toggleFavorite(station) },
            connections = station.connectionIds.mapNotNull { id ->
                state.stations.firstOrNull { it.id == id }?.let { it to GeoMath.distance(station.location, it.location) }
            },
            onStationClick = viewModel::selectStation,
        )
    }

    if (showFilters) {
        FiltersSheet(
            lines = state.lines,
            selectedLines = state.settings.lineFilter,
            enabledSystems = state.settings.enabledSystems,
            radiusM = state.settings.radiusM,
            units = units,
            favorites = state.favorites,
            stations = state.stations,
            onToggleLine = { viewModel.toggleLine(it) },
            onClearLines = { viewModel.updateSettings { it.copy(lineFilter = emptySet()) } },
            onRadius = { r -> viewModel.updateSettings { it.copy(radiusM = r) } },
            onFavoriteClick = { showFilters = false; viewModel.selectStation(it) },
            onDismiss = { showFilters = false },
        )
    }
}

/** En el mapa el cono sigue el borde superior si el teléfono está acostado; si no, la cámara. */
private fun mapHeading(o: OrientationUi): Double =
    if (o.elevation < -45 || o.heading.isNaN()) o.topHeading else o.heading

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Black.copy(alpha = 0.55f), contentColor = Color.White),
    ) { Icon(icon, description) }
}

@Composable
private fun Notice(text: String) {
    Text(
        text,
        color = Color.White,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.CenterMessage(text: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier.align(Alignment.Center).padding(32.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.padding(6.dp))
        Button(onClick = onAction) { Text(action) }
    }
}

private fun voiceText(context: Context, state: MainUiState, o: OrientationUi): String {
    val nearest = state.nearby.firstOrNull() ?: return context.getString(R.string.voice_none)
    val distance = DescribeDirection.roundDistance(nearest.distanceM)
    val heading = if (state.viewMode == ViewMode.Ar) o.heading else mapHeading(o)
    val s = nearest.station
    val name = if (s.system == TransportSystem.Metro) s.name else context.getString(s.system.labelRes) + " " + s.name
    if (heading.isNaN()) return context.getString(R.string.voice_nearest_nodir, name, distance)
    val dir = context.getString(directionRes(GeoMath.relativeAngle(nearest.bearing, heading)))
    return context.getString(R.string.voice_nearest, name, distance, dir)
}

private class Tts(context: Context) {
    private var ready = false
    private val engine = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }

    fun speak(context: Context, text: String) {
        if (!ready) return
        engine.language = Locale.forLanguageTag(context.getString(R.string.tts_locale))
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "metroar")
    }

    fun shutdown() = engine.shutdown()
}

@Composable
private fun rememberTts(context: Context): Tts {
    val tts = remember { Tts(context) }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }
    return tts
}

private fun vibrate(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 120, 250), -1))
}

private fun openWalkingRoute(context: Context, station: Station) {
    val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${station.lat},${station.lon}&mode=w"))
    val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${station.lat},${station.lon}(${Uri.encode(station.name)})"))
    runCatching { context.startActivity(nav) }.recoverCatching { context.startActivity(geo) }
}

