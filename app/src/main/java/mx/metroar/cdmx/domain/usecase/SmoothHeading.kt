package mx.metroar.cdmx.domain.usecase

import mx.metroar.cdmx.domain.geo.GeoMath
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Suavizado exponencial de un ángulo circular. Promedia seno y coseno por separado, así el
 * paso de 359° a 0° no produce un giro de vuelta completa.
 *
 * [alpha] es el peso de la lectura nueva (0..1): más bajo = más estable, más lento.
 */
class SmoothHeading(private val alpha: Double = 0.15) {
    private var sinAvg = 0.0
    private var cosAvg = 0.0
    private var initialized = false

    fun update(degrees: Double): Double {
        val rad = Math.toRadians(degrees)
        if (!initialized) {
            sinAvg = sin(rad)
            cosAvg = cos(rad)
            initialized = true
        } else {
            sinAvg += alpha * (sin(rad) - sinAvg)
            cosAvg += alpha * (cos(rad) - cosAvg)
        }
        return value
    }

    val value: Double get() = GeoMath.normalize360(Math.toDegrees(atan2(sinAvg, cosAvg)))

    fun reset() {
        initialized = false
    }
}

/** Suavizado exponencial de un valor lineal (p. ej. la inclinación). */
class SmoothValue(private val alpha: Double = 0.15) {
    private var avg = Double.NaN

    fun update(v: Double): Double {
        avg = if (avg.isNaN()) v else avg + alpha * (v - avg)
        return avg
    }
}
