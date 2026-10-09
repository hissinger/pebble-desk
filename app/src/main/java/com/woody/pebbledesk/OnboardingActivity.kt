package com.woody.pebbledesk

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * ⑭ 시작하기(온보딩). 처음 한 번, 기본으로 꺼져 있는 읽는 책 자동 추가·오늘 읽은 시간을 설명하고 켜게 한다.
 * 항목마다 설명 · 켜면 홈에 생기는 모습(작게) · `켜기 ›`(그 시스템 설정을 연다. 돌아오면 `켜짐 ✓`).
 * 하단은 하나도 안 켰으면 `나중에`, 하나라도 켰으면 `시작하기 ›`. 어느 쪽이든 닫으면 다시 나오지 않는다.
 */
class OnboardingActivity : EinkActivity() {
    private lateinit var items: LinearLayout
    private lateinit var done: TextView
    /** 지난번에 그린 상태(자동 추가 켜짐 · 막힘 · 읽은 시간 켜짐). 같으면 다시 그리지 않는다(전자잉크 갱신을 줄인다). */
    private var shownState: List<Boolean>? = null
    private val cover by lazy { sampleCover(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = vbox()
        root.addView(text(getString(R.string.onboarding_title), 34f, Ui.heavy).apply {
            setPadding(dp(Ui.MARGIN), dp(30), dp(Ui.MARGIN), 0)
        })
        root.addView(text(getString(R.string.onboarding_intro), 17f, color = Ui.GRAY, lines = 2).apply {
            setLineSpacing(0f, 1.2f)
            setPadding(dp(Ui.MARGIN), dp(10), dp(Ui.MARGIN), dp(18))
        })
        root.addView(hline(2))
        items = vbox()
        root.addView(items, lp(MATCH, 0, 1f))

        root.addView(hline(1, inset = false))
        // 왼쪽 안내는 남는 폭만 쓰고 넘치면 줄인다(영어는 길어 버튼과 겹칠 수 있다).
        val footer = hbox().apply { setPadding(dp(Ui.MARGIN), 0, 0, 0) }
        footer.addView(text(getString(R.string.onboarding_settings_hint), 14f, color = Ui.LIGHT_GRAY), lp(0, WRAP, 1f))
        done = text("", 22f).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), 0, dp(Ui.MARGIN), 0)
            setOnClickListener { finish() }
        }
        footer.addView(done, lp(WRAP, MATCH))
        root.addView(footer, lp(MATCH, dp(Ui.FOOTER_DP)))
        setContentView(root)
    }

    /** 시스템 설정에서 돌아올 때마다 켜졌는지 다시 본다. */
    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val watching = ReaderWatchService.isEnabled(this)
        val books = prefs.autoBooks && watching
        // 켜려고 접근성에 갔다가 켜지 못하고 돌아왔다: 안드로이드 13+ 은 '제한된 설정'일 수 있어 푸는 길을 함께 보인다.
        val blocked = prefs.autoBooks && !watching && ReaderWatchService.mayBeRestricted
        val access = ReadingLog.hasAccess(this)
        val time = prefs.readingTime && access
        val state = listOf(books, blocked, time)
        if (state == shownState) return
        shownState = state
        items.removeAllViews()
        items.addView(item(
            getString(R.string.auto_books), books,
            getString(R.string.onboarding_books_desc), getString(R.string.onboarding_books_note),
            getString(R.string.onboarding_books_how), bookPreview(), blocked,
        ) {
            prefs.autoBooks = true
            if (watching) render() else ReaderWatchService.openSettings(this)
        })
        items.addView(hline(1, Ui.DIVIDER))
        items.addView(item(
            getString(R.string.reading_time), time,
            getString(R.string.onboarding_time_desc), getString(R.string.onboarding_time_note),
            getString(R.string.onboarding_time_how), footerPreview(), blocked = false,
        ) {
            prefs.readingTime = true
            if (access) render() else ReadingLog.openAccessSettings(this)
        })
        items.addView(hline(1, Ui.DIVIDER))
        val any = books || time
        done.text = getString(if (any) R.string.onboarding_start else R.string.onboarding_later)
        done.typeface = if (any) Ui.bold else Ui.regular
    }

    /**
     * 기능 한 항목: 이름 ··· `켜기 ›`/`켜짐 ✓`, 아래 왼쪽에 설명(검정)과 덧붙임(회색), 오른쪽에 [preview].
     * 꺼져 있으면 맨 아래에 시스템 설정에서 할 일 한 줄(연한 회색). [blocked] 면 그 대신 제한된 설정 안내와 `앱 정보 열기 ›`.
     * 꺼져 있을 때는 항목 어디를 눌러도 [turnOn].
     */
    private fun item(
        name: String, on: Boolean, desc: String, note: String, how: String, preview: View, blocked: Boolean,
        turnOn: () -> Unit,
    ): View = vbox().apply {
        setPadding(dp(Ui.MARGIN), dp(22), dp(Ui.MARGIN), dp(18))
        addView(hbox().apply {
            addView(text(name, 25f, Ui.bold), lp(0, WRAP, 1f))
            addView(text(getString(if (on) R.string.onboarding_on else R.string.onboarding_turn_on), 22f,
                if (on) Ui.regular else Ui.bold, if (on) Ui.GRAY else Ui.BLACK))
        })
        addView(hbox().apply {
            gravity = Gravity.TOP
            addView(vbox().apply {
                addView(text(desc, 17f, lines = 5).apply { setLineSpacing(0f, 1.2f) })
                addView(text(note, 15f, color = Ui.GRAY, lines = 3).apply { setLineSpacing(0f, 1.15f) },
                    lp(MATCH, WRAP).apply { topMargin = dp(6) })
            }, lp(0, WRAP, 1f))
            addView(preview, lp(dp(PREVIEW_DP), WRAP).apply { marginStart = dp(22); topMargin = dp(4) })
        }, lp(MATCH, WRAP).apply { topMargin = dp(14) })
        if (blocked) {
            addView(text(getString(R.string.restricted_hint), 15f, color = Ui.GRAY, lines = 3).apply { setLineSpacing(0f, 1.15f) },
                lp(MATCH, WRAP).apply { topMargin = dp(12) })
            addView(text("${getString(R.string.open_app_info)}  ›", 18f, Ui.bold).apply {
                setPadding(0, dp(8), dp(24), dp(4))
                setOnClickListener { ReaderWatchService.openAppInfo(this@OnboardingActivity) }
            }, lp(WRAP, WRAP))
        } else if (!on) {
            addView(text(how, 14f, color = Ui.LIGHT_GRAY), lp(MATCH, WRAP).apply { topMargin = dp(12) })
        }
        if (!on) setOnClickListener { turnOn() }
    }

    /** 자동 추가를 켜면 홈에 생기는 것: 맨 앞 책(작게). 예시 책 표지는 퍼블릭 도메인(`tools/sample_cover.py`). */
    private fun bookPreview(): View = hbox().apply {
        gravity = Gravity.TOP
        val w = dp(SAMPLE_COVER_DP)
        addView(coverImage(cover), lp(w + dp(2), w * 3 / 2 + dp(2)))
        addView(vbox().apply {
            addView(text(getString(R.string.sample_title), 16f, Ui.heavy, lines = 2))
            addView(text("${getString(R.string.sample_author)} · ${getString(R.string.progress_pct, 38)}", 12f, color = Ui.GRAY),
                lp(WRAP, WRAP).apply { topMargin = dp(6) })
            addView(text("${getString(R.string.sample_app)}  ›", 12f, Ui.bold), lp(WRAP, WRAP).apply { topMargin = dp(8) })
        }, lp(0, WRAP, 1f).apply { marginStart = dp(12) })
    }

    /** 오늘 읽은 시간을 켜면 홈 하단 줄 가운데에 생기는 것 */
    private fun footerPreview(): View = vbox().apply {
        addView(hline(1, inset = false), lp(MATCH, dp(1)).apply { topMargin = dp(12) })
        addView(hbox().apply {
            addView(text(getString(R.string.all_apps_link), 13f, Ui.bold))
            addView(View(context), lp(0, 1, 1f))
            addView(text(getString(R.string.reading_today, duration(42 * 60_000L)) + "  ·  " +
                getString(R.string.reading_streak, 5), 12f, color = Ui.GRAY))
        }, lp(MATCH, dp(44)))
    }

    companion object {
        /** 오른쪽 미리 보기 칸 폭 */
        private const val PREVIEW_DP = 176
        private const val SAMPLE_COVER_DP = 52

        fun intent(context: Context) = Intent(context, OnboardingActivity::class.java)

        /** 처음 설치(또는 독서 두 기능을 한 번도 건드리지 않음)이고 잠금이 풀렸으면 보인다. */
        fun shouldShow(context: Context, prefs: HomePrefs) = !prefs.onboarded && Storage.isUnlocked(context)

        /** 예시 책 표지. 직접 그리는 그림이라 크레마 화면 대비만큼 미리 밝힌다(홈 표지와 같다). */
        private fun sampleCover(context: Context): Bitmap =
            BitmapFactory.decodeResource(context.resources, R.drawable.sample_cover, BitmapFactory.Options().apply {
                inScaled = false; inMutable = true
            }).also { Crema.undoContrast(it, Crema.contrastTone(context)) }
    }
}
