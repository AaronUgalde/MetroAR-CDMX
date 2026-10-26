package mx.metroar.cdmx.domain.usecase

import mx.metroar.cdmx.domain.geo.GeoMath
import kotlin.math.abs

data class ScreenMarker(
    val nearby: NearbyStation,
    /** Centro del marcador en píxeles de la vista. */
    val x: Float,
    val y: Float,
    val scale: Float,
    val alpha: Float,
    /** Ángulo relativo a la cámara: negativo = izquierda. */
    val relativeAngle: Double,
    /** Estaciones agrupadas bajo este marcador porque no cabían sin encimarse. */
    val grouped: List<NearbyStation> = emptyList(),
)

enum class EdgeSide { Left, Right }

data class EdgeIndicator(
    val nearby: NearbyStation,
    val side: EdgeSide,
    val relativeAngle: Double,
)

data class ArFrame(
    val markers: List<ScreenMarker>,
    val edges: List<EdgeIndicator>,
    val horizonY: Float,
) {
    companion object {
        val EMPTY = ArFrame(emptyList(), emptyList(), 0f)
    }
}

/**
 * Proyecta estaciones a coordenadas de pantalla comparando el rumbo hacia cada estación con el
 * rumbo de la cámara:
 *
 *     α = θestación − θteléfono  (normalizado a [−180°, 180°))
 *     x = W/2 + α / (FOV/2) · W/2
 *
 * La coordenada Y sale de la elevación de la cámara, para que los marcadores queden sobre el
 * horizonte. Los marcadores que se enciman se apilan en niveles; si no caben, se agrupan.
 */
class ProjectToScreen(
    private val maxMarkers: Int = 8,
    private val maxLevels: Int = 3,
    private val maxEdgesPerSide: Int = 3,
) {
    data class Viewport(
        val widthPx: Float,
        val heightPx: Float,
        val markerWidthPx: Float,
        val markerHeightPx: Float,
        val topInsetPx: Float = 0f,
        val bottomInsetPx: Float = 0f,
    )

    operator fun invoke(
        stations: List<NearbyStation>,
        heading: Double,
        elevation: Double,
        fov: FieldOfView,
        viewport: Viewport,
        radiusM: Double,
    ): ArFrame {
        if (viewport.widthPx <= 0f || viewport.heightPx <= 0f || heading.isNaN()) return ArFrame.EMPTY
        val w = viewport.widthPx
        val h = viewport.heightPx
        val halfH = fov.horizontal / 2
        val halfV = fov.vertical / 2
        val horizonY = (h / 2 + (elevation / halfV) * h / 2).toFloat()

        val sorted = stations.sortedBy { it.distanceM }
        val visible = mutableListOf<Pair<NearbyStation, Double>>()
        val edges = mutableListOf<EdgeIndicator>()
        for (s in sorted) {
            val alpha = GeoMath.relativeAngle(s.bearing, heading)
            if (abs(alpha) <= halfH) {
                visible += s to alpha
            } else {
                edges += EdgeIndicator(s, if (alpha < 0) EdgeSide.Left else EdgeSide.Right, alpha)
            }
        }

        class Placed(val s: NearbyStation, val alpha: Double, val x: Float, val level: Int, val scale: Float) {
            val grouped = mutableListOf<NearbyStation>()
        }

        val placed = mutableListOf<Placed>()
        for ((s, alpha) in visible) {
            val x = (w / 2 + (alpha / halfH) * w / 2).toFloat()
            val scale = scaleFor(s.distanceM, radiusM)
            val minSep = viewport.markerWidthPx * scale * 0.9f
            val level = if (placed.size >= maxMarkers) -1 else (0 until maxLevels).firstOrNull { lvl ->
                placed.none { it.level == lvl && abs(it.x - x) < minSep }
            } ?: -1
            if (level >= 0) {
                placed += Placed(s, alpha, x, level, scale)
            } else {
                placed.minByOrNull { abs(it.x - x) }?.grouped?.add(s)
            }
        }

        val minY = viewport.topInsetPx + viewport.markerHeightPx / 2
        val maxY = h - viewport.bottomInsetPx - viewport.markerHeightPx / 2
        val markers = placed.map { p ->
            val rawY = horizonY - viewport.markerHeightPx * (0.7f + p.level * 1.1f)
            ScreenMarker(
                nearby = p.s,
                x = p.x,
                y = if (minY < maxY) rawY.coerceIn(minY, maxY) else rawY,
                scale = p.scale,
                alpha = alphaFor(p.s.distanceM, radiusM),
                relativeAngle = p.alpha,
                grouped = p.grouped.toList(),
            )
        }
        val limitedEdges = edges.filter { it.side == EdgeSide.Left }.take(maxEdgesPerSide) +
            edges.filter { it.side == EdgeSide.Right }.take(maxEdgesPerSide)
        return ArFrame(markers, limitedEdges, horizonY)
    }

    companion object {
        /** Más cerca = más grande. 1.0 a 0 m, 0.6 en el borde del radio. */
        fun scaleFor(distanceM: Double, radiusM: Double): Float =
            (1.0 - 0.4 * (distanceM / radiusM).coerceIn(0.0, 1.0)).toFloat()

        /** Más cerca = más opaco. 1.0 a 0 m, 0.65 en el borde del radio. */
        fun alphaFor(distanceM: Double, radiusM: Double): Float =
            (1.0 - 0.35 * (distanceM / radiusM).coerceIn(0.0, 1.0)).toFloat()
    }
}
