package com.woody.pebbledesk

import android.content.Context
import org.json.JSONArray
import java.time.DayOfWeek
import java.time.LocalDate

/** 런처 설정과 앱 목록 상태(자주 쓰는 앱·숨긴 앱) */
class HomePrefs(context: Context) {
    private val sp = Storage.of(context).getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 예전(책 한 권만 저장하던) 버전의 읽고 있는 앱. 그 책을 새 목록으로 옮길 때만 읽는다. */
    val legacyReadingApp: String? get() = sp.getString("default_app", null)

    /**
     * 자주 쓰는 앱(직접 정한 순서). 예전에는 순서 없는 묶음(favorites)으로 저장했으므로,
     * 아직 목록이 없으면 그 묶음을 늘어놓아 시작한다.
     */
    var favoriteList: List<String>
        get() {
            sp.getString("favorite_list", null)?.let { json ->
                return runCatching { JSONArray(json).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList())
            }
            return sp.getStringSet("favorites", emptySet())!!.sorted()
        }
        set(v) = sp.edit().putString("favorite_list", JSONArray(v.distinct()).toString()).remove("favorites").apply()

    /** 들어 있는지 볼 때와 넣고 뺄 때. 새로 넣는 앱은 목록 맨 뒤에 붙는다. */
    var favorites: Set<String>
        get() = favoriteList.toSet()
        set(v) {
            val old = favoriteList
            favoriteList = old.filter { it in v } + v.filter { it !in old }
        }

    /** [key] 를 [delta] 칸 옮긴다(-1 이면 위로). */
    fun moveFavorite(key: String, delta: Int) {
        val list = favoriteList.toMutableList()
        val from = list.indexOf(key)
        val to = from + delta
        if (from < 0 || to !in list.indices) return
        list.add(to, list.removeAt(from))
        favoriteList = list
    }

    var hidden: Set<String>
        get() = sp.getStringSet("hidden", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("hidden", v).apply()

    /** 홈 시계: true 면 24시간(18:24), false 면 12시간(오후 6:24) */
    var clock24h: Boolean
        get() = sp.getBoolean("clock_24h", true)
        set(v) = sp.edit().putBoolean("clock_24h", v).apply()

    /** 앱 전체(시계·날짜 포함)를 영어로. false 면 한국어 */
    var english: Boolean
        get() = sp.getBoolean(KEY_ENGLISH, false)
        set(v) = sp.edit().putBoolean(KEY_ENGLISH, v).apply()

    /** 자주 쓰는 앱을 격자(아이콘 아래 이름, 한 줄에 4개)로. false 면 목록 */
    var favGrid: Boolean
        get() = sp.getBoolean("fav_grid", false)
        set(v) = sp.edit().putBoolean("fav_grid", v).apply()

    /** 독서 기록의 4주를 날마다 막대그래프로. false 면 링 달력 */
    var readingGraph: Boolean
        get() = sp.getBoolean("reading_graph", false)
        set(v) = sp.edit().putBoolean("reading_graph", v).apply()

    /** 올해 읽은 책을 표지 격자(한 줄에 4권)로. false 면 목록 */
    var yearGrid: Boolean
        get() = sp.getBoolean("year_grid", false)
        set(v) = sp.edit().putBoolean("year_grid", v).apply()

    /** 올해 읽은 책에서 그해 완독한 책만. false 면 전체 */
    var yearFinishedOnly: Boolean
        get() = sp.getBoolean("year_finished_only", false)
        set(v) = sp.edit().putBoolean("year_finished_only", v).apply()

    // 읽는 책 자동 추가·오늘 읽은 시간은 따로 켜짐 값을 두지 않는다. 시스템의 접근성(ReaderWatchService.isEnabled)·
    // 사용 기록 액세스(ReadingLog.hasAccess)가 곧 켜짐이다(1.3.1 까지의 `auto_books`·`reading_time` 은 읽지 않는다).

    /** 시작하기(온보딩)를 한 번 띄웠는가(둘 다 이미 켜져 있어 건너뛴 경우도) */
    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()

    /**
     * 하루 목표(분). 0 이면 목표 없이 읽은 날만 표시. 바꾸면 오늘부터의 목표로 `reading_goal_history` 에 남겨
     * 지난날은 그날의 목표로 그린다([readingGoals]). 처음 바꿀 때 그 전 목표를 '처음부터'로 함께 남긴다.
     */
    var readingGoalMin: Int
        get() = sp.getInt("reading_goal_min", 30)
        set(v) {
            if (v == readingGoalMin) return
            val today = LocalDate.now().toEpochDay()
            // 같은 날 여러 번 바꾸면 마지막 값만
            val history = goalHistory().ifEmpty { listOf(Long.MIN_VALUE to readingGoalMin) }.filter { it.first < today } + (today to v)
            sp.edit().putInt("reading_goal_min", v)
                .putString("reading_goal_history", history.joinToString(",") { "${it.first}:${it.second}" }).apply()
        }

    /** (이날부터, 목표 분) 바꾼 순서대로. 바꾼 적이 없으면(이 기능 전 설치 포함) 비어 있다. */
    private fun goalHistory(): List<Pair<Long, Int>> =
        sp.getString("reading_goal_history", null).orEmpty().split(',').mapNotNull { e ->
            val (day, min) = e.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            day.toLongOrNull()?.let { d -> min.toIntOrNull()?.let { d to it } }
        }

    /** 날짜별 하루 목표(분): 그날까지 마지막으로 정한 목표. 기록이 없으면 지금 목표. 한 번 읽어 여러 날에 쓴다. */
    fun readingGoals(): (LocalDate) -> Int {
        val history = goalHistory()
        val now = readingGoalMin
        return { day -> history.lastOrNull { it.first <= day.toEpochDay() }?.second ?: now }
    }

    /** 독서 기록 달력·이번 주의 시작 요일: 월요일(기본) 또는 일요일 */
    var firstDayOfWeek: DayOfWeek
        get() = if (sp.getBoolean("week_starts_sunday", false)) DayOfWeek.SUNDAY else DayOfWeek.MONDAY
        set(v) = sp.edit().putBoolean("week_starts_sunday", v == DayOfWeek.SUNDAY).apply()

    var showIcons: Boolean
        get() = sp.getBoolean("show_icons", true)
        set(v) = sp.edit().putBoolean("show_icons", v).apply()

    fun toggleFavorite(key: String) {
        favorites = if (key in favorites) favorites - key else favorites + key
    }

    fun hide(key: String) {
        hidden = hidden + key
        favorites = favorites - key
    }

    fun unhide(key: String) {
        hidden = hidden - key
    }

    /** 지워졌거나 실행 화면 이름이 바뀐 앱을 정리한다([fix] 는 AppStore.keyFixer). */
    fun prune(fix: (String) -> String?) {
        favoriteList.let { list -> list.mapNotNull(fix).distinct().let { if (it != list) favoriteList = it } }
        hidden.let { set -> set.mapNotNull(fix).toSet().let { if (it != set) hidden = it } }
    }

    companion object {
        const val FILE = "home"
        private const val KEY_ENGLISH = "english"

        /** 화면을 만들기 전에(attachBaseContext) 언어를 정하려고 쓴다. */
        fun isEnglish(context: Context) =
            Storage.of(context).getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_ENGLISH, false)
    }
}
