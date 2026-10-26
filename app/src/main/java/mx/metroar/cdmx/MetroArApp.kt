package mx.metroar.cdmx

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import mx.metroar.cdmx.data.AndroidMagneticDeclination
import mx.metroar.cdmx.data.DataStoreFavoritesRepository
import mx.metroar.cdmx.data.DataStoreSettingsRepository
import mx.metroar.cdmx.data.FusedLocationProvider
import mx.metroar.cdmx.data.JsonStationRepository
import mx.metroar.cdmx.data.RotationVectorOrientationProvider
import mx.metroar.cdmx.data.dataStore
import mx.metroar.cdmx.presentation.MainViewModel

class MetroArApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Inyección de dependencias manual: un solo lugar donde se arma el grafo. */
class AppContainer(app: Application) {
    val stationRepository = JsonStationRepository(app)
    val locationProvider = FusedLocationProvider(app)
    val orientationProvider = RotationVectorOrientationProvider(app)
    val magneticDeclination = AndroidMagneticDeclination()
    val settingsRepository = DataStoreSettingsRepository(app.dataStore)
    val favoritesRepository = DataStoreFavoritesRepository(app.dataStore)

    val viewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            MainViewModel(
                stationRepository,
                locationProvider,
                orientationProvider,
                magneticDeclination,
                settingsRepository,
                favoritesRepository,
            )
        }
    }
}
