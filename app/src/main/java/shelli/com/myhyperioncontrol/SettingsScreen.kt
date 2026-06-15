package shelli.com.myhyperioncontrol

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Einstellungsbildschirm.
 *
 * Felder:
 *   - Server-Adresse (IP oder Hostname)
 *   - Port (Standard: 8090)
 *   - Priorität (1–253, Standard: 50)
 *
 * Automatische Suche:
 *   - „Hyperion suchen"-Button startet mDNS-Discovery im ViewModel
 *   - Gefundene Werte werden sofort in die Eingabefelder übernommen
 *
 * Änderungen werden erst beim Tippen auf „Speichern" dauerhaft übernommen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: HyperionViewModel,
    onNavigateBack: () -> Unit
) {
    // Lokale Formular-Zustände
    var host     by remember { mutableStateOf(viewModel.settings.serverHost) }
    var port     by remember { mutableStateOf(viewModel.settings.serverPort.toString()) }
    var priority by remember { mutableStateOf(viewModel.settings.priority.toString()) }

    // URI einer ausgewählten Sicherungsdatei, bis der Import bestätigt wurde
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    // Wenn mDNS-Discovery einen Server findet, Felder automatisch aktualisieren
    LaunchedEffect(viewModel.discoveredHost) {
        if (viewModel.discoveredHost.isNotEmpty()) {
            host = viewModel.discoveredHost
            port = viewModel.discoveredPort.toString()
        }
    }

    // Nach einem Import die Formularfelder mit den übernommenen Werten aktualisieren
    LaunchedEffect(viewModel.importCounter) {
        if (viewModel.importCounter > 0) {
            host     = viewModel.settings.serverHost
            port     = viewModel.settings.serverPort.toString()
            priority = viewModel.settings.priority.toString()
        }
    }

    // Validierung
    val portValid     = port.toIntOrNull()?.let { it in 1..65535 } ?: false
    val priorityValid = priority.toIntOrNull()?.let { it in 1..253  } ?: false
    val canSave       = host.isNotBlank() && portValid && priorityValid

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Automatische Serversuche ──────────────────────────────────
            OutlinedButton(
                onClick  = { viewModel.startNsdDiscovery() },
                enabled  = !viewModel.isDiscovering,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (viewModel.isDiscovering) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Suche läuft…")
                } else {
                    Icon(
                        imageVector        = Icons.Default.Search,
                        contentDescription = null,
                        modifier           = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Hyperion im Netzwerk suchen (mDNS)")
                }
            }

            // Hinweis zum Suchergebnis
            if (viewModel.discoveredHost.isNotEmpty()) {
                Text(
                    "Gefunden: ${viewModel.discoveredHost}:${viewModel.discoveredPort}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                )
            }

            HorizontalDivider()

            // ── Server-Adresse ────────────────────────────────────────────
            OutlinedTextField(
                value           = host,
                onValueChange   = { host = it },
                label           = { Text("Server-Adresse (IP / Hostname)") },
                singleLine      = true,
                modifier        = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )

            // ── Port ──────────────────────────────────────────────────────
            OutlinedTextField(
                value           = port,
                onValueChange   = { port = it },
                label           = { Text("Port – Flat-JSON-TCP (Standard: 19333)") },
                singleLine      = true,
                isError         = port.isNotEmpty() && !portValid,
                supportingText  = if (port.isNotEmpty() && !portValid)
                                      ({ Text("Gültiger Bereich: 1–65535") }) else null,
                modifier        = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            // ── Priorität ─────────────────────────────────────────────────
            OutlinedTextField(
                value           = priority,
                onValueChange   = { priority = it },
                label           = { Text("Priorität (1–253)") },
                singleLine      = true,
                isError         = priority.isNotEmpty() && !priorityValid,
                supportingText  = if (priority.isNotEmpty() && !priorityValid)
                                      ({ Text("Gültiger Bereich: 1–253") }) else null,
                modifier        = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Text(
                text  = "Niedrigere Priorität = höhere Dringlichkeit bei mehreren Hyperion-Quellen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            // ── Sicherung (Export/Import) ────────────────────────────────
            Text(
                text       = "Sicherung",
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            val exportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json")
            ) { uri -> uri?.let { viewModel.exportToUri(it) } }

            val importLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri -> uri?.let { pendingImportUri = it } }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick  = { exportLauncher.launch("HyperionControl_Backup.json") },
                    modifier = Modifier.weight(1f)
                ) { Text("Exportieren") }

                OutlinedButton(
                    onClick  = { importLauncher.launch(arrayOf("application/json")) },
                    modifier = Modifier.weight(1f)
                ) { Text("Importieren") }
            }

            Text(
                text  = "Sichert Server-Einstellungen, Farb-Presets und Muster in eine Datei.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            // ── Speichern ─────────────────────────────────────────────────
            Button(
                onClick  = {
                    viewModel.settings.serverHost  = host.trim()
                    viewModel.settings.serverPort  = port.toIntOrNull() ?: 8090
                    viewModel.settings.priority    = priority.toIntOrNull() ?: 50
                    viewModel.settings.isConfigured = true
                    viewModel.checkReachabilityNow()
                    onNavigateBack()
                },
                enabled  = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("Speichern")
            }
        }
    }

    // ── Import-Bestätigung ───────────────────────────────────────────────
    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("Sicherung importieren?") },
            text  = {
                Text("Server-Einstellungen, Farb-Presets und Muster werden durch den Inhalt der Datei ersetzt.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.importFromUri(uri)
                    pendingImportUri = null
                }) { Text("Importieren") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) { Text("Abbrechen") }
            }
        )
    }
}
