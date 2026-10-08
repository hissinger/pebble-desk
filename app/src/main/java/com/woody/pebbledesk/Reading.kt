package com.woody.pebbledesk

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.woody.pebbledesk.Db.rows
import com.woody.pebbledesk.Db.tx
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 오늘 읽은 시간과 연속 독서일(오늘까지, 오늘 아직 안 읽었으면 어제까지) */
data class ReadingSummary(val todayMs: Long, val streak: Int)

/** 날마다 읽은 시간(ms). 키는 [readingKey]: 어떤 책인지 알면 `패키지/제목`, 모르면 패키지만. */
typealias ReadingDays = Map<LocalDate, Map<String, Long>>

private fun readingKey(pkg: String, title: String?) = if (title == null) pkg else "$pkg/$title"
/** [readingKey] 의 패키지 */
fun readingPkg(key: String) = key.substringBefore('/')
/** [readingKey] 의 책 제목(모르면 null) */
fun readingTitle(key: String) = key.substringAfter('/', "").ifEmpty { null }

fun ReadingDays.total(day: LocalDate): Long = this[day]?.values?.sum() ?: 0

/** 한 책([readingKey])을 한 해 동안 읽은 시간과 처음·마지막으로 읽은 날(1분 이상 읽은 날만, 없으면 null) */
data class ReadSpan(val ms: Long = 0, val first: LocalDate? = null, val last: LocalDate? = null) {
    /** [day] 에 [dayMs] 를 더 읽었다 */
    fun plus(day: LocalDate, dayMs: Long): ReadSpan = if (dayMs < ReadingLog.MIN_DAY_MS) copy(ms = ms + dayMs)
        else ReadSpan(ms + dayMs, first?.let { minOf(it, day) } ?: day, last?.let { maxOf(it, day) } ?: day)
}

/** 해마다 책([readingKey])별 [ReadSpan] */
typealias ReadingYears = Map<Int, Map<String, ReadSpan>>

/** 읽은 시간 표시: `1시간 5분` · `42분` · `86시간`(열 시간이 넘으면 시간만) */
fun Context.duration(ms: Long): String {
    val min = ms / 60_000
    return when {
        min < 60 -> getString(R.string.dur_m, min)
        min % 60 == 0L || min >= 600 -> getString(R.string.dur_h, min / 60)
        else -> getString(R.string.dur_hm, min / 60, min % 60)
    }
}

/**
 * 오늘 읽은 시간·연속 독서일. 안드로이드 사용 기록(사용 기록 액세스 권한)에서 이북 앱의 '읽는 화면'이
 * 앞에 떠 있던 시간을 더한다. 어떤 책인지는 접근성 서비스가 그 앱에서 마지막으로 편 책([bookOpened])으로 나누고,
 * 그 앱에서 편 책을 한 번도 몰랐으면 앱으로만 센다(서비스를 끈 동안 읽은 시간도 마지막으로 편 책으로 들어간다).
 * 사용 기록은 며칠만 남으므로 끝난 날의 합계는 DB([Db]) `reading_day` 에 남겨 둔다(지우지 않는다. 지난해 읽은 책을 보려고).
 */
object ReadingLog {
    /**
     * 읽는 화면(클래스의 마지막 이름). 서재·스토어 화면과 구분한다.
     * 밀리 EPubViewActivity · 교보 ViewerEpubMainActivity · 알라딘 ViewerActivity, ReadONBookRenderActivity ·
     * 리디 EPubReaderActivity · YES24 CremaEPUBActivity · 북커스 EpubActivity 등. 리디의 WebViewActivity 같은 웹 화면은 뺀다.
     */
    private val READER = Regex(
        "^(?!.*WebView).*(ViewActivity|ViewerActivity|ReaderActivity|RenderActivity|" +
            "Viewer(Epub|Pdf|Comic)MainActivity|(EPUB|Epub|PDF|CPUB|TXT)Activity)$"
    )

    /** [cls](액티비티 전체 이름)가 이북 앱의 읽는 화면인가 */
    fun isReaderScreen(cls: String) = READER.matches(cls.substringAfterLast('.'))

    /** 연속일로 셀 하루 최소 시간 */
    const val MIN_DAY_MS = 60_000L

    /** 사용 기록에서 다시 계산할 지난 날 수(그보다 오래된 날은 저장해 둔 값을 쓴다) */
    private const val BACKFILL_DAYS = 7L

    /** 자정 직전에 펼친 책도 세도록 하루 시작보다 조금 앞에서부터 기록을 읽는다. */
    private const val LOOKBACK_MS = 6 * 60 * 60 * 1000L

    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else mode == AppOpsManager.MODE_ALLOWED
    }

    /** 셀 앱: 알려진 이북 앱과 책에 연결된 앱 */
    fun readerPackages(context: Context): Set<String> =
        AppStore.EBOOK_PACKAGES + BookShelf.list(context).mapNotNull { it.app?.substringBefore('/') }

    /** 시스템의 '사용 기록 액세스' 화면 */
    fun openAccessSettings(context: Context) {
        runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
    }

    /**
     * [packages] (이북 앱) 의 날마다 책([readingKey])별 읽은 시간. 권한이 없으면 null.
     * 끝난 날은 한 번만 계산해 저장하고, 오늘은 부를 때마다 다시 센다.
     */
    fun days(context: Context, packages: Set<String>): ReadingDays? {
        // 잠금 해제 전에는 사용 기록이 비어 보인다. 그대로 세면 어제를 0분으로 확정해 버리므로 기다린다.
        if (!Storage.isUnlocked(context) || !hasAccess(context)) return null
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val saved = load(context)
        val from = maxOf(saved.through?.plusDays(1) ?: today.minusDays(BACKFILL_DAYS), today.minusDays(BACKFILL_DAYS))
        // 같은 범위를 세던 중이면 지난번 이후의 사용 기록만 이어서 센다(홈으로 돌아올 때마다 하루치를 다시 읽지 않게).
        val c = counter?.takeIf { it.from == from && it.packages == packages && it.zone == zone }
            ?: Counter(from, packages, zone).also { counter = it }
        c.feed(context)
        val counted = c.totals(opens(context))
        // 기록이 시작된 날(그 전은 '안 읽음'이 아니라 '기록 없음')
        val since = saved.since ?: (saved.days.keys + from).min()
        val days = saved.days + counted
        // 새로 끝난 날(센 날 중 오늘 빼고)만 저장한다.
        if (saved.through != today.minusDays(1) || saved.since == null) save(context, Saved(today.minusDays(1), since, days - today), counted - today)
        return days
    }

    /** 해마다 책별 읽은 시간과 처음·마지막으로 읽은 날. 권한이 없으면 null. */
    fun years(context: Context, packages: Set<String>): ReadingYears? {
        val days = days(context, packages) ?: return null
        val out = mutableMapOf<Int, MutableMap<String, ReadSpan>>()
        days.forEach { (day, keys) ->
            val year = out.getOrPut(day.year) { mutableMapOf() }
            keys.forEach { (key, ms) -> year[key] = (year[key] ?: ReadSpan()).plus(day, ms) }
        }
        return out
    }

    /** 접근성 서비스가 알아낸 '[at] 에 [pkg] 에서 [title] 을 폈다'. 읽는 화면 시간을 그때 편 책으로 센다. */
    private class Open(val at: Long, val pkg: String, val title: String)

    @Volatile private var opens: List<Open>? = null

    /**
     * DB `book_open`(편 때 순). 비어 있으면(이 기능 전) 접근성 서비스가 기억해 둔 앱마다 마지막에 편 책부터 시작한다.
     * [bookOpened] 와 같은 잠금으로 읽는다(처음 읽는 동안 새로 편 책이 옛 목록에 덮이지 않게).
     */
    @Synchronized
    private fun opens(context: Context): List<Open> = opens ?: Db.get(context).rows("SELECT at, pkg, title FROM book_open ORDER BY at") {
        Open(it.getLong(0), it.getString(1), it.getString(2))
    }.ifEmpty {
        Storage.of(context).getSharedPreferences("reader_watch", Context.MODE_PRIVATE).all.mapNotNull { (k, v) ->
            if (k.startsWith("opened_") && v is String) Open(0, k.removePrefix("opened_"), v) else null
        }
    }.also { opens = it }

    /**
     * [pkg] 에서 [title] 을 폈다([at] 부터 읽은 시간은 이 책). 다시 셀 수 있는 날([BACKFILL_DAYS]) 남짓만 남기고,
     * 그보다 오래됐어도 앱마다 마지막 책은 남긴다(이북 앱은 마지막 책을 바로 다시 연다).
     */
    @Synchronized
    fun bookOpened(context: Context, pkg: String, title: String, at: Long = System.currentTimeMillis()) {
        val list = opens(context)
        if (list.filter { it.pkg == pkg }.maxByOrNull { it.at }?.title == title) return
        val old = at - (BACKFILL_DAYS + 3) * 24 * 60 * 60 * 1000L
        val kept = (list + Open(at, pkg, title)).groupBy { it.pkg }.values.flatMap { mine ->
            val last = mine.maxBy { it.at }
            mine.filter { it.at >= old || it === last }
        }.sortedBy { it.at }
        opens = kept
        runCatching {
            Db.get(context).tx {
                delete("book_open", null, null)
                kept.forEach { insertOrThrow("book_open", null, Db.values("at" to it.at, "pkg" to it.pkg, "title" to it.title)) }
            }
        }
    }

    /** [pkg] 에서 [t] 까지 마지막으로 편 책(모르면 null) */
    private fun List<Open>.bookAt(pkg: String, t: Long) = filter { it.pkg == pkg && it.at <= t }.maxByOrNull { it.at }?.title

    /** 기록이 시작된 날. 그 전 날은 읽었는지 알 수 없다. 아직 기록이 없으면 null. */
    fun since(context: Context): LocalDate? = load(context).since

    fun summary(context: Context, packages: Set<String>): ReadingSummary? =
        days(context, packages)?.let { ReadingSummary(it.total(LocalDate.now()), streak(it)) }

    /** 하루 [MIN_DAY_MS] 이상 읽은 날이 이어진 수. 오늘 아직 안 읽었으면 어제까지. */
    fun streak(days: ReadingDays, today: LocalDate = LocalDate.now()): Int {
        var streak = 0
        var day = if (days.total(today) >= MIN_DAY_MS) today else today.minusDays(1)
        while (days.total(day) >= MIN_DAY_MS) { streak++; day = day.minusDays(1) }
        return streak
    }

    /** 가장 길었던 연속일 */
    fun longestStreak(days: ReadingDays): Int {
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (day in days.keys.filter { days.total(it) >= MIN_DAY_MS }.sorted()) {
            run = if (prev != null && day == prev.plusDays(1)) run + 1 else 1
            best = maxOf(best, run)
            prev = day
        }
        return best
    }

    @Volatile private var counter: Counter? = null

    /**
     * [from] 부터 읽는 화면이 앞에 떠 있던 구간. [feed] 를 부를 때마다 지난번 이후의 사용 기록만 읽어
     * 이어서 센다(읽는 화면이 열려 있던 상태도 이어 간다). 어느 책인지는 [totals] 에서 그때까지 알아낸 편 책으로 나눈다
     * (알라딘처럼 서재로 돌아와서야 책을 아는 앱도 나중에 맞게 세도록).
     */
    private class Counter(val from: LocalDate, val packages: Set<String>, val zone: ZoneId) {
        private val start = from.atStartOfDay(zone).toInstant().toEpochMilli()
        /** [pkg] 의 읽는 화면이 [a]~[b] 에 앞에 있었다. */
        private class Span(val pkg: String, val a: Long, val b: Long)
        private val spans = mutableListOf<Span>()
        /** 여기까지 읽었다. 처음에는 자정 직전에 펼친 책도 세도록 조금 앞에서부터. */
        private var until = start - LOOKBACK_MS
        private var openAt = -1L
        private var openPkg = ""
        private var openClass: String? = null

        private fun add(pkg: String, a: Long, b: Long) {
            if (b > start) spans += Span(pkg, maxOf(a, start), b)
        }

        /** 날마다 책([readingKey])별 시간. 구간마다 닫힐 때까지 그 앱에서 마지막으로 편 책([opens])으로 센다. */
        @Synchronized
        fun totals(opens: List<Open>): ReadingDays {
            val totals = mutableMapOf<LocalDate, MutableMap<String, Long>>()
            for (s in spans) {
                val key = readingKey(s.pkg, opens.bookAt(s.pkg, s.b))
                var t = s.a
                while (t < s.b) {
                    val day = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
                    val end = minOf(s.b, day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
                    val keys = totals.getOrPut(day) { mutableMapOf() }
                    keys[key] = (keys[key] ?: 0) + (end - t)
                    t = end
                }
            }
            return totals
        }

        @Synchronized
        fun feed(context: Context) {
            val now = System.currentTimeMillis()
            val events = context.getSystemService(UsageStatsManager::class.java)?.queryEvents(until, now) ?: return
            until = now
            val e = UsageEvents.Event()
            while (events.getNextEvent(e)) {
                when (e.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        if (openAt >= 0) add(openPkg, openAt, e.timeStamp)
                        val cls = e.className
                        val reading = e.packageName in packages && cls != null && isReaderScreen(cls)
                        openAt = if (reading) e.timeStamp else -1
                        openPkg = e.packageName
                        openClass = if (reading) cls else null
                    }
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> if (openAt >= 0 && e.className == openClass) {
                        add(openPkg, openAt, e.timeStamp); openAt = -1
                    }
                    // 화면이 꺼지면 읽기도 멈춘 것으로 본다.
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> if (openAt >= 0) { add(openPkg, openAt, e.timeStamp); openAt = -1 }
                }
            }
            // 아직 열려 있는 읽는 화면은 닫힐 때 센다(홈에서 부르므로 보통은 이미 닫혀 있다).
        }
    }

    private class Saved(val through: LocalDate?, val since: LocalDate?, val days: ReadingDays)

    /** 메모리 사본(돌아올 때마다 DB를 읽지 않도록). 쓸 때 함께 바꾼다. */
    @Volatile private var cached: Saved? = null

    private fun load(context: Context): Saved = cached ?: read(context).also { cached = it }

    /** DB `reading_day`(날·책·ms)와 `reading_meta`(through·since) */
    private fun read(context: Context): Saved {
        val db = Db.get(context)
        val days = mutableMapOf<LocalDate, MutableMap<String, Long>>()
        db.rows("SELECT day, key, ms FROM reading_day") { c ->
            days.getOrPut(LocalDate.ofEpochDay(c.getLong(0))) { mutableMapOf() }[c.getString(1)] = c.getLong(2)
        }
        val meta = db.rows("SELECT name, value FROM reading_meta") { it.getString(0) to it.getString(1) }.toMap()
        fun date(name: String) = meta[name]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return Saved(date("through"), date("since"), days)
    }

    /**
     * [saved] 를 메모리 사본으로 두고, [changed] 날들(그날의 책별 시간을 통째로)과 through·since 를 한 트랜잭션으로 저장한다.
     * 날마다 기록은 지우지 않는다.
     */
    private fun save(context: Context, saved: Saved, changed: ReadingDays) {
        cached = saved
        runCatching {
            Db.get(context).tx {
                changed.forEach { (day, keys) ->
                    val d = day.toEpochDay()
                    delete("reading_day", "day = ?", arrayOf(d.toString()))
                    keys.forEach { (key, ms) -> insertOrThrow("reading_day", null, Db.values("day" to d, "key" to key, "ms" to ms)) }
                }
                for ((name, value) in listOf("through" to saved.through, "since" to saved.since)) {
                    replaceOrThrow("reading_meta", null, Db.values("name" to name, "value" to value?.toString()))
                }
            }
        }
    }
}
