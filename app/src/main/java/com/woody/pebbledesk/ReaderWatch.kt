package com.woody.pebbledesk

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import java.io.ByteArrayOutputStream
import android.view.Display
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.graphics.Rect
import android.graphics.Bitmap
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.woody.pebbledesk.readers.AppWindows
import com.woody.pebbledesk.readers.Cell
import com.woody.pebbledesk.readers.ReaderApps
import com.woody.pebbledesk.readers.ReaderSpec
import com.woody.pebbledesk.readers.usableThumb
import java.time.LocalDate

/**
 * 읽고 있는 책 자동 추가(접근성 서비스). 이북 앱([ReaderApps], 앱마다 화면 이름·요소 이름은 `readers/` 의 앱 파일)의 **서재 화면**에 보이는 제목·저자·진행률·반납일과
 * 사용자가 누른 책 칸만 읽는다. 다른 앱은 시스템이 아예 보내지 않고(`res/xml/reader_watch.xml` 의 packageNames),
 * 읽는 화면은 본문(웹 화면)을 훑지 않고 진행률 줄·메뉴 제목만 이름으로 읽는다. 읽은 내용은 책 목록에만 적고 밖으로 보내지 않는다.
 */
class ReaderWatchService : AccessibilityService() {
    /** 앱마다 지금 앞에 있는 화면(액티비티 이름) */
    private val screen = mutableMapOf<String, String>()
    /**
     * 앱마다 마지막으로 편 책의 제목(읽는 화면의 진행률을 붙일 곳). 표지 다시 찾기로 id 가 바뀌어도 찾도록 제목으로 둔다.
     * 서비스가 다시 시작돼도(앱 업데이트·재부팅) 읽는 화면으로 바로 돌아간 책의 진행률을 적도록 저장해 둔다.
     */
    private val openedPrefs by lazy { Storage.of(this).getSharedPreferences("reader_watch", MODE_PRIVATE) }
    private fun opened(pkg: String) = openedPrefs.getString("opened_$pkg", null)
    /** [title] 이 null 이면 지운다(어떤 책을 폈는지 모른다) */
    private fun setOpened(pkg: String, title: String?) {
        if (opened(pkg) != title) openedPrefs.edit().putString("opened_$pkg", title).apply()
    }
    /** 검색(웹 화면)에서 보고 있는 책 상세. 여기서 읽는 화면으로 넘어가면 이 책을 편 것으로 본다(`바로 읽기` 클릭에는 글자가 없다). */
    private val webBook = mutableMapOf<String, Cell>()
    /** 눌렀지만 아직 읽는 화면이 열리지 않은 책 칸과 누른 때([ReaderSpec.openOnViewer] 인 앱) */
    private val clicked = mutableMapOf<String, Pair<Cell, Long>>()
    /** 서재에서 책 칸으로 알아보지 못한 것을 누른 때(문리더 홈 탭·교보도서관 표지 보기의 표지만 있는 칸은 눌러도 제목이 오지 않는다) */
    private val unknownClick = mutableMapOf<String, Long>()
    /**
     * 그렇게 누른 뒤 읽는 화면이 열려 어떤 책인지 모르는 앱과 열린 때. 앞서 편 책과 다른 책일 수 있어 마지막으로 편 책을 지워 두고
     * (진행률을 적지 않는다. 서비스가 다시 시작돼도), 읽는 화면 메뉴의 제목으로 알면 열린 때부터 그 책으로 센다.
     */
    private val unknownOpen = mutableMapOf<String, Long>()
    private var lastWebRead = 0L
    /**
     * 아직 목록에 없는 책의 잘라 둔 표지(정리한 제목 → JPEG). 검색 화면에서 `바로 읽기`를 누를 때, 알라딘 서재가 잠깐 보일 때
     * 찍어 두었다가 그 책이 들어오면 붙인다. 최근 [PENDING_MAX]권만.
     */
    private val pendingCovers = object : LinkedHashMap<String, ByteArray>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?) = size > PENDING_MAX
    }
    private var lastEagerRead = 0L
    /** 그 앱의 화면이 마지막으로 바뀐 때 */
    private var lastTransition = 0L
    /** 앱마다 마지막으로 읽은 서재 칸들(누른 순간 화면이 이미 넘어가 칸을 못 읽을 때 누른 글자와 맞춰 본다) */
    private val lastShelf = mutableMapOf<String, List<Cell>>()
    /** 이번 서재 방문에서 책(정리한 제목)마다 바로 자르기를 해 본 횟수. 그 앱 화면이 새로 열릴 때마다 비운다. */
    private val eagerTries = mutableMapOf<String, Int>()
    /** 서재에서 읽는 화면으로 갔다 온 앱(서재의 '최근 읽은 책' 줄로 어떤 책인지 안다) */
    private val readSince = mutableSetOf<String>()
    /** [readSince] 앱마다 서재를 떠나 읽는 화면이 처음 열린 때(돌아와서야 어떤 책인지 아는 앱의 읽은 시간을 그 책으로 세게) */
    private val readerAt = mutableMapOf<String, Long>()
    /** [readSince] 앱 가운데 읽는 화면 메뉴로 이미 책을 안 앱('최근 읽은 책' 줄은 늦게 바뀌어 앞서 읽은 책이 보이기도 해 다시 보지 않는다) */
    private val readKnown = mutableSetOf<String>()
    private var lastShot = 0L
    private val handler = Handler(Looper.getMainLooper())
    /** 화면이 멈추면 다시 읽을 서재 */
    private var settleSpec: ReaderSpec? = null
    private val settleLater = Runnable { settleSpec?.let(::settleShelf) }
    /** 읽는 화면이 잠잠해지면(쪽 넘김·메뉴 열기 뒤) 읽을 앱 */
    private var viewerSpec: ReaderSpec? = null
    /** 진행률을 아직 못 읽어 다시 읽어 볼 횟수(메뉴의 쪽 정보는 조금 늦게 채워지고, 채워질 때 알림이 없다) */
    private var viewerRetries = 0
    private val viewerLater = Runnable { viewerSpec?.let(::readViewer) }


    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val spec = ReaderApps.byPackage[e.packageName?.toString()] ?: return
        WatchLog.event(this, spec.pkg)
        // 서재는 화면이 바뀌는 동안(칸을 다시 채우는 중, 표지를 불러오는 중, 누른 칸이 반전된 순간) 읽지 않고 멈출 때까지 미룬다.
        if (settleSpec?.pkg == spec.pkg) scheduleSettle(spec)
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // 대화상자 같은 것은 빼고 그 앱의 화면만 기억한다(문리더 목차 `PrefChapters` 처럼 앱 안의 대화상자도 이름이 앱 것이라 액티비티인지 본다).
                val cls = e.className?.toString()
                val isScreen = cls != null && cls.startsWith(spec.classPrefix) && isActivity(spec.pkg, cls)
                WatchLog.add(this, if (isScreen) WatchLog.Kind.SCREEN else WatchLog.Kind.OTHER_WINDOW, spec.pkg, cls.orEmpty().substringAfterLast('.'),
                    if (!isScreen) "" else when (cls) {
                        in spec.shelves -> WatchLog.Role.SHELF.name
                        in spec.viewers -> WatchLog.Role.VIEWER.name
                        else -> WatchLog.Role.OTHER.name
                    })
                cls?.takeIf { isScreen }?.let {
                    // 화면이 새로 열릴 때마다(홈에서 돌아온 서재 포함) 바로 자르기 횟수를 다시 센다.
                    eagerTries.clear()
                    val now = System.currentTimeMillis()
                    lastTransition = now
                    screen[spec.pkg] = it
                    if (ReadingLog.isReaderScreen(it)) {
                        // 서재를 떠나 처음 연 읽는 화면의 때만 적는다(읽는 중 목차 같은 다른 화면에 갔다 와도 처음부터 그 책으로 세게).
                        if (spec.recent != null && readSince.add(spec.pkg)) readerAt[spec.pkg] = now
                        unknownClick.remove(spec.pkg)?.takeIf { now - it < CLICK_OPEN_MS }?.let {
                            unknownOpen[spec.pkg] = now
                            setOpened(spec.pkg, null)
                        }
                        webBook.remove(spec.pkg)?.let { book -> bookOpened(spec, book) }
                        clicked.remove(spec.pkg)?.takeIf { now - it.second < CLICK_OPEN_MS }?.let { bookOpened(spec, it.first) }
                    }
                }
                readScreen(spec, force = true)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> readScreen(spec, force = false)
            AccessibilityEvent.TYPE_VIEW_CLICKED -> if (screen[spec.pkg] in spec.shelves) {
                val cell = spec.clickedCell(e.source, e.text, windowsOf(spec), lastShelf[spec.pkg])?.let { c ->
                    // 방금 읽어 둔 칸으로 맞춘 것은 그 뒤 읽는 화면에서 진행률이 바뀌었을 수 있어 목록에 이미 있는 책에는 쓰지 않는다(반납일은 그대로).
                    if (c.stale && BookShelf.findByTitle(this, c.title) != null) c.copy(progress = -1) else c
                }
                WatchLog.add(this, WatchLog.Kind.CLICK, spec.pkg, cell?.title.orEmpty())
                when {
                    cell == null -> {
                        // 표지만 있는 칸일 수 있다(누르자마자 읽는 화면으로 넘어가 누른 요소도 읽을 수 없다). 메뉴에 제목이 없는 앱은 알 길이 없어 두지 않는다.
                        if (spec.tellsTitle) unknownClick[spec.pkg] = System.currentTimeMillis()
                        rememberWebBook(spec)
                    }
                    // 누르면 내려받기·기간 만료 안내가 먼저 뜨는 앱은 읽는 화면이 열릴 때 편 것으로 본다. 기간이 지난 책은 열리지 않는다.
                    spec.openOnViewer -> if (cell.due in 0 until LocalDate.now().toEpochDay()) clicked.remove(spec.pkg)
                        else clicked[spec.pkg] = cell to System.currentTimeMillis()
                    else -> bookOpened(spec, cell)
                }
            }
        }
    }

    /** 서재 화면이면 멈춘 뒤에 보이는 책들을 읽고([settleShelf]), 읽는 화면이면 잠잠해진 뒤 진행률·제목을 읽는다([readViewer]). 그 밖의 화면은 읽지 않는다. */
    private fun readScreen(spec: ReaderSpec, force: Boolean) {
        when (screen[spec.pkg]) {
            in spec.shelves -> {
                // 검색 화면의 책 상세는 `바로 읽기`를 누르자마자 사라지므로 멈추기를 기다리지 않고 1초마다 기억해 둔다.
                val now = System.currentTimeMillis()
                // 아래 둘이 같이 쓴다(그 앱의 화면 트리는 필요할 때 한 번만 가져온다).
                val shelfRoot by lazy { root(spec) }
                if (force || now - lastWebRead >= THROTTLE_MS) {
                    lastWebRead = now
                    shelfRoot?.let { root -> spec.webBook(root)?.let { webBook[spec.pkg] = it } ?: webBook.remove(spec.pkg) }
                }
                // 서재를 잠깐만 보고 책으로 넘어가는 앱(알라딘·북커스·리디·YES24 전자도서관)은 멈추기를 기다리지 않고 표지가 보이는 대로 잘라 둔다.
                // 화면이 막 바뀐 때(읽는 화면에서 돌아오는 중이면 화면 캡처가 막힌 읽는 화면이 검게 찍힌다)와 아직 찍을 수 없을 때는 건너뛴다.
                val ready = now - lastTransition >= TRANSITION_MS && now - lastShot >= SHOT_INTERVAL_MS
                if (spec.eagerCovers && ready && (force || now - lastEagerRead >= THROTTLE_MS)) {
                    lastEagerRead = now
                    shelfRoot?.let { root ->
                        captureCovers(spec, readShelf(spec, root).distinctBy { it.title }.mapNotNull { s ->
                            val thumb = s.thumb?.takeIf(::usableThumb) ?: return@mapNotNull null
                            val book = BookShelf.findByTitle(this, s.title)
                            // 목록에 없는 책은 들어올 때 붙일 표지를 미리 찍어 둔다(목록이 다 찼으면 들어올 수 없어 찍지 않는다).
                            val need = if (book == null) BookShelf.canAutoAdd(this, s.title) && pendingFor(s.title) == null else !BookShelf.hasCover(this, book.id)
                            if (need && tryCover(s.title)) s.title to thumb else null
                        }, retry = false)
                    }
                }
                scheduleSettle(spec)
            }
            in spec.viewers -> {
                viewerSpec = spec
                viewerRetries = VIEWER_RETRIES
                handler.removeCallbacks(viewerLater)
                handler.postDelayed(viewerLater, VIEWER_SETTLE_MS)
            }
        }
    }

    /**
     * 읽는 화면: 진행률·제목은 앱이 읽는다([ReaderSpec.readViewer]). 진행률이 아직 비어 있으면 조금 뒤 다시 읽는다(메뉴의 쪽 정보는 늦게
     * 채워지고 그때 알림이 없다). 제목이 보이면 그 책을 편 것으로 보고(서재 칸으로는 알 수 없는 교보도서관 표지 보기·교보eBook·알라딘 격자 보기·
     * 리디·문리더 홈 탭), 진행률을 그 책에 적는다.
     */
    private fun readViewer(spec: ReaderSpec) {
        viewerSpec = null
        if (screen[spec.pkg] !in spec.viewers) return
        val windows = windowsOf(spec).takeIf { it.all.isNotEmpty() } ?: return logNoWindow(spec)
        val info = spec.readViewer(windows)
        val progress = info.progress
        if (progress >= 0 || info.title != null) WatchLog.add(this, WatchLog.Kind.PROGRESS, spec.pkg, "$progress", info.title.orEmpty())
        if (progress < 0 && viewerRetries-- > 0) {
            viewerSpec = spec
            handler.postDelayed(viewerLater, VIEWER_RETRY_MS)
        }
        info.title?.let { title ->
            if (spec.pkg in readSince) readKnown += spec.pkg
            val current = opened(spec.pkg)?.let { BookShelf.findByTitle(this, it) }
            if (current == null || BookShelf.findByTitle(this, title)?.id != current.id) {
                bookOpened(spec, Cell(title, info.author, progress, -1, null, Rect()), unknownOpen[spec.pkg])
                return
            }
        }
        if (progress < 0) return
        val book = opened(spec.pkg)?.let { BookShelf.findByTitle(this, it) } ?: return
        BookShelf.updateStatus(this, book.id, progress, -1)
    }

    /**
     * 책 칸이 아닌 것을 눌렀다: 검색 화면의 `바로 읽기`일 수 있으니 지금 보이는 책 상세를 기억하고 표지를 찍어 둔다
     * (웹 화면은 다 그려진 뒤 알림이 없을 때가 있어, 1초마다 기억해 둔 것이 비어 있을 수 있다).
     */
    private fun rememberWebBook(spec: ReaderSpec) {
        val root = root(spec) ?: return
        val web = spec.webBook(root) ?: return
        webBook[spec.pkg] = web
        val book = BookShelf.findByTitle(this, web.title)
        if (web.thumb != null && (book == null || !BookShelf.hasCover(this, book.id))) captureCovers(spec, listOf(web.title to web.thumb))
    }

    /** 찍어도 버려지는 칸(한 가지 색 표지 등)을 계속 찍지 않도록 서재를 볼 때마다 책마다 [EAGER_TRIES]번만 자른다. 더 해 볼 수 있으면 센다. */
    private fun tryCover(title: String): Boolean {
        val key = normTitle(title)
        val tries = eagerTries.getOrDefault(key, 0)
        if (tries >= EAGER_TRIES) return false
        eagerTries[key] = tries + 1
        return true
    }

    /** 잘라 둔 표지 가운데 [title] 의 것(부제·공백 무시)의 열쇠 */
    private fun pendingFor(title: String): String? {
        val n = normTitle(title)
        return pendingCovers.keys.firstOrNull { sameTitle(it, n) }
    }

    /** 서재에 보이는 책 칸들(읽은 것은 [lastShelf] 에 남긴다) */
    private fun readShelf(spec: ReaderSpec, root: AccessibilityNodeInfo): List<Cell> =
        spec.shelfCells(root).also { if (it.isNotEmpty()) lastShelf[spec.pkg] = it }

    /**
     * 책을 폈다: 목록에 있으면 맨 앞으로, 없으면 새로 넣는다(목록이 다 찼으면 넣지 않는다). 인터넷 검색은 하지 않고,
     * 표지는 이북 앱 서재에 보이는 그 책의 표지 칸을 화면에서 잘라 온다(안 되면 빈 표지).
     * [at] 은 그 책을 편 때(서재로 돌아와서야 알면 읽는 화면이 열린 때). 읽은 시간을 이때부터 이 책으로 센다.
     */
    private fun bookOpened(spec: ReaderSpec, seen: Cell, at: Long? = null) {
        webBook.remove(spec.pkg)
        unknownClick -= spec.pkg
        unknownOpen -= spec.pkg
        var result = WatchLog.Opened.FRONT
        val book = BookShelf.findByTitle(this, seen.title)?.also {
            BookShelf.moveToFront(this, it.id)
            BookShelf.updateStatus(this, it.id, seen.progress, seen.due, seen.author)
        } ?: if (!BookShelf.canAutoAdd(this, seen.title)) {
            result = if (BookShelf.isFull(this)) WatchLog.Opened.FULL else WatchLog.Opened.FINISHED
            null
        } else {
            // 다 찼으면 앱 목록(시스템 호출)도 보지 않는다. 다 찬 동안 목록에 없는 책을 읽으면 화면 이벤트마다 여기 온다.
            val app = AppStore.load(this).firstOrNull { it.pkg == spec.pkg }?.key
            result = WatchLog.Opened.NEW
            BookShelf.addOpened(this, seen.title, seen.author, app, seen.progress, seen.due)
        }
        WatchLog.add(this, WatchLog.Kind.OPENED, spec.pkg, seen.title, result.name)
        // 넣지 못한 책도 연 책으로 적는다(그래야 읽는 화면의 진행률이 앞서 연 다른 책에 붙지 않는다).
        setOpened(spec.pkg, book?.title ?: seen.title)
        ReadingLog.bookOpened(this, spec.pkg, book?.title ?: seen.title, at ?: System.currentTimeMillis())
        if (book == null) return
        // 미리 잘라 둔 표지(검색 화면의 `바로 읽기`, 알라딘 서재)
        pendingFor(book.title)?.let { key ->
            val bytes = pendingCovers.remove(key) ?: return@let
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
        val root = root(spec) ?: return logNoWindow(spec)
        val seen = readShelf(spec, root)
        WatchLog.add(this, WatchLog.Kind.SHELF, spec.pkg, "${seen.size}")
        // 책 칸을 눌러도 알림이 오지 않는 앱(알라딘): 읽는 화면에서 돌아오면 서재 아래 '최근 읽은 책'이 방금 편 책이다.
        if (spec.pkg in readSince) {
            if (readKnown.remove(spec.pkg)) readSince -= spec.pkg
            else spec.recentTitle(root)?.let { title ->
                readSince -= spec.pkg
                bookOpened(spec, seen.firstOrNull { sameTitle(it.title, title) } ?: Cell(title, "", -1, -1, null, Rect()), readerAt[spec.pkg])
            }
        }
        val targets = mutableListOf<Pair<String, Rect>>()
        // 검색 화면의 책 상세에 보이는 표지(목록에 있는데 표지가 없으면)
        spec.webBook(root)?.let { web ->
            webBook[spec.pkg] = web
            val book = BookShelf.findByTitle(this, web.title)
            if (book != null && web.thumb != null && !BookShelf.hasCover(this, book.id)) targets += web.title to web.thumb
        }
        for (s in seen) {
            val book = BookShelf.findByTitle(this, s.title) ?: continue
            BookShelf.updateStatus(this, book.id, s.progress, s.due, s.author)
            if (s.thumb != null && !BookShelf.hasCover(this, book.id)) targets += s.title to s.thumb
        }
        if (!spec.secure) captureCovers(spec, targets)
    }

    /**
     * 화면을 한 번 찍어 책(제목)마다 표지 칸([Rect], 화면 좌표)을 잘라 표지로 저장한다. 아직 목록에 없는 책이면
     * [pendingCovers] 에 두었다가 그 책이 들어올 때 붙인다. 안드로이드 11 부터 되고,
     * 1초에 한 번만 찍을 수 있으므로 너무 이르면 조금 뒤에 다시 한다. 한 가지 색뿐인 칸(그림을 불러오기 전)은 버린다.
     */
    private fun captureCovers(spec: ReaderSpec, targets: List<Pair<String, Rect>>, retry: Boolean = true) {
        if (targets.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val now = System.currentTimeMillis()
        if (now - lastShot < SHOT_INTERVAL_MS) { if (retry) scheduleSettle(spec); return }
        lastShot = now
        val context = applicationContext
        val wanted = targets.distinctBy { it.first }
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val shot = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                    ?.copy(Bitmap.Config.ARGB_8888, false)
                result.hardwareBuffer.close()
                shot ?: return
                // 요소 이름 없이 자리로 칸을 찾는 서재(리디·휴대폰 밀리)는 화면이 움직이는 중이었을 수 있어, 찍은 뒤에도 같은 자리에 있는 칸만 자른다.
                val still = if (!spec.cellsByPosition) wanted else {
                    val now = root(spec)?.let { readShelf(spec, it) }.orEmpty().associate { it.title to it.thumb }
                    wanted.filter { (title, rect) -> now[title] == rect }
                }
                Bg.run({
                    try {
                        still.mapNotNull { (title, rect) ->
                            val r = Rect(rect).apply { intersect(0, 0, shot.width, shot.height) }
                            if (!usableThumb(r)) return@mapNotNull null
                            val crop = trimEdges(Bitmap.createBitmap(shot, r.left, r.top, r.width(), r.height()))
                            // 화면 캡처를 막은 창(읽는 화면 등)은 검게 찍혀 여기서 버려진다.
                            if (isFlat(crop)) return@mapNotNull null
                            title to ByteArrayOutputStream().also { crop.compress(Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()
                        }
                    } finally {
                        shot.recycle()
                    }
                }) { result ->
                    for ((title, bytes) in result.getOrNull().orEmpty()) {
                        val book = BookShelf.findByTitle(context, title)
                        if (book == null) { pendingCovers[normTitle(title)] = bytes; continue }
                        // 한 권이 실패해도(저장 공간 등) 나머지는 넣는다. 못 넣은 책은 다음에 서재를 볼 때 다시 한다.
                        if (!BookShelf.hasCover(context, book.id)) runCatching { BookShelf.setCover(context, book.id, bytes) }
                    }
                }
            }

            override fun onFailure(errorCode: Int) {}
        })
    }


    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        recordLife(this, connected = true)
        WatchLog.connected(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        recordLife(this, connected = false)
        WatchLog.add(this, WatchLog.Kind.UNBOUND)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(settleLater)
        handler.removeCallbacks(viewerLater)
        WatchLog.add(this, WatchLog.Kind.DESTROYED)
        WatchLog.flush()
        super.onDestroy()
    }

    /** 화면에 떠 있는 그 앱의 창들(읽는 화면 + 위에 뜬 메뉴·알림). 창 목록을 못 얻으면 맨 앞 창만. */
    private fun appRoots(spec: ReaderSpec): List<AccessibilityNodeInfo> =
        runCatching { windows.mapNotNull { it.root }.filter { it.packageName == spec.pkg } }.getOrDefault(emptyList())
            .ifEmpty { listOfNotNull(root(spec)) }

    /**
     * [cls] 가 [pkg] 의 액티비티인가. 대화상자가 닫힐 때는 화면 바뀜 알림이 다시 오지 않아, 대화상자를 화면으로 기억하면
     * 그 아래 읽는 화면을 읽지 않게 된다. 창이 바뀔 때만 불리고 앱이 업데이트될 수 있어 담아 두지 않는다. 앱 정보를 못 읽으면 액티비티로 본다.
     */
    private fun isActivity(pkg: String, cls: String): Boolean {
        val activities = runCatching { packageManager.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES).activities }.getOrNull()
            ?: return true
        return activities.any { it.name == cls }
    }

    /** 지금 앞에 있는 그 앱의 화면(다른 앱·시스템 창이면 null) */
    private fun root(spec: ReaderSpec): AccessibilityNodeInfo? = rootInActiveWindow?.takeIf { it.packageName == spec.pkg }

    private fun windowsOf(spec: ReaderSpec) = AppWindows(root(spec), appRoots(spec))

    /** 그 앱의 화면을 못 얻었을 때 기록한다. 다른 앱으로 떠난 것(맨 앞 창이 다른 앱)은 빼고, 맨 앞 창을 아예 못 얻을 때만. */
    private fun logNoWindow(spec: ReaderSpec) {
        if (rootInActiveWindow == null) WatchLog.add(this, WatchLog.Kind.NO_WINDOW, spec.pkg)
    }

    companion object {
        private const val THROTTLE_MS = 1000L
        /** 읽는 화면이 이만큼 잠잠하면 진행률·제목을 읽는다 */
        private const val VIEWER_SETTLE_MS = 700L
        /** 진행률이 비어 있으면 이 간격으로 이만큼 더 읽어 본다 */
        private const val VIEWER_RETRY_MS = 1000L
        private const val VIEWER_RETRIES = 3
        private const val SHOT_INTERVAL_MS = 1100L
        /** 서재 화면이 이만큼 그대로여야 표지를 찍는다 */
        private const val SETTLE_MS = 1500L
        /** 바로 자르기를 서재 방문마다 책 한 권에 해 보는 최대 횟수 */
        private const val EAGER_TRIES = 3
        /** 목록에 없는 책의 표지를 잘라 둘 최대 권수 */
        private const val PENDING_MAX = 6
        /** 칸을 누른 뒤 이 안에 읽는 화면이 열리면 그 책을 편 것으로 본다(내려받기를 기다리는 시간) */
        private const val CLICK_OPEN_MS = 120_000L
        /** 화면이 바뀐 뒤 이만큼은 바로 자르지 않는다 */
        private const val TRANSITION_MS = 1000L

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

        /**
         * 서비스가 연결·끊긴 때와 끊긴 횟수(기기 정보에서 본다). 켜 둔 서비스가 저절로 꺼지는 기기(Moaan MIX7S)에서
         * 언제 끊겼는지 알려고. 이 앱 프로세스가 왜 끝났는지는 기기 정보가 안드로이드에서 따로 읽는다.
         */
        private fun recordLife(context: Context, connected: Boolean) {
            val prefs = lifePrefs(context)
            val now = System.currentTimeMillis()
            prefs.edit().apply {
                if (connected) putLong(LIFE_CONNECTED, now)
                else putLong(LIFE_DISCONNECTED, now).putInt(LIFE_DISCONNECTS, prefs.getInt(LIFE_DISCONNECTS, 0) + 1)
            }.apply()
        }

        /** 마지막 연결 때·마지막 끊김 때(없으면 0)·끊긴 횟수 */
        fun lifeLog(context: Context): Triple<Long, Long, Int> = lifePrefs(context).let {
            Triple(it.getLong(LIFE_CONNECTED, 0), it.getLong(LIFE_DISCONNECTED, 0), it.getInt(LIFE_DISCONNECTS, 0))
        }

        private fun lifePrefs(context: Context) = Storage.of(context).getSharedPreferences("reader_watch_life", Context.MODE_PRIVATE)
        private const val LIFE_CONNECTED = "connected"
        private const val LIFE_DISCONNECTED = "disconnected"
        private const val LIFE_DISCONNECTS = "disconnects"

        /** 시스템 접근성 설정에서 이 서비스가 켜져 있는가 */
        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, ReaderWatchService::class.java)
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        /**
         * 안드로이드 13+ 의 '제한된 설정': 파일(APK)로 직접 깐 앱은 접근성에서 흐리게 막힌다. 앱 정보 › ⋮ ›
         * `제한된 설정 허용` 으로 풀어야 한다(그 창을 한 번 본 뒤에야 메뉴가 생긴다). 막혔는지는 알 수 없어 13+ 이면 안내한다.
         */
        val mayBeRestricted: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        /** 시스템 접근성 설정 */
        fun openSettings(context: Context) {
            runCatching { context.startActivity(settingsIntent()) }
        }

        fun settingsIntent() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

        /**
         * 이 앱의 앱 정보(제한된 설정 허용). 기기 설정 아이콘을 숨긴 기기(iReader)에서도 직접 열린다.
         * 안드로이드 13 설정 앱은 ⋮ 메뉴를 만들 때 앱 uid 를 앱 목록이 넘기는 인자나 extra `uId` 에서만 읽어,
         * 없으면 -1 로 묻다가 실패해 `제한된 설정 허용` 이 빠진다(에뮬레이터 API 33 에서 확인). 그래서 uid 를 함께 넘긴다.
         */
        fun openAppInfo(context: Context) {
            runCatching { context.startActivity(appInfoIntent(context)) }
        }

        fun appInfoIntent(context: Context): Intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).putExtra("uId", Process.myUid())
    }
}
