package mx.metroar.cdmx.domain.geo

import mx.metroar.cdmx.domain.model.GeoPoint
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GeoMath {
    const val EARTH_RADIUS_M = 6_371_000.0

    /** Velocidad de caminata usada para estimar minutos (80 m por minuto). */
    const val WALK_METERS_PER_MINUTE = 80.0

    /** Distancia en metros con la fórmula de Haversine. */
    fun distance(from: GeoPoint, to: GeoPoint): Double {
        val phi1 = Math.toRadians(from.lat)
        val phi2 = Math.toRadians(to.lat)
        val dPhi = phi2 - phi1
        val dLambda = Math.toRadians(to.lon - from.lon)
        val a = sin(dPhi / 2).let { it * it } +
            cos(phi1) * cos(phi2) * sin(dLambda / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Rumbo inicial de [from] hacia [to]: 0° = norte, sentido horario, en [0, 360). */
    fun bearing(from: GeoPoint, to: GeoPoint): Double {
        val phi1 = Math.toRadians(from.lat)
        val phi2 = Math.toRadians(to.lat)
        val dLambda = Math.toRadians(to.lon - from.lon)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        return normalize360(Math.toDegrees(atan2(y, x)))
    }

    /** Lleva cualquier ángulo al rango [−180, 180). Evita el salto de 359° a 0°. */
    fun normalize180(degrees: Double): Double {
        val a = normalize360(degrees)
        return if (a >= 180.0) a - 360.0 else a
    }

    fun normalize360(degrees: Double): Double {
        val a = degrees % 360.0
        return if (a < 0) a + 360.0 else a
    }

    /** Diferencia angular con signo de [from] a [to]; positivo = a la derecha. */
    fun relativeAngle(to: Double, from: Double): Double = normalize180(to - from)

    fun walkingMinutes(meters: Double): Int = maxOf(1, ceil(meters / WALK_METERS_PER_MINUTE).toInt())

    /** Punto a [meters] de distancia de [from] en dirección [bearingDeg]. Útil en pruebas y simulación. */
    fun destination(from: GeoPoint, bearingDeg: Double, meters: Double): GeoPoint {
        val delta = meters / EARTH_RADIUS_M
        val theta = Math.toRadians(bearingDeg)
        val phi1 = Math.toRadians(from.lat)
        val lambda1 = Math.toRadians(from.lon)
        val phi2 = asin(sin(phi1) * cos(delta) + cos(phi1) * sin(delta) * cos(theta))
        val lambda2 = lambda1 + atan2(sin(theta) * sin(delta) * cos(phi1), cos(delta) - sin(phi1) * sin(phi2))
        return GeoPoint(Math.toDegrees(phi2), Math.toDegrees(lambda2))
    }
}
