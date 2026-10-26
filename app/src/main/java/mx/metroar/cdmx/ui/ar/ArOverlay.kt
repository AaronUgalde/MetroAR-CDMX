package mx.metroar.cdmx.ui.ar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.metroar.cdmx.R
import mx.metroar.cdmx.domain.repository.DistanceUnits
import mx.metroar.cdmx.domain.usecase.ArFrame
import mx.metroar.cdmx.domain.usecase.EdgeIndicator
import mx.metroar.cdmx.domain.usecase.EdgeSide
import mx.metroar.cdmx.domain.usecase.ScreenMarker
import mx.metroar.cdmx.ui.components.Format
import mx.metroar.cdmx.ui.components.LineBadge
import mx.metroar.cdmx.ui.components.directionText

/** Tamaño de referencia del marcador (escala 1.0); el ViewModel lo usa para evitar encimados. */
val MarkerWidth = 230.dp
val MarkerHeight = 66.dp

/** Dibuja los marcadores ya posicionados por el ViewModel. No hace cálculos de posición. */
@Composable
fun ArOverlay(
    frame: ArFrame,
    units: DistanceUnits,
    onStationClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    edgeTopPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    Box(modifier.fillMaxSize()) {
        Layout(
            content = { frame.markers.forEach { StationMarker(it, units, onStationClick) } },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val placeables = measurables.map { it.measure(loose) }
            layout(constraints.maxWidth, constraints.maxHeight) {
                // Las más lejanas primero, para que las cercanas queden encima.
                placeables.zip(frame.markers).reversed().forEach { (p, m) ->
                    p.place((m.x - p.width / 2f).toInt(), (m.y - p.height / 2f).toInt())
                }
            }
        }

        EdgeColumn(frame.edges.filter { it.side == EdgeSide.Left }, units, onStationClick,
            Modifier.align(Alignment.CenterStart).padding(top = edgeTopPadding))
        EdgeColumn(frame.edges.filter { it.side == EdgeSide.Right }, units, onStationClick,
            Modifier.align(Alignment.CenterEnd).padding(top = edgeTopPadding))
    }
}

@Composable
private fun StationMarker(marker: ScreenMarker, units: DistanceUnits, onClick: (String) -> Unit) {
    val n = marker.nearby
    val distance = Format.distance(n.distanceM, units)
    val minutes = stringResource(R.string.minutes_short, n.walkingMinutes)
    val lines = n.station.lines.joinToString(", ") { it.shortName }
    val description = stringResource(
        R.string.marker_a11y, n.station.name, lines, distance, n.walkingMinutes, directionText(marker.relativeAngle),
    )
    Row(
        modifier = Modifier
            .graphicsLayer {
                scaleX = marker.scale
                scaleY = marker.scale
                alpha = marker.alpha
            }
            .widthIn(max = MarkerWidth)
            .shadow(8.dp, RoundedCornerShape(18.dp))
            .background(Color.White, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button) { onClick(n.station.id) }
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
            n.station.lines.forEach { LineBadge(it, size = 30.dp) }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    n.station.name,
                    color = Color(0xFF111111),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (n.station.accessible) {
                    Icon(Icons.Filled.Accessible, null, tint = Color(0xFF1A73E8), modifier = Modifier.size(16.dp))
                }
            }
            Text("$distance · $minutes", color = Color(0xFF333333), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        if (marker.grouped.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.background(Color(0xFF222222), CircleShape).padding(horizontal = 7.dp, vertical = 3.dp),
            ) {
                Text("+${marker.grouped.size}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EdgeColumn(
    edges: List<EdgeIndicator>,
    units: DistanceUnits,
    onClick: (String) -> Unit,
    modifier: Modifier,
) {
    if (edges.isEmpty()) return
    Column(modifier.padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        edges.forEach { e -> EdgeArrow(e, units, onClick) }
    }
}

@Composable
private fun EdgeArrow(edge: EdgeIndicator, units: DistanceUnits, onClick: (String) -> Unit) {
    val n = edge.nearby
    val left = edge.side == EdgeSide.Left
    val distance = Format.distance(n.distanceM, units)
    val description = stringResource(R.string.edge_a11y, n.station.name, distance, directionText(edge.relativeAngle))
    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(50))
            .clickable(role = Role.Button) { onClick(n.station.id) }
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (left) Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(18.dp))
        n.station.lines.firstOrNull()?.let { LineBadge(it, size = 20.dp, modifier = Modifier.padding(horizontal = 4.dp)) }
        Column {
            Text(
                n.station.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 96.dp),
            )
            Text(distance, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
        }
        if (!left) Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}
