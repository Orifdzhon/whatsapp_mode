package dev.kaku.wafake.xposed

import android.app.AndroidAppHelper
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.json.JSONObject
import java.io.File

/**
 * Captures every message WhatsApp writes into msgstore.db *before* it can be
 * revoked or expire, and reports revocations back to the module app.
 *
 * Nothing here deletes, rewrites or unlocks anything. It only reads what the
 * client already wrote and keeps a copy outside the message table.
 */
object MessageVault {

    private const val MODULE = "dev.kaku.wafake"
    private const val ACTION_CAPTURE = "dev.kaku.wafake.VAULT_CAPTURE"
    private const val ACTION_MARK = "dev.kaku.wafake.VAULT_MARK"
    private const val INDEX_FILE = "wafake_index.jsonl"

    private const val TABLE = "message"

    @Volatile
    private var mainDb: SQLiteDatabase? = null

    fun arm(cl: ClassLoader) {
        val dbClass = runCatching { XposedHelpers.findClass("android.database.sqlite.SQLiteDatabase", cl) }
            .getOrNull() ?: return

        for (name in arrayOf("insert", "insertOrThrow", "insertWithOnConflict")) {
            XposedBridge.hookAllMethods(dbClass, name, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    runCatching { onInsert(param) }
                }
            })
        }

        for (name in arrayOf("update", "updateWithOnConflict", "delete")) {
            XposedBridge.hookAllMethods(dbClass, name, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    runCatching { onMutate(param) }
                }
            })
        }

        val conv = XposedHelpers.findClassIfExists("com.whatsapp.Conversation", cl)
        if (conv != null) {
            XposedBridge.hookAllMethods(conv, "onResume", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    runCatching { reconcile() }
                }
            })
        }
    }

    // ---------------------------------------------------------------- capture

    private fun onInsert(param: XC_MethodHook.MethodHookParam) {
        val db = param.thisObject as? SQLiteDatabase ?: return
        val table = param.args.firstOrNull { it is String } as? String ?: return
        if (table != TABLE) return

        val values = param.args.firstOrNull { it is ContentValues } as? ContentValues ?: return
        if (remember(db)) return

        val jid = values.getAsString("key_remote_jid") ?: return
        val keyId = values.getAsString("key_id") ?: return
        if (jid.isEmpty() || keyId.isEmpty()) return

        val rowId = (param.result as? Long) ?: -1L
        val body = values.getAsString("data").orEmpty()
        val fromMe = (values.getAsInteger("key_from_me") ?: 0) == 1
        val ts = values.getAsLong("timestamp") ?: System.currentTimeMillis()
        val mediaType = values.getAsInteger("media_wa_type") ?: 0
        val mime = values.getAsString("media_mime_type").orEmpty()
        val name = values.getAsString("media_name").orEmpty()

        var mediaPath = ""
        if (mediaType != 0 && rowId > 0) {
            mediaPath = queryMediaPath(db, rowId)
        }

        val app = AndroidAppHelper.currentApplication() ?: return

        var storedUri = ""
        if (mediaPath.isNotEmpty()) {
            storedUri = copyToDownloads(app, mediaPath, keyId, mime, name).orEmpty()
        }

        val payload = JSONObject().apply {
            put("key_id", keyId)
            put("jid", jid)
            put("from_me", if (fromMe) 1 else 0)
            put("body", body)
            put("ts", ts)
            put("media_type", mediaType)
            put("media_path", mediaPath)
            put("media_mime", mime)
            put("media_uri", storedUri)
            put("row_id", rowId)
        }
        send(app, ACTION_CAPTURE, payload.toString())

        index(app, keyId, jid, body.isNotEmpty(), mediaPath.isNotEmpty())
    }

    private fun queryMediaPath(db: SQLiteDatabase, rowId: Long): String = runCatching {
        db.rawQuery(
            "SELECT file_path FROM message_media WHERE message_row_id = ? LIMIT 1",
            arrayOf(rowId.toString())
        ).use { c ->
            if (c.moveToFirst()) c.getString(0).orEmpty() else ""
        }
    }.getOrDefault("")

    private fun copyToDownloads(
        ctx: Context,
        src: String,
        keyId: String,
        mime: String,
        originalName: String
    ): String? = runCatching {
        val f = File(src)
        if (!f.exists() || f.length() == 0L) return null

        val ext = originalName.substringAfterLast('.', "").ifEmpty {
            mime.substringAfterLast('/', "").ifEmpty { "bin" }
        }
        val display = "WAFake_${keyId.take(16)}.$ext"
        val safeMime = mime.ifEmpty { "application/octet-stream" }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, display)
                put(MediaStore.MediaColumns.MIME_TYPE, safeMime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/WAFakeVault")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: return null
            ctx.contentResolver.openOutputStream(uri)?.use { out ->
                f.inputStream().use { it.copyTo(out) }
            }
            cv.clear()
            cv.put(MediaStore.MediaColumns.IS_PENDING, 0)
            ctx.contentResolver.update(uri, cv, null, null)
            uri.toString()
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "WAFakeVault")
            dir.mkdirs()
            val dest = File(dir, display)
            f.copyTo(dest, overwrite = true)
            dest.absolutePath
        }
    }.getOrNull()

    // ------------------------------------------------------------ revocation

    private fun onMutate(param: XC_MethodHook.MethodHookParam) {
        val db = param.thisObject as? SQLiteDatabase ?: return
        val table = param.args.firstOrNull { it is String } as? String ?: return
        if (table != TABLE) return
        if (!remember(db)) return

        val app = AndroidAppHelper.currentApplication() ?: return

        val ids = HashSet<String>()
        for (a in param.args) {
            when (a) {
                is Array<*> -> a.forEach { s -> if (s is String) collectId(s, ids) }
                is String -> collectId(a, ids)
            }
        }
        if (ids.isEmpty()) return

        for (k in ids) {
            val o = JSONObject().apply { put("key_id", k) }
            send(app, ACTION_MARK, o.toString())
        }
    }

    private val idPattern = Regex("^[0-9A-Fa-f]{16,64}$")

    private fun collectId(raw: String, out: MutableSet<String>) {
        val s = raw.trim()
        if (idPattern.matches(s)) out.add(s)
    }

    // ------------------------------------------------------------- reconcile

    /**
     * Anything we indexed that no longer exists in the message table was
     * revoked or expired. This is implementation-agnostic: revokes, deletes
     * for me, and disappearing-message timers all land here.
     */
    private fun reconcile() {
        val app = AndroidAppHelper.currentApplication() ?: return
        val db = mainDb ?: openMain(app) ?: return
        mainDb = db

        val idx = File(app.filesDir, INDEX_FILE)
        if (!idx.exists()) return

        val lines = runCatching { idx.readLines() }.getOrNull() ?: return
        val seen = HashSet<String>()
        val marks = JSONObject()

        for (line in lines) {
            val o = runCatching { JSONObject(line) }.getOrNull() ?: continue
            val key = o.optString("k")
            if (key.isEmpty() || !seen.add(key)) continue

            var gone = true
            var emptied = false
            runCatching {
                db.rawQuery("SELECT data FROM message WHERE key_id = ? LIMIT 1", arrayOf(key)).use { c ->
                    if (c.moveToFirst()) {
                        gone = false
                        val data = c.getString(0)
                        emptied = data.isNullOrEmpty() && o.optBoolean("h", false)
                    }
                }
            }

            if (gone || emptied) marks.put(key, 1)
        }

        if (marks.length() == 0) return
        send(app, ACTION_MARK, JSONObject().put("ids", marks.keys().asSequence().toList()).toString())
    }

    private fun openMain(ctx: Context): SQLiteDatabase? = runCatching {
        val f = ctx.getDatabasePath("msgstore.db")
        if (!f.exists()) return null
        SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READONLY)
    }.getOrNull()

    private fun remember(db: SQLiteDatabase): Boolean =
        runCatching { db.path.endsWith("msgstore.db") }.getOrDefault(false)

    // ---------------------------------------------------------------- plumbing

    private fun index(app: Context, keyId: String, jid: String, hadText: Boolean, hadMedia: Boolean) {
        runCatching {
            val f = File(app.filesDir, INDEX_FILE)
            if (f.exists() && f.length() > 4L * 1024 * 1024) {
                // keep the index bounded; old entries are already delivered
                val keep = f.readLines().takeLast(4000)
                f.writeText(keep.joinToString("\n", postfix = "\n"))
            }
            val line = JSONObject().apply {
                put("k", keyId)
                put("j", jid)
                put("h", hadText)
                put("m", hadMedia)
            }.toString()
            f.appendText(line + "\n")
        }
    }

    private fun send(ctx: Context, action: String, payload: String) {
        runCatching {
            val i = Intent(action).setPackage(MODULE).putExtra("payload", payload)
            ctx.sendBroadcast(i)
        }
    }
}
