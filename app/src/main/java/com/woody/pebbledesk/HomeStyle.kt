package com.woody.pebbledesk

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import java.time.LocalDate

/**
 * 홈 모양(설정 › 화면 › 홈 모양, DESIGN.md `홈 모양`): 책 자리를 어떻게 채우는가. 자주 쓰는 앱·상단·하단은 어느 모양이든 같다.
 * - [BASIC] 맨 앞 책 + 함께 읽는 책 3칸(글자와 함께, `홈 배치 규칙`). 처음 값.
 * - [COVER] 맨 앞 책 + 함께 읽는 책을 표지로만(4열, 높이에 드는 만큼 줄).
 * - [LIBRARY] 모든 책을 같은 크기 표지 격자로(3열, 칸마다 제목·진행률).
 */
enum class HomeStyle(val key: String, val label: Int) {
    BASIC("basic", R.string.home_style_basic),
    COVER("cover", R.string.home_style_cover),
    LIBRARY("library", R.string.home_style_library);

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: BASIC
    }
}

/** 서재 모양의 격자: 열·줄 수와 표지 폭(px, 테두리 빼고) */
data class LibraryGrid(val cols: Int, val rows: Int, val coverW: Int)

/** 표지 모양: 맨 앞 책 표지 높이, 함께 읽는 책 열·줄 수(줄이 0 이면 그 줄을 두지 않는다)와 표지 폭(px, 테두리 빼고) */
data class CoverShelf(val mainH: Int, val cols: Int, val rows: Int, val alsoW: Int)

/**
 * 홈 모양의 배치 계산(화면 없이 시험한다). 길이는 모두 px, 표지는 폭 : 높이 = 2 : 3.
 * 책 수는 넣지 않는다: 같은 화면·설정이면 책이 몇 권이든 표지 크기가 같다(책이 적으면 칸이 빈다).
 */
object HomeShelf {
    /**
     * 기본 모양 아래 글자(큰 표지) 함께 읽는 책: [coversH] 는 두 표지가 나눌 높이(테두리 빼고). 함께 읽는 책 표지를 [alsoMinW] 로 먼저 두고
     * 남는 높이를 맨 앞 책에(상한 [mainMaxW] 까지). 맨 앞 책이 상한이면 남는 높이를 함께 읽는 책 표지에(칸 폭 [cellW] 까지, 맨 앞 책 글자 몫
     * [mainNeedH] 는 남긴다). 맨 앞 책 표지 높이가 [mainNeedH](가장 긴 글자가 드는 높이)보다 작으면 null(다음 모양으로). 결과는 (맨 앞 책 폭, 함께 읽는 책 폭).
     */
    fun bigAlso(coversH: Int, mainNeedH: Int, alsoMinW: Int, mainMaxW: Int, cellW: Int): Pair<Int, Int>? {
        val mainH = coversH - alsoMinW * 3 / 2
        if (mainH < mainNeedH) return null
        val mainW = minOf(mainMaxW, mainH * 2 / 3)
        val alsoW = if (mainW < mainMaxW) alsoMinW else minOf(cellW, maxOf(alsoMinW, (coversH - maxOf(mainW * 3 / 2, mainNeedH)) * 2 / 3))
        return mainW to alsoW
    }

    /** 기본 모양 옆 글자·글자만 함께 읽는 책: 맨 앞 책이 쓸 높이 [left] 가 [mainNeedH] 보다 작으면 null, 아니면 맨 앞 책 폭(상한 [mainMaxW] 까지) */
    fun sideMain(left: Int, mainNeedH: Int, mainMaxW: Int): Int? = if (left < mainNeedH) null else minOf(mainMaxW, left * 2 / 3)

    /**
     * 서재 세로: [cols] 열, 줄 수는 표지 넓이의 합이 가장 큰 것. 표지가 상한([capW])에 막혀 아래가 한 줄의 [EXTRA_ROW] 넘게 남고
     * 한 줄 더 둬도 표지가 [minW] 이상이면 한 줄 더 둔다.
     * [room] 은 격자 높이, [textH] 는 표지 아래 글자(제목·진행률) 높이, [rowGap] 은 줄 사이, [cellW] 는 칸 폭(표지가 넘지 않는다).
     */
    fun library(room: Int, textH: Int, rowGap: Int, cellW: Int, capW: Int, minW: Int, cols: Int): LibraryGrid {
        val maxW = minOf(capW, cellW)
        fun coverH(r: Int) = minOf((room - (r - 1) * rowGap) / r - textH, maxW * 3 / 2)
        var rows = (1..MAX_ROWS).maxByOrNull { r -> coverH(r).let { if (it > 0) r.toLong() * it * it else 0L } } ?: 1
        val h = coverH(rows)
        val slack = room - rows * (h + textH) - (rows - 1) * rowGap
        if (slack > EXTRA_ROW * (h + textH) && coverH(rows + 1) * 2 / 3 >= minW) rows++
        return LibraryGrid(cols, rows, maxOf(0, coverH(rows) * 2 / 3))
    }

    /**
     * 서재 가로: 두 줄(표지가 [minW] 보다 작아지면 한 줄), 열은 칸 폭 [innerW] 에 드는 만큼([minCols] 열부터, 그때는 표지를 칸에 맞춘다).
     */
    fun libraryWide(room: Int, textH: Int, rowGap: Int, innerW: Int, colGap: Int, capW: Int, minW: Int, minCols: Int): LibraryGrid {
        fun coverW(r: Int) = minOf(((room - (r - 1) * rowGap) / r - textH) * 2 / 3, capW)
        val rows = if (coverW(2) >= minW) 2 else 1
        val w = maxOf(0, coverW(rows))
        val cols = maxOf(minCols, (innerW + colGap) / (w + colGap))
        return LibraryGrid(cols, rows, minOf(w, (innerW - (cols - 1) * colGap) / cols))
    }

    /**
     * 표지 세로: 맨 앞 책과 함께 읽는 책 줄들이 [room](두 표지 높이의 합 + 줄마다 표지 아래 [rowExtra] + 줄 사이 [rowGap])을 나눈다.
     * 함께 읽는 책은 가장 큰 표지([alsoCapW])로 맨 앞 책이 [mainFullH](글자를 키워 최소가 더 크면 최소) 이상 남는 가장 많은 줄(3줄까지). 한 줄인데 맨 앞 책이
     * 함께 읽는 책의 [MAIN_OVER] 배가 안 되면 둘을 같이 줄이고, 맨 앞 책이 최소([mainMinH], 가장 긴 글자가 드는 높이)에 닿으면 함께 읽는 책만 줄인다.
     * 함께 읽는 책 표지가 [alsoMinW] 보다 작아지면 그 줄을 두지 않는다. 맨 앞 책은 [mainCapH] 까지(남는 높이는 줄 사이로).
     */
    fun cover(room: Int, rowExtra: Int, rowGap: Int, cols: Int, alsoCapW: Int, alsoMinW: Int, mainFullH: Int, mainMinH: Int, mainCapH: Int): CoverShelf {
        val capH = alsoCapW * 3 / 2
        fun rowsH(r: Int, h: Int) = r * (h + rowExtra) + (r - 1) * rowGap
        val rows = (1..MAX_COVER_ROWS).lastOrNull { room - rowsH(it, capH) >= maxOf(mainFullH, mainMinH) } ?: 1
        var alsoH = capH
        var mainH = room - rowsH(rows, alsoH)
        if (rows == 1 && mainH < alsoH * MAIN_OVER) {
            alsoH = ((room - rowExtra) / (1 + MAIN_OVER)).toInt()
            mainH = room - rowsH(1, alsoH)
        }
        if (mainH < mainMinH) {
            mainH = mainMinH
            alsoH = room - mainMinH - rowExtra
        }
        if (alsoH * 2 / 3 < alsoMinW) return CoverShelf(minOf(maxOf(room, 0), mainCapH), cols, 0, 0)
        return CoverShelf(minOf(mainH, mainCapH), cols, rows, alsoH * 2 / 3)
    }

    /**
     * 표지 가로: 함께 읽는 책 한 줄, 열은 [innerW] 에 [cellMinW] 칸이 드는 만큼([minCols] 열부터). 두 표지 높이는 [ALSO_SHARE] 로 나누고
     * 함께 읽는 책은 칸 폭까지, 맨 앞 책은 [mainCapH] 까지.
     */
    fun coverWide(room: Int, rowExtra: Int, innerW: Int, colGap: Int, cellMinW: Int, minCols: Int, alsoMinW: Int, mainCapH: Int): CoverShelf {
        val cols = maxOf(minCols, (innerW + colGap) / (cellMinW + colGap))
        val cellW = (innerW - (cols - 1) * colGap) / cols
        val free = room - rowExtra
        val alsoH = minOf(cellW * 3 / 2, (free * ALSO_SHARE / (1 + ALSO_SHARE)).toInt())
        if (alsoH * 2 / 3 < alsoMinW) return CoverShelf(minOf(maxOf(room, 0), mainCapH), cols, 0, 0)
        return CoverShelf(minOf(free - alsoH, mainCapH), cols, 1, alsoH * 2 / 3)
    }

    private const val MAX_ROWS = 5
    private const val MAX_COVER_ROWS = 3
    /** 서재: 아래가 한 줄 높이의 이만큼 넘게 남으면 한 줄 더 */
    private const val EXTRA_ROW = 0.28f
    /** 표지 한 줄: 맨 앞 책 표지 높이 ÷ 함께 읽는 책 표지 높이가 이보다 작아지지 않게 */
    private const val MAIN_OVER = 1.4f
    /** 표지 가로: 함께 읽는 책 표지 높이 ÷ 맨 앞 책 표지 높이 */
    private const val ALSO_SHARE = 0.644f
}

/**
 * 표지 위 반납일 딱지 글자(서재·표지 모양, 기본 모양의 함께 읽는 책): `9일` · `오늘` · `지남`. 반납일을 모르면 null.
 * 기본 모양 맨 앞 책과 목록·상세의 반납일([bookStatus])은 `반납 9일 남음` 그대로다.
 */
fun Context.dueTag(book: ShelfBook, today: LocalDate = LocalDate.now()): String? {
    if (book.due < 0) return null
    val left = book.due - today.toEpochDay()
    return when {
        left > 0 -> getString(R.string.due_tag_days, left.toInt())
        left == 0L -> getString(R.string.due_tag_today)
        else -> getString(R.string.due_tag_over)
    }
}

/**
 * 반납일 딱지: 흰 바탕·둥근 모서리·가는 회색 테두리 안에 굵은 글자. 높이 [HEIGHT_DP](키운 글자만큼 높아진다), 좌우 [PAD_DP].
 * 글자는 줄 높이가 아니라 획 높이로 가운데에 둔다(한글은 받침 쪽으로 치우쳐 보인다). 위아래 기준은 딱지마다 같게 숫자와 세 낱말로 잰다.
 * 표지보다 넓으면 표지 밖으로 나간다(줄이지 않는다, 부모가 자르지 않게 둔다).
 */
class DueTagView(context: Context, private val label: String) : View(context) {
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.BLACK
        typeface = Ui.bold
        textSize = context.spf(TEXT_SP)
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = BORDER
        strokeWidth = maxOf(1f, context.dpf(0.5f))
    }
    private val ink = Rect().also { textPaint.getTextBounds(label, 0, label.length, it) }
    private val ref = Rect().also { r ->
        val s = "0123456789" + context.getString(R.string.due_tag_days, 0) + context.getString(R.string.due_tag_today) + context.getString(R.string.due_tag_over)
        textPaint.getTextBounds(s, 0, s.length, r)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(ink.width() + 2 * context.dp(PAD_DP), (context.dp(HEIGHT_DP) * Ui.textGrow).toInt())

    override fun onDraw(canvas: Canvas) {
        val half = border.strokeWidth / 2
        val r = context.dpf(RADIUS_DP)
        canvas.drawRoundRect(half, half, width - half, height - half, r, r, fill)
        canvas.drawRoundRect(half, half, width - half, height - half, r, r, border)
        canvas.drawText(label, (width - ink.width()) / 2f - ink.left, (height - ref.height()) / 2f - ref.top, textPaint)
    }

    companion object {
        const val TEXT_SP = 12.5f
        const val HEIGHT_DP = 16
        const val PAD_DP = 4
        const val RADIUS_DP = 3
        /** 표지 가장자리(위·오른쪽)에서 띄우는 거리 */
        const val INSET_DP = 4
        private const val BORDER = 0xFF787878.toInt()
    }
}

/**
 * 홈 모양 고르기 창의 그림 한 장: 홈을 선과 회색 칸으로만 줄인 것(진한 칸 = 맨 앞 책, 연한 칸 = 다른 표지, 가는 막대 = 글자,
 * 아래 동그라미 넷 = 자주 쓰는 앱, 맨 아래 선 = 하단 줄). 높이는 폭의 [RATIO] 배. 고른 것은 굵은 검정 테두리.
 */
class HomeStyleThumb(context: Context, private val style: HomeStyle, private val picked: Boolean) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (w * RATIO).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        // 그림은 폭 60 칸으로 그린다(u = 한 칸)
        val u = width / 60f
        fun box(x: Float, y: Float, w: Float, h: Float, color: Int) {
            paint.style = Paint.Style.FILL
            paint.color = color
            canvas.drawRect(x * u, y * u, (x + w) * u, (y + h) * u, paint)
        }
        fun cover(x: Float, y: Float, w: Float, main: Boolean = false) = box(x, y, w, w * 1.5f, if (main) Ui.LIGHT_GRAY else Ui.DIVIDER)
        fun line(x: Float, y: Float, w: Float, strong: Boolean = false) = box(x, y, w, 2.2f, if (strong) Ui.LIGHT_GRAY else Ui.DIVIDER)
        fun rule(y: Float, x0: Float, x1: Float) {
            paint.color = Ui.LIGHT_GRAY
            paint.strokeWidth = context.dpf(1)
            canvas.drawLine(x0 * u, y * u, x1 * u, y * u, paint)
        }
        canvas.drawColor(Color.WHITE)
        val left = 6f
        val inner = 60f - 2 * left
        val top = 7f
        // 맨 앞 책: 표지 옆 제목·저자, 아래 끝에 진행률
        fun mainBook() {
            cover(left, top, 17f, main = true)
            line(left + 21, top + 2, 22f, strong = true)
            line(left + 21, top + 8, 14f)
            line(left + 21, top + 21, inner - 21)
        }
        when (style) {
            HomeStyle.BASIC -> {
                mainBook()
                for (i in 0 until 3) {
                    val x = left + i * inner / 3
                    cover(x, top + 31, 5.5f)
                    line(x + 7.5f, top + 32, 7f)
                    line(x + 7.5f, top + 36, 5f)
                }
            }
            HomeStyle.COVER -> {
                mainBook()
                val w = (inner - 3 * 2.5f) / 4
                for (i in 0 until 4) cover(left + i * (w + 2.5f), top + 30, w)
            }
            HomeStyle.LIBRARY -> {
                val w = inner / 3 - 4.7f
                for (r in 0 until 2) for (i in 0 until 3) {
                    val x = left + i * inner / 3 + 1
                    val y = top + r * 25
                    cover(x, y, w, main = r == 0 && i == 0)
                    line(x, y + w * 1.5f + 1.5f, w)
                }
            }
        }
        // 자주 쓰는 앱과 하단 줄
        val h = height / u
        rule(h - 20, left, left + inner)
        paint.style = Paint.Style.FILL
        paint.color = Ui.DIVIDER
        for (i in 0 until 4) canvas.drawCircle((left + inner / 4 * (i + 0.5f)) * u, (h - 14) * u, 3 * u, paint)
        rule(h - 7, 0f, 60f)
        // 테두리: 고른 것은 굵은 검정
        paint.style = Paint.Style.STROKE
        paint.color = if (picked) Ui.BLACK else Ui.LIGHT_GRAY
        paint.strokeWidth = context.dpf(if (picked) PICKED_DP else 1f)
        val half = paint.strokeWidth / 2
        canvas.drawRect(half, half, width - half, height - half, paint)
    }

    companion object {
        const val RATIO = 1.36f
        private const val PICKED_DP = 3f
    }
}

/**
 * 홈 모양 고르기 창(설정 › 화면 › 홈 모양): 세 모양의 그림([HomeStyleThumb])을 나란히, 아래에 이름. 지금 값은 굵은 테두리 · 굵은 이름 + ✓.
 * 이름만으로는 뜻이 잘 전해지지 않아 다른 고르기 창([choose])과 달리 그림으로 고른다. 그림 폭은 [THUMB_DP] 까지(좁으면 칸 폭에 맞춘다).
 * 그림이나 이름을 누르면 고르고 닫는다.
 */
fun Context.chooseHomeStyle(selected: HomeStyle, onPick: (HomeStyle) -> Unit) {
    val styles = HomeStyle.entries
    val inner = resources.displayMetrics.widthPixels - 2 * dp(Ui.MARGIN)
    val w = minOf(dp(THUMB_DP), (inner - (styles.size - 1) * dp(THUMB_GAP_MIN_DP)) / styles.size)
    // 세로는 세 장을 폭에 고르게, 가로(넓은 화면)는 왼쪽부터 같은 간격으로
    val gap = if (landscape()) dp(THUMB_GAP_WIDE_DP) else (inner - styles.size * w) / (styles.size - 1)
    var dialog: Dialog? = null
    val row = hbox().apply {
        gravity = Gravity.TOP
        styles.forEachIndexed { i, style ->
            val on = style == selected
            addView(vbox().apply {
                addView(HomeStyleThumb(context, style, on), lp(w, WRAP))
                // 키운 글자로 이름이 그림 폭에 들지 않으면(영어 `Standard  ✓`) 기준 크기까지 줄인다
                addView(text(getString(style.label) + if (on) "  ✓" else "", THUMB_LABEL_SP, if (on) Ui.bold else Ui.regular).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    fitLines(THUMB_LABEL_SP, w)
                }, lp(MATCH, WRAP).apply { topMargin = dp(12) })
                contentDescription = getString(style.label)
                // 닫히는 중에 한 번 더 눌린 것은 버린다(다른 메뉴와 같다)
                setOnClickListener {
                    val shown = dialog?.takeIf { it.isShowing } ?: return@setOnClickListener
                    shown.dismiss()
                    onPick(style)
                }
            }, lp(w, WRAP).apply { if (i > 0) marginStart = gap })
        }
    }
    dialog = Sheet(this).header(getString(R.string.home_style)).note(row).show()
}

private const val THUMB_DP = 148
private const val THUMB_LABEL_SP = 20f
private const val THUMB_GAP_MIN_DP = 12
private const val THUMB_GAP_WIDE_DP = 24
