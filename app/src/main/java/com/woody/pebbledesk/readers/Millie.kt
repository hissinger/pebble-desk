package com.woody.pebbledesk.readers

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs

/**
 * 밀리의서재(크레마 e-ink 판 `kr.co.millie.eink`, 휴대폰 판 [MillieMobile] 과 다른 앱): 다운로드·전체도서 탭은 `BookshelfActivity`,
 * 책장 안(읽고있는 책 등)은 `BookShelfDetailActivity`. 칸에 제목·표지·진행률. 검색 탭(웹 화면) 책 상세의 `바로 읽기`·`이어 읽기` 로도 연다.
 * 읽는 화면은 아래 오른쪽 진행률(불러오는 동안의 `0%` 는 뺀다). 가운데를 누르면 뜨는 위 막대 `epub_navibar` 가운데에 제목(`팩트풀니스`)이
 * 이름 없는 글자로 있다(2026-10-16 페블에서 확인. 몇 초 뒤 저절로 닫힌다).
 * 전체도서 탭·책장 안의 받지 않은 책 칸은 누르면 내려받기만 해서, 누른 칸은 읽는 화면이 열릴 때 편 것으로 본다([openOnViewer]).
 */
object Millie : ReaderSpec(
    pkg = "kr.co.millie.eink", classPrefix = "kr.co.millie",
    shelves = setOf("kr.co.millie.eink.bookshelf.BookshelfActivity", "kr.co.millie.eink.bookshelf.BookShelfDetailActivity"),
    viewers = setOf(
        "kr.co.millie.eink.epub.EPubViewActivity",
        "kr.co.millie.eink.pdf.PDFViewActivity",
        "kr.co.millie.eink.epub.StoryViewActivity",
    ),
    titles = listOf("tv_book_title"), thumbs = listOf("iv_thumbnail"), authors = emptyList(), progress = "bookshelf_cell_reading", due = null,
    viewerProgress = listOf("tv_viewer_add_on_right"), viewerLoading = "epub_loading_message",
    openOnViewer = true,
) {
    /** 읽는 화면 메뉴(위 막대)에 제목이 보인다(이름 없는 글자라 [viewerTitle] 대신 [readViewer] 에서 읽는다) */
    override val tellsTitle: Boolean get() = true

    /** 읽는 화면: 기본 읽기(진행률)에 더해, 메뉴가 떠 있으면 위 막대 안의 글자 하나를 제목으로(뒤로·책갈피 단추는 글자가 없다) */
    override fun readViewer(windows: AppWindows): ViewerInfo {
        val info = super.readViewer(windows)
        val title = windows.all.flatMap { it.byId(pkg, NAVI_BAR) }.flatMap { barTexts(it, 0) }.distinct().singleOrNull() ?: return info
        return ViewerInfo(title, "", info.progress)
    }

    /** 위 막대 안의 글자들(몇 단계만) */
    private fun barTexts(n: AccessibilityNodeInfo, depth: Int): List<String> =
        listOfNotNull(n.text?.let(::plain)?.takeIf { it.isNotEmpty() }) +
            if (depth < 3) (0 until n.childCount).flatMap { i -> n.getChild(i)?.let { barTexts(it, depth + 1) }.orEmpty() } else emptyList()

    private const val NAVI_BAR = "epub_navibar"

    /** 검색 책 상세의 읽기 단추 글자 */
    private val READ_NOW = setOf("바로 읽기", "이어 읽기")
    /** 웹 화면(검색 책 상세)을 훑는 깊이, 같은 줄·같은 왼쪽으로 보는 차이(px) */
    private const val WEB_DEPTH = 40
    private const val ALIGN_PX = 8

    /**
     * 검색(웹 화면)의 책 상세: `바로 읽기` 단추와 같은 줄 맨 왼쪽 단추에 왼쪽을 맞춘, 단추 위의 글자들이 제목·저자다.
     * 표지는 그 왼쪽의 그림. 요소 이름이 없는 웹 화면이라 위치로 찾는다(`바로 읽기` 클릭에는 글자가 없다).
     */
    override fun webBook(root: AccessibilityNodeInfo): Cell? {
        val texts = mutableListOf<Pair<String, Rect>>()
        val images = mutableListOf<Rect>()
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            val b = n.bounds()
            n.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { texts += it to b }
            if (n.className?.endsWith("Image") == true) images += b
            if (depth < WEB_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        val button = texts.firstOrNull { it.first in READ_NOW }?.second ?: return null
        val rowLeft = texts.filter { abs(it.second.top - button.top) < ALIGN_PX }.minOf { it.second.left }
        val above = texts.filter { abs(it.second.left - rowLeft) < ALIGN_PX && it.second.bottom <= button.top }.sortedBy { it.second.top }
        val (title, titleAt) = above.firstOrNull() ?: return null
        val author = authorName(above.getOrNull(1)?.first.orEmpty())
        val cover = images.firstOrNull { it.right <= rowLeft && it.top <= titleAt.bottom && it.bottom >= button.top }
        return Cell(title, author, -1, -1, cover, Rect())
    }
}
