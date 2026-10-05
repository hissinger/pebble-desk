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
 * 읽고 있는 책 자동 추가(접근성 서비스). 교보도서관·교보eBook·밀리의서재·알라딘·북커스·리디·YES24(전자도서관·my YES)의 **서재 화면**에 보이는 제목·저자·진행률·반납일과
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
    private fun setOpened(pkg: String, title: String) {
        if (opened(pkg) != title) openedPrefs.edit().putString("opened_$pkg", title).apply()
    }
    /** 검색(웹 화면)에서 보고 있는 책 상세. 여기서 읽는 화면으로 넘어가면 이 책을 편 것으로 본다(`바로 읽기` 클릭에는 글자가 없다). */
    private val webBook = mutableMapOf<String, Seen>()
    /** 눌렀지만 아직 읽는 화면이 열리지 않은 책 칸과 누른 때([ReaderSpec.openOnViewer] 인 앱) */
    private val clicked = mutableMapOf<String, Pair<Seen, Long>>()
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
    private val lastShelf = mutableMapOf<String, List<Seen>>()
    /** 이번 서재 방문에서 책(정리한 제목)마다 바로 자르기를 해 본 횟수. 그 앱 화면이 새로 열릴 때마다 비운다. */
    private val eagerTries = mutableMapOf<String, Int>()
    /** 서재에서 읽는 화면으로 갔다 온 앱(서재의 '최근 읽은 책' 줄로 어떤 책인지 안다) */
    private val readSince = mutableSetOf<String>()
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
        val spec = SPECS[e.packageName?.toString()] ?: return
        if (!HomePrefs(this).autoBooks) return
        // 서재는 화면이 바뀌는 동안(칸을 다시 채우는 중, 표지를 불러오는 중, 누른 칸이 반전된 순간) 읽지 않고 멈출 때까지 미룬다.
        if (settleSpec?.pkg == spec.pkg) scheduleSettle(spec)
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // 대화상자 같은 것은 빼고 그 앱의 화면만 기억한다.
                e.className?.toString()?.takeIf { it.startsWith(spec.classPrefix) }?.let {
                    // 화면이 새로 열릴 때마다(홈에서 돌아온 서재 포함) 바로 자르기 횟수를 다시 센다.
                    eagerTries.clear()
                    lastTransition = System.currentTimeMillis()
                    screen[spec.pkg] = it
                    if (ReadingLog.isReaderScreen(it)) {
                        if (spec.recent != null) readSince += spec.pkg
                        webBook.remove(spec.pkg)?.let { book -> bookOpened(spec, book) }
                        clicked.remove(spec.pkg)?.takeIf { System.currentTimeMillis() - it.second < CLICK_OPEN_MS }?.let { bookOpened(spec, it.first) }
                    }
                }
                readScreen(spec, force = true)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> readScreen(spec, force = false)
            AccessibilityEvent.TYPE_VIEW_CLICKED -> if (screen[spec.pkg] in spec.shelves) {
                val cell = e.source?.let { clickedCell(spec, it) } ?: fromClickText(spec, e.text) ?: shelfCellByText(spec, e.text)
                when {
                    cell == null -> rememberWebBook(spec)
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
                if (spec.readNow.isNotEmpty() && (force || now - lastWebRead >= THROTTLE_MS)) {
                    lastWebRead = now
                    root(spec)?.let { root ->
                        readWebBook(spec, root)?.let { webBook[spec.pkg] = it } ?: webBook.remove(spec.pkg)
                    }
                }
                // 서재를 잠깐만 보고 책으로 넘어가는 앱(알라딘·북커스·리디·YES24 전자도서관)은 멈추기를 기다리지 않고 표지가 보이는 대로 잘라 둔다.
                // 화면이 막 바뀐 때(읽는 화면에서 돌아오는 중이면 화면 캡처가 막힌 읽는 화면이 검게 찍힌다)와 아직 찍을 수 없을 때는 건너뛴다.
                val ready = now - lastTransition >= TRANSITION_MS && now - lastShot >= SHOT_INTERVAL_MS
                if (spec.eagerCovers && ready && (force || now - lastEagerRead >= THROTTLE_MS)) {
                    lastEagerRead = now
                    root(spec)?.let { root ->
                        captureCovers(spec, readShelf(spec, root).distinctBy { it.title }.mapNotNull { s ->
                            val thumb = s.thumb?.takeIf(::usableThumb) ?: return@mapNotNull null
                            val book = BookShelf.findByTitle(this, s.title)
                            val need = if (book == null) pendingFor(s.title) == null else !BookShelf.hasCover(this, book.id)
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
     * 읽는 화면: 본문(웹 화면)은 훑지 않고 이름으로 찾은 요소만 읽는다. 진행률은 아래 줄([ReaderSpec.viewerProgress])에서,
     * 없으면 메뉴의 쪽([ReaderSpec.viewerPages])으로. 제목([ReaderSpec.viewerTitle])은 메뉴를 띄웠을 때만 보인다. 제목이 보이면
     * 그 책을 편 것으로 보고(서재 칸으로는 알 수 없는 교보도서관 표지 보기·교보eBook·알라딘 격자 보기·리디), 진행률을 그 책에 적는다.
     */
    private fun readViewer(spec: ReaderSpec) {
        viewerSpec = null
        if (screen[spec.pkg] !in spec.viewers) return
        // 메뉴·알림 창이 위에 떠 있어도 그 아래 읽는 화면의 진행률 줄을 읽도록 그 앱의 창을 모두 본다.
        val roots = appRoots(spec).ifEmpty { return }
        // 글자가 없는 묶음이면 바로 아래 글자들을 이어 붙인다(북커스 아래 줄: `8` ` / ` `371` 세 조각).
        fun textOf(n: AccessibilityNodeInfo): String? = n.text?.toString()
            ?: (0 until n.childCount).mapNotNull { n.getChild(it)?.text?.toString() }.joinToString("").ifEmpty { null }
        fun texts(id: String) = roots.flatMap { it.byId(spec, id) }.mapNotNull { textOf(it)?.trim() }.filter { it.isNotEmpty() }
        // 진행률 줄이 여럿이면 앱이 보여 주는 % 를 쪽으로 셈한 것보다 먼저 쓴다(리디 아래 좌·우는 사용자가 고른 대로 쪽·% 가 놓인다).
        // 책을 막 열었을 때의 `페이지 계산중 - 40%` 는 진행률이 아니다(my YES).
        val lines = spec.viewerProgress.flatMap(::texts).filterNot { "계산" in it }
        val progress = lines.firstNotNullOfOrNull { percent(it).takeIf { p -> p >= 0 } }
            ?: lines.firstNotNullOfOrNull { viewerPercent(it, spec.pagesRoundUp).takeIf { p -> p >= 0 } }
            ?: spec.viewerPages?.let { (cur, total) -> pagePercent(texts(cur).firstOrNull(), texts(total).firstOrNull(), spec.pagesRoundUp) }
            ?: -1
        if (progress < 0 && viewerRetries-- > 0) {
            viewerSpec = spec
            handler.postDelayed(viewerLater, VIEWER_RETRY_MS)
        }
        // 제목은 하나만 보일 때만 믿는다(같은 이름이 여럿이면 목차 같은 목록이다. 북커스는 흔한 이름 `tv_title`).
        spec.viewerTitle?.let { id -> texts(id).distinct().singleOrNull() }?.let { title ->
            val current = opened(spec.pkg)?.let { BookShelf.findByTitle(this, it) }
            if (current == null || BookShelf.findByTitle(this, title)?.id != current.id) {
                bookOpened(spec, Seen(title, "", progress, -1, null, Rect()))
                return
            }
        }
        if (progress < 0) return
        val book = opened(spec.pkg)?.let { BookShelf.findByTitle(this, it) } ?: return
        BookShelf.updateStatus(this, book.id, progress, -1)
    }

    /**
     * 누른 책 칸. 이벤트로 받은 요소는 위(부모)로 올라갈 수 없어서, 그 앱의 창(맨 앞 창부터)을 한 번 훑어 누른 요소를 찾고
     * 거기서 위로 올라가며 제목이 하나 든 가장 가까운 묶음을 칸으로 본다(표지 그림만 눌린 것으로 오기도 한다).
     * 창이 겹치는 앱(my YES 구매목록 창 뒤의 책장)에서 위치로 고르면 뒤 창의 칸을 고를 수 있고, 누르자마자 창이 닫히기도 해서 빨리 찾는다.
     * 못 찾으면, 창이 겹치지 않는 앱은 누른 곳의 가운데가 들어 있는 칸, 그래도 없으면 누른 요소 안에서 읽는다.
     */
    private fun clickedCell(spec: ReaderSpec, source: AccessibilityNodeInfo): Seen? {
        val at = Rect().also { source.getBoundsInScreen(it) }
        val roots = (listOfNotNull(root(spec)) + appRoots(spec)).distinct()
        for (r in roots) {
            val path = pathTo(r, source, at) ?: continue
            // 누른 요소에서 위로: 제목이 처음 보이는 묶음이 그 칸(제목이 둘 이상이면 여러 권을 담은 목록이라 칸이 아니다).
            for (n in path.asReversed()) {
                val titles = spec.titles.sumOf { n.count(spec, it) }
                if (titles == 0) continue
                if (titles == 1 && spec.thumbs.sumOf { n.count(spec, it) } > 0) readCell(spec, n)?.let { return it }
                break
            }
        }
        if (!spec.openOnViewer) {
            root(spec)?.let { readShelf(spec, it) }?.firstOrNull { it.cell.contains(at.centerX(), at.centerY()) }?.let { return it }
        }
        return cellOf(spec, source)?.let { readCell(spec, it) }
    }

    /**
     * [root] 에서 [target] 까지의 요소들(root 부터). 이벤트로 받은 요소는 창 번호가 실제 창과 다르게 오기도 해서
     * (my YES 구매목록 창의 단추가 뒤 창 번호로 온다) 요소 이름·종류·위치([at])가 모두 같으면 같은 요소로 본다.
     */
    private fun pathTo(root: AccessibilityNodeInfo, target: AccessibilityNodeInfo, at: Rect): List<AccessibilityNodeInfo>? {
        val path = ArrayList<AccessibilityNodeInfo>()
        val r = Rect()
        fun walk(n: AccessibilityNodeInfo, depth: Int): Boolean {
            n.getBoundsInScreen(r)
            // 누른 요소를 품지 않는 묶음은 들어가지 않는다.
            if (!r.contains(at)) return false
            path += n
            if (n.viewIdResourceName == target.viewIdResourceName && n.className == target.className && r == at) return true
            if (depth < DEEP_WALK_DEPTH) for (i in 0 until n.childCount) if (n.getChild(i)?.let { walk(it, depth + 1) } == true) return true
            path.removeAt(path.size - 1)
            return false
        }
        return if (walk(root, 0)) path else null
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
        val author = authorName(above.getOrNull(1)?.first.orEmpty())
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
    private fun readShelf(spec: ReaderSpec, root: AccessibilityNodeInfo): List<Seen> =
        (if (spec.unnamedShelf != null) unnamedCells(spec, root)
        else spec.titles.flatMap { root.byId(spec, it) }.mapNotNull { title -> cellOf(spec, title)?.let { readCell(spec, it) } })
            .also { if (it.isNotEmpty()) lastShelf[spec.pkg] = it }

    /**
     * 요소 이름이 없는 서재(리디): 설명이 그 안의 글자(제목)로 시작하고 표지 모양 그림이 든 묶음이 책 칸이다.
     * 서재 표시 요소([ReaderSpec.unnamedShelf])가 없는 화면(홈·검색 탭)은 읽지 않고, 위에 뜬 화면(작품 화면)에 가려진 칸은 뺀다.
     * 같은 책이 여럿이면(아래 '최근 본' 줄) 가장 큰 표지.
     */
    private fun unnamedCells(spec: ReaderSpec, root: AccessibilityNodeInfo): List<Seen> {
        val marker = spec.unnamedShelf ?: return emptyList()
        var shelf = false
        val found = mutableMapOf<String, Seen>()
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            // 같은 화면이 품은 홈·검색 탭의 웹 화면은 훑지 않는다.
            if (n.isWebView()) return
            // React Native 의 요소 이름은 `패키지:id/` 없이 그대로라 이름으로 찾기가 안 되어 훑으면서 본다.
            if (n.viewIdResourceName == marker) shelf = true
            val desc = n.contentDescription?.toString()
            if (!desc.isNullOrEmpty()) unnamedCell(n, desc)?.let { s ->
                val old = found[s.title]
                if (!covered(n) && (old?.thumb == null || s.thumb!!.width() > old.thumb.width())) found[s.title] = s
                return
            }
            if (depth < DEEP_WALK_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        return if (shelf) found.values.toList() else emptyList()
    }

    /**
     * [cell] 안의 글자 가운데 설명 [desc] 가 그것으로 시작하는 것(제목)과 표지 모양 그림. 둘 다 있어야 책 칸.
     * 목록 보기 칸은 설명 뒤에 진행률이 있다(`정의란 무엇인가, 26.7MB, 소장, 48%`).
     */
    private fun unnamedCell(cell: AccessibilityNodeInfo, desc: String): Seen? {
        var title: String? = null
        var thumb: Rect? = null
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (n.isWebView()) return
            val t = n.text?.toString()?.trim()
            if (!t.isNullOrEmpty() && desc.startsWith(t) && t.length > (title?.length ?: 0)) title = t
            if (n.className?.endsWith("ImageView") == true && thumb == null) {
                val r = Rect().also { n.getBoundsInScreen(it) }
                if (usableThumb(r) && r.width() >= MIN_UNNAMED_COVER_PX) thumb = r
            }
            if (depth < WALK_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(cell, 0)
        val bounds = Rect().also { cell.getBoundsInScreen(it) }
        val t = title ?: return null
        return Seen(t, "", percent(desc.removePrefix(t)), -1, thumb ?: return null, bounds)
    }

    private fun AccessibilityNodeInfo.isWebView() = className?.endsWith("WebView") == true

    /** [node] 를 덮는 화면이 있는가: 위로 올라가며 나중에 그려지는(drawingOrder 가 큰) 형제가 [node] 를 다 덮으면 가려졌다. */
    private fun covered(node: AccessibilityNodeInfo): Boolean {
        val at = Rect().also { node.getBoundsInScreen(it) }
        var n = node
        for (i in 0 until DEEP_WALK_DEPTH) {
            val parent = n.parent ?: return false
            for (j in 0 until parent.childCount) {
                val sib = parent.getChild(j) ?: continue
                if (sib == n || sib.drawingOrder <= n.drawingOrder) continue
                if (Rect().also { sib.getBoundsInScreen(it) }.contains(at)) return true
            }
            n = parent
        }
        return false
    }

    /**
     * 누른 순간 화면이 이미 읽는 화면으로 넘어가 칸을 못 읽었을 때(북커스·YES24 도서관): 누른 글자 가운데 하나가 방금 본 서재 칸의
     * 제목과 똑같으면 그 칸(YES24 도서관은 도서관 이름이 제목보다 앞에 온다). 탭·단추 글자는 책 제목과 같을 일이 없어 책으로 들어가지 않는다.
     * 진행률은 그 뒤 읽는 화면에서 바뀌었을 수 있어 목록에 이미 있는 책에는 쓰지 않는다(반납일은 읽는 동안 바뀌지 않는다).
     */
    private fun shelfCellByText(spec: ReaderSpec, list: List<CharSequence>): Seen? {
        val texts = list.map { it.toString().trim() }.filter { it.isNotEmpty() }.toSet()
        val s = lastShelf[spec.pkg]?.firstOrNull { it.title in texts } ?: return null
        val progress = if (BookShelf.findByTitle(this, s.title) == null) s.progress else -1
        return Seen(s.title, s.author, progress, s.due, s.thumb, s.cell)
    }

    /**
     * [node] 가 들어 있는 책 칸: 위로 올라가며 제목이 하나뿐인 가장 큰 묶음(그 위는 여러 권을 담은 목록).
     * 표지 그림이 없는 묶음은 책 칸이 아니다(알라딘은 `분야`·`필터` 단추도 같은 이름을 쓴다).
     */
    private fun cellOf(spec: ReaderSpec, node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var cell: AccessibilityNodeInfo? = null
        var n: AccessibilityNodeInfo? = node
        for (i in 0 until CELL_UP) {
            val c = n ?: break
            val titles = spec.titles.sumOf { c.count(spec, it) }
            if (titles > 1) break
            if (titles == 1) cell = c
            n = c.parent
        }
        return cell?.takeIf { c -> spec.thumbs.sumOf { c.count(spec, it) } > 0 }
    }

    /** 책 칸 하나: 칸 아래 요소들을 직접 훑어 이름별 글자를 모은다(누른 칸에서는 이름으로 찾기가 안 된다). */
    private fun readCell(spec: ReaderSpec, cell: AccessibilityNodeInfo): Seen? {
        val texts = mutableMapOf<String, String>()
        var thumb: Rect? = null
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            val id = n.viewIdResourceName?.substringAfter(":id/")
            val t = n.text?.toString()?.trim()
            if (id != null && !t.isNullOrEmpty()) texts.putIfAbsent(id, t)
            if (id in spec.thumbs && thumb == null) thumb = Rect().also { n.getBoundsInScreen(it) }
            if (depth < WALK_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(cell, 0)
        fun text(id: String?) = id?.let { texts[it] }.orEmpty()
        val title = spec.titles.map(::text).firstOrNull { it.isNotEmpty() } ?: return null
        val bounds = Rect().also { cell.getBoundsInScreen(it) }
        val author = spec.authors.map(::text).firstOrNull { it.isNotEmpty() }.orEmpty()
        return Seen(title, authorName(author), percent(text(spec.progress)), dueDate(text(spec.due)), thumb, bounds)
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
        val author = if (spec.authors.isNotEmpty()) authorName(items.getOrNull(1)?.takeIf { "%" !in it && "반납" !in it }.orEmpty()) else ""
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
            BookShelf.updateStatus(this, it.id, seen.progress, seen.due, seen.author)
        } ?: run {
            val app = AppStore.load(this).firstOrNull { it.pkg == spec.pkg }?.key
            BookShelf.addOpened(this, autoBookId(seen.title), seen.title, seen.author, app, seen.progress, seen.due)
        }
        setOpened(spec.pkg, book.title)
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
                // 요소 이름이 없는 서재(리디)는 화면이 움직이는 중이었을 수 있어, 찍은 뒤에도 같은 자리에 있는 칸만 자른다.
                val still = if (spec.unnamedShelf == null) wanted else {
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

    override fun onDestroy() {
        handler.removeCallbacks(settleLater)
        handler.removeCallbacks(viewerLater)
        super.onDestroy()
    }

    /** 자를 만한 표지 칸: 너무 작지 않고(가려졌거나 화면 밖), 세로로 긴 표지 모양(화면 위·아래에 걸쳐 일부만 보이면 아님) */
    private fun usableThumb(r: Rect) =
        r.width() >= MIN_THUMB_PX && r.height() >= MIN_THUMB_PX && r.height() >= r.width() * MIN_COVER_RATIO

    /** 화면에 떠 있는 그 앱의 창들(읽는 화면 + 위에 뜬 메뉴·알림). 창 목록을 못 얻으면 맨 앞 창만. */
    private fun appRoots(spec: ReaderSpec): List<AccessibilityNodeInfo> =
        runCatching { windows.mapNotNull { it.root }.filter { it.packageName == spec.pkg } }.getOrDefault(emptyList())
            .ifEmpty { listOfNotNull(root(spec)) }

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
        /** 서재 칸의 제목(보기 방식마다 이름이 다르면 여럿) */
        val titles: List<String>,
        /** 서재 칸의 표지 그림(보기 방식·화면마다 이름이 다르면 여럿) */
        val thumbs: List<String>,
        /** 서재 칸의 저자(앞에서부터 먼저 찾은 것) */
        val authors: List<String>,
        val progress: String?,
        val due: String?,
        /** 읽는 화면의 진행률 줄(앞에서부터 먼저 찾은 것) */
        val viewerProgress: List<String>,
        /** 읽는 화면 메뉴의 책 제목(메뉴를 띄웠을 때만 보인다) */
        val viewerTitle: String? = null,
        /** 쪽 번호가 지금 쪽·전체 쪽 두 요소로 나뉜 읽는 화면(북커스 메뉴) */
        val viewerPages: Pair<String, String>? = null,
        /** 쪽으로 셈한 진행률을 올림한다(서재에 보이는 % 와 맞추려고. 북커스는 1/368쪽을 1% 로 보인다) */
        val pagesRoundUp: Boolean = false,
        /** 서재 화면의 '최근 읽은 책' 제목(책 칸을 눌러도 알림이 오지 않는 앱) */
        val recent: String? = null,
        /** 검색(웹 화면) 책 상세의 읽기 단추 글자 */
        val readNow: Set<String> = emptySet(),
        /**
         * 서재를 잠깐만 보고 책으로 넘어가는 앱(알라딘·북커스·리디): 멈추기를 기다리지 않고 표지를 잘라 둔다.
         * 밀리는 앱을 다시 열 때 잠깐 칸을 다른 책으로 채우므로 쓰지 않는다.
         */
        val eagerCovers: Boolean = false,
        /** 칸을 눌러도 읽는 화면이 열려야 편 것으로 본다(my YES: 누르면 내려받기·기간 만료 안내가 먼저 뜬다) */
        val openOnViewer: Boolean = false,
        /** 앱 화면 전체가 화면 캡처를 막아(검게 찍힌다) 표지를 자르지 않는다(my YES) */
        val secure: Boolean = false,
        /**
         * 요소 이름이 없는 서재(리디, React Native)의 서재 표시 요소. 이것이 있는 화면에서 칸을 설명·글자·그림으로 찾는다([unnamedCells]).
         * 화면이 넘어가는 중(작품 화면이 밀려 들어옴)에 찍으면 어긋나므로, 찍은 뒤에도 칸이 그 자리에 있을 때만 쓴다.
         */
        val unnamedShelf: String? = null,
    )

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
        /** 책 칸을 찾을 때 위로 올라가는 단계, 칸 안을 훑는 깊이 */
        private const val CELL_UP = 8
        private const val WALK_DEPTH = 12
        /** 웹 화면(검색 책 상세)을 훑는 깊이, 같은 줄·같은 왼쪽으로 보는 차이(px) */
        private const val WEB_DEPTH = 40
        private const val ALIGN_PX = 8
        /** 이보다 작게 보이는 표지 칸은 자르지 않는다(가려졌거나 화면 밖) */
        private const val MIN_THUMB_PX = 40
        /** 표지 칸의 세로 ÷ 가로가 이보다 작으면 일부만 보이는 칸으로 본다(표지는 1.4~1.5) */
        private const val MIN_COVER_RATIO = 1.1f
        /**
         * 창 전체를 훑는 깊이(요소 이름이 없는 리디 서재, 누른 요소 찾기. React Native 는 칸이 60단계쯤 아래에 있다),
         * 리디 서재에서 표지로 쓸 그림의 최소 폭(목록 보기 표지 86px 는 받고 아래 '최근 본' 줄의 66px 는 뺀다)
         */
        private const val DEEP_WALK_DEPTH = 100
        private const val MIN_UNNAMED_COVER_PX = 80
        /** 바로 자르기를 서재 방문마다 책 한 권에 해 보는 최대 횟수 */
        private const val EAGER_TRIES = 3
        /** 목록에 없는 책의 표지를 잘라 둘 최대 권수 */
        private const val PENDING_MAX = 6
        /** 칸을 누른 뒤 이 안에 읽는 화면이 열리면 그 책을 편 것으로 본다(내려받기를 기다리는 시간) */
        private const val CLICK_OPEN_MS = 120_000L
        /** 화면이 바뀐 뒤 이만큼은 바로 자르지 않는다 */
        private const val TRANSITION_MS = 1000L

        private val SPECS = listOf(
            ReaderSpec(
                pkg = "kr.co.kyobobook.KEL", classPrefix = "com.kyobo",
                shelves = setOf("com.kyobo.ebook.kel.ui.main.MainActivity"),
                // 읽는 화면은 본문이 웹 화면이라 훑지 않고, 아래 진행률 줄과 메뉴의 제목만 이름으로 읽는다.
                viewers = setOf(
                    "com.kyobo.ebook.kel.viewer.epub.B2BViewerEpubMainActivity",
                    "com.kyobo.ebook.kel.viewer.pdf.B2BViewerPdfMainActivity",
                ),
                titles = listOf("tvTitle"), thumbs = listOf("ivThumbnail"), authors = listOf("tvAuthor"), progress = "tvReadPercentageTxt", due = "tvRemainDate",
                viewerProgress = listOf("bookIndicator"), viewerTitle = "viewer_top_booktitle",
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
                // 서재(shelves)를 읽지 않으므로 칸 요소 이름(titles·thumbs)은 쓰이지 않는다.
                titles = emptyList(), thumbs = emptyList(), authors = emptyList(), progress = null, due = null,
                viewerProgress = listOf("bookIndicator"), viewerTitle = "viewer_top_booktitle",
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
                titles = listOf("tv_book_title"), thumbs = listOf("iv_thumbnail"), authors = emptyList(), progress = "bookshelf_cell_reading", due = null,
                viewerProgress = listOf("tv_viewer_add_on_right"),
                readNow = setOf("바로 읽기", "이어 읽기"),
            ),
            // 알라딘: 격자 보기 칸에는 제목·표지만 있고 눌러도 알림이 없다(서재 아래 '최근 읽은 책'으로 안다).
            // 목록 보기 칸에는 제목·저자·진행률·대여 기한이 있고 누르면 알림이 온다. 읽는 화면은 메뉴의 제목으로 안다.
            ReaderSpec(
                pkg = "kr.co.aladin.ebook", classPrefix = "kr.co.aladin",
                shelves = setOf("kr.co.aladin.ebook.MainActivity"),
                viewers = setOf("kr.co.aladin.epubreader.readonbook.bookrender.ReadONBookRenderActivity"),
                titles = listOf("txt_title", "text_title"), thumbs = listOf("img_cover"), authors = listOf("text_author"), progress = "txt_read_percent",
                // 읽는 화면 아래 쪽 표시(`6 / 368　 저자소개`, 쪽·장 이름)로 진행률을 셈한다.
                due = "text_rent_date", viewerProgress = listOf("bookrender_txt_page_onepage", "viewermenu_text_pageinfo"),
                viewerTitle = "viewer_header_title",
                recent = "reading_book_tv_book_title", eagerCovers = true,
            ),
            // 북커스: 내서재 칸에 제목·표지·진행률·대여 기한(다운로드 탭), 리스트 보기에는 저자. 칸을 누르면 알림이 온다.
            // 읽는 화면(본문 웹 화면)은 하단 정보를 켰을 때만 아래 줄에 쪽이 보이고, 메뉴에는 제목과 쪽(지금 쪽 / 전체 쪽)이 나온다.
            ReaderSpec(
                pkg = "com.bookers.ebook", classPrefix = "com.bookers",
                shelves = setOf("com.bookers.ebook.ui.purchase.PurchaseActivity"),
                viewers = setOf("com.bookers.ebook.ui.viewer.epub.EpubActivity"),
                titles = listOf("tv_title"), thumbs = listOf("iv_cover"), authors = listOf("tv_author"), progress = "tv_percent", due = "tv_end_date",
                // 하단 정보를 켜면 아래 줄(`ll_page_area`, 쪽 또는 %)이 늘 보인다. 꺼 두면 메뉴의 쪽으로.
                viewerProgress = listOf("ll_page_area"), viewerTitle = "tv_title", viewerPages = "tv_current_page" to "tv_total_page",
                pagesRoundUp = true, eagerCovers = true,
            ),
            // 리디: 책은 교보eBook처럼 읽는 화면 메뉴의 제목(`title`)으로 안다. 서재(React Native, 내 서재 탭과 작품 화면)는 요소 이름이 없어
            // 칸 설명·글자로 표지만 잘라 둔다(칸을 눌러도 알림이 없다). 진행률은 뷰어 설정의 '하단 좌/우 정보 표시'를 켜면 아래 좌·우
            // (`15 / 640`, `2%`), 꺼 두면 메뉴의 쪽(`14 / 626`).
            ReaderSpec(
                pkg = "com.initialcoms.ridi", classPrefix = "com.ridi",
                shelves = setOf("com.ridi.books.viewer.main.activity.MainActivity"),
                viewers = setOf(
                    "com.ridi.books.viewer.reader.epub.EPubReaderActivity",
                    "com.ridi.books.viewer.reader.pagebased.pdf.PDFReaderActivity",
                    "com.ridi.books.viewer.reader.pagebased.comic.ComicBookReaderActivity",
                ),
                titles = emptyList(), thumbs = emptyList(), authors = emptyList(), progress = null, due = null,
                viewerProgress = listOf("reader_right_info", "reader_left_info", "reader_toolbar_page_text"), viewerTitle = "title",
                unnamedShelf = "libraryGnbMenus", eagerCovers = true,
            ),
            // YES24 도서관: 내 서재 칸(격자·목록 보기 같은 이름)에 제목·표지·저자(`<지은이 저>`)·반납(`D-15`). 진행 막대(`pb_read_percent`)는
            // 읽는 화면과 맞지 않아(읽는 화면 8% 인데 0) 쓰지 않는다. 읽는 화면(본문 웹 화면, 북커스와 같은 뷰어)은 아래에 진행률(`tv_percent`),
            // 뷰어 설정에서 하단 정보를 켜면 그 자리에 쪽(`ll_page_area`, `14` `/` `283` 세 조각)이 보인다. 메뉴에 제목(`tv_title`).
            ReaderSpec(
                pkg = "com.yes24.library.eink", classPrefix = "com.yes24",
                shelves = setOf("com.yes24.library.shelf.LibShelfActivity"),
                viewers = setOf("com.yes24.ebook.fourth.ui.viewer.epub.EpubActivity"),
                titles = listOf("tv_title"), thumbs = listOf("iv_cover"), authors = listOf("tv_author"), progress = null, due = "tv_d_day",
                viewerProgress = listOf("tv_percent", "ll_page_area"), viewerTitle = "tv_title", pagesRoundUp = true, eagerCovers = true,
            ),
            // my YES(YES24 서점, 크레마 기본 앱): 첫 화면 기본책장(격자) 칸에 제목 `textView_title`·표지 `grid_book_cover`·저자 `textView_author`
            // (`<김호연> 저`), 그 위에 뜨는 구매목록 창 칸(`ll_list_book`)에 제목 `tv_buylist_title`·작은 표지 `aiv_buylist_thumbnail`·저자.
            // 진행률·반납일은 서재에 없다. 읽는 화면(본문 웹 화면) 아래 `bottom_page_no`(`28%`), 메뉴에 제목 `tv_book_title`·`text_space_page`(`28%`).
            // 앱 화면 전체가 화면 캡처를 막아(검게 찍힘, 코드에 창 FLAG_SECURE) 표지는 자르지 않는다.
            ReaderSpec(
                pkg = "com.yes24.ebook.einkstore", classPrefix = "com.keph",
                shelves = setOf("com.keph.crema.lunar.ui.MainActivity"),
                viewers = setOf(
                    "com.keph.crema.lunar.ui.viewer.epub.CremaEPUBActivity",
                    "com.keph.crema.lunar.ui.viewer.pdf.CremaPDFActivity",
                    "com.keph.crema.lunar.ui.viewer.cpub.CremaCPUBActivity",
                    "com.keph.crema.lunar.ui.viewer.txt.CremaTXTActivity",
                ),
                titles = listOf("textView_title", "tv_buylist_title"), thumbs = listOf("grid_book_cover", "aiv_buylist_thumbnail"),
                // 기간이 지난 대여 책은 표지 위에 `기간만료`(`tv_book_disable_message`)가 뜬다.
                authors = listOf("textView_author", "tv_buylist_author"), progress = null, due = "tv_book_disable_message",
                viewerProgress = listOf("bottom_page_no", "text_space_page"), viewerTitle = "tv_book_title",
                openOnViewer = true, secure = true,
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

        /** `한강 지음`, `한스 로슬링 지음 / 이창신 옮김`, `<정지원,염선형 저>/ 미래의창` → 지은이만 */
        private fun authorName(text: String): String {
            val t = text.trim().removePrefix("<").substringBefore(">")
            return (AUTHOR_ROLE.find(t)?.let { t.substring(0, it.range.first) } ?: t).trim()
        }

        /** 지은이 뒤의 `지음`·`저`(뒤에 옮긴이·출판사가 이어질 수 있다) */
        private val AUTHOR_ROLE = Regex("""\s+(지음|저)(?=\s*($|/|,))""")

        private fun percent(text: CharSequence): Int =
            Regex("""(\d{1,3})\s*%""").find(text)?.groupValues?.get(1)?.toInt()?.coerceIn(0, 100) ?: -1

        /** 읽는 화면의 진행률: `2% (4/226p)` 처럼 % 가 있으면 그것, 없으면 쪽 `6 / 368` 로 계산(알라딘·북커스). 모르면 -1 */
        private fun viewerPercent(text: CharSequence, roundUp: Boolean): Int {
            percent(text).takeIf { it >= 0 }?.let { return it }
            val m = Regex("""(\d+)\s*/\s*(\d+)""").find(text) ?: return -1
            return pagePercent(m.groupValues[1], m.groupValues[2], roundUp)
        }

        /** 지금 쪽 ÷ 전체 쪽(버림, [roundUp] 이면 올림). 숫자가 아니면 -1 */
        private fun pagePercent(page: String?, total: String?, roundUp: Boolean): Int {
            val p = page?.trim()?.toLongOrNull() ?: return -1
            val t = total?.trim()?.toLongOrNull()?.takeIf { it > 0 } ?: return -1
            val pct = if (roundUp) (p * 100 + t - 1) / t else p * 100 / t
            return pct.toInt().coerceIn(0, 100)
        }

        /** `2026.10.20` → 그날, `반납 4일 남음`·`D-4` → 오늘 + 4일, `오늘 반납` → 오늘, `만료` → 어제(지남). 모르면 -1 */
        private fun dueDate(text: String): Long {
            if (text.isEmpty()) return -1
            Regex("""(\d{4})\s*[.\-/년]\s*(\d{1,2})\s*[.\-/월]\s*(\d{1,2})""").find(text)?.let { m ->
                val (y, mo, d) = m.destructured
                runCatching { return LocalDate.of(y.toInt(), mo.toInt(), d.toInt()).toEpochDay() }
            }
            val today = LocalDate.now().toEpochDay()
            Regex("""D-\s*(\d+)""", RegexOption.IGNORE_CASE).find(text)?.let { return today + it.groupValues[1].toLong() }
            if (text.equals("D-Day", ignoreCase = true)) return today
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
