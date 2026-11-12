package com.woody.pebbledesk

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 홈 모양(표지·서재)의 배치 계산([HomeShelf])과 표지 위 반납일 딱지 글자([dueTag]). 길이는 1dp = 1px 로 넣는다(페블 572×777dp 기준).
 * 책 수는 넣지 않는다: 책이 몇 권이든 표지 크기가 같다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeShelfTest {
    private fun library(room: Int) = HomeShelf.library(room, textH = 46, rowGap = 8, cellW = 156, capW = 156, minW = 80, cols = 3)
    private fun cover(room: Int, mainCap: Int = 411, mainMin: Int = 186) =
        HomeShelf.cover(room, rowExtra = 16, rowGap = 10, cols = 4, alsoCapW = 116, alsoMinW = 40, mainFullH = 270, mainMinH = mainMin, mainCapH = mainCap)

    @Test fun `서재 - 페블 격자 1줄은 두 줄`() = assertEquals(LibraryGrid(3, 2, 118), library(456))

    @Test fun `서재 - 낮으면 한 줄, 표지는 상한까지`() = assertEquals(LibraryGrid(3, 1, 156), library(300))

    @Test fun `서재 - 표지가 상한에 막혀 조금 남으면 줄을 늘리지 않는다`() = assertEquals(LibraryGrid(3, 3, 156), library(900))

    @Test fun `서재 - 많이 남으면 한 줄 더(표지는 작아진다)`() = assertEquals(LibraryGrid(3, 4, 132), library(1000))

    @Test fun `서재 - 줄들이 격자 높이를 넘지 않는다`() {
        for (room in 150..1300 step 7) library(room).let { g ->
            assertTrue("room $room $g", g.rows * (g.coverW * 3 / 2 + 46) + (g.rows - 1) * 8 <= room || g.coverW == 0)
        }
    }

    @Test fun `서재 가로 - 두 줄, 열은 폭에 드는 만큼`() {
        assertEquals(LibraryGrid(3, 2, 93), HomeShelf.libraryWide(380, 46, 8, innerW = 386, colGap = 12, capW = 156, minW = 80, minCols = 3))
        assertEquals(LibraryGrid(4, 2, 93), HomeShelf.libraryWide(380, 46, 8, innerW = 492, colGap = 12, capW = 156, minW = 80, minCols = 3))
    }

    @Test fun `서재 가로 - 표지가 너무 작아지면 한 줄`() =
        assertEquals(1, HomeShelf.libraryWide(300, 46, 8, innerW = 386, colGap = 12, capW = 156, minW = 80, minCols = 3).rows)

    @Test fun `표지 - 페블 격자 1줄은 맨 앞 책과 한 줄을 같이 줄인다`() = assertEquals(CoverShelf(243, 4, 1, 115), cover(432))

    @Test fun `표지 - 바형은 맨 앞 책이 180dp 넘게 남는 만큼 줄을 늘린다`() = assertEquals(CoverShelf(409, 4, 2, 116), cover(799))

    @Test fun `표지 - 맨 앞 책은 상한까지`() = assertEquals(CoverShelf(411, 4, 3, 116), cover(1100))

    @Test fun `표지 - 낮으면 맨 앞 책 최소를 먼저 지키고 함께 읽는 책만 줄인다`() = assertEquals(CoverShelf(186, 4, 1, 65), cover(300))

    @Test fun `표지 - 함께 읽는 책이 너무 작아지면 그 줄을 두지 않는다`() = assertEquals(CoverShelf(230, 4, 0, 0), cover(230))

    @Test fun `표지 - 맨 앞 책과 줄들이 책 자리를 넘지 않는다`() {
        for (room in 250..1300 step 7) cover(room).let { s ->
            assertTrue("room $room $s", s.mainH + s.rows * (s.alsoW * 3 / 2 + 16) + maxOf(0, s.rows - 1) * 10 <= room)
        }
    }

    /** 글자를 키우면 맨 앞 책 최소가 180dp 표지(270)보다 커진다: 줄 수를 최소로도 재야 두 줄이 책 자리를 넘지 않는다 */
    @Test fun `표지 - 글자를 키워 맨 앞 책 최소가 커져도 넘지 않는다`() {
        assertEquals(1, cover(700, mainMin = 340).rows)
        for (room in 400..1300 step 7) cover(room, mainMin = 340).let { s ->
            assertTrue("room $room $s", s.rows == 0 || (s.mainH >= 340 && s.mainH + s.rows * (s.alsoW * 3 / 2 + 16) + (s.rows - 1) * 10 <= room))
        }
    }

    @Test fun `표지 가로 - 한 줄, 열은 폭에 드는 만큼`() {
        val s = HomeShelf.coverWide(room = 370, rowExtra = 16, innerW = 386, colGap = 10, cellMinW = 90, minCols = 4, alsoMinW = 40, mainCapH = 243)
        assertEquals(4, s.cols)
        assertEquals(1, s.rows)
        assertTrue(s.mainH + s.alsoW * 3 / 2 + 16 <= 370)
        assertEquals(5, HomeShelf.coverWide(370, 16, innerW = 492, colGap = 10, cellMinW = 90, minCols = 4, alsoMinW = 40, mainCapH = 243).cols)
    }

    private val context: Context = RuntimeEnvironment.getApplication()
    private val today = LocalDate.of(2026, 11, 12)
    private fun book(due: Long) = ShelfBook("b-1", "Sea Song", "", null, 0, 33, due)

    @Test fun `딱지 - 남은 날 · 오늘 · 지남`() {
        assertEquals(context.getString(R.string.due_tag_days, 9), context.dueTag(book(today.toEpochDay() + 9), today))
        assertEquals(context.getString(R.string.due_tag_today), context.dueTag(book(today.toEpochDay()), today))
        assertEquals(context.getString(R.string.due_tag_over), context.dueTag(book(today.toEpochDay() - 2), today))
    }

    @Test fun `딱지 - 반납일을 모르면 없다`() = assertNull(context.dueTag(book(-1), today))

    @Test fun `홈 모양 - 모르는 값은 기본`() {
        assertEquals(HomeStyle.BASIC, HomeStyle.of(null))
        assertEquals(HomeStyle.BASIC, HomeStyle.of("x"))
        assertEquals(HomeStyle.LIBRARY, HomeStyle.of("library"))
    }
}
