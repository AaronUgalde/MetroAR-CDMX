package mx.metroar.cdmx.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.metroar.cdmx.R
import mx.metroar.cdmx.domain.geo.GeoMath
import mx.metroar.cdmx.domain.model.SensorAccuracy
import mx.metroar.cdmx.domain.repository.DistanceUnits
import mx.metroar.cdmx.presentation.NavigationState
import mx.metroar.cdmx.ui.theme.BadRed
import mx.metroar.cdmx.ui.theme.GoodGreen
import mx.metroar.cdmx.ui.theme.WarnAmber
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class Quality { Good, Fair, Poor }

fun gpsQuality(accuracyM: Float?): Quality = when {
    accuracyM == null -> Quality.Poor
    accuracyM <= 10f -> Quality.Good
    accuracyM <= 30f -> Quality.Fair
    else -> Quality.Poor
}

fun compassQuality(accuracy: SensorAccuracy): Quality = when (accuracy) {
    SensorAccuracy.High -> Quality.Good
    SensorAccuracy.Medium -> Quality.Fair
    else -> Quality.Poor
}

private val Quality.color get() = when (this) {
    Quality.Good -> GoodGreen
    Quality.Fair -> WarnAmber
    Quality.Poor -> BadRed
}

/** Indicador de precisión con color y texto (no depende solo del color). */
@Composable
fun StatusChip(icon: Boolean, label: String, value: String, quality: Quality) {
    val description = "$label: $value"
    Row(
        Modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (icon) Icons.Filled.GpsFixed else Icons.Filled.Explore, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.size(9.dp).background(quality.color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SafetyBanner(modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.WarningAmber, null, tint = WarnAmber, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.safety_banner), color = Color.White, fontSize = 12.sp)
    }
}

/** Aviso de calibración con un punto que recorre un 8 (lemniscata). */
@Composable
fun CalibrationCard(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "calib").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(width = 84.dp, height = 48.dp)) {
                val cx = size.width / 2
                val cy = size.height / 2
                val a = size.width / 2.3f
                fun lemniscate(u: Float): Offset {
                    val th = u * 2 * PI.toFloat()
                    val d = 1 + sin(th) * sin(th)
                    return Offset(cx + a * cos(th) / d, cy + a * sin(th) * cos(th) / d)
                }
                val path = Path()
                for (i in 0..60) {
                    val p = lemniscate(i / 60f)
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                drawPath(path, Color.Gray.copy(alpha = 0.5f), style = Stroke(3.dp.toPx()))
                drawCircle(WarnAmber, 7.dp.toPx(), lemniscate(t))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.calibrate_title), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.calibrate_body), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        }
    }
}

/** Flecha grande hacia la estación elegida, con distancia restante y aviso al llegar. */
@Composable
fun NavigationPanel(
    nav: NavigationState,
    heading: Double,
    units: DistanceUnits,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val relative = if (heading.isNaN()) 0.0 else GeoMath.relativeAngle(nav.bearing, heading)
    val distance = Format.distance(nav.distanceM, units)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (!nav.arrived) {
            Box(
                Modifier
                    .size(120.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Navigation,
                    contentDescription = stringResource(R.string.nav_arrow_a11y, directionText(relative)),
                    tint = Color.White,
                    modifier = Modifier.size(92.dp).rotate(relative.toFloat()),
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        Card(
            Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                    nav.target.lines.forEach { LineBadge(it, size = 30.dp) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(nav.target.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    if (nav.arrived) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, null, tint = GoodGreen, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.arrived), color = GoodGreen, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Text(
                            stringResource(R.string.nav_remaining, distance, GeoMath.walkingMinutes(nav.distanceM)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                TextButton(onClick = onStop) {
                    Text(stringResource(if (nav.arrived) R.string.done else R.string.stop))
                }
            }
        }
    }
}
