package com.woody.pebbledesk

import android.content.Context
import org.json.JSONArray

/** 런처 설정과 앱 목록 상태(읽고 있는 앱·자주 쓰는 앱·숨긴 앱·연 횟수) */
class HomePrefs(context: Context) {
    private val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val counts = context.getSharedPreferences("open_counts", Context.MODE_PRIVATE)

    /** 읽고 있는 앱(홈에 크게 보이는 앱). 저장 키는 예전 이름(default_app)을 그대로 쓴다. */
    var readingApp: String?
        get() = sp.getString("default_app", null)
        set(v) = sp.edit().putString("default_app", v).apply()

    /**
     * 자주 쓰는 앱(직접 정한 순서). 예전에는 순서 없는 묶음(favorites)으로 저장했으므로,
     * 아직 목록이 없으면 그 묶음을 많이 연 순으로 늘어놓아 시작한다.
     */
    var favoriteList: List<String>
        get() {
            sp.getString("favorite_list", null)?.let { json ->
                return runCatching { JSONArray(json).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList())
            }
            return sp.getStringSet("favorites", emptySet())!!.sortedWith(compareByDescending<String> { openCount(it) }.thenBy { it })
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

    var showIcons: Boolean
        get() = sp.getBoolean("show_icons", true)
        set(v) = sp.edit().putBoolean("show_icons", v).apply()

    fun openCount(key: String) = counts.getInt(key, 0)

    fun countOpen(key: String) = counts.edit().putInt(key, openCount(key) + 1).apply()

    fun resetCounts() = counts.edit().clear().apply()

    fun toggleFavorite(key: String) {
        favorites = if (key in favorites) favorites - key else favorites + key
    }

    fun hide(key: String) {
        hidden = hidden + key
        favorites = favorites - key
        if (readingApp == key) readingApp = null
    }

    fun unhide(key: String) {
        hidden = hidden - key
    }

    /** 지워졌거나 실행 화면 이름이 바뀐 앱을 정리한다([fix] 는 AppStore.keyFixer). */
    fun prune(fix: (String) -> String?) {
        readingApp?.let { key -> fix(key).let { if (it != key) readingApp = it } }
        favoriteList.let { list -> list.mapNotNull(fix).distinct().let { if (it != list) favoriteList = it } }
        hidden.let { set -> set.mapNotNull(fix).toSet().let { if (it != set) hidden = it } }
    }

    companion object {
        private const val FILE = "home"
        private const val KEY_ENGLISH = "english"

        /** 화면을 만들기 전에(attachBaseContext) 언어를 정하려고 쓴다. */
        fun isEnglish(context: Context) =
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_ENGLISH, false)
    }
}
