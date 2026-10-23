package com.woody.pebbledesk.readers

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 킨들(e-ink 판이 따로 없고 휴대폰 판 하나. 한국어가 없어 한국어 기기에서도 영어로 보인다): 서재(`oob.MainActivity` 의 홈 탭
 * `From Your Library` 줄·Library 탭)의 책 칸은 설명이 `제목, 성, 이름, , Book downloaded.Reading is 12% completed., 1.2 Megabyte`
 * 이다(진행률을 보이는 요소가 따로 없어 `progress` 없이 설명의 `Reading is 12% completed.` 로 읽는다). 격자 보기 칸(`badgeable_cover`)에는 설명과 표지(`cover_image`)뿐이고, 목록 보기 칸(이름 없는 단추)에는 제목
 * `lib_book_row_title`·저자 `lib_book_row_author`(`BRONTË, EMILY`, 대문자)도 있다. 받은 책 칸을 누르면 읽는 화면이 열릴 때 편 것으로 보고,
 * 받지 않은 책(누르면 Library 탭은 내려받기만, 홈 탭은 받은 뒤 바로 연다)은 읽는 화면 메뉴의 제목으로 안다. 읽는 화면의 본문·위 제목·아래 `12%` 는 직접 그리는 뷰라 드러나지 않고, 메뉴를 띄우면
 * 제목 `command_bar_title_bottom`·진행률 `nln_text_secondary`(`Location 1 of 4559  •  0%`)가 보인다.
 */
object Kindle : ReaderApp(
    pkg = "com.amazon.kindle", classPrefix = "com.amazon",
    shelves = setOf("com.amazon.kcp.oob.MainActivity"),
    viewers = setOf(
        "com.amazon.kcp.reader.StandAloneBookReaderActivity",
        "com.amazon.kcp.reader.SimplifiedStandAloneBookReaderActivity",
        "com.amazon.kcp.reader.StandAlonePdfReaderActivity",
        "com.amazon.kcp.reader.StandAloneMopReaderActivity",
        "com.amazon.kcp.reader.MopReaderActivity",
    ),
    titles = listOf("lib_book_row_title"), thumbs = listOf("cover_image"), authors = emptyList(), progress = null, due = null,
    viewerProgress = listOf("nln_text_secondary"), viewerTitle = "command_bar_title_bottom",
    eagerCovers = true, openOnViewer = true,
) {
    /** 책 칸 설명에서 제목·저자(`, , ` 앞)와 상태를 가르는 곳 */
    private const val SEP = ", , "

    /** 격자 보기·홈 탭 칸의 표지 위에 그리는 표시들 */
    private val BADGES = setOf("top_right_badge", "top_right_corner_badge", "bottom_right_badge")

    /** 서재(홈 탭 줄은 옆으로, Library 는 위아래로 넘긴다)가 움직이는 중에 찍으면 칸이 어긋나, 찍은 뒤에도 같은 자리에 있는 칸만 자른다. */
    override val cellsByPosition get() = true

    /** 아래 탭 줄의 탭들(Compose 라 이름에 `패키지:id/` 가 없다) */
    private val TABS = setOf("home_tab", "library_tab", "more_tab")

    /**
     * 아래 탭 줄 왼쪽에 떠 있는 '이어 읽기' 표지(마지막으로 읽은 책, 그림 하나). 설명이 `Most recent book. ` 뒤에 책 칸과 같은 꼴이다.
     * 표시 없이 깨끗해, 격자 보기·홈 탭 칸의 표지를 표시에 가려 자르지 못한 책(받은 책은 늘 ✓ 가 있다)도 읽고 돌아오면 이것으로 표지를 얻는다(목록 보기 칸보다 작다).
     */
    private const val RECENT = "book_in_bottom_bar"

    /**
     * 서재에 보이는 책 칸: 설명이 책 칸 모양이고 표지가 든 묶음(홈 탭의 추천 줄은 설명이 `제목 by 저자` 라 빠진다).
     * 아래 탭 줄 왼쪽에 마지막으로 읽은 책의 '이어 읽기' 표지([RECENT])가 탭 줄 위로 높이의 반쯤 솟아 떠 있어, 그 높이까지 내려온 표지는 자르지 않는다
     * (이어 읽기 표지는 그 책의 칸으로 맨 뒤에 둔다. 그 그림은 늘 세로로 긴 자리라 가로로 긴 표지는 자리 밖으로 넘쳐 그려지므로, 같은 책의 칸 표지가
     * 표지 모양이 아니면 자르지 않는다).
     * 홈 탭 줄은 옆으로 넘기는 줄이라 화면 왼쪽·오른쪽 끝에 닿은 표지는 잘려 보이는 것이어서 자르지 않는다(잘리면 더 세로로 길어 표지 모양으로 보인다).
     */
    override fun shelfCells(root: AccessibilityNodeInfo): List<Cell> {
        val found = mutableListOf<Cell>()
        var bar: Rect? = null
        var recent: Cell? = null
        /** 책마다 칸의 표지 자리(표시에 가려 자르지 않는 칸도) */
        val covers = mutableMapOf<String, Rect>()
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (n.isWebView()) return
            if (n.viewIdResourceName in TABS) bar = (bar ?: Rect()).apply { union(n.bounds()) }
            if (n.viewIdResourceName?.substringAfter(":id/") == RECENT) { recent = recentCell(n); return }
            descCell(n)?.let { (c, cover) -> found += c; covers[c.title] = cover; return }
            if (depth < DEEP_WALK_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        val screen = root.bounds()
        val cut = bar?.let { it.top - it.height() / 2 } ?: Int.MAX_VALUE
        return found.map { c ->
            val t = c.thumb ?: return@map c
            if (t.bottom > cut || t.left <= screen.left || t.right >= screen.right) c.copy(thumb = null) else c
        } + listOfNotNull(recent?.let { r -> if (covers[r.title]?.let(::usableThumb) == false) r.copy(thumb = null) else r })
    }

    /** '이어 읽기' 표지([RECENT]): 설명의 앞 문장(`Most recent book. `)을 떼면 책 칸 설명, 그림이 곧 표지 */
    private fun recentCell(n: AccessibilityNodeInfo): Cell? {
        val desc = n.contentDescription?.toString()?.substringAfter(". ", "") ?: return null
        val head = desc.substringBefore(SEP, "").takeIf { it.isNotBlank() } ?: return null
        val (name, author) = splitHead(head)
        val r = n.bounds()
        return Cell(name, personName(author), percent(desc.substringAfter(SEP)), -1, r, r)
    }

    /**
     * 누른 칸: 누른 것으로 오는 요소는 칸 단추를 감싼 묶음(같은 자리)이라 그 안의 칸(누른 곳의 가운데를 품는 것). 없으면(칸 안의 그림만 온 것)
     * 지금 서재에서 누른 곳의 가운데가 든 칸. 받은 책만(설명에 진행률이 있다) 책 칸으로 본다: 받지 않은 책은 누르면 내려받기만 하기도 해서,
     * 기억해 두면 그 사이 '이어 읽기'(누름 알림이 없다)로 연 다른 책 대신 그 책이 들어간다. 그때는 읽는 화면 메뉴의 제목으로 안다.
     */
    override fun clickedCell(source: AccessibilityNodeInfo?, text: List<CharSequence>, windows: AppWindows, last: List<Cell>?): Cell? {
        val s = source ?: return null
        val at = s.bounds()
        fun under(cells: List<Cell>?) = cells?.firstOrNull { it.cell.contains(at.centerX(), at.centerY()) }
        return (under(shelfCells(s)) ?: under(windows.active?.let(::shelfCells)))?.takeIf { it.progress >= 0 }
    }

    /**
     * 설명이 `제목, 저자, , 상태, 크기` 인 묶음 하나와 그 표지 자리. 목록 보기는 제목 글자가 따로 있어 그것을 쓰고, 격자 보기는 설명에서 가른다:
     * 저자는 `성, 이름` 이라 쉼표로 나눈 끝의 둘(둘뿐이면 끝 하나), 나머지가 제목(`Moby Dick: or, the White Whale`).
     * 격자 보기·홈 탭 칸은 표지 위에 진행률 띠(`top_right_badge`)·받음 ✓(`bottom_right_badge`)·오디오 표시를 그려, 표시가 있는 칸의 표지는 자르지 않는다
     * (목록 보기 칸은 표시가 표지 밖에 있다).
     */
    private fun descCell(n: AccessibilityNodeInfo): Pair<Cell, Rect>? {
        val desc = n.contentDescription?.toString() ?: return null
        val head = desc.substringBefore(SEP, "").takeIf { it.isNotBlank() } ?: return null
        var title: String? = null
        var thumb: Rect? = null
        var badged = false
        fun walk(c: AccessibilityNodeInfo, depth: Int) {
            when (c.viewIdResourceName?.substringAfter(":id/")) {
                in titles -> if (title == null) title = c.text?.let(::plain)?.takeIf { it.isNotEmpty() }
                in thumbs -> if (thumb == null) thumb = c.bounds()
                in BADGES -> badged = true
            }
            if (depth < WALK_DEPTH) for (i in 0 until c.childCount) c.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(n, 0)
        val cover = thumb ?: return null
        val (name, author) = title?.takeIf { head.startsWith(it) }?.let { t -> t to head.removePrefix(t).removePrefix(", ") } ?: splitHead(head)
        return Cell(name, personName(author), percent(desc.substringAfter(SEP)), -1, cover.takeUnless { badged }, n.bounds()) to cover
    }

    /** 격자 보기 설명의 `제목, 성, 이름` → 제목·`성, 이름` */
    private fun splitHead(head: String): Pair<String, String> {
        val parts = head.split(", ")
        return when {
            parts.size >= 3 -> parts.dropLast(2).joinToString(", ") to parts.takeLast(2).joinToString(", ")
            parts.size == 2 -> parts[0] to parts[1]
            else -> head to ""
        }
    }

    /** `Brontë, Emily` → `Emily Brontë`(쉼표가 하나일 때만) */
    private fun personName(author: String): String {
        val parts = author.split(", ")
        return if (parts.size == 2 && parts.all { it.isNotBlank() }) "${parts[1]} ${parts[0]}" else author.trim()
    }
}
