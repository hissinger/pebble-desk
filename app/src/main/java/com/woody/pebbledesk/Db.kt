package com.woody.pebbledesk

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * 앱 데이터 DB(기기 보호 저장소의 `databases/pebble.db`, 안드로이드 기본 SQLite라 앱 크기가 늘지 않는다).
 * - `reading_day`·`reading_meta`: 날마다 책별 읽은 시간과 어디까지 셌는지([ReadingLog])
 * - `book_open`: 이북 앱에서 책을 편 때([ReadingLog.bookOpened])
 * - `shelf`·`past`: 읽고 있는 책과 나간 책([BookShelf]). 표지 그림은 파일(`books/<id>.jpg`), 설정은 SharedPreferences.
 * 처음 열 때 예전 JSON 파일을 옮기고 `<이름>.migrated` 로 바꿔 둔다([importFiles]).
 */
object Db {
    private const val NAME = "pebble.db"
    private const val VERSION = 1

    @Volatile private var db: SQLiteDatabase? = null

    /** DB(한 번 열어 재사용). 이 실행에서 처음 열 때 예전 JSON 파일이 남아 있으면 옮긴다. */
    fun get(context: Context): SQLiteDatabase {
        db?.let { return it }
        synchronized(this) {
            db?.let { return it }
            val device = Storage.of(context)
            return Helper(device).writableDatabase.also {
                importFiles(device, it)
                db = it
            }
        }
    }

    private class Helper(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE reading_day (day INTEGER NOT NULL, key TEXT NOT NULL, ms INTEGER NOT NULL, PRIMARY KEY (day, key))")
            db.execSQL("CREATE TABLE reading_meta (name TEXT PRIMARY KEY, value TEXT)")
            db.execSQL("CREATE TABLE book_open (at INTEGER NOT NULL, pkg TEXT NOT NULL, title TEXT NOT NULL)")
            db.execSQL(
                "CREATE TABLE shelf (pos INTEGER NOT NULL, id TEXT PRIMARY KEY, title TEXT, author TEXT, app TEXT, " +
                    "updated_at INTEGER, progress INTEGER, due INTEGER, seen_title TEXT)"
            )
            db.execSQL(
                "CREATE TABLE past (pos INTEGER NOT NULL, id TEXT PRIMARY KEY, title TEXT, author TEXT, app TEXT, " +
                    "updated_at INTEGER, progress INTEGER, seen_title TEXT, finished INTEGER, left_at INTEGER, hidden INTEGER)"
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    /** [block] 을 한 트랜잭션으로(중간에 실패하면 아무것도 바뀌지 않는다) */
    inline fun <T> SQLiteDatabase.tx(block: SQLiteDatabase.() -> T): T {
        beginTransaction()
        try {
            return block().also { setTransactionSuccessful() }
        } finally {
            endTransaction()
        }
    }

    /** 커서의 줄마다 [row] 로 읽고 닫는다. */
    inline fun <T> SQLiteDatabase.rows(sql: String, args: Array<String>? = null, row: (Cursor) -> T): List<T> =
        rawQuery(sql, args).use { c -> buildList { while (c.moveToNext()) add(row(c)) } }

    fun values(vararg pairs: Pair<String, Any?>) = ContentValues().apply {
        for ((k, v) in pairs) when (v) {
            null -> putNull(k)
            is String -> put(k, v)
            is Long -> put(k, v)
            is Int -> put(k, v)
            is Boolean -> put(k, if (v) 1 else 0)
            else -> error("unsupported $k")
        }
    }

    /**
     * 예전 버전이 쓰던 JSON 파일을 DB로 옮긴다. 파일마다 한 트랜잭션으로 넣고 `.migrated` 로 이름을 바꾼다(다시 옮기지 않게, 되살릴 수 있게).
     * 읽지 못하는 파일은 `.bad` 로 따로 두고 넘어간다. DB에 이미 있는 것은 덮어쓰지 않는다.
     */
    private fun importFiles(device: Context, db: SQLiteDatabase) {
        val files = device.filesDir
        fun move(file: File, import: (String) -> Unit) {
            if (!file.exists()) return
            runCatching { db.tx { import(file.readText()) } }.onFailure { keepBadCopy(file) }
            file.renameTo(File(file.parentFile, "${file.name}.migrated"))
        }
        // {"through": "2026-10-03", "since": "2026-09-27", "days": {"2026-10-03": {"pkg/제목": ms}}}
        move(File(files, "reading.json")) { text ->
            val json = JSONObject(text)
            val days = json.getJSONObject("days")
            for (d in days.keys()) {
                val o = days.getJSONObject(d)
                val day = LocalDate.parse(d).toEpochDay()
                for (key in o.keys()) {
                    db.insertWithOnConflict("reading_day", null, values("day" to day, "key" to key, "ms" to o.getLong(key)), SQLiteDatabase.CONFLICT_IGNORE)
                }
            }
            for (name in listOf("through", "since")) {
                json.optString(name).takeIf { it.isNotEmpty() && it != "null" }?.let {
                    db.insertWithOnConflict("reading_meta", null, values("name" to name, "value" to it), SQLiteDatabase.CONFLICT_IGNORE)
                }
            }
        }
        // [[at, pkg, title], ...]
        move(File(files, "opened.json")) { text ->
            val a = JSONArray(text)
            for (i in 0 until a.length()) {
                val o = a.getJSONArray(i)
                db.insert("book_open", null, values("at" to o.getLong(0), "pkg" to o.getString(1), "title" to o.getString(2)))
            }
        }
        val books = File(files, "books")
        move(File(books, "books.json")) { text ->
            if (count(db, "shelf") > 0) return@move
            val a = JSONArray(text)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                db.insert("shelf", null, values(
                    "pos" to i, "id" to o.getString("id"), "title" to o.optString("title"), "author" to o.optString("author"),
                    "app" to o.optString("app"), "updated_at" to o.optLong("updated_at"), "progress" to o.optInt("progress", -1),
                    "due" to o.optLong("due", -1), "seen_title" to o.optString("seen_title"),
                ))
            }
        }
        move(File(books, "history.json")) { text ->
            if (count(db, "past") > 0) return@move
            val a = JSONArray(text)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                db.insert("past", null, values(
                    "pos" to i, "id" to o.getString("id"), "title" to o.optString("title"), "author" to o.optString("author"),
                    "app" to o.optString("app"), "updated_at" to o.optLong("updated_at"), "progress" to o.optInt("progress", -1),
                    "seen_title" to o.optString("seen_title"), "finished" to o.optLong("finished", -1).takeIf { it >= 0 },
                    "left_at" to o.optLong("left_at"), "hidden" to o.optBoolean("hidden"),
                ))
            }
        }
    }

    /** 읽지 못한 파일을 `.bad` 로 따로 둔다(되살릴 수 있게). */
    private fun keepBadCopy(file: File) {
        runCatching { file.copyTo(File(file.parentFile, "${file.name}.bad"), overwrite = true) }
    }

    private fun count(db: SQLiteDatabase, table: String): Long = db.rows("SELECT COUNT(*) FROM $table") { it.getLong(0) }.first()
}
