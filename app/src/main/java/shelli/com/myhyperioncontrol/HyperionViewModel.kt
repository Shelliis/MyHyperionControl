package shelli.com.myhyperioncontrol

import android.app.Application
import android.content.Context
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HyperionViewModel(application: Application) : AndroidViewModel(application) {

    val settings = SettingsStore(application)
    private val api = HyperionApi()
    private val nsdManager = application.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val mainHandler = Handler(Looper.getMainLooper())

    // ── Farbzustand ──────────────────────────────────────────────────────────
    // Startwerte aus persistierter letzter Farbe – Fallback: Weiß (sat=0)
    var hue        by mutableStateOf(settings.lastHue)
        private set
    var saturation by mutableStateOf(settings.lastSaturation)
        private set
    var brightness by mutableStateOf(settings.lastBrightness)
        private set
    var isOn by mutableStateOf(false)
        private set
    var statusMessage by mutableStateOf("")
        private set

    // ── Verbindungszustand ───────────────────────────────────────────────────
    /** true = Server antwortet auf TCP-Connect */
    var isServerReachable by mutableStateOf(false)
        private set

    /** true = mDNS-Suche läuft gerade */
    var isDiscovering by mutableStateOf(false)
        private set

    /** Letzter per mDNS gefundener Hostname – für die Einstellungsseite */
    var discoveredHost by mutableStateOf("")
        private set
    var discoveredPort by mutableStateOf(0)
        private set

    /** Name des zuletzt angewendeten Musters – null, wenn aktuell eine Einzelfarbe aktiv ist */
    var activePatternName by mutableStateOf<String?>(null)
        private set

    // ── Farb-Presets ─────────────────────────────────────────────────────────
    var presets by mutableStateOf(Array(5) { settings.getPreset(it) })
        private set

    fun savePreset(index: Int) {
        val updated = presets.copyOf()
        updated[index] = ColorPreset(hue, saturation, brightness)
        settings.savePreset(index, updated[index]!!)
        presets = updated
        statusMessage = "Preset ${index + 1} gespeichert"
    }

    fun deletePreset(index: Int) {
        val updated = presets.copyOf()
        updated[index] = null
        settings.deletePreset(index)
        presets = updated
        statusMessage = "Preset ${index + 1} gelöscht"
    }

    fun applyPreset(index: Int) {
        val preset = presets[index] ?: return
        hue        = preset.hue
        saturation = preset.saturation
        brightness = preset.brightness
        isOn       = true
        activePatternName = null
        scheduleSendColor(immediate = true)
    }

    // ── LED-Muster ────────────────────────────────────────────────────────────
    private val patternStore = PatternStore(application)
    var patterns by mutableStateOf(patternStore.loadAll())
        private set

    fun capturePattern(name: String) {
        viewModelScope.launch {
            api.getLedPattern(settings.serverHost, settings.serverPort)
                .onSuccess { leds ->
                    if (leds.isEmpty()) { statusMessage = "Keine LED-Daten verfügbar"; return@onSuccess }
                    patternStore.save(LedPattern(name, leds))
                    patterns = patternStore.loadAll()
                    statusMessage = "Muster \"$name\" gespeichert (${leds.size} LEDs)"
                }
                .onFailure { statusMessage = "Fehler: ${it.message}" }
        }
    }

    fun applyPattern(pattern: LedPattern) {
        viewModelScope.launch {
            sendPattern(pattern)
                .onSuccess {
                    isOn = true
                    activePatternName = pattern.name
                    statusMessage = "Muster \"${pattern.name}\" aktiv ✓"
                }
                .onFailure { statusMessage = "Fehler: ${it.message}" }
        }
    }

    /** Sendet ein Muster, dessen LED-Farben mit der aktuellen Helligkeit skaliert sind. */
    private suspend fun sendPattern(pattern: LedPattern) =
        api.setLedPattern(settings.serverHost, settings.serverPort, settings.priority, scaleLeds(pattern.leds, brightness))

    /** Skaliert jeden RGB-Wert mit dem Helligkeitsfaktor (0.0–1.0), geclamped auf 0–255. */
    private fun scaleLeds(leds: List<IntArray>, factor: Float): List<IntArray> =
        leds.map { led ->
            intArrayOf(
                (led[0] * factor).toInt().coerceIn(0, 255),
                (led[1] * factor).toInt().coerceIn(0, 255),
                (led[2] * factor).toInt().coerceIn(0, 255)
            )
        }

    fun deletePattern(pattern: LedPattern) {
        patternStore.delete(pattern.name)
        patterns = patternStore.loadAll()
        if (activePatternName == pattern.name) activePatternName = null
        statusMessage = "Muster \"${pattern.name}\" gelöscht"
    }

    fun renamePattern(pattern: LedPattern, newName: String) {
        patternStore.delete(pattern.name)
        patternStore.save(LedPattern(newName, pattern.leds))
        patterns = patternStore.loadAll()
        if (activePatternName == pattern.name) activePatternName = newName
        statusMessage = "Muster umbenannt in \"$newName\""
    }

    // ── Sicherung (Export/Import) ───────────────────────────────────────────
    /** Erhöht sich nach jedem erfolgreichen Import – Signal für die UI, Formularfelder neu zu laden. */
    var importCounter by mutableIntStateOf(0)
        private set

    fun exportToUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val json = BackupManager.export(settings, presets, patterns)
                getApplication<Application>().contentResolver.openOutputStream(uri)
                    ?.use { it.write(json.toByteArray()) }
                    ?: error("Konnte Datei nicht öffnen")
            }
                .onSuccess { statusMessage = "Sicherung exportiert ✓" }
                .onFailure { statusMessage = "Export fehlgeschlagen: ${it.message}" }
        }
    }

    fun importFromUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val json = getApplication<Application>().contentResolver.openInputStream(uri)
                    ?.use { it.bufferedReader().readText() }
                    ?: error("Konnte Datei nicht öffnen")
                BackupManager.import(json).getOrThrow()
            }
                .onSuccess { applyImport(it) }
                .onFailure { statusMessage = "Import fehlgeschlagen: ${it.message}" }
        }
    }

    private fun applyImport(result: BackupManager.ImportResult) {
        result.serverHost?.let { settings.serverHost = it; settings.isConfigured = true }
        result.serverPort?.let { settings.serverPort = it }
        result.priority?.let { settings.priority = it }

        for (i in result.presets.indices) {
            val preset = result.presets[i]
            if (preset != null) settings.savePreset(i, preset) else settings.deletePreset(i)
        }
        presets = Array(5) { settings.getPreset(it) }

        patternStore.loadAll().forEach { patternStore.delete(it.name) }
        result.patterns.forEach { patternStore.save(it) }
        patterns = patternStore.loadAll()

        if (activePatternName != null && patterns.none { it.name == activePatternName }) {
            activePatternName = null
        }

        checkReachabilityNow()
        importCounter++
        statusMessage = "Sicherung importiert ✓"
    }

    // ── Zuletzt gesendete Werte (null = noch nie gesendet) ───────────────────
    var lastSentR by mutableStateOf<Int?>(null)
        private set
    var lastSentG by mutableStateOf<Int?>(null)
        private set
    var lastSentB by mutableStateOf<Int?>(null)
        private set
    var lastSentBrightnessPercent by mutableStateOf<Int?>(null)
        private set

    private var sendJob: Job? = null
    /** Zeitstempel des letzten tatsächlich abgesendeten Pakets (ms) */
    private var lastSendTimeMs = 0L

    /** Laufende Discovery-Listener-Referenz (für späteres Stoppen) */
    @Volatile
    private var activeDiscoveryListener: NsdManager.DiscoveryListener? = null

    // ── Initialisierung ───────────────────────────────────────────────────────
    init {
        if (!settings.isConfigured) {
            startNsdDiscovery()
        } else {
            // Aktuelle Serverfarbe beim Start laden (setzt das Farbrad)
            loadColorFromServer()
        }
        // Periodische Erreichbarkeitsprüfung (startet sofort, dann alle 15 s)
        startPeriodicReachabilityCheck()
    }

    // ── Farb-Aktionen ────────────────────────────────────────────────────────

    fun onColorChanged(newHue: Float, newSaturation: Float) {
        hue = newHue
        saturation = newSaturation
        activePatternName = null
        if (isOn) scheduleSendColor()   // Throttle: sofort + danach max. alle 80 ms
    }

    fun onBrightnessChanged(newBrightness: Float) {
        brightness = newBrightness
        if (!isOn) return

        val pattern = activePatternName?.let { name -> patterns.find { it.name == name } }
        if (pattern != null) {
            scheduleSendPattern(pattern)   // Helligkeit des aktiven Musters anpassen
        } else {
            scheduleSendColor()
        }
    }

    fun turnOn() {
        isOn = true
        // War beim letzten Ausschalten ein Muster aktiv, dieses wieder anwenden –
        // sonst die zuletzt gewählte Einzelfarbe senden.
        val pattern = activePatternName?.let { name -> patterns.find { it.name == name } }
        if (pattern != null) {
            applyPattern(pattern)
        } else {
            scheduleSendColor(immediate = true)
        }
    }

    fun turnOff() {
        sendJob?.cancel()
        viewModelScope.launch {
            val result = api.clear(settings.serverHost, settings.serverPort, settings.priority)
            result
                .onSuccess { isOn = false; statusMessage = "LEDs aus ✓" }
                .onFailure { isOn = false; statusMessage = "Fehler: ${it.message}" }
        }
    }

    /**
     * Throttle-Logik: sofort senden wenn ≥ 80 ms seit letztem Paket vergangen,
     * sonst auf das Ende des 80-ms-Fensters warten (trailing send).
     * Jeder neue Aufruf überschreibt den wartenden Job → immer aktuellster Wert.
     *
     * @param immediate true = sofort ohne Wartezeit (z. B. beim Einschalten)
     */
    private fun scheduleSendColor(immediate: Boolean = false) {
        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            if (!immediate) {
                val elapsed = System.currentTimeMillis() - lastSendTimeMs
                val wait    = (THROTTLE_MS - elapsed).coerceAtLeast(0L)
                if (wait > 0L) delay(wait)
            }
            lastSendTimeMs = System.currentTimeMillis()

            val hsv      = floatArrayOf(hue, saturation, brightness)
            val colorInt = android.graphics.Color.HSVToColor(hsv)
            val r        = android.graphics.Color.red(colorInt)
            val g        = android.graphics.Color.green(colorInt)
            val b        = android.graphics.Color.blue(colorInt)
            val result   = api.setColor(
                host     = settings.serverHost,
                port     = settings.serverPort,
                priority = settings.priority,
                r = r, g = g, b = b
            )
            result
                .onSuccess {
                    lastSentR                = r
                    lastSentG                = g
                    lastSentB                = b
                    lastSentBrightnessPercent = (brightness * 100f).toInt()
                    statusMessage            = "Farbe gesetzt ✓"
                    settings.lastHue        = hue
                    settings.lastSaturation = saturation
                    settings.lastBrightness = brightness
                }
                .onFailure { statusMessage = "Fehler: ${it.message}" }
        }
    }

    /**
     * Throttle-Logik analog zu [scheduleSendColor]: passt die Helligkeit des
     * aktuell aktiven Musters an und sendet es erneut (max. alle 80 ms).
     */
    private fun scheduleSendPattern(pattern: LedPattern) {
        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            val elapsed = System.currentTimeMillis() - lastSendTimeMs
            val wait    = (THROTTLE_MS - elapsed).coerceAtLeast(0L)
            if (wait > 0L) delay(wait)
            lastSendTimeMs = System.currentTimeMillis()

            sendPattern(pattern)
                .onSuccess {
                    lastSentBrightnessPercent = (brightness * 100f).toInt()
                    settings.lastBrightness   = brightness
                }
                .onFailure { statusMessage = "Fehler: ${it.message}" }
        }
    }

    companion object {
        private const val THROTTLE_MS = 80L   // max. ~12 Pakete/Sekunde
    }

    // ── mDNS-Erkennung ───────────────────────────────────────────────────────

    /**
     * Startet eine mDNS-Suche nach dem Service-Typ "_hyperion._tcp."
     * Erster gefundener Server wird sofort als Konfiguration übernommen.
     * Automatischer Abbruch nach 30 Sekunden.
     */
    fun startNsdDiscovery() {
        if (isDiscovering) return
        stopActiveDiscovery()   // vorherige Suche ggf. stoppen

        isDiscovering = true
        discoveredHost = ""
        statusMessage = "Suche Hyperion-Server im Netzwerk…"

        // Resolver: erhält die aufgelöste IP + Port
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                mainHandler.post {
                    isDiscovering = false
                    statusMessage = "Auflösung fehlgeschlagen (Code $errorCode)"
                }
            }
            override fun onServiceResolved(info: NsdServiceInfo) {
                val host = info.host?.hostAddress ?: run {
                    mainHandler.post { isDiscovering = false }
                    return
                }
                val port = info.port
                mainHandler.post {
                    // Konfiguration sofort übernehmen
                    discoveredHost         = host
                    discoveredPort         = port
                    settings.serverHost    = host
                    settings.serverPort    = port
                    settings.isConfigured  = true
                    isDiscovering          = false
                    statusMessage          = "✓ Server gefunden: $host:$port"
                    checkReachabilityNow()
                    loadColorFromServer()   // Farbrad nach Discovery befüllen
                }
            }
        }

        // Discovery-Listener: empfängt gefundene Services
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(type: String, errorCode: Int) {
                mainHandler.post {
                    isDiscovering = false
                    activeDiscoveryListener = null
                    statusMessage = "Suche konnte nicht gestartet werden (Code $errorCode)"
                }
            }
            override fun onStopDiscoveryFailed(type: String, errorCode: Int) {}
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {
                mainHandler.post { if (isDiscovering) isDiscovering = false }
            }
            override fun onServiceFound(info: NsdServiceInfo) {
                // Ersten Treffer nehmen, Discovery danach stoppen
                val self = this
                activeDiscoveryListener = null
                mainHandler.post {
                    try { nsdManager.stopServiceDiscovery(self) } catch (_: Exception) {}
                    nsdManager.resolveService(info, resolveListener)
                }
            }
            override fun onServiceLost(info: NsdServiceInfo) {}
        }

        activeDiscoveryListener = listener
        nsdManager.discoverServices("_hyperion._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)

        // Timeout: nach 30 s aufgeben
        viewModelScope.launch {
            delay(30_000L)
            if (isDiscovering) {
                stopActiveDiscovery()
                isDiscovering = false
                if (discoveredHost.isEmpty()) {
                    statusMessage = "Kein Hyperion-Server gefunden – " +
                        "bitte Einstellungen manuell prüfen."
                }
            }
        }
    }

    // ── Erreichbarkeit ───────────────────────────────────────────────────────

    /** Sofortige einmalige Prüfung (z. B. nach Speichern der Einstellungen). */
    fun checkReachabilityNow() {
        viewModelScope.launch {
            isServerReachable = api.isReachable(settings.serverHost, settings.serverPort)
        }
    }

    /** Startet eine Endlosschleife: sofort + alle 15 Sekunden prüfen.
     *  Erkennt Übergänge:
     *  - nicht erreichbar → erreichbar: Farbrad befüllen (wenn nicht aktiv)
     *  - erreichbar → nicht erreichbar: isOn zurücksetzen
     */
    private fun startPeriodicReachabilityCheck() {
        viewModelScope.launch {
            var wasReachable = false
            while (true) {
                val reachable = api.isReachable(settings.serverHost, settings.serverPort)
                if (reachable && !wasReachable && !isOn) {
                    // Server gerade wieder online → Farbrad synchronisieren
                    fetchColorFromServer()
                }
                if (!reachable && wasReachable && isOn) {
                    // Server gerade offline gegangen → Einschaltzustand zurücksetzen
                    isOn = false
                    statusMessage = "Server nicht erreichbar"
                }
                isServerReachable = reachable
                wasReachable = reachable
                delay(15_000L)
            }
        }
    }

    // ── Aktuellen Serverzustand laden ────────────────────────────────────────

    /**
     * Fragt Hyperion nach dem aktuellen Farbzustand und setzt Farbrad + Helligkeit.
     * Setzt isOn NICHT – das bleibt Aufgabe des Aufrufers.
     *
     * @return true wenn unsere eigene Priorität aktiv war, false sonst oder bei Fehler.
     */
    private suspend fun fetchColorFromServer(): Boolean {
        val result = api.getServerInfo(settings.serverHost, settings.serverPort)
        var ourPriorityActive = false
        result.onSuccess { json ->
            runCatching {
                if (!json.optBoolean("success", true)) return@onSuccess

                val priorities = json
                    .optJSONObject("info")
                    ?.optJSONArray("priorities")
                    ?: return@onSuccess

                var bestR = -1; var bestG = -1; var bestB = -1

                for (i in 0 until priorities.length()) {
                    val p = priorities.getJSONObject(i)
                    if (!p.optBoolean("active", false)) continue

                    // Nur Einträge mit RGB-Farbwert auswerten
                    val rgb = p.optJSONObject("value")
                               ?.optJSONArray("RGB")
                               ?: continue

                    val r = rgb.getInt(0)
                    val g = rgb.getInt(1)
                    val b = rgb.getInt(2)

                    if (p.optInt("priority", -1) == settings.priority) {
                        // Unsere eigene Priorität – höchste Relevanz
                        bestR = r; bestG = g; bestB = b
                        ourPriorityActive = true
                        break
                    }
                    if (p.optBoolean("visible", false) && bestR < 0) {
                        // Sichtbare Priorität als Fallback (nur erster Treffer)
                        bestR = r; bestG = g; bestB = b
                    }
                }

                if (bestR < 0) return@onSuccess   // Keine passende Farbe gefunden

                // RGB → HSV
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(
                    android.graphics.Color.rgb(bestR, bestG, bestB), hsv
                )
                hue        = hsv[0]
                saturation = hsv[1]
                // Bei reinem Schwarz (V = 0) Helligkeit auf Minimum setzen
                brightness = if (hsv[2] > 0f) hsv[2] else 0.01f
            }
            // runCatching schluckt JSON-Parsing-Fehler ohne Absturz
        }
        return ourPriorityActive
    }

    /**
     * Öffentliche Variante: lädt Farbzustand vom Server und setzt isOn,
     * wenn unsere eigene Priorität aktiv war.
     * Wird beim App-Start und nach mDNS-Discovery aufgerufen.
     */
    fun loadColorFromServer() {
        viewModelScope.launch {
            val ourPriorityActive = fetchColorFromServer()
            if (ourPriorityActive) isOn = true
        }
    }

    // ── Aufräumen ────────────────────────────────────────────────────────────

    private fun stopActiveDiscovery() {
        val listener = activeDiscoveryListener ?: return
        activeDiscoveryListener = null
        try { nsdManager.stopServiceDiscovery(listener) } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        stopActiveDiscovery()
    }
}
