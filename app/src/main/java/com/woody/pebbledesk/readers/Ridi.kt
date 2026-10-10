package com.woody.pebbledesk.readers

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 리디: 책은 교보eBook처럼 읽는 화면 메뉴의 제목(`title`)으로 안다. 서재(React Native, 내 서재 탭과 작품 화면)는 요소 이름이 없어
 * 칸 설명·글자로 표지만 잘라 둔다(칸을 눌러도 알림이 없다). 진행률은 뷰어 설정의 '하단 좌/우 정보 표시'를 켜면 아래 좌·우
 * (`15 / 640`, `2%`), 꺼 두면 메뉴의 쪽(`14 / 626`).
 */
object Ridi : ReaderSpec(
    pkg = "com.initialcoms.ridi", classPrefix = "com.ridi",
    shelves = setOf("com.ridi.books.viewer.main.activity.MainActivity"),
    viewers = setOf(
        "com.ridi.books.viewer.reader.epub.EPubReaderActivity",
        "com.ridi.books.viewer.reader.pagebased.pdf.PDFReaderActivity",
        "com.ridi.books.viewer.reader.pagebased.comic.ComicBookReaderActivity",
    ),
    titles = emptyList(), thumbs = emptyList(), authors = emptyList(), progress = null, due = null,
    viewerProgress = listOf("reader_right_info", "reader_left_info", "reader_toolbar_page_text"), viewerTitle = "title",
    eagerCovers = true,
) {
    override val cellsByPosition get() = true

    /** 서재 표시 요소(내 서재 머리). 이름에 `패키지:id/` 가 없어 이름으로 찾기가 안 되어 훑으면서 본다. */
    private const val SHELF_MARKER = "libraryGnbMenus"

    /** 표지로 쓸 그림의 최소 폭(목록 보기 표지 86px 는 받고 아래 '최근 본' 줄의 66px 는 뺀다) */
    private const val MIN_COVER_PX = 80

    /**
     * 요소 이름이 없는 서재: 설명이 그 안의 글자(제목)로 시작하고 표지 모양 그림이 든 묶음이 책 칸이다.
     * 서재 표시 요소가 없는 화면(홈·검색 탭)은 읽지 않고, 위에 뜬 화면(작품 화면)에 가려진 칸은 뺀다.
     * 같은 책이 여럿이면(아래 '최근 본' 줄) 가장 큰 표지.
     */
    override fun shelfCells(root: AccessibilityNodeInfo): List<Cell> {
        var shelf = false
        val found = mutableMapOf<String, Cell>()
        /** [over]: 위 단계들에서 나중에 그려지는(drawingOrder 가 큰) 형제들의 자리. 그중 하나가 칸을 다 덮으면 위에 뜬 화면에 가려진 칸이다. */
        fun walk(n: AccessibilityNodeInfo, depth: Int, over: List<Rect>) {
            // 같은 화면이 품은 홈·검색 탭의 웹 화면은 훑지 않는다.
            if (n.isWebView()) return
            if (n.viewIdResourceName == SHELF_MARKER) shelf = true
            val desc = n.contentDescription?.toString()
            if (!desc.isNullOrEmpty()) cell(n, desc)?.let { s ->
                val old = found[s.title]
                if ((old?.thumb == null || s.thumb!!.width() > old.thumb.width()) && over.none { it.contains(s.cell) }) found[s.title] = s
                return
            }
            if (depth >= DEEP_WALK_DEPTH) return
            val kids = (0 until n.childCount).mapNotNull { n.getChild(it) }
            val order = kids.map { it.drawingOrder }
            val rects = kids.map { it.bounds() }
            kids.forEachIndexed { i, k ->
                val above = kids.indices.filter { j -> order[j] > order[i] && !rects[j].isEmpty }.map { rects[it] }
                walk(k, depth + 1, if (above.isEmpty()) over else over + above)
            }
        }
        walk(root, 0, emptyList())
        return if (shelf) found.values.toList() else emptyList()
    }

    /**
     * [cell] 안의 글자 가운데 설명 [desc] 가 그것으로 시작하는 것(제목)과 표지 모양 그림. 둘 다 있어야 책 칸.
     * 목록 보기 칸은 설명 뒤에 진행률이 있다(`정의란 무엇인가, 26.7MB, 소장, 48%`).
     */
    private fun cell(cell: AccessibilityNodeInfo, desc: String): Cell? {
        var title: String? = null
        var thumb: Rect? = null
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (n.isWebView()) return
            val t = n.text?.toString()?.trim()
            if (!t.isNullOrEmpty() && desc.startsWith(t) && t.length > (title?.length ?: 0)) title = t
            if (n.className?.endsWith("ImageView") == true && thumb == null) {
                val r = n.bounds()
                if (usableThumb(r) && r.width() >= MIN_COVER_PX) thumb = r
            }
            if (depth < WALK_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(cell, 0)
        val t = title ?: return null
        return Cell(t, "", percent(desc.removePrefix(t)), -1, thumb ?: return null, cell.bounds())
    }
}
