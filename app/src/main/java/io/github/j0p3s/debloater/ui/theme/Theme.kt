package io.github.j0p3s.debloater.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.j0p3s.debloater.data.SafetyLevel

private val Light = lightColorScheme(
    primary = Color(0xFF006B58), onPrimary = Color.White,
    primaryContainer = Color(0xFFD3F3E7), onPrimaryContainer = Color(0xFF00382D),
    secondary = Color(0xFF52665F), secondaryContainer = Color(0xFFE4EDE8), onSecondaryContainer = Color(0xFF243D31),
    tertiary = Color(0xFF3E617B), background = Color(0xFFF3F6F5),
    surface = Color(0xFFFCFDFB), surfaceVariant = Color(0xFFE8EEEA),
    onSurface = Color(0xFF17241F), onSurfaceVariant = Color(0xFF4F6058),
    outline = Color(0xFF73847B), outlineVariant = Color(0xFFD4DED8),
    surfaceTint = Color(0xFF006B58), surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAF8), surfaceContainer = Color(0xFFF0F4F1),
    surfaceContainerHigh = Color(0xFFEAF0EC), surfaceContainerHighest = Color(0xFFE3EBE5),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF7BDBC0), onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF164E40), onPrimaryContainer = Color(0xFFB9F3DD),
    secondary = Color(0xFFB1CCC0), secondaryContainer = Color(0xFF263D33), onSecondaryContainer = Color(0xFFD1E8DA),
    tertiary = Color(0xFFA6CCE8), background = Color(0xFF101915),
    surface = Color(0xFF16221C), surfaceVariant = Color(0xFF24352D),
    onSurface = Color(0xFFE0EAE3), onSurfaceVariant = Color(0xFFB5C8BC),
    outline = Color(0xFF829B8C), outlineVariant = Color(0xFF34493D),
    surfaceTint = Color(0xFF7BDBC0), surfaceContainerLowest = Color(0xFF0B130F),
    surfaceContainerLow = Color(0xFF16221C), surfaceContainer = Color(0xFF1B2922),
    surfaceContainerHigh = Color(0xFF213128), surfaceContainerHighest = Color(0xFF293C31),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(20.dp),
)

@Composable
fun DebloaterTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) Dark else Light, shapes = AppShapes, typography = Typography(), content = content)
}

/** Fixed, recognisable colours per safety level (independent of dynamic colour). */
fun levelColor(level: SafetyLevel, dark: Boolean): Color = when (level) {
    SafetyLevel.RECOMMENDED -> if (dark) Color(0xFF7FD69A) else Color(0xFF1B7F3B)
    SafetyLevel.SAFE -> if (dark) Color(0xFF8CC8FF) else Color(0xFF1565C0)
    SafetyLevel.EXTREME -> if (dark) Color(0xFFFFB74D) else Color(0xFFB45F06)
    SafetyLevel.EXPERT -> if (dark) Color(0xFFFF8A65) else Color(0xFFD84315)
    SafetyLevel.UNSAFE -> if (dark) Color(0xFFFF8A80) else Color(0xFFC62828)
    SafetyLevel.UNKNOWN -> if (dark) Color(0xFFBDBDBD) else Color(0xFF616161)
}

/** Status colours for text and tints; lighter in dark mode so they stay readable on dark surfaces. */
val OkColor: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF7FD69A) else Color(0xFF2E7D32)
val WarnColor: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFFB74D) else Color(0xFFB45F06)
val ErrorColor: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFF8A80) else Color(0xFFD32F2F)
/** Fixed badge background that always carries white content. */
val OkBadgeColor = Color(0xFF2E7D32)
