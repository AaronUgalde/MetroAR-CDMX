# MetroAR CDMX

Android augmented reality app: point the camera around you and see markers floating in the real direction of the nearest Metro stations, with each one's line (official color + number), distance and walking minutes.

> Independent project, not affiliated with the Sistema de Transporte Colectivo. It doesn't use official logos or trademarks.

## What's included (MVP 1.0 + parts of phase 2)

| Feature | Status |
|---|---|
| Camera view with markers in their real direction | ✅ |
| Line color and number, distance and minutes (80 m/min) | ✅ |
| Scaling and opacity by distance | ✅ |
| Off-screen arrows (left/right) | ✅ |
| No more than 8 markers; stacking and "+N" grouping | ✅ |
| Station sheet: lines, transfers, accessibility, hours, "Llévame ahí", favorite | ✅ |
| 2D offline map (no API keys): lines, stations, position, direction cone, radius | ✅ |
| Switches to map automatically when the phone lies flat | ✅ |
| Offline data (GTFS packaged in `assets/stations.json`) | ✅ |
| Compass and GPS accuracy indicator (green/yellow/red + text) | ✅ |
| Figure-8 calibration notice | ✅ |
| Walking navigation with an arrow, distance remaining and vibration on arrival | ✅ (to the station's center) |
| Line and radius filters, favorites | ✅ |
| Voice mode ("Estación Allende, 200 metros al frente") | ✅ |
| Dark mode | ✅ (follows the system) |
| 3-screen onboarding + permissions with explanations | ✅ |
| Multiple entrances per station, English, other transit systems, alerts | ⏳ phases 2–3 |

## How it works

For each frame: `α = θstation − θphone` (normalized to [−180°, 180°)), `x = W/2 + α/(FOV/2)·W/2`. The Y coordinate comes from the camera's elevation, so markers sit above the horizon.

- **Heading**: the `Rotation Vector` sensor (fallback: accelerometer + magnetometer). The heading comes directly from the camera vector (−Z of the device) in the rotation matrix, so it works with the phone upright or tilted without `remapCoordinateSystem`.
- **Magnetic declination**: `GeomagneticField` (≈ +4.5° in CDMX).
- **Smoothing**: exponential on sine/cosine (no 359°→0° jump).
- **Real FOV**: focal length and sensor size via Camera2, adjusted for the preview crop (`FILL_CENTER`).
- **GPS**: Fused Location Provider; readings worse than 30 m are ignored unless there's nothing better. Nearby stations are recalculated only after moving more than 20 m. Frequency drops when you're standing still. If Play Services sends nothing within 8 s, the app uses `LocationManager`.
- Camera and sensors only run while the screen is visible.

## Architecture

Clean Architecture + MVVM. The domain layer is pure Kotlin (no Android) and is tested with plain JUnit.

```
domain/      models, GeoMath (Haversine, bearing), ProjectToScreen, SmoothHeading,
             OrientationMath, FieldOfView, LocationFilter, GetNearbyStations
data/        JSON repository, fused location, Rotation Vector, DataStore (favorites/settings)
presentation/MainViewModel: combines location + orientation flows and exposes positioned markers
ui/          Compose: CameraX, AR overlay, Canvas map, sheets, onboarding, settings
```

Dependency injection is manual (`AppContainer` in `MetroArApp.kt`), and data lives in JSON + DataStore rather than Room. With ~160 stations a database isn't worth it, and this avoids KSP/Hilt. Migrating to Hilt/Room is mechanical if you ever need it.

## Data

`tools/build-stations.mjs` downloads the static GTFS from the [CDMX Open Data Portal](https://datos.cdmx.gob.mx), keeps only Metro (`agency_id = METRO`), merges platforms with the same name into transfer stations, and saves the station order for each line.

```bash
node tools/build-stations.mjs
```

Current result: 12 lines, 163 stations, 28 with transfers. The `update-stations.yml` workflow runs it every month and opens a PR if anything changed.

## Building

Requires Android Studio (JDK 17+ and SDK 37).

```bash
./gradlew :app:testDebugUnitTest   # 33 unit tests (domain + ViewModel)
./gradlew :app:assembleDebug
```

### Testing in the emulator

Start the emulator with `-camera-back virtualscene` and simulate location:

```bash
adb shell cmd location providers add-test-provider gps
adb shell cmd location providers set-test-provider-enabled gps true
adb shell cmd location providers set-test-provider-location gps --location 19.4340,-99.1372 --accuracy 6
```

## Privacy

Camera and location are used only while the app is open. Everything is computed on the device; neither the location nor the images leave the phone. There's no analytics.

## Next steps

- Coordinates for each entrance (geographic layers of the same portal / OpenStreetMap) and navigation to the nearest entrance.
- English strings (`values-en`).
- Metrobús, Tren Ligero, Cablebús, Trolebús (same GTFS, change `AGENCY` in the script).
- Optional ARCore Geospatial mode.
- Compose UI tests and a field log of angular error.
