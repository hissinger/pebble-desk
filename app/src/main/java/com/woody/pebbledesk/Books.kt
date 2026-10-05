package com.woody.pebbledesk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.util.LruCache
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
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
    // 검색 결과 한 권(`<div class="itemUnit">`)에서 읽는 것. HTML 라이브러리(jsoup)는 앱 크기의 절반이라 쓰지 않는다.
    private val TYPE = Regex("""<span class="gd_res">(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
    private val NAME = Regex("""(<a\b[^>]*\bclass="gd_name"[^>]*>)(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
    private val HREF = Regex("""href="([^"]*)"""")
    private val AUTHORS = Regex("""<span[^>]*\binfo_auth\b[^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
    private val LINK = Regex("""<a\b[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)

    /** 네트워크를 쓰므로 백그라운드에서 부른다. */
    @Throws(IOException::class)
    fun search(query: String): List<BookResult> {
        val url = "https://www.yes24.com/Product/Search?domain=ALL&query=" + URLEncoder.encode(query, "UTF-8")
        val html = String(get(url, readTimeout = 15_000) { conn ->
            // 검색 페이지가 아닌 곳(메인)으로 넘겨졌으면 결과가 아니다.
            if (!conn.url.toString().contains("/Search", ignoreCase = true)) throw IOException("redirected")
        }, Charsets.UTF_8)
        return html.split("class=\"itemUnit\"").drop(1).mapNotNull { item ->
            val type = TYPE.find(item)?.groupValues?.get(1)?.let(::plain)
            if (type != null && type in EXCLUDED_TYPES) return@mapNotNull null
            val name = NAME.find(item) ?: return@mapNotNull null
            val href = HREF.find(name.groupValues[1])?.groupValues?.get(1) ?: return@mapNotNull null
            val id = GOODS_ID.find(href)?.groupValues?.get(1) ?: return@mapNotNull null
            val authors = AUTHORS.find(item)?.groupValues?.get(1).orEmpty()
            BookResult(
                id = id,
                title = plain(name.groupValues[2]),
                author = LINK.findAll(authors).joinToString(", ") { plain(it.groupValues[1]) },
                thumbUrl = "https://image.yes24.com/goods/$id/L",
                coverUrl = "https://image.yes24.com/goods/$id/XL",
            )
        }.distinctBy { it.id }
    }

    /** HTML 조각의 글자만(태그를 빼고 `&#39;` 같은 것을 풀어서) */
    private fun plain(html: String) = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim()

    /** 네트워크를 쓰므로 백그라운드에서 부른다. */
    @Throws(IOException::class)
    fun download(url: String): ByteArray = get(url, readTimeout = 20_000, referer = "https://www.yes24.com/")

    /** [url] 을 받아 온다(200 이 아니거나 [verify] 가 던지면 IOException). */
    @Throws(IOException::class)
    private fun get(url: String, readTimeout: Int, referer: String? = null, verify: (HttpURLConnection) -> Unit = {}): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = readTimeout
        conn.setRequestProperty("User-Agent", USER_AGENT)
        referer?.let { conn.setRequestProperty("Referer", it) }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conn.responseCode}")
            verify(conn)
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
data class ShelfBook(
    val id: String,
    val title: String,
    val author: String,
    val app: String?,
    val updatedAt: Long,
    /** 이북 앱 서재에서 읽어 온 진행률(%). 모르면 -1 */
    val progress: Int = -1,
    /** 교보도서관 반납일(epochDay). 모르면 -1 */
    val due: Long = -1,
)

/** 홈·목록에 붙이는 진행률·반납일 `[47% 읽음, 반납 4일 남음]`. 모르는 값은 뺀다(홈은 한 줄씩, 목록은 이어서). */
fun Context.bookStatus(book: ShelfBook, today: LocalDate = LocalDate.now()): List<String> {
    val parts = mutableListOf<String>()
    if (book.progress >= 0) parts += getString(R.string.progress_pct, book.progress)
    if (book.due >= 0) {
        val left = book.due - today.toEpochDay()
        parts += when {
            left > 0 -> getString(R.string.due_days, left.toInt())
            left == 0L -> getString(R.string.due_today)
            else -> getString(R.string.due_over)
        }
    }
    return parts
}

/** 제목 맞추기용: 부제(`:` 뒤)·공백·기호를 빼고 소문자로. 이북 앱 서재는 부제를 빼고 보여 주기도 한다. */
fun normTitle(title: String): String =
    title.substringBefore(':').filter { it.isLetterOrDigit() }.lowercase()

/** 이북 앱에서 편 책을 자동으로 넣을 때의 id(앱이 보여 준 제목으로 만든다) */
fun autoBookId(title: String) = "auto-${normTitle(title)}"

/** 같은 책으로 볼 만한가: 정리한 제목이 같거나, 한쪽이 다른 쪽의 앞부분(두 글자 이상) */
fun sameTitle(a: String, b: String): Boolean {
    val x = normTitle(a)
    val y = normTitle(b)
    if (x.length < 2 || y.length < 2) return x == y
    return x == y || x.startsWith(y) || y.startsWith(x)
}

/**
 * 읽고 있는 책 목록(최대 [MAX]권). 맨 앞 책이 홈에 크게 보이고, 그 책의 앱이 '읽고 있는 앱'이다.
 * 목록은 books/books.json, 표지는 books/<id>.jpg.
 */
object BookShelf {
    const val MAX = 4
    private val covers = LruCache<String, Bitmap>(8)

    private fun dir(context: Context) = File(Storage.of(context).filesDir, "books").apply { mkdirs() }
    private fun index(context: Context) = File(dir(context), "books.json")
    private fun coverFile(context: Context, id: String) = File(dir(context), "$id.jpg")

    /**
     * 이 런처만 쓰는 파일이라 쓸 때 함께 바꾸는 메모리 사본(돌아올 때마다 파일을 읽지 않도록).
     * 화면(메인 스레드)과 접근성 서비스(표지 잘라 넣기는 다른 스레드)가 함께 고치므로 고치는 함수는 모두 @Synchronized.
     */
    @Volatile private var cached: List<ShelfBook>? = null

    fun list(context: Context): List<ShelfBook> {
        cached?.let { return it }
        migrateSingleBook(context)
        return readIndex(context).also { cached = it }
    }

    private fun readIndex(context: Context): List<ShelfBook> {
        val f = index(context)
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { arr.getJSONObject(it) }.map {
                ShelfBook(it.getString("id"), it.optString("title"), it.optString("author"),
                    it.optString("app").ifEmpty { null }, it.optLong("updated_at"),
                    it.optInt("progress", -1), it.optLong("due", -1))
            }
        }.getOrElse {
            // 읽지 못하는 목록은 빈 목록으로 시작하되, 다음 저장이 덮어쓰기 전에 되살릴 수 있게 따로 둔다.
            runCatching { f.copyTo(File(f.parentFile, "${f.name}.bad"), overwrite = true) }
            emptyList()
        }
    }

    /** 흑백으로 줄인 표지. 표지 그림이 없거나 읽지 못하면 제목·저자·앱 이름으로 그린 빈 표지([BlankCover]). */
    fun cover(context: Context, book: ShelfBook, widthPx: Int): Bitmap {
        // 빈 표지에는 앱 이름이 들어가므로 앱이 바뀌어도, 표지는 화면 대비에 맞춰 밝히므로 대비가 바뀌어도 다시 그린다.
        val tone = Crema.contrastTone(context)
        val key = "${book.id}@$widthPx@${book.updatedAt}@${book.app}@$tone"
        covers.get(key)?.let { return it }
        // 원본(큰 JPEG)을 매번 줄이지 않도록, 화면 폭으로 줄인 흑백 표지를 파일로 둔다. 원본이 바뀌면 다시 만든다.
        val original = coverFile(context, book.id)
        // 화면 대비만큼 미리 밝힌 그림이라 대비 값을 이름에 넣는다.
        val thumb = File(dir(context), "${book.id}_${widthPx}t${(tone * 100).toInt()}.png")
        val bmp = thumb.takeIf { it.lastModified() >= original.lastModified() }?.let { BitmapFactory.decodeFile(it.path) }
            ?: runCatching { BookSearch.grayCover(original.readBytes(), widthPx)?.also { Crema.undoContrast(it, tone) } }.getOrNull()?.also { made ->
                // 같은 폭의 예전 줄인 표지(대비가 다르거나 예전 이름)는 지운다.
                val stale = Regex("""${Regex.escape("${book.id}_$widthPx")}(g|t\d+)?\.png""")
                dir(context).listFiles { f -> f != thumb && stale.matches(f.name) }?.forEach { it.delete() }
                runCatching { thumb.outputStream().use { made.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            }
            ?: BlankCover.draw(book.title, book.author, book.app?.let { app -> AppStore.load(context).find { it.key == app }?.label }, widthPx)
        covers.put(key, bmp)
        return bmp
    }

    /** 맨 앞에 넣는다(이미 있으면 앞으로 옮긴다). 새 책인데 [MAX]권이 차 있으면 넣지 않는다([isFull] 로 먼저 확인). 이미지가 아니면 IOException. */
    @Throws(IOException::class)
    @Synchronized fun add(context: Context, book: BookResult, coverBytes: ByteArray, app: String?): ShelfBook {
        saveCover(context, book.id, coverBytes)
        val old = list(context)
        val prev = old.find { it.id == book.id }
        val added = ShelfBook(book.id, book.title, book.author, prev?.app ?: app, System.currentTimeMillis(),
            prev?.progress ?: -1, prev?.due ?: -1)
        write(context, (listOf(added) + old.filter { it.id != book.id }).take(MAX))
        return added
    }

    /**
     * 이북 앱에서 편 책을 맨 앞에 표지 그림 없이 넣는다(읽고 있는 책 자동 추가, 빈 표지로 보인다).
     * [MAX]권이 넘으면 맨 뒤(가장 오래 안 편 책)가 빠진다.
     */
    @Synchronized fun addOpened(context: Context, id: String, title: String, author: String, app: String?, progress: Int, due: Long): ShelfBook {
        val added = ShelfBook(id, title, author, app, System.currentTimeMillis(), progress, due)
        val old = list(context)
        val kept = (listOf(added) + old.filter { it.id != id }).take(MAX)
        (old - kept.toSet()).forEach { dropFiles(context, it.id) }
        write(context, kept)
        return added
    }

    /** 표지를 다시 고른 책: 자리·앱·진행률은 두고 표지·제목·저자만 바꾼다. */
    @Synchronized fun replace(context: Context, oldId: String, book: BookResult, coverBytes: ByteArray) {
        val books = list(context)
        val old = books.find { it.id == oldId } ?: return
        saveCover(context, book.id, coverBytes)
        val new = old.copy(id = book.id, title = book.title, author = book.author, updatedAt = System.currentTimeMillis())
        write(context, books.filter { it.id != book.id || it.id == oldId }.map { if (it.id == oldId) new else it })
        if (oldId != book.id) dropFiles(context, oldId)
    }

    /** 이북 앱 서재에서 본 진행률·반납일(저자는 비어 있을 때만)을 적는다(순서는 그대로). 바뀐 게 없으면 쓰지 않는다. */
    @Synchronized fun updateStatus(context: Context, id: String, progress: Int, due: Long, author: String = "") {
        val books = list(context)
        val book = books.find { it.id == id } ?: return
        val p = if (progress >= 0) progress else book.progress
        val d = if (due >= 0) due else book.due
        // 저자는 비어 있을 때만 채운다(읽는 화면 메뉴의 제목으로 먼저 들어온 책).
        val a = book.author.ifBlank { author }
        if (p == book.progress && d == book.due && a == book.author) return
        write(context, books.map { if (it.id == id) it.copy(progress = p, due = d, author = a) else it })
    }

    fun hasCover(context: Context, id: String) = coverFile(context, id).length() > 0

    /** 표지만 바꾼다(이북 앱 화면에서 잘라 온 표지). 바뀐 표지가 다시 그려지도록 updatedAt 도 새로. */
    @Synchronized fun setCover(context: Context, id: String, bytes: ByteArray) {
        val books = list(context)
        if (books.none { it.id == id }) return
        saveCover(context, id, bytes)
        val now = System.currentTimeMillis()
        write(context, books.map { if (it.id == id) it.copy(updatedAt = now) else it })
    }

    /**
     * 제목이 같은 책(부제·공백 무시). 사용자가 제목을 고친 자동 추가 책은 앱이 보여 준 제목으로 만든 id 로도 찾는다
     * (그러지 않으면 다음에 펼 때 새 책으로 들어가며 고친 제목이 되돌아간다).
     */
    fun findByTitle(context: Context, title: String): ShelfBook? {
        val books = list(context)
        return books.firstOrNull { sameTitle(it.title, title) } ?: books.firstOrNull { it.id == autoBookId(title) }
    }

    @Throws(IOException::class)
    private fun saveCover(context: Context, id: String, bytes: ByteArray) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) throw IOException("not an image")
        writeAtomically(coverFile(context, id)) { it.writeBytes(bytes) }
    }

    private fun dropFiles(context: Context, id: String) {
        coverFile(context, id).delete()
        dir(context).listFiles { f -> f.name.startsWith("${id}_") }?.forEach { it.delete() }
    }

    /** 새 책을 더 넣을 수 없으면 true. 이미 있는 책([id])을 다시 고르는 것은 괜찮다. */
    fun isFull(context: Context, id: String? = null): Boolean {
        val books = list(context)
        return books.size >= MAX && books.none { it.id == id }
    }

    @Synchronized fun moveToFront(context: Context, id: String) {
        val books = list(context)
        val book = books.find { it.id == id } ?: return
        write(context, listOf(book) + books.filter { it.id != id })
    }

    /** [id] 책을 [delta] 칸 옮긴다(-1 이면 위로). 맨 위 책이 홈에 크게 보인다. */
    @Synchronized fun move(context: Context, id: String, delta: Int) {
        val books = list(context).toMutableList()
        val from = books.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to !in books.indices) return
        books.add(to, books.removeAt(from))
        write(context, books)
    }

    /** 책마다 저장한 앱 키를 정리한다(지워진 앱은 null, 실행 화면 이름이 바뀐 앱은 새 키로). */
    @Synchronized fun pruneApps(context: Context, fix: (String) -> String?) {
        val books = list(context)
        val fixed = books.map { b -> b.app?.let { b.copy(app = fix(it)) } ?: b }
        if (fixed != books) write(context, fixed)
    }

    /** 제목을 바꾼다(제목이 잘렸거나 없는 책). 빈 표지가 새 제목으로 다시 그려지도록 updatedAt 도 새로. */
    @Synchronized fun setTitle(context: Context, id: String, title: String) {
        val now = System.currentTimeMillis()
        write(context, list(context).map { if (it.id == id) it.copy(title = title, updatedAt = now) else it })
    }

    @Synchronized fun setApp(context: Context, id: String, app: String?) =
        write(context, list(context).map { if (it.id == id) it.copy(app = app) else it })

    @Synchronized fun remove(context: Context, id: String) {
        write(context, list(context).filter { it.id != id })
        dropFiles(context, id)
    }

    private fun write(context: Context, books: List<ShelfBook>) {
        val arr = JSONArray()
        books.forEach {
            arr.put(JSONObject().put("id", it.id).put("title", it.title).put("author", it.author)
                .put("app", it.app ?: "").put("updated_at", it.updatedAt)
                .put("progress", it.progress).put("due", it.due))
        }
        writeAtomically(index(context)) { it.writeText(arr.toString()) }
        cached = books
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
