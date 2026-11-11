package dev.kaku.wafake.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import dev.kaku.wafake.Fake
import dev.kaku.wafake.FakeStore
import dev.kaku.wafake.xposed.FakeBubble

/**
 * Rootless mode. Draws fake bubbles on top of WhatsApp's conversation window
 * using an accessibility overlay. Purely visual, nothing is injected into the
 * app process and nothing touches its database.
 */
class FakeOverlayService : AccessibilityService() {

    companion object {
        private const val WA = "com.whatsapp"
        private const val PREFS = "wafake_config"
        private const val KEY_OVERLAY = "overlay_enabled"

        private val TITLE_IDS = arrayOf(
            "com.whatsapp:id/conversation_contact_name",
            "com.whatsapp:id/conversation_title",
            "com.whatsapp:id/title"
        )
    }

    private var wm: WindowManager? = null
    private var overlay: LinearLayout? = null
    private var lastRender = ""
    private var visible = false

    private val main = Handler(Looper.getMainLooper())
    private val restoreAlpha = Runnable { overlay?.alpha = 1f }

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        if (e.packageName?.toString() != WA) {
            hide()
            return
        }
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> refresh()
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> dim()
        }
    }

    override fun onInterrupt() = hide()

    override fun onDestroy() {
        hide()
        wm = null
        super.onDestroy()
    }

    // --------------------------------------------------------------- render

    private fun dim() {
        overlay?.alpha = 0.12f
        main.removeCallbacks(restoreAlpha)
        main.postDelayed(restoreAlpha, 900)
    }

    private fun refresh() {
        val prefs = getSharedPreferences(PREFS, 0)
        if (!prefs.getBoolean(KEY_OVERLAY, true)) {
            hide()
            return
        }

        val title = currentTitle()
        if (title.isNullOrEmpty()) {
            hide()
            return
        }

        val all = FakeStore.decode(prefs.getString("fakes_json", null))
        val mine = all.filter { matches(it, title) }.sortedBy { it.timestamp }
        if (mine.isEmpty()) {
            hide()
            return
        }

        val key = title + "|" + mine.joinToString(",") { it.id + it.timestamp }
        if (key == lastRender && visible) return
        lastRender = key

        show(mine)
    }

    private fun matches(f: Fake, title: String): Boolean {
        val j = f.jid
        if (j == "*") return true
        if (j.startsWith("title:")) return j.removePrefix("title:").equals(title, ignoreCase = true)
        return j.equals(title, ignoreCase = true)
    }

    private fun currentTitle(): String? {
        val root = rootInActiveWindow ?: return null
        for (id in TITLE_IDS) {
            val nodes = runCatching { root.findAccessibilityNodeInfosByViewId(id) }.getOrNull() ?: continue
            for (n in nodes) {
                val t = n.text?.toString()?.trim()
                if (!t.isNullOrEmpty()) return t
            }
        }
        return toolbarTitleFallback(root)
    }

    /**
     * Some builds drop the resource id. The conversation toolbar is the first
     * clickable container near the top carrying a non-empty label.
     */
    private fun toolbarTitleFallback(root: AccessibilityNodeInfo): String? {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        var scanned = 0
        while (stack.isNotEmpty() && scanned < 400) {
            val n = stack.removeFirst()
            scanned++
            val t = n.text?.toString()?.trim()
            if (!t.isNullOrEmpty() && n.isClickable && t.length in 2..60) return t
            for (i in 0 until n.childCount) n.getChild(i)?.let { stack.add(it) }
        }
        return null
    }

    private fun show(fakes: List<Fake>) {
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            alpha = 1f
        }

        for (f in fakes.takeLast(6)) {
            val bubble = FakeBubble.create(ctx)
            FakeBubble.bind(bubble, f.text, f.timestamp)
            root.addView(bubble)
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = dp(84)
        }

        val w = wm ?: return
        if (overlay == null) {
            overlay = root
            runCatching { w.addView(root, lp) }
            visible = true
        } else {
            runCatching { w.removeView(overlay) }
            overlay = root
            runCatching { w.addView(root, lp) }
            visible = true
        }
    }

    private fun hide() {
        val w = wm ?: return
        val v = overlay ?: return
        runCatching { w.removeView(v) }
        overlay = null
        visible = false
        lastRender = ""
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()
}
