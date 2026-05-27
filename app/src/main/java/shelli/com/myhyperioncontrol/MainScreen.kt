package shelli.com.myhyperioncontrol

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Hauptbildschirm der App.
 *
 * Layout (von oben nach unten):
 *   TopAppBar  [Hyperion Control]  [⚙]
 *   ── Statuszeile (Discovery-Spinner / Server-Indikator) ──
 *   Farbrad
 *   Farbvorschau-Kreis
 *   Helligkeits-Slider
 *   [  EIN  ]  [  AUS  ]   ← nur aktiv wenn Server erreichbar
 *   Statusmeldung
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: HyperionViewModel,
    onNavigateToSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hyperion Control", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Statuszeile ───────────────────────────────────────────────
            ServerStatusBar(viewModel)

            Spacer(Modifier.height(8.dp))

            // ── Farbrad ──────────────────────────────────────────────────
            ColorWheel(
                hue             = viewModel.hue,
                saturation      = viewModel.saturation,
                modifier        = Modifier.fillMaxWidth(0.9f),
                onColorSelected = { h, s -> viewModel.onColorChanged(h, s) }
            )

            Spacer(Modifier.height(20.dp))

            // ── Farbvorschau ─────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(viewModel.previewColor)
                    .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )

            Spacer(Modifier.height(24.dp))

            // ── Helligkeit ───────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text     = "Helligkeit",
                    style    = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(88.dp)
                )
                Slider(
                    value         = viewModel.brightness,
                    onValueChange = { viewModel.onBrightnessChanged(it) },
                    valueRange    = 0.01f..1f,
                    modifier      = Modifier.weight(1f)
                )
                Text(
                    text      = "${(viewModel.brightness * 100).toInt()} %",
                    style     = MaterialTheme.typography.bodySmall,
                    modifier  = Modifier.width(44.dp),
                    textAlign = TextAlign.End
                )
            }

            Spacer(Modifier.height(28.dp))

            // ── Ein / Aus ─────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick  = { viewModel.turnOn() },
                    enabled  = viewModel.isServerReachable,   // ← nur wenn Server da
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor         = Color(0xFF43A047),
                        disabledContainerColor = Color(0xFF43A047).copy(alpha = 0.35f)
                    )
                ) {
                    Text("Ein", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick  = { viewModel.turnOff() },
                    enabled  = viewModel.isServerReachable,   // ← nur wenn Server da
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor         = Color(0xFFE53935),
                        disabledContainerColor = Color(0xFFE53935).copy(alpha = 0.35f)
                    )
                ) {
                    Text("Aus", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Farbinfo + Statusmeldung ──────────────────────────────────
            ColorInfoPanel(viewModel)

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Hilfs-Composable: Statuszeile ────────────────────────────────────────────

@Composable
private fun ServerStatusBar(viewModel: HyperionViewModel) {
    when {
        // mDNS-Suche läuft
        viewModel.isDiscovering -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(14.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Suche Hyperion-Server…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Server erreichbar
        viewModel.isServerReachable -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF43A047))   // Grün
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "${viewModel.settings.serverHost}:${viewModel.settings.serverPort} – erreichbar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Server NICHT erreichbar
        else -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935))   // Rot
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Server nicht erreichbar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ── Hilfs-Composable: Farbinfo-Panel ─────────────────────────────────────────

@Composable
private fun ColorInfoPanel(viewModel: HyperionViewModel) {
    // RGB aus aktuellem HSV (inklusive Helligkeit) berechnen –
    // identisch mit dem, was an den Server geschickt wird.
    val colorInt = android.graphics.Color.HSVToColor(
        floatArrayOf(viewModel.hue, viewModel.saturation, viewModel.brightness)
    )
    val r   = android.graphics.Color.red(colorInt)
    val g   = android.graphics.Color.green(colorInt)
    val b   = android.graphics.Color.blue(colorInt)
    val hex = "#%02X%02X%02X".format(r, g, b)
    val brightnessPercent = (viewModel.brightness * 100f).toInt()

    Surface(
        shape  = MaterialTheme.shapes.small,
        color  = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Farbchip + Hex-Code
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(Color(colorInt))
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text  = hex,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text  = "Helligkeit  $brightnessPercent %",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // RGB-Einzelwerte
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                RgbChip("R", r, Color(0xFFEF5350))
                RgbChip("G", g, Color(0xFF66BB6A))
                RgbChip("B", b, Color(0xFF42A5F5))
            }

            // Letzte Statusmeldung
            if (viewModel.statusMessage.isNotEmpty()) {
                Text(
                    text  = viewModel.statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RgbChip(label: String, value: Int, labelColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text  = value.toString(),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
