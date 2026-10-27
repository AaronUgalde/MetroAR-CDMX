package mx.metroar.cdmx.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsRailway
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.metroar.cdmx.R
import mx.metroar.cdmx.domain.model.Line
import mx.metroar.cdmx.domain.model.Station
import mx.metroar.cdmx.domain.model.TransportSystem
import mx.metroar.cdmx.domain.repository.DistanceUnits
import mx.metroar.cdmx.domain.usecase.DescribeDirection
import mx.metroar.cdmx.domain.usecase.RelativeDirection
import java.util.Locale
import kotlin.math.roundToInt

val Line.composeColor: Color get() = Color(color)

/** Texto blanco o negro según el fondo, para cumplir contraste 4.5:1 (la Línea 5 es amarilla). */
val Line.contentColor: Color get() = if (composeColor.luminance() > 0.4f) Color(0xFF111111) else Color.White

val TransportSystem.labelRes: Int
    get() = when (this) {
        TransportSystem.Metro -> R.string.system_metro
        TransportSystem.Metrobus -> R.string.system_metrobus
        TransportSystem.TrenLigero -> R.string.system_tren_ligero
        TransportSystem.Cablebus -> R.string.system_cablebus
        TransportSystem.Suburbano -> R.string.system_suburbano
        TransportSystem.Interurbano -> R.string.system_interurbano
        TransportSystem.Trolebus -> R.string.system_trolebus
    }

val TransportSystem.icon: ImageVector
    get() = when (this) {
        TransportSystem.Metro -> Icons.Filled.DirectionsSubway
        TransportSystem.Metrobus -> Icons.Filled.DirectionsBus
        TransportSystem.TrenLigero -> Icons.Filled.Tram
        TransportSystem.Cablebus -> CableCarIcon
        TransportSystem.Suburbano -> Icons.Filled.Train
        TransportSystem.Interurbano -> Icons.Filled.DirectionsRailway
        TransportSystem.Trolebus -> Icons.Filled.ElectricBolt
    }

/**
 * El Metro usa círculo y los demás sistemas un cuadrado redondeado: así "Línea 1" del Metro y
 * "Línea 1" del Metrobús no se confunden aunque compartan número.
 */
private val TransportSystem.badgeShape: Shape
    get() = if (this == TransportSystem.Metro) CircleShape else RoundedCornerShape(28)

/** Nombre corto para hablar o mostrar: "Balderas" en el Metro, "Metrobús Balderas" en otros. */
@Composable
fun stationTitle(station: Station): String =
    if (station.system == TransportSystem.Metro) station.name
    else stringResource(station.system.labelRes) + " " + station.name

/** Color oficial y siempre el número, para quien no distingue colores. */
@Composable
fun LineBadge(line: Line, size: Dp = 26.dp, modifier: Modifier = Modifier) {
    val shape = line.system.badgeShape
    Box(
        modifier = modifier
            .size(size)
            .background(line.composeColor, shape)
            .border(1.5.dp, Color.White.copy(alpha = 0.9f), shape)
            .semantics { contentDescription = line.name },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = line.shortName,
            color = line.contentColor,
            fontWeight = FontWeight.Black,
            fontSize = (size.value * if (line.shortName.length > 1) 0.4f else 0.48f).sp,
            maxLines = 1,
        )
    }
}

@Composable
fun SystemIcon(system: TransportSystem, tint: Color, size: Dp = 16.dp, modifier: Modifier = Modifier) {
    Icon(system.icon, contentDescription = stringResource(system.labelRes), tint = tint, modifier = modifier.size(size))
}

/** Cabina colgando de un cable: Material no trae un ícono de teleférico. */
private val CableCarIcon: ImageVector by lazy {
    ImageVector.Builder("CableCar", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            // Cable inclinado.
            moveTo(2f, 6.5f); lineTo(22f, 2.5f); lineTo(22f, 4f); lineTo(2f, 8f); close()
            // Brazo que sostiene la cabina.
            moveTo(11.25f, 5.5f); lineTo(12.75f, 5.2f); lineTo(12.75f, 10f); lineTo(11.25f, 10f); close()
            // Cabina con ventanas.
            moveTo(6f, 10f); lineTo(18f, 10f); lineTo(18f, 21f); lineTo(6f, 21f); close()
        }
        path(fill = SolidColor(Color.White)) {
            moveTo(7.5f, 11.5f); lineTo(11.25f, 11.5f); lineTo(11.25f, 15f); lineTo(7.5f, 15f); close()
            moveTo(12.75f, 11.5f); lineTo(16.5f, 11.5f); lineTo(16.5f, 15f); lineTo(12.75f, 15f); close()
        }
    }.build()
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
fun directionText(relativeAngle: Double): String = stringResource(directionRes(relativeAngle))

fun directionRes(relativeAngle: Double): Int = when (DescribeDirection.of(relativeAngle)) {
    RelativeDirection.Ahead -> R.string.dir_ahead
    RelativeDirection.AheadRight -> R.string.dir_ahead_right
    RelativeDirection.Right -> R.string.dir_right
    RelativeDirection.Behind -> R.string.dir_behind
    RelativeDirection.Left -> R.string.dir_left
    RelativeDirection.AheadLeft -> R.string.dir_ahead_left
}
