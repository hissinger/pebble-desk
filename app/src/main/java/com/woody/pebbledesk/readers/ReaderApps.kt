package com.woody.pebbledesk.readers

import android.content.Context

/**
 * 읽는 책 자동 추가가 받는 이북 앱들(앱마다 파일 하나). 앱을 더하면 여기와
 * `res/xml/reader_watch.xml` 의 packageNames(시스템이 이 앱들의 화면만 보낸다), 설정 › 독서 › 자동 추가 안내의 앱(`ReaderGuide.APPS`,
 * 빠지면 단위 테스트가 알린다)을 함께 고친다.
 */
object ReaderApps {
    val ALL = listOf(
        KyoboLibrary, KyoboEbook, KyoboEbookMobile, Millie, MillieMobile, Aladin, Bookers, Ridi, RidiOneStore, Yes24Library, Yes24LibraryMobile, Yes24Ebook, MyYes, MoonReader, MoonReaderPro, EasyView, EasyViewFree, CherieViewer, Kindle, Libby, NeoReader, KOReader, KOReaderFdroid, Readest, BookFusion, Woojoo,
    )

    val byPackage = ALL.associateBy { it.pkg }

    /**
     * 책 파일 읽기가 필요한 앱([ReaderApp.needsBookFiles]). 이 가운데 깔린 앱이 있으면 시작하기에 책 파일 읽기 쪽을 더한다
     * (홈의 안내 창은 자동 추가 서비스가 꺼진 채 그 앱의 읽는 화면을 볼 때 적는다, `ReaderWatchService.wantFiles`).
     */
    val fileApps = ALL.filter { it.needsBookFiles }

    /** 이북 앱 패키지(읽고 있는 앱 고르기에서 먼저 보이고, 오늘 읽은 시간을 센다) */
    val packages = byPackage.keys

    /** [pkg] 앱의 [cls](액티비티 전체 이름)가 읽는 화면인가([ReaderApp.isViewer]) */
    fun isViewer(context: Context, pkg: String, cls: String) = byPackage[pkg]?.isViewer(context, cls) == true

    /** 표시(열림·닫힘)로 읽은 시간을 자르는 액티비티들([ReaderApp.markedScreens]: Libby·Readest 의 서재 액티비티, KOReader 의 읽는 화면) */
    private val hosts = ALL.flatMap { it.markedScreens }.toSet()

    /** [cls] 가 읽는 화면이 보인 동안만 세는 액티비티인가([hosts]) */
    fun hostsViewer(cls: String) = cls in hosts

    /** 읽는 화면이 떴는지 적힌 것이 없으면 떠 있는 것으로 보는 앱([ViewerTime.MARKS_OPEN], KOReader: 데이터를 못 읽으면 앞에 있던 동안을 다 센다). 아니면 안 뜬 것으로(Libby) */
    private val shownByDefault = ALL.filter { it.viewerTime == ViewerTime.MARKS_OPEN }.map { it.pkg }.toSet()

    fun shownByDefault(pkg: String) = pkg in shownByDefault
}
