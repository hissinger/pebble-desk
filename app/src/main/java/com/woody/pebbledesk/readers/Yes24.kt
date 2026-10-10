package com.woody.pebbledesk.readers

/**
 * YES24 도서관: 내 서재 칸(격자·목록 보기 같은 이름)에 제목·표지·저자(`<지은이 저>`)·반납(`D-15`). 진행 막대(`pb_read_percent`)는
 * 읽는 화면과 맞지 않아(읽는 화면 8% 인데 0) 쓰지 않는다. 읽는 화면(본문 웹 화면, 북커스와 같은 뷰어)은 아래에 진행률(`tv_percent`),
 * 뷰어 설정에서 하단 정보를 켜면 그 자리에 쪽(`ll_page_area`, `14` `/` `283` 세 조각)이 보인다. 메뉴에 제목(`tv_title`).
 */
val Yes24Library = ReaderSpec(
    pkg = "com.yes24.library.eink", classPrefix = "com.yes24",
    shelves = setOf("com.yes24.library.shelf.LibShelfActivity"),
    viewers = setOf("com.yes24.ebook.fourth.ui.viewer.epub.EpubActivity"),
    titles = listOf("tv_title"), thumbs = listOf("iv_cover"), authors = listOf("tv_author"), progress = null, due = "tv_d_day",
    viewerProgress = listOf("tv_percent", "ll_page_area"), viewerTitle = "tv_title", pagesRoundUp = true, eagerCovers = true,
)

/**
 * YES24 eBook(휴대폰·태블릿용, 크레마의 my YES 와 다른 앱. YES24 도서관·북커스와 같은 회사 코드): 내서재(`PurchaseActivity`)의
 * 읽고 있는 책·책장·구매한 책 칸에 제목 `tv_title`(띄어쓰기가 NBSP)·표지 `iv_cover`·저자 `tv_author`. 칸을 누르면 `[제목, 저자, 0%]`.
 * 칸의 진행률(`tv_read_percent`)은 읽는 화면보다 늦게 바뀌어(읽는 화면 1% 인데 0%) 쓰지 않는다. 읽는 화면(도서관과 같은 뷰어)의
 * 하단 정보는 기본이 '모두 표시 안함'이라, 켜면 아래 줄 `ll_page_area`(`1%` 또는 쪽)·`tv_toc_title`. 메뉴에 제목 `tv_title`·쪽 `ll_menu_page_area`(`1 / 519`).
 */
val Yes24Ebook = ReaderSpec(
    pkg = "com.yes24.ebook.fourth", classPrefix = "com.yes24",
    shelves = setOf("com.yes24.ebook.fourth.ui.purchase.PurchaseActivity"),
    viewers = setOf(
        "com.yes24.ebook.fourth.ui.viewer.epub.EpubActivity",
        "com.yes24.ebook.fourth.ui.viewer.pdf.PDFActivity",
        "com.yes24.ebook.fourth.ui.viewer.comic.ComicActivity",
    ),
    titles = listOf("tv_title"), thumbs = listOf("iv_cover"), authors = listOf("tv_author"), progress = null, due = null,
    viewerProgress = listOf("ll_page_area", "ll_menu_page_area"), viewerTitle = "tv_title", pagesRoundUp = true, eagerCovers = true,
)

/**
 * my YES(YES24 서점, 크레마 기본 앱): 첫 화면 기본책장(격자) 칸에 제목 `textView_title`·표지 `grid_book_cover`·저자 `textView_author`
 * (`<김호연> 저`), 그 위에 뜨는 구매목록 창 칸(`ll_list_book`)에 제목 `tv_buylist_title`·작은 표지 `aiv_buylist_thumbnail`·저자.
 * 진행률·반납일은 서재에 없다(기간이 지난 대여 책은 표지 위에 `기간만료`). 읽는 화면(본문 웹 화면) 아래 `bottom_page_no`(`28%`),
 * 메뉴에 제목 `tv_book_title`·`text_space_page`(`28%`). 앱 화면 전체가 화면 캡처를 막아(검게 찍힘, 창 FLAG_SECURE) 표지는 자르지 않는다.
 * USB 디버깅이 켜져 있으면 앱이 스스로 닫힌다.
 */
val MyYes = ReaderSpec(
    pkg = "com.yes24.ebook.einkstore", classPrefix = "com.keph",
    shelves = setOf("com.keph.crema.lunar.ui.MainActivity"),
    viewers = setOf(
        "com.keph.crema.lunar.ui.viewer.epub.CremaEPUBActivity",
        "com.keph.crema.lunar.ui.viewer.pdf.CremaPDFActivity",
        "com.keph.crema.lunar.ui.viewer.cpub.CremaCPUBActivity",
        "com.keph.crema.lunar.ui.viewer.txt.CremaTXTActivity",
    ),
    titles = listOf("textView_title", "tv_buylist_title"), thumbs = listOf("grid_book_cover", "aiv_buylist_thumbnail"),
    authors = listOf("textView_author", "tv_buylist_author"), progress = null, due = "tv_book_disable_message",
    viewerProgress = listOf("bottom_page_no", "text_space_page"), viewerTitle = "tv_book_title",
    openOnViewer = true, secure = true,
)
