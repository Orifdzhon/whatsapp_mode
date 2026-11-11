package dev.kaku.wafake.xposed

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View

object ChatResolver {

    private const val CONVERSATION_ACTIVITY = "com.whatsapp.Conversation"

    fun activity(view: View): Activity? {
        var ctx: Context? = view.context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    fun isConversation(view: View): Boolean {
        val a = activity(view) ?: return false
        return a.javaClass.name == CONVERSATION_ACTIVITY
    }

    /**
     * Chat JID. WhatsApp hands it to ConversationActivity through the intent.
     * Keys change across builds, so probe the known set, then fall back to the
     * activity title, which WhatsApp sets to the contact/group name.
     */
    fun jid(view: View): String {
        val a = activity(view) ?: return ""
        val intent = a.intent

        val candidates = arrayOf("jid", "chat_jid", "com.whatsapp.jid", "android.intent.extra.TEXT")
        intent?.let { i ->
            for (k in candidates) {
                val v = i.getStringExtra(k)
                if (!v.isNullOrEmpty() && (v.contains("@") || k == "android.intent.extra.TEXT")) return v
            }
            val data = i.dataString
            if (!data.isNullOrEmpty()) return data
        }

        val title = runCatching { a.title?.toString().orEmpty() }.getOrDefault("")
        return if (title.isNotEmpty()) "title:$title" else ""
    }
}
