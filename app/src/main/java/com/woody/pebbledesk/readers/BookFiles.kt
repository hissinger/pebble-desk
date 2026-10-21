package com.woody.pebbledesk.readers

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Xml
import com.woody.pebbledesk.Storage
import com.woody.pebbledesk.sameTitle
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

/**
 * 기기 저장소의 책 파일 읽기(KOReader 처럼 화면에 글자를 드러내지 않는 앱이 남긴 기록과 책 파일). 안드로이드 11+ 은 '모든 파일 액세스',
 * 그 전은 저장소 읽기 권한이 있어야 한다([canRead]). 사용자가 설정 › 독서에서 허용한다([request]).
 */
object BookFiles {
    /** 저장소의 다른 앱 파일(KOReader 기록·책 옆 `.sdr`·책 파일)을 읽을 수 있는가 */
    fun canRead(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    /**
     * 허용하러 간다: 11+ 은 이 앱의 '모든 파일 액세스' 시스템 화면(없으면 목록), 그 전은 권한 창. 그 전 판에서 '다시 묻지 않음'으로 거절해
     * 권한 창이 더는 뜨지 않으면(한 번 물었는데 안내도 못 띄움) 이 앱의 앱 정보(권한)를 연다.
     */
    fun request(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                activity.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${activity.packageName}")))
            }.recoverCatching { activity.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            return
        }
        val permission = Manifest.permission.READ_EXTERNAL_STORAGE
        val prefs = Storage.of(activity).getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(ASKED, false) && !activity.shouldShowRequestPermissionRationale(permission)) {
            runCatching { activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}"))) }
            return
        }
        prefs.edit().putBoolean(ASKED, true).apply()
        activity.requestPermissions(arrayOf(permission), 0)
    }

    private const val PREFS = "book_files"
    private const val ASKED = "asked"

    /** EPUB 책 정보: 제목·저자(여럿이면 `, `)와 표지 그림의 묶음 안 경로(없으면 null) */
    class Epub(val title: String, val author: String, val cover: String?)

    /** EPUB 의 책 정보(EPUB 이 아니거나 못 읽으면 null) */
    fun epub(path: String): Epub? {
        if (!path.endsWith(".epub", ignoreCase = true)) return null
        return runCatching { ZipFile(path).use { zip -> readEpub(zip) } }.getOrNull()
    }

    /**
     * 저장소에서 책 정보의 제목이 [title] 인 EPUB 의 경로(연 파일을 내놓지 않는 리더, 문리더). 얕은 폴더부터 [SEARCH_ENTRIES] 개까지 보고
     * 숨긴 폴더(`.`)·`Android` 는 들어가지 않는다. 같은 제목([sameTitle])이 여럿이면 제목·저자가 그대로 같은 것. 못 찾으면 null.
     * 한 번 읽은 파일의 제목·저자는 기억해 두어 다음 찾기에서 다시 열지 않는다. 화면 스레드에서 부르지 않는다.
     */
    fun findEpub(title: String, author: String): String? {
        val root = storageRoot
        val skip = File(root, "Android")
        val dirs = ArrayDeque<Pair<File, Int>>().apply { add(root to 0) }
        var entries = 0
        var match: String? = null
        while (dirs.isNotEmpty() && entries < SEARCH_ENTRIES) {
            val (dir, depth) = dirs.removeFirst()
            for (f in dir.listFiles().orEmpty().sortedBy { it.name }) {
                if (++entries > SEARCH_ENTRIES) break
                if (f.name.startsWith('.')) continue
                if (f.isDirectory) {
                    if (depth < SEARCH_DEPTH && f != skip) dirs += f to depth + 1
                    continue
                }
                if (!f.name.endsWith(".epub", ignoreCase = true)) continue
                val (t, a) = epubNames.getOrPut("${f.path}@${f.lastModified()}") { epub(f.path)?.let { it.title to it.author } ?: ("" to "") }
                if (t.isEmpty() || !sameTitle(t, title)) continue
                if (t == title && a == author) return f.path
                if (match == null) match = f.path
            }
        }
        return match
    }

    /** 찾기에서 읽은 EPUB(경로@수정 시각)의 제목·저자([findEpub]. 못 읽은 파일은 빈 제목) */
    private val epubNames = ConcurrentHashMap<String, Pair<String, String>>()

    private const val SEARCH_ENTRIES = 5000
    private const val SEARCH_DEPTH = 6

    /**
     * 책 파일의 표지(JPEG, 긴 변을 [COVER_PX] 안으로 줄임). EPUB 만(책 정보의 표지 그림), 없거나 못 읽으면 null.
     * 큰 그림을 다루므로 화면 스레드에서 부르지 않는다.
     */
    fun cover(path: String): ByteArray? = runCatching {
        ZipFile(path).use { zip ->
            val href = readEpub(zip)?.cover ?: return null
            val entry = zip.getEntry(href) ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            zip.getInputStream(entry).use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= COVER_PX) sample *= 2
            val decoded = zip.getInputStream(entry).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
                ?: return null
            val scale = COVER_PX.toFloat() / maxOf(decoded.width, decoded.height)
            val bitmap = if (scale < 1f) Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
                .also { decoded.recycle() } else decoded
            ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it); bitmap.recycle() }.toByteArray()
        }
    }.getOrNull()

    /** EPUB 묶음: `META-INF/container.xml` 이 가리키는 OPF 의 제목·저자·표지 */
    private fun readEpub(zip: ZipFile): Epub? {
        val opfPath = zip.getEntry("META-INF/container.xml")?.let { e ->
            zip.getInputStream(e).use { s -> firstAttr(s, "rootfile", "full-path") }
        } ?: return null
        val opf = zip.getEntry(opfPath) ?: return null
        val base = opfPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        var title = ""
        val authors = mutableListOf<String>()
        var coverId: String? = null
        val items = mutableMapOf<String, String>()
        var coverImage: String? = null
        zip.getInputStream(opf).use { s ->
            val p = parser(s)
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.eventType != XmlPullParser.START_TAG) continue
                when (p.name.substringAfter(':')) {
                    "title" -> if (title.isEmpty()) title = p.nextText().trim()
                    "creator" -> p.nextText().trim().takeIf { it.isNotEmpty() }?.let(authors::add)
                    "meta" -> if (p.attr("name") == "cover") coverId = p.attr("content")
                    "item" -> {
                        val id = p.attr("id")
                        val href = p.attr("href") ?: continue
                        if (id != null) items[id] = href
                        if (p.attr("properties")?.split(' ')?.contains("cover-image") == true) coverImage = href
                    }
                }
            }
        }
        // 경로는 URI 라 `+` 는 글자 그대로다(URLDecoder 는 공백으로 바꾼다)
        val cover = (coverImage ?: coverId?.let(items::get))?.let { base + URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }?.let(::normalize)
        return Epub(title, authors.joinToString(", "), cover)
    }

    /** `OEBPS/../Images/a.jpg` → `Images/a.jpg` */
    private fun normalize(path: String): String {
        val out = ArrayList<String>()
        for (part in path.split('/')) when (part) {
            "", "." -> Unit
            ".." -> if (out.isNotEmpty()) out.removeAt(out.size - 1)
            else -> out += part
        }
        return out.joinToString("/")
    }

    private fun parser(s: InputStream): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(s, null)
    }

    private fun XmlPullParser.attr(name: String): String? =
        (0 until attributeCount).firstOrNull { getAttributeName(it).substringAfter(':') == name }?.let(::getAttributeValue)

    private fun firstAttr(s: InputStream, tag: String, name: String): String? {
        val p = parser(s)
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG && p.name.substringAfter(':') == tag) return p.attr(name)
        }
        return null
    }

    /** 표지의 긴 변(홈의 큰 표지보다 조금 크게) */
    private const val COVER_PX = 900

    /** 기기 저장소 맨 위(`/storage/emulated/0`) */
    val storageRoot: File get() = Environment.getExternalStorageDirectory()
}
