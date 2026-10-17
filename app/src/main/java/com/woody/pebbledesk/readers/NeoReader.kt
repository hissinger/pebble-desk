package com.woody.pebbledesk.readers

import android.content.Context
import android.net.Uri
import com.woody.pebbledesk.R

/**
 * 네오리더(BOOX 기본 리더 `com.onyx.kreader`, 시스템 앱. 내 파일·BOOX 서점 책을 연다): 서재가 없고 실행 화면도 없다. 책은 BOOX 런처
 * (`com.onyx`, 앱 목록의 ONYX Launcher)의 서재나 파일 관리자에서 연다. 그래서 책의 앱은 파일을 받는 화면 `ui.ReaderHomeActivity`([fileViewer])로
 * 두고 홈에서 누르면 그 책의 파일을 네오리더로 바로 연다(파일을 모르면 BOOX 런처).
 * 읽는 화면은 탭 액티비티 `ui.ReaderTab1Activity`~`ReaderTab4Activity`(책 하나에 탭 하나, 탭마다 다른 프로세스)이고 본문은 직접 그리는
 * 화면(SurfaceView)이라 글자가 드러나지 않는다. 아래 상태 줄의 진행 칸 `page_name`(설정에 따라 `12/300`·`4.0%`, 쪽 이름이 있으면
 * `iv(12/300)`, 쪽을 세는 동안은 `...`)과, 메뉴를 띄웠을 때 아래 진행 칸의 제목 `tv_book_title`·쪽 `page_num` 을 읽는다.
 * 메뉴 제목은 BOOX 설정 `책 제목 사용` 이 켜져 있으면 책 정보의 제목, 아니면 파일 이름(확장자 포함)이다.
 * 어떤 책인지는 네오리더가 권한 없이 내놓은 데이터 창구의 `Metadata`(연 책마다 제목·저자·파일 경로·연 때)로 안다([fileBook]): 메뉴 없이도
 * 방금 연 책을, 메뉴 제목이 파일 이름이어도 책 정보의 제목을 쓴다. 창구가 없으면(네오리더가 꺼짐 등) 메뉴 제목으로 안다.
 * 책을 둘 이상 열면 메뉴 위에 탭 줄(`doc_bar`)도 보이지만 지금 탭이 어느 것인지 드러나지 않아 쓰지 않는다.
 * (BOOX Palma2 펌웨어 4.2(2026-08)의 네오리더 38994. 에뮬레이터에서 요소 이름과 자동 추가를 확인했고, 2026-10-17 BOOX 실기기(사용자, Page 또는 Go6)에서도 됐다(docs/READER_APPS.md))
 */
object NeoReader : ReaderSpec(
    pkg = "com.onyx.kreader", classPrefix = "com.onyx.kreader",
    shelves = emptySet(),
    viewers = (1..4).map { "com.onyx.kreader.ui.ReaderTab${it}Activity" }.toSet(),
    titles = emptyList(), thumbs = emptyList(), authors = emptyList(), progress = null, due = null,
    viewerProgress = listOf("page_name", "page_num"), viewerTitle = "tv_book_title",
    fileViewer = FileViewer("com.onyx.kreader.ui.ReaderHomeActivity", R.string.app_neoreader, shelfPkg = "com.onyx"),
    viewerTabs = true,
) {
    override fun splitTitle(text: String): Pair<String, String> = withoutBookFile(text) to ""

    /**
     * 메뉴 제목이 보이면 그것과 제목이나 파일 이름(확장자 뗌)이 같은 책, 안 보이면 가장 최근에 연 책이 이 읽는 화면이 열린 뒤([since])에 열렸을 때만
     * (탭을 오갈 때는 연 때가 바뀌지 않아 앞 탭의 책일 수 있다). 제목이 없는 책(PDF 등)은 파일 이름으로.
     */
    override fun fileBook(context: Context, info: ViewerInfo, since: Long): FileBook? {
        val rows = recent(context) ?: return null
        val menu = info.title
        val row = if (menu != null) rows.firstOrNull { it.title == menu || withoutBookFile(it.name) == menu }
            else rows.firstOrNull()?.takeIf { it.lastAccess >= since - OPEN_SLACK_MS }
        return row?.let { FileBook(it.title.ifBlank { withoutBookFile(it.name) }, it.authors, it.path) }
    }

    private class Row(val title: String, val authors: String, val name: String, val path: String, val lastAccess: Long)

    /** 최근에 연 책들(연 때 늦은 것부터 [RECENT_ROWS]권). 창구가 없거나 읽지 못하면 null */
    private fun recent(context: Context): List<Row>? = runCatching {
        context.contentResolver.query(
            METADATA, arrayOf("title", "authors", "name", "nativeAbsolutePath", "lastAccess"),
            "lastAccess IS NOT NULL", null, "lastAccess DESC",
        )?.use { c ->
            buildList {
                while (size < RECENT_ROWS && c.moveToNext()) {
                    val path = c.getString(3) ?: continue
                    add(Row(c.getString(0).orEmpty().trim(), c.getString(1).orEmpty().trim(), c.getString(2).orEmpty(), path, c.getLong(4)))
                }
            }
        }
    }.getOrNull()

    private val METADATA = Uri.parse("content://com.onyx.content.database.ContentProvider/Metadata")
    private const val RECENT_ROWS = 20
    /** 읽는 화면이 열렸다는 알림보다 연 때가 조금 앞설 수 있어 봐준다 */
    private const val OPEN_SLACK_MS = 3_000L
}
