package mx.metroar.cdmx.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import mx.metroar.cdmx.domain.geo.GeoMath
import mx.metroar.cdmx.domain.model.DeviceOrientation
import mx.metroar.cdmx.domain.model.SensorAccuracy
import mx.metroar.cdmx.domain.repository.OrientationProvider
import mx.metroar.cdmx.domain.usecase.OrientationMath

/**
 * Orientación a partir del sensor virtual Rotation Vector, que ya fusiona acelerómetro,
 * magnetómetro y giroscopio. Si el teléfono no lo tiene, se usa acelerómetro + magnetómetro.
 *
 * La precisión combina la del magnetómetro (lo que pide calibrar en forma de 8) con la
 * estimación de error de rumbo que da Rotation Vector en values[4].
 */
class RotationVectorOrientationProvider(context: Context) : OrientationProvider {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    override val isAvailable: Boolean
        get() = rotationSensor != null || (magnetometer != null && accelerometer != null)

    override fun orientation(declination: Flow<Float>): Flow<DeviceOrientation> = callbackFlow {
        var declinationDeg = 0f
        val declinationJob = launch { declination.collect { declinationDeg = it } }

        val rotation = FloatArray(9)
        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var hasGravity = false
        var hasMagnetic = false
        var magAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
        var headingErrorDeg = -1.0

        fun emit() {
            val a = OrientationMath.fromRotationMatrix(rotation)
            val d = declinationDeg.toDouble()
            trySend(
                DeviceOrientation(
                    cameraAzimuth = if (a.cameraAzimuth.isNaN()) Double.NaN else GeoMath.normalize360(a.cameraAzimuth + d),
                    topAzimuth = GeoMath.normalize360(a.topAzimuth + d),
                    elevation = a.elevation,
                    accuracy = combine(magAccuracy, headingErrorDeg),
                ),
            )
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotation, event.values)
                        if (event.values.size > 4 && event.values[4] >= 0f) {
                            headingErrorDeg = Math.toDegrees(event.values[4].toDouble())
                        }
                        emit()
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        lowPass(event.values, gravity, hasGravity); hasGravity = true
                        if (rotationSensor == null && hasMagnetic) fallback()
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        lowPass(event.values, geomagnetic, hasMagnetic); hasMagnetic = true
                        if (rotationSensor == null && hasGravity) fallback()
                    }
                }
            }

            private fun fallback() {
                if (SensorManager.getRotationMatrix(rotation, null, gravity, geomagnetic)) emit()
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) magAccuracy = accuracy
            }
        }

        rotationSensor?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        // El magnetómetro se registra siempre para conocer su precisión (y como respaldo).
        magnetometer?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        if (rotationSensor == null) {
            accelerometer?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        }

        awaitClose {
            declinationJob.cancel()
            sensorManager.unregisterListener(listener)
        }
    }

    private fun lowPass(input: FloatArray, output: FloatArray, initialized: Boolean, alpha: Float = 0.2f) {
        for (i in 0..2) output[i] = if (initialized) output[i] + alpha * (input[i] - output[i]) else input[i]
    }

    private fun combine(magStatus: Int, headingErrorDeg: Double): SensorAccuracy {
        val fromMag = when (magStatus) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> SensorAccuracy.High
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> SensorAccuracy.Medium
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> SensorAccuracy.Low
            else -> SensorAccuracy.Unreliable
        }
        val fromHeading = when {
            headingErrorDeg < 0 -> SensorAccuracy.High // el sensor no lo reporta
            headingErrorDeg <= 10 -> SensorAccuracy.High
            headingErrorDeg <= 20 -> SensorAccuracy.Medium
            headingErrorDeg <= 35 -> SensorAccuracy.Low
            else -> SensorAccuracy.Unreliable
        }
        return maxOf(fromMag, fromHeading) // el enum va de mejor a peor
    }
}
