package com.woody.pebbledesk.readers

import android.view.accessibility.AccessibilityNodeInfo

/**
 * 밀리의서재(휴대폰·태블릿 판 `kr.co.millie.millieshelf`, e-ink 판 [Millie] 와 다른 앱): 첫 화면 `MainTabActivity` 의 내서재 탭이 통째로
 * 웹 화면이라 요소 이름이 없다. 책 칸의 표지 단추 글자가 `제목/저자 지음 / 옮긴이 옮김 썸네일`(오디오북은 앞에 `오디오북 리더 … 완독` 같은 표시),
 * 제목 단추는 `제목 저자`. 눌러도 알림의 글자는 비어 있어 누른 단추의 글자로 안다. 서재에 진행률은 없다. 웹 화면은 처음 한 번 요청받은 뒤에야
 * 안의 요소를 내놓는다.
 * 읽는 화면(`MillieViewerActivity`, 화면 캡처 막힘)은 가운데를 눌러 메뉴를 띄우면 위 막대(`viewer_top_bar`)에 제목 `tv_title`,
 * 아래에 진행률 `tv_per`(`0%`)·쪽 `tv_current_page`/`tv_total_page`. 책을 열 때 뜨는 '읽던 페이지' 안내 창도 제목이 `tv_title`(`안내`)이다.
 */
object MillieMobile : ReaderSpec(
    pkg = "kr.co.millie.millieshelf", classPrefix = "kr.co.millie.millieshelf",
    shelves = setOf("kr.co.millie.millieshelf.kotlin.ui.MainTabActivity"),
    viewers = setOf(
        "kr.co.millie.millieshelf.kotlin.ui.viewer.millieviewer.MillieViewerActivity",
        "kr.co.millie.millieshelf.kotlin.ui.viewer.StoryViewerActivity",
        "kr.co.millie.millieshelf.kotlin.ui.viewer.webtoon.ui.WebtoonViewerActivity",
        "kr.co.millie.millieshelf.kotlin.ui.viewer.pdf.ui.PdfViewerActivity",
        "kr.co.millie.millieshelf.viewer.pdf.ui.PDFWebViewActivity",
    ),
    titles = emptyList(), thumbs = emptyList(), authors = emptyList(), progress = null, due = null,
    viewerProgress = listOf("tv_viewer_add_on_right", "tv_per"), viewerPages = "tv_current_page" to "tv_total_page",
    viewerTitle = "tv_title", eagerCovers = true,
) {
    override val cellsByPosition get() = true

    private const val THUMB = " 썸네일"

    /** 제목 앞에 붙는 표시(`오디오북 리더 오디오북 리더 완독 완독 하나님을 아는 지식`) */
    private val BADGES = Regex("""^((오디오북|리더|완독)\s+)+""")

    /** 웹 서재: 글자가 표지 단추 꼴인 요소가 책 칸이고 그 자리가 표지다. 같은 책이 여럿이면 가장 큰 표지. */
    override fun shelfCells(root: AccessibilityNodeInfo): List<Cell> {
        val found = mutableMapOf<String, Cell>()
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            coverButton(n)?.let { c ->
                if ((found[c.title]?.cell?.width() ?: -1) < c.cell.width()) found[c.title] = c
                return
            }
            if (depth < DEEP_WALK_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        return found.values.toList()
    }

    /** 누른 단추: 표지 단추면 그 글자로, 제목 단추(`제목 저자`)면 방금 읽은 서재 칸 가운데 제목이 그 글자에 든 칸 */
    override fun clickedCell(source: AccessibilityNodeInfo?, text: List<CharSequence>, windows: AppWindows, last: List<Cell>?): Cell? {
        source?.let(::coverButton)?.let { return it }
        val label = source?.let { (it.text ?: it.contentDescription)?.let(::plain) }.orEmpty()
        return last?.filter { it.title.length >= 2 && it.title in label }?.maxByOrNull { it.title.length }
    }

    /** 제목은 위 막대 안의 것만(안내 창의 `안내` 를 책 제목으로 보지 않게) */
    override fun titleRoots(windows: AppWindows): List<AccessibilityNodeInfo> = windows.all.flatMap { it.byId(pkg, "viewer_top_bar") }

    /** 표지 단추(`팩트풀니스/한스 로슬링 지음 / 이창신 옮김 썸네일`)면 그 책 칸 */
    private fun coverButton(n: AccessibilityNodeInfo): Cell? {
        val (title, author) = (n.text ?: n.contentDescription)?.let { coverText(plain(it)) } ?: return null
        val r = n.bounds()
        return Cell(title, authorName(author), -1, -1, r, r)
    }

    /** 표지 단추 글자 → 제목·저자. 표지 단추가 아니면 null. */
    internal fun coverText(text: String): Pair<String, String>? {
        val body = text.takeIf { it.endsWith(THUMB) }?.removeSuffix(THUMB) ?: return null
        val slash = body.indexOf('/').takeIf { it > 0 } ?: return null
        val title = BADGES.replace(body.substring(0, slash), "").trim().ifEmpty { return null }
        return title to body.substring(slash + 1).trim()
    }
}
