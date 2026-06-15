package shelli.com.myhyperioncontrol

import org.json.JSONArray
import org.json.JSONObject

/**
 * Serialisiert/deserialisiert Server-Einstellungen, Farb-Presets und LED-Muster
 * als JSON-Sicherungsdatei.
 */
object BackupManager {

    private const val VERSION = 1

    data class ImportResult(
        val serverHost: String?,
        val serverPort: Int?,
        val priority: Int?,
        val presets: Array<ColorPreset?>,
        val patterns: List<LedPattern>
    )

    fun export(settings: SettingsStore, presets: Array<ColorPreset?>, patterns: List<LedPattern>): String {
        val root = JSONObject().apply {
            put("version", VERSION)
            put("settings", JSONObject().apply {
                put("serverHost", settings.serverHost)
                put("serverPort", settings.serverPort)
                put("priority", settings.priority)
            })
            put("presets", JSONArray().apply {
                for (preset in presets) {
                    if (preset == null) {
                        put(JSONObject.NULL)
                    } else {
                        put(JSONObject().apply {
                            put("hue", preset.hue)
                            put("saturation", preset.saturation)
                            put("brightness", preset.brightness)
                        })
                    }
                }
            })
            put("patterns", JSONArray().apply {
                for (pattern in patterns) {
                    put(JSONObject().apply {
                        put("name", pattern.name)
                        put("leds", JSONArray().apply {
                            for (led in pattern.leds) {
                                put(JSONArray().apply { put(led[0]); put(led[1]); put(led[2]) })
                            }
                        })
                    })
                }
            })
        }
        return root.toString(2)
    }

    fun import(json: String): Result<ImportResult> = runCatching {
        val root = JSONObject(json)

        val settingsObj = root.optJSONObject("settings")
        val serverHost  = settingsObj?.optString("serverHost")?.takeIf { it.isNotBlank() }
        val serverPort  = settingsObj?.takeIf { it.has("serverPort") }?.optInt("serverPort")
        val priority    = settingsObj?.takeIf { it.has("priority") }?.optInt("priority")

        val presetsArr = root.optJSONArray("presets") ?: JSONArray()
        val presets = Array(5) { i ->
            val item = if (i < presetsArr.length()) presetsArr.opt(i) else null
            if (item == null || item == JSONObject.NULL) null
            else (item as JSONObject).let {
                ColorPreset(
                    hue        = it.getDouble("hue").toFloat(),
                    saturation = it.getDouble("saturation").toFloat(),
                    brightness = it.getDouble("brightness").toFloat()
                )
            }
        }

        val patternsArr = root.optJSONArray("patterns") ?: JSONArray()
        val patterns = (0 until patternsArr.length()).map { i ->
            val obj     = patternsArr.getJSONObject(i)
            val ledsArr = obj.getJSONArray("leds")
            LedPattern(
                name = obj.getString("name"),
                leds = (0 until ledsArr.length()).map { j ->
                    val rgb = ledsArr.getJSONArray(j)
                    intArrayOf(rgb.getInt(0), rgb.getInt(1), rgb.getInt(2))
                }
            )
        }

        ImportResult(serverHost, serverPort, priority, presets, patterns)
    }
}
