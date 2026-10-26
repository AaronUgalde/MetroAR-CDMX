package mx.metroar.cdmx.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import mx.metroar.cdmx.domain.model.GeoPoint
import mx.metroar.cdmx.domain.model.Line
import mx.metroar.cdmx.domain.model.Station
import mx.metroar.cdmx.ui.components.composeColor
import mx.metroar.cdmx.ui.theme.UserBlue
import kotlin.math.cos

/** Estado de cámara del mapa: metros por píxel y desplazamiento. Se conserva entre vistas. */
class MapCamera {
    var metersPerPx by mutableFloatStateOf(Float.NaN)
    var pan by mutableStateOf(Offset.Zero)
    fun recenter() {
        pan = Offset.Zero
    }
}

/**
 * Mapa 2D sin conexión: estaciones y trazos de cada línea en una proyección local
 * equirectangular (suficientemente exacta a escala de ciudad), con tu posición y un cono de
 * dirección. No usa mapas base, así funciona sin internet ni claves de API.
 */
@Composable
fun StationMap(
    lines: List<Line>,
    stations: List<Station>,
    user: GeoPoint?,
    userAccuracyM: Float?,
    heading: Double,
    radiusM: Int,
    highlightIds: Set<String>,
    selectedId: String?,
    camera: MapCamera,
    a11yDescription: String,
    onStationClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val measurer = rememberTextMeasurer()
    val center = user ?: ZOCALO
    val byId = remember(stations) { stations.associateBy { it.id } }
    val transform = rememberTransformableState { zoom, pan, _ ->
        camera.metersPerPx = (camera.metersPerPx / zoom).coerceIn(0.5f, 120f)
        camera.pan += pan
    }

    Canvas(
        modifier
            .semantics { contentDescription = a11yDescription }
            .transformable(transform)
            .pointerInput(stations, center) {
                detectTapGestures { tap ->
                    val proj = Projection(center, camera.metersPerPx, Size(size.width.toFloat(), size.height.toFloat()), camera.pan)
                    val hit = stations.minByOrNull { s -> (proj.toScreen(s.lat, s.lon) - tap).getDistance() }
                    if (hit != null && (proj.toScreen(hit.lat, hit.lon) - tap).getDistance() < 36.px(density)) {
                        onStationClick(hit.id)
                    }
                }
            },
    ) {
        if (camera.metersPerPx.isNaN()) camera.metersPerPx = (radiusM * 2.3f / size.minDimension).coerceIn(0.5f, 120f)
        val proj = Projection(center, camera.metersPerPx, size, camera.pan)
        drawRect(if (dark) Color(0xFF16181C) else Color(0xFFF1EFEA))

        val lineWidth = 5.px(density)
        lines.forEach { line ->
            val path = Path()
            line.stationIds.mapNotNull { byId[it] }.forEachIndexed { i, s ->
                val p = proj.toScreen(s.lat, s.lon)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            drawPath(path, line.composeColor, style = Stroke(lineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        if (user != null) {
            val u = proj.toScreen(user.lat, user.lon)
            drawCircle(
                UserBlue.copy(alpha = 0.6f), radiusM / camera.metersPerPx, u,
                style = Stroke(1.5f.px(density), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))),
            )
        }

        val showAllLabels = camera.metersPerPx < 6f
        stations.forEach { s ->
            val p = proj.toScreen(s.lat, s.lon)
            if (p.x < -50 || p.y < -50 || p.x > size.width + 50 || p.y > size.height + 50) return@forEach
            val transfer = s.lines.size > 1
            val r = (if (transfer) 7f else 5f).px(density)
            val selected = s.id == selectedId
            drawCircle(Color.White, r * if (selected) 1.6f else 1f, p)
            drawCircle(
                if (transfer) Color(0xFF222222) else s.lines.first().composeColor,
                r * if (selected) 1.6f else 1f, p, style = Stroke(2.5f.px(density)),
            )
            if (showAllLabels || s.id in highlightIds || selected) {
                drawLabel(measurer, s.name, p + Offset(r + 4.px(density), -9.px(density)), dark, bold = selected)
            }
        }

        if (user != null) drawUser(proj.toScreen(user.lat, user.lon), heading, userAccuracyM, camera.metersPerPx)
        // Debajo de la barra de estado y los indicadores de arriba.
        drawLabel(measurer, "N ↑", Offset(size.width - 44.px(density), 150.px(density)), dark, bold = true)
    }
}

private fun DrawScope.drawUser(p: Offset, heading: Double, accuracyM: Float?, metersPerPx: Float) {
    if (accuracyM != null) drawCircle(UserBlue.copy(alpha = 0.12f), (accuracyM / metersPerPx).coerceAtLeast(10f), p)
    if (!heading.isNaN()) {
        rotate(heading.toFloat(), pivot = p) {
            val len = 70.px(density)
            val cone = Path().apply {
                moveTo(p.x, p.y)
                lineTo(p.x - len * 0.55f, p.y - len)
                quadraticTo(p.x, p.y - len * 1.15f, p.x + len * 0.55f, p.y - len)
                close()
            }
            drawPath(cone, UserBlue.copy(alpha = 0.28f))
        }
    }
    drawCircle(Color.White, 10.px(density), p)
    drawCircle(UserBlue, 7.px(density), p)
}

private fun DrawScope.drawLabel(
    measurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    at: Offset,
    dark: Boolean,
    bold: Boolean = false,
) {
    val style = TextStyle(
        color = if (dark) Color(0xFFEDEDED) else Color(0xFF1B1B1B),
        fontSize = 12.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
        shadow = androidx.compose.ui.graphics.Shadow(if (dark) Color.Black else Color.White, blurRadius = 6f),
    )
    drawText(measurer, text, at, style, softWrap = false, maxLines = 1)
}

private class Projection(val center: GeoPoint, val metersPerPx: Float, val size: Size, val pan: Offset) {
    private val mPerDegLat = 110_574.0
    private val mPerDegLon = 111_320.0 * cos(Math.toRadians(center.lat))

    fun toScreen(lat: Double, lon: Double): Offset = Offset(
        (size.width / 2 + ((lon - center.lon) * mPerDegLon / metersPerPx).toFloat() + pan.x),
        (size.height / 2 - ((lat - center.lat) * mPerDegLat / metersPerPx).toFloat() + pan.y),
    )
}

private fun Float.px(density: Float) = this * density
private fun Int.px(density: Float) = this * density

val ZOCALO = GeoPoint(19.432608, -99.133209)
