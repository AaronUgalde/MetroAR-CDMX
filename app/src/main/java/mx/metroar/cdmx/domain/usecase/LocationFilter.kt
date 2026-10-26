package mx.metroar.cdmx.domain.usecase

import mx.metroar.cdmx.domain.model.UserLocation

/**
 * Descarta lecturas de GPS poco fiables (efecto cañón urbano). Las lecturas con precisión peor
 * a [maxAccuracyM] se ignoran, salvo que la última buena sea más vieja que [staleAfterMs]
 * (mejor una posición aproximada que ninguna) o que la nueva sea al menos igual de precisa.
 */
class LocationFilter(
    private val maxAccuracyM: Float = 30f,
    private val staleAfterMs: Long = 10_000,
) {
    var current: UserLocation? = null
        private set

    /** Devuelve true si la lectura se aceptó como posición actual. */
    fun accept(reading: UserLocation): Boolean {
        val prev = current
        val ok = prev == null ||
            reading.accuracyM <= maxAccuracyM ||
            reading.timeMillis - prev.timeMillis > staleAfterMs ||
            reading.accuracyM <= prev.accuracyM
        if (ok) current = reading
        return ok
    }
}
