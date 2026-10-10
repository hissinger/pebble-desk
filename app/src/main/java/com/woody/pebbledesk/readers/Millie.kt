package com.woody.pebbledesk.readers

/**
 * 밀리의서재: 다운로드·전체도서 탭은 `BookshelfActivity`, 책장 안(읽고있는 책 등)은 `BookShelfDetailActivity`. 칸에 제목·표지·진행률.
 * 검색 탭(웹 화면) 책 상세의 `바로 읽기`·`이어 읽기` 로도 연다. 읽는 화면은 아래 오른쪽 진행률(불러오는 동안의 `0%` 는 뺀다).
 */
val Millie = ReaderSpec(
    pkg = "kr.co.millie.eink", classPrefix = "kr.co.millie",
    shelves = setOf("kr.co.millie.eink.bookshelf.BookshelfActivity", "kr.co.millie.eink.bookshelf.BookShelfDetailActivity"),
    viewers = setOf(
        "kr.co.millie.eink.epub.EPubViewActivity",
        "kr.co.millie.eink.pdf.PDFViewActivity",
        "kr.co.millie.eink.epub.StoryViewActivity",
    ),
    titles = listOf("tv_book_title"), thumbs = listOf("iv_thumbnail"), authors = emptyList(), progress = "bookshelf_cell_reading", due = null,
    viewerProgress = listOf("tv_viewer_add_on_right"), viewerLoading = "epub_loading_message",
    readNow = setOf("바로 읽기", "이어 읽기"),
)
