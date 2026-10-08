package com.woody.pebbledesk

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import kotlin.math.sqrt

/**
 * ⑫ 독서 기록: 연속일과 오늘 링, 최근 4주 목표 링 달력(애플 활동 앱처럼), 이번 달 목표 달성, 이번 주·이번 달·올해 합계, 이번 주 책별 시간.
 * 홈 하단의 `오늘 42분`이나 설정 > 독서 기록으로 연다. 숫자는 [ReadingLog] 에서 온다.
 */
class ReadingActivity : EinkActivity() {
    private lateinit var body: LinearLayout
    /** 이번 주 읽은 책(다섯 권이 넘으면 쪽을 넘긴다). 읽은 책이 없으면 null. */
    private var bookRows: PagedRows? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = vbox()
        root.addView(titleRow(getString(R.string.reading_stats)) { finish() })
        root.addView(hline(2))
        body = vbox()
        root.addView(body, lp(MATCH, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        body.removeAllViews()
        val days = ReadingLog.days(this, ReadingLog.readerPackages(this))
        if (days == null) {
            body.addView(text(getString(R.string.reading_no_access), 18f, color = Ui.GRAY, lines = 3).apply {
                setPadding(dp(Ui.MARGIN), dp(40), dp(Ui.MARGIN), 0)
            })
            return
        }
        val today = LocalDate.now()
        // 지난날은 그날 정해져 있던 목표로(목표를 바꿔도 지난 링은 그대로). 제목·오늘 링은 지금 목표.
        val goals = prefs.readingGoals()
        val goalMs = prefs.readingGoalMin * 60_000L
        // 목표가 없던 날은 읽은 날(1분 이상)만 꽉 찬 링으로
        fun frac(day: LocalDate): Float {
            val ms = days.total(day)
            val goal = goals(day) * 60_000L
            return if (goal > 0) (ms.toFloat() / goal).coerceAtMost(1f) else if (ms >= ReadingLog.MIN_DAY_MS) 1f else 0f
        }

        val since = ReadingLog.since(this) ?: today
        body.addView(hero(days, today, goalMs, frac(today), monthGoal(days, today, since, goalMs, ::frac)))
        body.addView(calendar(today, since, goalMs, ::frac))
        // 아래: 왼쪽 합계 · 오른쪽 이번 주 읽은 책. 칸마다 달력처럼 제목과 그 아래 선(두 칸 모두 위에 맞춘다).
        body.addView(hbox().apply {
            gravity = Gravity.TOP
            setPadding(dp(Ui.MARGIN), dp(10), dp(Ui.MARGIN), 0)
            addView(section(getString(R.string.read_time), totals(days, today)), lp(dp(TOTALS_W), WRAP))
            val page = text("", 13f, Ui.bold, Ui.GRAY)
            addView(section(getString(R.string.books_this_week), byBook(days, today, page), page), lp(0, WRAP, 1f).apply { marginStart = dp(BOOKS_GAP) })
        })
    }

    /** 제목(오른쪽 끝에 [page]: 쪽 표시) · 그 아래 선 · [content] */
    private fun section(title: String, content: View, page: TextView? = null) = vbox().apply {
        addView(hbox().apply {
            addView(text(title, 13f, Ui.bold, Ui.GRAY), lp(0, WRAP, 1f))
            // 쪽 표시가 작아 누르기 어려우므로 제목 줄 어디를 눌러도 넘긴다.
            page?.let { addView(it, lp(WRAP, WRAP)); setOnClickListener { _ -> it.performClick() } }
        }, lp(MATCH, WRAP).apply { bottomMargin = dp(8) })
        addView(hline(1, inset = false))
        addView(content, lp(MATCH, WRAP).apply { topMargin = dp(4) })
    }

    /** 왼쪽 큰 연속일과 이번 달 목표 달성([month]: 제목 to 값), 오른쪽 오늘 링(가운데 오늘 읽은 시간) */
    private fun hero(days: ReadingDays, today: LocalDate, goalMs: Long, todayFrac: Float, month: Pair<String, String>) = hbox().apply {
        setPadding(dp(Ui.MARGIN), dp(8), dp(Ui.MARGIN), 0)
        addView(text(ReadingLog.streak(days, today).toString(), 80f, Ui.heavy))
        addView(vbox().apply {
            addView(text(getString(R.string.streak_sub, ReadingLog.longestStreak(days)), 15f, Ui.medium, Ui.GRAY))
            addView(text(getString(R.string.streak_days), 28f, Ui.bold), lp(WRAP, WRAP).apply { topMargin = dp(4) })
            addView(hbox().apply {
                addView(text(month.first, 15f, Ui.medium, Ui.GRAY))
                addView(text(month.second, 15f, Ui.bold), lp(WRAP, WRAP).apply { marginStart = dp(6) })
            }, lp(WRAP, WRAP).apply { topMargin = dp(8) })
        }, lp(0, WRAP, 1f).apply { marginStart = dp(10) })
        val time = text(duration(days.total(today)), 17f, Ui.bold)
        val goal = if (goalMs > 0) text(getString(R.string.goal_of, duration(goalMs)), 11f, Ui.medium, Ui.GRAY) else null
        fitInRing(time, goal)
        addView(FrameLayout(context).apply {
            addView(RingView(context, RING_STROKE).apply { frac = todayFrac }, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(vbox().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                addView(time, lp(WRAP, WRAP))
                goal?.let { addView(it, lp(WRAP, WRAP)) }
            }, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        }, lp(dp(RING), dp(RING)))
    }

    /** 오늘 링 안쪽 동그라미에 들어가게 [time] 글자를 줄인다(`1시간 34분`처럼 길면 링에 겹친다). */
    private fun fitInRing(time: TextView, goal: TextView?) {
        fun TextView.lineH() = paint.fontMetrics.let { it.descent - it.ascent }
        val r = dpf(RING / 2f - RING_STROKE)
        var sp = 17f
        while (sp > 12f) {
            val half = (time.lineH() + (goal?.lineH() ?: 0f)) / 2
            val room = 2 * sqrt(r * r - half * half) - dpf(6)
            if (time.paint.measureText(time.text.toString()) <= room) break
            sp -= 0.5f
            time.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        }
    }

    /** 이번 주의 첫날(설정의 한 주 시작 요일). 달력·이번 주 합계·이번 주 책별이 모두 따른다. */
    private fun startOfWeek(today: LocalDate): LocalDate = today.with(TemporalAdjusters.previousOrSame(prefs.firstDayOfWeek))

    /** 최근 4주 링 달력. 날짜는 링 위에 작게, 오늘은 검은 동그라미 안에. 기록 전([since] 전)·앞날은 흐린 빈 링. */
    private fun calendar(today: LocalDate, since: LocalDate, goalMs: Long, progress: (LocalDate) -> Float) = vbox().apply {
        val locale = resources.configuration.locales[0]
        val start = startOfWeek(today).minusWeeks(WEEKS - 1L)
        val end = start.plusDays(WEEKS * 7L - 1)
        val dateFmt = DateTimeFormatter.ofPattern(getString(R.string.date_short_fmt), locale)
        addView(hbox().apply {
            setPadding(dp(Ui.MARGIN), dp(10), dp(Ui.MARGIN), dp(8))
            val title = if (goalMs > 0) getString(R.string.goal_title, duration(goalMs)) else getString(R.string.read_days_title)
            addView(text(title, 13f, Ui.bold, Ui.GRAY), lp(0, WRAP, 1f))
            addView(text("${start.format(dateFmt)} – ${end.format(dateFmt)}", 12f, Ui.medium, Ui.LIGHT_GRAY))
        })
        addView(hline(1))
        // 요일 줄과 4주치 날짜·링을 뷰 하나에 그린다(칸마다 뷰를 만들면 백 개가 넘어 느리다).
        addView(RingCalendar(context, start, today, since, WEEKS, progress), lp(MATCH, WRAP))
        addView(View(context), lp(MATCH, dp(24)))
    }

    /** 이번 달 목표 달성(그날의 목표로, [progress] 가 꽉 찬 날, 기록이 있는 날 중). 지금 목표가 없으면 제목대로 읽은 날을 센다. */
    private fun monthGoal(days: ReadingDays, today: LocalDate, since: LocalDate, goalMs: Long, progress: (LocalDate) -> Float): Pair<String, String> {
        val monthDays = (1..today.dayOfMonth).map { today.withDayOfMonth(it) }.filter { it >= since }
        val hits = monthDays.count { if (goalMs > 0) progress(it) >= 1f else days.total(it) >= ReadingLog.MIN_DAY_MS }
        val month = today.format(DateTimeFormatter.ofPattern(getString(R.string.month_fmt), resources.configuration.locales[0]))
        return getString(if (goalMs > 0) R.string.month_goal else R.string.month_read, month) to getString(R.string.days_of, hits, monthDays.size)
    }

    /** 왼쪽 칸: 이번 주 · 이번 달 · 올해 읽은 시간(위아래로) */
    private fun totals(days: ReadingDays, today: LocalDate) = vbox().apply {
        setPadding(0, dp(6), 0, 0)
        fun sum(from: LocalDate) = days.filterKeys { it in from..today }.keys.sumOf { days.total(it) }
        listOf(
            getString(R.string.this_week) to duration(sum(startOfWeek(today))),
            getString(R.string.this_month) to duration(sum(today.withDayOfMonth(1))),
            getString(R.string.this_year) to duration(sum(today.withDayOfYear(1))),
        ).forEachIndexed { i, (label, value) ->
            addView(text(label, 13f, Ui.medium, Ui.GRAY), lp(WRAP, WRAP).apply { if (i > 0) topMargin = dp(14) })
            addView(text(value, 20f, Ui.bold), lp(WRAP, WRAP).apply { topMargin = dp(4) })
        }
    }

    /**
     * 오른쪽 칸: 이번 주 책별 시간(많은 순, 한 쪽에 [BOOKS]권). 한 줄에 책 이름(목록에서 고친 이름으로) · 시간, 줄 사이에 옅은 선.
     * 어떤 책인지 모르는 시간(이 기능 전, 그 앱에서 편 책을 한 번도 몰랐을 때)은 합계에만 들어가고 여기에는 보이지 않는다.
     */
    private fun byBook(days: ReadingDays, today: LocalDate, page: TextView): View {
        bookRows = null
        val week = days.filterKeys { it in startOfWeek(today)..today }.values
        val perBook = mutableMapOf<String, Long>()
        week.forEach { keys ->
            keys.forEach { (key, ms) ->
                val title = readingTitle(key) ?: return@forEach
                val label = BookShelf.findByTitle(this, title)?.title ?: title
                perBook[label] = (perBook[label] ?: 0) + ms
            }
        }
        val books = perBook.filterValues { it >= 60_000 }.entries.sortedByDescending { it.value }
        if (books.isEmpty()) {
            // 읽긴 했는데 어떤 책인지 모르면 자동 추가를 켜라고 알린다.
            val read = week.sumOf { it.values.sum() } >= 60_000
            return text(getString(if (read) R.string.books_unknown else R.string.reading_empty), 15f, color = Ui.LIGHT_GRAY, lines = 3).apply {
                setPadding(0, dp(8), 0, 0)
            }
        }
        // 자주 쓰는 앱처럼 [BOOKS]줄씩, 넘치면 제목 오른쪽 `1 / 2 ›`(누르거나 밀면 다음 쪽). 덜 찬 쪽도 높이는 같다.
        val rows = PagedRows(this, dp(BOOK_ROW), fixedRows = BOOKS, insetDividers = false)
        rows.onPageChanged = {
            val n = rows.pageCount
            page.text = if (n <= 1) "" else "${rows.page + 1} / $n   ›"
        }
        page.setOnClickListener { rows.next() }
        rows.setRows(books.map { (label, ms) -> {
            hbox().apply {
                addView(text(label, 16f), lp(0, WRAP, 1f))
                addView(text(duration(ms), 15f, Ui.medium), lp(WRAP, WRAP).apply { marginStart = dp(12) })
            }
        } })
        bookRows = rows
        return rows
    }

    override fun onSwipe(toLeft: Boolean): Boolean {
        val rows = bookRows?.takeIf { it.pageCount > 1 } ?: return false
        if (toLeft) rows.next() else rows.prev()
        return true
    }

    companion object {
        private const val WEEKS = 4
        /** 아래 왼쪽 합계 칸 폭(dp) */
        private const val TOTALS_W = 150
        /** 왼쪽 합계 칸과 오른쪽 책 칸 사이(dp) */
        private const val BOOKS_GAP = 24
        /** 이번 주 읽은 책 한 쪽의 줄 수와 줄 높이(dp) */
        private const val BOOKS = 5
        private const val BOOK_ROW = 36
        /** 오늘 링 지름·굵기(dp) */
        private const val RING = 104
        private const val RING_STROKE = 12f

        fun intent(context: Context) = Intent(context, ReadingActivity::class.java)
    }
}

/** 둥근 끝 진행 링(애플 활동 링처럼) 그리기. 바탕 링 위에 12시부터 시계 방향으로 [frac](0~1)만큼. */
private class RingPainter(stroke: Float) {
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = stroke; color = Ui.BLACK; strokeCap = Paint.Cap.ROUND
    }
    /** 선 굵기의 절반: 링이 칸 안에 들어오게 이만큼 안쪽에 그린다. */
    val inset = stroke / 2

    fun draw(canvas: Canvas, oval: RectF, frac: Float, track: Int) {
        trackPaint.color = track
        canvas.drawOval(oval, trackPaint)
        if (frac >= 1f) canvas.drawOval(oval, arcPaint)
        else if (frac > 0f) canvas.drawArc(oval, -90f, 360f * frac, false, arcPaint)
    }
}

/** 링 바탕(크레마는 연한 회색을 어둡게 그려서 디자인 값보다 밝게 잡는다) */
private const val RING_TRACK = 0xFFF2F2F2.toInt()
/** 앞날·기록 전 날의 링 바탕 */
private const val NO_DATA_TRACK = 0xFFF9F9F9.toInt()

/** 링 하나(오늘 링) */
class RingView(context: Context, strokeDp: Float) : View(context) {
    var frac = 0f
    private val painter = RingPainter(context.dpf(strokeDp))
    private val oval = RectF()

    override fun onDraw(canvas: Canvas) {
        oval.set(painter.inset, painter.inset, width - painter.inset, height - painter.inset)
        painter.draw(canvas, oval, frac, RING_TRACK)
    }
}

/**
 * 요일 줄 + [weeks]주(월~일) 링 달력. 날짜는 링 위에 작게, 오늘은 검은 동그라미 안 흰 글자.
 * 앞날과 기록 시작일([since]) 전은 흐린 날짜·아주 연한 빈 링.
 */
private class RingCalendar(
    context: Context,
    private val start: LocalDate,
    private val today: LocalDate,
    private val since: LocalDate,
    private val weeks: Int,
    private val progress: (LocalDate) -> Float,
) : View(context) {
    /** [start] 의 요일부터 일주일 */
    private val weekdays = (0L..6L).map { start.dayOfWeek.plus(it).getDisplayName(TextStyle.NARROW, context.resources.configuration.locales[0]) }
    private val margin = context.dpf(Ui.MARGIN)
    private val weekdayTop = context.dpf(8)
    private val weekdayH = context.dpf(22)
    private val rowGap = context.dpf(4)
    private val labelH = context.dpf(17)
    private val pillHalf = context.dpf(10)
    private val ringGap = context.dpf(2)
    private val ring = context.dpf(28)
    private val rowH = rowGap + labelH + ringGap + ring
    private val painter = RingPainter(context.dpf(6))
    private val oval = RectF()
    private fun textPaint(sp: Float, face: android.graphics.Typeface, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp * context.resources.displayMetrics.scaledDensity
        typeface = face
        this.color = color
    }
    private val weekdayPaint = textPaint(12f, Ui.medium, Ui.LIGHT_GRAY)
    private val datePaint = textPaint(11f, Ui.medium, Ui.GRAY)
    private val todayPaint = textPaint(11f, Ui.bold, Color.WHITE)
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.BLACK }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (weekdayH + weeks * rowH).toInt())
    }

    /** [cy] 에 세로 가운데로 */
    private fun Canvas.centerText(s: String, cx: Float, cy: Float, p: Paint) =
        drawText(s, cx, cy - (p.ascent() + p.descent()) / 2, p)

    override fun onDraw(canvas: Canvas) {
        val cw = (width - 2 * margin) / 7
        weekdays.forEachIndexed { i, d -> canvas.centerText(d, margin + cw * (i + 0.5f), weekdayTop + (weekdayH - weekdayTop) / 2, weekdayPaint) }
        for (w in 0 until weeks) for (i in 0 until 7) {
            val day = start.plusDays(w * 7L + i)
            val cx = margin + cw * (i + 0.5f)
            val top = weekdayH + w * rowH + rowGap
            val label = if (day.dayOfMonth == 1) "${day.monthValue}/1" else day.dayOfMonth.toString()
            val noData = day > today || day < since
            if (day == today) {
                canvas.drawOval(cx - pillHalf, top, cx + pillHalf, top + labelH, pillPaint)
                canvas.centerText(label, cx, top + labelH / 2, todayPaint)
            } else {
                datePaint.color = if (noData) Ui.DIVIDER else Ui.GRAY
                canvas.centerText(label, cx, top + labelH / 2, datePaint)
            }
            val ringTop = top + labelH + ringGap
            oval.set(cx - ring / 2 + painter.inset, ringTop + painter.inset, cx + ring / 2 - painter.inset, ringTop + ring - painter.inset)
            painter.draw(canvas, oval, if (noData) 0f else progress(day), if (noData) NO_DATA_TRACK else RING_TRACK)
        }
    }
}
