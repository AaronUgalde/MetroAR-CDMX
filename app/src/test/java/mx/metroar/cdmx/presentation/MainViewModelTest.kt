package mx.metroar.cdmx.presentation

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import mx.metroar.cdmx.domain.TestData
import mx.metroar.cdmx.domain.geo.GeoMath
import mx.metroar.cdmx.domain.model.DeviceOrientation
import mx.metroar.cdmx.domain.model.Favorite
import mx.metroar.cdmx.domain.model.Line
import mx.metroar.cdmx.domain.model.SensorAccuracy
import mx.metroar.cdmx.domain.model.TransportSystem
import mx.metroar.cdmx.domain.model.UserLocation
import mx.metroar.cdmx.domain.repository.FavoritesRepository
import mx.metroar.cdmx.domain.repository.LocationProvider
import mx.metroar.cdmx.domain.repository.MagneticDeclination
import mx.metroar.cdmx.domain.repository.OrientationProvider
import mx.metroar.cdmx.domain.repository.SettingsRepository
import mx.metroar.cdmx.domain.repository.StationRepository
import mx.metroar.cdmx.domain.repository.UserSettings
import mx.metroar.cdmx.domain.usecase.FieldOfView
import mx.metroar.cdmx.domain.usecase.ProjectToScreen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val origin = TestData.origin
    private val north = TestData.station("norte", GeoMath.destination(origin, 0.0, 300.0))
    private val east = TestData.station("este", GeoMath.destination(origin, 90.0, 600.0))
    private val far = TestData.station("lejos", GeoMath.destination(origin, 180.0, 5000.0))

    private val locations = MutableSharedFlow<UserLocation>(replay = 1)
    private val orientations = MutableSharedFlow<DeviceOrientation>(replay = 1)

    private val repo = object : StationRepository {
        override suspend fun stations() = listOf(north, east, far)
        override suspend fun lines() = listOf(TestData.line2, TestData.mb1)
    }
    private val locationProvider = object : LocationProvider {
        override fun locations(lowPower: Flow<Boolean>) = locations
        override fun hasPreciseLocation() = true
    }
    private val orientationProvider = object : OrientationProvider {
        override val isAvailable = true
        override fun orientation(declination: Flow<Float>) = orientations
    }
    private val settings = object : SettingsRepository {
        val state = MutableStateFlow(UserSettings(radiusM = 1500, onboardingDone = true))
        override val settings = state
        override suspend fun update(transform: (UserSettings) -> UserSettings) { state.value = transform(state.value) }
    }
    private val favorites = object : FavoritesRepository {
        val state = MutableStateFlow(emptyList<Favorite>())
        override val favorites: Flow<List<Favorite>> = state
        override suspend fun toggle(stationId: String, label: String) {
            state.value = if (state.value.any { it.stationId == stationId }) state.value.filterNot { it.stationId == stationId }
            else state.value + Favorite(stationId, label, 0)
        }
    }

    private lateinit var vm: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        vm = MainViewModel(repo, locationProvider, orientationProvider, object : MagneticDeclination {
            override fun at(location: UserLocation) = 0f
        }, settings, favorites)
        vm.onPermissions(camera = true, location = true)
        vm.onViewport(
            ViewportInfo(
                ProjectToScreen.Viewport(1000f, 2000f, markerWidthPx = 200f, markerHeightPx = 100f),
                FieldOfView(60.0, 80.0),
            ),
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun facing(heading: Double, elevation: Double = 0.0) =
        DeviceOrientation(heading, heading, elevation, SensorAccuracy.High)

    @Test
    fun `mirando al norte la estacion norte queda al centro y la del este es una flecha`() = runTest {
        locations.emit(UserLocation(origin, 5f, 0))
        orientations.emit(facing(0.0))
        vm.arFrame.test {
            val frame = awaitItemWhere { it.markers.isNotEmpty() }
            assertEquals(listOf("norte"), frame.markers.map { it.nearby.station.id })
            assertEquals(500f, frame.markers[0].x, 1f)
            assertEquals(listOf("este"), frame.edges.map { it.nearby.station.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `al girar al este cambia el marcador visible`() = runTest {
        locations.emit(UserLocation(origin, 5f, 0))
        orientations.emit(facing(90.0))
        vm.arFrame.test {
            val frame = awaitItemWhere { it.markers.isNotEmpty() }
            assertEquals(listOf("este"), frame.markers.map { it.nearby.station.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filtra por radio y cambia a mapa al acostar el telefono`() = runTest {
        locations.emit(UserLocation(origin, 5f, 0))
        orientations.emit(facing(0.0, elevation = -85.0))
        vm.uiState.test {
            val s = awaitItemWhere { it.nearby.isNotEmpty() && it.autoMap }
            assertEquals(listOf("norte", "este"), s.nearby.map { it.station.id })
            assertEquals(ViewMode.Map, s.viewMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `navegacion avisa al llegar`() = runTest {
        locations.emit(UserLocation(origin, 5f, 0))
        orientations.emit(facing(0.0))
        vm.navigateTo(north)
        vm.uiState.test {
            val going = awaitItemWhere { it.navigation != null }
            assertTrue(!going.navigation!!.arrived)
            assertEquals(300.0, going.navigation!!.distanceM, 1.0)
            locations.emit(UserLocation(GeoMath.destination(origin, 0.0, 290.0), 5f, 1_000))
            val there = awaitItemWhere { it.navigation?.arrived == true }
            assertEquals("norte", there.navigation!!.target.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `apagar un sistema quita sus lineas del filtro y nunca deja cero sistemas`() = runTest {
        settings.state.value = settings.state.value.copy(
            enabledSystems = setOf(TransportSystem.Metro, TransportSystem.Metrobus),
            lineFilter = setOf("metro-2", "mb-1"),
        )
        vm.uiState.test {
            awaitItemWhere { it.lines.isNotEmpty() }
            vm.toggleSystem(TransportSystem.Metrobus).join()
            assertEquals(setOf(TransportSystem.Metro), settings.state.value.enabledSystems)
            assertEquals(setOf("metro-2"), settings.state.value.lineFilter)
            vm.toggleSystem(TransportSystem.Metro).join()
            assertEquals(setOf(TransportSystem.Metro), settings.state.value.enabledSystems)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `favoritos se agregan y quitan`() = runTest {
        vm.uiState.test {
            vm.toggleFavorite(north)
            assertTrue(awaitItemWhere { it.isFavorite("norte") }.isFavorite("norte"))
            vm.toggleFavorite(north)
            awaitItemWhere { !it.isFavorite("norte") && !it.loading }
            cancelAndIgnoreRemainingEvents()
        }
    }
}

/** Consume eventos hasta encontrar uno que cumpla la condición. */
private suspend fun <T> app.cash.turbine.ReceiveTurbine<T>.awaitItemWhere(predicate: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}

