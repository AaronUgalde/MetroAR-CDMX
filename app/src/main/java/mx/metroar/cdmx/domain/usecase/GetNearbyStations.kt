package mx.metroar.cdmx.domain.usecase

import mx.metroar.cdmx.domain.geo.GeoMath
import mx.metroar.cdmx.domain.model.GeoPoint
import mx.metroar.cdmx.domain.model.Station

data class NearbyStation(
    val station: Station,
    val distanceM: Double,
    /** Rumbo verdadero desde el usuario hacia la estación. */
    val bearing: Double,
) {
    val walkingMinutes: Int get() = GeoMath.walkingMinutes(distanceM)
}

/**
 * Filtra las estaciones dentro de [radiusM] (y de las líneas elegidas) y las ordena por distancia.
 * Con ~165 estaciones de Metro basta con recorrerlas todas; no hace falta un índice espacial.
 */
class GetNearbyStations {
    operator fun invoke(
        stations: List<Station>,
        user: GeoPoint,
        radiusM: Double,
        lineFilter: Set<String> = emptySet(),
    ): List<NearbyStation> = stations.asSequence()
        .filter { s -> lineFilter.isEmpty() || s.lines.any { it.id in lineFilter } }
        .map { NearbyStation(it, GeoMath.distance(user, it.location), GeoMath.bearing(user, it.location)) }
        .filter { it.distanceM <= radiusM }
        .sortedBy { it.distanceM }
        .toList()
}
