package mx.metroar.cdmx.domain

import mx.metroar.cdmx.domain.geo.GeoMath
import mx.metroar.cdmx.domain.model.GeoPoint
import mx.metroar.cdmx.domain.model.Line
import mx.metroar.cdmx.domain.model.Station
import mx.metroar.cdmx.domain.model.TransportSystem
import mx.metroar.cdmx.domain.usecase.NearbyStation

object TestData {
    val origin = GeoPoint(19.432608, -99.133209)

    val line1 = Line("metro-1", "Línea 1", "1", 0xFFF94F8E, 0xFFFFFFFF, TransportSystem.Metro, "", emptyList())
    val line2 = Line("metro-2", "Línea 2", "2", 0xFF0071C1, 0xFFFFFFFF, TransportSystem.Metro, "", emptyList())

    fun station(id: String, at: GeoPoint, vararg lines: Line = arrayOf(line2)) =
        Station(id, id, at.lat, at.lon, lines.toList(), accessible = false, entrances = emptyList())

    /** Estación a [meters] del origen con rumbo [bearing]. */
    fun nearby(id: String, bearing: Double, meters: Double): NearbyStation {
        val p = GeoMath.destination(origin, bearing, meters)
        return NearbyStation(station(id, p), meters, bearing)
    }
}
