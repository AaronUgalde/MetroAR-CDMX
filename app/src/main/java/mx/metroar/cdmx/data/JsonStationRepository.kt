package mx.metroar.cdmx.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mx.metroar.cdmx.domain.model.Entrance
import mx.metroar.cdmx.domain.model.Line
import mx.metroar.cdmx.domain.model.Station
import mx.metroar.cdmx.domain.model.TransportSystem
import mx.metroar.cdmx.domain.repository.StationRepository

/**
 * Estaciones empaquetadas en assets/stations.json (generado por tools/build-stations.mjs a partir
 * del GTFS de la CDMX). No necesita internet.
 */
class JsonStationRepository(private val context: Context) : StationRepository {
    private val mutex = Mutex()
    private var cache: Pair<List<Line>, List<Station>>? = null

    override suspend fun stations(): List<Station> = load().second
    override suspend fun lines(): List<Line> = load().first

    private suspend fun load(): Pair<List<Line>, List<Station>> = mutex.withLock {
        cache ?: withContext(Dispatchers.IO) {
            val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            parse(text)
        }.also { cache = it }
    }

    companion object {
        const val ASSET = "stations.json"
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): Pair<List<Line>, List<Station>> {
            val dto = json.decodeFromString(StationsFileDto.serializer(), text)
            val lines = dto.lines.map { it.toDomain() }
            val byId = lines.associateBy { it.id }
            val stations = dto.stations.map { s ->
                Station(
                    id = s.id,
                    name = s.name,
                    lat = s.lat,
                    lon = s.lon,
                    lines = s.lineIds.mapNotNull { byId[it] },
                    accessible = s.accessible,
                    entrances = s.entrances.map { Entrance(it.id, s.id, it.lat, it.lon, it.name, it.hasElevator) },
                )
            }
            return lines to stations
        }

        private fun LineDto.toDomain() = Line(
            id = id,
            name = name,
            shortName = shortName,
            color = parseColor(color),
            textColor = parseColor(textColor),
            system = runCatching { TransportSystem.valueOf(system) }.getOrDefault(TransportSystem.Metro),
            terminals = terminals,
            stationIds = stationIds,
        )

        private fun parseColor(hex: String): Long = 0xFF000000 or hex.removePrefix("#").toLong(16)
    }
}

@Serializable
private class StationsFileDto(
    val version: String = "",
    val lines: List<LineDto>,
    val stations: List<StationDto>,
)

@Serializable
private class LineDto(
    val id: String,
    val name: String,
    val shortName: String,
    val color: String,
    val textColor: String = "#FFFFFF",
    val system: String = "Metro",
    val terminals: String = "",
    val stationIds: List<String> = emptyList(),
)

@Serializable
private class StationDto(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val lineIds: List<String>,
    val accessible: Boolean = false,
    val entrances: List<EntranceDto> = emptyList(),
)

@Serializable
private class EntranceDto(
    val id: String,
    val lat: Double,
    val lon: Double,
    val name: String = "",
    val hasElevator: Boolean = false,
)
