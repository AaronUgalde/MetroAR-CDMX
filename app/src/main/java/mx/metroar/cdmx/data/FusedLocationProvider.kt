package mx.metroar.cdmx.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import mx.metroar.cdmx.domain.model.GeoPoint
import mx.metroar.cdmx.domain.model.UserLocation
import mx.metroar.cdmx.domain.repository.LocationProvider
import mx.metroar.cdmx.domain.repository.MagneticDeclination

/**
 * Ubicación fusionada (GPS + Wi-Fi + red) solo mientras la app está en uso. Si Google Play
 * Services no entrega nada en unos segundos (no instalado o caído), se usa LocationManager.
 */
class FusedLocationProvider(private val context: Context) : LocationProvider {
    private val client = LocationServices.getFusedLocationProviderClient(context)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun locations(lowPower: Flow<Boolean>): Flow<UserLocation> =
        lowPower.distinctUntilChanged().flatMapLatest { slow -> updates(slow) }

    @SuppressLint("MissingPermission") // El ViewModel solo se suscribe con permiso concedido.
    private fun updates(lowPower: Boolean): Flow<UserLocation> = callbackFlow {
        val request = if (lowPower) {
            LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 5_000L).build()
        } else {
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
                .setMinUpdateIntervalMillis(500L)
                .build()
        }
        var gotFused = false
        val fusedCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                gotFused = true
                result.locations.forEach { trySend(it.toUserLocation()) }
            }
        }
        runCatching {
            client.lastLocation.addOnSuccessListener { loc -> if (loc != null) trySend(loc.toUserLocation()) }
            client.requestLocationUpdates(request, fusedCallback, Looper.getMainLooper())
        }

        val platformListener = LocationListener { loc -> if (!gotFused) trySend(loc.toUserLocation()) }
        val fallback = launch {
            delay(FALLBACK_AFTER_MS)
            if (gotFused) return@launch
            val interval = if (lowPower) 5_000L else 1_000L
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .filter { runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false) }
                .forEach { provider ->
                    locationManager.getLastKnownLocation(provider)?.let { trySend(it.toUserLocation()) }
                    locationManager.requestLocationUpdates(provider, interval, 0f, platformListener, Looper.getMainLooper())
                }
        }
        awaitClose {
            fallback.cancel()
            client.removeLocationUpdates(fusedCallback)
            locationManager.removeUpdates(platformListener)
        }
    }

    private fun Location.toUserLocation() = UserLocation(
        point = GeoPoint(latitude, longitude),
        accuracyM = if (hasAccuracy()) accuracy else 50f,
        timeMillis = time,
        altitudeM = if (hasAltitude()) altitude else CDMX_ALTITUDE_M,
    )

    override fun hasPreciseLocation(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
}

private const val FALLBACK_AFTER_MS = 8_000L
private const val CDMX_ALTITUDE_M = 2240.0

/** Declinación magnética con el modelo WMM de Android. En CDMX ronda +4° a +5° (este). */
class AndroidMagneticDeclination : MagneticDeclination {
    override fun at(location: UserLocation): Float = GeomagneticField(
        location.point.lat.toFloat(),
        location.point.lon.toFloat(),
        location.altitudeM.toFloat(),
        location.timeMillis.takeIf { it > 0 } ?: System.currentTimeMillis(),
    ).declination
}
