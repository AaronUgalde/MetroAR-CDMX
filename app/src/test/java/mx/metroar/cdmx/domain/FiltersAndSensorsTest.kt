package mx.metroar.cdmx.domain

import mx.metroar.cdmx.domain.TestData.line1
import mx.metroar.cdmx.domain.TestData.origin
import mx.metroar.cdmx.domain.TestData.station
import mx.metroar.cdmx.domain.geo.GeoMath
import mx.metroar.cdmx.domain.model.UserLocation
import mx.metroar.cdmx.domain.usecase.DescribeDirection
import mx.metroar.cdmx.domain.usecase.FieldOfView
import mx.metroar.cdmx.domain.usecase.FlatPhoneDetector
import mx.metroar.cdmx.domain.usecase.GetNearbyStations
import mx.metroar.cdmx.domain.usecase.LocationFilter
import mx.metroar.cdmx.domain.usecase.OrientationMath
import mx.metroar.cdmx.domain.usecase.RelativeDirection
import mx.metroar.cdmx.domain.usecase.SmoothHeading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FiltersAndSensorsTest {

    @Test
    fun `cercanas filtra por radio, linea y ordena por distancia`() {
        val stations = listOf(
            station("lejos", GeoMath.destination(origin, 0.0, 2000.0)),
            station("media", GeoMath.destination(origin, 90.0, 800.0), line1),
            station("cerca", GeoMath.destination(origin, 180.0, 200.0)),
        )
        val all = GetNearbyStations()(stations, origin, 1500.0)
        assertEquals(listOf("cerca", "media"), all.map { it.station.id })
        val onlyL1 = GetNearbyStations()(stations, origin, 1500.0, setOf(line1.id))
        assertEquals(listOf("media"), onlyL1.map { it.station.id })
        assertEquals(180.0, all[0].bearing, 0.1)
        assertEquals(3, all[0].walkingMinutes)
    }

    @Test
    fun `suavizado promedia bien al cruzar el norte`() {
        val s = SmoothHeading(alpha = 0.5)
        s.update(350.0)
        val v = s.update(10.0)
        // El promedio de 350° y 10° es 0°, no 180°.
        assertTrue("v=$v", v > 355.0 || v < 5.0)
    }

    @Test
    fun `suavizado converge a la lectura estable`() {
        val s = SmoothHeading(alpha = 0.2)
        s.update(90.0)
        repeat(60) { s.update(120.0) }
        assertEquals(120.0, s.value, 0.1)
    }

    @Test
    fun `matriz identidad - telefono acostado mirando al piso con la parte de arriba al norte`() {
        val identity = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        val a = OrientationMath.fromRotationMatrix(identity)
        assertEquals(-90.0, a.elevation, 1e-6)
        assertTrue(a.cameraAzimuth.isNaN())
        assertEquals(0.0, a.topAzimuth, 1e-6)
    }

    @Test
    fun `telefono vertical con la camara al norte`() {
        // Dispositivo rotado 90° sobre X: Y del dispositivo apunta arriba, −Z (cámara) al norte.
        val r = floatArrayOf(
            1f, 0f, 0f,
            0f, 0f, -1f,
            0f, 1f, 0f,
        )
        val a = OrientationMath.fromRotationMatrix(r)
        assertEquals(0.0, a.cameraAzimuth, 1e-6)
        assertEquals(0.0, a.elevation, 1e-6)
    }

    @Test
    fun `telefono vertical con la camara al este`() {
        // Cámara (−Z) hacia el este: tercera columna = (−1, 0, 0).
        val r = floatArrayOf(
            0f, 0f, -1f,
            -1f, 0f, 0f,
            0f, 1f, 0f,
        )
        val a = OrientationMath.fromRotationMatrix(r)
        assertEquals(90.0, a.cameraAzimuth, 1e-6)
    }

    @Test
    fun `detector de telefono acostado con histeresis`() {
        val d = FlatPhoneDetector()
        assertFalse(d.update(-30.0))
        assertTrue(d.update(-70.0))
        assertTrue(d.update(-50.0)) // sigue en mapa hasta subir más
        assertFalse(d.update(-20.0))
    }

    @Test
    fun `filtro de GPS ignora lecturas imprecisas recientes`() {
        val f = LocationFilter()
        assertTrue(f.accept(UserLocation(origin, 8f, 0)))
        val far = GeoMath.destination(origin, 0.0, 300.0)
        assertFalse(f.accept(UserLocation(far, 80f, 1_000)))
        assertTrue(f.accept(UserLocation(far, 80f, 20_000))) // la buena ya es vieja
    }

    @Test
    fun `FOV a partir de la optica de la camara`() {
        // Sensor típico 6.4 x 4.8 mm, focal 4.7 mm, pantalla 1080 x 2400.
        val fov = FieldOfView.fromCameraOptics(4.7, 6.4, 4.8, 1080, 2400)
        // La pantalla es más alta que la imagen 3:4, así que se recorta a los lados.
        assertEquals(68.5, fov.vertical, 1.0)
        assertTrue(fov.horizontal < 2 * Math.toDegrees(Math.atan(4.8 / (2 * 4.7))))
        assertTrue(fov.horizontal in 25.0..60.0)
    }

    @Test
    fun `direcciones habladas`() {
        assertEquals(RelativeDirection.Ahead, DescribeDirection.of(5.0))
        assertEquals(RelativeDirection.Right, DescribeDirection.of(90.0))
        assertEquals(RelativeDirection.AheadLeft, DescribeDirection.of(-45.0))
        assertEquals(RelativeDirection.Behind, DescribeDirection.of(-170.0))
        assertEquals(300, DescribeDirection.roundDistance(312.0))
        assertEquals(40, DescribeDirection.roundDistance(47.0))
    }
}
