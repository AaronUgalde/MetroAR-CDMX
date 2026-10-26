package mx.metroar.cdmx.domain

import mx.metroar.cdmx.domain.geo.GeoMath
import mx.metroar.cdmx.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathTest {
    private val zocalo = GeoPoint(19.432608, -99.133209)
    private val bellasArtes = GeoPoint(19.435478, -99.141270)

    @Test
    fun `distancia Zocalo a Bellas Artes es de unos 900 m`() {
        val d = GeoMath.distance(zocalo, bellasArtes)
        assertEquals(900.0, d, 30.0)
    }

    @Test
    fun `distancia a uno mismo es cero`() {
        assertEquals(0.0, GeoMath.distance(zocalo, zocalo), 1e-9)
    }

    @Test
    fun `un grado de latitud mide unos 111 km`() {
        val d = GeoMath.distance(GeoPoint(19.0, -99.0), GeoPoint(20.0, -99.0))
        assertEquals(111_195.0, d, 50.0)
    }

    @Test
    fun `rumbo del Zocalo hacia Bellas Artes es cercano al oeste`() {
        val b = GeoMath.bearing(zocalo, bellasArtes)
        // Oeste-noroeste: unos 290°.
        assertTrue("rumbo $b", b in 270.0..300.0)
    }

    @Test
    fun `rumbos cardinales`() {
        val o = GeoPoint(19.4, -99.1)
        assertEquals(0.0, GeoMath.bearing(o, GeoPoint(19.5, -99.1)), 0.01)
        assertEquals(180.0, GeoMath.bearing(o, GeoPoint(19.3, -99.1)), 0.01)
        assertEquals(90.0, GeoMath.bearing(o, GeoPoint(19.4, -99.0)), 0.1)
        assertEquals(270.0, GeoMath.bearing(o, GeoPoint(19.4, -99.2)), 0.1)
    }

    @Test
    fun `normalizar angulos al rango -180 a 180`() {
        assertEquals(0.0, GeoMath.normalize180(360.0), 1e-9)
        assertEquals(-10.0, GeoMath.normalize180(350.0), 1e-9)
        assertEquals(10.0, GeoMath.normalize180(-350.0), 1e-9)
        assertEquals(-180.0, GeoMath.normalize180(180.0), 1e-9)
        assertEquals(179.0, GeoMath.normalize180(-181.0), 1e-9)
    }

    @Test
    fun `angulo relativo cruza de 359 a 0 sin saltos`() {
        assertEquals(2.0, GeoMath.relativeAngle(to = 1.0, from = 359.0), 1e-9)
        assertEquals(-2.0, GeoMath.relativeAngle(to = 359.0, from = 1.0), 1e-9)
    }

    @Test
    fun `minutos caminando a 80 m por minuto`() {
        assertEquals(1, GeoMath.walkingMinutes(10.0))
        assertEquals(5, GeoMath.walkingMinutes(350.0))
        assertEquals(19, GeoMath.walkingMinutes(1500.0))
    }

    @Test
    fun `destination es inversa de distance y bearing`() {
        val p = GeoMath.destination(zocalo, 45.0, 500.0)
        assertEquals(500.0, GeoMath.distance(zocalo, p), 0.5)
        assertEquals(45.0, GeoMath.bearing(zocalo, p), 0.1)
    }
}
