package com.woody.pebbledesk

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters

/**
 * ⑫ 독서 기록: 연속일과 오늘 링, 최근 4주 목표 링 달력(애플 활동 앱처럼), 이번 주·이번 달·올해 합계, 이번 주 앱별 시간.
 * 홈 하단의 `오늘 42분`이나 설정 > 독서 기록으로 연다. 숫자는 [ReadingLog] 에서 온다.
 */
class ReadingActivity : EinkActivity() {
    private lateinit var body: LinearLayout

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
        val goalMs = prefs.readingGoalMin * 60_000L
        // 목표가 없으면 읽은 날(1분 이상)만 꽉 찬 링으로
        fun frac(day: LocalDate): Float {
            val ms = days.total(day)
            return if (goalMs > 0) (ms.toFloat() / goalMs).coerceAtMost(1f) else if (ms >= ReadingLog.MIN_DAY_MS) 1f else 0f
        }

        val since = ReadingLog.since(this) ?: today
        body.addView(hero(days, today, frac(today)))
        body.addView(calendar(today, since, goalMs, ::frac))
        body.addView(hline(1, Ui.DIVIDER))
        body.addView(totals(days, today, since, goalMs))
        body.addView(hline(1, Ui.DIVIDER))
        body.addView(byApp(days, today))
    }

    /** 왼쪽 큰 연속일, 오른쪽 오늘 링(가운데 오늘 읽은 시간) */
    private fun hero(days: ReadingDays, today: LocalDate, todayFrac: Float) = hbox().apply {
        setPadding(dp(Ui.MARGIN), dp(8), dp(Ui.MARGIN), 0)
        addView(text(ReadingLog.streak(days, today).toString(), 80f, Ui.heavy))
        addView(vbox().apply {
            addView(text(getString(R.string.streak_sub, ReadingLog.longestStreak(days)), 15f, Ui.medium, Ui.GRAY))
            addView(text(getString(R.string.streak_days), 28f, Ui.bold), lp(WRAP, WRAP).apply { topMargin = dp(4) })
        }, lp(0, WRAP, 1f).apply { marginStart = dp(10) })
        addView(FrameLayout(context).apply {
            addView(RingView(context, 14f).apply { frac = todayFrac }, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(vbox().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                addView(text(duration(days.total(today)), 17f, Ui.bold))
                if (prefs.readingGoalMin > 0) addView(text(getString(R.string.goal_of, duration(prefs.readingGoalMin * 60_000L)), 11f, Ui.medium, Ui.GRAY))
            }, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        }, lp(dp(86), dp(86)))
    }

    /** 최근 4주(월~일) 링 달력. 날짜는 링 위에 작게, 오늘은 검은 동그라미 안에. 기록 전([since] 전)·앞날은 흐린 빈 링. */
    private fun calendar(today: LocalDate, since: LocalDate, goalMs: Long, progress: (LocalDate) -> Float) = vbox().apply {
        val locale = resources.configuration.locales[0]
        val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(WEEKS - 1L)
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
        addView(View(context), lp(MATCH, dp(14)))
    }

    /** 이번 주 · 이번 달 목표 달성(목표가 없으면 읽은 날) · 올해 */
    private fun totals(days: ReadingDays, today: LocalDate, since: LocalDate, goalMs: Long) = hbox().apply {
        setPadding(dp(Ui.MARGIN), dp(10), dp(Ui.MARGIN), dp(10))
        val locale = resources.configuration.locales[0]
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        fun sum(from: LocalDate) = days.filterKeys { it in from..today }.keys.sumOf { days.total(it) }
        // 이번 달 중 기록이 있는 날만 센다.
        val monthDays = (1..today.dayOfMonth).map { today.withDayOfMonth(it) }.filter { it >= since }
        val hit = monthDays.count { days.total(it) >= (if (goalMs > 0) goalMs else ReadingLog.MIN_DAY_MS) }
        val month = today.format(DateTimeFormatter.ofPattern(getString(R.string.month_fmt), locale))
        listOf(
            getString(R.string.this_week) to duration(sum(weekStart)),
            getString(if (goalMs > 0) R.string.month_goal else R.string.month_read, month) to getString(R.string.days_of, hit, monthDays.size),
            getString(R.string.this_year) to duration(sum(today.withDayOfYear(1))),
        ).forEach { (label, value) ->
            addView(vbox().apply {
                addView(text(label, 13f, Ui.medium, Ui.GRAY))
                addView(text(value, 20f, Ui.bold), lp(WRAP, WRAP).apply { topMargin = dp(4) })
            }, lp(0, WRAP, 1f))
        }
    }

    /** 이번 주 앱별 시간(많은 순 세 개). 막대는 가장 많은 앱 기준. */
    private fun byApp(days: ReadingDays, today: LocalDate) = vbox().apply {
        setPadding(dp(Ui.MARGIN), dp(10), dp(Ui.MARGIN), 0)
        addView(text(getString(R.string.apps_this_week), 13f, Ui.bold, Ui.GRAY))
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val perApp = mutableMapOf<String, Long>()
        days.filterKeys { it in weekStart..today }.values.forEach { apps -> apps.forEach { (pkg, ms) -> perApp[pkg] = (perApp[pkg] ?: 0) + ms } }
        val top = perApp.filterValues { it >= 60_000 }.entries.sortedByDescending { it.value }.take(3)
        if (top.isEmpty()) {
            addView(text(getString(R.string.reading_empty), 15f, color = Ui.LIGHT_GRAY), lp(WRAP, WRAP).apply { topMargin = dp(8) })
            return@apply
        }
        val apps = AppStore.load(context)
        val max = top.first().value.toFloat()
        top.forEachIndexed { i, (pkg, ms) ->
            addView(hbox().apply {
                val label = apps.firstOrNull { it.pkg == pkg }?.label ?: pkg
                addView(text(label, 16f), lp(0, WRAP, 4f))
                // 막대: 칸을 이 앱 시간 : 나머지 비율로 나눈다.
                addView(hbox().apply {
                    addView(View(context).apply { setBackgroundColor(if (i == 0) Ui.BLACK else Ui.GRAY) }, lp(0, dp(8), ms.toFloat()))
                    addView(View(context), lp(0, dp(8), max - ms))
                }, lp(0, dp(8), 4f))
                addView(text(duration(ms), 15f, Ui.medium).apply { gravity = Gravity.END }, lp(0, WRAP, 2f))
            }, lp(MATCH, WRAP).apply { topMargin = dp(6) })
        }
    }

    companion object {
        private const val WEEKS = 4

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
    private val weekdays = (1..7).map { DayOfWeek.of(it).getDisplayName(TextStyle.NARROW, context.resources.configuration.locales[0]) }
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
