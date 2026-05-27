package shelli.com.myhyperioncontrol

import android.content.Context

/**
 * Speichert Server-Verbindungsparameter dauerhaft in SharedPreferences.
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("hyperion_prefs", Context.MODE_PRIVATE)

    /** IP-Adresse oder Hostname des Hyperion-Servers */
    var serverHost: String
        get() = prefs.getString("host", "192.168.1.100") ?: "192.168.1.100"
        set(value) { prefs.edit().putString("host", value).apply() }

    /** Port des Hyperion Flat-JSON-TCP-Servers (Standard: 19333) */
    var serverPort: Int
        get() = prefs.getInt("port", 19333)
        set(value) { prefs.edit().putInt("port", value).apply() }

    /** JSON-RPC Priorität (1–253, niedrigere Werte = höhere Priorität) */
    var priority: Int
        get() = prefs.getInt("priority", 50)
        set(value) { prefs.edit().putInt("priority", value).apply() }

    /**
     * true, sobald der Benutzer (oder die Auto-Discovery) die Verbindung
     * mindestens einmal erfolgreich konfiguriert hat.
     * Solange false, wird beim App-Start automatisch nach einem Server gesucht.
     */
    var isConfigured: Boolean
        get() = prefs.getBoolean("configured", false)
        set(value) { prefs.edit().putBoolean("configured", value).apply() }

    // ── Zuletzt verwendete Farbe ─────────────────────────────────────────────
    // Gespeichert als Float-Bits, da SharedPreferences kein Float-Array kennt.

    /** Farbton (0°–360°) der zuletzt gesendeten Farbe */
    var lastHue: Float
        get() = Float.fromBits(prefs.getInt("last_hue", 0f.toBits()))
        set(value) { prefs.edit().putInt("last_hue", value.toBits()).apply() }

    /** Sättigung (0.0–1.0) der zuletzt gesendeten Farbe */
    var lastSaturation: Float
        get() = Float.fromBits(prefs.getInt("last_sat", 0f.toBits()))   // 0 = Weiß
        set(value) { prefs.edit().putInt("last_sat", value.toBits()).apply() }

    /** Helligkeit (0.0–1.0) der zuletzt gesendeten Farbe */
    var lastBrightness: Float
        get() = Float.fromBits(prefs.getInt("last_bri", 1f.toBits()))   // 1 = voll
        set(value) { prefs.edit().putInt("last_bri", value.toBits()).apply() }
}
