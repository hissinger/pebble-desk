package com.woody.pebbledesk.readers

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 이북 앱 하나(읽는 책 자동 추가 [com.woody.pebbledesk.ReaderWatchService] 가 쓴다). 화면 이름·요소 이름과, 서재 칸·누른 칸·
 * 읽는 화면을 읽는 동작을 가진다. 기본 동작은 요소 이름으로 찾는 것이고, 다르게 읽어야 하는 앱은 그 앱 파일에서 메서드를 바꾼다
 * (리디·휴대폰 밀리·e-ink 밀리·문리더·킨들). 앱이 업데이트돼 이름이 바뀌면 그 앱 파일만 고친다. 앱 목록은 [ReaderApps].
 */
open class ReaderSpec(
    val pkg: String,
    val classPrefix: String,
    /** 책 칸이 보이는 화면들(서재·탭·책장 안) */
    val shelves: Set<String>,
    /** 읽는 화면들. 오늘 읽은 시간에서도 읽는 화면으로 센다([ReaderApps.isViewer]). */
    val viewers: Set<String>,
    /** 서재 칸의 제목(보기 방식마다 이름이 다르면 여럿) */
    val titles: List<String>,
    /** 서재 칸의 표지 그림(보기 방식·화면마다 이름이 다르면 여럿) */
    val thumbs: List<String>,
    /**
     * 서재 칸의 표지 위에 겹쳐 그리는 요소(받기 단추·받는 중 표시·서비스 종료 막 등). 이것이 보이는 칸은 표지를 자르지 않는다
     * (자르면 그 표시가 표지에 박히고, 표지는 한 번 붙으면 다시 찍지 않는다).
     */
    val coverOverlays: List<String> = emptyList(),
    /** 서재 칸의 저자(앞에서부터 먼저 찾은 것) */
    val authors: List<String>,
    /** 서재 칸의 진행률. null 이면 서재의 진행률을 쓰지 않는다(누른 칸의 글자에 있어도). */
    val progress: String?,
    val due: String?,
    /** 읽는 화면의 진행률 줄(앞에서부터 먼저 찾은 것) */
    val viewerProgress: List<String>,
    /**
     * 읽는 화면이 책을 불러오는 중에 보이는 요소. 이것이 보이는 동안의 `0%` 는 진행률로 보지 않는다
     * (밀리는 불러오는 처음 2초쯤 `0%` 를 보이다가 진짜 값으로 바꾸고, 불러오기 문구는 그 뒤로도 20초쯤 남는다).
     */
    val viewerLoading: String? = null,
    /** 읽는 화면 메뉴의 책 제목(메뉴를 띄웠을 때만 보인다) */
    val viewerTitle: String? = null,
    /** 쪽 번호가 지금 쪽·전체 쪽 두 요소로 나뉜 읽는 화면(북커스 메뉴) */
    val viewerPages: Pair<String, String>? = null,
    /** 쪽으로 셈한 진행률을 올림한다(서재에 보이는 % 와 맞추려고. 북커스는 1/368쪽을 1% 로 보인다) */
    val pagesRoundUp: Boolean = false,
    /** 서재 화면의 '최근 읽은 책' 제목(책 칸을 눌러도 알림이 오지 않는 앱. 읽는 화면에서 돌아오면 이것이 방금 편 책) */
    val recent: String? = null,
    /**
     * 서재를 잠깐만 보고 책으로 넘어가는 앱(알라딘·북커스): 멈추기를 기다리지 않고 표지를 잘라 둔다(멈췄을 때는 모든 앱이 자른다).
     * 밀리는 앱을 다시 열 때 잠깐 칸을 다른 책으로 채우므로 쓰지 않는다.
     */
    val eagerCovers: Boolean = false,
    /** 칸을 눌러도 읽는 화면이 열려야 편 것으로 본다(my YES: 누르면 내려받기·기간 만료 안내가 먼저 뜬다. 밀리: 받지 않은 책은 누르면 받기만 한다. YES24 도서관: 칸 안의 받기·반납 단추도 칸 클릭으로 온다) */
    val openOnViewer: Boolean = false,
    /**
     * 누른 요소가 든 칸을 못 찾았을 때 누른 곳 자리의 칸으로 본다. 끄는 앱: my YES(구매목록 창이 책장 위에 겹쳐 뒤 창의 칸을 고를 수 있다),
     * Libby(칸 안의 `책 열기`·`대출 관리` 단추도 칸 자리에 든다)
     */
    val clickByPlace: Boolean = true,
    /** 앱 화면 전체가 화면 캡처를 막아(검게 찍힌다) 표지를 자르지 않는다(my YES) */
    val secure: Boolean = false,
    /** 서재 편집(지우기·옮기기) 중에만 보이는 요소. 그동안은 칸을 눌러도 책을 고르는 것이라 서재를 읽지 않는다(알라딘) */
    val shelfEditing: String? = null,
    /**
     * 읽는 화면이 따로 액티비티가 아니라 서재 액티비티 안에 뜨는 앱(Libby): 그 묶음의 요소 이름. 이것이 보이는 동안을
     * 읽는 화면 `<서재 액티비티>#<이름>`([viewers] 에 적는다)으로 보고, 읽은 시간도 그 동안만 센다([com.woody.pebbledesk.ReadingLog.viewerShown]).
     * 요소 이름이 아니라 웹 화면 안의 HTML id 로 가르는 앱(Readest, [showsViewer] 를 바꾼다)은 읽는 화면의 이름일 뿐이다.
     */
    val viewerHolder: String? = null,
    /** 실행 화면이 없어 파일로만 여는 앱(네오리더): 파일을 받는 화면과 고르기에 보일 이름, 파일을 모를 때 열 서재 앱 */
    val fileViewer: FileViewer? = null,
    /**
     * 읽는 화면 액티비티가 여럿이고 하나가 책 한 권(탭)인 앱(네오리더 `ReaderTab1~4Activity`): 탭마다 메뉴로 마지막으로 안 책을
     * 따로 기억해 두고, 다른 탭으로 넘어가면 그 탭의 책을 편 것으로 본다. 모르는 탭이면 메뉴로 알 때까지 진행률을 적지 않는다.
     */
    val viewerTabs: Boolean = false,
    /**
     * 책이 열려 있는지를 화면이 아니라 이북 앱 데이터([fileBooks] 의 [FileBook.openedAt]·[FileBook.closedAt])로 아는 앱(KOReader):
     * 화면이 직접 그려져 앱 안에서 책을 열고 닫아도 알림이 없지만 그 때가 데이터에 남아, 그 앱이 앞에 올 때와 홈으로 돌아올 때 지난번 뒤의 일을
     * 한꺼번에 맞춘다([com.woody.pebbledesk.ReaderWatchService.syncFileApps]). 읽은 시간은 책이 열려 있던 동안만 센다([ReaderApps.hostsViewer]).
     */
    val fileState: Boolean = false,
    /** 실행 화면이 있지만 홈에서 책을 누르면 그 책의 파일([fileBooks]·[bookFile] 로 안 경로)을 바로 여는 앱(KOReader·이지뷰어). 파일을 모르면 앱을 연다. */
    val opensBookFile: Boolean = false,
    /**
     * 내 파일을 열지만 어느 파일인지 내놓지 않는 앱(문리더): 읽는 화면 제목으로 안 책의 표지가 없으면 저장소에서 그 제목의 EPUB 을 찾아
     * 그 책의 파일로 적는다(표지는 그릴 때 그 파일에서 꺼낸다). 책 파일 읽기를 허용했을 때만([BookFiles.canRead]).
     */
    val searchesBookFile: Boolean = false,
    /**
     * 책 파일 읽기가 필요하다([BookFiles.canRead]): 꺼져 있으면 책(KOReader)·표지(문리더·네오리더)·표지와 책 정보(이지뷰어·채리뷰어)를 모른다.
     * 깔려 있으면 시작하기에 책 파일 읽기 쪽을 더하고([ReaderApps.fileApps]), 꺼진 채 그 앱의 읽는 화면이 뜨면 홈에서 묻는다. 앱을 더할 때 여기서 정한다.
     */
    val needsBookFiles: Boolean = false,
) {
    /** 서재에 보이는 책 칸들. 기본: 제목 요소마다 그것이 든 칸을 찾아 읽는다. */
    open fun shelfCells(root: AccessibilityNodeInfo): List<Cell> =
        titles.flatMap { root.byId(pkg, it) }.mapNotNull { title -> cellOf(title)?.let(::readCell) }

    /**
     * 누른 책 칸([source] 는 누른 요소, 누르자마자 화면이 넘어가면 null. [text] 는 알림의 글자, [last] 는 방금 읽어 둔 서재 칸).
     * 기본: 누른 요소가 든 칸 → 알림의 글자(제목·`%`) → 알림의 글자 가운데 방금 본 서재 칸 제목.
     */
    open fun clickedCell(source: AccessibilityNodeInfo?, text: List<CharSequence>, windows: AppWindows, last: List<Cell>?): Cell? =
        source?.let { clickedNode(it, windows) } ?: fromClickText(text) ?: shelfCellByText(text, last)

    /** 요소 이름 없이 자리로 칸을 찾는 서재(화면이 움직이는 중이면 어긋나, 표지를 찍은 뒤에도 칸이 그 자리에 있을 때만 자른다) */
    open val cellsByPosition: Boolean get() = false

    /**
     * 찍은 뒤 다시 읽은 서재 칸들 [now](표지 자리가 있는 것만)에 [title] 의 표지 자리가 아직 [rect] 그대로인가([cellsByPosition]·[coverOverlays] 인 앱).
     * 기본: 그 제목의 (마지막) 칸이 그 자리다
     */
    open fun coverStill(now: List<Cell>, title: String, rect: Rect): Boolean = now.associate { it.title to it.thumb }[title] == rect

    /** 메뉴를 띄웠을 때 읽는 화면에 제목이 보이는가(보이면 표지만 눌러 연 책도 알 수 있다) */
    open val tellsTitle: Boolean get() = viewerTitle != null

    /**
     * 읽는 화면: 본문(웹 화면)은 훑지 않고 이름으로 찾은 요소만 읽는다. 진행률은 아래 줄([viewerProgress])에서,
     * 없으면 메뉴의 쪽([viewerPages])으로. 제목([viewerTitleIds])은 메뉴를 띄웠을 때만 보이고, 같은 이름이 여럿이면(목차 같은 목록) 믿지 않는다.
     * 메뉴·알림 창이 위에 떠 있어도 그 아래 읽는 화면의 진행률 줄을 읽도록 그 앱의 창을 모두 본다.
     */
    open fun readViewer(windows: AppWindows): ViewerInfo {
        val roots = windows.all
        fun texts(id: String, inside: List<AccessibilityNodeInfo> = roots) =
            inside.flatMap { it.byId(pkg, id) }.mapNotNull { textOf(it)?.let(::plain) }.filter { it.isNotEmpty() }
        // 진행률 줄이 여럿이면 앱이 보여 주는 % 를 쪽으로 셈한 것보다 먼저 쓴다(리디 아래 좌·우는 사용자가 고른 대로 쪽·% 가 놓인다).
        // 책을 막 열었을 때의 `페이지 계산중 - 40%` 는 진행률이 아니다(my YES). 책을 불러오는 동안 먼저 보이는 `0%` 도 아니다(밀리).
        val loading by lazy { viewerLoading?.let { texts(it).isNotEmpty() } == true }
        val lines = viewerProgress.flatMap { texts(it) }.filterNot { "계산" in it || (percent(it) == 0 && loading) }
        val progress = lines.firstNotNullOfOrNull { percent(it).takeIf { p -> p >= 0 } }
            ?: lines.firstNotNullOfOrNull { viewerPercent(it, pagesRoundUp).takeIf { p -> p >= 0 } }
            ?: viewerPages?.let { (cur, total) -> pagePercent(texts(cur).firstOrNull(), texts(total).firstOrNull(), pagesRoundUp) }
            ?: -1
        val titleRoots = titleRoots(windows)
        val title = viewerTitleIds.firstNotNullOfOrNull { id -> texts(id, titleRoots).distinct().singleOrNull() }
            ?: return ViewerInfo(null, "", progress)
        val (name, author) = splitTitle(title)
        return ViewerInfo(name, author, progress)
    }

    /**
     * 읽는 화면만으로는 어떤 책인지 모르거나 제목이 파일 이름일 때, 그 앱이 내놓은 데이터에서 책(제목·저자·파일 경로)을 찾는다.
     * [info] 는 읽는 화면에서 읽은 것(메뉴 제목이 보이면 그 책을), [since] 는 이 읽는 화면이 열린 때(제목을 모르면 그 뒤에 연 책만 믿는다).
     * 기본: 없음(null)
     */
    open fun fileBook(context: Context, info: ViewerInfo, since: Long): FileBook? = null

    /**
     * 파일로만 여는 앱([fileViewer])의 책 파일을 그 앱 데이터에서 다시 찾는다: 제목이나 파일 이름(확장자 뗌)이 [titles] 중 하나인 책 파일들의 경로
     * (그 앱이 최근에 다룬 것부터. 파일이 옮겨졌거나 예전 판에서 파일 없이 넣은 책을 홈에서 누를 때). 모르면 빈 목록. 기본: 빈 목록
     */
    open fun bookFiles(context: Context, titles: Collection<String>): List<String> = emptyList()

    /**
     * 책이 열려 있는지를 데이터로 아는 앱([fileState]): [since](ms) 뒤에 열었거나 닫은 책들과 지금 책(마지막), 오래된 것부터.
     * 기록이 없으면 빈 목록, 데이터를 읽을 수 없으면 null. 기본: null
     */
    open fun fileBooks(context: Context, since: Long): List<FileBook>? = null

    /**
     * 서재 칸의 파일([Cell.file], 화면에 보이는 위치 + 파일 이름) → 실제 경로(그 책의 파일로 적어 두어 읽는 화면의 파일 이름으로 찾고,
     * EPUB 표지를 꺼내고, 홈에서 바로 연다). 모르면 null. 기본: null
     */
    open fun bookFile(context: Context, shown: String): String? = null

    /**
     * 서재 칸에서 자른 그림 [crop] 이 이북 앱이 표지 대신 그리는 기본 그림(파일 종류 아이콘 등)인가. 그러면 표지로 두지 않는다
     * (표지는 한 번 붙으면 다시 찍지 않으므로, 진짜 표지가 보일 때 자르게). 화면 스레드가 아닌 곳에서 부른다. 기본: 아니다
     */
    open fun placeholderCover(context: Context, crop: Bitmap): Boolean = false

    /** 책을 열기 전에 확인 창으로 그 파일을 보이는 앱(이지뷰어 ▶·다음 파일). 그 창이 뜰 때·누를 때 [openingFile] 을 읽는다 */
    open val confirmsOpen: Boolean get() = false

    /** 확인 창에 보이는 열 파일([Cell.file] 과 같은 꼴: 위치 + 파일 이름). 없으면 null */
    open fun openingFile(windows: AppWindows): String? = null

    /** 읽는 화면 제목을 찾을 요소 이름들(앞에서부터, 글자가 있는 것). 기본: [viewerTitle] 하나 */
    protected open val viewerTitleIds: List<String> get() = listOfNotNull(viewerTitle)

    /** 읽는 화면 제목을 찾을 묶음들. 기본: 그 앱의 창 전체 */
    protected open fun titleRoots(windows: AppWindows): List<AccessibilityNodeInfo> = windows.all

    /** 읽는 화면 제목 글자 → 제목·저자. 기본: 글자 그대로가 제목 */
    protected open fun splitTitle(text: String): Pair<String, String> = text to ""

    /**
     * 서재 액티비티 안에 읽는 화면([viewerHolder])이 떠 있는가. 기본: 그 이름의 요소가 있다(안드로이드 요소 이름으로만 찾는다.
     * 웹 화면 안의 HTML id 로 가르는 앱(Readest)은 바꾼다)
     */
    open fun showsViewer(root: AccessibilityNodeInfo): Boolean = viewerHolder?.let { root.byId(pkg, it).isNotEmpty() } == true

    /**
     * 읽는 화면에 지금 열린 책을 가리키는 그 앱의 열쇠가 늘 보이는 앱(Readest 책 해시)인가. 그러면 열쇠로 책을 가른다
     * ([viewerKey]·[keyBook]·[keyCover]·[keyIntent], `ReaderWatchService.readKeyed`).
     */
    open val viewerKeys: Boolean get() = false

    /** 읽는 화면의 지금 책 열쇠([viewerKeys]). 모르면 null */
    open fun viewerKey(windows: AppWindows): String? = null

    /** 열쇠 [key] 의 책을 그 앱 데이터에서(제목·저자·진행률). 읽을 수 없거나 모르면 null. 기본: null */
    open fun keyBook(context: Context, key: String): FileBook? = null

    /** 열쇠 [key] 의 책 표지(JPEG). 모르면 null. 그림을 다루므로 화면 스레드에서 부르지 않는다. 기본: null */
    open fun keyCover(context: Context, key: String): ByteArray? = null

    /** 홈에서 열쇠 [key] 의 책을 바로 여는 인텐트. 없으면 null(앱을 연다). 기본: null */
    open fun keyIntent(key: String): Intent? = null

    /** 그 앱 데이터에서 표지를 가져올 수 있다([keyCover]): 서재 화면에서 표지를 자르지 않는다. 기본: 아니다 */
    open fun dataCovers(context: Context): Boolean = false

    /** 서재 편집 중인가([shelfEditing]) */
    fun editing(root: AccessibilityNodeInfo): Boolean = shelfEditing?.let { root.byId(pkg, it).isNotEmpty() } == true

    /** 서재의 '최근 읽은 책' 제목([recent], 없으면 null) */
    fun recentTitle(root: AccessibilityNodeInfo): String? =
        recent?.let { id -> root.byId(pkg, id).firstOrNull()?.text?.let(::plain)?.takeIf { it.isNotEmpty() } }

    /** 서재 화면 안 웹 화면의 책 상세(여기서 읽는 화면으로 넘어가면 그 책을 편 것). 기본: 없음 */
    open fun webBook(root: AccessibilityNodeInfo): Cell? = null

    /**
     * 누른 책 칸. 이벤트로 받은 요소는 위(부모)로 올라갈 수 없어서, 그 앱의 창(맨 앞 창부터)을 한 번 훑어 누른 요소를 찾고
     * 거기서 위로 올라가며 제목이 하나 든 가장 가까운 묶음을 칸으로 본다(표지 그림만 눌린 것으로 오기도 한다).
     * 창이 겹치는 앱(my YES 구매목록 창 뒤의 책장)에서 위치로 고르면 뒤 창의 칸을 고를 수 있고, 누르자마자 창이 닫히기도 해서 빨리 찾는다.
     * 못 찾으면 누른 곳의 가운데가 들어 있는 칸([clickByPlace] 인 앱), 그래도 없으면 누른 요소 안에서 읽는다.
     */
    private fun clickedNode(source: AccessibilityNodeInfo, windows: AppWindows): Cell? {
        val at = source.bounds()
        for (r in (listOfNotNull(windows.active) + windows.all).distinct()) {
            val path = pathTo(r, source, at) ?: continue
            // 누른 요소에서 위로: 제목이 처음 보이는 묶음이 그 칸(제목이 둘 이상이면 여러 권을 담은 목록이라 칸이 아니다).
            for (n in path.asReversed()) {
                val titleCount = titles.sumOf { n.count(pkg, it) }
                if (titleCount == 0) continue
                if (titleCount == 1 && thumbs.sumOf { n.count(pkg, it) } > 0) readCell(n)?.let { return it }
                break
            }
        }
        if (clickByPlace) {
            windows.active?.let(::shelfCells)?.firstOrNull { it.cell.contains(at.centerX(), at.centerY()) }?.let { return it }
        }
        return cellOf(source)?.let(::readCell)
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
     * [node] 가 들어 있는 책 칸: 위로 올라가며 제목이 하나뿐인 가장 큰 묶음(그 위는 여러 권을 담은 목록).
     * 표지 그림이 없는 묶음은 책 칸이 아니다(알라딘은 `분야`·`필터` 단추도 같은 이름을 쓴다).
     */
    private fun cellOf(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var cell: AccessibilityNodeInfo? = null
        var n: AccessibilityNodeInfo? = node
        for (i in 0 until CELL_UP) {
            val c = n ?: break
            val titleCount = titles.sumOf { c.count(pkg, it) }
            if (titleCount > 1) break
            if (titleCount == 1) cell = c
            n = c.parent
        }
        return cell?.takeIf { c -> thumbs.sumOf { c.count(pkg, it) } > 0 }
    }

    /** 책 칸 하나: 칸 아래 요소들을 직접 훑어 이름별 글자를 모은다(누른 칸에서는 이름으로 찾기가 안 된다). */
    protected open fun readCell(cell: AccessibilityNodeInfo): Cell? {
        val texts = mutableMapOf<String, String>()
        var thumb: Rect? = null
        var covered = false
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            val id = n.viewIdResourceName?.substringAfter(":id/")
            val t = n.text?.let(::plain)
            if (id != null && !t.isNullOrEmpty()) texts.putIfAbsent(id, t)
            if (id in thumbs && thumb == null) thumb = n.bounds()
            if (id in coverOverlays && n.isVisibleToUser) covered = true
            if (depth < WALK_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(cell, 0)
        fun text(id: String?) = id?.let { texts[it] }.orEmpty()
        val title = titles.map(::text).firstOrNull { it.isNotEmpty() } ?: return null
        val author = authors.map(::text).firstOrNull { it.isNotEmpty() }.orEmpty()
        return Cell(title, authorName(author), percent(text(progress)), dueDate(text(due)), thumb.takeUnless { covered }, cell.bounds())
    }

    /**
     * 누른 칸을 못 읽었을 때: 클릭 이벤트의 글자 목록(제목이 맨 앞, `%`·반납 문구는 모양으로 가린다).
     * 탭·단추(`다운로드`, `책장` 등)를 책으로 넣지 않도록 진행률(`%`)이 함께 있을 때만 책 칸으로 본다.
     */
    private fun fromClickText(list: List<CharSequence>): Cell? {
        val items = list.map(::plain).filter { it.isNotEmpty() }
        val clickProgress = items.firstNotNullOfOrNull { percent(it).takeIf { p -> p >= 0 } } ?: return null
        val title = items.firstOrNull()?.takeIf { percent(it) < 0 && !STATUS_ONLY.matches(it) } ?: return null
        // 반납 문구: `반납 4일 남음`, 북커스 격자 보기 칸은 저자 없이 `9일 남음`
        fun isDue(t: String) = "반납" in t || "만료" in t || STATUS_ONLY.matches(t)
        val dueDay = if (due != null) items.firstOrNull(::isDue)?.let(::dueDate) ?: -1 else -1
        val author = if (authors.isNotEmpty()) authorName(items.getOrNull(1)?.takeIf { "%" !in it && !isDue(it) }.orEmpty()) else ""
        // 서재 진행률을 쓰지 않는 앱(읽는 화면보다 늦게 바뀐다)은 누른 글자의 진행률도 쓰지 않는다.
        return Cell(title, author, if (progress != null) clickProgress else -1, dueDay, null, Rect())
    }

    /**
     * 누른 순간 화면이 이미 읽는 화면으로 넘어가 칸을 못 읽었을 때(북커스·YES24 도서관): 누른 글자 가운데 하나가 방금 본 서재 칸의
     * 제목과 똑같으면 그 칸(YES24 도서관은 도서관 이름이 제목보다 앞에 온다). 탭·단추 글자는 책 제목과 같을 일이 없어 책으로 들어가지 않는다.
     */
    private fun shelfCellByText(list: List<CharSequence>, last: List<Cell>?): Cell? {
        val texts = list.map(::plain).filter { it.isNotEmpty() }.toSet()
        return last?.firstOrNull { it.title in texts }?.copy(stale = true)
    }

    /** 글자가 없는 묶음이면 바로 아래 글자들을 이어 붙인다(북커스 아래 줄: `8` ` / ` `371` 세 조각). */
    private fun textOf(n: AccessibilityNodeInfo): String? = n.text?.toString()
        ?: (0 until n.childCount).mapNotNull { n.getChild(it)?.text?.toString() }.joinToString("").ifEmpty { null }
}
