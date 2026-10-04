package com.woody.pebbledesk

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import java.time.DayOfWeek
import java.time.format.TextStyle

/** ⑧ 설정. 회색 소제목으로 묶고 값은 오른쪽에 같은 굵기로 쓴다. 켜짐은 검정, 꺼짐은 진회색, `›` 는 다음 화면. */
class SettingsActivity : EinkActivity() {
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = vbox()
        root.addView(titleRow(getString(R.string.settings)) { finish() })
        root.addView(hline(2))
        list = vbox()
        root.addView(list, lp(MATCH, 0, 1f))
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()
        root.addView(text("Pebble Desk $version", 15f, color = Ui.LIGHT_GRAY).apply {
            gravity = Gravity.CENTER
        }, lp(MATCH, dp(44)))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        list.removeAllViews()
        AppStore.loadAndPrune(this, prefs)

        // 읽고 있는 앱은 책을 정할 때 함께 고른다(책 없이 바꾸려면 앱을 길게 눌러 '읽고 있는 앱으로 설정').
        section(getString(R.string.section_home))
        val books = BookShelf.list(this)
        val booksText = when (books.size) {
            0 -> getString(R.string.none)
            1 -> books[0].title
            else -> getString(R.string.books_more, books[0].title, books.size - 1)
        }
        row(getString(R.string.now_reading), booksText, on = books.isNotEmpty(), next = true) {
            startActivity(BooksActivity.intent(this))
        }
        row(getString(R.string.favorites), prefs.favoriteList.size.toString(), next = true) {
            startActivity(FavoritesActivity.intent(this))
        }
        val views = listOf(getString(R.string.fav_view_list), getString(R.string.fav_view_grid))
        row(getString(R.string.fav_view), views[if (prefs.favGrid) 1 else 0], next = true) {
            choose(getString(R.string.fav_view), views, if (prefs.favGrid) 1 else 0) {
                prefs.favGrid = it == 1; render()
            }
        }
        toggle(getString(R.string.app_icons), prefs.showIcons, hint = getString(R.string.app_icons_hint)) { prefs.showIcons = it }
        // 켜 두었는데 권한이 없으면 값 자리에 '권한 허용'을 두고, 누르면 시스템 화면으로 보낸다.
        val access = ReadingLog.hasAccess(this)
        val needAccess = prefs.readingTime && !access
        row(
            getString(R.string.reading_time),
            getString(if (needAccess) R.string.allow_access else if (prefs.readingTime) R.string.on else R.string.off),
            on = prefs.readingTime, next = needAccess, hint = getString(R.string.reading_time_hint),
        ) {
            when {
                needAccess -> ReadingLog.openAccessSettings(this)
                prefs.readingTime -> { prefs.readingTime = false; render() }
                else -> { prefs.readingTime = true; if (!access) ReadingLog.openAccessSettings(this) else render() }
            }
        }
        if (prefs.readingTime) {
            val goals = listOf(0, 15, 20, 30, 45, 60)
            val goalText = { m: Int -> if (m == 0) getString(R.string.none) else getString(R.string.dur_m, m) }
            row(getString(R.string.daily_goal), goalText(prefs.readingGoalMin), next = true) {
                choose(getString(R.string.daily_goal), goals.map(goalText), goals.indexOf(prefs.readingGoalMin).coerceAtLeast(0)) {
                    prefs.readingGoalMin = goals[it]; render()
                }
            }
            // 독서 기록 달력과 '이번 주'의 시작 요일
            val days = listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY)
            val dayNames = days.map { it.getDisplayName(TextStyle.FULL, resources.configuration.locales[0]) }
            val first = days.indexOf(prefs.firstDayOfWeek)
            row(getString(R.string.week_starts), dayNames[first], next = true) {
                choose(getString(R.string.week_starts), dayNames, first) { prefs.firstDayOfWeek = days[it]; render() }
            }
        }
        val clocks = listOf(getString(R.string.clock24), getString(R.string.clock12))
        row(getString(R.string.clock), clocks[if (prefs.clock24h) 0 else 1], next = true) {
            choose(getString(R.string.clock), clocks, if (prefs.clock24h) 0 else 1) {
                prefs.clock24h = it == 0; render()
            }
        }
        // 언어 이름은 바꾸지 않는다(어느 언어에서도 알아볼 수 있게). 바꾸면 이 화면부터 다시 만든다.
        val languages = listOf("한국어", "English")
        row(getString(R.string.language), languages[if (prefs.english) 1 else 0], next = true) {
            choose(getString(R.string.language), languages, if (prefs.english) 1 else 0) {
                if ((it == 1) != prefs.english) {
                    prefs.english = it == 1
                    recreate()
                }
            }
        }

        section(getString(R.string.section_apps))
        row(getString(R.string.hidden_apps), prefs.hidden.size.toString(), next = true) {
            startActivity(AppListActivity.intent(this, AppListActivity.Mode.HIDDEN))
        }
        row(getString(R.string.set_default_home), null, next = true) { openHomeSettings() }
        row(getString(R.string.device_settings), null, next = true, last = true) {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
    }

    private fun section(title: String) {
        list.addView(text(title, 16f, color = Ui.LIGHT_GRAY).apply {
            setPadding(dp(Ui.MARGIN), dp(14), dp(Ui.MARGIN), dp(2))
        })
    }

    /** 설정 한 줄. [hint] 는 이름 뒤에 작은 회색 글자로 붙는 보조 설명. 값은 같은 굵기로, 꺼짐은 진회색. */
    private fun row(
        label: String, value: String?, on: Boolean = true, next: Boolean = false, last: Boolean = false,
        hint: String? = null, action: () -> Unit,
    ) {
        val row = hbox().apply {
            setPadding(dp(Ui.MARGIN), 0, dp(Ui.MARGIN), 0)
            addView(text(label, 22f))
            if (hint != null) addView(text(hint, 15f, color = Ui.LIGHT_GRAY), lp(WRAP, WRAP).apply { marginStart = dp(10) })
            addView(View(context), lp(0, 1, 1f))
            val shown = listOfNotNull(value, if (next) "›" else null).joinToString("  ")
            if (shown.isNotEmpty()) addView(text(shown, 20f, Ui.medium, if (on) Ui.BLACK else Ui.GRAY))
            setOnClickListener { action() }
        }
        list.addView(row, lp(MATCH, dp(45)))
        if (!last) list.addView(hline(1, Ui.DIVIDER))
    }

    private fun toggle(label: String, value: Boolean, hint: String? = null, set: (Boolean) -> Unit) =
        row(label, getString(if (value) R.string.on else R.string.off), on = value, hint = hint) { set(!value); render() }

    /** 안드로이드 홈 앱 선택 화면. 기기마다 있는 화면이 달라 차례로 시도한다. */
    private fun openHomeSettings() {
        val candidates = listOf(
            Intent(Settings.ACTION_HOME_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (intent in candidates) {
            if (runCatching { startActivity(intent) }.isSuccess) return
        }
    }
}
