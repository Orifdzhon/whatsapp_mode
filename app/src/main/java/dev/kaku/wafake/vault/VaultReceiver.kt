package dev.kaku.wafake.vault

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONObject

class VaultReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CAPTURE = "dev.kaku.wafake.VAULT_CAPTURE"
        const val ACTION_MARK = "dev.kaku.wafake.VAULT_MARK"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val raw = intent.getStringExtra("payload") ?: return
        val db = VaultDb(context.applicationContext)

        when (intent.action) {
            ACTION_CAPTURE -> {
                val o = runCatching { JSONObject(raw) }.getOrNull() ?: return
                db.add(
                    keyId = o.optString("key_id"),
                    jid = o.optString("jid"),
                    fromMe = o.optInt("from_me"),
                    body = o.optString("body"),
                    ts = o.optLong("ts"),
                    mediaType = o.optInt("media_type"),
                    mediaPath = o.optString("media_path"),
                    mediaMime = o.optString("media_mime"),
                    mediaUri = o.optString("media_uri")
                )

                // a new capture may revive a previously flagged entry
                db.markAlive(o.optString("key_id"))
            }

            ACTION_MARK -> {
                val o = runCatching { JSONObject(raw) }.getOrNull() ?: return
                val ids = o.optJSONArray("ids")
                if (ids != null) {
                    for (i in 0 until ids.length()) db.markDeleted(ids.optString(i))
                } else {
                    db.markDeleted(o.optString("key_id"))
                }
            }
        }
    }
}
