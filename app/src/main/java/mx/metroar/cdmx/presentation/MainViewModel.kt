package mx.metroar.cdmx.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.metroar.cdmx.domain.geo.GeoMath
import mx.metroar.cdmx.domain.model.DeviceOrientation
import mx.metroar.cdmx.domain.model.Favorite
import mx.metroar.cdmx.domain.model.Line
import mx.metroar.cdmx.domain.model.SensorAccuracy
import mx.metroar.cdmx.domain.model.Station
import mx.metroar.cdmx.domain.model.UserLocation
import mx.metroar.cdmx.domain.repository.FavoritesRepository
import mx.metroar.cdmx.domain.repository.LocationProvider
import mx.metroar.cdmx.domain.repository.MagneticDeclination
import mx.metroar.cdmx.domain.repository.OrientationProvider
import mx.metroar.cdmx.domain.repository.SettingsRepository
import mx.metroar.cdmx.domain.repository.StationRepository
import mx.metroar.cdmx.domain.repository.UserSettings
import mx.metroar.cdmx.domain.usecase.ArFrame
import mx.metroar.cdmx.domain.usecase.FieldOfView
import mx.metroar.cdmx.domain.usecase.FlatPhoneDetector
import mx.metroar.cdmx.domain.usecase.GetNearbyStations
import mx.metroar.cdmx.domain.usecase.LocationFilter
import mx.metroar.cdmx.domain.usecase.NearbyStation
import mx.metroar.cdmx.domain.usecase.ProjectToScreen
import mx.metroar.cdmx.domain.usecase.SmoothHeading
import mx.metroar.cdmx.domain.usecase.SmoothValue

enum class ViewMode { Ar, Map }

data class OrientationUi(
    /** Rumbo verdadero suavizado de la cámara; NaN si apunta casi vertical. */
    val heading: Double = Double.NaN,
    /** Rumbo del borde superior del teléfono, para el cono en el mapa. */
    val topHeading: Double = Double.NaN,
    val elevation: Double = 0.0,
    val accuracy: SensorAccuracy = SensorAccuracy.Medium,
    val received: Boolean = false,
)

data class NavigationState(
    val target: Station,
    val distanceM: Double,
    val bearing: Double,
    val arrived: Boolean,
)

data class MainUiState(
    val loading: Boolean = true,
    val lines: List<Line> = emptyList(),
    val stations: List<Station> = emptyList(),
    val location: UserLocation? = null,
    val nearby: List<NearbyStation> = emptyList(),
    val settings: UserSettings = UserSettings(),
    val favorites: List<Favorite> = emptyList(),
    val cameraGranted: Boolean = false,
    val locationGranted: Boolean = false,
    val preciseLocation: Boolean = true,
    val compassAvailable: Boolean = true,
    val viewMode: ViewMode = ViewMode.Ar,
    /** El modo mapa se activó solo porque el teléfono está acostado. */
    val autoMap: Boolean = false,
    val selectedStationId: String? = null,
    val navigation: NavigationState? = null,
) {
    val selectedStation: Station? get() = stations.firstOrNull { it.id == selectedStationId }
    fun isFavorite(stationId: String) = favorites.any { it.stationId == stationId }
}

data class ViewportInfo(val viewport: ProjectToScreen.Viewport, val fov: FieldOfView)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val stationRepository: StationRepository,
    private val locationProvider: LocationProvider,
    private val orientationProvider: OrientationProvider,
    private val magneticDeclination: MagneticDeclination,
    private val settingsRepository: SettingsRepository,
    private val favoritesRepository: FavoritesRepository,
) : ViewModel() {

    private val getNearby = GetNearbyStations()
    private val project = ProjectToScreen()
    private val locationFilter = LocationFilter()
    private val headingSmoother = SmoothHeading(alpha = 0.12)
    private val topSmoother = SmoothHeading(alpha = 0.12)
    private val elevationSmoother = SmoothValue(alpha = 0.15)
    private val flatDetector = FlatPhoneDetector()

    private val cameraGranted = MutableStateFlow(false)
    private val locationGranted = MutableStateFlow(false)
    private val userViewMode = MutableStateFlow(ViewMode.Ar)
    private val selectedStationId = MutableStateFlow<String?>(null)
    private val navTarget = MutableStateFlow<Station?>(null)
    private val viewport = MutableStateFlow<ViewportInfo?>(null)
    private val lowPower = MutableStateFlow(false)
    private val recent = ArrayDeque<UserLocation>()

    private val sharing = SharingStarted.WhileSubscribed(stopTimeoutMillis = 2_000)

    private val data: Flow<Pair<List<Line>, List<Station>>> = flow {
        emit(stationRepository.lines() to stationRepository.stations())
    }.shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    private val settings = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Lecturas aceptadas por el filtro de precisión. Solo corre mientras la UI está visible. */
    private val location: Flow<UserLocation?> = locationGranted
        .flatMapLatest { granted ->
            if (!granted) flowOf(null)
            else locationProvider.locations(lowPower)
                .filter { locationFilter.accept(it) }
                .onEach(::updateStationary)
                .map<UserLocation, UserLocation?> { it }
                .onStart { emit(locationFilter.current) }
        }
        .shareIn(viewModelScope, sharing, replay = 1)

    /** Ancla para recalcular cercanas: solo cambia cuando el usuario se mueve más de 20 m. */
    private val anchor: Flow<UserLocation?> = location.distinctUntilChanged { old, new ->
        old != null && new != null && GeoMath.distance(old.point, new.point) <= RECOMPUTE_DISTANCE_M
    }

    private val declination: Flow<Float> = location.filterNotNull()
        .map { magneticDeclination.at(it) }
        .distinctUntilChanged()
        .onStart { emit(CDMX_DECLINATION) }

    private val rawOrientation: Flow<DeviceOrientation> =
        if (orientationProvider.isAvailable) orientationProvider.orientation(declination) else emptyFlow()

    val orientation: StateFlow<OrientationUi> = rawOrientation
        .map { o ->
            val heading = if (o.cameraAzimuth.isNaN()) Double.NaN else headingSmoother.update(o.cameraAzimuth)
            OrientationUi(
                heading = heading,
                topHeading = topSmoother.update(o.topAzimuth),
                elevation = elevationSmoother.update(o.elevation),
                accuracy = o.accuracy,
                received = true,
            )
        }
        .stateIn(viewModelScope, sharing, OrientationUi())

    private val nearby: Flow<List<NearbyStation>> = combine(data, anchor, settings.filterNotNull()) { (_, stations), loc, s ->
        if (loc == null) emptyList()
        else getNearby(stations, loc.point, s.radiusM.toDouble(), s.lineFilter)
    }

    private val autoMap: Flow<Boolean> = combine(orientation, settings.filterNotNull()) { o, s ->
        o.received && s.autoMapWhenFlat && flatDetector.update(o.elevation)
    }.distinctUntilChanged()

    private val navigation: Flow<NavigationState?> = combine(navTarget, location) { target, loc ->
        if (target == null || loc == null) return@combine null
        val d = GeoMath.distance(loc.point, target.location)
        NavigationState(
            target = target,
            distanceM = d,
            bearing = GeoMath.bearing(loc.point, target.location),
            arrived = d <= ARRIVAL_RADIUS_M,
        )
    }

    val uiState: StateFlow<MainUiState> = combine(
        combine(data, location, nearby, settings, favoritesRepository.favorites) { d, loc, near, s, favs ->
            Base(d.first, d.second, loc, near, s, favs)
        },
        combine(cameraGranted, locationGranted, userViewMode, autoMap, selectedStationId) { cam, locG, mode, auto, sel ->
            Flags(cam, locG, mode, auto, sel)
        },
        navigation.onStart { emit(null) },
    ) { base, flags, nav ->
        val autoActive = flags.autoMap && flags.mode == ViewMode.Ar
        MainUiState(
            loading = base.settings == null,
            lines = base.lines,
            stations = base.stations,
            location = base.location,
            nearby = base.nearby,
            settings = base.settings ?: UserSettings(),
            favorites = base.favorites,
            cameraGranted = flags.camera,
            locationGranted = flags.location,
            preciseLocation = !flags.location || locationProvider.hasPreciseLocation(),
            compassAvailable = orientationProvider.isAvailable,
            viewMode = if (!flags.camera || flags.mode == ViewMode.Map || autoActive) ViewMode.Map else ViewMode.Ar,
            autoMap = autoActive && flags.camera,
            selectedStationId = flags.selected,
            navigation = nav,
        )
    }.stateIn(viewModelScope, sharing, MainUiState())

    /** Marcadores ya posicionados en pantalla; la vista solo los dibuja. */
    val arFrame: StateFlow<ArFrame> = combine(orientation, nearby.onStart { emit(emptyList()) }, viewport, settings) { o, near, vp, s ->
        if (vp == null || s == null) ArFrame.EMPTY
        else project(near, o.heading, o.elevation, vp.fov, vp.viewport, s.radiusM.toDouble())
    }.stateIn(viewModelScope, sharing, ArFrame.EMPTY)

    fun onPermissions(camera: Boolean, location: Boolean) {
        cameraGranted.value = camera
        locationGranted.value = location
    }

    fun onViewport(info: ViewportInfo) {
        viewport.value = info
    }

    fun toggleViewMode() {
        val current = uiState.value.viewMode
        userViewMode.value = if (current == ViewMode.Ar) ViewMode.Map else ViewMode.Ar
    }

    fun selectStation(id: String?) {
        selectedStationId.value = id
    }

    fun navigateTo(station: Station) {
        navTarget.value = station
        selectedStationId.value = null
        userViewMode.value = ViewMode.Ar
    }

    fun stopNavigation() {
        navTarget.value = null
    }

    fun toggleFavorite(station: Station) = viewModelScope.launch {
        favoritesRepository.toggle(station.id, station.name)
    }

    fun updateSettings(transform: (UserSettings) -> UserSettings) = viewModelScope.launch {
        settingsRepository.update(transform)
    }

    fun toggleLine(lineId: String) = updateSettings { s ->
        s.copy(lineFilter = if (lineId in s.lineFilter) s.lineFilter - lineId else s.lineFilter + lineId)
    }

    /** Baja la frecuencia del GPS cuando el usuario lleva un rato sin moverse. */
    private fun updateStationary(loc: UserLocation) {
        recent.addLast(loc)
        while (recent.size > 6) recent.removeFirst()
        val first = recent.first()
        val span = loc.timeMillis - first.timeMillis
        val moved = recent.maxOf { GeoMath.distance(first.point, it.point) }
        lowPower.update { wasLow ->
            when {
                moved > 10 -> false
                recent.size >= 6 && span >= 10_000 && moved < 5 -> true
                else -> wasLow
            }
        }
    }

    private data class Base(
        val lines: List<Line>,
        val stations: List<Station>,
        val location: UserLocation?,
        val nearby: List<NearbyStation>,
        val settings: UserSettings?,
        val favorites: List<Favorite>,
    )

    private data class Flags(
        val camera: Boolean,
        val location: Boolean,
        val mode: ViewMode,
        val autoMap: Boolean,
        val selected: String?,
    )

    companion object {
        const val RECOMPUTE_DISTANCE_M = 20.0
        const val ARRIVAL_RADIUS_M = 30.0
        const val CDMX_DECLINATION = 4.5f
    }
}
