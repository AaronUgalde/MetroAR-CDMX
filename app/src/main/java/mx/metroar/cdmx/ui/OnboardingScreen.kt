package mx.metroar.cdmx.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mx.metroar.cdmx.R
import mx.metroar.cdmx.ui.theme.GoodGreen

private data class Page(val icon: ImageVector, val title: Int, val body: Int)

private val pages = listOf(
    Page(Icons.Filled.Explore, R.string.onb1_title, R.string.onb1_body),
    Page(Icons.Filled.TouchApp, R.string.onb2_title, R.string.onb2_body),
    Page(Icons.Filled.CloudOff, R.string.onb3_title, R.string.onb3_body),
)

/** Tres pantallas cortas y luego los permisos, cada uno con su razón. */
@Composable
fun OnboardingScreen(
    cameraGranted: Boolean,
    locationGranted: Boolean,
    onRequestCamera: () -> Unit,
    onRequestLocation: () -> Unit,
    onFinish: () -> Unit,
) {
    val pager = rememberPagerState { pages.size + 1 }
    val scope = rememberCoroutineScope()
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        HorizontalPager(pager, Modifier.weight(1f)) { index ->
            if (index < pages.size) {
                val p = pages[index]
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier.size(140.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(p.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp))
                    }
                    Spacer(Modifier.height(32.dp))
                    Text(
                        stringResource(p.title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(p.body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    Text(stringResource(R.string.permissions_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(20.dp))
                    PermissionCard(Icons.Filled.CameraAlt, R.string.perm_camera_title, R.string.perm_camera_why, cameraGranted, onRequestCamera)
                    Spacer(Modifier.height(12.dp))
                    PermissionCard(Icons.Filled.LocationOn, R.string.perm_location_title, R.string.perm_location_why, locationGranted, onRequestLocation)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.privacy_short), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(pages.size + 1) { i ->
                    Box(
                        Modifier.size(if (i == pager.currentPage) 10.dp else 8.dp).background(
                            if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape,
                        ),
                    )
                }
            }
            if (pager.currentPage < pages.size) {
                TextButton(onClick = { scope.launch { pager.animateScrollToPage(pages.size) } }) { Text(stringResource(R.string.skip)) }
                Button(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) { Text(stringResource(R.string.next)) }
            } else {
                Button(onClick = onFinish) { Text(stringResource(R.string.start)) }
            }
        }
    }
}

@Composable
private fun PermissionCard(icon: ImageVector, title: Int, why: Int, granted: Boolean, onRequest: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), fontWeight = FontWeight.Bold)
                Text(stringResource(why), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(8.dp))
            if (granted) {
                Icon(Icons.Filled.CheckCircle, stringResource(R.string.granted), tint = GoodGreen)
            } else {
                OutlinedButton(onClick = onRequest) { Text(stringResource(R.string.allow)) }
            }
        }
    }
}
