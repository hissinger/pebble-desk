package com.woody.pebbledesk

import android.content.Context
import android.view.Gravity
import android.view.View
import com.woody.pebbledesk.BookRecord.State
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 책 상세: 홈에서 책을 길게 누를 때와 올해 읽은 책에서 책을 누를 때 같은 화면. 머리([bookSheet])·기록 칸([recordView]) 아래
 * 메뉴는 책 상태에 따라
 * - 읽고 있는 책(다시 읽는 책 포함): `완독` / `책 정보 고치기 ›` / `책 빼기`
 * - 나간 책·목록에 없던 책: `완독`(아직 완독하지 않았으면) / `다시 읽기`(읽고 있는 책에 자리가 있으면) / `책 정보 고치기 ›` / `기록에서 지우기`
 * `완독`·`책 빼기`·`기록에서 지우기`는 한 번 더 묻는다. [full] 이면 기록 전체(올해 읽은 책), 아니면 지금 읽는 것만(홈, 메뉴가 길어서).
 * [finishOn] 은 나간 책을 완독할 때의 날(지난해를 보고 있으면 그해 마지막으로 읽은 날). 무엇이든 바뀌면 [onChange].
 */
fun Context.showBookDetail(r: BookRecord, full: Boolean, finishOn: LocalDate = LocalDate.now(), onChange: () -> Unit) {
    val book = r.book
    val sheet = bookSheet(r, full)
    if (r.state == State.READING) {
        sheet.item(getString(R.string.finish_book)) {
            confirm(getString(R.string.finish_confirm), book.title, getString(R.string.finish_book)) { BookShelf.finish(this, book.id); onChange() }
        }
        sheet.item(getString(R.string.edit_book)) { showEditMenu(r, onChange) }
        sheet.item(getString(R.string.remove_book)) {
            confirm(getString(R.string.remove_confirm), book.title, getString(R.string.remove_book)) { BookShelf.remove(this, book.id); onChange() }
        }
    } else {
        val pastId = book.id.takeIf { r.state == State.PAST }
        if (!r.finished) sheet.item(getString(R.string.finish_book)) {
            confirm(getString(R.string.finish_confirm), book.title, getString(R.string.finish_book)) {
                BookShelf.finishPast(this, pastId, book.title, book.app, finishOn); onChange()
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

/**
 * 책 상세의 머리: 표지(완독 띠), 제목, 저자(알면), 회색 줄 `교보도서관 · 47% 읽음 · 반납 4일 남음`(아는 것만, 진행률·반납일은
 * 읽고 있는 책만), 완독한 적이 있으면 오른쪽 위에 완독 도장, 그 아래 기록 칸([recordView], 기록이 없으면 두지 않는다).
 */
private fun Context.bookSheet(r: BookRecord, full: Boolean): Sheet {
    val book = r.book
    val app = book.app?.let { key -> AppStore.load(this).find { it.key == key }?.label }
    val status = (listOfNotNull(app) + if (r.state == State.READING) bookStatus(book) else emptyList()).joinToString("  ·  ")
    val sub = listOfNotNull(book.author.takeIf { it.isNotBlank() }, status.ifEmpty { null }).joinToString("\n").ifEmpty { null }
    val sheet = Sheet(this).header(book.title, sub, image = BookShelf.cover(this, book, dp(SHEET_COVER_DP)))
    recordView(r, full)?.let { sheet.note(it) }
    if (r.finished) sheet.stamp()
    return sheet
}

/**
 * 기록 칸. [full] 이면(올해 읽은 책) 회차 기록([BookRecord.segments], 완독한 것만): 시작한 해마다 작은 굵은 머리글 `2026` 을 두고,
 * 그 아래 줄마다 `1회`(회색, 완독이 둘 이상일 때만) · 기간(검정, 연도 없이 `8월 20일 – 9월 6일`, 해를 넘기면 끝 날에만
 * `2026년 1월 5일`, 읽는 중이면 `10월 8일부터`) · 읽은 시간(회색, 오른쪽 정렬). `완독` 글자는 쓰지 않는다(표지의 띠가 말한다).
 * 아니면(홈) 지금 읽는 것 한 줄([BookRecord.current], 회차 칸 없이)과, 완독한 적이 있으면 회색 `완독 4회 · 마지막 10월 8일`.
 * 보일 것이 없으면 null.
 */
private fun Context.recordView(r: BookRecord, full: Boolean): View? {
    val segs = if (full) r.segments() else listOfNotNull(r.current())
    if (segs.all { it.from == null && it.to == null && it.ms < ReadingLog.MIN_DAY_MS }) return null
    val locale = resources.configuration.locales[0]
    val fmt = DateTimeFormatter.ofPattern(getString(R.string.date_short_fmt), locale)
    val yearFmt = DateTimeFormatter.ofPattern(getString(R.string.date_year_fmt), locale)
    fun period(s: BookRecord.Segment): String {
        val from = s.from
        val to = s.to
        return when {
            to == null -> from?.let { getString(R.string.since_date, it.format(fmt)) }.orEmpty()
            from == null || from >= to -> to.format(fmt)
            else -> "${from.format(fmt)} – ${to.format(if (from.year != to.year) yearFmt else fmt)}"
        }
    }
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
                addView(text(period(s), 18f), lp(0, WRAP, 1f))
                val t = duration(s.ms).takeIf { s.ms >= ReadingLog.MIN_DAY_MS }.orEmpty()
                addView(text(t, 18f, color = Ui.GRAY).apply { gravity = Gravity.END }, lp(WRAP, WRAP).apply { marginStart = dp(12) })
            }, lp(MATCH, WRAP).apply { topMargin = dp(6) })
        }
        if (!full && r.finished) {
            val summary = getString(R.string.finish_summary, r.finishes.size, r.finishes.last().format(fmt))
            addView(text(summary, 16f, color = Ui.GRAY), lp(WRAP, WRAP).apply { topMargin = dp(6) })
        }
    }
}
