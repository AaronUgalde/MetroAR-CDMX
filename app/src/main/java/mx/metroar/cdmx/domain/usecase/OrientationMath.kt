package mx.metroar.cdmx.domain.usecase

import mx.metroar.cdmx.domain.geo.GeoMath
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Convierte la matriz de rotación del sensor (dispositivo → mundo, 3x3 en orden por filas,
 * mundo = Este, Norte, Arriba) en ángulos útiles para la vista AR.
 *
 * La cámara trasera mira hacia −Z del dispositivo, así que su dirección en el mundo es la
 * tercera columna negada. No hace falta remapCoordinateSystem porque calculamos el rumbo a
 * partir del vector de la cámara directamente.
 */
object OrientationMath {

    data class Angles(
        /** Rumbo magnético de la cámara; NaN si la cámara apunta casi vertical. */
        val cameraAzimuth: Double,
        /** Rumbo magnético del borde superior del teléfono. */
        val topAzimuth: Double,
        /** Elevación de la cámara sobre el horizonte, en grados. */
        val elevation: Double,
    )

    fun fromRotationMatrix(r: FloatArray): Angles {
        require(r.size >= 9)
        val camEast = -r[2].toDouble()
        val camNorth = -r[5].toDouble()
        val camUp = -r[8].toDouble()
        val horizontal = hypot(camEast, camNorth)
        val cameraAzimuth = if (horizontal < 0.05) Double.NaN
        else GeoMath.normalize360(Math.toDegrees(atan2(camEast, camNorth)))
        val topAzimuth = GeoMath.normalize360(Math.toDegrees(atan2(r[1].toDouble(), r[4].toDouble())))
        val elevation = Math.toDegrees(asin(camUp.coerceIn(-1.0, 1.0)))
        return Angles(cameraAzimuth, topAzimuth, elevation)
    }
}

/**
 * Decide si conviene la vista de mapa porque el teléfono está acostado. Usa histéresis para no
 * parpadear entre vistas cerca del umbral.
 */
class FlatPhoneDetector(
    private val enterBelowDeg: Double = -60.0,
    private val exitAboveDeg: Double = -40.0,
) {
    var isFlat = false
        private set

    fun update(elevation: Double): Boolean {
        isFlat = if (isFlat) elevation < exitAboveDeg else elevation < enterBelowDeg
        return isFlat
    }
}
