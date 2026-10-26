package mx.metroar.cdmx.domain.model

/** Punto geográfico en grados decimales (WGS84). */
data class GeoPoint(val lat: Double, val lon: Double)

enum class TransportSystem { Metro, Metrobus, TrenLigero, Cablebus, Trolebus, Ecobici }

data class Line(
    val id: String,
    val name: String,
    val shortName: String,
    /** Color oficial en formato ARGB. */
    val color: Long,
    val textColor: Long,
    val system: TransportSystem,
    val terminals: String,
    /** Estaciones en orden de recorrido. */
    val stationIds: List<String>,
)

data class Entrance(
    val id: String,
    val stationId: String,
    val lat: Double,
    val lon: Double,
    val name: String,
    val hasElevator: Boolean,
)

data class Station(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val lines: List<Line>,
    val accessible: Boolean,
    val entrances: List<Entrance>,
) {
    val location get() = GeoPoint(lat, lon)

    /** Líneas con las que se puede transbordar en esta estación. */
    val transfers: List<Line> get() = if (lines.size > 1) lines else emptyList()
}

data class Favorite(val stationId: String, val label: String, val createdAt: Long)

/** Lectura de ubicación del usuario. [accuracyM] es el radio de error estimado. */
data class UserLocation(
    val point: GeoPoint,
    val accuracyM: Float,
    val timeMillis: Long,
    val altitudeM: Double = 0.0,
)

enum class SensorAccuracy { High, Medium, Low, Unreliable }

/**
 * Orientación del teléfono ya corregida a norte verdadero.
 * [cameraAzimuth]: rumbo hacia donde mira la cámara trasera (0° = norte, sentido horario).
 * [topAzimuth]: rumbo del borde superior del teléfono, útil cuando está acostado (vista de mapa).
 * [elevation]: ángulo de la cámara sobre el horizonte (−90° = mirando al piso).
 */
data class DeviceOrientation(
    val cameraAzimuth: Double,
    val topAzimuth: Double,
    val elevation: Double,
    val accuracy: SensorAccuracy,
)
