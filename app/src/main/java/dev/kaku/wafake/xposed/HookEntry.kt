package dev.kaku.wafake.xposed

import android.content.Intent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import dev.kaku.wafake.Fake
import dev.kaku.wafake.FakeStore

class HookEntry : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "WAFake"
        private const val TARGET = "com.whatsapp"
        private const val MODULE_PKG = "dev.kaku.wafake"
        const val ACTION_DELETE = "dev.kaku.wafake.DELETE_FAKE"

        private val hidden = java.util.Collections.synchronizedSet(mutableSetOf<String>())

        fun hide(f: Fake) {
            hidden.add(f.id)
        }

        internal fun isHidden(id: String) = hidden.contains(id)
    }

    private fun prefs(): XSharedPreferences =
        XSharedPreferences(MODULE_PKG, FakeStore.PREFS_FILE).apply {
            runCatching { makeWorldReadable() }
            reload()
        }

    private fun enabled(): Boolean =
        runCatching { prefs().getBoolean(FakeStore.KEY_ENABLED, true) }.getOrDefault(true)

    private fun load(jid: String): List<Fake> {
        val raw = runCatching { prefs().getString(FakeStore.KEY_DATA, null) }.getOrNull()
        return FakeStore.forChat(FakeStore.decode(raw), jid).filterNot { isHidden(it.id) }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET) return

        val recyclerClass = runCatching {
            XposedHelpers.findClass("androidx.recyclerview.widget.RecyclerView", lpparam.classLoader)
        }.getOrNull() ?: return

        XposedBridge.hookAllMethods(recyclerClass, "setAdapter", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!enabled()) return

                val view = param.thisObject as? View ?: return
                val adapter = param.args.getOrNull(0) as? RecyclerView.Adapter<*> ?: return
                if (adapter is FakeAdapterProxy) return
                if (!ChatResolver.isConversation(view)) return

                val jid = ChatResolver.jid(view)
                if (jid.isEmpty()) return

                val mine = load(jid)
                if (mine.isEmpty()) return

                @Suppress("UNCHECKED_CAST")
                val host = adapter as RecyclerView.Adapter<RecyclerView.ViewHolder>
                val proxy = FakeAdapterProxy(host, mine) { f ->
                    // tell the module app to drop it from prefs so it stops
                    // appearing on the next conversation open
                    runCatching {
                        val i = Intent(HookEntry.ACTION_DELETE)
                            .setPackage(MODULE_PKG)
                            .putExtra("id", f.id)
                        view.context.sendBroadcast(i)
                    }
                }

                XposedBridge.log("$TAG: overlay ${mine.size} row(s) -> $jid")

                (view as RecyclerView).post {
                    runCatching { view.adapter = proxy }
                }
            }
        })

        XposedBridge.log("$TAG: armed on $TARGET")

        runCatching { MessageVault.arm(lpparam.classLoader) }
            .onFailure { XposedBridge.log("$TAG: vault arm failed: ${it.message}") }
    }
}
