package mx.metroar.cdmx.domain

import mx.metroar.cdmx.domain.TestData.nearby
import mx.metroar.cdmx.domain.usecase.EdgeSide
import mx.metroar.cdmx.domain.usecase.FieldOfView
import mx.metroar.cdmx.domain.usecase.ProjectToScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectToScreenTest {
    private val project = ProjectToScreen()
    private val fov = FieldOfView(horizontal = 60.0, vertical = 80.0)
    private val vp = ProjectToScreen.Viewport(1000f, 2000f, markerWidthPx = 200f, markerHeightPx = 100f)

    @Test
    fun `estacion al frente queda al centro`() {
        val frame = project(listOf(nearby("a", 90.0, 300.0)), heading = 90.0, elevation = 0.0, fov, vp, 1500.0)
        assertEquals(1, frame.markers.size)
        assertEquals(500f, frame.markers[0].x, 0.5f)
    }

    @Test
    fun `estacion en el borde del FOV queda en el borde de la pantalla`() {
        val frame = project(listOf(nearby("a", 30.0, 300.0)), heading = 0.0, elevation = 0.0, fov, vp, 1500.0)
        assertEquals(1000f, frame.markers[0].x, 0.5f)
        val left = project(listOf(nearby("b", 345.0, 300.0)), heading = 0.0, elevation = 0.0, fov, vp, 1500.0)
        assertEquals(250f, left.markers[0].x, 0.5f)
    }

    @Test
    fun `funciona al cruzar el norte`() {
        val frame = project(listOf(nearby("a", 5.0, 300.0)), heading = 355.0, elevation = 0.0, fov, vp, 1500.0)
        // α = +10° → x = 500 + 10/30 * 500
        assertEquals(666.7f, frame.markers[0].x, 0.5f)
    }

    @Test
    fun `estaciones fuera del FOV se vuelven flechas en el borde correcto`() {
        val frame = project(
            listOf(nearby("detras-der", 120.0, 300.0), nearby("izq", 280.0, 400.0)),
            heading = 0.0, elevation = 0.0, fov, vp, 1500.0,
        )
        assertTrue(frame.markers.isEmpty())
        assertEquals(EdgeSide.Right, frame.edges.first { it.nearby.station.id == "detras-der" }.side)
        assertEquals(EdgeSide.Left, frame.edges.first { it.nearby.station.id == "izq" }.side)
    }

    @Test
    fun `el horizonte baja cuando la camara mira hacia arriba`() {
        val level = project(listOf(nearby("a", 0.0, 300.0)), 0.0, elevation = 0.0, fov, vp, 1500.0)
        val up = project(listOf(nearby("a", 0.0, 300.0)), 0.0, elevation = 20.0, fov, vp, 1500.0)
        assertEquals(1000f, level.horizonY, 0.5f)
        assertEquals(1500f, up.horizonY, 0.5f)
        assertTrue(up.markers[0].y > level.markers[0].y)
    }

    @Test
    fun `cercanas se ven mas grandes y opacas`() {
        val frame = project(
            listOf(nearby("cerca", -10.0, 100.0), nearby("lejos", 10.0, 1400.0)),
            0.0, 0.0, fov, vp, 1500.0,
        )
        val cerca = frame.markers.first { it.nearby.station.id == "cerca" }
        val lejos = frame.markers.first { it.nearby.station.id == "lejos" }
        assertTrue(cerca.scale > lejos.scale)
        assertTrue(cerca.alpha > lejos.alpha)
    }

    @Test
    fun `marcadores en la misma direccion se apilan y luego se agrupan`() {
        val same = (1..5).map { nearby("s$it", 0.0, 100.0 * it) }
        val frame = project(same, 0.0, 0.0, fov, vp, 1500.0)
        // Tres niveles de apilado; las otras dos se agrupan bajo un marcador.
        assertEquals(3, frame.markers.size)
        assertEquals(3, frame.markers.map { it.y }.distinct().size)
        assertEquals(2, frame.markers.sumOf { it.grouped.size })
    }

    @Test
    fun `nunca mas de ocho marcadores`() {
        val many = (0 until 20).map { nearby("s$it", -29.0 + it * 3.0, 100.0 + it) }
        val frame = project(many, 0.0, 0.0, fov, vp.copy(markerWidthPx = 10f), 1500.0)
        assertEquals(8, frame.markers.size)
        assertEquals(12, frame.markers.sumOf { it.grouped.size })
    }

    @Test
    fun `sin rumbo valido no dibuja nada`() {
        val frame = project(listOf(nearby("a", 0.0, 100.0)), Double.NaN, 0.0, fov, vp, 1500.0)
        assertTrue(frame.markers.isEmpty() && frame.edges.isEmpty())
    }
}
