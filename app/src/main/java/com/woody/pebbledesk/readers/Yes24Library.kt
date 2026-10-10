package com.woody.pebbledesk.readers

/**
 * YES24 도서관: 내 서재 칸(격자·목록 보기 같은 이름)에 제목·표지·저자(`<지은이 저>`)·반납(`D-15`). 진행 막대(`pb_read_percent`)는
 * 읽는 화면과 맞지 않아(읽는 화면 8% 인데 0) 쓰지 않는다. 읽는 화면(본문 웹 화면, 북커스와 같은 뷰어)은 아래에 진행률(`tv_percent`),
 * 뷰어 설정에서 하단 정보를 켜면 그 자리에 쪽(`ll_page_area`, `14` `/` `283` 세 조각)이 보인다. 메뉴에 제목(`tv_title`).
 */
object Yes24Library : ReaderSpec(
    pkg = "com.yes24.library.eink", classPrefix = "com.yes24",
    shelves = setOf("com.yes24.library.shelf.LibShelfActivity"),
    viewers = setOf("com.yes24.ebook.fourth.ui.viewer.epub.EpubActivity"),
    titles = listOf("tv_title"), thumbs = listOf("iv_cover"), authors = listOf("tv_author"), progress = null, due = "tv_d_day",
    viewerProgress = listOf("tv_percent", "ll_page_area"), viewerTitle = "tv_title", pagesRoundUp = true, eagerCovers = true,
)
