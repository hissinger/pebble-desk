package com.woody.pebbledesk.readers

import android.view.accessibility.AccessibilityNodeInfo
import com.woody.pebbledesk.BookShelf
import com.woody.pebbledesk.HomePrefs
import com.woody.pebbledesk.ReadingLog
import com.woody.pebbledesk.Seen
import com.woody.pebbledesk.ShelfRecorder
import com.woody.pebbledesk.hasTitle
import com.woody.pebbledesk.WatchLog
import com.woody.pebbledesk.normTitle
import java.time.LocalDate

/**
 * 바탕 어댑터: 화면에 보이는 것만 읽는 앱(대부분)의 흐름. 서재 칸·누른 칸·읽는 화면 메뉴 제목으로 책을 알고, 서재 칸의 진행률·반납일·표지를 적는다.
 * 다른 길로 책을 아는 앱은 이것을 상속해 그 앱 파일에서 훅을 덮는다(docs/reader-adapter-plan.md `어댑터 관계`). 훅을 덮을 때 [onScreen] 은 덮지 않는다.
 */
open class ScreenReader(val app: ReaderApp) : ReaderAdapter {
    /** 눌렀지만 아직 읽는 화면이 열리지 않은 책 칸과 누른 때([ReaderApp.openOnViewer] 인 앱) */
    protected var clicked: Pair<Cell, Long>? = null
    /** 서재에서 책 칸으로 알아보지 못한 것을 누른 때(문리더 홈 탭·교보도서관 표지 보기의 표지만 있는 칸은 눌러도 제목이 오지 않는다) */
    private var unknownClick: Long? = null
    /** 마지막으로 읽은 서재 칸들(누른 순간 화면이 이미 넘어가 칸을 못 읽을 때 누른 글자와 맞춰 본다) */
    private var lastShelf: List<Cell>? = null
    /** 이번 서재 방문에서 책(정리한 제목)마다 바로 자르기를 해 본 횟수. 창 바뀜으로 화면이 새로 열릴 때마다 비운다 */
    private val eagerTries = mutableMapOf<String, Int>()
    private var lastEagerRead = 0L

    /**
     * 공통 전이(덮지 않는다): 서재 안에 뜨는 읽는 화면의 열림·닫힘 표시, 책 파일 읽기 안내, 그리고 읽는 화면이 앞에 왔으면
     * 제목을 못 읽은 칸을 눌러 들어온 것은 책 모름으로(메뉴의 제목을 읽으면 그 책으로), 메뉴 없이 떠났다 다시 들어온 것이면 그 책 모름이 된 때를 지운 뒤 [onEnterViewer].
     */
    final override fun onScreen(ctx: AppContext, name: String, from: String?, again: Boolean, byWindow: Boolean) {
        // 화면이 새로 열릴 때마다(홈에서 돌아온 서재 포함) 바로 자르기 횟수를 다시 센다.
        if (byWindow) eagerTries.clear()
        val now = ctx.now
        val viewer = app.isViewer(ctx.context, name)
        // 읽는 화면이 서재 액티비티 안에 뜨는 앱은 읽는 화면이 보이고 사라진 때를 읽은 시간에 적는다
        if (app.viewerTime == ViewerTime.MARKS_CLOSED) ctx.recorder.viewerShown(ctx.pkg, viewer, now)
        // 책 파일 읽기가 필요한 앱으로 책을 읽는데 꺼져 있으면 홈에서 묻게 한다(깔아만 둔 앱은 읽는 화면이 뜨지 않아 묻지 않는다)
        if (app.needsBookFiles && viewer && !BookFiles.canRead(ctx.context)) wantFiles(ctx)
        if (!ReadingLog.isReaderScreen(ctx.context, ctx.pkg, name)) return
        val unknown = unknownClick?.takeIf { now - it < CLICK_OPEN_MS }
        unknownClick = null
        if (unknown != null) {
            ctx.unknownOpenAt = now
            // 제목을 못 읽은 칸을 눌러 들어왔다: 여기부터는 책 모름(앞서 편 책에 붙이지 않게. 메뉴의 제목을 읽으면 그 책으로)
            ctx.unknown(now)
        } else if (!again && !app.keepsUnknownOnReenter) {
            // 그렇게 연 읽는 화면을 메뉴 없이 떠났다 다시 들어왔으면(다른 책일 수 있다) 그때 연 때를 쓰지 않는다(그 사이 시간은 책 모름 그대로).
            // 같은 읽는 화면의 알림이 한 번 더 온 것이면 그대로 둔다.
            ctx.unknownOpenAt = null
        }
        onEnterViewer(ctx, from, again)
    }

    /** 바탕: 서재에서 누른 칸이 있으면 그 책을 편 것으로 */
    override fun onEnterViewer(ctx: AppContext, from: String?, again: Boolean) {
        takeClicked(ctx)?.let { openCell(ctx, it) }
    }

    /** 읽는 화면이 열릴 때 쓸 누른 칸([CLICK_OPEN_MS] 안). 꺼내면 지운다 */
    protected fun takeClicked(ctx: AppContext): Cell? = clicked?.takeIf { ctx.now - it.second < CLICK_OPEN_MS }?.first.also { clicked = null }

    /** 서재에서 무엇인가를 눌렀다: 책 칸이면 그 책을 편 것으로 본다(눌러도 읽는 화면이 열려야 편 것으로 보는 앱은 열릴 때) */
    override fun onClick(ctx: AppContext, click: Click) {
        if (ctx.screen !in app.shelves) return
        val windows = ctx.windows()
        if (windows.all.any(app::editing)) return
        val cell = app.clickedCell(click.source, click.text, windows, lastShelf)?.let { c ->
            // 방금 읽어 둔 칸으로 맞춘 것은 그 뒤 읽는 화면에서 진행률이 바뀌었을 수 있어 목록에 이미 있는 책에는 쓰지 않는다(반납일은 그대로).
            if (c.stale && ctx.recorder.lookup.byTitle(c.title) != null) c.copy(progress = -1) else c
        }
        ctx.log(WatchLog.Kind.CLICK, cell?.title.orEmpty())
        when {
            cell == null -> {
                // 표지만 있는 칸일 수 있다(누르자마자 읽는 화면으로 넘어가 누른 요소도 읽을 수 없다). 메뉴에 제목이 없는 앱은 알 길이 없어 두지 않는다.
                if (app.tellsTitle) unknownClick = ctx.now
                onClickNoCell(ctx)
            }
            // 누르면 내려받기·기간 만료 안내가 먼저 뜨는 앱은 읽는 화면이 열릴 때 편 것으로 본다. 기간이 지난 책은 열리지 않는다.
            app.openOnViewer -> clicked = if (cell.due in 0 until LocalDate.now().toEpochDay()) null else cell to ctx.now
            else -> openCell(ctx, cell)
        }
    }

    /** 서재에서 누른 것이 책 칸이 아니다(제목 못 읽은 누름을 적은 뒤). 바탕: 아무것도 안 함 */
    protected open fun onClickNoCell(ctx: AppContext) {}

    /** 서재 칸 [cell] 의 책을 [at] 에 폈다. 바탕: 그 제목으로 */
    protected open fun openCell(ctx: AppContext, cell: Cell, at: Long? = null) {
        ctx.opened(Seen(cell.title, cell.author, cell.progress, cell.due), at)
    }

    /**
     * 서재를 잠깐만 보고 책으로 넘어가는 앱(알라딘·북커스·YES24 전자도서관)은 멈추기를 기다리지 않고 표지가 보이는 대로 잘라 둔다.
     * 화면이 막 바뀐 때(읽는 화면에서 돌아오는 중이면 화면 캡처가 막힌 읽는 화면이 검게 찍힌다)와 아직 찍을 수 없을 때는 건너뛴다.
     */
    override fun onShelfLive(ctx: AppContext, force: Boolean) {
        if (!app.eagerCovers) return
        val now = ctx.now
        val ready = now - ctx.lastTransition >= TRANSITION_MS && ctx.canShoot
        if (!ready || !(force || now - lastEagerRead >= THROTTLE_MS)) return
        lastEagerRead = now
        val root = ctx.root ?: return
        val lookup = ctx.recorder.lookup
        ctx.capture(readShelf(root).distinctBy { it.title }.mapNotNull { s ->
            val thumb = s.thumb?.takeIf(::usableThumb) ?: return@mapNotNull null
            val book = lookup.byTitle(s.title)
            // 목록에 없는 책은 들어올 때 붙일 표지를 미리 찍어 둔다.
            val need = if (book == null) !ctx.recorder.hasPendingTitle(s.title) else !lookup.hasCover(book.id)
            if (need && tryCover(s.title)) CoverSpot(s.title, thumb) else null
        }, retry = false)
    }

    /**
     * 서재 화면이 멈췄다: 보이는 책들의 진행률·반납일을 적고, 표지 없는 책은 표지 칸을 찍어 잘라 온다
     * (목록에 없는 책도 잘라 두었다가 그 책이 들어오면 붙인다).
     */
    override fun onShelf(ctx: AppContext) {
        val root = ctx.root ?: return ctx.noWindow()
        val seen = readShelf(root)
        ctx.log(WatchLog.Kind.SHELF, "${seen.size}")
        val spots = mutableListOf<CoverSpot>()
        shelfStatuses(ctx, seen, spots)
        if (!app.secure) ctx.capture(spots)
    }

    /** 서재에 보이는 책 칸들(읽은 것은 [lastShelf] 에 남긴다). 서재 편집 중이면 읽지 않는다(고르기 표시가 표지에 찍힌다). */
    protected fun readShelf(root: AccessibilityNodeInfo): List<Cell> =
        if (app.editing(root)) emptyList() else app.shelfCells(root).also { if (it.isNotEmpty()) lastShelf = it }

    /**
     * 서재 칸 [seen] 의 진행률·반납일을 제목으로 맞춰 한 번에 적고, 표지 없는 책·목록에 없는 책의 표지 자리를 [spots] 에 더한다.
     * 목록에 없는 책도 멈춘 지금 잘라 두어, 그 책을 펴면 처음부터 표지가 붙게 한다(리디는 바로 자르기를 하지 않아 이것으로만 자른다).
     */
    protected fun shelfStatuses(ctx: AppContext, seen: List<Cell>, spots: MutableList<CoverSpot>) {
        val lookup = ctx.recorder.lookup
        val statuses = mutableListOf<BookShelf.Status>()
        for (s in seen) {
            val book = lookup.byTitle(s.title)
            if (book == null) {
                val thumb = s.thumb?.takeIf(::usableThumb)
                if (thumb != null && !ctx.recorder.hasPendingTitle(s.title)) spots += CoverSpot(s.title, thumb)
                continue
            }
            statuses += BookShelf.Status(book.id, s.progress, s.due, s.author)
            if (s.thumb != null && !lookup.hasCover(book.id) && !ShelfRecorder.coverFromFile(ctx.context, lookup.filePath(book.id))) spots += CoverSpot(s.title, s.thumb, book.id)
        }
        // 서재 한 화면의 진행률·반납일을 한 번에 쓴다
        ctx.recorder.statuses(statuses)
    }

    /**
     * 읽는 화면: 진행률·제목은 앱이 읽는다([ReaderApp.readViewer]). 진행률이 아직 비어 있으면 조금 뒤 다시 읽는다(메뉴의 쪽 정보는 늦게
     * 채워지고 그때 알림이 없다). 제목이 보이면 그 책을 편 것으로 보고(서재 칸으로는 알 수 없는 교보도서관 표지 보기·교보eBook·알라딘 격자 보기·
     * 리디·문리더 홈 탭, 서재 없이 바로 열린 책), 진행률을 그 책에 적는다.
     */
    override fun onViewer(ctx: AppContext) {
        val shown = readShown(ctx) ?: return
        logShown(ctx, shown)
        retryProgress(ctx, shown.progress)
        recordShown(ctx, shown)
    }

    /** 읽는 화면에서 읽은 것. 창을 못 얻으면 null(적어 둔다) */
    protected fun readShown(ctx: AppContext): ViewerInfo? {
        val windows = ctx.windows().takeIf { it.all.isNotEmpty() } ?: return null.also { ctx.noWindow() }
        return app.readViewer(windows)
    }

    protected fun logShown(ctx: AppContext, shown: ViewerInfo) {
        if (shown.progress >= 0 || shown.title != null) ctx.log(WatchLog.Kind.PROGRESS, "${shown.progress}", shown.title.orEmpty())
    }

    /** 진행률을 아직 못 읽었으면 조금 뒤 다시 읽는다(재시도는 잠잠 예약마다 3번) */
    protected fun retryProgress(ctx: AppContext, progress: Int) {
        if (progress < 0 && ctx.viewerRetry()) ctx.viewerAgain(VIEWER_RETRY_MS)
    }

    /**
     * 메뉴 제목·진행률을 바탕 규칙으로 적는다: 편 책 기록의 책이 이 제목의 읽고 있는 책이면 그 책을 읽는 중이고(같은 제목의 책이 여럿이어도
     * 목록 순서로 다른 책으로 바꾸지 않는다) 진행률만, 아니면(서비스가 꺼졌다 켜져 '책 모름'·다른 책·그 책이 목록에 없음) 이 제목의 책을
     * 편 것으로 다시 적는다(그래야 그 뒤 시간이 이 책에). [force] 면 같은 책이어도 편 것으로.
     * 진행률은 편 책 기록의 책에(서비스가 꺼진 동안 다른 책을 폈을 수 있다: '책 모름'이면 메뉴·서재에서 제목을 볼 때까지 적지 않는다).
     */
    protected fun recordShown(ctx: AppContext, info: ViewerInfo, force: Boolean = false) {
        info.title?.let { title ->
            if (force || ctx.recorder.recordedBook(ctx.pkg)?.hasTitle(title) != true) {
                ctx.opened(Seen(title, info.author, info.progress), ctx.unknownOpenAt)
                return
            }
        }
        if (info.progress < 0) return
        val book = ctx.recorder.recordedBook(ctx.pkg) ?: return
        ctx.recorder.status(book.id, info.progress)
    }

    override fun beforeOpened(ctx: AppContext) {
        // 앞서 눌러 내려받기만 한 칸이 그 뒤 연 다른 책 대신 들어가지 않게
        clicked = null
        unknownClick = null
    }

    /**
     * 요소 이름 없이 자리로 칸을 찾는 서재(리디·휴대폰 밀리)는 화면이 움직이는 중이었을 수 있어, 찍은 뒤에도 같은 자리에 있는 칸만 자른다.
     * 표지 위에 받기 단추 등을 겹치는 서재(e-ink 밀리·리디 작품 화면)도 읽은 뒤 찍기 전에 겹쳤을 수 있어 다시 본다(겹친 칸은 표지 자리가 없다).
     * 같은 책 칸이 둘이면(킨들 '이어 읽기' 표지와 표시에 가려 자르지 않는 칸) 표지 자리가 있는 칸으로 본다([ReaderApp.coverStill]).
     */
    override fun stillSpots(ctx: AppContext, spots: List<CoverSpot>): List<CoverSpot> {
        if (!app.cellsByPosition && app.coverOverlays.isEmpty()) return spots
        val now = ctx.root?.let(::readShelf).orEmpty().filter { it.thumb != null }
        return spots.filter { app.coverStill(now, it.title, it.rect) }
    }

    /** 찍어도 버려지는 칸(한 가지 색 표지 등)을 계속 찍지 않도록 서재를 볼 때마다 책마다 [EAGER_TRIES]번만 자른다. 더 해 볼 수 있으면 센다. */
    private fun tryCover(title: String): Boolean {
        val key = normTitle(title)
        val tries = eagerTries.getOrDefault(key, 0)
        if (tries >= EAGER_TRIES) return false
        eagerTries[key] = tries + 1
        return true
    }

    /**
     * 책 파일 읽기가 필요한 앱([ReaderApp.needsBookFiles])의 읽는 화면이 떴는데 책 파일 읽기가 꺼져 있다: 홈으로 돌아오면 묻도록 적어 둔다
     * ([com.woody.pebbledesk.HomeActivity.warnFileAccess]). `다시 보지 않기` 를 누른 앱은 적지 않는다.
     */
    private fun wantFiles(ctx: AppContext) {
        val prefs = HomePrefs(ctx.context)
        if (ctx.pkg in prefs.fileNoticeSkip || ctx.pkg in prefs.fileNoticeWanted) return
        prefs.fileNoticeWanted = prefs.fileNoticeWanted + ctx.pkg
    }

    companion object {
        /** 칸을 누른 뒤 이 안에 읽는 화면이 열리면 그 책을 편 것으로 본다(내려받기를 기다리는 시간) */
        const val CLICK_OPEN_MS = 120_000L
        /** 진행률이 비어 있으면 이 간격으로 다시 읽어 본다 */
        const val VIEWER_RETRY_MS = 1000L
        /** 멈추기 전에 하는 일(바로 자르기·책 상세·최근 줄)의 간격 */
        const val THROTTLE_MS = 1000L
        /** 화면이 바뀐 뒤 이만큼은 바로 자르지 않는다 */
        private const val TRANSITION_MS = 1000L
        /** 바로 자르기를 서재 방문마다 책 한 권에 해 보는 최대 횟수 */
        private const val EAGER_TRIES = 3
    }
}
