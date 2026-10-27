package mx.metroar.cdmx.domain.repository

import kotlinx.coroutines.flow.Flow
import mx.metroar.cdmx.domain.model.DeviceOrientation
import mx.metroar.cdmx.domain.model.Favorite
import mx.metroar.cdmx.domain.model.Line
import mx.metroar.cdmx.domain.model.Station
import mx.metroar.cdmx.domain.model.TransportSystem
import mx.metroar.cdmx.domain.model.UserLocation

interface StationRepository {
    suspend fun stations(): List<Station>
    suspend fun lines(): List<Line>
}

interface LocationProvider {
    /** Emite lecturas mientras haya suscriptores. [lowPower] baja la frecuencia cuando el usuario está quieto. */
    fun locations(lowPower: Flow<Boolean>): Flow<UserLocation>
    fun hasPreciseLocation(): Boolean
}

interface OrientationProvider {
    /** Orientación ya corregida por declinación magnética, a ~60 Hz. */
    fun orientation(declination: Flow<Float>): Flow<DeviceOrientation>
    val isAvailable: Boolean
}

interface MagneticDeclination {
    /** Declinación en grados, positiva al este (en CDMX ≈ +4° a +5°). */
    fun at(location: UserLocation): Float
}

interface FavoritesRepository {
    val favorites: Flow<List<Favorite>>
    suspend fun toggle(stationId: String, label: String)
}

enum class DistanceUnits { Metric, Imperial }

data class UserSettings(
    val radiusM: Int = 1500,
    val units: DistanceUnits = DistanceUnits.Metric,
    val lineFilter: Set<String> = emptySet(),
    val enabledSystems: Set<TransportSystem> = TransportSystem.DEFAULT_ENABLED,
    val onboardingDone: Boolean = false,
    val autoMapWhenFlat: Boolean = true,
)

interface SettingsRepository {
    val settings: Flow<UserSettings>
    suspend fun update(transform: (UserSettings) -> UserSettings)
}
