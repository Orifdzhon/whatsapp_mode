package dev.kaku.wafake

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Fake(
    val id: String,
    val jid: String,
    val text: String,
    val timestamp: Long,
    val outgoing: Boolean = true
)

/**
 * Single source of truth. Stored in /data/data/dev.kaku.wafake/shared_prefs/wafake_config.xml
 * and read inside the WhatsApp process through XSharedPreferences.
 */
object FakeStore {

    const val PREFS_FILE = "wafake_config"
    const val KEY_DATA = "fakes_json"
    const val KEY_ENABLED = "enabled"

    fun decode(raw: String?): List<Fake> {
        if (raw.isNullOrEmpty()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val jid = o.optString("jid")
                val text = o.optString("text")
                if (jid.isEmpty() || text.isEmpty()) return@mapNotNull null
                Fake(
                    id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
                    jid = jid,
                    text = text,
                    timestamp = o.optLong("ts"),
                    outgoing = o.optBoolean("out", true)
                )
            }
        }.getOrDefault(emptyList())
    }

    fun encode(list: List<Fake>): String {
        val arr = JSONArray()
        list.forEach { f ->
            arr.put(
                JSONObject().apply {
                    put("id", f.id)
                    put("jid", f.jid)
                    put("text", f.text)
                    put("ts", f.timestamp)
                    put("out", f.outgoing)
                }
            )
        }
        return arr.toString()
    }

    fun forChat(list: List<Fake>, jid: String): List<Fake> {
        if (jid.isEmpty()) return emptyList()
        return list.filter { it.jid == jid }.sortedBy { it.timestamp }
    }
}
