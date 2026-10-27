package mx.metroar.cdmx.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import mx.metroar.cdmx.domain.model.TransportSystem
import mx.metroar.cdmx.domain.repository.DistanceUnits
import mx.metroar.cdmx.domain.repository.UserSettings
import mx.metroar.cdmx.ui.components.Format
import mx.metroar.cdmx.ui.components.MAX_RADIUS_M
import mx.metroar.cdmx.ui.components.MIN_RADIUS_M
import mx.metroar.cdmx.ui.components.SystemIcon
import mx.metroar.cdmx.ui.components.labelRes
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    onUpdate: ((UserSettings) -> UserSettings) -> Unit,
    onToggleSystem: (TransportSystem) -> Unit,
    onCalibrate: () -> Unit,
    onShowIntro: () -> Unit,
    onBack: () -> Unit,
) {
    var radius by remember(settings.radiusM) { mutableFloatStateOf(settings.radiusM.toFloat()) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Section(stringResource(R.string.search_radius))
            Text(Format.radius(radius.roundToInt(), settings.units))
            Slider(
                value = radius,
                onValueChange = { radius = (it / 100f).roundToInt() * 100f },
                onValueChangeFinished = { onUpdate { s -> s.copy(radiusM = radius.roundToInt()) } },
                valueRange = MIN_RADIUS_M.toFloat()..MAX_RADIUS_M.toFloat(),
            )

            Section(stringResource(R.string.units))
            val options = listOf(DistanceUnits.Metric to R.string.units_metric, DistanceUnits.Imperial to R.string.units_imperial)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { i, (u, label) ->
                    SegmentedButton(
                        selected = settings.units == u,
                        onClick = { onUpdate { s -> s.copy(units = u) } },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    ) { Text(stringResource(label)) }
                }
            }

            Section(stringResource(R.string.view))
            SwitchRow(stringResource(R.string.auto_map), settings.autoMapWhenFlat) { on -> onUpdate { it.copy(autoMapWhenFlat = on) } }

            Section(stringResource(R.string.transport_types))
            TransportSystem.entries.forEach { system ->
                val on = system in settings.enabledSystems
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    SystemIcon(system, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 20.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(system.labelRes))
                        if (system == TransportSystem.Trolebus) {
                            Text(
                                stringResource(R.string.trolebus_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    // El último sistema activo no se puede apagar.
                    Switch(
                        checked = on,
                        onCheckedChange = { onToggleSystem(system) },
                        enabled = !on || settings.enabledSystems.size > 1,
                    )
                }
            }

            Section(stringResource(R.string.compass))
            LinkRow(stringResource(R.string.calibrate_compass), onCalibrate)

            Section(stringResource(R.string.language))
            Text(stringResource(R.string.language_body), style = MaterialTheme.typography.bodyMedium)

            Section(stringResource(R.string.privacy))
            Text(stringResource(R.string.privacy_long), style = MaterialTheme.typography.bodyMedium)

            Section(stringResource(R.string.credits))
            Text(stringResource(R.string.credits_body), style = MaterialTheme.typography.bodyMedium)

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            LinkRow(stringResource(R.string.show_intro), onShowIntro)
            Text(
                stringResource(R.string.version_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
    )
}
