package com.woody.pebbledesk.readers

/**
 * 이북 앱 하나의 화면 이름·요소 이름과 그 앱만의 동작(읽는 책 자동 추가 [com.woody.pebbledesk.ReaderWatchService] 가 쓴다).
 * 앱이 업데이트돼 이름이 바뀌면 그 앱 파일만 고친다. 앱 목록은 [ReaderApps].
 */
class ReaderSpec(
    val pkg: String,
    val classPrefix: String,
    /** 책 칸이 보이는 화면들(서재·탭·책장 안) */
    val shelves: Set<String>,
    /** 읽는 화면들. 오늘 읽은 시간에서도 읽는 화면으로 센다([ReaderApps.isViewer]). */
    val viewers: Set<String>,
    /** 서재 칸의 제목(보기 방식마다 이름이 다르면 여럿) */
    val titles: List<String>,
    /** 서재 칸의 표지 그림(보기 방식·화면마다 이름이 다르면 여럿) */
    val thumbs: List<String>,
    /** 서재 칸의 저자(앞에서부터 먼저 찾은 것) */
    val authors: List<String>,
    /** 서재 칸의 진행률. null 이면 서재의 진행률을 쓰지 않는다(누른 칸의 글자에 있어도). */
    val progress: String?,
    val due: String?,
    /** 읽는 화면의 진행률 줄(앞에서부터 먼저 찾은 것) */
    val viewerProgress: List<String>,
    /**
     * 읽는 화면이 책을 불러오는 중에 보이는 요소. 이것이 보이는 동안의 `0%` 는 진행률로 보지 않는다
     * (밀리는 불러오는 처음 2초쯤 `0%` 를 보이다가 진짜 값으로 바꾸고, 불러오기 문구는 그 뒤로도 20초쯤 남는다).
     */
    val viewerLoading: String? = null,
    /** 읽는 화면 메뉴의 책 제목(메뉴를 띄웠을 때만 보인다) */
    val viewerTitle: String? = null,
    /** 메뉴의 책 제목 글자 → 제목·저자(저자까지 한 줄에 보이는 앱. 없으면 글자 그대로가 제목) */
    val splitViewerTitle: ((String) -> Pair<String, String>)? = null,
    /** 진행률이 없는 칸은 책이 아니다(문리더 책장의 묶음 칸은 진행률 줄을 감춘다) */
    val cellNeedsProgress: Boolean = false,
    /** 쪽 번호가 지금 쪽·전체 쪽 두 요소로 나뉜 읽는 화면(북커스 메뉴) */
    val viewerPages: Pair<String, String>? = null,
    /** 쪽으로 셈한 진행률을 올림한다(서재에 보이는 % 와 맞추려고. 북커스는 1/368쪽을 1% 로 보인다) */
    val pagesRoundUp: Boolean = false,
    /** 서재 화면의 '최근 읽은 책' 제목(책 칸을 눌러도 알림이 오지 않는 앱) */
    val recent: String? = null,
    /** 검색(웹 화면) 책 상세의 읽기 단추 글자 */
    val readNow: Set<String> = emptySet(),
    /**
     * 서재를 잠깐만 보고 책으로 넘어가는 앱(알라딘·북커스·리디): 멈추기를 기다리지 않고 표지를 잘라 둔다.
     * 밀리는 앱을 다시 열 때 잠깐 칸을 다른 책으로 채우므로 쓰지 않는다.
     */
    val eagerCovers: Boolean = false,
    /** 칸을 눌러도 읽는 화면이 열려야 편 것으로 본다(my YES: 누르면 내려받기·기간 만료 안내가 먼저 뜬다) */
    val openOnViewer: Boolean = false,
    /** 앱 화면 전체가 화면 캡처를 막아(검게 찍힌다) 표지를 자르지 않는다(my YES) */
    val secure: Boolean = false,
    /**
     * 요소 이름이 없는 서재(리디, React Native)의 서재 표시 요소. 이것이 있는 화면에서 칸을 설명·글자·그림으로 찾는다.
     * 화면이 넘어가는 중(작품 화면이 밀려 들어옴)에 찍으면 어긋나므로, 찍은 뒤에도 칸이 그 자리에 있을 때만 쓴다.
     */
    val unnamedShelf: String? = null,
)
