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
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 오늘 읽은 시간과 연속 독서일(오늘까지, 오늘 아직 안 읽었으면 어제까지) */
data class ReadingSummary(val todayMs: Long, val streak: Int)

/** 날마다 앱(패키지)별 읽은 시간(ms) */
typealias ReadingDays = Map<LocalDate, Map<String, Long>>

fun ReadingDays.total(day: LocalDate): Long = this[day]?.values?.sum() ?: 0

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
 * 앞에 떠 있던 시간을 더한다. 어떤 책인지는 알 수 없으므로 책과 상관없이 하루 합계만 센다.
 * 사용 기록은 며칠만 남으므로 끝난 날의 합계는 files/reading.json 에 남겨 둔다.
 */
object ReadingLog {
    /**
     * 읽는 화면(클래스의 마지막 이름). 서재·스토어 화면과 구분한다.
     * 밀리 EPubViewActivity · 교보 ViewerEpubMainActivity · 알라딘 ViewerActivity, ReadONBookRenderActivity ·
     * 리디 EPubReaderActivity · YES24 CremaEPUBActivity 등. 리디의 WebViewActivity 같은 웹 화면은 뺀다.
     */
    private val READER = Regex(
        "^(?!.*WebView).*(ViewActivity|ViewerActivity|ReaderActivity|RenderActivity|" +
            "Viewer(Epub|Pdf|Comic)MainActivity|(EPUB|PDF|CPUB|TXT)Activity)$"
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
     * [packages] (이북 앱) 의 날마다 앱별 읽은 시간. 권한이 없으면 null.
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
        val days = saved.days + c.totals.mapValues { it.value.toMap() }
        // 기록이 시작된 날(그 전은 '안 읽음'이 아니라 '기록 없음')
        val since = saved.since ?: (saved.days.keys + from).min()
        if (saved.through != today.minusDays(1) || saved.since == null) save(context, Saved(today.minusDays(1), since, days - today))
        return days
    }

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
     * [from] 부터 날마다 앱별로 읽는 화면이 앞에 떠 있던 시간. [feed] 를 부를 때마다 지난번 이후의 사용 기록만 읽어
     * 이어서 센다(읽는 화면이 열려 있던 상태도 이어 간다).
     */
    private class Counter(val from: LocalDate, val packages: Set<String>, val zone: ZoneId) {
        private val start = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val totals = mutableMapOf<LocalDate, MutableMap<String, Long>>()
        /** 여기까지 읽었다. 처음에는 자정 직전에 펼친 책도 세도록 조금 앞에서부터. */
        private var until = start - LOOKBACK_MS
        private var openAt = -1L
        private var openPkg = ""
        private var openClass: String? = null

        private fun add(pkg: String, a: Long, b: Long) {
            var t = maxOf(a, start)
            while (t < b) {
                val day = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
                val end = minOf(b, day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
                val apps = totals.getOrPut(day) { mutableMapOf() }
                apps[pkg] = (apps[pkg] ?: 0) + (end - t)
                t = end
            }
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

    private fun file(context: Context) = File(Storage.of(context).filesDir, "reading.json")

    /** `{"through": "2026-10-03", "since": "2026-09-27", "days": {"2026-10-03": {"kr.co.millie.eink": 1234}}}` */
    /** 이 런처만 쓰는 파일이라 쓸 때 함께 바꾸는 메모리 사본 */
    @Volatile private var cached: Saved? = null

    private fun load(context: Context): Saved = cached ?: read(context).also { cached = it }

    private fun read(context: Context): Saved = runCatching {
        val json = JSONObject(file(context).readText())
        val days = json.getJSONObject("days")
        Saved(LocalDate.parse(json.getString("through")), json.optString("since").takeIf { it.isNotEmpty() }?.let(LocalDate::parse), days.keys().asSequence().associate { key ->
            // 처음 만든 형식(앱 구분 없는 하루 합계)이면 getJSONObject 가 실패해 버리고, 사용 기록에서 다시 센다.
            val o = days.getJSONObject(key)
            LocalDate.parse(key) to o.keys().asSequence().associateWith { o.getLong(it) }
        })
    }.getOrElse { Saved(null, null, emptyMap()) }

    private fun save(context: Context, saved: Saved) {
        cached = saved
        val days = JSONObject()
        // '올해' 합계와 연속일에 충분한 1년여치만 남긴다.
        val oldest = LocalDate.now().minusDays(400)
        saved.days.filterKeys { it >= oldest }.forEach { (d, apps) ->
            days.put(d.toString(), JSONObject().apply { apps.forEach { (pkg, ms) -> put(pkg, ms) } })
        }
        runCatching { file(context).writeText(JSONObject().put("through", saved.through.toString()).put("since", saved.since.toString()).put("days", days).toString()) }
    }
}
