package com.woody.pebbledesk

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.util.LruCache
import com.woody.pebbledesk.Db.rows
import com.woody.pebbledesk.Db.tx
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.io.IOException
import java.util.UUID
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

/**
 * 읽고 있는 책 한 권: 표지는 앱 전용 저장소의 `books/<id>.jpg`, [app] 은 그 책을 읽는 앱(없으면 null).
 * [id] 는 넣을 때 런처가 만든다(`b-` + UUID). 같은 책을 두 번 넣어도 따로 다룬다(합치지 않는다).
 */
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
    /** 자동으로 넣을 때 이북 앱이 보여 준 제목. 사용자가 제목을 고쳐도 다음에 펼 때 이 책으로 찾는다. 직접 넣은 책은 "" */
    val seenTitle: String = "",
)

/**
 * 읽고 있는 책 목록에서 나간 책(완독했거나 뺀 책). 올해 읽은 책 목록에 보인다. 완독한 날은 따로 [BookShelf.finishes].
 * 표지는 읽던 때 그대로 `books/<id>.jpg`. [hidden] 은 올해 읽은 책에서 지운 책(읽은 시간이 남아 있어도 다시 보이지 않게).
 */
data class PastBook(val book: ShelfBook, val leftAt: Long, val hidden: Boolean = false)

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

/** 같은 책으로 볼 만한가: 정리한 제목이 같거나, 한쪽이 다른 쪽의 앞부분(두 글자 이상) */
fun sameTitle(a: String, b: String): Boolean {
    val x = normTitle(a)
    val y = normTitle(b)
    if (x.length < 2 || y.length < 2) return x == y
    return x == y || x.startsWith(y) || y.startsWith(x)
}

/**
 * 읽고 있는 책 목록(최대 [MAX]권). 앞 [HOME]권이 홈에 보이고(맨 앞 책은 크게), 맨 앞 책의 앱이 '읽고 있는 앱'이다.
 * 목록은 DB([Db]) `shelf`, 나간 책은 `past`, 표지는 books/<id>.jpg.
 */
object BookShelf {
    const val MAX = 10
    /** 홈에 보이는 책 수: 맨 앞 책 + 함께 읽는 책 3칸 */
    const val HOME = 4
    private val covers = LruCache<String, Bitmap>(16)

    private fun dir(context: Context) = File(Storage.of(context).filesDir, "books").apply { mkdirs() }
    private fun coverFile(context: Context, id: String) = File(dir(context), "$id.jpg")

    /**
     * 이 런처만 쓰는 데이터라 쓸 때 함께 바꾸는 메모리 사본(돌아올 때마다 DB를 읽지 않도록).
     * 화면(메인 스레드)과 접근성 서비스(표지 잘라 넣기는 다른 스레드)가 함께 고치므로 고치는 함수는 모두 @Synchronized.
     */
    @Volatile private var cached: List<ShelfBook>? = null

    fun list(context: Context): List<ShelfBook> = cached ?: load(context)

    @Volatile private var cachedHistory: List<PastBook>? = null

    /** 나간 책(완독한 책·뺀 책·지운 책), 나간 순서대로 */
    fun history(context: Context): List<PastBook> = cachedHistory ?: loadHistory(context)

    @Synchronized private fun loadHistory(context: Context): List<PastBook> {
        cachedHistory?.let { return it }
        return Db.get(context).rows(
            "SELECT id, title, author, app, updated_at, progress, seen_title, left_at, hidden FROM past ORDER BY pos"
        ) { c ->
            PastBook(
                ShelfBook(c.getString(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getString(3)?.ifEmpty { null },
                    c.getLong(4), c.getInt(5), seenTitle = c.getString(6).orEmpty()),
                c.getLong(7), c.getInt(8) != 0,
            )
        }.also { cachedHistory = it }
    }

    @Volatile private var cachedFinishes: Map<String, List<LocalDate>>? = null

    /** 책(id)마다 완독한 날(오래된 순). 다시 읽어도 남는다. */
    fun finishes(context: Context): Map<String, List<LocalDate>> = cachedFinishes ?: loadFinishes(context)

    @Synchronized private fun loadFinishes(context: Context): Map<String, List<LocalDate>> {
        cachedFinishes?.let { return it }
        return Db.get(context).rows("SELECT book_id, day FROM finish ORDER BY day") { it.getString(0) to LocalDate.ofEpochDay(it.getLong(1)) }
            .groupBy({ it.first }, { it.second }).also { cachedFinishes = it }
    }

    /** [id] 책을 [on] 에 완독했다(같은 날 두 번은 한 번으로) */
    @Synchronized private fun addFinish(context: Context, id: String, on: LocalDate) {
        Db.get(context).insertWithOnConflict("finish", null, Db.values("book_id" to id, "day" to on.toEpochDay()), SQLiteDatabase.CONFLICT_IGNORE)
        val all = finishes(context)
        cachedFinishes = all + (id to ((all[id].orEmpty() + on).distinct().sorted()))
    }

    /** 나간 책 목록을 쓴다(지우지 않는다. 지운 책도 읽은 시간이 남아 있어 다시 보이지 않게 남긴다). 한 트랜잭션으로 통째로 바꾼다. */
    private fun writeHistory(context: Context, past: List<PastBook>) {
        Db.get(context).tx {
            delete("past", null, null)
            past.forEachIndexed { i, p ->
                val b = p.book
                insertOrThrow("past", null, Db.values(
                    "pos" to i, "id" to b.id, "title" to b.title, "author" to b.author, "app" to b.app.orEmpty(),
                    "updated_at" to b.updatedAt, "progress" to b.progress, "seen_title" to b.seenTitle,
                    "left_at" to p.leftAt, "hidden" to p.hidden,
                ))
            }
        }
        cachedHistory = past
    }

    /** 읽고 있는 책을 목록에서 내보낸다(나간 책으로 남긴다). 표지는 남긴다. */
    @Synchronized private fun leave(context: Context, id: String) {
        val books = list(context)
        val book = books.find { it.id == id } ?: return
        writeHistory(context, history(context) + PastBook(book, System.currentTimeMillis()))
        write(context, books.filter { it.id != id })
    }

    /** 완독: 오늘 완독한 날을 더하고 읽고 있는 책에서 뺀다(다시 읽던 책이면 두 번째 완독). */
    @Synchronized fun finish(context: Context, id: String) {
        addFinish(context, id, LocalDate.now())
        leave(context, id)
    }

    /** 나간 책(뺀 책) 또는 목록에 없던 책([title], 그 책을 읽은 [app])을 [on] 에 완독한 책으로 */
    @Synchronized fun finishPast(context: Context, pastId: String?, title: String, app: String?, on: LocalDate = LocalDate.now()) {
        val id = pastId?.takeIf { id -> history(context).any { it.book.id == id } } ?: keep(context, title, app).id
        addFinish(context, id, on)
    }

    /**
     * 다시 읽기: 나간 책([pastId]) 또는 목록에 없던 책([title])을 읽고 있는 책 맨 앞으로(표지·앱·완독한 날 그대로).
     * 목록이 다 찼으면 넣지 않고 false.
     */
    @Synchronized fun restore(context: Context, pastId: String?, title: String, app: String?): Boolean {
        if (isFull(context)) return false
        val past = history(context)
        val book = past.find { it.book.id == pastId }?.book ?: titleOnly(title, app)
        write(context, listOf(book.copy(updatedAt = System.currentTimeMillis())) + list(context))
        if (past.any { it.book.id == pastId }) writeHistory(context, past.filter { it.book.id != pastId })
        return true
    }

    /** 올해 읽은 책에서 지운다(읽은 시간은 합계에 그대로). 목록에 없던 책은 그 제목을 지운 책으로 적어 둔다. 표지는 버린다. */
    @Synchronized fun forget(context: Context, pastId: String?, title: String) {
        val past = history(context)
        val now = System.currentTimeMillis()
        writeHistory(context, if (past.any { it.book.id == pastId }) past.map { if (it.book.id == pastId) it.copy(hidden = true, leftAt = now) else it }
            else past + PastBook(titleOnly(title, null), now, hidden = true))
        pastId?.let { dropFiles(context, it) }
    }

    /** 목록에 없던 책(읽은 시간에만 있는 제목)을 책으로. 앱이 보여 준 제목을 [ShelfBook.seenTitle] 로 둬 제목을 고쳐도 읽은 시간이 붙는다. */
    private fun titleOnly(title: String, app: String?) = ShelfBook(newId(), title, "", app, System.currentTimeMillis(), seenTitle = title)

    /** 목록에 없던 책을 나간 책으로 적어 두고 돌려준다(제목·저자·표지를 고칠 수 있게). */
    @Synchronized fun keep(context: Context, title: String, app: String?): ShelfBook =
        titleOnly(title, app).also { writeHistory(context, history(context) + PastBook(it, System.currentTimeMillis())) }

    /** 나간 책 중 제목이 같은 책(지운 책 포함). 고친 제목이면 앱이 보여 준 제목으로도 찾는다. */
    fun findPast(context: Context, title: String): PastBook? {
        val norm = normTitle(title)
        return history(context).lastOrNull { sameTitle(it.book.title, title) }
            ?: history(context).lastOrNull { it.book.seenTitle.isNotEmpty() && normTitle(it.book.seenTitle) == norm }
    }

    /**
     * 자동 추가로 넣어도 되는가: 목록에 자리가 있고, 완독한 책이 아니다
     * (완독한 책을 확인하러 잠깐 열어도 다시 들어오지 않게. 다시 읽으려면 올해 읽은 책에서 `다시 읽기`).
     */
    fun canAutoAdd(context: Context, title: String): Boolean =
        !isFull(context) && findPast(context, title)?.let { !it.hidden && finishes(context)[it.book.id].orEmpty().isNotEmpty() } != true

    @Synchronized private fun load(context: Context): List<ShelfBook> {
        cached?.let { return it }
        migrateSingleBook(context)
        return migrateIds(context, readIndex(context)).also { cached = it }
    }

    private const val ID_PREFIX = "b-"
    private fun newId() = ID_PREFIX + UUID.randomUUID()

    private fun readIndex(context: Context): List<ShelfBook> =
        Db.get(context).rows("SELECT id, title, author, app, updated_at, progress, due, seen_title FROM shelf ORDER BY pos") { c ->
            ShelfBook(c.getString(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getString(3)?.ifEmpty { null },
                c.getLong(4), c.getInt(5), c.getLong(6), c.getString(7).orEmpty())
        }

    /** 흑백으로 줄인 표지. 표지 그림이 없거나 읽지 못하면 제목·저자·앱 이름으로 그린 빈 표지([BlankCover]). */
    fun cover(context: Context, book: ShelfBook, widthPx: Int): Bitmap {
        // 빈 표지에는 앱 이름이 들어가므로 앱이 바뀌어도, 표지는 화면 대비에 맞춰 밝히므로 대비가 바뀌어도 다시 그린다.
        // 한 번이라도 완독한 책은 어디에 놓이든(홈·목록·메뉴·올해 읽은 책) `완독` 띠를 그려 넣는다.
        val tone = Crema.contrastTone(context)
        // 띠 글자는 앱 언어를 따르므로 키에 글자를 넣는다(빈 글자 = 띠 없음).
        val ribbon = if (finishes(context)[book.id].orEmpty().isEmpty()) "" else context.getString(R.string.finish_book)
        val key = "${book.id}@$widthPx@${book.updatedAt}@${book.app}@$tone@$ribbon"
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
        val out = if (ribbon.isEmpty()) bmp else withRibbon(bmp, ribbon)
        covers.put(key, out)
        return out
    }

    /**
     * 표지 오른쪽 위 모서리를 45도로 가로지르는 진한 회색([Ui.GRAY]) [text] 띠를 그린 사본. 띠 굵기·글자는 표지 폭에 비례해(굵기 21%, 글자는 띠의 58%)
     * 큰 표지와 작은 표지가 같은 모양이다. 띠 가운데는 모서리에서 폭의 1/4 안쪽, 위아래 흰 줄로 어두운 표지에서도 띠가 보인다.
     * 표지 칸은 모두 2:3 이고 그림은 가운데를 잘라 보이므로, 먼저 2:3 으로 잘라 두고 그린다(아니면 띠가 잘려 한쪽으로 쏠린다).
     */
    private fun withRibbon(cover: Bitmap, text: String): Bitmap {
        val vw = minOf(cover.width, cover.height * 2 / 3)
        val vh = minOf(cover.height, cover.width * 3 / 2)
        val out = Bitmap.createBitmap(vw, vh, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(cover, -(cover.width - vw) / 2f, -(cover.height - vh) / 2f, null)
        val w = out.width.toFloat()
        val bandH = w * 0.21f
        val k = w * 0.25f
        val half = w * 0.55f
        val inset = bandH * 0.09f
        Canvas(out).apply {
            translate(w - k, k)
            rotate(45f)
            drawRect(-half, -bandH / 2, half, bandH / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.GRAY })
            val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = maxOf(1f, bandH * 0.045f) }
            drawLine(-half, -bandH / 2 + inset, half, -bandH / 2 + inset, edge)
            drawLine(-half, bandH / 2 - inset, half, bandH / 2 - inset, edge)
            val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Ui.bold; textSize = bandH * 0.58f }
            // 글꼴 높이(ascent·descent)가 아니라 실제 글자 모양으로 띠 가운데에 둔다(한글 글꼴은 위아래 여백이 커서 한쪽으로 쏠린다).
            val bounds = Rect().also { label.getTextBounds(text, 0, text.length, it) }
            drawText(text, -bounds.exactCenterX(), -bounds.exactCenterY(), label)
        }
        return out
    }

    /**
     * 검색에서 고른 책을 새 책으로 맨 앞에 넣는다. [MAX]권이 차 있으면 넣지 않고 null.
     * 책은 사용자가 직접 뺄 때만 빠진다(자동으로 밀어내지 않는다). 이미지가 아니면 IOException.
     */
    @Throws(IOException::class)
    @Synchronized fun add(context: Context, book: BookResult, coverBytes: ByteArray): ShelfBook? {
        if (isFull(context)) return null
        val added = ShelfBook(newId(), book.title, book.author, null, System.currentTimeMillis())
        saveCover(context, added.id, coverBytes)
        write(context, listOf(added) + list(context))
        return added
    }

    /**
     * 이북 앱에서 편 책을 맨 앞에 표지 그림 없이 넣는다(읽고 있는 책 자동 추가, 빈 표지로 보인다).
     * [MAX]권이 차 있으면 넣지 않고 null(있던 책을 빼지 않는다. 사용자가 목록에서 직접 뺀다).
     */
    @Synchronized fun addOpened(context: Context, title: String, author: String, app: String?, progress: Int, due: Long): ShelfBook? {
        if (isFull(context)) return null
        val added = ShelfBook(newId(), title, author, app, System.currentTimeMillis(), progress, due, seenTitle = title)
        write(context, listOf(added) + list(context))
        return added
    }

    /**
     * 표지를 다시 고른 책(읽고 있는 책·나간 책): 자리·앱·진행률은 두고 표지·제목·저자만 바꾼다(줄인 표지는 원본이 새로워 다시 만든다).
     */
    @Throws(IOException::class)
    @Synchronized fun replace(context: Context, id: String, book: BookResult, coverBytes: ByteArray) {
        if (list(context).none { it.id == id } && history(context).none { it.book.id == id }) return
        saveCover(context, id, coverBytes)
        edit(context, id) { it.copy(title = book.title, author = book.author) }
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
        // 저자가 채워지면 빈 표지를 다시 그리도록 updatedAt 도 새로.
        val updated = if (a != book.author) System.currentTimeMillis() else book.updatedAt
        write(context, books.map { if (it.id == id) it.copy(progress = p, due = d, author = a, updatedAt = updated) else it })
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
     * 제목이 같은 책(부제·공백 무시). 사용자가 제목을 고친 자동 추가 책은 앱이 보여 준 제목([ShelfBook.seenTitle])으로도 찾는다
     * (그러지 않으면 다음에 펼 때 새 책으로 들어간다).
     */
    fun findByTitle(context: Context, title: String): ShelfBook? {
        val books = list(context)
        val norm = normTitle(title)
        return books.firstOrNull { sameTitle(it.title, title) }
            ?: books.firstOrNull { it.seenTitle.isNotEmpty() && normTitle(it.seenTitle) == norm }
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

    /** 새 책을 더 넣을 수 없으면 true */
    fun isFull(context: Context): Boolean = list(context).size >= MAX

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

    /** 제목을 바꾼다(제목이 잘렸거나 없는 책). 읽고 있는 책·나간 책 모두. */
    fun setTitle(context: Context, id: String, title: String) = edit(context, id) { it.copy(title = title) }

    /** 저자를 직접 넣거나 고친다(이북 앱이 저자를 보여 주지 않은 책). 읽고 있는 책·나간 책 모두. */
    fun setAuthor(context: Context, id: String, author: String) = edit(context, id) { it.copy(author = author) }

    /**
     * 읽고 있는 책 또는 나간 책의 제목·저자를 고친다. 표지가 다시 그려지도록 updatedAt 도 새로.
     * 처음 고칠 때 원래 제목을 [ShelfBook.seenTitle] 로 남겨 이북 앱이 보여 주는 제목으로 계속 찾는다(읽은 시간·자동 추가).
     */
    @Synchronized private fun edit(context: Context, id: String, change: (ShelfBook) -> ShelfBook) {
        val now = System.currentTimeMillis()
        fun ShelfBook.changed() = change(this).copy(updatedAt = now, seenTitle = seenTitle.ifEmpty { title })
        val books = list(context)
        if (books.any { it.id == id }) write(context, books.map { if (it.id == id) it.changed() else it })
        else writeHistory(context, history(context).map { if (it.book.id == id) it.copy(book = it.book.changed()) else it })
    }

    @Synchronized fun setApp(context: Context, id: String, app: String?) =
        write(context, list(context).map { if (it.id == id) it.copy(app = app) else it })

    /** 책 빼기: 읽고 있는 책에서 빼서 뺀 책으로 남긴다(올해 읽은 시간이 있으면 올해 읽은 책에 보인다). */
    fun remove(context: Context, id: String) = leave(context, id)

    /** 읽고 있는 책 목록을 한 트랜잭션으로 통째로 바꾼다(순서 = pos). */
    private fun write(context: Context, books: List<ShelfBook>) {
        Db.get(context).tx {
            delete("shelf", null, null)
            books.forEachIndexed { i, b ->
                insertOrThrow("shelf", null, Db.values(
                    "pos" to i, "id" to b.id, "title" to b.title, "author" to b.author, "app" to b.app.orEmpty(),
                    "updated_at" to b.updatedAt, "progress" to b.progress, "due" to b.due, "seen_title" to b.seenTitle,
                ))
            }
        }
        cached = books
    }

    /** 예전(한 권만 저장하던) now_reading/ 을 목록의 첫 책으로 옮긴다. 그때의 읽고 있는 앱을 그 책의 앱으로. */
    private fun migrateSingleBook(context: Context) {
        val oldDir = File(context.filesDir, "now_reading")
        val info = File(oldDir, "info.json")
        val cover = File(oldDir, "cover.jpg")
        if (!info.exists() || readIndex(context).isNotEmpty()) return
        val json = runCatching { JSONObject(info.readText()) }.getOrNull()
        if (json != null && cover.length() > 0) {
            val id = newId()
            cover.copyTo(coverFile(context, id), overwrite = true)
            val app = HomePrefs(context).legacyReadingApp
            write(context, listOf(ShelfBook(id, json.optString("title"), json.optString("author"), app, json.optLong("updated_at"))))
        }
        oldDir.deleteRecursively()
    }

    /**
     * 예전 id(YES24 상품 번호, 자동 추가는 `auto-<정리한 제목>`, 옛 한 권은 `legacy`)를 런처가 만든 id 로 바꾼다(1.2.1 까지의 데이터).
     * `auto-` 책은 id 의 제목을 [ShelfBook.seenTitle] 로 옮겨, 제목을 고친 책도 계속 찾는다.
     * 중간에 꺼져도 책·표지를 잃지 않게: 표지를 새 이름으로 **복사** → 목록 저장 → 예전 파일 지우기.
     * 표지 복사나 목록 저장이 실패하면 예전 id 그대로 쓰고(id 는 이름일 뿐이라 그대로도 동작한다) 다음에 다시 한다.
     */
    private fun migrateIds(context: Context, books: List<ShelfBook>): List<ShelfBook> {
        if (books.all { it.id.startsWith(ID_PREFIX) }) return books
        val moved = books.map { b ->
            if (b.id.startsWith(ID_PREFIX)) return@map b
            val seen = b.seenTitle.ifEmpty { if (b.id.startsWith("auto-")) b.id.removePrefix("auto-") else "" }
            b.copy(id = newId(), seenTitle = seen)
        }
        val copied = runCatching {
            books.zip(moved).filter { (old, new) -> old.id != new.id }.forEach { (old, new) ->
                val cover = coverFile(context, old.id)
                if (cover.exists()) cover.copyTo(coverFile(context, new.id), overwrite = true)
            }
            write(context, moved)
        }
        if (copied.isFailure) {
            moved.filter { m -> books.none { it.id == m.id } }.forEach { coverFile(context, it.id).delete() }
            return books
        }
        // 목록에 없는 표지·줄인 표지(예전 id 의 것, 앞서 실패한 복사본)를 지운다. 나간 책의 표지는 남긴다.
        val keep = moved.map { it.id } + history(context).map { it.book.id }
        dir(context).listFiles { f ->
            (f.name.endsWith(".jpg") || f.name.endsWith(".png")) && keep.none { f.name.startsWith("$it.") || f.name.startsWith("${it}_") }
        }?.forEach { it.delete() }
        return moved
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
