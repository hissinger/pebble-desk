package com.woody.pebbledesk

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 책 한 권의 읽은 기록: 책(목록에 없던 책은 제목만 든 임시 책), 상태, 완독한 날 모두(오래된 순), 날마다 읽은 시간(남은 기록 전체).
 * 홈과 올해 읽은 책에서 같은 책 상세([showBookDetail])로 보인다.
 */
class BookRecord(
    val book: ShelfBook, val state: State, val hidden: Boolean, val finishes: List<LocalDate>, val dayMs: Map<LocalDate, Long>,
) {
    enum class State {
        /** 읽고 있는 책 목록에 있다(완독했던 책이면 다시 읽는 중) */
        READING,
        /** 나간 책(완독했거나 뺀 책) */
        PAST,
        /** 목록에 넣지 못한 책(읽은 시간에만 있는 제목) */
        TITLE_ONLY,
    }

    /** 한 번이라도 완독했다(표지에 띠) */
    val finished get() = finishes.isNotEmpty()
    /** 남은 기록 전체에서 읽은 시간 */
    val ms = dayMs.values.sum()
    /** 1분 이상 읽은 날(오래된 순) */
    val readDays: List<LocalDate> = dayMs.filterValues { it >= ReadingLog.MIN_DAY_MS }.keys.sorted()

    /** [year] 에 읽은 시간 */
    fun yearMs(year: Int) = dayMs.entries.sumOf { (d, ms) -> if (d.year == year) ms else 0L }

    /** [after] 다음 날부터 [upTo] 까지(null 이면 끝없이) 읽은 시간: 회차마다 읽은 시간 */
    private fun spent(after: LocalDate?, upTo: LocalDate?) =
        dayMs.entries.sumOf { (d, ms) -> if ((after == null || d > after) && (upTo == null || d <= upTo)) ms else 0L }

    /** 기록 한 줄: 회차(여러 회차일 때만), 시작 날(모르면 null), 끝 날(읽는 중이면 null), 그 동안 읽은 시간 */
    class Segment(val nth: Int?, val from: LocalDate?, val to: LocalDate?, val ms: Long)

    /**
     * 회차 기록(완독한 것만). 완독한 책은 완독마다 한 줄(시작 = 앞 완독 다음에 처음 읽은 날, 시간 = 앞 완독 다음 날부터 그 완독 날까지),
     * 완독이 둘 이상일 때만 `1회` 칸. 다시 읽는 중이거나 다시 읽다 그만둔 회차는 넣지 않는다(지금 읽는 것은 [current]).
     * 완독하지 않은 책은 한 줄: 읽는 중이면 처음 읽은 날부터, 뺀 책은 처음 – 마지막으로 읽은 날.
     */
    fun segments(): List<Segment> {
        if (finished) {
            val numbered = finishes.size >= 2
            var prev: LocalDate? = null
            return finishes.mapIndexed { i, f ->
                val from = readDays.firstOrNull { d -> (prev == null || d > prev!!) && d <= f }
                Segment(if (numbered) i + 1 else null, from, f, spent(prev, f)).also { prev = f }
            }
        }
        return if (state == State.READING) listOf(Segment(null, readDays.firstOrNull(), null, ms))
        else listOf(Segment(null, readDays.firstOrNull(), readDays.lastOrNull(), ms))
    }

    /**
     * 지금 읽는 것(읽고 있는 책만, 회차 칸 없이): 처음 읽는 책은 처음 읽은 날부터 전체, 다시 읽는 책은 마지막 완독 뒤
     * 처음 읽은 날(없으면 다시 읽기를 누른 날)부터 그 뒤 읽은 시간.
     */
    fun current(): Segment? {
        if (state != State.READING) return null
        if (!finished) return Segment(null, readDays.firstOrNull(), null, ms)
        val last = finishes.last()
        val from = readDays.firstOrNull { it > last } ?: Instant.ofEpochMilli(book.updatedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        return Segment(null, from, null, spent(last, null))
    }

    companion object {
        /** 목록에 없던 책(읽은 시간에만 있는 제목)의 임시 id 머리 */
        private const val TITLE_ONLY = "t:"

        /**
         * 1분이라도 읽었거나 완독한 모든 책(지운 책 포함, [hidden]). 읽은 시간의 제목을 읽고 있는 책 → 나간 책 순으로 맞추고,
         * 어디에도 없으면 제목만 든 기록. 완독한 책은 읽은 시간이 없어도(기록을 켜기 전에 읽은 책) 들어간다.
         */
        fun all(context: Context): List<BookRecord> {
            val shelf = BookShelf.list(context)
            val finishes = BookShelf.finishes(context)
            val apps by lazy { AppStore.load(context) }
            class Acc(val book: ShelfBook, val state: State, val hidden: Boolean) {
                val dayMs = sortedMapOf<LocalDate, Long>()
            }
            val acc = linkedMapOf<String, Acc>()
            fun accOf(title: String, pkg: String): Acc {
                val norm = normTitle(title)
                shelf.firstOrNull { sameTitle(it.title, title) || (it.seenTitle.isNotEmpty() && normTitle(it.seenTitle) == norm) }?.let { b ->
                    return acc.getOrPut(b.id) { Acc(b, State.READING, false) }
                }
                BookShelf.findPast(context, title)?.let { p ->
                    return acc.getOrPut(p.book.id) { Acc(p.book, State.PAST, p.hidden) }
                }
                val app = apps.firstOrNull { it.pkg == pkg }?.key
                return acc.getOrPut(TITLE_ONLY + norm) { Acc(ShelfBook(TITLE_ONLY + norm, title, "", app, 0), State.TITLE_ONLY, false) }
            }
            ReadingLog.days(context, ReadingLog.readerPackages(context))?.forEach { (day, keys) ->
                keys.forEach { (key, ms) ->
                    val title = readingTitle(key) ?: return@forEach
                    val a = accOf(title, readingPkg(key))
                    a.dayMs[day] = (a.dayMs[day] ?: 0) + ms
                }
            }
            // 다시 읽는 중인 책은 읽고 있는 책으로.
            shelf.filter { finishes[it.id] != null }.forEach { b -> acc.getOrPut(b.id) { Acc(b, State.READING, false) } }
            BookShelf.history(context).filter { finishes[it.book.id] != null }.forEach { p ->
                acc.getOrPut(p.book.id) { Acc(p.book, State.PAST, p.hidden) }
            }
            return acc.values.map { BookRecord(it.book, it.state, it.hidden, finishes[it.book.id].orEmpty(), it.dayMs) }
        }

        /** 읽고 있는 책 한 권의 기록(읽은 시간도 완독도 없으면 빈 기록) */
        fun of(context: Context, book: ShelfBook): BookRecord = all(context).firstOrNull { it.book.id == book.id }
            ?: BookRecord(book, State.READING, false, BookShelf.finishes(context)[book.id].orEmpty(), emptyMap())
    }
}
