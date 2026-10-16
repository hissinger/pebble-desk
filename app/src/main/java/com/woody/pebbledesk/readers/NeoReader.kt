package com.woody.pebbledesk.readers

/**
 * 네오리더(BOOX 기본 리더 `com.onyx.kreader`, 시스템 앱. 내 파일·BOOX 서점 책을 연다): 서재가 없고 실행 화면도 없다. 책은 BOOX 런처
 * (`com.onyx`, 앱 목록의 ONYX Launcher)의 서재나 파일 관리자에서 열어, 홈에서 책을 누르면 BOOX 런처를 연다([appPkg]).
 * 읽는 화면은 탭 액티비티 `ui.ReaderTab1Activity`~`ReaderTab4Activity`(책 하나에 탭 하나, 탭마다 다른 프로세스)이고 본문은 직접 그리는
 * 화면(SurfaceView)이라 글자가 드러나지 않는다. 아래 상태 줄의 진행 칸 `page_name`(설정에 따라 `12/300`·`4.0%`, 쪽 이름이 있으면
 * `iv(12/300)`, 쪽을 세는 동안은 `...`)과, 메뉴를 띄웠을 때 아래 진행 칸의 제목 `tv_book_title`·쪽 `page_num` 을 읽는다.
 * 제목은 BOOX 설정 `책 제목 사용` 이 켜져 있으면 책 정보의 제목, 아니면 파일 이름(확장자 포함)이라 확장자를 뗀다.
 * 책을 둘 이상 열면 메뉴 위에 탭 줄(`doc_bar`)도 보이지만 지금 탭이 어느 것인지 드러나지 않아 쓰지 않는다.
 * (BOOX Palma2 펌웨어 4.2(2026-08)의 네오리더 38994. 에뮬레이터에서 요소 이름과 자동 추가를 확인했다(docs/READER_APPS.md). BOOX 실기기 **(미확인)**)
 */
object NeoReader : ReaderSpec(
    pkg = "com.onyx.kreader", classPrefix = "com.onyx.kreader",
    shelves = emptySet(),
    viewers = (1..4).map { "com.onyx.kreader.ui.ReaderTab${it}Activity" }.toSet(),
    titles = emptyList(), thumbs = emptyList(), authors = emptyList(), progress = null, due = null,
    viewerProgress = listOf("page_name", "page_num"), viewerTitle = "tv_book_title",
    appPkg = "com.onyx", viewerTabs = true,
) {
    override fun splitTitle(text: String): Pair<String, String> = withoutBookFile(text) to ""
}
