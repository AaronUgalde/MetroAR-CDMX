package mx.metroar.cdmx.domain.usecase

import mx.metroar.cdmx.domain.geo.GeoMath
import mx.metroar.cdmx.domain.model.GeoPoint
import mx.metroar.cdmx.domain.model.Station
import mx.metroar.cdmx.domain.model.TransportSystem

data class NearbyStation(
    val station: Station,
    val distanceM: Double,
    /** Rumbo verdadero desde el usuario hacia la estación. */
    val bearing: Double,
) {
    val walkingMinutes: Int get() = GeoMath.walkingMinutes(distanceM)
}

/**
 * Filtra las estaciones dentro de [radiusM], de los sistemas activos y de las líneas elegidas, y las
 * ordena por distancia. Con ~1,000 estaciones basta con recorrerlas todas: menos de 1 ms, y solo se
 * recalcula al moverse 20 m. No hace falta un índice espacial.
 */
class GetNearbyStations {
    operator fun invoke(
        stations: List<Station>,
        user: GeoPoint,
        radiusM: Double,
        lineFilter: Set<String> = emptySet(),
        systems: Set<TransportSystem> = TransportSystem.entries.toSet(),
    ): List<NearbyStation> = stations.asSequence()
        .filter { it.system in systems }
        .filter { s -> lineFilter.isEmpty() || s.lines.any { it.id in lineFilter } }
        .map { NearbyStation(it, GeoMath.distance(user, it.location), GeoMath.bearing(user, it.location)) }
        .filter { it.distanceM <= radiusM }
        .sortedBy { it.distanceM }
        .toList()
}
