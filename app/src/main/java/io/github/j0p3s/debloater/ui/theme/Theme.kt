package io.github.j0p3s.debloater.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.j0p3s.debloater.data.SafetyLevel

private val Light = lightColorScheme(primary = Color(0xFF2E6B4F), secondary = Color(0xFF4D6357), tertiary = Color(0xFF3D6373))
private val Dark = darkColorScheme(primary = Color(0xFF95D5B2), secondary = Color(0xFFB4CCBC), tertiary = Color(0xFFA5CCDD))

@Composable
fun DebloaterTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
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

val OkColor = Color(0xFF2E7D32)
val WarnColor = Color(0xFFEF8F00)
val ErrorColor = Color(0xFFD32F2F)
