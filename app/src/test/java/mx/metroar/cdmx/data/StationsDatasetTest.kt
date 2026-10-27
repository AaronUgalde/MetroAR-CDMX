package mx.metroar.cdmx.data

import mx.metroar.cdmx.domain.geo.GeoMath
import mx.metroar.cdmx.domain.model.TransportSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Revisa el stations.json empaquetado, tal como lo genera tools/build-stations.mjs. */
class StationsDatasetTest {
    private val parsed = JsonStationRepository.parse(File("src/main/assets/stations.json").readText())
    private val lines = parsed.first
    private val stations = parsed.second
    private val byId = stations.associateBy { it.id }

    @Test
    fun `el Metro conserva sus 163 estaciones y 12 lineas`() {
        assertEquals(163, stations.count { it.system == TransportSystem.Metro })
        assertEquals(12, lines.count { it.system == TransportSystem.Metro })
        // Los IDs del Metro no llevan prefijo: los favoritos guardados antes siguen funcionando.
        assertTrue("allende" in byId && "balderas" in byId)
    }

    @Test
    fun `cada sistema tiene estaciones y todas sus lineas existen`() {
        TransportSystem.entries.forEach { system ->
            assertTrue("sin estaciones: $system", stations.any { it.system == system })
        }
        stations.forEach { s ->
            assertTrue("sin líneas: ${s.id}", s.lines.isNotEmpty())
            assertTrue("línea de otro sistema en ${s.id}", s.lines.all { it.system == s.system })
        }
        lines.forEach { l -> assertTrue("línea vacía: ${l.id}", l.stationIds.all { it in byId }) }
    }

    @Test
    fun `las conexiones apuntan a estaciones cercanas de otro sistema`() {
        stations.forEach { s ->
            s.connectionIds.forEach { id ->
                val other = byId.getValue(id)
                assertTrue(other.system != s.system)
                assertTrue(GeoMath.distance(s.location, other.location) < 350.0)
            }
        }
        assertTrue("mb-balderas" in byId.getValue("balderas").connectionIds)
        assertTrue("cb-indios-verdes" in byId.getValue("indios-verdes").connectionIds)
    }
}
