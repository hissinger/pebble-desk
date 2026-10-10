package com.woody.pebbledesk.readers

/**
 * 교보도서관: 서재(목록·표지 보기) 칸에 제목·저자·진행률·반납(`반납 4일 남음`). 표지 보기 칸에는 제목이 없다(`4일`, `47%` 만).
 * 읽는 화면은 본문이 웹 화면이라 훑지 않고, 아래 진행률 줄(`bookIndicator` `2% (4/226p)`)과 메뉴의 제목만 이름으로 읽는다.
 */
val KyoboLibrary = ReaderSpec(
    pkg = "kr.co.kyobobook.KEL", classPrefix = "com.kyobo",
    shelves = setOf("com.kyobo.ebook.kel.ui.main.MainActivity"),
    viewers = setOf(
        "com.kyobo.ebook.kel.viewer.epub.B2BViewerEpubMainActivity",
        "com.kyobo.ebook.kel.viewer.pdf.B2BViewerPdfMainActivity",
    ),
    titles = listOf("tvTitle"), thumbs = listOf("ivThumbnail"), authors = listOf("tvAuthor"), progress = "tvReadPercentageTxt", due = "tvRemainDate",
    viewerProgress = listOf("bookIndicator"), viewerTitle = "viewer_top_booktitle",
)

/**
 * 교보eBook: 서재(Compose)에는 요소 이름이 없고 격자 보기에는 제목도 없어 서재는 읽지 않는다.
 * 교보도서관과 같은 읽는 화면이라 진행률 줄과 메뉴의 제목으로 안다(서재·내 책장 어디서 열어도).
 */
val KyoboEbook = ReaderSpec(
    pkg = "com.kyobo.ebook.eink", classPrefix = "com.kyobo",
    shelves = emptySet(),
    viewers = setOf(
        "com.kyobo.ebook.common.b2c.viewer.epub.ViewerEpubMainActivity",
        "com.kyobo.ebook.common.b2c.viewer.pdf.ViewerPdfMainActivity",
        "com.kyobo.ebook.common.b2c.viewer.comic.ViewerComicMainActivity",
    ),
    // 서재(shelves)를 읽지 않으므로 칸 요소 이름(titles·thumbs)은 쓰이지 않는다.
    titles = emptyList(), thumbs = emptyList(), authors = emptyList(), progress = null, due = null,
    viewerProgress = listOf("bookIndicator"), viewerTitle = "viewer_top_booktitle",
)
