package com.woody.pebbledesk.readers

/**
 * 리디: 책은 교보eBook처럼 읽는 화면 메뉴의 제목(`title`)으로 안다. 서재(React Native, 내 서재 탭과 작품 화면)는 요소 이름이 없어
 * 칸 설명·글자로 표지만 잘라 둔다(칸을 눌러도 알림이 없다). 진행률은 뷰어 설정의 '하단 좌/우 정보 표시'를 켜면 아래 좌·우
 * (`15 / 640`, `2%`), 꺼 두면 메뉴의 쪽(`14 / 626`).
 */
val Ridi = ReaderSpec(
    pkg = "com.initialcoms.ridi", classPrefix = "com.ridi",
    shelves = setOf("com.ridi.books.viewer.main.activity.MainActivity"),
    viewers = setOf(
        "com.ridi.books.viewer.reader.epub.EPubReaderActivity",
        "com.ridi.books.viewer.reader.pagebased.pdf.PDFReaderActivity",
        "com.ridi.books.viewer.reader.pagebased.comic.ComicBookReaderActivity",
    ),
    titles = emptyList(), thumbs = emptyList(), authors = emptyList(), progress = null, due = null,
    viewerProgress = listOf("reader_right_info", "reader_left_info", "reader_toolbar_page_text"), viewerTitle = "title",
    unnamedShelf = "libraryGnbMenus", eagerCovers = true,
)
