package dev.kaku.wafake.vault

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class VaultDb(ctx: Context) : SQLiteOpenHelper(ctx, "vault.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE vault (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                key_id TEXT UNIQUE,
                jid TEXT,
                from_me INTEGER,
                body TEXT,
                ts INTEGER,
                media_type INTEGER,
                media_path TEXT,
                media_mime TEXT,
                media_uri TEXT,
                deleted INTEGER DEFAULT 0,
                captured_at INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_vault_jid ON vault(jid)")
        db.execSQL("CREATE INDEX idx_vault_deleted ON vault(deleted)")
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) = Unit

    fun add(
        keyId: String, jid: String, fromMe: Int, body: String, ts: Long,
        mediaType: Int, mediaPath: String, mediaMime: String, mediaUri: String
    ) {
        if (keyId.isEmpty()) return
        val cv = ContentValues().apply {
            put("key_id", keyId)
            put("jid", jid)
            put("from_me", fromMe)
            put("body", body)
            put("ts", ts)
            put("media_type", mediaType)
            put("media_path", mediaPath)
            put("media_mime", mediaMime)
            put("media_uri", mediaUri)
            put("deleted", 0)
            put("captured_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("vault", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun markDeleted(keyId: String) {
        writableDatabase.execSQL("UPDATE vault SET deleted = 1 WHERE key_id = ?", arrayOf(keyId))
    }

    fun markAlive(keyId: String) {
        writableDatabase.execSQL("UPDATE vault SET deleted = 0 WHERE key_id = ?", arrayOf(keyId))
    }

    fun all(deletedOnly: Boolean): List<Row> {
        val where = if (deletedOnly) "WHERE deleted = 1" else ""
        val out = ArrayList<Row>()
        readableDatabase.rawQuery(
            "SELECT key_id, jid, from_me, body, ts, deleted, media_uri, media_type FROM vault $where ORDER BY ts DESC LIMIT 2000",
            null
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Row(
                        keyId = c.getString(0),
                        jid = c.getString(1).orEmpty(),
                        fromMe = c.getInt(2) == 1,
                        body = c.getString(3).orEmpty(),
                        ts = c.getLong(4),
                        deleted = c.getInt(5) == 1,
                        mediaUri = c.getString(6).orEmpty(),
                        mediaType = c.getInt(7)
                    )
                )
            }
        }
        return out
    }

    fun count(deletedOnly: Boolean = false): Int {
        val where = if (deletedOnly) "WHERE deleted = 1" else ""
        readableDatabase.rawQuery("SELECT COUNT(*) FROM vault $where", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun remove(keyId: String) {
        writableDatabase.delete("vault", "key_id = ?", arrayOf(keyId))
    }

    fun wipe() {
        writableDatabase.delete("vault", null, null)
    }

    data class Row(
        val keyId: String,
        val jid: String,
        val fromMe: Boolean,
        val body: String,
        val ts: Long,
        val deleted: Boolean,
        val mediaUri: String,
        val mediaType: Int
    )
}
