package it.cerrone.dailyhub

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Tasks, chat and deletion markers kept on the phone. A dirty row still has to be sent to the PC. */
class LocalDb private constructor(context: Context) : SQLiteOpenHelper(context, "nexus.sqlite3", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, uid TEXT NOT NULL UNIQUE, title TEXT NOT NULL, " +
                "due TEXT, done INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL, completed_at TEXT, " +
                "updated_at TEXT NOT NULL, deleted INTEGER NOT NULL DEFAULT 0, dirty INTEGER NOT NULL DEFAULT 1)"
        )
        db.execSQL(
            "CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT, uid TEXT NOT NULL UNIQUE, conversation_id TEXT NOT NULL, " +
                "at TEXT NOT NULL, role TEXT NOT NULL, text TEXT NOT NULL, actions TEXT, updated_at TEXT NOT NULL, " +
                "deleted INTEGER NOT NULL DEFAULT 0, dirty INTEGER NOT NULL DEFAULT 1)"
        )
        db.execSQL(
            "CREATE TABLE tombstones (conversation_id TEXT PRIMARY KEY, updated_at TEXT NOT NULL, dirty INTEGER NOT NULL DEFAULT 1)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun openTasks(today: LocalDate): List<TaskInfo> = tasks(includeDone = false, today)

    fun tasks(includeDone: Boolean, today: LocalDate): List<TaskInfo> =
        queryTasks(if (includeDone) "deleted = 0" else "deleted = 0 AND done = 0", today)

    fun addTask(title: String, due: LocalDate?): Long {
        val now = nowIso()
        val values = ContentValues().apply {
            put("uid", UUID.randomUUID().toString())
            put("title", title.trim())
            put("due", due?.toString())
            put("done", 0)
            put("created_at", now)
            put("updated_at", now)
            put("deleted", 0)
            put("dirty", 1)
        }
        return writableDatabase.insert("tasks", null, values)
    }

    fun completeTask(id: Long) {
        val now = nowIso()
        val values = ContentValues().apply {
            put("done", 1)
            put("completed_at", now)
            put("updated_at", now)
            put("dirty", 1)
        }
        writableDatabase.update("tasks", values, "id = ? AND deleted = 0", arrayOf(id.toString()))
    }

    fun deleteTask(id: Long) {
        val values = ContentValues().apply {
            put("deleted", 1)
            put("updated_at", nowIso())
            put("dirty", 1)
        }
        writableDatabase.update("tasks", values, "id = ? AND deleted = 0", arrayOf(id.toString()))
    }

    /** Edits title and due date of a task (the Edit dialog on the home screen). */
    fun updateTask(id: Long, title: String, due: LocalDate?) {
        val values = ContentValues().apply {
            put("title", title.trim())
            if (due == null) putNull("due") else put("due", due.toString())
            put("updated_at", nowIso())
            put("dirty", 1)
        }
        writableDatabase.update("tasks", values, "id = ? AND deleted = 0", arrayOf(id.toString()))
    }

    fun completeByUid(uid: String): TaskInfo? {
        val row = taskByUid(uid) ?: return null
        completeTask(row.id)
        return taskByUid(uid)
    }

    fun updateByUid(uid: String, title: String?, due: String?, clearDue: Boolean): TaskInfo? {
        val row = taskByUid(uid) ?: return null
        val values = ContentValues().apply {
            if (!title.isNullOrBlank()) put("title", title.trim())
            if (clearDue) putNull("due") else if (due != null) put("due", due)
            put("updated_at", nowIso())
            put("dirty", 1)
        }
        writableDatabase.update("tasks", values, "uid = ?", arrayOf(uid))
        return taskByUid(uid) ?: row
    }

    fun dirtyTasks(): JSONArray = jsonTasks("dirty = 1")

    /** The PC's copy replaces ours. Dirty is cleared because this row is no longer waiting to be sent. */
    fun applyTask(obj: JSONObject) {
        val uid = obj.getString("uid")
        val values = ContentValues().apply {
            put("uid", uid)
            put("title", obj.getString("title"))
            if (obj.isNull("due")) putNull("due") else put("due", obj.getString("due"))
            put("done", if (obj.optBoolean("done")) 1 else 0)
            put("created_at", obj.getString("createdAt"))
            if (obj.isNull("completedAt")) putNull("completed_at") else put("completed_at", obj.getString("completedAt"))
            put("updated_at", obj.getString("updatedAt"))
            put("deleted", if (obj.optBoolean("deleted")) 1 else 0)
            put("dirty", 0)
        }
        val updated = writableDatabase.update("tasks", values, "uid = ?", arrayOf(uid))
        if (updated == 0) writableDatabase.insert("tasks", null, values)
    }

    fun recordExchange(conversationId: String, question: String, reply: String, actions: List<String>) {
        val now = nowIso()
        val db = writableDatabase
        db.beginTransaction()
        try {
            insertMessage(db, conversationId, now, "user", question, emptyList())
            insertMessage(db, conversationId, now, "bot", reply, actions)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun conversations(): List<ChatSummary> {
        val rows = readableDatabase.rawQuery(
            "SELECT conversation_id, MIN(id) AS first_id, MAX(at) AS last, COUNT(*) AS total FROM messages " +
                "WHERE deleted = 0 GROUP BY conversation_id ORDER BY MAX(id) DESC LIMIT 60",
            null,
        )
        return rows.use {
            val found = mutableListOf<ChatSummary>()
            while (it.moveToNext()) {
                val id = it.getString(0)
                val first = readableDatabase.rawQuery("SELECT text FROM messages WHERE id = ?", arrayOf(it.getLong(1).toString()))
                val title = first.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else tr("Conversazione", "Conversation") }
                found.add(ChatSummary(id, title.take(60), it.getString(2), it.getInt(3), sourceOf(id)))
            }
            found
        }
    }

    fun conversationMessages(conversationId: String): List<StoredMessage> {
        val rows = readableDatabase.rawQuery(
            "SELECT role, text, actions FROM messages WHERE conversation_id = ? AND deleted = 0 ORDER BY id",
            arrayOf(conversationId),
        )
        return rows.use {
            val found = mutableListOf<StoredMessage>()
            while (it.moveToNext()) {
                val raw = it.getString(2)
                val actions = mutableListOf<String>()
                if (raw != null) {
                    val array = JSONArray(raw)
                    for (index in 0 until array.length()) actions.add(array.getString(index))
                }
                found.add(StoredMessage(it.getString(0) == "user", it.getString(1), actions))
            }
            found
        }
    }

    /** Last exchanges as plain user/bot text, for continuing a conversation. */
    fun recentTurns(conversationId: String, limit: Int): List<Pair<String, String>> {
        val stored = conversationMessages(conversationId)
        val pairs = mutableListOf<Pair<String, String>>()
        var pending: String? = null
        for (message in stored) {
            if (message.fromUser) pending = message.text else if (pending != null) {
                pairs += pending to message.text
                pending = null
            }
        }
        return pairs.takeLast(limit)
    }

    fun deleteConversation(conversationId: String): Boolean {
        val now = nowIso()
        val values = ContentValues().apply {
            put("deleted", 1)
            put("updated_at", now)
            put("dirty", 0)
        }
        val changed = writableDatabase.update("messages", values, "conversation_id = ? AND deleted = 0", arrayOf(conversationId))
        if (changed == 0) return false
        val tomb = ContentValues().apply {
            put("conversation_id", conversationId)
            put("updated_at", now)
            put("dirty", 1)
        }
        writableDatabase.insertWithOnConflict("tombstones", null, tomb, SQLiteDatabase.CONFLICT_REPLACE)
        return true
    }

    fun dirtyMessages(): JSONArray {
        val rows = readableDatabase.rawQuery("SELECT * FROM messages WHERE dirty = 1", null)
        val array = JSONArray()
        rows.use {
            while (it.moveToNext()) array.put(messageJson(it))
        }
        return array
    }

    fun dirtyTombstones(): JSONArray {
        val rows = readableDatabase.rawQuery("SELECT conversation_id, updated_at FROM tombstones WHERE dirty = 1", null)
        val array = JSONArray()
        rows.use {
            while (it.moveToNext()) {
                array.put(JSONObject().put("id", it.getString(0)).put("updatedAt", it.getString(1)))
            }
        }
        return array
    }

    fun applyMessage(obj: JSONObject) {
        if (isTombstoned(obj.getString("conversationId"))) return
        val uid = obj.getString("uid")
        val values = ContentValues().apply {
            put("uid", uid)
            put("conversation_id", obj.getString("conversationId"))
            put("at", obj.getString("at"))
            put("role", obj.getString("role"))
            put("text", obj.getString("text"))
            val actions = obj.optJSONArray("actions")
            if (actions == null || actions.length() == 0) putNull("actions") else put("actions", actions.toString())
            put("updated_at", obj.getString("updatedAt"))
            put("deleted", if (obj.optBoolean("deleted")) 1 else 0)
            put("dirty", 0)
        }
        val updated = writableDatabase.update("messages", values, "uid = ?", arrayOf(uid))
        if (updated == 0 && !obj.optBoolean("deleted")) writableDatabase.insert("messages", null, values)
    }

    fun applyTombstone(id: String, updatedAt: String) {
        val values = ContentValues().apply {
            put("deleted", 1)
            put("updated_at", updatedAt)
            put("dirty", 0)
        }
        writableDatabase.update("messages", values, "conversation_id = ?", arrayOf(id))
        val tomb = ContentValues().apply {
            put("conversation_id", id)
            put("updated_at", updatedAt)
            put("dirty", 0)
        }
        writableDatabase.insertWithOnConflict("tombstones", null, tomb, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun clearDirty() {
        writableDatabase.execSQL("UPDATE tasks SET dirty = 0 WHERE dirty = 1")
        writableDatabase.execSQL("UPDATE messages SET dirty = 0 WHERE dirty = 1")
        writableDatabase.execSQL("UPDATE tombstones SET dirty = 0 WHERE dirty = 1")
    }

    private fun isTombstoned(conversationId: String): Boolean {
        val rows = readableDatabase.rawQuery(
            "SELECT 1 FROM tombstones WHERE conversation_id = ?",
            arrayOf(conversationId),
        )
        return rows.use { it.moveToFirst() }
    }

    private fun taskByUid(uid: String): TaskInfo? {
        val rows = readableDatabase.rawQuery("SELECT * FROM tasks WHERE uid = ? AND deleted = 0", arrayOf(uid))
        return rows.use { if (it.moveToFirst()) readTask(it, LocalDate.now()) else null }
    }

    private fun queryTasks(where: String, today: LocalDate): List<TaskInfo> {
        val rows = readableDatabase.rawQuery(
            "SELECT * FROM tasks WHERE $where ORDER BY due IS NULL, due, created_at",
            null,
        )
        return rows.use {
            val found = mutableListOf<TaskInfo>()
            while (it.moveToNext()) found += readTask(it, today)
            found
        }
    }

    private fun jsonTasks(where: String): JSONArray {
        val rows = readableDatabase.rawQuery("SELECT * FROM tasks WHERE $where", null)
        val array = JSONArray()
        rows.use {
            while (it.moveToNext()) {
                array.put(
                    JSONObject()
                        .put("uid", it.getString(it.getColumnIndexOrThrow("uid")))
                        .put("title", it.getString(it.getColumnIndexOrThrow("title")))
                        .put("due", it.nullable("due") ?: JSONObject.NULL)
                        .put("done", it.getInt(it.getColumnIndexOrThrow("done")) == 1)
                        .put("createdAt", it.getString(it.getColumnIndexOrThrow("created_at")))
                        .put("completedAt", it.nullable("completed_at") ?: JSONObject.NULL)
                        .put("updatedAt", it.getString(it.getColumnIndexOrThrow("updated_at")))
                        .put("deleted", it.getInt(it.getColumnIndexOrThrow("deleted")) == 1)
                )
            }
        }
        return array
    }

    private fun readTask(cursor: android.database.Cursor, today: LocalDate): TaskInfo {
        val due = cursor.nullable("due")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val done = cursor.getInt(cursor.getColumnIndexOrThrow("done")) == 1
        return TaskInfo(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
            due = due,
            overdue = due != null && !done && due < today,
            done = done,
            uid = cursor.getString(cursor.getColumnIndexOrThrow("uid")),
        )
    }

    private fun insertMessage(db: SQLiteDatabase, conversationId: String, at: String, role: String, text: String, actions: List<String>) {
        val values = ContentValues().apply {
            put("uid", UUID.randomUUID().toString())
            put("conversation_id", conversationId)
            put("at", at)
            put("role", role)
            put("text", text)
            if (actions.isEmpty()) putNull("actions") else put("actions", JSONArray(actions).toString())
            put("updated_at", at)
            put("deleted", 0)
            put("dirty", 1)
        }
        db.insert("messages", null, values)
    }

    private fun messageJson(cursor: android.database.Cursor) = JSONObject()
        .put("uid", cursor.getString(cursor.getColumnIndexOrThrow("uid")))
        .put("conversationId", cursor.getString(cursor.getColumnIndexOrThrow("conversation_id")))
        .put("at", cursor.getString(cursor.getColumnIndexOrThrow("at")))
        .put("role", cursor.getString(cursor.getColumnIndexOrThrow("role")))
        .put("text", cursor.getString(cursor.getColumnIndexOrThrow("text")))
        .put("actions", cursor.nullable("actions")?.let { JSONArray(it) } ?: JSONArray())
        .put("updatedAt", cursor.getString(cursor.getColumnIndexOrThrow("updated_at")))
        .put("deleted", cursor.getInt(cursor.getColumnIndexOrThrow("deleted")) == 1)

    private fun android.database.Cursor.nullable(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }

    companion object {
        @Volatile private var instance: LocalDb? = null

        fun get(context: Context): LocalDb = instance ?: synchronized(this) {
            instance ?: LocalDb(context.applicationContext).also { instance = it }
        }

        /** Always with seconds and an offset, e.g. 2026-11-19T17:05:00+01:00, so the PC reads it unambiguously. */
        fun nowIso(): String =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS))

        fun sourceOf(conversationId: String): String = when {
            conversationId.startsWith("sport-") -> "sport"
            conversationId.startsWith("android-") -> "phone"
            else -> "pc"
        }
    }
}
