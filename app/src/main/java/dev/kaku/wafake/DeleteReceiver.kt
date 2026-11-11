package dev.kaku.wafake

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.io.File

class DeleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "dev.kaku.wafake.DELETE_FAKE") return
        val id = intent.getStringExtra("id") ?: return

        val prefs = context.getSharedPreferences(FakeStore.PREFS_FILE, Context.MODE_PRIVATE)
        val all = FakeStore.decode(prefs.getString(FakeStore.KEY_DATA, null)).toMutableList()
        all.removeAll { it.id == id }
        prefs.edit().putString(FakeStore.KEY_DATA, FakeStore.encode(all)).commit()

        runCatching {
            val f = File(context.dataDir, "shared_prefs/${FakeStore.PREFS_FILE}.xml")
            ProcessBuilder("chmod", "644", f.absolutePath).start().waitFor()
        }
    }
}
