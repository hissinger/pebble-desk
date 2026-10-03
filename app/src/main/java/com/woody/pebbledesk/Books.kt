package com.woody.pebbledesk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import org.jsoup.Jsoup
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/** YES24 검색 결과 한 권 */
data class BookResult(
    val id: String,
    val title: String,
    val author: String,
    val thumbUrl: String,
    val coverUrl: String,
)

/** YES24 검색 페이지에서 책 표지를 찾는다. 크레마와 같은 YES24 표지라 eBook 표지와도 대부분 일치한다. */
object BookSearch {
    // YES24 는 오래된 Chrome UA 를 메인 페이지로 리다이렉트하므로 Safari UA 를 쓴다.
    private const val USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"
    private val EXCLUDED_TYPES = setOf("[중고도서]", "[Blu-ray]", "[DVD]", "[음반]", "[LP]", "[GIFT]")
    private val GOODS_ID = Regex("""/goods/(\d+)""")

    /** 네트워크를 쓰므로 백그라운드에서 부른다. */
    @Throws(IOException::class)
    fun search(query: String): List<BookResult> {
        val url = "https://www.yes24.com/Product/Search?domain=ALL&query=" + URLEncoder.encode(query, "UTF-8")
        val doc = Jsoup.connect(url).userAgent(USER_AGENT).timeout(15_000).get()
        if (!doc.location().contains("/Search", ignoreCase = true)) throw IOException("redirected")
        return doc.select("div.itemUnit").mapNotNull { item ->
            val type = item.selectFirst("span.gd_res")?.text()?.trim()
            if (type != null && type in EXCLUDED_TYPES) return@mapNotNull null
            val name = item.selectFirst("a.gd_name") ?: return@mapNotNull null
            val id = GOODS_ID.find(name.attr("href"))?.groupValues?.get(1) ?: return@mapNotNull null
            BookResult(
                id = id,
                title = name.text().trim(),
                author = item.select("span.info_auth a").joinToString(", ") { it.text().trim() },
                thumbUrl = "https://image.yes24.com/goods/$id/L",
                coverUrl = "https://image.yes24.com/goods/$id/XL",
            )
        }.distinctBy { it.id }
    }

    /** 네트워크를 쓰므로 백그라운드에서 부른다. */
    @Throws(IOException::class)
    fun download(url: String): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Referer", "https://www.yes24.com/")
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    /** 폭 [widthPx] 에 맞춰 줄이고 흑백으로 바꾼다. 디코딩하지 못하면 null. */
    fun grayCover(bytes: ByteArray, widthPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= widthPx) sample *= 2
        val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val h = (src.height.toLong() * widthPx / src.width).toInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, null, Rect(0, 0, widthPx, h), Ui.grayPaint())
        src.recycle()
        return out
    }
}

/** 네트워크·파일 작업용 백그라운드 스레드와 화면 스레드로 돌려주기 */
object Bg {
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    fun <T> run(work: () -> T, done: (Result<T>) -> Unit) {
        pool.execute {
            val result = runCatching(work)
            main.post { done(result) }
        }
    }
}

/** 읽고 있는 책 한 권: 표지는 앱 전용 저장소의 `books/<id>.jpg`, [app] 은 그 책을 읽는 앱(없으면 null). */
data class ShelfBook(val id: String, val title: String, val author: String, val app: String?, val updatedAt: Long)

/**
 * 읽고 있는 책 목록(최대 [MAX]권). 맨 앞 책이 홈에 크게 보이고, 그 책의 앱이 '읽고 있는 앱'이다.
 * 목록은 books/books.json, 표지는 books/<id>.jpg.
 */
object BookShelf {
    const val MAX = 4
    private val covers = LruCache<String, Bitmap>(8)

    private fun dir(context: Context) = File(context.filesDir, "books").apply { mkdirs() }
    private fun index(context: Context) = File(dir(context), "books.json")
    private fun coverFile(context: Context, id: String) = File(dir(context), "$id.jpg")

    /** 이 런처만 쓰는 파일이라 쓸 때 함께 바꾸는 메모리 사본(돌아올 때마다 파일을 읽지 않도록) */
    @Volatile private var cached: List<ShelfBook>? = null

    fun list(context: Context): List<ShelfBook> {
        cached?.let { return it }
        migrateSingleBook(context)
        return readIndex(context).also { cached = it }
    }

    private fun readIndex(context: Context): List<ShelfBook> {
        val f = index(context)
        if (!f.exists()) return emptyList()
        val arr = runCatching { JSONArray(f.readText()) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map {
            ShelfBook(it.getString("id"), it.optString("title"), it.optString("author"),
                it.optString("app").ifEmpty { null }, it.optLong("updated_at"))
        }.filter { coverFile(context, it.id).length() > 0 }
    }

    /** 흑백으로 줄인 표지. 없거나 읽지 못하면 null. */
    fun cover(context: Context, book: ShelfBook, widthPx: Int): Bitmap? {
        val key = "${book.id}@$widthPx@${book.updatedAt}"
        covers.get(key)?.let { return it }
        // 원본(큰 JPEG)을 매번 줄이지 않도록, 화면 폭으로 줄인 흑백 표지를 파일로 둔다. 원본이 바뀌면 다시 만든다.
        val original = coverFile(context, book.id)
        val thumb = File(dir(context), "${book.id}_$widthPx.png")
        val bmp = thumb.takeIf { it.lastModified() >= original.lastModified() }?.let { BitmapFactory.decodeFile(it.path) }
            ?: runCatching { BookSearch.grayCover(original.readBytes(), widthPx) }.getOrNull()?.also { made ->
                runCatching { thumb.outputStream().use { made.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            }
            ?: return null
        covers.put(key, bmp)
        return bmp
    }

    /** 맨 앞에 넣는다(이미 있으면 앞으로 옮긴다). 새 책인데 [MAX]권이 차 있으면 넣지 않는다([isFull] 로 먼저 확인). 이미지가 아니면 IOException. */
    @Throws(IOException::class)
    fun add(context: Context, book: BookResult, coverBytes: ByteArray, app: String?): ShelfBook {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size, bounds)
        if (bounds.outWidth <= 0) throw IOException("not an image")
        writeAtomically(coverFile(context, book.id)) { it.writeBytes(coverBytes) }
        val old = list(context)
        val added = ShelfBook(book.id, book.title, book.author, old.find { it.id == book.id }?.app ?: app, System.currentTimeMillis())
        write(context, (listOf(added) + old.filter { it.id != book.id }).take(MAX))
        return added
    }

    /** 새 책을 더 넣을 수 없으면 true. 이미 있는 책([id])을 다시 고르는 것은 괜찮다. */
    fun isFull(context: Context, id: String? = null): Boolean {
        val books = list(context)
        return books.size >= MAX && books.none { it.id == id }
    }

    fun moveToFront(context: Context, id: String) {
        val books = list(context)
        val book = books.find { it.id == id } ?: return
        write(context, listOf(book) + books.filter { it.id != id })
    }

    /** [id] 책을 [delta] 칸 옮긴다(-1 이면 위로). 맨 위 책이 홈에 크게 보인다. */
    fun move(context: Context, id: String, delta: Int) {
        val books = list(context).toMutableList()
        val from = books.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to !in books.indices) return
        books.add(to, books.removeAt(from))
        write(context, books)
    }

    /** 책마다 저장한 앱 키를 정리한다(지워진 앱은 null, 실행 화면 이름이 바뀐 앱은 새 키로). */
    fun pruneApps(context: Context, fix: (String) -> String?) {
        val books = list(context)
        val fixed = books.map { b -> b.app?.let { b.copy(app = fix(it)) } ?: b }
        if (fixed != books) write(context, fixed)
    }

    fun setApp(context: Context, id: String, app: String?) =
        write(context, list(context).map { if (it.id == id) it.copy(app = app) else it })

    fun remove(context: Context, id: String) {
        write(context, list(context).filter { it.id != id })
        coverFile(context, id).delete()
        dir(context).listFiles { f -> f.name.startsWith("${id}_") }?.forEach { it.delete() }
    }

    private fun write(context: Context, books: List<ShelfBook>) {
        val arr = JSONArray()
        books.forEach {
            arr.put(JSONObject().put("id", it.id).put("title", it.title).put("author", it.author)
                .put("app", it.app ?: "").put("updated_at", it.updatedAt))
        }
        writeAtomically(index(context)) { it.writeText(arr.toString()) }
        cached = books.filter { coverFile(context, it.id).length() > 0 }
    }

    /** 예전(한 권만 저장하던) now_reading/ 을 목록의 첫 책으로 옮긴다. 그때의 읽고 있는 앱을 그 책의 앱으로. */
    private fun migrateSingleBook(context: Context) {
        val oldDir = File(context.filesDir, "now_reading")
        val info = File(oldDir, "info.json")
        val cover = File(oldDir, "cover.jpg")
        if (!info.exists() || index(context).exists()) return
        val json = runCatching { JSONObject(info.readText()) }.getOrNull()
        if (json != null && cover.length() > 0) {
            val id = "legacy"
            cover.copyTo(coverFile(context, id), overwrite = true)
            val app = HomePrefs(context).legacyReadingApp
            write(context, listOf(ShelfBook(id, json.optString("title"), json.optString("author"), app, json.optLong("updated_at"))))
        }
        oldDir.deleteRecursively()
    }

    private fun writeAtomically(target: File, write: (File) -> Unit) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        write(tmp)
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw IOException("cannot write ${target.name}")
        }
    }
}
