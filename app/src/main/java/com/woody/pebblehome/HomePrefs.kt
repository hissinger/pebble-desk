package com.woody.pebblehome

import android.content.Context

/** 런처 설정과 앱 목록 상태(기본 앱·자주 쓰는 앱·숨긴 앱·연 횟수) */
class HomePrefs(context: Context) {
    private val sp = context.getSharedPreferences("home", Context.MODE_PRIVATE)
    private val counts = context.getSharedPreferences("open_counts", Context.MODE_PRIVATE)

    var defaultApp: String?
        get() = sp.getString("default_app", null)
        set(v) = sp.edit().putString("default_app", v).apply()

    var favorites: Set<String>
        get() = sp.getStringSet("favorites", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("favorites", v).apply()

    var hidden: Set<String>
        get() = sp.getStringSet("hidden", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("hidden", v).apply()

    /** 기기를 켜거나 슬립에서 깨면 홈 대신 기본 앱을 연다. */
    var openDefaultOnWake: Boolean
        get() = sp.getBoolean("open_default_on_wake", false)
        set(v) = sp.edit().putBoolean("open_default_on_wake", v).apply()

    /** 자주 쓰는 앱 정렬: true 면 많이 연 순, false 면 가나다순 */
    var sortByCount: Boolean
        get() = sp.getBoolean("sort_by_count", true)
        set(v) = sp.edit().putBoolean("sort_by_count", v).apply()

    var showIcons: Boolean
        get() = sp.getBoolean("show_icons", true)
        set(v) = sp.edit().putBoolean("show_icons", v).apply()

    var showNowReading: Boolean
        get() = sp.getBoolean("show_now_reading", true)
        set(v) = sp.edit().putBoolean("show_now_reading", v).apply()

    var largeText: Boolean
        get() = sp.getBoolean("large_text", false)
        set(v) = sp.edit().putBoolean("large_text", v).apply()

    /** 목록 글자 배율 (글자 크기: 보통 / 크게) */
    val textScale: Float get() = if (largeText) 1.15f else 1f

    fun openCount(key: String) = counts.getInt(key, 0)

    fun countOpen(key: String) = counts.edit().putInt(key, openCount(key) + 1).apply()

    fun resetCounts() = counts.edit().clear().apply()

    fun toggleFavorite(key: String) {
        favorites = if (key in favorites) favorites - key else favorites + key
    }

    fun hide(key: String) {
        hidden = hidden + key
        favorites = favorites - key
        if (defaultApp == key) defaultApp = null
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
        defaultApp?.let { key -> fix(key).let { if (it != key) defaultApp = it } }
        favorites.let { set -> set.mapNotNull(::fix).toSet().let { if (it != set) favorites = it } }
        hidden.let { set -> set.mapNotNull(::fix).toSet().let { if (it != set) hidden = it } }
    }
}
