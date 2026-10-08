package com.woody.pebbledesk

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * ⑬ 올해 읽은 책(독서 기록 제목 오른쪽 `올해 읽은 책 ›`). 제목 오른쪽 `2026 ▾` 로 지난해를 고르면 `2025년 읽은 책`.
 * 그해 1분 이상 읽었거나 그해 다 읽은 책을 모두 보인다:
 * 읽고 있는 책·다 읽은 책·뺀 책·목록에 넣지 못한 책(읽은 시간에만 있는 제목). 최근에 읽은(다 읽은) 순.
 * 목록은 줄마다 표지·제목·저자(읽는 중이면 `· 읽는 중`, 기간·읽은 시간은 누르면 나오는 메뉴에), 격자는 한 줄에 표지 4권과 그 아래 책 이름.
 * 다 읽은 책은 검은 뱃지 `✓ 다 읽음`: 목록은 줄 오른쪽 끝, 격자는 표지 오른쪽 위(흰 테두리로 어두운 표지에서도 보이게).
 * 하단 왼쪽 `목록 · 격자` 로 바꾸고 기억한다. 책을 누르면 `다 읽음` / `다시 읽기` / `기록에서 지우기`.
 */
class YearBooksActivity : EinkActivity() {
    /** 목록·격자(줄 높이가 달라 따로 두고 하나만 보인다) */
    private lateinit var listRows: PagedRows
    private lateinit var gridRows: PagedRows
    private val rows get() = if (prefs.yearGrid) gridRows else listRows
    private lateinit var pageText: TextView
    private lateinit var viewToggle: TextView
    private lateinit var empty: View
    private var cellW = 0
    /** 보고 있는 해(처음에는 올해). 제목 오른쪽 `2026 ▾` 로 고른다. */
    private var year = LocalDate.now().year
    private lateinit var titleText: TextView
    private lateinit var yearPick: TextView

    /**
     * 한 줄: 책(목록에 없던 책은 제목만 든 임시 책), 상태, 다 읽은 날, 남은 기록 전체에서 읽은 시간·처음 읽은 날·마지막으로 읽은 날.
     * [sortDay] 는 마지막으로 읽은 날과 다 읽은 날 중 늦은 날(목록 순서).
     */
    private class Row(
        val book: ShelfBook, val state: State, val finished: LocalDate?,
        val ms: Long, val first: LocalDate?, val last: LocalDate?, val sortDay: LocalDate,
    )

    private enum class State {
        /** 읽고 있는 책 목록에 있다 */
        READING,
        FINISHED,
        /** 뺀 책이거나 목록에 넣지 못한 책 */
        LEFT,
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = vbox()
        root.addView(titleRow("") { finish() }.apply {
            // 제목 줄: `‹` · 제목 · (빈 자리) · `2026 ▾`(고를 해가 둘 이상일 때)
            titleText = getChildAt(1) as TextView
            addView(View(context), lp(0, 0, 1f))
            yearPick = text("", 21f, Ui.bold).apply { setPadding(dp(16), dp(20), 0, dp(20)) }
            addView(yearPick, lp(WRAP, WRAP))
        })
        root.addView(hline(2))
        empty = text(getString(R.string.year_books_empty), 18f, color = Ui.GRAY, lines = 3).apply {
            setPadding(dp(Ui.MARGIN), dp(40), dp(Ui.MARGIN), 0)
        }
        root.addView(empty)
        cellW = (resources.displayMetrics.widthPixels - 2 * dp(Ui.MARGIN) - (COLUMNS - 1) * dp(GAP_DP)) / COLUMNS
        listRows = PagedRows(this, dp(ROW_DP))
        gridRows = PagedRows(this, cellW * 3 / 2 + dp(GRID_TEXT_DP), dividers = false)
        val pages = FrameLayout(this)
        pages.addView(listRows, FrameLayout.LayoutParams(MATCH, MATCH))
        pages.addView(gridRows, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(pages, lp(MATCH, 0, 1f))

        root.addView(hline(1, inset = false))
        val footer = FrameLayout(this)
        viewToggle = text("", 21f).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Ui.MARGIN), 0, dp(24), 0)
            setOnClickListener { prefs.yearGrid = !prefs.yearGrid; render() }
        }
        footer.addView(viewToggle, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.START))
        pageText = text("", 21f).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), 0, dp(Ui.MARGIN), 0)
            setOnClickListener { rows.next() }
        }
        footer.addView(pageText, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.END))
        root.addView(footer, lp(MATCH, dp(Ui.FOOTER_DP)))
        for (r in listOf(listRows, gridRows)) r.onPageChanged = { if (r === rows) showPage() }
        setContentView(root)
    }

    override fun onSwipe(toLeft: Boolean): Boolean {
        if (rows.pageCount <= 1) return false
        if (toLeft) rows.next() else rows.prev()
        return true
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun showPage() {
        pageText.text = if (rows.pageCount <= 1) "" else "${rows.page + 1} / ${rows.pageCount}   ›"
    }

    private fun render() {
        val today = LocalDate.now()
        var (list, years) = yearRows(this, year, today)
        // 지난해의 마지막 책을 기록에서 지워 그해가 비면 올해로 돌아온다(빈 지난해에 `올해 읽은 책이 없습니다`가 뜨지 않게).
        if (year !in years) {
            year = today.year
            list = yearRows(this, year, today).first
        }
        titleText.text = if (year == today.year) getString(R.string.year_books) else getString(R.string.year_books_of, year)
        yearPick.text = "$year ▾"
        yearPick.visibility = if (years.size > 1) View.VISIBLE else View.GONE
        yearPick.setOnClickListener {
            choose(getString(R.string.pick_year), years.map { it.toString() }, years.indexOf(year)) {
                year = years[it]
                render()
                rows.showPage(0)
            }
        }
        empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        val grid = prefs.yearGrid
        // 지금 보기는 굵게, 다른 보기는 흐리게: `목록 · 격자`
        viewToggle.text = SpannableStringBuilder().apply {
            listOf(R.string.fav_view_list to !grid, R.string.fav_view_grid to grid).forEachIndexed { i, (res, on) ->
                if (i > 0) append("  ·  ", ForegroundColorSpan(Ui.LIGHT_GRAY), 0)
                append(getString(res), if (on) StyleSpan(Typeface.BOLD) else ForegroundColorSpan(Ui.LIGHT_GRAY), 0)
            }
        }
        viewToggle.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        listRows.visibility = if (grid) View.GONE else View.VISIBLE
        gridRows.visibility = if (grid) View.VISIBLE else View.GONE
        if (grid) gridRows.setRows(list.chunked(COLUMNS).map { line -> { gridRow(line) } })
        else listRows.setRows(list.map { r -> { bookRow(r) } })
        showPage()
    }

    /** 표지 [COLUMNS]권 한 줄. 표지 아래에 책 이름 한 줄(다 읽은 책은 표지의 뱃지로 안다). */
    private fun gridRow(line: List<Row>): View = hbox().apply {
        gravity = Gravity.TOP
        setPadding(dp(Ui.MARGIN), dp(12), dp(Ui.MARGIN), 0)
        line.forEachIndexed { i, r ->
            addView(vbox().apply {
                addView(badgedCover(r), lp(cellW, cellW * 3 / 2))
                addView(text(r.book.title, 13f, Ui.medium).apply { gravity = Gravity.CENTER_HORIZONTAL }, lp(MATCH, WRAP).apply { topMargin = dp(6) })
                setOnClickListener { showMenu(r) }
            }, lp(cellW, MATCH).apply { if (i > 0) marginStart = dp(GAP_DP) })
        }
    }

    /**
     * 다 읽은 책 뱃지 `✓ 다 읽음`(검은 바탕 흰 글자). 아래 글자만으로는 다 읽은 책이 눈에 띄지 않는다.
     * 흑백 화면이라 어두운 표지 위에서도 보이게 흰 테두리를 두른다.
     */
    private fun finishedBadge(): TextView = text("✓ " + getString(R.string.finish_book), 13f, Ui.bold, Color.WHITE).apply {
        gravity = Gravity.CENTER
        background = GradientDrawable().apply { setColor(Ui.BLACK); cornerRadius = dpf(5); setStroke(dp(2), Color.WHITE) }
        setPadding(dp(8), dp(4), dp(8), dp(4))
    }

    /** 격자의 표지. 다 읽은 책은 오른쪽 위에 [finishedBadge]. */
    private fun badgedCover(r: Row): View {
        val cover = coverImage(BookShelf.cover(this, r.book, cellW))
        if (r.state != State.FINISHED) return cover
        return FrameLayout(this).apply {
            addView(cover, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(finishedBadge(), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.END).apply { setMargins(0, dp(5), dp(5), 0) })
        }
    }

    private fun dateFmt() = DateTimeFormatter.ofPattern(getString(R.string.date_short_fmt), resources.configuration.locales[0])

    private fun bookRow(r: Row): View = hbox().apply {
        setPadding(dp(Ui.MARGIN), 0, dp(Ui.MARGIN), 0)
        val w = dp(COVER_DP)
        addView(coverImage(BookShelf.cover(context, r.book, w)), lp(w + dp(2), w * 3 / 2 + dp(2)))
        addView(vbox().apply {
            addView(text(r.book.title, 22f, Ui.bold, lines = 2))
            // 목록은 짧게: 저자(알면)와 `읽는 중`만. 다 읽음은 오른쪽 뱃지가, 기간·읽은 시간은 누르면 나오는 메뉴가 말한다.
            val sub = listOfNotNull(r.book.author.takeIf { it.isNotBlank() }, getString(R.string.reading_now).takeIf { r.state == State.READING })
            if (sub.isNotEmpty()) addView(text(sub.joinToString("  ·  "), 16f, color = Ui.GRAY), lp(WRAP, WRAP).apply { topMargin = dp(6) })
        }, lp(0, WRAP, 1f).apply { marginStart = dp(16) })
        if (r.state == State.FINISHED) addView(finishedBadge(), lp(WRAP, WRAP).apply { marginStart = dp(12) })
        setOnClickListener { showMenu(r) }
    }

    /**
     * 상태 · 기간 · 총 시간(다 읽은 책은 상태 없이, 뱃지가 말한다). 기간은 다 읽은 책 `9월 2일 – 10월 6일`(처음 읽은 날 – 다 읽은 날), 읽는 중 `9월 2일부터`,
     * 뺀 책 `9월 2일 – 9월 20일`(처음 – 마지막으로 읽은 날). 하루뿐이거나 처음 읽은 날을 모르면 그날만.
     */
    private fun status(r: Row): String {
        val fmt = dateFmt()
        val yearFmt = DateTimeFormatter.ofPattern(getString(R.string.date_year_fmt), resources.configuration.locales[0])
        // 시작일이 다른 해(작년에 시작한 책)면 연도를 붙인다.
        fun start(from: LocalDate, to: LocalDate) = from.format(if (from.year != to.year) yearFmt else fmt)
        fun period(from: LocalDate?, to: LocalDate?): String? = when {
            to == null -> from?.let { getString(R.string.since_date, start(it, LocalDate.now())) }
            from == null || from >= to -> to.format(fmt)
            else -> "${start(from, to)} – ${to.format(fmt)}"
        }
        val parts = mutableListOf<String>()
        when (r.state) {
            // `다 읽음`은 글자 대신 머리 오른쪽 위 뱃지로 보인다.
            State.FINISHED -> period(r.first, r.finished)?.let { parts += it }
            State.READING -> {
                parts += getString(R.string.reading_now)
                period(r.first, null)?.let { parts += it }
            }
            State.LEFT -> period(r.first, r.last)?.let { parts += it }
        }
        if (r.ms >= 60_000) parts += getString(R.string.total_time, duration(r.ms))
        return parts.joinToString("  ·  ")
    }

    private fun showMenu(r: Row) {
        val pastId = r.book.id.takeIf { r.state != State.READING && !it.startsWith(TITLE_ONLY) }
        // 머리: 표지 · 제목 · 저자(알면) · 상태
        val sub = listOfNotNull(r.book.author.takeIf { it.isNotBlank() }, status(r)).joinToString("\n")
        val sheet = Sheet(this).header(r.book.title, sub, image = BookShelf.cover(this, r.book, dp(COVER_DP)))
        if (r.state == State.FINISHED) sheet.badge(finishedBadge())
        if (r.state != State.FINISHED) sheet.item(getString(R.string.finish_book)) {
            // 지난해 책은 그해 마지막으로 읽은 날에 다 읽은 것으로(올해는 오늘).
            val on = if (year == LocalDate.now().year) LocalDate.now() else r.sortDay.takeIf { it != LocalDate.MIN } ?: LocalDate.of(year, 12, 31)
            if (r.state == State.READING) BookShelf.finish(this, r.book.id) else BookShelf.finishPast(this, pastId, r.book.title, r.book.app, on)
            render()
        }
        // 다 찼으면 다시 읽기를 두지 않는다(읽고 있는 책에서 먼저 한 권을 빼야 한다).
        if (r.state != State.READING && !BookShelf.isFull(this)) sheet.item(getString(R.string.read_again)) {
            BookShelf.restore(this, pastId, r.book.title, r.book.app)
            render()
        }
        if (r.state != State.READING) sheet.item(getString(R.string.forget_book)) {
            BookShelf.forget(this, pastId, r.book.title)
            render()
        }
        sheet.show()
    }

    companion object {
        private const val COVER_DP = 48
        /** 읽고 있는 책 목록과 같은 줄 높이 */
        private const val ROW_DP = 98
        /** 목록에 없던 책(읽은 시간에만 있는 제목)의 임시 id 머리 */
        private const val TITLE_ONLY = "t:"
        /** 격자: 한 줄 권수, 표지 사이, 표지 밖 높이(위 여백·아래 글자) */
        private const val COLUMNS = 4
        private const val GAP_DP = 14
        private const val GRID_TEXT_DP = 34

        fun intent(context: Context) = Intent(context, YearBooksActivity::class.java)

        /**
         * [year] 에 1분 이상 읽었거나 그해 다 읽은 책과, 고를 수 있는 해(책이 있는 해와 올해, 최근 순).
         * 읽은 시간의 제목을 읽고 있는 책 → 나간 책 순으로 맞추고, 어디에도 없으면 제목만 든 줄. 지운 책은 뺀다.
         * 그해 마지막으로 읽은(다 읽은) 날 순, 같으면 오래 읽은 순. 기간·총 시간은 남은 기록 전체로 센다(해를 넘긴 책도 맞게).
         */
        private fun yearRows(context: Context, year: Int, today: LocalDate): Pair<List<Row>, List<Int>> {
            val shelf = BookShelf.list(context)
            val apps by lazy { AppStore.load(context) }
            class Acc(val book: ShelfBook, val state: State, val finished: LocalDate?, val hidden: Boolean) {
                var ms = 0L
                val yearMs = mutableMapOf<Int, Long>()
                var first: LocalDate? = null
                var last: LocalDate? = null
                /** [year] 에 마지막으로 읽은 날 */
                var yearLast: LocalDate? = null
            }
            val acc = linkedMapOf<String, Acc>()
            fun accOf(title: String, pkg: String): Acc {
                val norm = normTitle(title)
                shelf.firstOrNull { sameTitle(it.title, title) || (it.seenTitle.isNotEmpty() && normTitle(it.seenTitle) == norm) }?.let { b ->
                    return acc.getOrPut(b.id) { Acc(b, State.READING, null, false) }
                }
                BookShelf.findPast(context, title)?.let { p ->
                    return acc.getOrPut(p.book.id) { Acc(p.book, if (p.finished != null) State.FINISHED else State.LEFT, p.finished, p.hidden) }
                }
                val app = apps.firstOrNull { it.pkg == pkg }?.key
                return acc.getOrPut(TITLE_ONLY + norm) { Acc(ShelfBook(TITLE_ONLY + norm, title, "", app, 0), State.LEFT, null, false) }
            }
            ReadingLog.years(context, ReadingLog.readerPackages(context))?.forEach { (y, keys) ->
                keys.forEach { (key, span) ->
                    val title = readingTitle(key) ?: return@forEach
                    val a = accOf(title, readingPkg(key))
                    a.ms += span.ms
                    a.yearMs[y] = (a.yearMs[y] ?: 0) + span.ms
                    span.first?.let { d -> a.first = a.first?.let { minOf(it, d) } ?: d }
                    span.last?.let { d ->
                        a.last = a.last?.let { maxOf(it, d) } ?: d
                        if (y == year) a.yearLast = a.yearLast?.let { maxOf(it, d) } ?: d
                    }
                }
            }
            // 다 읽은 책은 읽은 시간이 없어도(기록을 켜기 전에 읽은 책) 그해에 보인다.
            BookShelf.history(context).filter { it.finished != null }.forEach { p ->
                acc.getOrPut(p.book.id) { Acc(p.book, State.FINISHED, p.finished, p.hidden) }
            }
            val shown = acc.values.filter { !it.hidden }
            val years = (shown.flatMap { a -> a.yearMs.filterValues { it >= ReadingLog.MIN_DAY_MS }.keys + listOfNotNull(a.finished?.year) } + today.year)
                .distinct().sortedDescending()
            val rows = shown
                .filter { (it.yearMs[year] ?: 0) >= ReadingLog.MIN_DAY_MS || it.finished?.year == year }
                .map { a ->
                    val sortDay = maxOf(a.yearLast ?: LocalDate.MIN, a.finished?.takeIf { it.year == year } ?: LocalDate.MIN)
                    Row(a.book, a.state, a.finished, a.ms, a.first, a.last, sortDay)
                }
                .sortedWith(compareByDescending<Row> { it.sortDay }.thenByDescending { it.ms })
            return rows to years
        }
    }
}
