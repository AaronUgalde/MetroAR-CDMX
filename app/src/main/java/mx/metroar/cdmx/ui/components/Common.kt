package mx.metroar.cdmx.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.metroar.cdmx.R
import mx.metroar.cdmx.domain.model.Line
import mx.metroar.cdmx.domain.repository.DistanceUnits
import mx.metroar.cdmx.domain.usecase.DescribeDirection
import mx.metroar.cdmx.domain.usecase.RelativeDirection
import java.util.Locale
import kotlin.math.roundToInt

val Line.composeColor: Color get() = Color(color)

/** Texto blanco o negro según el fondo, para cumplir contraste 4.5:1 (la Línea 5 es amarilla). */
val Line.contentColor: Color get() = if (composeColor.luminance() > 0.4f) Color(0xFF111111) else Color.White

/** Círculo con el color oficial y siempre el número, para quien no distingue colores. */
@Composable
fun LineBadge(line: Line, size: Dp = 26.dp, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.line_n, line.shortName)
    Box(
        modifier = modifier
            .size(size)
            .background(line.composeColor, CircleShape)
            .border(1.5.dp, Color.White.copy(alpha = 0.9f), CircleShape)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = line.shortName,
            color = line.contentColor,
            fontWeight = FontWeight.Black,
            fontSize = (size.value * 0.48f).sp,
            maxLines = 1,
        )
    }
}

object Format {
    fun distance(meters: Double, units: DistanceUnits): String = when (units) {
        DistanceUnits.Metric -> if (meters < 1000) "${DescribeDirection.roundDistance(meters)} m"
        else String.format(Locale.US, "%.1f km", meters / 1000)
        DistanceUnits.Imperial -> {
            val feet = meters * 3.28084
            if (feet < 1000) "${(feet / 10).roundToInt() * 10} ft"
            else String.format(Locale.US, "%.1f mi", meters / 1609.344)
        }
    }

    fun radius(meters: Int, units: DistanceUnits): String = distance(meters.toDouble(), units)
}

@Composable
fun directionText(relativeAngle: Double): String = stringResource(
    when (DescribeDirection.of(relativeAngle)) {
        RelativeDirection.Ahead -> R.string.dir_ahead
        RelativeDirection.AheadRight -> R.string.dir_ahead_right
        RelativeDirection.Right -> R.string.dir_right
        RelativeDirection.Behind -> R.string.dir_behind
        RelativeDirection.Left -> R.string.dir_left
        RelativeDirection.AheadLeft -> R.string.dir_ahead_left
    },
)
