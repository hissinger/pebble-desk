package com.woody.pebblehome

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout

/** ⑧ 설정. 회색 소제목으로 묶고 값은 오른쪽에 쓴다. 켜짐은 굵게, 꺼짐은 회색, `›` 는 다음 화면. */
class SettingsActivity : EinkActivity() {
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = vbox()
        root.addView(titleRow("설정") { finish() })
        root.addView(hline(2))
        list = vbox()
        root.addView(list, lp(MATCH, 0, 1f))
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()
        root.addView(text("Pebble Home $version", 15f, color = Ui.LIGHT_GRAY).apply {
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
        val apps = AppStore.loadAndPrune(this, prefs)
        val defaultLabel = apps.find { it.key == prefs.defaultApp }?.label

        section("기본 앱")
        row("기본 앱", defaultLabel ?: "없음", on = defaultLabel != null, next = true) {
            startActivity(AppListActivity.intent(this, AppListActivity.Mode.PICK_DEFAULT))
        }
        toggle("켤 때 기본 앱 바로 열기", prefs.openDefaultOnWake) { prefs.openDefaultOnWake = it }

        section("홈 화면")
        toggle("지금 읽는 책 (Cover Pebble)", prefs.showNowReading) { prefs.showNowReading = it }
        row("자주 쓰는 앱 정렬", if (prefs.sortByCount) "많이 연 순" else "가나다순", next = true) {
            choose("자주 쓰는 앱 정렬", listOf("많이 연 순", "가나다순"), if (prefs.sortByCount) 0 else 1) {
                prefs.sortByCount = it == 0; render()
            }
        }
        toggle("앱 아이콘", prefs.showIcons) { prefs.showIcons = it }
        row("글자 크기", if (prefs.largeText) "크게" else "보통", next = true) {
            choose("글자 크기", listOf("보통", "크게"), if (prefs.largeText) 1 else 0) {
                prefs.largeText = it == 1; render()
            }
        }

        section("앱 관리")
        row("숨긴 앱", prefs.hidden.size.toString(), next = true) {
            startActivity(AppListActivity.intent(this, AppListActivity.Mode.HIDDEN))
        }
        row("연 횟수 초기화", null) {
            Sheet(this).header("연 횟수 초기화", "모든 앱의 연 횟수를 0으로 되돌립니다")
                .item("초기화", bold = true) { prefs.resetCounts() }
                .show()
        }
        row("기본 홈 앱으로 설정", null, next = true) { openHomeSettings() }
        row("기기 설정 열기", null, next = true, last = true) {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
    }

    private fun section(title: String) {
        list.addView(text(title, 16f, color = Ui.LIGHT_GRAY).apply {
            setPadding(dp(Ui.MARGIN), dp(14), dp(Ui.MARGIN), dp(2))
        })
    }

    private fun row(label: String, value: String?, on: Boolean = true, next: Boolean = false, last: Boolean = false, action: () -> Unit) {
        val row = hbox().apply {
            setPadding(dp(Ui.MARGIN), 0, dp(Ui.MARGIN), 0)
            addView(text(label, 22f), lp(0, WRAP, 1f))
            val shown = listOfNotNull(value, if (next) "›" else null).joinToString("  ")
            if (shown.isNotEmpty()) {
                addView(text(shown, 20f, if (on && value != null) Ui.bold else Ui.regular, if (on) Ui.BLACK else Ui.LIGHT_GRAY))
            }
            setOnClickListener { action() }
        }
        list.addView(row, lp(MATCH, dp(46)))
        if (!last) list.addView(hline(1, Ui.DIVIDER))
    }

    private fun toggle(label: String, value: Boolean, set: (Boolean) -> Unit) =
        row(label, if (value) "켜짐" else "꺼짐", on = value) { set(!value); render() }

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
