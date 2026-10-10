package com.woody.pebbledesk.readers

/**
 * 읽는 책 자동 추가가 받는 이북 앱들(앱마다 파일 하나). 앱을 더하면 여기와
 * `res/xml/reader_watch.xml` 의 packageNames(시스템이 이 앱들의 화면만 보낸다)를 함께 고친다.
 */
object ReaderApps {
    val ALL = listOf(
        KyoboLibrary, KyoboEbook, Millie, Aladin, Bookers, Ridi, Yes24Library, Yes24Ebook, MyYes, MoonReader,
    )

    val byPackage = ALL.associateBy { it.pkg }

    /** 이북 앱 패키지(읽고 있는 앱 고르기에서 먼저 보이고, 오늘 읽은 시간을 센다) */
    val packages = byPackage.keys

    private val viewers = ALL.flatMap { it.viewers }.toSet()

    /** [cls](액티비티 전체 이름)가 이 앱들의 읽는 화면인가 */
    fun isViewer(cls: String) = cls in viewers
}
