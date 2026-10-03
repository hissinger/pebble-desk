package com.woody.pebblehome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import org.jsoup.Jsoup
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

/** 지금 읽는 책 */
data class NowReading(val title: String, val author: String, val updatedAt: Long, val cover: Bitmap)

/** 지금 읽는 책을 앱 전용 저장소에 둔다: 원본 표지(cover.jpg)와 제목·저자(info.json). */
object NowReadingStore {
    private var cached: NowReading? = null

    private fun dir(context: Context) = File(context.filesDir, "now_reading").apply { mkdirs() }
    private fun info(context: Context) = File(dir(context), "info.json")
    private fun cover(context: Context) = File(dir(context), "cover.jpg")

    /** 없으면 null. 바뀌지 않았으면 표지를 다시 읽지 않는다. */
    fun read(context: Context, coverWidthPx: Int): NowReading? {
        val json = info(context).takeIf { it.exists() }?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
            ?: return null.also { cached = null }
        val updatedAt = json.optLong("updated_at")
        cached?.let { if (it.updatedAt == updatedAt && it.cover.width == coverWidthPx) return it }
        val bitmap = runCatching { BookSearch.grayCover(cover(context).readBytes(), coverWidthPx) }.getOrNull() ?: return null
        return NowReading(json.optString("title"), json.optString("author"), updatedAt, bitmap).also { cached = it }
    }

    /** 설정 화면용: 표지는 읽지 않고 제목만. 없으면 null. */
    fun title(context: Context): String? = info(context).takeIf { it.exists() }
        ?.let { runCatching { JSONObject(it.readText()).optString("title") }.getOrNull() }

    /** 이미지가 아니면(오류 페이지 등) 저장하지 않고 IOException. */
    @Throws(IOException::class)
    fun save(context: Context, book: BookResult, coverBytes: ByteArray) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size, bounds)
        if (bounds.outWidth <= 0) throw IOException("not an image")
        writeAtomically(cover(context)) { it.writeBytes(coverBytes) }
        val json = JSONObject().put("title", book.title).put("author", book.author)
            .put("updated_at", System.currentTimeMillis())
        writeAtomically(info(context)) { it.writeText(json.toString()) }
    }

    fun clear(context: Context) {
        info(context).delete()
        cover(context).delete()
        cached = null
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
