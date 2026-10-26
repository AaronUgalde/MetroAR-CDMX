package mx.metroar.cdmx.domain.usecase

import kotlin.math.abs

enum class RelativeDirection { Ahead, AheadRight, Right, Behind, Left, AheadLeft }

/** Traduce un ángulo relativo a una dirección hablada ("a tu derecha"). */
object DescribeDirection {
    fun of(relativeAngle: Double): RelativeDirection {
        val a = abs(relativeAngle)
        val right = relativeAngle >= 0
        return when {
            a <= 20 -> RelativeDirection.Ahead
            a <= 60 -> if (right) RelativeDirection.AheadRight else RelativeDirection.AheadLeft
            a <= 135 -> if (right) RelativeDirection.Right else RelativeDirection.Left
            else -> RelativeDirection.Behind
        }
    }

    /** Redondea la distancia para decirla en voz alta o mostrarla. */
    fun roundDistance(meters: Double): Int = when {
        meters < 100 -> ((meters / 10).toInt().coerceAtLeast(1)) * 10
        meters < 1000 -> ((meters + 25) / 50).toInt() * 50
        else -> ((meters + 50) / 100).toInt() * 100
    }
}
