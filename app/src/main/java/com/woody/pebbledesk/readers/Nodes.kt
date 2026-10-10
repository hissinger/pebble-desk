package com.woody.pebbledesk.readers

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.time.LocalDate

/**
 * 서재 칸 한 권에서 읽은 것. [cell] 은 칸의 화면 위치, [thumb] 는 표지 그림 자리(모르면 null).
 * [stale] 은 방금 읽어 둔 서재 칸을 누른 글자로 맞춘 것(그 뒤 읽는 화면에서 진행률이 바뀌었을 수 있다).
 */
data class Cell(
    val title: String, val author: String, val progress: Int, val due: Long, val thumb: Rect?, val cell: Rect,
    val stale: Boolean = false,
)

/** 읽는 화면에서 읽은 것. [title]·[author] 는 메뉴에 제목이 보일 때만(모르면 null), [progress] 는 모르면 -1 */
class ViewerInfo(val title: String?, val author: String, val progress: Int)

/** 화면에 떠 있는 그 앱의 창들. [active] 는 맨 앞 창(다른 앱이면 null), [all] 은 읽는 화면 + 위에 뜬 메뉴·알림 창 */
class AppWindows(val active: AccessibilityNodeInfo?, val all: List<AccessibilityNodeInfo>)

/** 책 칸을 찾을 때 위로 올라가는 단계, 칸 안을 훑는 깊이 */
internal const val CELL_UP = 8
internal const val WALK_DEPTH = 12
/** 창 전체를 훑는 깊이(요소 이름이 없는 서재, 누른 요소 찾기. React Native 는 칸이 60단계쯤 아래에 있다) */
internal const val DEEP_WALK_DEPTH = 100
/** 이보다 작게 보이는 표지 칸은 자르지 않는다(가려졌거나 화면 밖) */
private const val MIN_THUMB_PX = 40
/** 표지 칸의 세로 ÷ 가로가 이보다 작으면 일부만 보이는 칸으로 본다(표지는 1.4~1.5) */
private const val MIN_COVER_RATIO = 1.1f

/** 자를 만한 표지 칸: 너무 작지 않고(가려졌거나 화면 밖), 세로로 긴 표지 모양(화면 위·아래에 걸쳐 일부만 보이면 아님) */
fun usableThumb(r: Rect) =
    r.width() >= MIN_THUMB_PX && r.height() >= MIN_THUMB_PX && r.height() >= r.width() * MIN_COVER_RATIO

internal fun AccessibilityNodeInfo.bounds() = Rect().also { getBoundsInScreen(it) }

internal fun AccessibilityNodeInfo.isWebView() = className?.endsWith("WebView") == true

internal fun AccessibilityNodeInfo.byId(pkg: String, id: String): List<AccessibilityNodeInfo> =
    findAccessibilityNodeInfosByViewId("$pkg:id/$id")

/** 아래에 이름이 [id] 인 요소가 몇 개인가. 이벤트로 받은 요소에서는 이름으로 찾기가 안 되어 직접 훑는다. */
internal fun AccessibilityNodeInfo.count(pkg: String, id: String): Int {
    val full = "$pkg:id/$id"
    fun walk(n: AccessibilityNodeInfo, depth: Int): Int {
        var c = if (n.viewIdResourceName == full) 1 else 0
        if (depth < WALK_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { c += walk(it, depth + 1) }
        return c
    }
    return walk(this, 0)
}

/** 화면 글자를 보통 글자로(YES24 eBook 서재 제목은 띄어쓰기가 줄바꿈 없는 공백 U+00A0 이다) */
internal fun plain(text: CharSequence): String = text.toString().replace(' ', ' ').trim()

/** 제목이 아니라 상태 글자(`4일`, `반납 4일 남음`, `D-3`, `만료`) — 교보도서관 표지 보기 칸은 제목 없이 이것만 있다 */
internal val STATUS_ONLY = Regex("""^(반납\s*)?(\d+\s*일(\s*남음)?|D-?\d+|만료|오늘 반납)$""")

/** `한강 지음`, `한스 로슬링 지음 / 이창신 옮김`, `<정지원,염선형 저>/ 미래의창` → 지은이만 */
internal fun authorName(text: String): String {
    val t = text.trim().removePrefix("<").substringBefore(">")
    return (AUTHOR_ROLE.find(t)?.let { t.substring(0, it.range.first) } ?: t).trim()
}

/** 지은이 뒤의 `지음`·`저`(뒤에 옮긴이·출판사가 이어질 수 있다) */
private val AUTHOR_ROLE = Regex("""\s+(지음|저)(?=\s*($|/|,))""")

/** `47%` → 47, 소수(문리더 `30.9%`)는 버림. 없으면 -1 */
internal fun percent(text: CharSequence): Int =
    Regex("""(\d{1,3})(?:[.,]\d+)?\s*%""").find(text)?.groupValues?.get(1)?.toInt()?.coerceIn(0, 100) ?: -1

/** 읽는 화면의 진행률: `2% (4/226p)` 처럼 % 가 있으면 그것, 없으면 쪽 `6 / 368` 로 계산(알라딘·북커스). 모르면 -1 */
internal fun viewerPercent(text: CharSequence, roundUp: Boolean): Int {
    percent(text).takeIf { it >= 0 }?.let { return it }
    val m = Regex("""(\d+)\s*/\s*(\d+)""").find(text) ?: return -1
    return pagePercent(m.groupValues[1], m.groupValues[2], roundUp)
}

/** 지금 쪽 ÷ 전체 쪽(버림, [roundUp] 이면 올림). 숫자가 아니면 -1 */
internal fun pagePercent(page: String?, total: String?, roundUp: Boolean): Int {
    val p = page?.trim()?.toLongOrNull() ?: return -1
    val t = total?.trim()?.toLongOrNull()?.takeIf { it > 0 } ?: return -1
    val pct = if (roundUp) (p * 100 + t - 1) / t else p * 100 / t
    return pct.toInt().coerceIn(0, 100)
}

/** `2026.10.20` → 그날, `반납 4일 남음`·`D-4` → 오늘 + 4일, `오늘 반납` → 오늘, `만료` → 어제(지남). 모르면 -1 */
internal fun dueDate(text: String): Long {
    if (text.isEmpty()) return -1
    Regex("""(\d{4})\s*[.\-/년]\s*(\d{1,2})\s*[.\-/월]\s*(\d{1,2})""").find(text)?.let { m ->
        val (y, mo, d) = m.destructured
        runCatching { return LocalDate.of(y.toInt(), mo.toInt(), d.toInt()).toEpochDay() }
    }
    val today = LocalDate.now().toEpochDay()
    Regex("""D-\s*(\d+)""", RegexOption.IGNORE_CASE).find(text)?.let { return today + it.groupValues[1].toLong() }
    if (text.equals("D-Day", ignoreCase = true)) return today
    Regex("""(\d+)\s*일""").find(text)?.let { return today + it.groupValues[1].toLong() }
    if ("만료" in text) return today - 1
    if ("오늘" in text) return today
    return -1
}
