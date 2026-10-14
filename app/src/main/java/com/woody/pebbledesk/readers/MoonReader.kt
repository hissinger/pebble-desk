package com.woody.pebbledesk.readers

import android.view.accessibility.AccessibilityNodeInfo

/**
 * 문리더(내 파일을 여는 리더): 서재(최근 목록·책장, 목록·격자 보기 같은 이름) 칸에 제목 `myBookName`·표지 `myBookImage`·
 * 진행률 `progresTv`, 목록 보기에 저자 `myBookAuthor`(격자 보기는 저자순 정렬일 때만 `myAuthor`). 칸을 누르면 알림이 온다.
 * 책장의 묶음 칸은 진행률 줄을 감춘다. 홈 탭의 '최근 목록' 줄은 표지만 있어(누르면 빈 알림) 읽는 화면 메뉴의 제목(`txtTitle`)으로 안다.
 * 읽는 화면(본문은 직접 그리는 뷰라 글자가 드러나지 않는다) 아래 상태 줄 오른쪽 `statusRight` 에 진행률(`30.9%`, 설정에 따라
 * `12/300`), 메뉴에 `txtTextViewPercent`(`30%`). 상태 줄 가운데(`statusMiddle`, `2장 (5/8)`)는 장 안의 쪽이라 쓰지 않는다.
 * 유료판(`com.flyersoft.moonreaderp`)은 같은 코드로 만든 앱이라(무료판 코드가 패키지 이름으로 판을 가른다) 화면 이름은 같고
 * 요소 이름 앞만 그 패키지가 된다고 본다 **(미확인)**.
 */
open class MoonReaderSpec(pkg: String) : ReaderSpec(
    pkg = pkg, classPrefix = "com.flyersoft.moonreader",
    shelves = setOf("com.flyersoft.moonreader.ActivityMain"),
    viewers = setOf("com.flyersoft.moonreader.ActivityTxt"),
    titles = listOf("myBookName"), thumbs = listOf("myBookImage"), authors = listOf("myBookAuthor", "myAuthor"), progress = "progresTv", due = null,
    viewerProgress = listOf("statusRight", "txtTextViewPercent"), viewerTitle = "txtTitle",
    eagerCovers = true,
) {
    /** 진행률이 없는 칸은 책이 아니다(책장의 묶음 칸은 진행률 줄을 감춘다) */
    override fun readCell(cell: AccessibilityNodeInfo): Cell? = super.readCell(cell)?.takeIf { it.progress >= 0 }

    /**
     * 메뉴 제목 → 제목·저자. EPUB 등은 `제목 - 저자`(저자가 없으면 제목만)라 마지막 ` - ` 에서 나눈다.
     * TXT·HTML 등은 파일 이름(`이름.txt`)이라 확장자만 뗀다(`저자 - 제목.txt` 같은 파일 이름은 나누지 않는다).
     */
    override fun splitTitle(text: String): Pair<String, String> {
        BOOK_FILE.find(text)?.let { return text.substring(0, it.range.first) to "" }
        val i = text.lastIndexOf(" - ")
        return if (i > 0) text.substring(0, i).trim() to text.substring(i + 3).trim() else text to ""
    }
}

/** 파일로 연 책의 확장자(메뉴 제목이 파일 이름일 때 뗀다) */
private val BOOK_FILE = Regex("""\.(txt|html?|xhtml|mht|mhtml|md|rtf|docx|odt|fb2(\.zip)?|chm|epub|mobi|azw3?|pdf|djvu|cbz|cbr|zip)$""", RegexOption.IGNORE_CASE)

/** 무료판 */
object MoonReader : MoonReaderSpec("com.flyersoft.moonreader")

/** 유료판(Moon+ Reader Pro) */
object MoonReaderPro : MoonReaderSpec("com.flyersoft.moonreaderp")
