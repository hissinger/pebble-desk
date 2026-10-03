package com.woody.pebblehome

import android.content.Context

/** 런처 설정과 앱 목록 상태(읽고 있는 앱·자주 쓰는 앱·숨긴 앱·연 횟수) */
class HomePrefs(context: Context) {
    private val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val counts = context.getSharedPreferences("open_counts", Context.MODE_PRIVATE)

    /** 읽고 있는 앱(홈에 크게 보이는 앱). 저장 키는 예전 이름(default_app)을 그대로 쓴다. */
    var readingApp: String?
        get() = sp.getString("default_app", null)
        set(v) = sp.edit().putString("default_app", v).apply()

    var favorites: Set<String>
        get() = sp.getStringSet("favorites", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("favorites", v).apply()

    var hidden: Set<String>
        get() = sp.getStringSet("hidden", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("hidden", v).apply()

    /** 자주 쓰는 앱 정렬: true 면 많이 연 순, false 면 가나다순 */
    var sortByCount: Boolean
        get() = sp.getBoolean("sort_by_count", true)
        set(v) = sp.edit().putBoolean("sort_by_count", v).apply()

    /** 홈 시계: true 면 24시간(18:24), false 면 12시간(오후 6:24) */
    var clock24h: Boolean
        get() = sp.getBoolean("clock_24h", true)
        set(v) = sp.edit().putBoolean("clock_24h", v).apply()

    /** 앱 전체(시계·날짜 포함)를 영어로. false 면 한국어 */
    var english: Boolean
        get() = sp.getBoolean(KEY_ENGLISH, false)
        set(v) = sp.edit().putBoolean(KEY_ENGLISH, v).apply()

    var showIcons: Boolean
        get() = sp.getBoolean("show_icons", true)
        set(v) = sp.edit().putBoolean("show_icons", v).apply()



    fun openCount(key: String) = counts.getInt(key, 0)

    fun countOpen(key: String) = counts.edit().putInt(key, openCount(key) + 1).apply()

    fun resetCounts() = counts.edit().clear().apply()

    /**
     * 읽고 있는 앱을 [key] 로 바꾼다(null 이면 해제). 이전 앱은 홈에서 사라지지 않도록 자주 쓰는 앱에 넣는다.
     */
    fun changeReadingApp(key: String?) {
        val previous = readingApp
        if (previous == key) return
        if (previous != null) favorites = favorites + previous
        readingApp = key
    }

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

    /**
     * 목록에 없는 앱을 정리한다. 앱을 업데이트하는 동안에는 잠깐 목록에서 빠지므로,
     * 패키지가 정말 지워졌을 때만 빼고, 실행 화면 이름만 바뀌었으면 같은 패키지의 새 화면으로 옮긴다.
     */
    fun prune(apps: List<AppEntry>, isInstalled: (String) -> Boolean) {
        val keys = apps.map { it.key }.toSet()
        fun fix(key: String): String? {
            if (key in keys) return key
            val pkg = key.substringBefore('/')
            apps.firstOrNull { it.pkg == pkg }?.let { return it.key }
            return if (isInstalled(pkg)) key else null
        }
        readingApp?.let { key -> fix(key).let { if (it != key) readingApp = it } }
        favorites.let { set -> set.mapNotNull(::fix).toSet().let { if (it != set) favorites = it } }
        hidden.let { set -> set.mapNotNull(::fix).toSet().let { if (it != set) hidden = it } }
    }

    companion object {
        private const val FILE = "home"
        private const val KEY_ENGLISH = "english"

        /** 화면을 만들기 전에(attachBaseContext) 언어를 정하려고 쓴다. */
        fun isEnglish(context: Context) =
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_ENGLISH, false)
    }
}
