package com.woody.pebbledesk

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import java.io.ByteArrayOutputStream
import android.view.Display
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.graphics.Rect
import android.graphics.Bitmap
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.time.LocalDate
import kotlin.math.abs

/**
 * 읽고 있는 책 자동 추가(접근성 서비스). 교보도서관·교보eBook·밀리의서재·알라딘·북커스의 **서재 화면**에 보이는 제목·저자·진행률·반납일과
 * 사용자가 누른 책 칸만 읽는다. 다른 앱은 시스템이 아예 보내지 않고(`res/xml/reader_watch.xml` 의 packageNames),
 * 읽는 화면은 본문(웹 화면)을 훑지 않고 진행률 줄·메뉴 제목만 이름으로 읽는다. 읽은 내용은 책 목록에만 적고 밖으로 보내지 않는다.
 */
class ReaderWatchService : AccessibilityService() {
    /** 앱마다 지금 앞에 있는 화면(액티비티 이름) */
    private val screen = mutableMapOf<String, String>()
    /** 앱마다 마지막으로 편 책의 제목(밀리 뷰어의 진행률을 붙일 곳). 표지 다시 찾기로 id 가 바뀌어도 찾도록 제목으로 둔다. */
    private val opened = mutableMapOf<String, String>()
    /** 검색(웹 화면)에서 보고 있는 책 상세. 여기서 읽는 화면으로 넘어가면 이 책을 편 것으로 본다(`바로 읽기` 클릭에는 글자가 없다). */
    private val webBook = mutableMapOf<String, Seen>()
    private var lastWebRead = 0L
    /** 아직 목록에 없는 책의 잘라 둔 표지(검색 화면에서 `바로 읽기`를 누를 때 찍음). 그 책이 들어오면 붙인다. */
    private var pendingCover: Pair<String, ByteArray>? = null
    /** 서재에서 읽는 화면으로 갔다 온 앱(서재의 '최근 읽은 책' 줄로 어떤 책인지 안다) */
    private val readSince = mutableSetOf<String>()
    private var lastShot = 0L
    private val handler = Handler(Looper.getMainLooper())
    /** 화면이 멈추면 다시 읽을 서재 */
    private var settleSpec: ReaderSpec? = null
    private val settleLater = Runnable { settleSpec?.let(::settleShelf) }
    /** 읽는 화면이 잠잠해지면(쪽 넘김·메뉴 열기 뒤) 읽을 앱 */
    private var viewerSpec: ReaderSpec? = null
    private val viewerLater = Runnable { viewerSpec?.let(::readViewer) }


    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val spec = SPECS[e.packageName?.toString()] ?: return
        if (!HomePrefs(this).autoBooks) return
        // 서재는 화면이 바뀌는 동안(칸을 다시 채우는 중, 표지를 불러오는 중, 누른 칸이 반전된 순간) 읽지 않고 멈출 때까지 미룬다.
        if (settleSpec?.pkg == spec.pkg) scheduleSettle(spec)
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // 대화상자 같은 것은 빼고 그 앱의 화면만 기억한다.
                e.className?.toString()?.takeIf { it.startsWith(spec.classPrefix) }?.let {
                    screen[spec.pkg] = it
                    if (ReadingLog.isReaderScreen(it)) {
                        if (spec.recent != null) readSince += spec.pkg
                        webBook.remove(spec.pkg)?.let { book -> bookOpened(spec, book) }
                    }
                }
                readScreen(spec, force = true)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> readScreen(spec, force = false)
            AccessibilityEvent.TYPE_VIEW_CLICKED -> if (screen[spec.pkg] in spec.shelves) {
                val cell = e.source?.let { clickedCell(spec, it) } ?: fromClickText(spec, e.text)
                if (cell != null) bookOpened(spec, cell) else rememberWebBook(spec)
            }
        }
    }

    /** 서재 화면이면 멈춘 뒤에 보이는 책들을 읽고([settleShelf]), 읽는 화면이면 잠잠해진 뒤 진행률·제목을 읽는다([readViewer]). 그 밖의 화면은 읽지 않는다. */
    private fun readScreen(spec: ReaderSpec, force: Boolean) {
        when (screen[spec.pkg]) {
            in spec.shelves -> {
                // 검색 화면의 책 상세는 `바로 읽기`를 누르자마자 사라지므로 멈추기를 기다리지 않고 1초마다 기억해 둔다.
                val now = System.currentTimeMillis()
                if (spec.readNow.isNotEmpty() && (force || now - lastWebRead >= THROTTLE_MS)) {
                    lastWebRead = now
                    root(spec)?.let { root ->
                        readWebBook(spec, root)?.let { webBook[spec.pkg] = it } ?: webBook.remove(spec.pkg)
                    }
                }
                scheduleSettle(spec)
            }
            in spec.viewers -> {
                viewerSpec = spec
                handler.removeCallbacks(viewerLater)
                handler.postDelayed(viewerLater, VIEWER_SETTLE_MS)
            }
        }
    }

    /**
     * 읽는 화면: 본문(웹 화면)은 훑지 않고 이름으로 찾은 요소만 읽는다. 진행률 줄([ReaderSpec.viewerProgress])은 늘 있고,
     * 제목([ReaderSpec.viewerTitle], 교보 읽는 화면)은 가운데를 눌러 메뉴를 띄웠을 때만 보인다. 제목이 보이면 그 책을
     * 편 것으로 보고(서재 칸에 제목이 없는 교보도서관 표지 보기·교보eBook), 진행률을 그 책에 적는다.
     */
    private fun readViewer(spec: ReaderSpec) {
        viewerSpec = null
        if (screen[spec.pkg] !in spec.viewers) return
        val root = root(spec) ?: return
        val progress = spec.viewerProgress?.let { id -> root.byId(spec, id).firstOrNull()?.text?.let(::percent) } ?: -1
        spec.viewerTitle?.let { id -> root.byId(spec, id).firstOrNull()?.text?.toString()?.trim() }?.takeIf { it.isNotEmpty() }?.let { title ->
            val current = opened[spec.pkg]?.let { BookShelf.findByTitle(this, it) }
            if (current == null || BookShelf.findByTitle(this, title)?.id != current.id) {
                bookOpened(spec, Seen(title, "", progress, -1, null, Rect()))
                return
            }
        }
        if (progress < 0) return
        val book = opened[spec.pkg]?.let { BookShelf.findByTitle(this, it) } ?: return
        BookShelf.updateStatus(this, book.id, progress, -1)
    }

    /**
     * 누른 책 칸. 이벤트로 받은 요소는 위(부모)로 올라갈 수 없어서, 누른 곳의 가운데가 들어 있는 칸을 서재 화면에서 찾는다
     * (표지 그림만 눌린 것으로 오기도 한다). 못 찾으면 누른 요소 안에서 읽는다.
     */
    private fun clickedCell(spec: ReaderSpec, source: AccessibilityNodeInfo): Seen? {
        val at = Rect().also { source.getBoundsInScreen(it) }
        val root = root(spec)
        return root?.let { readShelf(spec, it) }?.firstOrNull { it.cell.contains(at.centerX(), at.centerY()) }
            ?: cellOf(spec, source)?.let { readCell(spec, it) }
    }

    /**
     * 검색(웹 화면)의 책 상세: `바로 읽기` 단추와 같은 줄 맨 왼쪽 단추에 왼쪽을 맞춘, 단추 위의 글자들이 제목·저자다.
     * 표지는 그 왼쪽의 그림. 요소 이름이 없는 웹 화면이라 위치로 찾는다.
     */
    private fun readWebBook(spec: ReaderSpec, root: AccessibilityNodeInfo): Seen? {
        val texts = mutableListOf<Pair<String, Rect>>()
        val images = mutableListOf<Rect>()
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            val b = Rect().also { n.getBoundsInScreen(it) }
            n.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { texts += it to b }
            if (n.className?.endsWith("Image") == true) images += b
            if (depth < WEB_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        val button = texts.firstOrNull { it.first in spec.readNow }?.second ?: return null
        val rowLeft = texts.filter { abs(it.second.top - button.top) < ALIGN_PX }.minOf { it.second.left }
        val above = texts.filter { abs(it.second.left - rowLeft) < ALIGN_PX && it.second.bottom <= button.top }.sortedBy { it.second.top }
        val (title, titleAt) = above.firstOrNull() ?: return null
        val author = above.getOrNull(1)?.first?.substringBefore(" 지음")?.trim().orEmpty()
        val cover = images.firstOrNull { it.right <= rowLeft && it.top <= titleAt.bottom && it.bottom >= button.top }
        return Seen(title, author, -1, -1, cover, Rect())
    }

    /**
     * 책 칸이 아닌 것을 눌렀다: 검색 화면의 `바로 읽기`일 수 있으니 지금 보이는 책 상세를 기억하고 표지를 찍어 둔다
     * (웹 화면은 다 그려진 뒤 알림이 없을 때가 있어, 1초마다 기억해 둔 것이 비어 있을 수 있다).
     */
    private fun rememberWebBook(spec: ReaderSpec) {
        if (spec.readNow.isEmpty()) return
        val root = root(spec) ?: return
        val web = readWebBook(spec, root) ?: return
        webBook[spec.pkg] = web
        val book = BookShelf.findByTitle(this, web.title)
        if (web.thumb != null && (book == null || !BookShelf.hasCover(this, book.id))) captureCovers(spec, listOf(web.title to web.thumb))
    }

    /** 서재에 보이는 책 칸들 */
    private fun readShelf(spec: ReaderSpec, root: AccessibilityNodeInfo): List<Seen> =
        root.byId(spec, spec.title).mapNotNull { title -> cellOf(spec, title)?.let { readCell(spec, it) } }

    /**
     * [node] 가 들어 있는 책 칸: 위로 올라가며 제목이 하나뿐인 가장 큰 묶음(그 위는 여러 권을 담은 목록).
     * 표지 그림이 없는 묶음은 책 칸이 아니다(알라딘은 `분야`·`필터` 단추도 같은 이름을 쓴다).
     */
    private fun cellOf(spec: ReaderSpec, node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var cell: AccessibilityNodeInfo? = null
        var n: AccessibilityNodeInfo? = node
        for (i in 0 until CELL_UP) {
            val c = n ?: break
            val titles = c.count(spec, spec.title)
            if (titles > 1) break
            if (titles == 1) cell = c
            n = c.parent
        }
        return cell?.takeIf { it.count(spec, spec.thumb) > 0 }
    }

    /** 책 칸 하나: 칸 아래 요소들을 직접 훑어 이름별 글자를 모은다(누른 칸에서는 이름으로 찾기가 안 된다). */
    private fun readCell(spec: ReaderSpec, cell: AccessibilityNodeInfo): Seen? {
        val texts = mutableMapOf<String, String>()
        var thumb: Rect? = null
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            val id = n.viewIdResourceName?.substringAfter(":id/")
            val t = n.text?.toString()?.trim()
            if (id != null && !t.isNullOrEmpty()) texts.putIfAbsent(id, t)
            if (id == spec.thumb && thumb == null) thumb = Rect().also { n.getBoundsInScreen(it) }
            if (depth < WALK_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(cell, 0)
        fun text(id: String?) = id?.let { texts[it] }.orEmpty()
        val title = text(spec.title).ifEmpty { return null }
        val bounds = Rect().also { cell.getBoundsInScreen(it) }
        return Seen(title, text(spec.author), percent(text(spec.progress)), dueDate(text(spec.due)), thumb, bounds)
    }

    /**
     * 누른 칸을 못 읽었을 때: 클릭 이벤트의 글자 목록(제목이 맨 앞, `%`·반납 문구는 모양으로 가린다).
     * 탭·단추(`다운로드`, `책장` 등)를 책으로 넣지 않도록 진행률(`%`)이 함께 있을 때만 책 칸으로 본다.
     */
    private fun fromClickText(spec: ReaderSpec, list: List<CharSequence>): Seen? {
        val items = list.map { it.toString().trim() }.filter { it.isNotEmpty() }
        val progress = items.firstNotNullOfOrNull { percent(it).takeIf { p -> p >= 0 } } ?: return null
        val title = items.firstOrNull()?.takeIf { percent(it) < 0 && !STATUS_ONLY.matches(it) } ?: return null
        val due = if (spec.due != null) items.firstOrNull { "반납" in it || "만료" in it }?.let(::dueDate) ?: -1 else -1
        val author = if (spec.author != null) items.getOrNull(1)?.takeIf { "%" !in it && "반납" !in it }.orEmpty() else ""
        return Seen(title, author, progress, due, null, Rect())
    }

    /**
     * 책을 폈다: 목록에 있으면 맨 앞으로, 없으면 새로 넣는다. 인터넷 검색은 하지 않고, 표지는 이북 앱 서재에 보이는
     * 그 책의 표지 칸을 화면에서 잘라 온다(안 되면 빈 표지).
     */
    private fun bookOpened(spec: ReaderSpec, seen: Seen) {
        webBook.remove(spec.pkg)
        val book = BookShelf.findByTitle(this, seen.title)?.also {
            BookShelf.moveToFront(this, it.id)
            BookShelf.updateStatus(this, it.id, seen.progress, seen.due)
        } ?: run {
            val app = AppStore.load(this).firstOrNull { it.pkg == spec.pkg }?.key
            BookShelf.addOpened(this, autoBookId(seen.title), seen.title, seen.author, app, seen.progress, seen.due)
        }
        opened[spec.pkg] = book.title
        // 검색 화면에서 `바로 읽기`를 누를 때 잘라 둔 표지
        pendingCover?.takeIf { sameTitle(it.first, book.title) }?.let { (_, bytes) ->
            pendingCover = null
            if (!BookShelf.hasCover(this, book.id)) runCatching { BookShelf.setCover(this, book.id, bytes) }
        }
        // 그 밖의 표지는 지금(누른 칸이 반전돼 있거나 화면이 넘어가는 중) 찍지 않고, 서재로 돌아와 화면이 멈췄을 때 잘라 온다.
    }

    private fun scheduleSettle(spec: ReaderSpec) {
        settleSpec = spec
        handler.removeCallbacks(settleLater)
        handler.postDelayed(settleLater, SETTLE_MS)
    }

    /**
     * 서재 화면이 [SETTLE_MS] 동안 그대로면: 보이는 책들의 진행률·반납일을 적고, 표지 없는 책은 표지 칸을 찍어 잘라 온다.
     * (밀리는 앱을 다시 열 때 잠깐 모든 칸을 한 책·0% 로 채웠다가 고친다. 그 사이 값을 적지 않도록 멈춘 뒤에 읽는다.)
     */
    private fun settleShelf(spec: ReaderSpec) {
        settleSpec = null
        if (screen[spec.pkg] !in spec.shelves) return
        val root = root(spec) ?: return
        val seen = readShelf(spec, root)
        // 책 칸을 눌러도 알림이 오지 않는 앱(알라딘): 읽는 화면에서 돌아오면 서재 아래 '최근 읽은 책'이 방금 편 책이다.
        spec.recent?.takeIf { spec.pkg in readSince }?.let { id ->
            val title = root.byId(spec, id).firstOrNull()?.text?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@let
            readSince -= spec.pkg
            bookOpened(spec, seen.firstOrNull { sameTitle(it.title, title) } ?: Seen(title, "", -1, -1, null, Rect()))
        }
        val targets = mutableListOf<Pair<String, Rect>>()
        // 검색 화면의 책 상세에 보이는 표지(목록에 있는데 표지가 없으면)
        if (spec.readNow.isNotEmpty()) {
            readWebBook(spec, root)?.let { web ->
                webBook[spec.pkg] = web
                val book = BookShelf.findByTitle(this, web.title)
                if (book != null && web.thumb != null && !BookShelf.hasCover(this, book.id)) targets += web.title to web.thumb
            }
        }
        for (s in seen) {
            val book = BookShelf.findByTitle(this, s.title) ?: continue
            BookShelf.updateStatus(this, book.id, s.progress, s.due)
            if (s.thumb != null && !BookShelf.hasCover(this, book.id)) targets += s.title to s.thumb
        }
        captureCovers(spec, targets)
    }

    /**
     * 화면을 한 번 찍어 책(제목)마다 표지 칸([Rect], 화면 좌표)을 잘라 표지로 저장한다. 아직 목록에 없는 책이면
     * [pendingCover] 로 두었다가 그 책이 들어올 때 붙인다. 안드로이드 11 부터 되고,
     * 1초에 한 번만 찍을 수 있으므로 너무 이르면 조금 뒤에 다시 한다. 한 가지 색뿐인 칸(그림을 불러오기 전)은 버린다.
     */
    private fun captureCovers(spec: ReaderSpec, targets: List<Pair<String, Rect>>) {
        if (targets.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val now = System.currentTimeMillis()
        if (now - lastShot < SHOT_INTERVAL_MS) { scheduleSettle(spec); return }
        lastShot = now
        val context = applicationContext
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val shot = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                    ?.copy(Bitmap.Config.ARGB_8888, false)
                result.hardwareBuffer.close()
                shot ?: return
                Bg.run({
                    try {
                        targets.mapNotNull { (title, rect) ->
                            val r = Rect(rect).apply { intersect(0, 0, shot.width, shot.height) }
                            if (r.width() < MIN_THUMB_PX || r.height() < MIN_THUMB_PX) return@mapNotNull null
                            val crop = trimEdges(Bitmap.createBitmap(shot, r.left, r.top, r.width(), r.height()))
                            if (isFlat(crop)) return@mapNotNull null
                            title to ByteArrayOutputStream().also { crop.compress(Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()
                        }
                    } finally {
                        shot.recycle()
                    }
                }) { result ->
                    for ((title, bytes) in result.getOrNull().orEmpty()) {
                        val book = BookShelf.findByTitle(context, title)
                        if (book == null) { pendingCover = title to bytes; continue }
                        // 한 권이 실패해도(저장 공간 등) 나머지는 넣는다. 못 넣은 책은 다음에 서재를 볼 때 다시 한다.
                        if (!BookShelf.hasCover(context, book.id)) runCatching { BookShelf.setCover(context, book.id, bytes) }
                    }
                }
            }

            override fun onFailure(errorCode: Int) {}
        })
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(settleLater)
        handler.removeCallbacks(viewerLater)
        super.onDestroy()
    }

    /** 지금 앞에 있는 그 앱의 화면(다른 앱·시스템 창이면 null) */
    private fun root(spec: ReaderSpec): AccessibilityNodeInfo? = rootInActiveWindow?.takeIf { it.packageName == spec.pkg }

    private fun AccessibilityNodeInfo.byId(spec: ReaderSpec, id: String): List<AccessibilityNodeInfo> =
        findAccessibilityNodeInfosByViewId("${spec.pkg}:id/$id")

    /** 아래에 이름이 [id] 인 요소가 몇 개인가. 이벤트로 받은 요소에서는 이름으로 찾기가 안 되어 직접 훑는다. */
    private fun AccessibilityNodeInfo.count(spec: ReaderSpec, id: String): Int {
        val full = "${spec.pkg}:id/$id"
        fun walk(n: AccessibilityNodeInfo, depth: Int): Int {
            var c = if (n.viewIdResourceName == full) 1 else 0
            if (depth < WALK_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { c += walk(it, depth + 1) }
            return c
        }
        return walk(this, 0)
    }

    /** 서재 칸 한 권에서 읽은 것. [cell] 은 칸의 화면 위치 */
    private class Seen(val title: String, val author: String, val progress: Int, val due: Long, val thumb: Rect?, val cell: Rect)

    /** 앱마다 화면 요소 이름(앱이 업데이트돼 바뀌면 여기만 고친다) */
    private class ReaderSpec(
        val pkg: String,
        val classPrefix: String,
        /** 책 칸이 보이는 화면들(서재·탭·책장 안) */
        val shelves: Set<String>,
        val viewers: Set<String>,
        val title: String,
        /** 서재 칸의 표지 그림 */
        val thumb: String,
        val author: String?,
        val progress: String?,
        val due: String?,
        val viewerProgress: String?,
        /** 읽는 화면 메뉴의 책 제목(메뉴를 띄웠을 때만 보인다) */
        val viewerTitle: String? = null,
        /** 서재 화면의 '최근 읽은 책' 제목(책 칸을 눌러도 알림이 오지 않는 앱) */
        val recent: String? = null,
        /** 검색(웹 화면) 책 상세의 읽기 단추 글자 */
        val readNow: Set<String> = emptySet(),
    )

    companion object {
        private const val THROTTLE_MS = 1000L
        /** 읽는 화면이 이만큼 잠잠하면 진행률·제목을 읽는다 */
        private const val VIEWER_SETTLE_MS = 700L
        private const val SHOT_INTERVAL_MS = 1100L
        /** 서재 화면이 이만큼 그대로여야 표지를 찍는다 */
        private const val SETTLE_MS = 1500L
        /** 책 칸을 찾을 때 위로 올라가는 단계, 칸 안을 훑는 깊이 */
        private const val CELL_UP = 8
        private const val WALK_DEPTH = 12
        /** 웹 화면(검색 책 상세)을 훑는 깊이, 같은 줄·같은 왼쪽으로 보는 차이(px) */
        private const val WEB_DEPTH = 40
        private const val ALIGN_PX = 8
        /** 이보다 작게 보이는 표지 칸은 자르지 않는다(가려졌거나 화면 밖) */
        private const val MIN_THUMB_PX = 40

        private val SPECS = listOf(
            ReaderSpec(
                pkg = "kr.co.kyobobook.KEL", classPrefix = "com.kyobo",
                shelves = setOf("com.kyobo.ebook.kel.ui.main.MainActivity"),
                // 읽는 화면은 본문이 웹 화면이라 훑지 않고, 아래 진행률 줄과 메뉴의 제목만 이름으로 읽는다.
                viewers = setOf(
                    "com.kyobo.ebook.kel.viewer.epub.B2BViewerEpubMainActivity",
                    "com.kyobo.ebook.kel.viewer.pdf.B2BViewerPdfMainActivity",
                ),
                title = "tvTitle", thumb = "ivThumbnail", author = "tvAuthor", progress = "tvReadPercentageTxt", due = "tvRemainDate",
                viewerProgress = "bookIndicator", viewerTitle = "viewer_top_booktitle",
            ),
            // 교보eBook: 서재(Compose)에는 요소 이름이 없고 격자 보기에는 제목도 없어 서재는 읽지 않는다.
            // 교보도서관과 같은 읽는 화면이라 진행률 줄과 메뉴의 제목으로 안다(서재·내 책장 어디서 열어도).
            ReaderSpec(
                pkg = "com.kyobo.ebook.eink", classPrefix = "com.kyobo",
                shelves = emptySet(),
                viewers = setOf(
                    "com.kyobo.ebook.common.b2c.viewer.epub.ViewerEpubMainActivity",
                    "com.kyobo.ebook.common.b2c.viewer.pdf.ViewerPdfMainActivity",
                    "com.kyobo.ebook.common.b2c.viewer.comic.ViewerComicMainActivity",
                ),
                // 서재(shelves)를 읽지 않으므로 칸 요소 이름(title·thumb)은 쓰이지 않는다.
                title = "", thumb = "", author = null, progress = null, due = null,
                viewerProgress = "bookIndicator", viewerTitle = "viewer_top_booktitle",
            ),
            ReaderSpec(
                pkg = "kr.co.millie.eink", classPrefix = "kr.co.millie",
                // 다운로드·전체도서 탭은 BookshelfActivity, 책장 안(읽고있는 책 등)은 BookShelfDetailActivity
                shelves = setOf("kr.co.millie.eink.bookshelf.BookshelfActivity", "kr.co.millie.eink.bookshelf.BookShelfDetailActivity"),
                viewers = setOf(
                    "kr.co.millie.eink.epub.EPubViewActivity",
                    "kr.co.millie.eink.pdf.PDFViewActivity",
                    "kr.co.millie.eink.epub.StoryViewActivity",
                ),
                title = "tv_book_title", thumb = "iv_thumbnail", author = null, progress = "bookshelf_cell_reading", due = null,
                viewerProgress = "tv_viewer_add_on_right",
                readNow = setOf("바로 읽기", "이어 읽기"),
            ),
            // 알라딘: 서재 칸에 제목·표지만 있다(진행률 없음). 책 칸을 눌러도 알림이 없어 서재 아래 '최근 읽은 책'으로 안다.
            ReaderSpec(
                pkg = "kr.co.aladin.ebook", classPrefix = "kr.co.aladin",
                shelves = setOf("kr.co.aladin.ebook.MainActivity"),
                viewers = emptySet(),
                title = "txt_title", thumb = "img_cover", author = null, progress = null, due = null,
                viewerProgress = null, recent = "reading_book_tv_book_title",
            ),
            // 북커스: 내서재 칸에 제목·표지·진행률·대여 만료.
            ReaderSpec(
                pkg = "com.bookers.ebook", classPrefix = "com.bookers",
                shelves = setOf("com.bookers.ebook.ui.purchase.PurchaseActivity"),
                viewers = emptySet(),
                title = "tv_title", thumb = "iv_cover", author = null, progress = "tv_percent", due = "tv_end_date",
                viewerProgress = null,
            ),
        ).associateBy { it.pkg }

        /** 거의 한 가지 색뿐인 그림(아직 그려지지 않은 표지 칸)인가. 16×16 점의 밝기 차로 본다. */
        private fun isFlat(b: Bitmap): Boolean {
            var min = 255
            var max = 0
            for (y in 0 until 16) for (x in 0 until 16) {
                val l = luma(b.getPixel((x * 2 + 1) * b.width / 32, (y * 2 + 1) * b.height / 32))
                if (l < min) min = l
                if (l > max) max = l
            }
            return max - min < FLAT_RANGE
        }

        private const val FLAT_RANGE = 40

        /** 색 하나의 밝기(0~255) */
        private fun luma(c: Int) = (299 * (c shr 16 and 0xFF) + 587 * (c shr 8 and 0xFF) + 114 * (c and 0xFF)) / 1000

        /**
         * 표지 칸 가장자리의 테두리 선과 여백(밀리는 칸에 1px 선을 두르고 표지를 가운데 맞춰 양옆이 빈다)을 걷어 낸다.
         * 바깥 2px 안의 한 가지 색 줄(테두리 선)과 흰 줄(여백)만 지운다. 표지 바탕이 한 가지 색(빨간 표지 등)이어도
         * 깎지 않도록 그 밖의 줄은 두고, 한쪽에 [MAX_TRIM] 까지만.
         */
        private fun trimEdges(b: Bitmap): Bitmap {
            /** [depth]: 이 줄이 가장자리에서 몇 번째인가 */
            fun trimmable(px: IntArray, depth: Int): Boolean {
                var min = 255
                var max = 0
                for (c in px) {
                    val l = luma(c)
                    if (l < min) min = l
                    if (l > max) max = l
                }
                return max - min < FLAT_RANGE && (depth < FRAME_PX || min >= WHITE)
            }
            val w = b.width
            val h = b.height
            val maxX = (w * MAX_TRIM).toInt()
            val maxY = (h * MAX_TRIM).toInt()
            var left = 0
            var right = w
            var top = 0
            var bottom = h
            // 지금 남은 범위 안에서만 본다(테두리 선을 걷어야 그 안쪽 여백 줄이 한 가지 색이 된다).
            fun row(y: Int, depth: Int) =
                trimmable(IntArray(right - left).also { b.getPixels(it, 0, right - left, left, y, right - left, 1) }, depth)
            fun col(x: Int, depth: Int) =
                trimmable(IntArray(bottom - top).also { b.getPixels(it, 0, 1, x, top, 1, bottom - top) }, depth)
            do {
                var changed = false
                if (left < maxX && col(left, left)) { left++; changed = true }
                if (w - right < maxX && col(right - 1, w - right)) { right--; changed = true }
                if (top < maxY && row(top, top)) { top++; changed = true }
                if (h - bottom < maxY && row(bottom - 1, h - bottom)) { bottom--; changed = true }
            } while (changed)
            if (left == 0 && top == 0 && right == w && bottom == h) return b
            return Bitmap.createBitmap(b, left, top, right - left, bottom - top)
        }

        private const val MAX_TRIM = 0.08f
        /** 테두리 선으로 보는 가장자리 두께 */
        private const val FRAME_PX = 2
        /** 여백으로 보는 밝기 */
        private const val WHITE = 230

        /** 제목이 아니라 상태 글자(`4일`, `반납 4일 남음`, `D-3`, `만료`) — 교보도서관 표지 보기 칸은 제목 없이 이것만 있다 */
        private val STATUS_ONLY = Regex("""^(반납\s*)?(\d+\s*일(\s*남음)?|D-?\d+|만료|오늘 반납)$""")

        private fun percent(text: CharSequence): Int =
            Regex("""(\d{1,3})\s*%""").find(text)?.groupValues?.get(1)?.toInt()?.coerceIn(0, 100) ?: -1

        /** `2026.10.20` → 그날, `반납 4일 남음` → 오늘 + 4일, `오늘 반납` → 오늘, `만료` → 어제(지남). 모르면 -1 */
        private fun dueDate(text: String): Long {
            if (text.isEmpty()) return -1
            Regex("""(\d{4})\s*[.\-/년]\s*(\d{1,2})\s*[.\-/월]\s*(\d{1,2})""").find(text)?.let { m ->
                val (y, mo, d) = m.destructured
                runCatching { return LocalDate.of(y.toInt(), mo.toInt(), d.toInt()).toEpochDay() }
            }
            val today = LocalDate.now().toEpochDay()
            Regex("""(\d+)\s*일""").find(text)?.let { return today + it.groupValues[1].toLong() }
            if ("만료" in text) return today - 1
            if ("오늘" in text) return today
            return -1
        }

        /** 시스템 접근성 설정에서 이 서비스가 켜져 있는가 */
        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, ReaderWatchService::class.java)
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
