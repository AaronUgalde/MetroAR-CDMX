package mx.metroar.cdmx.domain.usecase

import kotlin.math.atan
import kotlin.math.max

/** Campo de visión efectivo, en grados, de lo que se ve en pantalla. */
data class FieldOfView(val horizontal: Double, val vertical: Double) {
    companion object {
        val DEFAULT = FieldOfView(horizontal = 60.0, vertical = 75.0)

        /**
         * Calcula el FOV visible en un teléfono vertical a partir de la óptica real de la cámara
         * (distancia focal y tamaño físico del sensor, ambos en mm, con el sensor en horizontal).
         *
         * La vista previa llena la pantalla (FILL_CENTER), así que una parte de la imagen queda
         * recortada; el FOV visible es menor que el del sensor completo.
         */
        fun fromCameraOptics(
            focalLengthMm: Double,
            sensorWidthMm: Double,
            sensorHeightMm: Double,
            viewWidthPx: Int,
            viewHeightPx: Int,
        ): FieldOfView {
            if (focalLengthMm <= 0 || sensorWidthMm <= 0 || sensorHeightMm <= 0 || viewWidthPx <= 0 || viewHeightPx <= 0) {
                return DEFAULT
            }
            // En vertical el lado corto del sensor queda a lo ancho de la pantalla.
            val imgW = sensorHeightMm
            val imgH = sensorWidthMm
            val scale = max(viewWidthPx / imgW, viewHeightPx / imgH)
            val visibleW = viewWidthPx / scale
            val visibleH = viewHeightPx / scale
            val h = 2 * Math.toDegrees(atan(visibleW / (2 * focalLengthMm)))
            val v = 2 * Math.toDegrees(atan(visibleH / (2 * focalLengthMm)))
            return FieldOfView(h, v)
        }
    }
}
