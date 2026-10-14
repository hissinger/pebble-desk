package com.woody.pebbledesk

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.woody.pebbledesk.BookRecord.State
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 책 상세: 홈에서 책을 길게 누를 때와 올해 읽은 책에서 책을 누를 때 같은 화면. 머리([bookSheet])·기록 칸([recordView]) 아래
 * 메뉴는 책 상태에 따라
 * - 읽고 있는 책(다시 읽는 책 포함): `완독` / `책 정보 고치기 ›` / `책 빼기`
 * - 나간 책·목록에 없던 책: `완독`(아직 완독하지 않았으면) / `다시 읽기`(읽고 있는 책에 자리가 있으면) / `책 정보 고치기 ›` / `기록에서 지우기`
 * `완독`·`책 빼기`는 확인 대신 별점·감상 메뉴([reviewSheet], 비워 두고 눌러도 된다), `기록에서 지우기`는 한 번 더 묻는다.
 * [full] 이면 회차 기록(완독 2회 이상이면 마지막 회차만, 누르면 완독 기록)과 별점·감상 칸(올해 읽은 책), 아니면 지금 읽는 것만(홈, 메뉴가 길어서). [finishOn] 은 나간 책을 완독할 때의 날(지난해를 보고 있으면 그해 마지막으로 읽은 날). 무엇이든 바뀌면 [onChange].
 */
fun Context.showBookDetail(r: BookRecord, full: Boolean, finishOn: LocalDate = LocalDate.now(), onChange: () -> Unit) {
    val book = r.book
    val sub = bookSubtitle(book, r.state == State.READING)
    val sheet = bookSheet(r, sub, full, onChange) { showBookDetail(r, full, finishOn, onChange) }
    if (r.state == State.READING) {
        sheet.item(getString(R.string.finish_book)) {
            reviewSheet(book, sub, getString(R.string.finish_book)) {
                BookShelf.finish(this, book.id); BookShelf.setReview(this, book.id, it); onChange()
            }
        }
        sheet.item(getString(R.string.edit_book)) { showEditMenu(r, onChange) }
        sheet.item(getString(R.string.remove_book)) { removeBook(r, sub, onChange) }
    } else {
        val pastId = book.id.takeIf { r.state == State.PAST }
        if (!r.finished) sheet.item(getString(R.string.finish_book)) {
            reviewSheet(book, sub, getString(R.string.finish_book)) {
                BookShelf.setReview(this, BookShelf.finishPast(this, pastId, book.title, book.app, finishOn), it); onChange()
            }
        }
        // 다 찼으면 다시 읽기를 두지 않는다(읽고 있는 책에서 먼저 한 권을 빼야 한다).
        if (!BookShelf.isFull(this)) sheet.item(getString(R.string.read_again)) {
            BookShelf.restore(this, pastId, book.title, book.app); onChange()
        }
        sheet.item(getString(R.string.edit_book)) { showEditMenu(r, onChange) }
        sheet.item(getString(R.string.forget_book)) {
            confirm(getString(R.string.forget_confirm), book.title, getString(R.string.forget_book)) { BookShelf.forget(this, pastId, book.title); onChange() }
        }
    }
    sheet.show()
}

/**
 * `책 빼기`(홈 책 상세·읽고 있는 책 목록의 `빼기`): 올해 읽은 책에 남는 책([BookRecord.inYearBooks])이면 확인 대신 별점·감상 메뉴
 * (맨 아래 `취소` / `책 빼기`), 아니면(거의 읽지 않은 책. 남긴 별점을 볼 곳이 없다) 확인만 `읽고 있는 책에서 뺄까요?`.
 */
fun Context.removeBook(r: BookRecord, sub: CharSequence?, onChange: () -> Unit) {
    val book = r.book
    if (!r.inYearBooks) {
        confirm(getString(R.string.remove_confirm), book.title, getString(R.string.remove_book)) { BookShelf.remove(this, book.id); onChange() }
        return
    }
    reviewSheet(book, sub, getString(R.string.remove_book)) {
        BookShelf.remove(this, book.id); BookShelf.setReview(this, book.id, it); onChange()
    }
}

/**
 * `책 정보 고치기`: 머리에 제목·저자, 읽고 있는 책이면 `읽고 있는 앱 바꾸기`, 그리고 `제목 고치기` / `저자 입력`(비어 있으면)·`저자 고치기` /
 * `표지 다시 찾기`. 목록에 없던 책은 고치는 순간 나간 책으로 적어 둔다(고친 것이 남게).
 */
private fun Context.showEditMenu(r: BookRecord, onChange: () -> Unit) {
    val book = r.book
    fun target() = if (r.state == State.TITLE_ONLY) BookShelf.keep(this, book.title, book.app) else book
    // 이북 앱이 저자를 보여 주지 않아(읽는 화면 메뉴로 들어온 책 등) 비어 있으면 `저자 입력`, 있으면 `저자 고치기`.
    val authorLabel = if (book.author.isBlank()) R.string.add_author else R.string.edit_author
    val sheet = Sheet(this).header(book.title, book.author.takeIf { it.isNotBlank() })
    if (r.state == State.READING) sheet.item(getString(R.string.change_reader)) {
        startActivity(AppListActivity.intent(this, AppListActivity.Mode.PICK_READER, book.id))
    }
    sheet.item(getString(R.string.edit_title)) {
        Sheet(this).header(getString(R.string.edit_title)).input(book.title) { BookShelf.setTitle(this, target().id, it); onChange() }.show()
    }
    sheet.item(getString(authorLabel)) {
        Sheet(this).header(getString(authorLabel)).input(book.author) { BookShelf.setAuthor(this, target().id, it); onChange() }.show()
    }
    sheet.item(getString(R.string.find_cover)) { startActivity(BookSearchActivity.intent(this, replace = target())) }
    sheet.show()
}

/** 책 머리의 제목 아래: 저자(알면), 회색 줄 `교보도서관 · 47% 읽음 · 반납 4일 남음`(아는 것만, 진행률·반납일은 [reading] 일 때만) */
fun Context.bookSubtitle(book: ShelfBook, reading: Boolean): String? {
    val app = book.app?.let { key -> AppStore.load(this).find { it.key == key }?.label }
    val status = (listOfNotNull(app) + if (reading) bookStatus(book) else emptyList()).joinToString("  ·  ")
    return listOfNotNull(book.author.takeIf { it.isNotBlank() }, status.ifEmpty { null }).joinToString("\n").ifEmpty { null }
}

/**
 * 책 상세의 머리: 표지(완독 띠), 제목, [sub], 완독한 적이 있으면 오른쪽 위에 완독 도장, 그 아래 기록 칸([recordView], 기록이 없으면 두지 않는다).
 * 컬러면 머리와 칸들을 책 상세 색면([Sheet.paper])에 둬 아래 메뉴와 나눈다.
 * [full](올해 읽은 책)이면 그 아래 별점·감상 칸([reviewBlock], 누르면 고친다. 목록에 없던 책은 남기는 순간 나간 책으로 적어 둔다).
 * 완독이 [HISTORY_FROM]회 이상이면 기록 칸을 누르면 완독 기록([showHistory]), 닫으면 상세를 다시 띄운다([reopen]).
 */
private fun Context.bookSheet(r: BookRecord, sub: String?, full: Boolean, onChange: () -> Unit, reopen: () -> Unit): Sheet {
    val book = r.book
    val sheet = headSheet(r, sub)
    val history = if (r.finishes.size >= HISTORY_FROM) { { showHistory(r, sub, reopen) } } else null
    recordView(r, full)?.let { sheet.note(it, history) }
    if (full) sheet.note(reviewBlock(BookShelf.reviews(this)[book.id])) {
        reviewSheet(book, sub, getString(R.string.save)) { review ->
            if (r.state != State.TITLE_ONLY || !review.isEmpty) {
                val id = if (r.state == State.TITLE_ONLY) BookShelf.keep(this, book.title, book.app).id else book.id
                BookShelf.setReview(this, id, review)
                onChange()
            }
        }
    }
    return sheet
}

/** 책 상세의 머리(표지·제목·[sub]·완독 도장)만 든 메뉴. 상세와 완독 기록이 같이 쓴다. */
private fun Context.headSheet(r: BookRecord, sub: String?): Sheet {
    val sheet = Sheet(this).header(r.book.title, sub, image = BookShelf.cover(this, r.book, dp(SHEET_COVER_DP))).paper()
    if (r.finished) sheet.stamp()
    return sheet
}

/** 완독이 이만큼 이상이면 기록 칸에 마지막 회차만 두고 누르면 완독 기록([showHistory]) */
private const val HISTORY_FROM = 2

/**
 * 기록 칸. [full] 이면(올해 읽은 책) 회차 기록([BookRecord.segments], 완독한 것만): 시작한 해마다 작은 굵은 머리글 `2026` 을 두고,
 * 그 아래 줄마다 `1회`(회색, 완독이 둘 이상일 때만) · 기간(검정, 연도 없이 `8월 20일 – 9월 6일`, 해를 넘기면 끝 날에만
 * `2026년 1월 5일`, 읽는 중이면 `10월 8일부터`) · 읽은 시간(회색, 오른쪽 정렬). `완독` 글자는 쓰지 않는다(표지의 띠가 말한다).
 * 완독이 [HISTORY_FROM]회 이상이면 마지막 회차 한 줄과 회색 `완독 5회` · 오른쪽 `전체 기록 ›`(메뉴가 화면을 넘지 않게).
 * 아니면(홈) 지금 읽는 것 한 줄([BookRecord.current], 회차 칸 없이)과, 완독한 적이 있으면 회색 `완독 4회 · 마지막 10월 8일`
 * (완독이 [HISTORY_FROM]회 이상이면 오른쪽에 `전체 기록 ›`).
 * 보일 것이 없으면 null.
 */
private fun Context.recordView(r: BookRecord, full: Boolean): View? {
    val folded = r.finishes.size >= HISTORY_FROM
    val segs = when {
        !full -> listOfNotNull(r.current())
        folded -> listOf(r.segments().last())
        else -> r.segments()
    }
    if (segs.all { it.from == null && it.to == null && it.ms < ReadingLog.MIN_DAY_MS }) return null
    return vbox().apply {
        val numbered = segs.any { it.nth != null }
        // 회차 칸 폭: 가장 긴 `N회` + 여백
        val nthPaint = text("", 18f).paint
        val nthW = if (!numbered) 0 else segs.maxOf { nthPaint.measureText(getString(R.string.finish_nth, it.nth ?: 0)) }.toInt() + dp(14)
        var year: Int? = null
        segs.forEach { s ->
            val y = (s.from ?: s.to)?.year
            if (full && y != null && y != year) {
                addView(text(y.toString(), 14f, Ui.bold, Ui.GRAY), lp(WRAP, WRAP).apply { if (year != null) topMargin = dp(10) })
                year = y
            }
            addView(hbox().apply {
                if (numbered) addView(text(s.nth?.let { getString(R.string.finish_nth, it) }.orEmpty(), 18f, color = Ui.GRAY), lp(nthW, WRAP))
                addView(text(period(s, withYear = false), 18f), lp(0, WRAP, 1f))
                val t = duration(s.ms).takeIf { s.ms >= ReadingLog.MIN_DAY_MS }.orEmpty()
                addView(text(t, 18f, color = Ui.GRAY).apply { gravity = Gravity.END }, lp(WRAP, WRAP).apply { marginStart = dp(12) })
            }, lp(MATCH, WRAP).apply { topMargin = dp(6) })
        }
        if (folded || (!full && r.finished)) addView(hbox().apply {
            val summary = if (full) getString(R.string.finish_count, r.finishes.size)
            else getString(R.string.finish_summary, r.finishes.size, r.finishes.last().format(dateFormat(R.string.date_short_fmt)))
            addView(text(summary, 16f, color = Ui.GRAY), lp(0, WRAP, 1f))
            if (folded) addView(text(getString(R.string.all_history), 16f, color = Ui.GRAY), lp(WRAP, WRAP).apply { marginStart = dp(12) })
        }, lp(MATCH, WRAP).apply { topMargin = dp(if (full) 10 else 6) })
    }
}

private fun Context.dateFormat(pattern: Int) = DateTimeFormatter.ofPattern(getString(pattern), resources.configuration.locales[0])

/**
 * 한 회차의 기간: `8월 20일 – 9월 6일`(해를 넘기면 끝 날에 해), 읽는 중이면 `10월 8일부터`, 하루면 그날.
 * [withYear] 면 시작 날(하루면 그날)에도 해를 붙인다(`2026년 9월 20일 – 10월 8일`).
 */
private fun Context.period(s: BookRecord.Segment, withYear: Boolean): String {
    val fmt = dateFormat(R.string.date_short_fmt)
    val yearFmt = dateFormat(R.string.date_year_fmt)
    val startFmt = if (withYear) yearFmt else fmt
    val from = s.from
    val to = s.to
    return when {
        to == null -> from?.let { getString(R.string.since_date, it.format(startFmt)) }.orEmpty()
        from == null || from >= to -> to.format(startFmt)
        else -> "${from.format(startFmt)} – ${to.format(if (from.year != to.year) yearFmt else fmt)}"
    }
}

/**
 * 완독 기록(완독 [HISTORY_FROM]회 이상, 상세의 기록 칸을 누르면): 머리는 상세와 같고([headSheet]), 그 아래 요약 한 줄
 * 굵은 `완독 5회` + 회색 `· 모두 21시간 7분 · 2023년부터`(시간은 회차 시간의 합, 시작한 해는 여러 해에 걸쳐 완독했을 때만),
 * 그 아래 최근 회차부터 세로 타임라인([Timeline]: 줄마다 해를 붙인 기간 / 회색 `5회 · 3시간 12분`).
 * 화면에 다 들어가지 않으면 쪽으로 나눠 요약 줄 오른쪽에 `‹ 1 / 2 ›`(누르거나 페이지 버튼으로 넘김). 닫으면(닫기·뒤로 가기·바깥) 상세로 돌아간다([back]).
 */
private fun Context.showHistory(r: BookRecord, sub: String?, back: () -> Unit) {
    val segs = r.segments().asReversed()
    val sheet = headSheet(r, sub)
    val total = segs.sumOf { it.ms }
    val firstYear = segs.last().let { (it.from ?: it.to)?.year }
    val lastYear = segs.first().to?.year
    val rest = listOfNotNull(
        getString(R.string.total_time, duration(total)).takeIf { total >= ReadingLog.MIN_DAY_MS },
        firstYear?.takeIf { lastYear != null && it < lastYear }?.let { getString(R.string.since_year, it) },
    ).joinToString("") { "  ·  $it" }
    val pages = text("", 18f, color = Ui.GRAY)
    val summary = hbox().apply {
        addView(text(getString(R.string.finish_count, segs.size), 18f, Ui.bold))
        addView(text(rest, 18f, color = Ui.GRAY), lp(0, WRAP, 1f))
        addView(pages, lp(WRAP, WRAP).apply { marginStart = dp(12) })
    }
    val rows = segs.map { s ->
        vbox().apply {
            addView(text(period(s, withYear = true), 18f))
            val detail = listOfNotNull(getString(R.string.finish_nth, s.nth ?: 0), duration(s.ms).takeIf { s.ms >= ReadingLog.MIN_DAY_MS })
            addView(text(detail.joinToString("  ·  "), 16f, color = Ui.GRAY), lp(WRAP, WRAP).apply { topMargin = dp(6) })
        }
    }
    // 한 쪽에 들어가는 줄 수: 화면 높이에서 머리·요약 줄·칸 여백·닫기 줄·선과 여유를 빼고 남는 만큼.
    val width = resources.displayMetrics.widthPixels - dp(Ui.MARGIN) * 2
    val screen = (this as? Activity)?.findViewById<View>(android.R.id.content)?.height?.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
    val rowH = rows.first().heightAt(width - dp(Timeline.GUTTER_DP))
    val gap = dp(Timeline.GAP_DP)
    val avail = screen - sheet.headHeight() - summary.heightAt(width) - dp(TIMELINE_TOP_DP) - dp(NOTE_PAD_TOP_DP + NOTE_PAD_BOTTOM_DP) - dp(CLOSE_ROW_DP) - dp(24)
    val timeline = Timeline(this, rows, perPage = ((avail + gap) / (rowH + gap)).coerceAtLeast(1), rowH)
    timeline.onPageChanged = { pages.text = pageLabel(timeline) }
    pages.text = pageLabel(timeline)
    pages.pagesClick(timeline)
    sheet.note(vbox().apply {
        addView(summary)
        addView(timeline, lp(MATCH, WRAP).apply { topMargin = dp(TIMELINE_TOP_DP) })
    })
    sheet.paged(timeline).onDismiss { if ((this as? Activity)?.isFinishing != true) back() }.show()
}

private const val TIMELINE_TOP_DP = 16

/**
 * 완독 기록의 세로 타임라인: 줄마다 왼쪽에 점(첫 줄의 가운데 높이, 가장 최근 완독은 채운 점), 점 사이를 회색 세로선으로 잇는다.
 * 쪽마다 [perPage] 줄. 앞뒤 쪽이 있으면 선을 위·아래 끝까지 이어 그린다. 여러 쪽이면 덜 찬 쪽도 같은 높이(메뉴 높이가 그대로이게).
 */
private class Timeline(context: Context, private val rows: List<View>, private val perPage: Int, rowH: Int) : LinearLayout(context), Paged {
    companion object {
        const val GUTTER_DP = 28
        const val GAP_DP = 16
    }

    override var page = 0
        private set
    override val pageCount = (rows.size + perPage - 1) / perPage

    var onPageChanged: (() -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = context.dpf(1.5f) }

    init {
        orientation = VERTICAL
        setWillNotDraw(false)
        setPadding(context.dp(GUTTER_DP), 0, 0, 0)
        if (pageCount > 1) minimumHeight = perPage * rowH + (perPage - 1) * context.dp(GAP_DP)
        render()
    }

    override fun next() = showPage(page + 1)
    override fun prev() = showPage(page - 1)

    private fun showPage(p: Int) {
        val target = p.coerceIn(0, pageCount - 1)
        if (target == page) return
        page = target
        render()
        onPageChanged?.invoke()
    }

    private fun render() {
        removeAllViews()
        rows.drop(page * perPage).take(perPage).forEachIndexed { i, v ->
            addView(v, lp(MATCH, WRAP).apply { if (i > 0) topMargin = context.dp(GAP_DP) })
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (childCount == 0) return
        val x = context.dpf(7)
        val r = context.dpf(6)
        // 점 높이: 줄의 첫 글자 줄 가운데
        val ys = (0 until childCount).map { i ->
            val row = getChildAt(i) as ViewGroup
            val first = row.getChildAt(0)
            row.top + first.top + first.height / 2f
        }
        paint.color = Ui.LIGHT_GRAY
        if (page > 0) canvas.drawLine(x, 0f, x, ys.first() - r, paint)
        ys.zipWithNext { a, b -> canvas.drawLine(x, a + r, x, b - r, paint) }
        if (page < pageCount - 1) canvas.drawLine(x, ys.last() + r, x, height.toFloat(), paint)
        paint.color = Ui.BLACK
        ys.forEachIndexed { i, y ->
            paint.style = if (page == 0 && i == 0) Paint.Style.FILL else Paint.Style.STROKE
            canvas.drawCircle(x, y, if (paint.style == Paint.Style.STROKE) r - paint.strokeWidth / 2 else r, paint)
        }
        paint.style = Paint.Style.STROKE
    }
}
