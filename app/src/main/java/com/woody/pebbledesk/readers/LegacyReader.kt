package com.woody.pebbledesk.readers

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.woody.pebbledesk.BookShelf
import com.woody.pebbledesk.Seen
import com.woody.pebbledesk.ShelfRecorder
import com.woody.pebbledesk.WatchLog
import com.woody.pebbledesk.hasTitle
import com.woody.pebbledesk.normTitle
import com.woody.pebbledesk.sameTitle
import java.io.File

/**
 * 옮기는 동안만 쓰는 어댑터(docs/reader-adapter-plan.md 3단계 `Legacy`): 아직 그 앱 파일로 옮기지 않은 특수 앱의 처리를
 * 예전 서비스 코드 그대로, [ReaderApp] 의 표시(`clickOnly`·`recent`·`searchesBookFile`, 책 상세 [ReaderApp.webBook])로 갈라 둔다.
 * 표시가 없는 앱에는 바탕 [ScreenReader] 와 같다. 특수 앱을 하나씩 옮기며(4단계) 그 앱의 갈래·기억을 여기서 지운다.
 */
open class LegacyReader(app: ReaderApp) : ScreenReader(app) {
    /** 검색(웹 화면)에서 보고 있는 책 상세. 여기서 읽는 화면으로 넘어가면 이 책을 편 것으로 본다(`바로 읽기` 클릭에는 글자가 없다). */
    private var webBook: Cell? = null
    /**
     * [webBook] 의 책 상세가 화면에서 사라진 때. 누른 단추에 알림이 없는 앱(BookFusion `Read`)은 단추를 누르면 상세가 먼저 사라지고 조금 뒤 읽는 화면이 떠,
     * 사라진 뒤 [WEB_KEEP_MS] 동안은 기억을 둔다
     */
    private var webGone: Long? = null
    private var lastWebRead = 0L
    /** 저장소에서 책 파일을 찾아 본 책(정리한 제목, [searchFile]). 서비스가 살아 있는 동안 한 권에 한 번만 */
    private val fileSearched = mutableSetOf<String>()
    /** 서재에서 읽는 화면으로 갔다 왔다(서재의 '최근 읽은 책' 줄로 어떤 책인지 안다) */
    private var readSince = false
    /** 서재를 떠나 읽는 화면이 처음 열린 때 */
    private var readerAt: Long? = null
    /** 읽는 화면 메뉴로 이미 책을 알았다('최근 읽은 책' 줄은 늦게 바뀌어 다시 보지 않는다) */
    private var readKnown = false
    /** 서재로 돌아왔는데 '최근 읽은 책' 줄이 아직 읽으러 가기 전 그대로다(바뀌기를 기다린다) */
    private var recentWait = false

    // ── 화면 ──

    override fun onEnterViewer(ctx: AppContext, from: String?, again: Boolean) {
        val now = ctx.now
        // 서재를 떠나 처음 연 읽는 화면의 때만 적는다(읽는 중 목차 같은 다른 화면에 갔다 와도 처음부터 그 책으로 세게).
        // 줄이 바뀌기를 기다리다 서재에서 다시 읽으러 갔으면 이번 책의 때로 다시 적는다.
        if (app.recent != null && ((!readSince).also { readSince = true } || recentWait.also { recentWait = false })) readerAt = now
        // 사라진 책 상세는 잠깐만, 그리고 사라진 뒤 다른 칸을 누르지 않았을 때만 쓴다
        val gone = webGone.also { webGone = null }
        val clickedLater = gone != null && (clicked?.second ?: 0L) > gone
        val web = webBook.also { webBook = null }?.takeIf { gone == null || (now - gone < WEB_KEEP_MS && !clickedLater) }?.also { openCell(ctx, it) }
        val clickedCell = takeClicked(ctx)
        clickedCell?.let { openCell(ctx, it) }
        // 누른 칸으로만 책을 아는 앱(BookFusion)이 누른 칸을 모른 채 서재에서 넘어왔다(누름 알림이 없는 칸, 책 상세도 아님): 여기부터 책 모름
        if (app.clickOnly && web == null && clickedCell == null && from in app.shelves) ctx.unknown(now)
    }

    /**
     * 책 칸이 아닌 것을 눌렀다: 검색 화면의 `바로 읽기`일 수 있으니 지금 보이는 책 상세를 기억하고 표지를 찍어 둔다
     * (웹 화면은 다 그려진 뒤 알림이 없을 때가 있어, 1초마다 기억해 둔 것이 비어 있을 수 있다).
     */
    override fun onClickNoCell(ctx: AppContext) {
        val root = ctx.root ?: return
        val web = app.webBook(root) ?: return
        webBook = web
        webGone = null
        val book = ctx.recorder.lookup.byTitle(web.title)
        if (web.thumb != null && (book == null || !ctx.recorder.lookup.hasCover(book.id))) ctx.capture(listOf(CoverSpot(web.title, web.thumb)))
    }

    override fun beforeOpened(ctx: AppContext) {
        super.beforeOpened(ctx)
        webBook = null
        webGone = null
    }

    // ── 서재 ──

    override fun onShelfLive(ctx: AppContext, force: Boolean) {
        // 검색 화면의 책 상세는 `바로 읽기`를 누르자마자 사라지므로 멈추기를 기다리지 않고 1초마다 기억해 둔다.
        val now = ctx.now
        if (force || now - lastWebRead >= THROTTLE_MS) {
            lastWebRead = now
            ctx.root?.let { root ->
                val web = app.webBook(root)
                if (web != null) { webBook = web; webGone = null }
                else if (webBook != null && now - (webGone ?: now.also { webGone = it }) >= WEB_KEEP_MS) { webBook = null; webGone = null }
                // 서재를 잠깐만 보고 책으로 넘어가도 읽으러 가기 전의 '최근 읽은 책' 줄을 알도록 멈추기를 기다리지 않는다.
                rememberRecent(ctx, root)
            }
        }
        super.onShelfLive(ctx, force)
    }

    /**
     * 서재 화면이 멈췄다: 바탕([ScreenReader.onShelf])에 더해, 책 칸을 눌러도 알림이 오지 않는 앱(알라딘)의 '최근 읽은 책' 줄,
     * 검색 화면의 책 상세 표지.
     */
    override fun onShelf(ctx: AppContext) {
        val root = ctx.root ?: return ctx.noWindow()
        val seen = readShelf(root)
        ctx.log(WatchLog.Kind.SHELF, "${seen.size}")
        val lookup = ctx.recorder.lookup
        // 책 칸을 눌러도 알림이 오지 않는 앱(알라딘): 읽는 화면에서 돌아오면 서재 아래 '최근 읽은 책'이 방금 편 책이다.
        // 그 줄이 읽으러 가기 전 그대로(모르면 그대로로 본다)면 믿지 않고 바뀌기를 기다린다: 알라딘은 줄을 늦게 바꾸고, 내 파일(PDF 등)은 줄에 오르지 않는다.
        val recent = app.recentTitle(root)
        if (readSince) {
            if (readKnown) {
                readKnown = false
                readSince = false
            } else if (recent != null && recentBefore(ctx).let { it == null || it == recent }) {
                // 몰랐으면 처음 본 줄을 읽으러 가기 전의 줄로 적어 둔다(그래야 바뀌면 알아본다).
                setRecentBefore(ctx, recent)
                if (!recentWait) {
                    recentWait = true
                    ctx.log(WatchLog.Kind.RECENT_WAIT, recent)
                }
            } else recent?.let { title ->
                readSince = false
                recentWait = false
                openCell(ctx, seen.firstOrNull { sameTitle(it.title, title) } ?: Cell(title, "", -1, -1, null, Rect()), readerAt)
            }
        }
        rememberRecent(ctx, root)
        val spots = mutableListOf<CoverSpot>()
        // 검색 화면의 책 상세에 보이는 표지(목록에 있는데 표지가 없으면)
        app.webBook(root)?.let { web ->
            webBook = web
            webGone = null
            val book = lookup.byTitle(web.title)
            if (book != null && web.thumb != null && !lookup.hasCover(book.id)) spots += CoverSpot(web.title, web.thumb)
        }
        shelfStatuses(ctx, seen, spots)
        if (!app.secure) ctx.capture(spots)
    }

    /** 읽으러 가기 전에 본 서재의 '최근 읽은 책'([ReaderApp.recent]) */
    private fun recentBefore(ctx: AppContext) = ctx.prefs.getString("recent_${ctx.pkg}", null)

    /** [title] 이 null 이면 지운다(모른다) */
    private fun setRecentBefore(ctx: AppContext, title: String?) {
        if (recentBefore(ctx) != title) ctx.prefs.edit().putString("recent_${ctx.pkg}", title).apply()
    }

    /**
     * 읽으러 가기 전의 '최근 읽은 책' 줄을 적어 둔다(돌아와 아직 기다리는 동안은 고치지 않는다).
     * 줄이 안 보이면(아직 안 그려짐) 지운다: 앞서 적은 줄과 비교하면 그사이 바뀐 줄을 방금 편 책으로 잘못 본다.
     */
    private fun rememberRecent(ctx: AppContext, root: AccessibilityNodeInfo) {
        if (app.recent == null || readSince) return
        setRecentBefore(ctx, app.recentTitle(root))
    }

    // ── 읽는 화면 ──

    override fun onViewer(ctx: AppContext) {
        val shown = readShown(ctx) ?: return
        val recorder = ctx.recorder
        val progress = shown.progress
        logShown(ctx, shown)
        retryProgress(ctx, progress)
        shown.title?.let { title ->
            // '최근 읽은 책' 줄로 아는 앱은 서재를 떠난 뒤 메뉴로 처음 안 책은 앞서 편 책과 같아도 편 것으로 본다(맨 앞으로).
            // 같은 책을 다시 읽으면 그 줄이 그대로라 서재로 돌아와서는 알 수 없다.
            val first = readSince && (!readKnown).also { readKnown = true }
            // 편 책 기록의 책이 이 제목의 읽고 있는 책이면 그 책을 읽는 중이다(같은 제목의 책이 여럿이어도 목록 순서로 다른 책으로 바꾸지 않는다).
            // 아니면(서비스가 꺼졌다 켜져 '책 모름'·다른 책·책 id 없는 옛 줄·그 책이 목록에 없음) 이 제목의 책을 편 것으로 다시 적는다(그래야 그 뒤 시간이 이 책에)
            val opened = first || recorder.recordedBook(ctx.pkg)?.hasTitle(title) != true
            if (opened) ctx.opened(Seen(title, shown.author, progress), ctx.unknownOpenAt)
            if (app.searchesBookFile) searchFile(ctx, title, shown.author)
            if (opened) return
        }
        // 진행률은 편 책 기록의 책에(서비스가 꺼진 동안 다른 책을 폈을 수 있다: '책 모름'이면 메뉴·서재에서 제목을 볼 때까지 적지 않는다).
        if (progress < 0) return
        val book = recorder.recordedBook(ctx.pkg) ?: return
        recorder.status(book.id, progress)
    }

    /**
     * 연 파일을 내놓지 않는 리더([ReaderApp.searchesBookFile], 문리더)의 책 [title]: 표지도 파일도 모르면 저장소에서 그 제목의 EPUB 을 찾아
     * 그 책의 파일로 적는다(표지는 그릴 때 꺼낸다). 저장소를 훑으므로 뒤에서, 책마다 한 번만(책 파일 읽기를 허용하기 전에는 세지 않는다).
     */
    private fun searchFile(ctx: AppContext, title: String, author: String) {
        val key = normTitle(title)
        if (key in fileSearched) return
        val lookup = ctx.recorder.lookup
        val book = lookup.byTitle(title) ?: return
        if (lookup.hasCover(book.id) || lookup.filePath(book.id) != null || !BookFiles.canRead(ctx.context)) return
        fileSearched += key
        ctx.inBackground({ BookFiles.findEpub(title, author) }) { result ->
            val path = result.getOrNull() ?: return@inBackground
            ctx.log(WatchLog.Kind.FILE, title, File(path).name)
            saveFile(ctx, title, path)
        }
    }

    /** 이북 앱이 [title] 로 연 파일을 그 책에 적어 둔다(홈에서 그 파일을 바로 열고, 표지가 없으면 그릴 때 그 파일에서 꺼낸다) */
    private fun saveFile(ctx: AppContext, title: String, path: String) {
        ctx.recorder.lookup.byTitle(title)?.let { ctx.recorder.setFile(it.id, path) }
    }

    companion object {
        /** 책 상세가 사라진 뒤 그 책을 기억해 두는 때([webGone]) */
        private const val WEB_KEEP_MS = 3000L
    }
}
