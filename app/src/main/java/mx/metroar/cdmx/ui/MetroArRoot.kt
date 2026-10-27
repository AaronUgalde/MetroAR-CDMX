package mx.metroar.cdmx.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.metroar.cdmx.presentation.MainViewModel
import mx.metroar.cdmx.ui.map.MapCamera

private enum class Screen { Main, Settings }

@Composable
fun MetroArRoot(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var camera by remember { mutableStateOf(context.granted(Manifest.permission.CAMERA)) }
    var location by remember { mutableStateOf(context.hasAnyLocation()) }
    var screen by rememberSaveable { mutableStateOf(Screen.Main) }
    var forceCalibration by rememberSaveable { mutableStateOf(false) }
    var replayIntro by rememberSaveable { mutableStateOf(false) }
    val mapCamera = remember { MapCamera() }

    // El usuario puede cambiar permisos desde Ajustes del sistema; se revisan al volver.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        camera = context.granted(Manifest.permission.CAMERA)
        location = context.hasAnyLocation()
    }
    LaunchedEffect(camera, location) { viewModel.onPermissions(camera, location) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { camera = it }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        location = context.hasAnyLocation()
    }
    val requestCamera = { cameraLauncher.launch(Manifest.permission.CAMERA) }
    val requestLocation = {
        locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    when {
        state.loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        !state.settings.onboardingDone || replayIntro -> OnboardingScreen(
            cameraGranted = camera,
            locationGranted = location,
            onRequestCamera = requestCamera,
            onRequestLocation = requestLocation,
            onFinish = {
                replayIntro = false
                viewModel.updateSettings { it.copy(onboardingDone = true) }
            },
        )
        screen == Screen.Settings -> {
            BackHandler { screen = Screen.Main }
            SettingsScreen(
                settings = state.settings,
                onUpdate = { viewModel.updateSettings(it) },
                onToggleSystem = { viewModel.toggleSystem(it) },
                onCalibrate = { forceCalibration = true; screen = Screen.Main },
                onShowIntro = { replayIntro = true; screen = Screen.Main },
                onBack = { screen = Screen.Main },
            )
        }
        else -> {
            BackHandler(enabled = state.navigation != null) { viewModel.stopNavigation() }
            MainScreen(
                viewModel = viewModel,
                state = state,
                mapCamera = mapCamera,
                forceCalibration = forceCalibration,
                onCalibrationShown = { forceCalibration = false },
                onRequestCamera = requestCamera,
                onRequestLocation = requestLocation,
                onOpenSettings = { screen = Screen.Settings },
            )
        }
    }
}

private fun Context.granted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun Context.hasAnyLocation() =
    granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)
