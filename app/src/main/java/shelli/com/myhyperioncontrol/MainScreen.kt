package shelli.com.myhyperioncontrol

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
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

            Spacer(Modifier.height(16.dp))

            // ── Farb-Presets ──────────────────────────────────────────────
            PresetButtonsRow(
                presets           = viewModel.presets,
                currentHue        = viewModel.hue,
                currentSaturation = viewModel.saturation,
                currentBrightness = viewModel.brightness,
                isPatternActive   = viewModel.activePatternName != null,
                onShortPress      = { viewModel.applyPreset(it) },
                onSave            = { viewModel.savePreset(it) },
                onDelete          = { viewModel.deletePreset(it) }
            )

            Spacer(Modifier.height(12.dp))

            // ── Muster ───────────────────────────────────────────────────
            PatternSection(
                patterns          = viewModel.patterns,
                isServerReachable = viewModel.isServerReachable,
                activePatternName = viewModel.activePatternName,
                onCapture         = { viewModel.capturePattern(it) },
                onApply           = { viewModel.applyPattern(it) },
                onDelete          = { viewModel.deletePattern(it) },
                onRename          = { pattern, newName -> viewModel.renamePattern(pattern, newName) }
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
    val rgb = "R $r  G $g  B $b"
    val brightnessPercent = (viewModel.brightness * 100f).toInt()

    var showRgb by remember { mutableStateOf(false) }

    Surface(
        shape  = MaterialTheme.shapes.small,
        color  = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Farbchip + Hex-Code / RGB-Werte (Klick wechselt die Anzeige)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { showRgb = !showRgb }
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(Color(colorInt))
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text  = if (showRgb) rgb else hex,
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

// ── Preset-Buttons ────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetButtonsRow(
    presets: Array<ColorPreset?>,
    currentHue: Float,
    currentSaturation: Float,
    currentBrightness: Float,
    isPatternActive: Boolean,
    onShortPress: (Int) -> Unit,
    onSave: (Int) -> Unit,
    onDelete: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        for (index in 0 until 5) {
            val preset = presets[index]
            val bgColor = if (preset != null) {
                Color(android.graphics.Color.HSVToColor(
                    floatArrayOf(preset.hue, preset.saturation, preset.brightness)
                ))
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
            // Helligkeit der Hintergrundfarbe bestimmt die Textfarbe
            val luminance = 0.2126f * bgColor.red + 0.7152f * bgColor.green + 0.0722f * bgColor.blue
            val textColor = if (luminance > 0.45f) Color.Black else Color.White

            // Halo, wenn dieses Preset der aktuell aktiven Farbe entspricht
            val isSelected = !isPatternActive && preset != null &&
                preset.hue == currentHue &&
                preset.saturation == currentSaturation &&
                preset.brightness == currentBrightness

            var showMenu by remember { mutableStateOf(false) }

            Box {
                Box(
                    modifier = Modifier
                        .shadow(
                            elevation   = if (isSelected) 8.dp else 0.dp,
                            shape       = CircleShape,
                            ambientColor = Color.White,
                            spotColor    = Color.White
                        )
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(bgColor)
                        .border(
                            width = if (isSelected) 3.dp
                                    else if (preset != null) 2.dp else 1.dp,
                            color = if (isSelected) Color.White
                                    else if (preset != null) MaterialTheme.colorScheme.outline
                                    else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        )
                        .combinedClickable(
                            onClick = { onShortPress(index) },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showMenu = true
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text       = if (preset != null) "${index + 1}" else "+",
                        color      = if (preset != null) textColor
                                     else MaterialTheme.colorScheme.onSurfaceVariant,
                        style      = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                DropdownMenu(
                    expanded         = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text    = { Text("Speichern") },
                        onClick = { showMenu = false; onSave(index) }
                    )
                    DropdownMenuItem(
                        text    = { Text("Löschen", color = MaterialTheme.colorScheme.error) },
                        enabled = preset != null,
                        onClick = { showMenu = false; onDelete(index) }
                    )
                }
            }
        }
    }
}

// ── Muster-Abschnitt ─────────────────────────────────────────────────────────

@Composable
private fun PatternSection(
    patterns: List<LedPattern>,
    isServerReachable: Boolean,
    activePatternName: String?,
    onCapture: (String) -> Unit,
    onApply: (LedPattern) -> Unit,
    onDelete: (LedPattern) -> Unit,
    onRename: (LedPattern, String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var nameInput  by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text       = "Muster",
            style      = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.padding(bottom = 8.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding        = PaddingValues(end = 4.dp)
        ) {
            items(patterns) { pattern ->
                PatternCard(
                    pattern  = pattern,
                    enabled  = isServerReachable,
                    isActive = pattern.name == activePatternName,
                    onApply  = { onApply(pattern) },
                    onDelete = { onDelete(pattern) },
                    onRename = { newName -> onRename(pattern, newName) }
                )
            }
            item {
                OutlinedButton(
                    onClick  = { nameInput = ""; showDialog = true },
                    enabled  = isServerReachable,
                    modifier = Modifier.height(52.dp)
                ) {
                    Text("+ Muster", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Muster benennen") },
            text  = {
                OutlinedTextField(
                    value         = nameInput,
                    onValueChange = { nameInput = it },
                    label         = { Text("Name") },
                    singleLine    = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick  = { onCapture(nameInput.trim()); showDialog = false },
                    enabled  = nameInput.isNotBlank()
                ) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Abbrechen") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PatternCard(
    pattern: LedPattern,
    enabled: Boolean,
    isActive: Boolean,
    onApply: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit
) {
    var showMenu        by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput     by remember { mutableStateOf(pattern.name) }
    val haptic = LocalHapticFeedback.current

    Box {
        ElevatedCard(
            modifier = Modifier
                .width(110.dp)
                .shadow(
                    elevation    = if (isActive) 8.dp else 0.dp,
                    shape        = MaterialTheme.shapes.medium,
                    ambientColor = Color.White,
                    spotColor    = Color.White
                )
                .let {
                    if (isActive) it.border(2.dp, Color.White, MaterialTheme.shapes.medium) else it
                }
                .combinedClickable(
                    enabled     = enabled,
                    onClick     = onApply,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showMenu = true
                    }
                )
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 10.dp)
                    .alpha(if (enabled) 1f else 0.4f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text       = pattern.name,
                    style      = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis,
                    textAlign  = TextAlign.Center,
                    modifier   = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 32.dp)
                )
            }
        }

        DropdownMenu(
            expanded         = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text    = { Text("Umbenennen") },
                onClick = {
                    showMenu   = false
                    renameInput = pattern.name
                    showRenameDialog = true
                }
            )
            DropdownMenuItem(
                text    = { Text("Löschen", color = MaterialTheme.colorScheme.error) },
                onClick = { showMenu = false; onDelete() }
            )
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Umbenennen") },
            text  = {
                OutlinedTextField(
                    value         = renameInput,
                    onValueChange = { renameInput = it },
                    label         = { Text("Name") },
                    singleLine    = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick  = { onRename(renameInput.trim()); showRenameDialog = false },
                    enabled  = renameInput.isNotBlank()
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Abbrechen") }
            }
        )
    }
}
