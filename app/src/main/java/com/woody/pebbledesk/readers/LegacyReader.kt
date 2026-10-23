package com.woody.pebbledesk.readers

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.woody.pebbledesk.HomeSignals
import com.woody.pebbledesk.OpenSteps
import com.woody.pebbledesk.Seen
import com.woody.pebbledesk.ShelfBook
import com.woody.pebbledesk.ShelfRecorder
import com.woody.pebbledesk.hasTitle
import com.woody.pebbledesk.Target
import com.woody.pebbledesk.WatchLog
import com.woody.pebbledesk.BookShelf
import com.woody.pebbledesk.normTitle
import com.woody.pebbledesk.sameTitle
import java.io.File

/**
 * 옮기는 동안만 쓰는 어댑터(docs/reader-adapter-plan.md 3단계 `Legacy`): 아직 그 앱 파일로 옮기지 않은 특수 앱의 처리를
 * 예전 서비스 코드 그대로, [ReaderApp] 의 표시(`fileState`·`viewerTabs`·`confirmsOpen`·`clickOnly`·`recent`·
 * `searchesBookFile`·`fileBook`)로 갈라 둔다. 표시가 없는 앱에는 바탕 [ScreenReader] 와 같다.
 * 특수 앱을 하나씩 옮기며(4단계) 그 앱의 갈래·기억을 여기서 지운다.
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
    /** 탭마다 책이 다른 앱([ReaderApp.viewerTabs])에서 책 모름 시각을 적은 탭(같은 모르는 탭에 다시 와도 처음 열린 때를 둔다) */
    private var unknownTab: String? = null
    /** 저장소에서 책 파일을 찾아 본 책(정리한 제목, [searchFile]). 서비스가 살아 있는 동안 한 권에 한 번만 */
    private val fileSearched = mutableSetOf<String>()
    /** 이북 앱 데이터로 책을 아는 앱([ReaderApp.fileBook])에 메뉴 없이 물어볼 남은 횟수(읽는 화면이 앞에 올 때마다 다시 센다) */
    private var fileTries = 0
    /** 파일로만 여는 앱([ReaderApp.fileViewer])의 읽는 화면에서 그 책의 파일을 아직 모를 때 다시 읽어 볼 남은 횟수 */
    private var fileRetries = 0
    /** 마지막으로 메뉴 제목으로 찾은 책(메뉴를 띄워 둔 동안 다시 묻지 않게, 읽는 화면이 앞에 올 때마다 비운다) */
    private var fileTitles: Pair<String, FileBook>? = null
    /** 열기 확인 창(이지뷰어 ▶·다음 파일)에 보인 파일([Cell.file] 꼴)과 그 때 */
    private var openingFiles: Pair<String, Long>? = null
    /** 이 읽는 화면에서 파일 이름마다 정한 책 id(모르면 null. 같은 파일을 보는 동안 판단을 바꾸지 않게) */
    private var viewerFileBooks: Pair<String, String?>? = null
    /** 서재에서 누른 파일 칸(이지뷰어). 읽는 화면에 그 파일 이름이 보이면 편 것으로 본다 */
    private var clickedFiles: Cell? = null
    /** 파일 이름만 보이는 읽는 화면(이지뷰어)에서 이번에 본 파일 이름(바뀌면 다른 책을 연 것) */
    private var viewerFiles: String? = null
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
        // 같은 읽는 화면의 알림이 한 번 더 온 것이면 이 읽는 화면에서 본 파일·누른 칸을 그대로 둔다
        if (!again) {
            viewerFiles = null
            viewerFileBooks = null
            clickedFiles = null
        }
        fileTries = FILE_TRIES
        fileRetries = FILE_RETRIES
        fileTitles = null
        // 서재를 떠나 처음 연 읽는 화면의 때만 적는다(읽는 중 목차 같은 다른 화면에 갔다 와도 처음부터 그 책으로 세게).
        // 줄이 바뀌기를 기다리다 서재에서 다시 읽으러 갔으면 이번 책의 때로 다시 적는다.
        if (app.recent != null && ((!readSince).also { readSince = true } || recentWait.also { recentWait = false })) readerAt = now
        // 사라진 책 상세는 잠깐만, 그리고 사라진 뒤 다른 칸을 누르지 않았을 때만 쓴다
        val gone = webGone.also { webGone = null }
        val clickedLater = gone != null && (clicked?.second ?: 0L) > gone
        val web = webBook.also { webBook = null }?.takeIf { gone == null || (now - gone < WEB_KEEP_MS && !clickedLater) }?.also { openBook(ctx, it) }
        val clickedCell = takeClicked(ctx)
        // 파일 칸은 읽는 화면에 그 파일 이름이 보일 때 편 것으로 본다(누른 뒤 다른 앱으로 다른 파일을 열었을 수 있다)
        clickedCell?.let { cell -> if (cell.file != null) clickedFiles = cell else openBook(ctx, cell) }
        // 누른 칸으로만 책을 아는 앱(BookFusion)이 누른 칸을 모른 채 서재에서 넘어왔다(누름 알림이 없는 칸, 책 상세도 아님): 여기부터 책 모름
        if (app.clickOnly && web == null && clickedCell == null && from in app.shelves) ctx.unknown(now)
        // 홈에서 바로 연 책의 파일도 그렇게
        HomeSignals.take(ctx.pkg)?.takeIf { now - it.second < CLICK_OPEN_MS }?.let { (cell, _) -> if (clickedFiles == null) clickedFiles = cell }
        val name = ctx.screen
        if (app.viewerTabs && name != null && app.isViewer(ctx.context, name)) enterTab(ctx, name, now)
    }

    /**
     * 탭마다 책이 다른 앱([ReaderApp.viewerTabs])에서 읽는 화면 탭 [cls] 가 앞에 왔다. 그 탭으로 마지막으로 안 책이 지금 편 책과
     * 다르면 그 책을 편 것으로 본다. 모르는 탭이면 앞서 편 책과 다를 수 있어 '책 모름'을 적고, 메뉴나 이북 앱 데이터로 알면 탭이 열린 때부터 그 책으로 센다.
     */
    private fun enterTab(ctx: AppContext, cls: String, now: Long) {
        // 그 탭의 책이 읽고 있는 책 목록에 있을 때만(빼거나 지운 책을 되살리지 않게)
        val book = tabBook(ctx, cls)?.let { ctx.recorder.lookup.get(it) }
        if (book == null) {
            // 같은 모르는 탭에 다시 오면(네오리더 설정 화면에 갔다 옴 등) 처음 열린 때를 둔다.
            if (unknownTab != cls || ctx.unknownOpenAt == null) {
                ctx.unknownOpenAt = now
                unknownTab = cls
                ctx.unknown(now)
            }
        } else if (ctx.recorder.recordedBook(ctx.pkg)?.id != book.id) openBook(ctx, Cell(book.title, "", -1, -1, null, Rect()), now, book.id)
    }

    override fun onWindow(ctx: AppContext) {
        // 열기 확인 창(이지뷰어 ▶·다음 파일)이 뜨면 그 파일을 기억한다
        if (app.confirmsOpen) rememberOpening(ctx)
    }

    override fun onClick(ctx: AppContext, click: Click) {
        // 열기 확인 창의 `확인`을 누르는 순간에도 본다(창이 뜰 때 글자가 아직 비어 있었을 수 있다)
        if (app.confirmsOpen) rememberOpening(ctx)
        super.onClick(ctx, click)
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

    override fun openCell(ctx: AppContext, cell: Cell, at: Long?) = openBook(ctx, cell, at)

    /**
     * 책을 폈다. 파일 칸([Cell.file], 이지뷰어)과 읽는 화면의 파일 이름으로 정한 책 [id] 는 제목이 아니라 파일로 책을 가른다([fileBookId]).
     * 이북 앱 데이터로 파일을 안 책(네오리더)은 [byFile]: [id] 의 책, 없으면(null) 같은 제목의 책이 있어도 새 책.
     */
    private fun openBook(ctx: AppContext, cell: Cell, at: Long? = null, id: String? = null, byFile: Boolean = id != null || cell.file != null) {
        val path = cell.file?.let { app.bookFile(ctx.context, it) }
        val name = cell.file?.substringAfterLast('/')
        val fileId = id ?: cell.file?.let { fileBookId(ctx, it, cell.title, path) }
        ctx.opened(Seen(cell.title, cell.author, cell.progress, cell.due), at, if (byFile) Target.Exact(fileId) else Target.ByTitle,
            cell.file?.let(::fileCoverKey), object : OpenSteps {
                override fun found(book: ShelfBook) {
                    // 파일 이름이 제목이던 책(책 정보가 보이기 전에 넣음)은 이제 칸에 보이는 책 정보의 제목·저자로
                    if (fileId != null && name != null) retitle(ctx, book, name, cell)
                    cell.file?.let { rememberShelfFile(ctx, it, path, book) }
                }

                override fun logged(book: ShelfBook) {
                    if (app.viewerTabs) ctx.screen?.takeIf { app.isViewer(ctx.context, it) }?.let { setTabBook(ctx, it, book.id) }
                }
            })
        // 그 밖의 표지는 지금(누른 칸이 반전돼 있거나 화면이 넘어가는 중) 찍지 않고, 서재로 돌아와 화면이 멈췄을 때 잘라 온다.
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
     * 검색 화면의 책 상세 표지, 파일 칸(이지뷰어: 파일로 책을 정하고 표지는 그 파일 열쇠로).
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
                openBook(ctx, seen.firstOrNull { sameTitle(it.title, title) } ?: Cell(title, "", -1, -1, null, Rect()), readerAt)
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
        val statuses = mutableListOf<BookShelf.Status>()
        for (s in seen) {
            val key = s.file?.let(::fileCoverKey)
            val book = if (s.file != null) fileBookId(ctx, s.file, s.title, app.bookFile(ctx.context, s.file))?.let { lookup.get(it) }
                else lookup.byTitle(s.title)
            if (book == null) {
                val thumb = s.thumb?.takeIf(::usableThumb)
                if (thumb != null && (key?.let { !ctx.recorder.hasPendingCover(it) } ?: !ctx.recorder.hasPendingTitle(s.title))) spots += CoverSpot(s.title, thumb, key = key)
                continue
            }
            statuses += BookShelf.Status(book.id, s.progress, s.due, s.author)
            if (s.thumb != null && !lookup.hasCover(book.id) && !ShelfRecorder.coverFromFile(ctx.context, lookup.filePath(book.id))) spots += CoverSpot(s.title, s.thumb, book.id, key)
        }
        // 서재 한 화면의 진행률·반납일을 한 번에 쓴다
        ctx.recorder.statuses(statuses)
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
        // 책이 열려 있는지를 데이터로 아는 앱(KOReader)은 화면을 읽지 않고 데이터로 맞춘다(앞에 온 때 한 번)
        if (app.fileState) return syncFiles(ctx)
        val windows = ctx.windows().takeIf { it.all.isNotEmpty() } ?: return ctx.noWindow()
        val shown = app.readViewer(windows)
        val recorder = ctx.recorder
        val lookup = recorder.lookup
        val file = fileBook(ctx, shown)
        // 파일 이름만 보이는 읽는 화면(이지뷰어): 그 파일로 넣은 책. 처음 보는(바뀐) 파일이면 그때(처음이면 읽는 화면이 열린 때)부터 그 책
        val shownFile = shown.file?.takeIf { file == null && shown.title == null }
        val fileAt = shownFile?.let { viewerFileAt(ctx, it) }
        // 서재에서 누른 파일 칸: 그 파일 이름이 보이면 편 것으로 적는다. 열린 직후 앞 파일 이름이 남아 보이는 동안([VIEWER_SWAP_MS])은 기다리고(그때 다시 읽는다),
        // 그 뒤에도 파일 이름이 한 번도 보이지 않으면(이지뷰어 설정으로 위 줄·파일명을 끔) 누른 칸을 편 것으로, 다른 파일이 보이면 버린다
        clickedFiles?.let { cell ->
            val opened = ctx.viewerOpenedAt ?: 0L
            val wait = opened + VIEWER_SWAP_MS - ctx.now
            when {
                fileAt != null && sameFileName(cell.file.orEmpty().substringAfterLast('/'), shownFile.orEmpty()) -> { clickedFiles = null; openBook(ctx, cell, fileAt) }
                wait > 0 -> ctx.viewerAgain(wait)
                viewerFiles != null -> clickedFiles = null
                else -> { clickedFiles = null; openBook(ctx, cell, opened) }
            }
        }
        // 열기 확인 창으로 안 파일(▶·다음 파일). 그 창은 그 뒤 처음 보는 파일에 한 번만 쓴다(같은 이름의 다른 파일을 나중에 열어도 그 파일로 보지 않게)
        val opening = shownFile?.takeIf { fileAt != null }?.let { openingFor(ctx, it) }?.also { openingFiles = null }
        // 파일로 정한 책은 제목이 아니라 id 로 다룬다(폴더가 다른 같은 이름·제목의 책과 섞지 않게)
        val fileBook = shownFile?.let { name ->
            val decided = viewerFileBooks?.takeIf { fileAt == null && it.first == name }
            if (decided != null) decided.second else fileGuess(ctx, name, opening, fresh = fileAt != null).also { viewerFileBooks = name to it }
        }?.let { lookup.bookOf(it) }
        val fileId = fileBook?.id
        val fileTitle = fileBook?.title
        // 그 책의 파일을 아직 모르면(서재에서 누를 때는 받기 전이던 원격 파일) 이 읽는 화면에서 처음 볼 때, 서재에서 열 때 본 위치로 다시 찾는다
        if (fileAt != null && fileId != null) lookup.get(fileId)?.takeIf { lookup.filePath(it.id) == null }?.let { book ->
            shelfFileOf(ctx, book.id)?.let { app.bookFile(ctx.context, it) }?.let { recorder.setFile(book.id, it); fileInfo(ctx, book, it) }
        }
        // 파일로만 여는 앱(네오리더)은 그 책의 파일을 알 때만 넣는다(메뉴 제목만으로 넣으면 홈에서 열 수 없다).
        val info = file?.let { ViewerInfo(lookup.knownTitle(it.path, it.title), it.author, shown.progress) }
            ?: fileTitle?.let { ViewerInfo(it, "", shown.progress) }
            ?: if (app.fileViewer != null) ViewerInfo(null, "", shown.progress) else shown
        val progress = info.progress
        if (progress >= 0 || info.title != null || shownFile != null) ctx.log(WatchLog.Kind.PROGRESS, "$progress", info.title ?: shownFile.orEmpty())
        // 그 앱 데이터에 아직 안 적혔을 수 있어(책을 여는 동안 적는다) 조금 뒤 다시 본다
        val retryFile = app.fileViewer != null && file == null && fileRetries.also { fileRetries = it - 1 } > 0
        if ((progress < 0 && ctx.viewerRetry()) || retryFile) ctx.viewerAgain(VIEWER_RETRY_MS)
        if (shownFile != null && fileId == null) {
            // 넣은 적 없는 파일을 ▶·다음 파일로 열었다: 확인 창으로 그 파일을 알면 서재에서 연 것처럼 넣는다
            // (실제 경로를 모르면(원격) 확인 창에 보인 위치로. 제목은 파일 이름·그림은 폴더 이름, 읽히는 EPUB 이면 책 정보 [fileInfo])
            if (opening != null) {
                viewerFileBooks = null
                val f = File(opening.path ?: opening.shown)
                val title = if (isImageFile(f.name)) f.parentFile?.name.orEmpty().trimStart('#') else withoutBookFile(f.name)
                return openBook(ctx, Cell(title, "", progress, -1, null, Rect(), file = opening.path ?: opening.shown), fileAt)
            }
            // 다른 앱으로 연 파일은 넣지 않고 여기부터 책 모름(앞서 편 책에 붙이지 않게)
            if (fileAt != null && recorder.openedId(ctx.pkg) != null) ctx.unknown(fileAt)
            return
        }
        // 그림 폴더 책은 쪽을 넘기며 보는 그림을 책의 파일로(홈에서 누르면 그 그림부터 연다)
        if (fileAt != null && fileId != null && isImageFile(shownFile.orEmpty())) lookup.filePath(fileId)?.takeIf(::isImageFile)?.let { cur ->
            File(File(cur).parentFile, shownFile.orEmpty()).takeIf { it.path != cur && it.isFile }?.let { recorder.setFile(fileId, it.path) }
        }
        // 이북 앱 데이터로 파일을 안 책(네오리더)도 제목이 아니라 그 파일로 가른다(파일 안의 제목이 같은 다른 파일과 섞지 않게): 그 파일로 넣은 책,
        // 없으면 파일 없이 넣은 같은 제목의 책(1.5.11 까지 메뉴 제목만으로 넣은 책), 둘 다 없으면 새 책
        val byFile = fileId != null || file != null
        val bookId = fileId ?: file?.let { f -> lookup.byFile(f.path) ?: lookup.withoutFile(info.title.orEmpty()) }
        info.title?.let { title ->
            // '최근 읽은 책' 줄로 아는 앱은 서재를 떠난 뒤 메뉴로 처음 안 책은 앞서 편 책과 같아도 편 것으로 본다(맨 앞으로).
            // 같은 책을 다시 읽으면 그 줄이 그대로라 서재로 돌아와서는 알 수 없다.
            val first = readSince && (!readKnown).also { readKnown = true }
            // 편 책 기록의 책이 이 제목의 읽고 있는 책이면 그 책을 읽는 중이다(같은 제목의 책이 여럿이어도 목록 순서로 다른 책으로 바꾸지 않는다).
            // 아니면(서비스가 꺼졌다 켜져 '책 모름'·다른 책·책 id 없는 옛 줄·그 책이 목록에 없음) 이 제목의 책을 편 것으로 다시 적는다(그래야 그 뒤 시간이 이 책에)
            val opened = first || recorder.recordedBook(ctx.pkg)?.let { if (byFile) it.id == bookId else it.hasTitle(title) } != true
            if (opened) {
                // 메뉴 없이 이북 앱 데이터로 방금 연 책을 알았으면 읽는 화면이 열린 때부터 그 책으로 센다.
                val at = ctx.unknownOpenAt ?: ctx.viewerOpenedAt?.takeIf { file != null && shown.title == null } ?: fileAt
                openBook(ctx, Cell(title, info.author, progress, -1, null, Rect()), at, bookId, byFile)
            }
            if (file != null) recorder.recordedBook(ctx.pkg)?.let { recorder.setFile(it.id, file.path) }
            else if (app.searchesBookFile) searchFile(ctx, title, info.author)
            if (opened) return
        }
        // 진행률은 편 책 기록의 책에(서비스가 꺼진 동안 다른 책을 폈을 수 있다: '책 모름'이면 메뉴·서재에서 제목을 볼 때까지 적지 않는다).
        if (progress < 0) return
        val book = recorder.recordedBook(ctx.pkg) ?: return
        recorder.status(book.id, progress)
    }

    /**
     * 이북 앱 데이터로 책을 아는 앱([ReaderApp.fileBook], 네오리더). 쪽을 넘길 때마다 그 앱을 깨우지 않도록 메뉴 제목이 새로 보일 때(결과는 기억)와
     * 읽는 화면이 앞에 온 뒤 메뉴 없이 [FILE_TRIES]번까지만 묻는다(방금 연 책을 찾으면 그만 묻는다).
     */
    private fun fileBook(ctx: AppContext, shown: ViewerInfo): FileBook? {
        val title = shown.title
        if (title != null) {
            fileTitles?.let { (t, book) -> if (t == title) return book }
            return app.fileBook(ctx.context, shown, 0)?.also { fileTitles = title to it }
        }
        val tries = fileTries
        if (tries <= 0) return null
        fileTries = tries - 1
        val since = ctx.viewerOpenedAt ?: return null
        return app.fileBook(ctx.context, shown, since)?.also { fileTries = 0 }
    }

    /**
     * 읽는 화면에 [name] 파일이 보인다: 이 읽는 화면에서 처음 본 파일이면 읽는 화면이 열린 때, 앞서 본 파일과 다르면 지금, 같으면 null.
     * 이지뷰어는 읽는 화면을 다시 쓸 때 위 줄을 늦게 바꿔 처음에 앞 파일 이름이 보이므로, 열린 지 [VIEWER_SWAP_MS] 안에 바뀌면 열린 때로 본다
     */
    private fun viewerFileAt(ctx: AppContext, name: String): Long? {
        val now = ctx.now
        val opened = ctx.viewerOpenedAt
        val before = viewerFiles
        viewerFiles = name
        return when (before) {
            null -> opened ?: now
            name -> null
            else -> opened?.takeIf { now - it < VIEWER_SWAP_MS } ?: now
        }
    }

    /**
     * 읽는 화면에 보이는 파일 이름 [name] 의 책 id. 서재에서 눌러 넣은 책만, 확인한 것만 쓰고 짐작하지 않는다(모르면 null, 다른 책에 붙이지 않는다):
     * - ▶·다음 파일의 열기 확인 창으로 연 파일 [opening] 이면 그 파일로 넣은 책(실제 경로를 모르는 원격 파일은 서재에서 마지막으로 편 파일과 위치·이름이 모두 같을 때)
     * - 서재에서 눌러 이 읽는 화면을 연 책(그 책을 이 읽는 화면이 열릴 무렵에 폈을 때만, [fresh] 가 아니면 같은 화면에서 이어 읽는 중)이
     *   그 이름의 파일로 넣은 책이거나, 위치를 모르는 파일로 넣은 책인데 서재에서 마지막으로 편 파일 이름이 이것이면 그 책
     * 그 밖(다른 앱으로 연 파일, 같은 이름의 책이 한 권 있어도)은 모른다
     */
    private fun fileGuess(ctx: AppContext, name: String, opening: Opening?, fresh: Boolean): String? {
        val lookup = ctx.recorder.lookup
        if (opening != null) return opening.path?.let(lookup::byPath) ?: lastShelfBook(ctx) { it.equals(opening.shown, ignoreCase = true) }
        val justOpened = !fresh || (ctx.lastOpenedAt ?: 0L) >= (ctx.viewerOpenedAt ?: Long.MAX_VALUE) - OPENED_SLACK_MS
        return ctx.recorder.openedId(ctx.pkg)?.takeIf { justOpened }
            ?.takeIf { it in lookup.idsByFileName(name) || inSameFolder(ctx, it, name) || it == lastShelfBook(ctx) { last -> sameFileName(last.substringAfterLast('/'), name) } }
    }

    /** 그림 폴더 책 [bookId] 의 폴더에 그림 [name] 이 있다(쪽을 넘기면 읽는 화면 위 줄의 그림 이름이 바뀐다) */
    private fun inSameFolder(ctx: AppContext, bookId: String, name: String): Boolean =
        isImageFile(name) && ctx.recorder.lookup.filePath(bookId)?.takeIf(::isImageFile)?.let { File(File(it).parentFile, name).isFile } == true

    /** 이 앱 서재에서 마지막으로 편 파일이 [bookId] 책이면 그 파일(화면에 보인 위치 + 이름) */
    private fun shelfFileOf(ctx: AppContext, bookId: String): String? =
        ctx.prefs.getString("shelf_file_${ctx.pkg}", null)?.takeIf { ctx.prefs.getString("shelf_book_${ctx.pkg}", null) == bookId }

    /** 이 앱 서재에서 마지막으로 편 파일(화면에 보인 위치 + 이름)이 [matches] 이고 그 책의 파일(실제 경로)을 모르면 그 책의 id */
    private fun lastShelfBook(ctx: AppContext, matches: (String) -> Boolean): String? {
        ctx.prefs.getString("shelf_file_${ctx.pkg}", null)?.takeIf(matches) ?: return null
        return ctx.prefs.getString("shelf_book_${ctx.pkg}", null)?.takeIf { ctx.recorder.lookup.filePath(it) == null }
    }

    /** 열기 확인 창에 보인 파일을 기억한다([ReaderApp.openingFile]) */
    private fun rememberOpening(ctx: AppContext) {
        app.openingFile(ctx.windows())?.let { openingFiles = it to ctx.now }
    }

    /** 열기 확인 창으로 연 파일: 창에 보인 위치 + 이름 [shown](이름은 읽는 화면의 실제 이름)과 실제 경로 [path](원격 등 모르면 null) */
    private class Opening(val shown: String, val path: String?)

    /** 조금 전 열기 확인 창에 보인 파일의 이름이 [name] 이면 그 파일 */
    private fun openingFor(ctx: AppContext, name: String): Opening? {
        val (shown, at) = openingFiles ?: return null
        // 창을 본 뒤 곧 열린 파일만(확인을 누르고 읽는 화면이 열리기까지)
        if (at < (ctx.viewerOpenedAt ?: 0L) - OPENING_MS || ctx.now - at > CLICK_OPEN_MS) return null
        val shownName = shown.substringAfterLast('/')
        if (!sameFileName(shownName, name)) return null
        // 확인 창의 이름은 목록처럼 대소문자가 바뀌어 보일 수 있어, 같은 이름이면 읽는 화면 위 줄의 이름(실제 이름)으로
        val real = if (shownName.equals(name, ignoreCase = true)) shown.dropLast(shownName.length) + name else shown
        return Opening(real, app.bookFile(ctx.context, real))
    }

    /**
     * 파일 목록인 서재(이지뷰어)에서 [shown] 파일([Cell.file], 실제 경로 [path])로 [book] 을 폈다: 실제 경로를 알면 책의 파일로 적고(읽는 화면의 파일 이름으로
     * 찾고, EPUB 표지·책 정보를 꺼내고, 홈에서 바로 연다), 그 파일 이름을 이 앱에서 마지막으로 편 책으로 둔다(실제 경로를 모르는 파일도 읽는 화면에서 알아보게)
     */
    private fun rememberShelfFile(ctx: AppContext, shown: String, path: String?, book: ShelfBook) {
        // 이미 다른 파일이 적힌 책(제목이 같은 다른 파일)의 경로는 덮어쓰지 않는다
        path?.takeIf { p -> ctx.recorder.lookup.filePath(book.id)?.equals(p, ignoreCase = true) != false }
            ?.let { ctx.recorder.setFile(book.id, it); fileInfo(ctx, book, it) }
        ctx.prefs.edit().putString("shelf_file_${ctx.pkg}", shown).putString("shelf_book_${ctx.pkg}", book.id).apply()
    }

    /**
     * 파일 이름이 제목인 책(책 정보가 보이지 않던 칸: 받기 전의 원격 파일·격자 보기·TXT 등)은 그 파일이 읽히는 EPUB 이면 책 정보의 제목·저자로 바꾼다.
     * 앱이 보이던 제목(파일 이름)은 [ShelfBook.seenTitle] 로 남아 서재 칸·편 책 기록과 계속 맞는다([BookShelf.setTitle]).
     */
    private fun fileInfo(ctx: AppContext, book: ShelfBook, path: String) {
        // 사용자가 고친 제목(앱이 보인 제목과 다르다)은 두고, 파일 이름이 제목인 책만
        if (book.seenTitle.isNotEmpty() && book.seenTitle != book.title) return
        if (!book.title.equals(withoutBookFile(File(path).name), ignoreCase = true) || !BookFiles.canRead(ctx.context)) return
        val epub = BookFiles.epub(path) ?: return
        if (epub.title.isNotBlank()) ctx.recorder.setTitle(book.id, epub.title)
        if (book.author.isEmpty() && epub.author.isNotBlank()) ctx.recorder.setAuthor(book.id, epub.author)
    }

    /**
     * 파일 [name] 으로 넣은 책 [book] 의 제목이 그 파일 이름이면(받기 전의 원격 파일·격자 보기에서 넣음) 서재 칸 [cell] 에 보이는 책 정보의 제목·저자로 바꾼다.
     * 앱이 보이던 제목(파일 이름)은 [ShelfBook.seenTitle] 로 남는다
     */
    private fun retitle(ctx: AppContext, book: ShelfBook, name: String, cell: Cell) {
        val stem = withoutBookFile(name)
        if (!book.title.equals(stem, ignoreCase = true) || cell.title.equals(stem, ignoreCase = true)) return
        ctx.recorder.setTitle(book.id, cell.title)
        if (book.author.isEmpty() && cell.author.isNotEmpty()) ctx.recorder.setAuthor(book.id, cell.author)
    }

    /**
     * 파일 칸(파일 목록인 서재, 이지뷰어)에 보인 파일 [shown](제목 [title], 실제 경로 [path])의 책 id. 폴더가 다르면 이름·제목이 같아도 다른 책이다
     * (`만화A/01.zip`·`만화B/01.zip`): 실제 경로를 알면 그 파일로 넣은 책, 없으면 제목이 같고 파일이 적히지 않은 책(파일을 적기 전에 넣은 책).
     * 실제 경로를 모르면(원격 등) 제목이 같고 파일이 적히지 않은 책, 없으면 서재에서 마지막으로 편 파일과 위치·이름이 모두 같은 책.
     * 읽고 있는 책이 먼저, 없으면 나간 책(지운 책은 빼고). 없으면 null(새 책)
     */
    private fun fileBookId(ctx: AppContext, shown: String, title: String, path: String?): String? {
        val lookup = ctx.recorder.lookup
        path?.let(lookup::byPath)?.let { return it }
        return lookup.withoutFile(title)
            ?: if (path == null) lastShelfBook(ctx) { it.equals(shown, ignoreCase = true) } else null
    }

    /** 파일 칸에서 잘라 둔 표지의 열쇠(제목이 아니라 그 파일) */
    private fun fileCoverKey(shown: String) = "file:" + shown.lowercase()

    /**
     * 탭마다 책이 다른 앱([ReaderApp.viewerTabs])에서 읽는 화면 [cls](탭)로 마지막으로 편 책의 id(서비스가 다시 시작돼도 남긴다).
     * 1.5.11 까지는 제목(`tab_<탭>`)을 두어 파일 안의 제목이 같은 다른 책과 헷갈렸다. 옛 값은 쓰지 않는다(그 탭은 처음 보는 탭으로).
     */
    private fun tabBook(ctx: AppContext, cls: String) = ctx.prefs.getString("tab_book_$cls", null)

    private fun setTabBook(ctx: AppContext, cls: String, id: String) {
        if (tabBook(ctx, cls) != id) ctx.prefs.edit().putString("tab_book_$cls", id).remove("tab_$cls").apply()
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

    // ── 데이터로 아는 앱 ──

    override fun onHome(ctx: AppContext) {
        if (app.fileState && runCatching { ctx.context.packageManager.getApplicationInfo(ctx.pkg, 0) }.isSuccess) syncFiles(ctx)
    }

    /**
     * 책이 열려 있는지를 데이터로 아는 앱(KOReader, [ReaderApp.fileState])을 그 앱의 데이터로 맞춘다. 그 앱이 앞에 올 때와 홈으로 돌아올 때만
     * 부르고(지켜보지 않는다), 지난번에 맞춘 뒤([since])의 일을 데이터에 적힌 때로 한꺼번에 적는다:
     * - 그 사이 열었다 닫은 앞 책들(앱 안에서 책을 바꿈): 오래된 것부터 연 때에 편 것으로(목록에 없으면 넣는다), 열려 있던 동안만 읽은 시간으로.
     * - 지금 책: 그 사이 열었으면(지금 닫혀 있어도) 연 때에 편 것으로. 그 전부터 열려 있던 책은 편 책 기록이 다르면(서비스가 다시 시작됨 등)
     *   다시 적되, 목록에 없는 책은 처음 보는 파일일 때만 넣는다(완독·빼기 한 책이 그 앱에 열려 있어도 되살아나지 않게). 진행률을 그 책에.
     * 처음 맞출 때는 지금 책만 본다(그 앱의 지난 기록을 모두 넣지 않게). 데이터를 읽을 수 없으면 앞에 있던 동안을 다 센다.
     */
    private fun syncFiles(ctx: AppContext) {
        val recorder = ctx.recorder
        val now = System.currentTimeMillis()
        val key = "sync_${ctx.pkg}"
        // 그 앱은 때를 초로 버려 적으므로 지난번에 맞춘 그 초에 일어난 일도 다시 본다(같은 것은 다시 적히지 않는다)
        val since = ctx.prefs.getLong(key, 0L).let { if (it > 0) it / 1000 * 1000 - 1000 else 0L }
        // 처음 맞출 때는 지금 책만 묻는다(지난 기록의 책들을 읽지 않게)
        val books = app.fileBooks(ctx.context, if (since > 0) since else Long.MAX_VALUE)
            ?: return recorder.viewerShown(ctx.pkg, shown = true, at = now)
        ctx.prefs.edit().putLong(key, now).apply()
        val current = books.lastOrNull() ?: return
        if (since > 0) for (b in books.dropLast(1)) {
            if (b.openedAt > since) {
                fileOpened(ctx, b, b.openedAt)
                recorder.viewerShown(ctx.pkg, shown = true, at = b.openedAt, current = false)
            }
            if (b.closedAt > since) recorder.viewerShown(ctx.pkg, shown = false, at = b.closedAt, current = false)
        }
        markFileViewer(ctx, current)
        val title = recorder.lookup.knownTitle(current.path, current.title)
        val newFile = newFile(ctx, current.path)
        if (recorder.recordedBook(ctx.pkg)?.hasTitle(title) != true) when {
            since > 0 && current.openedAt > since -> return fileOpened(ctx, current, current.openedAt)
            current.closedAt == 0L && (newFile || recorder.lookup.byTitle(title) != null) ->
                return fileOpened(ctx, current, ctx.viewerOpenedAt ?: now)
        }
        saveFile(ctx, title, current.path)
        if (current.progress < 0) return
        val book = recorder.recordedBook(ctx.pkg)?.takeIf { it.hasTitle(title) } ?: return
        recorder.status(book.id, current.progress)
    }

    /** 데이터로 안 책 [b] 를 [at] 에 편 것으로 적는다(목록에 없으면 넣고 맨 앞으로, 그 파일을 적어 둔다) */
    private fun fileOpened(ctx: AppContext, b: FileBook, at: Long) {
        val title = ctx.recorder.lookup.knownTitle(b.path, b.title)
        openBook(ctx, Cell(title, b.author, b.progress, -1, null, Rect()), at)
        saveFile(ctx, title, b.path)
    }

    /**
     * 지금 책의 열림·닫힘을 적는다(읽은 시간은 열려 있던 동안만): 닫혔으면 열려 있던 동안을 지난 일로 적고 지금은 닫힘,
     * 열려 있으면 연 때부터 열림, 모르면(다른 앱이 접근 시각을 바꿈) 열림(앞에 있던 동안을 다 센다). 지금 상태가 앞서 적은 것보다 앞의 때면 앞서 적은 때부터.
     */
    private fun markFileViewer(ctx: AppContext, file: FileBook) {
        val recorder = ctx.recorder
        if (file.closedAt > 0) {
            if (file.openedAt > 0) recorder.viewerShown(ctx.pkg, shown = true, at = file.openedAt, current = false)
            recorder.viewerShown(ctx.pkg, shown = false, at = file.closedAt)
        } else recorder.viewerShown(ctx.pkg, shown = true, at = file.openedAt.takeIf { it > 0 } ?: System.currentTimeMillis())
    }

    /** 그 앱 데이터의 지금 책 파일이 지난번에 본 것과 다른가(처음 보면 참). 지금 것을 적어 둔다(서비스가 다시 시작돼도 남긴다) */
    private fun newFile(ctx: AppContext, path: String): Boolean {
        val key = "file_${ctx.pkg}"
        if (ctx.prefs.getString(key, null) == path) return false
        ctx.prefs.edit().putString(key, path).apply()
        return true
    }

    companion object {
        /** 책 상세가 사라진 뒤 그 책을 기억해 두는 때([webGone]) */
        private const val WEB_KEEP_MS = 3000L
        /** 읽는 화면이 앞에 온 뒤 메뉴 없이 이북 앱 데이터에 물어볼 횟수(책을 여는 동안 연 때가 늦게 적힐 수 있다) */
        private const val FILE_TRIES = 6
        /** 파일로만 여는 앱에서 책의 파일을 모를 때 다시 읽어 볼 횟수 */
        private const val FILE_RETRIES = 5
        /** 열기 확인 창을 본 뒤 이만큼 안에 열린 읽는 화면만 그 창의 파일로 본다 */
        private const val OPENING_MS = 30_000L
        /** 읽는 화면이 열린 뒤 위 줄의 파일 이름이 이만큼 안에 바뀌면 앞 파일 이름이 남아 있던 것(이지뷰어) */
        private const val VIEWER_SWAP_MS = 5_000L
        /** 서재에서 눌러 연 책은 읽는 화면이 열리기 조금 전에 편 것으로 적힐 수 있다 */
        private const val OPENED_SLACK_MS = 5_000L
    }
}
