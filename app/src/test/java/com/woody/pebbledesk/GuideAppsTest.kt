package com.woody.pebbledesk

import com.woody.pebbledesk.readers.ReaderApps
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File

/** 자동 추가가 받는 이북 앱([ReaderApps.ALL])이 자동 추가 안내([SettingsActivity.GUIDE_APPS])와 접근성 설정(reader_watch.xml)에 빠짐없이 있고, 책 파일을 쓰는 앱에 책 파일 읽기 필요가 적혀 있는가 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GuideAppsTest {
    @Test fun `자동 추가 안내에 모든 이북 앱이 한 번씩`() {
        val guided = SettingsActivity.GUIDE_APPS.flatMap { it.specs }
        assertEquals(guided.size, guided.toSet().size)
        assertEquals(ReaderApps.ALL.map { it.pkg }.sorted(), guided.map { it.pkg }.sorted())
    }

    @Test fun `책 파일로 책·표지를 아는 앱은 책 파일 읽기가 필요하다고 적혀 있다`() {
        val usesFiles = ReaderApps.ALL.filter { it.fileState || it.searchesBookFile || it.fileViewer != null || it.opensBookFile }
        assertEquals(emptyList<String>(), usesFiles.filterNot { it.needsBookFiles }.map { it.pkg })
    }

    @Test fun `접근성 서비스가 모든 이북 앱 화면을 받는다`() {
        val xml = File("src/main/res/xml/reader_watch.xml")
        val p = XmlPullParserFactory.newInstance().newPullParser().apply { setInput(xml.reader()) }
        var names: List<String> = emptyList()
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType != XmlPullParser.START_TAG) continue
            for (i in 0 until p.attributeCount) if (p.getAttributeName(i).endsWith("packageNames")) names = p.getAttributeValue(i).split(',')
        }
        assertEquals(ReaderApps.ALL.map { it.pkg }.sorted(), names.sorted())
    }
}
