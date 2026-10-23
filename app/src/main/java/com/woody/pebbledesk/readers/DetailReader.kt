package com.woody.pebbledesk.readers

import android.view.accessibility.AccessibilityNodeInfo
import com.woody.pebbledesk.WatchLog

/**
 * 책 상세에서 읽는 화면으로 가는 앱의 판단과 기억(쓰는 앱: BookFusion·밀리 e-ink. 바꾸면 둘 다 시험한다).
 * 서재 화면 안의 책 상세([detail]: BookFusion 책 상세, 밀리 검색 상세)에서 `Read`·`바로 읽기`를 누르면 그 책을 편 것으로 본다(그 단추에는 글자가 없다).
 * 상세는 멈추기를 기다리지 않고 1초마다 기억하고, 단추를 누르면 상세가 먼저 사라지기도 해(BookFusion) 사라진 뒤 [KEEP_MS] 동안은 둔다.
 * 그 사이 다른 칸을 눌렀으면 그 칸이 먼저다. [clickOnly]: 읽는 화면에 제목이 없어 누른 칸으로만 책을 아는 앱(BookFusion) — 누른 칸도 상세도 모른 채
 * 서재에서 넘어오면 책 모름(앞서 편 책에 붙이지 않게).
 */
open class DetailReader(app: ReaderApp, private val clickOnly: Boolean, private val detail: (AccessibilityNodeInfo) -> Cell?) : ScreenReader(app) {
    /** 보고 있는 책 상세 */
    private var book: Cell? = null
    /** 그 상세가 화면에서 사라진 때 */
    private var gone: Long? = null
    private var lastRead = 0L

    /** 기억한 상세(사라진 지 [KEEP_MS] 안, 그 뒤 다른 칸을 누르지 않았을 때)면 그 책, 없으면 바탕(누른 칸). 둘 다 없고 [clickOnly] 면 서재에서 넘어왔을 때 책 모름 */
    override fun onEnterViewer(ctx: AppContext, from: String?, again: Boolean) {
        val now = ctx.now
        val gone = gone.also { gone = null }
        val clickedLater = gone != null && (clicked?.second ?: 0L) > gone
        val web = book.also { book = null }?.takeIf { gone == null || (now - gone < KEEP_MS && !clickedLater) }?.also { openCell(ctx, it) }
        val clickedCell = takeClicked(ctx)
        clickedCell?.let { openCell(ctx, it) }
        if (clickOnly && web == null && clickedCell == null && from in app.shelves) ctx.unknown(now)
    }

    /**
     * 책 칸이 아닌 것을 눌렀다: `바로 읽기`일 수 있으니 지금 보이는 책 상세를 기억하고 표지를 찍어 둔다
     * (웹 화면은 다 그려진 뒤 알림이 없을 때가 있어, 1초마다 기억해 둔 것이 비어 있을 수 있다).
     */
    override fun onClickNoCell(ctx: AppContext) {
        val root = ctx.root ?: return
        val web = detail(root) ?: return
        book = web
        gone = null
        val found = ctx.recorder.lookup.byTitle(web.title)
        if (web.thumb != null && (found == null || !ctx.recorder.lookup.hasCover(found.id))) ctx.capture(listOf(CoverSpot(web.title, web.thumb)))
    }

    override fun beforeOpened(ctx: AppContext) {
        super.beforeOpened(ctx)
        book = null
        gone = null
    }

    /** 책 상세는 `바로 읽기`를 누르자마자 사라지므로 멈추기를 기다리지 않고 1초마다 기억해 둔다 */
    override fun onShelfLive(ctx: AppContext, force: Boolean) {
        val now = ctx.now
        if (force || now - lastRead >= THROTTLE_MS) {
            lastRead = now
            ctx.root?.let { root ->
                val web = detail(root)
                if (web != null) { book = web; gone = null }
                else if (book != null && now - (gone ?: now.also { gone = it }) >= KEEP_MS) { book = null; gone = null }
            }
        }
        super.onShelfLive(ctx, force)
    }

    /** 서재가 멈췄다: 책 상세를 다시 기억하고, 그 표지(목록에 있는데 표지가 없으면)를 칸 표지와 한 번에 찍는다 */
    override fun onShelf(ctx: AppContext) {
        val root = ctx.root ?: return ctx.noWindow()
        val seen = readShelf(root)
        ctx.log(WatchLog.Kind.SHELF, "${seen.size}")
        val spots = mutableListOf<CoverSpot>()
        detail(root)?.let { web ->
            book = web
            gone = null
            val found = ctx.recorder.lookup.byTitle(web.title)
            if (found != null && web.thumb != null && !ctx.recorder.lookup.hasCover(found.id)) spots += CoverSpot(web.title, web.thumb)
        }
        shelfStatuses(ctx, seen, spots)
        if (!app.secure) ctx.capture(spots)
    }

    companion object {
        /** 책 상세가 사라진 뒤 그 책을 기억해 두는 때 */
        private const val KEEP_MS = 3000L
    }
}
