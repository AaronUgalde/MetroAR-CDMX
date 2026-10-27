package mx.metroar.cdmx.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import mx.metroar.cdmx.domain.model.Favorite
import mx.metroar.cdmx.domain.model.TransportSystem
import mx.metroar.cdmx.domain.repository.DistanceUnits
import mx.metroar.cdmx.domain.repository.FavoritesRepository
import mx.metroar.cdmx.domain.repository.SettingsRepository
import mx.metroar.cdmx.domain.repository.UserSettings

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "metroar")

class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    private object Keys {
        val radius = intPreferencesKey("radius_m")
        val units = stringPreferencesKey("units")
        val lines = stringSetPreferencesKey("line_filter")
        val systems = stringSetPreferencesKey("enabled_systems")
        val onboarding = booleanPreferencesKey("onboarding_done")
        val autoMap = booleanPreferencesKey("auto_map_when_flat")
    }

    override val settings: Flow<UserSettings> = store.data.map { it.toSettings() }

    private fun Preferences.toSettings(): UserSettings {
        val d = UserSettings()
        return UserSettings(
            radiusM = this[Keys.radius] ?: d.radiusM,
            units = this[Keys.units]?.let { runCatching { DistanceUnits.valueOf(it) }.getOrNull() } ?: d.units,
            lineFilter = this[Keys.lines] ?: d.lineFilter,
            enabledSystems = this[Keys.systems]
                ?.mapNotNull { name -> TransportSystem.entries.firstOrNull { it.name == name } }
                ?.toSet()
                ?: d.enabledSystems,
            onboardingDone = this[Keys.onboarding] ?: d.onboardingDone,
            autoMapWhenFlat = this[Keys.autoMap] ?: d.autoMapWhenFlat,
        )
    }

    override suspend fun update(transform: (UserSettings) -> UserSettings) {
        store.edit { p ->
            val next = transform(p.toSettings())
            p[Keys.radius] = next.radiusM
            p[Keys.units] = next.units.name
            p[Keys.lines] = next.lineFilter
            p[Keys.systems] = next.enabledSystems.map { it.name }.toSet()
            p[Keys.onboarding] = next.onboardingDone
            p[Keys.autoMap] = next.autoMapWhenFlat
        }
    }
}

class DataStoreFavoritesRepository(private val store: DataStore<Preferences>) : FavoritesRepository {
    private val key = stringPreferencesKey("favorites")
    private val serializer = ListSerializer(FavoriteDto.serializer())

    override val favorites: Flow<List<Favorite>> = store.data.map { p -> decode(p[key]) }

    override suspend fun toggle(stationId: String, label: String) {
        store.edit { p ->
            val list = decode(p[key])
            val next = if (list.any { it.stationId == stationId }) {
                list.filterNot { it.stationId == stationId }
            } else {
                list + Favorite(stationId, label, System.currentTimeMillis())
            }
            p[key] = Json.encodeToString(serializer, next.map { FavoriteDto(it.stationId, it.label, it.createdAt) })
        }
    }

    private fun decode(raw: String?): List<Favorite> = raw?.let {
        runCatching { Json.decodeFromString(serializer, it) }.getOrNull()
    }.orEmpty().map { Favorite(it.stationId, it.label, it.createdAt) }

    @Serializable
    private class FavoriteDto(val stationId: String, val label: String, val createdAt: Long)
}
