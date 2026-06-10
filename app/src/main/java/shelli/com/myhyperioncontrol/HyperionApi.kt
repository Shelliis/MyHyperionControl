package shelli.com.myhyperioncontrol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Kommuniziert mit dem Hyperion-Server über das Flat-JSON-TCP-Protokoll.
 *
 * Protokoll: Roher TCP-Socket, ein JSON-Objekt pro Zeile (newline-terminiert).
 *   Client → Server:  {"command":"color","priority":50,"color":[255,0,0]}\n
 *   Server → Client:  {"command":"color","success":true,"tan":0}\n
 *
 * Standard-Port des Flat-JSON-Servers: 19333 (konfigurierbar).
 * (Nicht zu verwechseln mit dem HTTP/WebSocket-Server auf Port 8090.)
 */
class HyperionApi {

    /**
     * Öffnet eine TCP-Verbindung, sendet ein JSON-Kommando und liest die Antwort.
     * Verbindung wird nach jedem Kommando wieder geschlossen.
     */
    private suspend fun sendCommand(
        host: String,
        port: Int,
        payload: JSONObject
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 5_000)
                socket.soTimeout = 5_000

                // JSON-Zeile senden (Hyperion erwartet \n als Trennzeichen)
                val writer = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)
                writer.write(payload.toString())
                writer.newLine()
                writer.flush()

                // Antwort lesen (eine JSON-Zeile)
                val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                val line   = reader.readLine()
                    ?: throw IOException("Keine Antwort vom Server")
                JSONObject(line)
            }
        }
    }

    /**
     * Fragt den vollständigen Serverstatus ab (aktive Prioritäten, Farben, …).
     * JSON-RPC-Kommando: "serverinfo"
     */
    suspend fun getServerInfo(host: String, port: Int): Result<JSONObject> {
        val payload = JSONObject().apply { put("command", "serverinfo") }
        return sendCommand(host, port, payload)
    }

    /**
     * Liest die aktuellen LED-Farben über den LED-Stream.
     * Sendet ledstream-start, liest ACK + erstes Update, schließt die Verbindung.
     */
    suspend fun getLedPattern(host: String, port: Int): Result<List<IntArray>> =
        withContext(Dispatchers.IO) {
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 5_000)
                    socket.soTimeout = 5_000

                    val writer = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)
                    val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)

                    writer.write(JSONObject().apply {
                        put("command",    "ledcolors")
                        put("subcommand", "ledstream-start")
                    }.toString())
                    writer.newLine()
                    writer.flush()

                    // Zeile 1: ACK
                    val ackLine = reader.readLine() ?: throw IOException("Keine Antwort vom Server")
                    val ack = JSONObject(ackLine)
                    if (!ack.optBoolean("success", true)) {
                        throw IOException("ledstream-start abgelehnt: $ackLine")
                    }

                    // Zeile 2: erstes LED-Update
                    val updateLine = reader.readLine()
                        ?: throw IOException("Kein LED-Update empfangen")
                    parseLedStreamUpdate(JSONObject(updateLine))
                }
            }
        }

    private fun parseLedStreamUpdate(json: JSONObject): List<IntArray> {
        // Format: {"command":"ledcolors-ledstream-update","data":{"leds":[r,g,b,r,g,b,...]}}
        val flat = json.optJSONObject("data")?.optJSONArray("leds") ?: return emptyList()
        val count = flat.length() / 3
        return (0 until count).map { i ->
            intArrayOf(flat.getInt(i * 3), flat.getInt(i * 3 + 1), flat.getInt(i * 3 + 2))
        }
    }

    /** Sendet ein individuelles Farbmuster (ein RGB-Wert pro LED). */
    suspend fun setLedPattern(
        host: String,
        port: Int,
        priority: Int,
        leds: List<IntArray>
    ): Result<JSONObject> {
        val colorArray = JSONArray()
        for (led in leds) { colorArray.put(led[0]); colorArray.put(led[1]); colorArray.put(led[2]) }
        val payload = JSONObject().apply {
            put("command",  "color")
            put("priority", priority)
            put("color",    colorArray)
            put("origin",   "MyHyperionControl")
        }
        return sendCommand(host, port, payload)
    }

    /**
     * Setzt eine Volltonfarbe an alle LEDs der angegebenen Priorität.
     * JSON-RPC-Kommando: "color"
     */
    suspend fun setColor(
        host: String,
        port: Int,
        priority: Int,
        r: Int,
        g: Int,
        b: Int
    ): Result<JSONObject> {
        val payload = JSONObject().apply {
            put("command",  "color")
            put("priority", priority)
            put("color",    JSONArray().apply { put(r); put(g); put(b) })
            put("origin",   "MyHyperionControl")
        }
        return sendCommand(host, port, payload)
    }

    /**
     * Löscht die angegebene Priorität (LEDs aus, wenn keine andere Priorität aktiv).
     * JSON-RPC-Kommando: "clear"
     */
    suspend fun clear(
        host: String,
        port: Int,
        priority: Int
    ): Result<JSONObject> {
        val payload = JSONObject().apply {
            put("command",  "clear")
            put("priority", priority)
        }
        return sendCommand(host, port, payload)
    }

    /**
     * Prüft, ob ein TCP-Verbindungsaufbau gelingt (2-Sekunden-Timeout).
     * Sendet keinerlei Daten – reines Erreichbarkeits-Ping.
     */
    suspend fun isReachable(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { it.connect(InetSocketAddress(host, port), 2_000) }
            true
        }.getOrDefault(false)
    }
}
