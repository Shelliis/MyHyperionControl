package shelli.com.myhyperioncontrol.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Festes schwarzes Dark-Theme – kein Dynamic Color, kein Light-Mode.
 * Passt zur Nutzung als LED-Fernbedienung (dunkles Display spart Akku auf OLED).
 */
private val HyperionColorScheme = darkColorScheme(
    primary             = Amber,          // Akzent (Buttons, Fokus-Ringe)
    onPrimary           = AmberDark,      // Text auf Amber-Flächen
    primaryContainer    = AmberDark,
    onPrimaryContainer  = Amber,

    background          = Black,          // ← Reines Schwarz
    onBackground        = White,

    surface             = Black,          // Scaffold, TopAppBar, Dialoge
    onSurface           = White,

    surfaceVariant      = DarkGray,       // Info-Panel, Eingabefelder
    onSurfaceVariant    = LightGray,

    outline             = BorderGray,     // Rahmen, Divider

    error               = ErrorRed,
    onError             = White,
)

@Composable
fun MyHyperionControlTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HyperionColorScheme,
        typography  = Typography,
        content     = content
    )
}
