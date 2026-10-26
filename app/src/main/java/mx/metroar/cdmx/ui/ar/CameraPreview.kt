package mx.metroar.cdmx.ui.ar

import android.hardware.camera2.CameraCharacteristics
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Óptica de la cámara trasera, para calcular el campo de visión real. */
data class CameraOptics(val focalLengthMm: Double, val sensorWidthMm: Double, val sensorHeightMm: Double)

/**
 * Vista previa de la cámara trasera a pantalla completa. CameraX la liga al ciclo de vida, así
 * que se pausa sola al salir de la pantalla o bloquear el teléfono. No se graba ni se sube nada.
 */
@Composable
fun CameraPreview(modifier: Modifier = Modifier, onOptics: (CameraOptics?) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            val p = future.get()
            provider = p
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            runCatching {
                p.unbindAll()
                val camera = p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                onOptics(readOptics(camera.cameraInfo))
            }.onFailure { onOptics(null) }
        }, ContextCompat.getMainExecutor(context))
        onDispose { provider?.unbindAll() }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

@OptIn(ExperimentalCamera2Interop::class)
private fun readOptics(info: androidx.camera.core.CameraInfo): CameraOptics? = runCatching {
    val c2 = Camera2CameraInfo.from(info)
    val focal = c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
    val size = c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
    if (focal == null || size == null || focal <= 0f) null
    else CameraOptics(focal.toDouble(), size.width.toDouble(), size.height.toDouble())
}.getOrNull()
