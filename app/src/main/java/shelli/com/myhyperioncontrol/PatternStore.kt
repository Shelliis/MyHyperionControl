package shelli.com.myhyperioncontrol

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class LedPattern(val name: String, val leds: List<IntArray>)

class PatternStore(context: Context) {

    private val file = File(context.filesDir, "led_patterns.json")

    fun loadAll(): List<LedPattern> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val obj     = arr.getJSONObject(i)
                val ledsArr = obj.getJSONArray("leds")
                LedPattern(
                    name = obj.getString("name"),
                    leds = (0 until ledsArr.length()).map { j ->
                        val rgb = ledsArr.getJSONArray(j)
                        intArrayOf(rgb.getInt(0), rgb.getInt(1), rgb.getInt(2))
                    }
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(pattern: LedPattern) {
        val all = loadAll().filter { it.name != pattern.name }.toMutableList()
        all.add(pattern)
        persist(all)
    }

    fun delete(name: String) = persist(loadAll().filter { it.name != name })

    private fun persist(patterns: List<LedPattern>) {
        val arr = JSONArray()
        for (p in patterns) {
            val ledsArr = JSONArray()
            for (led in p.leds) {
                ledsArr.put(JSONArray().apply { put(led[0]); put(led[1]); put(led[2]) })
            }
            arr.put(JSONObject().apply { put("name", p.name); put("leds", ledsArr) })
        }
        file.writeText(arr.toString())
    }
}
