package com.woody.pebbledesk.readers

import android.content.Context
import com.woody.pebbledesk.readers.LuaData.num
import com.woody.pebbledesk.readers.LuaData.str
import com.woody.pebbledesk.readers.LuaData.table
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

/**
 * KOReader(내 파일을 여는 오픈소스 리더, GitHub 판 `org.koreader.launcher`·F-Droid 판 `org.koreader.launcher.fdroid`): 서재와 읽는 화면이 모두
 * 액티비티 하나(`MainActivity`, NativeActivity)이고 화면 전체를 직접 그려 접근성에는 빈 뷰 하나만 보인다(글자·요소 이름 없음, 화면 바뀜 알림도
 * 앱이 앞에 올 때만). 그래서 어떤 책인지는 KOReader 가 저장소에 남기는 기록으로 안다([fileBook], '모든 파일 액세스'가 있어야 한다, [BookFiles]):
 * - `koreader/history.lua`: 연 책 목록(최근 것이 [1], 파일 경로·때). 책을 열 때 바로 쓰고, 닫을 때 그 책의 때를 다시 쓴다.
 * - 책 옆 `<이름>.sdr/metadata.<확장자>.lua`(설정에 따라 `koreader/docsettings/…`·`koreader/hashdocsettings/…`): 책 정보(`doc_props` 제목·저자),
 *   진행률(`percent_finished` 0~1), 고친 책 정보는 같은 폴더 `custom_metadata.lua`(`custom_props`). 책을 닫거나 KOReader 가 뒤로 갈 때 쓴다.
 * 처음 연 책은 `.sdr` 이 아직 없어 EPUB 은 책 파일의 정보로, 그 밖은 파일 이름(확장자 뗌)으로 안다.
 * 책이 열려 있는지(닫고 파일 목록에 있는지)는 history.lua 의 때와 책 파일의 접근 시각으로 안다([KOReaderData.books]). 읽은 시간은
 * 책이 열려 있던 동안만 센다(Libby 처럼 `viewer_mark`, 모르면 앱이 앞에 있던 동안을 다).
 * 앱 안에서 책을 바꿔도 알림이 없지만, 연 때·닫은 때가 파일에 남아 지켜보지 않고 KOReader 가 앞에 올 때와 홈으로 돌아올 때만 읽는다([fileState]).
 * (KOReader v2026.07.1, 2026-10-18 에뮬레이터에서 확인)
 */
open class KOReaderApp(pkg: String) : ReaderApp(
    pkg = pkg, classPrefix = "org.koreader.launcher",
    shelves = emptySet(),
    viewers = setOf("org.koreader.launcher.MainActivity"),
    titles = emptyList(), thumbs = emptyList(), authors = emptyList(), progress = null, due = null,
    viewerProgress = emptyList(),
    fileState = true,
    opensBookFile = true,
    needsBookFiles = true,
) {
    override fun fileBooks(context: Context, since: Long): List<FileBook>? {
        if (!BookFiles.canRead(context)) return null
        return KOReaderData(File(BookFiles.storageRoot, "koreader")).books(since)
    }
}

/** GitHub 판 */
object KOReader : KOReaderApp("org.koreader.launcher")

/** F-Droid 판 */
object KOReaderFdroid : KOReaderApp("org.koreader.launcher.fdroid")

/** KOReader 의 저장소 폴더 [dir](`/storage/emulated/0/koreader`)에서 읽는다. 같은 파일은 바뀌었을 때만 다시 읽는다. */
internal class KOReaderData(private val dir: File) {
    /**
     * history.lua 의 책들 가운데 [since](ms) 뒤에 열었거나 닫은 책과 맨 앞 책(지금 책), 오래된 것부터(지금 책이 끝, 최근 [MAX_BOOKS]권까지).
     * KOReader 안내서(`koreader/help/`, 처음 켜면 책처럼 연다)는 뺀다(사용자가 koreader 폴더에 둔 책은 넣는다). 기록이 없으면 빈 목록, 못 읽으면 null.
     * 책마다 열림·닫힘: KOReader 는 책을 열 때 history.lua 맨 앞 줄의 때를 적고 그 책 파일의 접근 시각(atime)을 같은 때(초)로 맞추며
     * (`ReadHistory:addItem` 의 `lfs.touch`), 닫을 때는 그 줄의 때만 다시 적는다(`updateLastBookTime`). 저장소는 noatime 이라 읽기만으로는
     * 접근 시각이 바뀌지 않는다. 그래서 접근 시각 = 줄의 때면 열려 있음(연 때), 줄의 때가 더 늦으면 닫힘(닫은 때), 접근 시각이 더 늦으면(다른 앱이 바꿈) 모름.
     * 줄은 최근 것부터라 [since] 보다 앞의 줄을 만나면 그 뒤는 보지 않는다(파일 시각을 묻지 않는다).
     */
    fun books(since: Long): List<FileBook>? {
        val list = history() ?: return null
        return list.withIndex().takeWhile { (i, e) -> i == 0 || e.second > since }.take(MAX_BOOKS).map { (_, e) -> fileBook(e.first, e.second) }.reversed()
    }

    private fun fileBook(path: String, time: Long): FileBook {
        val book = book(path)
        return when (val state = state(path, time)) {
            is State.Open -> FileBook(book.title, book.author, path, book.progress, openedAt = state.at)
            is State.Closed -> FileBook(book.title, book.author, path, book.progress, openedAt = state.openedAt, closedAt = state.at)
            State.Unknown -> FileBook(book.title, book.author, path, book.progress)
        }
    }

    /** history.lua 의 책들(최근 것부터, 경로와 그 줄의 때 ms). KOReader 안내서는 뺀다. 못 읽으면 null */
    private fun history(): List<Pair<String, Long>>? {
        val list = read(File(dir, "history.lua")) as? Map<*, *> ?: return null
        return list.keys.filterIsInstance<Double>().sorted()
            .mapNotNull { list[it] as? Map<*, *> }
            .mapNotNull { e -> e.str("file")?.takeIf { !it.startsWith(dir.path + "/help/") }?.let { it to ((e.num("time") ?: 0.0) * 1000).toLong() } }
    }

    sealed class State {
        /** [at] 에 열어 지금 열려 있다 */
        class Open(val at: Long) : State()
        /** [openedAt] 에 열어 [at] 에 닫았다 */
        class Closed(val at: Long, val openedAt: Long) : State()
        object Unknown : State()
    }

    /** [path] 책이 history.lua 에 [time](ms) 으로 적혀 있을 때 열림·닫힘(그 책 파일의 접근 시각과 견준다, 초 단위) */
    fun state(path: String, time: Long): State {
        val atime = accessTime(File(path)) ?: return State.Unknown
        val a = atime / 1000
        val t = time / 1000
        return when {
            a == t -> State.Open(a * 1000)
            a < t -> State.Closed(t * 1000, a * 1000)
            else -> State.Unknown
        }
    }

    class Book(val title: String, val author: String, val progress: Int)

    /** [path] 책의 제목·저자·진행률(%, 모르면 -1): 고친 책 정보 → KOReader 가 읽은 책 정보 → EPUB 책 정보 → 파일 이름 */
    fun book(path: String): Book {
        val meta = sidecar(path)?.let { read(it) } as? Map<*, *>
        // 고친 책 정보는 책을 열기 전(파일 목록)에도 고칠 수 있어 metadata 가 없는 `.sdr` 에만 있기도 하다
        val custom = sidecarDirs(path).map { File(it, "custom_metadata.lua") }.firstOrNull { it.isFile }
            ?.let { (read(it) as? Map<*, *>)?.table("custom_props") }
        val props = meta?.table("doc_props")
        fun prop(key: String) = custom?.str(key)?.trim()?.takeIf { it.isNotEmpty() } ?: props?.str(key)?.trim()?.takeIf { it.isNotEmpty() }
        val epub by lazy { epub(path) }
        val title = prop("title") ?: epub?.title?.takeIf { it.isNotEmpty() } ?: withoutBookFile(File(path).name)
        val author = (prop("authors") ?: epub?.author.orEmpty()).split('\n').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
        val progress = meta?.num("percent_finished")?.takeIf { it in 0.0..1.0 }?.let { (it * 100).toInt() } ?: -1
        return Book(title, author, progress)
    }

    /**
     * [path] 책의 `metadata.<확장자>.lua`: `.sdr` 폴더 후보([sidecarDirs]) 가운데 있는 것, 여럿이면 가장 최근에 쓴 것(KOReader 도 이렇게 고른다).
     * 없으면 `.old`(쓰는 중에 깨졌을 때의 앞 판).
     */
    fun sidecar(path: String): File? {
        val name = "metadata.${path.substringAfterLast('.', "_")}.lua"
        val dirs = sidecarDirs(path)
        return dirs.map { File(it, name) }.filter { it.isFile }.maxByOrNull { it.lastModified() }
            ?: dirs.map { File(it, "$name.old") }.firstOrNull { it.isFile }
    }

    /** [path] 책의 `.sdr` 폴더 후보: 책 옆(`doc`), `koreader/docsettings/` 아래 같은 경로(`dir`), `koreader/hashdocsettings/`(`hash`, 그 폴더가 있을 때만) */
    private fun sidecarDirs(path: String): List<File> {
        val base = path.substringBeforeLast('.', path)
        return listOfNotNull(
            File("$base.sdr"),
            File(dir, "docsettings$base.sdr"),
            File(dir, "hashdocsettings").takeIf { it.isDirectory }?.let { hashes -> hashOf(path)?.let { File(hashes, "${it.take(2)}/$it.sdr") } },
        )
    }

    /**
     * Lua 값 파일을 바뀌었을 때만 다시 읽는다(KOReader 가 앞에 올 때·홈으로 돌아올 때마다 묻는다). 못 읽은 것(KOReader 가 쓰는 중)은
     * 담아 두지 않고 다음에 다시 읽는다.
     */
    private fun read(file: File): Any? {
        if (!file.isFile) return null
        return cached(file, "lua", keepMissing = false) { LuaData.parse(file.readBytes()) }
    }

    /** 책 파일의 EPUB 정보(`.sdr` 이 아직 없는 책은 다시 읽을 때마다 묻는다. EPUB 이 아니거나 못 읽은 것도 그 파일이 그대로면 다시 열지 않는다) */
    private fun epub(path: String): BookFiles.Epub? = cached(File(path), "epub", keepMissing = true) { BookFiles.epub(path) }

    /** 책 파일의 부분 MD5(`hashdocsettings` 를 쓸 때 다시 읽을 때마다 묻는다) */
    private fun hashOf(path: String): String? = cached(File(path), "md5", keepMissing = true) { partialMd5(File(path)) }

    companion object {
        /** 한 번에 맞추는 책 수(오래 홈에 오지 않은 채 책을 많이 바꿨어도 이만큼만) */
        private const val MAX_BOOKS = 20

        /** 파일의 접근 시각(ms). 못 읽으면 null */
        fun accessTime(file: File): Long? = runCatching {
            Files.readAttributes(file.toPath(), BasicFileAttributes::class.java).lastAccessTime().toMillis()
        }.getOrNull()

        /** 파일(종류 + 경로)마다 (수정 시각·크기, 읽은 것. 없음은 [Missing]). 최근 [CACHE_MAX]개 */
        private val cache = object : LinkedHashMap<String, Pair<Pair<Long, Long>, Any>>() {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Pair<Long, Long>, Any>>?) = size > CACHE_MAX
        }
        private const val CACHE_MAX = 16
        private object Missing

        /**
         * [file] 에서 [load] 로 읽은 것을 그 파일이 바뀌지 않았으면 다시 쓴다. 못 읽었으면(null) [keepMissing] 일 때만 '없음'으로 담는다
         * (KOReader 가 쓰는 중인 Lua 파일은 다음에 다시 읽고, 책 파일은 그대로면 다시 열지 않는다).
         */
        @Suppress("UNCHECKED_CAST")
        private fun <T : Any> cached(file: File, kind: String, keepMissing: Boolean, load: () -> T?): T? {
            val key = "$kind:${file.path}"
            val stamp = file.lastModified() to file.length()
            synchronized(cache) { cache[key]?.takeIf { it.first == stamp }?.let { return it.second.takeIf { v -> v !== Missing } as T? } }
            val value = runCatching(load).getOrNull()
            if (value != null || keepMissing) synchronized(cache) { cache[key] = stamp to (value ?: Missing) }
            return value
        }

        /**
         * KOReader 의 책 열쇠(`util.partialMD5`): 0·1K·4K·16K…1G 자리에서 1K 씩(파일이 끝나면 그만) 모아 MD5.
         * 처음 자리가 0 인 것은 LuaJIT `lshift(1024, -2)` 가 32비트로 넘쳐서다.
         */
        fun partialMd5(file: File): String? = runCatching {
            file.inputStream().channel.use { ch ->
                val md = MessageDigest.getInstance("MD5")
                for (i in -1..10) {
                    val at = if (i < 0) 0L else 1024L shl (2 * i)
                    if (at >= ch.size()) break
                    val buf = java.nio.ByteBuffer.allocate(1024)
                    ch.position(at)
                    while (buf.hasRemaining() && ch.read(buf) > 0) Unit
                    md.update(buf.array(), 0, buf.position())
                }
                md.digest().joinToString("") { "%02x".format(it) }
            }
        }.getOrNull()
    }
}
