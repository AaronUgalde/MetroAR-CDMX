package mx.metroar.cdmx.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.metroar.cdmx.R
import mx.metroar.cdmx.domain.model.Favorite
import mx.metroar.cdmx.domain.model.Line
import mx.metroar.cdmx.domain.model.Station
import mx.metroar.cdmx.domain.repository.DistanceUnits
import kotlin.math.roundToInt

const val MIN_RADIUS_M = 300
const val MAX_RADIUS_M = 5000

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FiltersSheet(
    lines: List<Line>,
    selectedLines: Set<String>,
    radiusM: Int,
    units: DistanceUnits,
    favorites: List<Favorite>,
    stations: List<Station>,
    onToggleLine: (String) -> Unit,
    onClearLines: () -> Unit,
    onRadius: (Int) -> Unit,
    onFavoriteClick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var radius by remember { mutableFloatStateOf(radiusM.toFloat()) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(stringResource(R.string.search_radius), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(Format.radius(radius.roundToInt(), units), style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = radius,
                onValueChange = { radius = (it / 100f).roundToInt() * 100f },
                onValueChangeFinished = { onRadius(radius.roundToInt()) },
                valueRange = MIN_RADIUS_M.toFloat()..MAX_RADIUS_M.toFloat(),
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.filter_lines),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (selectedLines.isNotEmpty()) TextButton(onClick = onClearLines) { Text(stringResource(R.string.all_lines)) }
            }
            Text(
                stringResource(R.string.transport_metro_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                lines.forEach { line ->
                    FilterChip(
                        selected = line.id in selectedLines,
                        onClick = { onToggleLine(line.id) },
                        label = { Text(line.name) },
                        leadingIcon = { LineBadge(line, size = 22.dp) },
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text(stringResource(R.string.favorites), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (favorites.isEmpty()) {
                Text(
                    stringResource(R.string.favorites_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            val byId = stations.associateBy { it.id }
            favorites.sortedByDescending { it.createdAt }.forEach { fav ->
                val station = byId[fav.stationId] ?: return@forEach
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onFavoriteClick(station.id) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(fav.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    station.lines.forEach { LineBadge(it, size = 22.dp, modifier = Modifier.padding(start = 2.dp)) }
                }
            }
        }
    }
}
