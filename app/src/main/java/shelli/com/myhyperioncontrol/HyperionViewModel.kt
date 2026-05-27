package shelli.com.myhyperioncontrol

import android.app.Application
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
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

    /** Vorschaufarbe (volle Helligkeit – unabhängig vom Slider) */
    val previewColor: Color
        get() = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, 1f)))

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
        if (isOn) scheduleSendColor()   // Throttle: sofort + danach max. alle 80 ms
    }

    fun onBrightnessChanged(newBrightness: Float) {
        brightness = newBrightness
        if (isOn) scheduleSendColor()
    }

    fun turnOn() {
        isOn = true
        scheduleSendColor(immediate = true)
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
