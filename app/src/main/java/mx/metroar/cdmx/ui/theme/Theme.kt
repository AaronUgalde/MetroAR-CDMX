package mx.metroar.cdmx.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Naranja neutro propio de la app (evita los colores y marcas oficiales del STC). */
val Brand = Color(0xFFE8590C)
val GoodGreen = Color(0xFF1E9E4A)
val WarnAmber = Color(0xFFE0A100)
val BadRed = Color(0xFFD7263D)
val UserBlue = Color(0xFF1A73E8)

private val Light = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCB),
    onPrimaryContainer = Color(0xFF3A1300),
    secondary = Color(0xFF37474F),
    secondaryContainer = Color(0xFFFFE3D4),
    onSecondaryContainer = Color(0xFF3A1300),
    background = Color(0xFFF7F7F5),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDEDE9),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFF8A4C),
    onPrimary = Color(0xFF2B1000),
    primaryContainer = Color(0xFF7A2E00),
    onPrimaryContainer = Color(0xFFFFDBCB),
    secondary = Color(0xFFB0BEC5),
    secondaryContainer = Color(0xFF5A2A0C),
    onSecondaryContainer = Color(0xFFFFDBCB),
    background = Color(0xFF121212),
    surface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFF2A2A2D),
)

@Composable
fun MetroArTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, content = content)
}
