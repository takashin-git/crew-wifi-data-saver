package app.crewdatasaver.ui.theme

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

private val Navy = Color(0xFF0B3A5B)
private val Teal = Color(0xFF00897B)

private val LightColors = lightColorScheme(
    primary = Navy,
    secondary = Teal,
    tertiary = Color(0xFFE65100),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CCAF0),
    secondary = Color(0xFF80CBC4),
    tertiary = Color(0xFFFFB74D),
)

@Composable
fun CrewDataSaverTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
