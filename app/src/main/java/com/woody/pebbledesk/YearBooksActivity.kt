package com.woody.pebbledesk

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import java.time.LocalDate

/**
 * ⑬ 올해 읽은 책(독서 기록 제목 오른쪽 `올해 읽은 책 ›`). 제목 오른쪽 `2026 ▾` 로 지난해를 고르면 `2025년 읽은 책`.
 * 그해 1분 이상 읽었거나 그해 완독한 책을 모두 보인다:
 * 읽고 있는 책·완독한 책·뺀 책·목록에 넣지 못한 책(읽은 시간에만 있는 제목). 최근에 읽은(다 읽은) 순.
 * 목록은 줄마다 표지·제목·저자와 별점(완독한 책은 오른쪽 끝에 도장, 읽는 중 표시는 없다), 격자는 한 줄에 표지 4권과 그 아래 책 이름·별점.
 * 한 번이라도 완독한 책은 표지가 어디에 놓이든(격자·목록·상세) 오른쪽 위 모서리를 가로지르는 `완독` 띠. 완독한 날은 모두 남고
 * 상세에 회차마다 기간이 나온다. 다시 읽어도 완독 기록은 그대로다.
 * 하단 왼쪽 `목록 · 격자`, 가운데 `전체 · 완독`(그해 완독한 책만)으로 바꾸고 기억한다. 책을 누르면 홈과 같은 책 상세([showBookDetail]).
 */
class YearBooksActivity : EinkActivity() {
    /** 목록·격자(줄 높이가 달라 따로 두고 하나만 보인다) */
    private lateinit var listRows: PagedRows
    private lateinit var gridRows: PagedRows
    private val rows get() = if (prefs.yearGrid) gridRows else listRows
    private lateinit var pageText: TextView
    private lateinit var viewToggle: TextView
    private lateinit var filterToggle: TextView
    private lateinit var empty: TextView
    private var cellW = 0
    /** 격자 표지 크기: 칸 높이는 그대로 두고 아래 별 줄만큼 줄인다(2:3) */
    private var gridCoverW = 0
    private var gridCoverH = 0
    /** 책(id)마다 별점·감상(그릴 때마다 읽는다) */
    private var reviews: Map<String, Review> = emptyMap()
    /** 보고 있는 해(처음에는 올해). 제목 오른쪽 `2026 ▾` 로 고른다. */
    private var year = LocalDate.now().year
    private lateinit var titleText: TextView
    /** 제목 옆 권수(모든 앱의 앱 수와 같은 꼴). 지금 거른 목록의 수 */
    private lateinit var titleCount: TextView
    private lateinit var yearPick: TextView

    /** 한 줄: 책의 기록, 그해 마지막으로 읽은 날과 완독한 날 중 늦은 날(목록 순서), 그해 완독했는가(`완독` 거르기) */
    private class Row(val record: BookRecord, val sortDay: LocalDate, val doneInYear: Boolean) {
        val book get() = record.book
        val finished get() = record.finished
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = vbox()
        root.addView(titleRow("", Palette.Area.LIBRARY) { finish() }.apply {
            // 제목 줄: `‹` · 제목 · 권수 · (빈 자리) · `2026 ▾`(고를 해가 둘 이상일 때)
            titleText = getChildAt(1) as TextView
            titleCount = text("", 21f, color = Ui.GRAY)
            addView(titleCount, lp(WRAP, WRAP).apply { marginStart = dp(14); topMargin = dp(6) })
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
        // 글자를 키우면 줄·격자 글자 칸도 그만큼 높인다(줄이면 고정 크기 표지가 잘리므로 100% 아래로는 그대로).
        listRows = PagedRows(this, rowDp(ROW_DP * Ui.textGrow))
        gridRows = PagedRows(this, cellW * 3 / 2 + dp(GRID_TEXT_DP * Ui.textGrow), dividers = false)
        gridCoverH = cellW * 3 / 2 - dp(GRID_STARS_DP)
        gridCoverW = gridCoverH * 2 / 3
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
        // 가운데 `전체 · 완독`: 그해 완독한 책만 거른다(기억한다).
        filterToggle = text("", 21f).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            setOnClickListener { prefs.yearFinishedOnly = !prefs.yearFinishedOnly; render(); rows.showPage(0) }
        }
        footer.addView(filterToggle, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.CENTER_HORIZONTAL))
        pageText = text("", 21f).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), 0, dp(Ui.MARGIN), 0)
            pagesClick { rows }
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
        pageText.text = pageLabel(rows)
    }

    private fun render() {
        val today = LocalDate.now()
        // 기록은 한 번만 읽는다(읽은 시간 계산이 사용 기록을 읽는다).
        val records = BookRecord.all(this).filter { !it.hidden }
        reviews = BookShelf.reviews(this)
        val years = years(records, today)
        // 지난해의 마지막 책을 기록에서 지워 그해가 비면 올해로 돌아온다(빈 지난해에 `올해 읽은 책이 없습니다`가 뜨지 않게).
        if (year !in years) year = today.year
        val all = yearRows(records, year)
        val finishedOnly = prefs.yearFinishedOnly
        val list = if (finishedOnly) all.filter { it.doneInYear } else all
        titleText.text = if (year == today.year) getString(R.string.year_books) else getString(R.string.year_books_of, year)
        titleCount.text = if (list.isEmpty()) "" else getString(R.string.year_books_count, list.size)
        yearPick.text = "$year ▾"
        yearPick.visibility = if (years.size > 1) View.VISIBLE else View.GONE
        yearPick.setOnClickListener {
            choose(getString(R.string.pick_year), years.map { it.toString() }, years.indexOf(year)) {
                year = years[it]
                render()
                rows.showPage(0)
            }
        }
        empty.text = getString(if (all.isEmpty()) R.string.year_books_empty else R.string.year_finished_empty)
        empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        val grid = prefs.yearGrid
        viewToggle.text = toggleText(R.string.fav_view_list, R.string.fav_view_grid, !grid)
        filterToggle.text = toggleText(R.string.filter_all, R.string.finish_book, !finishedOnly)
        // 그해 책이 하나도 없으면 보기·거르기를 감춘다(완독만 거른 결과가 비면 되돌릴 수 있게 둔다).
        viewToggle.visibility = if (all.isEmpty()) View.GONE else View.VISIBLE
        filterToggle.visibility = viewToggle.visibility
        listRows.visibility = if (grid) View.GONE else View.VISIBLE
        gridRows.visibility = if (grid) View.VISIBLE else View.GONE
        if (grid) gridRows.setRows(list.chunked(COLUMNS).map { line -> { gridRow(line) } })
        else listRows.setRows(list.map { r -> { bookRow(r) } })
        showPage()
    }

    /** 두 갈래 고르기 글자 `목록 · 격자`: 지금 고른 쪽은 굵게, 다른 쪽은 흐리게 */
    private fun toggleText(first: Int, second: Int, firstOn: Boolean) = SpannableStringBuilder().apply {
        listOf(first to firstOn, second to !firstOn).forEachIndexed { i, (res, on) ->
            if (i > 0) append("  ·  ", ForegroundColorSpan(Ui.LIGHT_GRAY), 0)
            append(getString(res), if (on) StyleSpan(Typeface.BOLD) else ForegroundColorSpan(Ui.LIGHT_GRAY), 0)
        }
    }

    /**
     * 표지 [COLUMNS]권 한 줄. 표지 아래에 책 이름 한 줄(완독한 책은 표지의 띠로 안다), 별점이 있으면 그 아래 작은 별(없으면 빈 자리).
     * 표지를 가리지 않게 별 줄만큼 표지를 줄였다(칸 높이는 그대로).
     */
    private fun gridRow(line: List<Row>): View = hbox().apply {
        gravity = Gravity.TOP
        setPadding(dp(Ui.MARGIN), dp(12), dp(Ui.MARGIN), 0)
        line.forEachIndexed { i, r ->
            addView(vbox().apply {
                addView(coverImage(BookShelf.cover(context, r.book, gridCoverW)), lp(gridCoverW, gridCoverH).apply { gravity = Gravity.CENTER_HORIZONTAL })
                addView(text(r.book.title, 13f, Ui.medium).apply { gravity = Gravity.CENTER_HORIZONTAL }, lp(MATCH, WRAP).apply { topMargin = dp(6) })
                stars(r, 12)?.let { addView(it, lp(WRAP, WRAP).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(5) }) }
                setOnClickListener { showMenu(r) }
            }, lp(cellW, MATCH).apply { if (i > 0) marginStart = dp(GAP_DP) })
        }
    }

    /**
     * 한 줄: 표지·제목·저자와 별점. 완독한 적이 있으면 오른쪽 끝에 줄보다 큰 완독 도장(위아래·오른쪽이 조금 잘린다).
     * 표지는 줄 높이를 거의 다 채운다(위아래 [COVER_GAP_DP] 만 남기고, 폭은 2:3).
     */
    private fun bookRow(r: Row): View = (if (r.finished) StampedRow(this, STAMP_DP, STAMP_END_DP) else hbox()).apply {
        setPadding(dp(Ui.MARGIN), 0, dp(Ui.MARGIN), 0)
        addView(coverImage(BookShelf.cover(context, r.book, coverW)), lp(coverW + dp(2), coverW * 3 / 2 + dp(2)))
        addView(vbox().apply {
            addView(text(r.book.title, 22f, Ui.bold, lines = 2))
            // 목록은 짧게: 저자(알면)와 별점(있으면). 완독은 표지의 띠와 도장이, 읽는 중인지·완독 횟수·기간·읽은 시간은 누르면 나오는 상세가 말한다.
            val author = r.book.author.takeIf { it.isNotBlank() }
            val stars = stars(r, 13)
            if (author != null || stars != null) addView(hbox().apply {
                author?.let {
                    // 저자가 길어도 별이 밀려나지 않게 별 자리를 뺀 만큼만 쓴다.
                    addView(text(it, 16f, color = Ui.GRAY).apply {
                        if (stars != null) maxWidth = r.textWidth() - stars.apply { measure(0, 0) }.measuredWidth - dp(DOT_W_DP)
                    })
                }
                stars?.let {
                    if (author != null) addView(text("·", 16f, color = Ui.GRAY), lp(WRAP, WRAP).apply { marginStart = dp(DOT_GAP_DP); marginEnd = dp(DOT_GAP_DP) })
                    addView(it)
                }
            }, lp(WRAP, WRAP).apply { topMargin = dp(6) })
        }, lp(0, WRAP, 1f).apply {
            marginStart = dp(16)
            // 글자가 도장에 덮이지 않게 도장 왼쪽 끝까지 비운다.
            if (r.finished) marginEnd = stampSpace
        })
        setOnClickListener { showMenu(r) }
    }

    private val stampSpace get() = dp(STAMP_END_DP + STAMP_DP / 2 - Ui.MARGIN)

    /** 목록 줄 표지 그림 폭(테두리 빼고): 줄 높이에서 위아래 [COVER_GAP_DP] 와 테두리를 뺀 높이의 2/3 */
    private val coverW get() = (rowDp(ROW_DP * Ui.textGrow) - dp(2 * COVER_GAP_DP + 2)) * 2 / 3

    /** 목록 줄 글자 칸 폭(화면 폭 - 여백 - 표지 - 도장 자리) */
    private fun Row.textWidth() = resources.displayMetrics.widthPixels - 2 * dp(Ui.MARGIN) - coverW - dp(2) - dp(16) - (if (finished) stampSpace else 0)

    /** 별점이 있으면 작은 별 다섯(회색, 반 개), 없으면 null */
    private fun stars(r: Row, sizeDp: Int): View? {
        val halves = reviews[r.book.id]?.halves ?: 0
        if (halves == 0) return null
        return StarBar(this, sizeDp, 2, filled = Ui.GRAY).apply { this.halves = halves }
    }

    /** 홈과 같은 책 상세(기록 전체). 지난해 책을 완독하면 그해 마지막으로 읽은 날에 완독한 것으로(올해는 오늘). */
    private fun showMenu(r: Row) {
        val finishOn = if (year == LocalDate.now().year) LocalDate.now() else r.sortDay.takeIf { it != LocalDate.MIN } ?: LocalDate.of(year, 12, 31)
        showBookDetail(r.record, full = true, finishOn = finishOn) { render() }
    }

    companion object {
        /** 목록 줄 표지 위아래 여백(dp). 표지는 나머지 높이를 채운다. */
        private const val COVER_GAP_DP = 6
        /** 읽고 있는 책 목록과 같은 줄 높이 */
        private const val ROW_DP = 98
        /** 목록 줄의 완독 도장: 지름(줄보다 커서 위아래가 잘린다), 가운데가 줄 오른쪽 끝에서 안쪽으로 */
        private const val STAMP_DP = 120
        private const val STAMP_END_DP = 62
        /** 격자: 한 줄 권수, 표지 사이, 표지 밖 높이(위 여백·아래 글자) */
        private const val COLUMNS = 4
        private const val GAP_DP = 14
        private const val GRID_TEXT_DP = 34
        /** 격자 별 줄(별 12dp + 위 5dp): 표지를 이만큼 줄인다 */
        private const val GRID_STARS_DP = 17
        /** 목록 저자와 별 사이 `·` 양옆 틈과 `·` 자리(점 폭 넉넉히) */
        private const val DOT_GAP_DP = 8
        private const val DOT_W_DP = 2 * DOT_GAP_DP + 8

        fun intent(context: Context) = Intent(context, YearBooksActivity::class.java)

        /** 고를 수 있는 해: 1분 이상 읽었거나 완독한 책이 있는 해와 올해(최근 순) */
        private fun years(records: List<BookRecord>, today: LocalDate): List<Int> {
            return (records.flatMap { r -> r.readYears + r.finishes.map { it.year } } + today.year).distinct().sortedDescending()
        }

        /**
         * [year] 에 1분 이상 읽었거나 그해 완독한 책. 그해 마지막으로 읽은(완독한) 날 순, 같으면 오래 읽은 순.
         * 기간·읽은 시간은 남은 기록 전체로 센다(해를 넘긴 책도 맞게).
         */
        private fun yearRows(records: List<BookRecord>, year: Int): List<Row> =
            records
                .filter { year in it.readYears || it.finishes.any { d -> d.year == year } }
                .map { r ->
                    val lastRead = r.readDays.lastOrNull { it.year == year } ?: LocalDate.MIN
                    val lastDone = r.finishes.lastOrNull { it.year == year } ?: LocalDate.MIN
                    Row(r, maxOf(lastRead, lastDone), doneInYear = lastDone != LocalDate.MIN)
                }
                .sortedWith(compareByDescending<Row> { it.sortDay }.thenByDescending { it.record.ms })
    }
}
