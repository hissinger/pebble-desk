package com.woody.pebbledesk.readers

/**
 * 알라딘: 격자 보기 칸에는 제목·표지만 있고 눌러도 알림이 없다(서재 아래 '최근 읽은 책'으로 안다. 그 줄은 늦게 바뀐다).
 * 목록 보기 칸에는 제목·저자·진행률·대여 기한이 있고 누르면 알림이 온다. 읽는 화면은 메뉴의 제목으로 알고,
 * 아래 쪽 표시(`6 / 368　 저자소개`, 쪽·장 이름)로 진행률을 셈한다.
 */
object Aladin : ReaderSpec(
    pkg = "kr.co.aladin.ebook", classPrefix = "kr.co.aladin",
    shelves = setOf("kr.co.aladin.ebook.MainActivity"),
    viewers = setOf("kr.co.aladin.epubreader.readonbook.bookrender.ReadONBookRenderActivity"),
    titles = listOf("txt_title", "text_title"), thumbs = listOf("img_cover"), authors = listOf("text_author"), progress = "txt_read_percent",
    due = "text_rent_date", viewerProgress = listOf("bookrender_txt_page_onepage", "viewermenu_text_pageinfo"),
    viewerTitle = "viewer_header_title",
    recent = "reading_book_tv_book_title", eagerCovers = true,
)
