package mx.metroar.cdmx.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TransferWithinAStation
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.metroar.cdmx.R
import mx.metroar.cdmx.domain.geo.GeoMath
import mx.metroar.cdmx.domain.model.Station
import mx.metroar.cdmx.domain.model.TransportSystem
import mx.metroar.cdmx.domain.repository.DistanceUnits
import mx.metroar.cdmx.domain.usecase.NearbyStation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationSheet(
    station: Station,
    nearby: NearbyStation?,
    heading: Double,
    units: DistanceUnits,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onNavigate: () -> Unit,
    onOpenMaps: () -> Unit,
    onToggleFavorite: () -> Unit,
    /** Estaciones de otros sistemas cerca de esta, con su distancia en metros. */
    connections: List<Pair<Station, Double>> = emptyList(),
    onStationClick: (String) -> Unit = {},
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    station.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = stringResource(if (isFavorite) R.string.favorite_remove else R.string.favorite_add),
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SystemIcon(station.system, tint = MaterialTheme.colorScheme.primary, size = 18.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(station.system.labelRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (nearby != null) {
                val dir = if (heading.isNaN()) "" else " · " + directionText(GeoMath.relativeAngle(nearby.bearing, heading))
                Text(
                    Format.distance(nearby.distanceM, units) + " · " +
                        stringResource(R.string.minutes_walking, nearby.walkingMinutes) + dir,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))

            station.lines.forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    LineBadge(line, size = 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(line.name, fontWeight = FontWeight.SemiBold)
                        Text(line.terminals, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            InfoRow(
                Icons.Filled.SwapHoriz,
                stringResource(R.string.transfers),
                if (station.transfers.isEmpty()) stringResource(R.string.no_transfers)
                else stringResource(R.string.transfer_lines, station.lines.joinToString(", ") { it.shortName }),
            )
            if (connections.isNotEmpty()) {
                Row(Modifier.padding(vertical = 6.dp)) {
                    Icon(Icons.Filled.TransferWithinAStation, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(R.string.connections), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        connections.take(MAX_CONNECTIONS).forEach { (other, meters) ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(role = Role.Button) { onStationClick(other.id) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SystemIcon(other.system, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 18.dp)
                                Spacer(Modifier.width(6.dp))
                                other.lines.forEach { LineBadge(it, size = 22.dp, modifier = Modifier.padding(end = 2.dp)) }
                                Spacer(Modifier.width(6.dp))
                                Text(stationTitle(other), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    stringResource(R.string.walk_meters, Format.distance(meters, units)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            InfoRow(
                Icons.Filled.Accessible,
                stringResource(R.string.accessibility),
                stringResource(if (station.accessible) R.string.accessible_yes else R.string.accessible_unknown),
            )
            InfoRow(
                Icons.Outlined.MeetingRoom,
                stringResource(R.string.entrances),
                if (station.entrances.isEmpty()) stringResource(R.string.entrances_soon)
                else station.entrances.joinToString("\n") { it.name },
            )
            InfoRow(
                Icons.Filled.Schedule,
                stringResource(R.string.schedule),
                stringResource(if (station.system == TransportSystem.Metro) R.string.schedule_metro else R.string.schedule_check_official),
            )

            Spacer(Modifier.height(16.dp))
            Button(onClick = onNavigate, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Filled.Navigation, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.take_me_there), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onToggleFavorite, modifier = Modifier.weight(1f)) {
                    Icon(if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.favorite))
                }
                OutlinedButton(onClick = onOpenMaps, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Filled.DirectionsWalk, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.walking_route))
                }
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, body: String) {
    Row(Modifier.padding(vertical = 6.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private const val MAX_CONNECTIONS = 4
