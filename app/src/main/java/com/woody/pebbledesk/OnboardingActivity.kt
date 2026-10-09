package com.woody.pebbledesk

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView

/**
 * ⑭ 시작하기(온보딩). 처음 한 번, 기본으로 꺼져 있는 두 기능을 한 쪽에 하나씩 설명하고 켜게 한다.
 * 1쪽 읽는 책 자동 추가, 2쪽 오늘 읽은 시간. 둘 다 이 앱의 기본 기능이라 켜져야 넘어간다(1쪽 `다음 ›`, 2쪽 `시작하기 ›`.
 * 그 전에는 흐리게, 누르지 않는다. 그만두려면 뒤로 키). 항목마다 설명 · 켜면 홈에 생기는 모습(작게) · `켜는 방법` 단계(켠 뒤에도
 * 그대로 둔다. 단계 옆 버튼이 그 시스템 화면을 연다. 돌아오면 이름 옆에 `켜짐 ✓`). 닫으면 다시 나오지 않는다.
 * 스크롤하지 않으므로 쓸 수 있는 높이(기기·시스템 줄마다 다르다)에 넘치면 쪽 안의 글자를 줄여 그린다([fitPage]).
 */
class OnboardingActivity : EinkActivity() {
    /** 쪽 내용이 들어가는 칸. 남는 높이를 모두 차지한다([available]). */
    private lateinit var content: FrameLayout
    private lateinit var prev: TextView
    private lateinit var pageText: TextView
    private lateinit var next: TextView
    /** 0 = 자동 추가, 1 = 오늘 읽은 시간 */
    private var page = 0
    /** 지난번에 그린 상태(쪽 · 자동 추가 켜짐 · 읽은 시간 켜짐). 같으면 다시 그리지 않는다(전자잉크 갱신을 줄인다). */
    private var shownState: List<Any>? = null
    /** [content] 의 높이. 알기 전(처음 레이아웃 전)에는 0 이라 그리지 않는다. */
    private var available = 0
    private val cover by lazy { sampleCover(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = savedInstanceState?.getInt(KEY_PAGE) ?: 0
        val root = vbox()
        root.addView(text(getString(R.string.onboarding_title), 34f, Ui.heavy).apply {
            setPadding(dp(Ui.MARGIN), dp(24), dp(Ui.MARGIN), 0)
        })
        root.addView(text(getString(R.string.onboarding_intro), 17f, color = Ui.GRAY, lines = 2).apply {
            setLineSpacing(0f, 1.2f)
            setPadding(dp(Ui.MARGIN), dp(8), dp(Ui.MARGIN), dp(14))
        })
        root.addView(hline(2))
        content = FrameLayout(this)
        root.addView(content, lp(MATCH, 0, 1f))
        // 높이가 정해지면(보통 처음 한 번) 이번 그리기를 건너뛰고 그 높이에 맞춰 채운 뒤 그린다(홈 책 자리와 같다. 빈 화면을 한 번 그리지 않는다).
        content.viewTreeObserver.addOnPreDrawListener {
            if (content.height == available) true else { available = content.height; shownState = null; render(); false }
        }

        // 하단: 왼쪽 `‹ 이전`(2쪽), 가운데 쪽 번호, 오른쪽 `다음 ›`(1쪽) / `시작하기 ›`(2쪽)
        root.addView(hline(1, inset = false))
        val footer = FrameLayout(this)
        prev = text(getString(R.string.onboarding_prev), 22f).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Ui.MARGIN), 0, dp(24), 0)
            setOnClickListener { showPage(0) }
        }
        footer.addView(prev, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.START))
        pageText = text("", 16f, color = Ui.LIGHT_GRAY).apply { gravity = Gravity.CENTER }
        footer.addView(pageText, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.CENTER))
        next = text("", 22f, Ui.bold).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), 0, dp(Ui.MARGIN), 0)
        }
        footer.addView(next, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.END))
        root.addView(footer, lp(MATCH, dp(Ui.FOOTER_DP)))
        setContentView(root)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_PAGE, page)
    }

    /** 시스템 설정에서 돌아올 때마다 켜졌는지 다시 본다. */
    override fun onResume() {
        super.onResume()
        render()
    }

    /** 밀기·페이지 버튼: 1쪽에서 다음은 자동 추가가 켜져 있을 때만 */
    override fun onSwipe(toLeft: Boolean): Boolean {
        val to = if (toLeft) 1 else 0
        if (to == page || (to == 1 && !ReaderWatchService.isEnabled(this))) return false
        showPage(to)
        return true
    }

    private fun showPage(to: Int) {
        page = to
        render()
    }

    private fun render() {
        if (available <= 0) return
        val books = ReaderWatchService.isEnabled(this)
        val time = ReadingLog.hasAccess(this)
        val state = listOf(page, books, time, available)
        if (state == shownState) return
        shownState = state
        content.removeAllViews()
        content.addView(fitPage(books, time), FrameLayout.LayoutParams(MATCH, WRAP))

        pageText.text = "${page + 1} / 2"
        prev.visibility = if (page == 1) View.VISIBLE else View.INVISIBLE
        // 그 쪽의 기능이 켜져야 넘어간다(다음 쪽 / 닫기).
        val done = if (page == 0) books else time
        next.text = getString(if (page == 0) R.string.onboarding_next else R.string.onboarding_start)
        next.setTextColor(if (done) Ui.BLACK else Ui.DIVIDER)
        next.setOnClickListener(if (!done) null else if (page == 0) View.OnClickListener { showPage(1) } else View.OnClickListener { finish() })
        next.isClickable = done
    }

    /**
     * 지금 쪽을 [available] 높이에 들어가게 만든다. 붙이기 전에 재 보고 넘치면 쪽 안의 글자를 [FIT_STEP] 씩
     * ([MAX_FIT_STEPS] 번, 70% 까지) 줄여 다시 만든다. 내용은 빼지 않는다.
     */
    private fun fitPage(books: Boolean, time: Boolean): View {
        var step = 0
        while (true) {
            val scale = 1f - step * FIT_STEP
            val view = buildPage(books, time, scale)
            view.measure(
                View.MeasureSpec.makeMeasureSpec(content.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            if (view.measuredHeight <= available || step == MAX_FIT_STEPS) return view
            step++
        }
    }

    /** 한 쪽. 글자 크기는 모두 [scale] 배 */
    private fun buildPage(books: Boolean, time: Boolean, scale: Float): View = vbox().apply {
        if (page == 0) {
            addView(feature(
                getString(R.string.auto_books), books,
                getString(R.string.onboarding_books_desc), getString(R.string.onboarding_books_note), bookPreview(scale), scale,
            ))
            addView(steps(booksSteps(), scale))
        } else {
            addView(feature(
                getString(R.string.reading_time), time,
                getString(R.string.onboarding_time_desc), getString(R.string.onboarding_time_note), footerPreview(scale), scale,
            ))
            addView(steps(listOf(
                getString(R.string.onboarding_time_s1) to Step(R.string.onboarding_open) { ReadingLog.openAccessSettings(this@OnboardingActivity) },
                getString(R.string.onboarding_time_s2) to null,
            ), scale))
            addView(text(getString(R.string.onboarding_settings_hint), 14f * scale, color = Ui.LIGHT_GRAY, lines = 2).apply {
                setPadding(dp(Ui.MARGIN), dp(18), dp(Ui.MARGIN), 0)
            })
        }
    }

    /**
     * 자동 추가 켜는 방법. 안드로이드 13+ 은 파일로 깐 앱을 '제한된 설정'으로 막을 수 있어(막혔는지는 알 수 없다) 푸는 단계까지.
     * `⋮` 의 `제한된 설정 허용` 은 접근성에서 막힌 창을 한 번 본 뒤에야 생긴다.
     */
    private fun booksSteps(): List<Pair<String, Step?>> {
        val accessibility = Step(R.string.onboarding_open_accessibility) { ReaderWatchService.openSettings(this) }
        return if (ReaderWatchService.mayBeRestricted) listOf(
            getString(R.string.onboarding_books_r1) to accessibility,
            getString(R.string.onboarding_books_r2) to null,
            getString(R.string.onboarding_books_r3) to Step(R.string.onboarding_open_app_info) { ReaderWatchService.openAppInfo(this) },
            getString(R.string.onboarding_books_r4) to accessibility,
        ) else listOf(
            getString(R.string.onboarding_books_s1) to accessibility,
            getString(R.string.onboarding_books_s2) to null,
        )
    }

    /** 단계 옆 버튼: 글자와 누르면 할 일 */
    private class Step(val label: Int, val action: () -> Unit)

    /** 기능 머리: 이름 ··· `켜짐 ✓`(켜졌을 때), 아래 왼쪽에 설명(검정)과 덧붙임(회색), 오른쪽에 [preview]. 글자는 [scale] 배. */
    private fun feature(name: String, on: Boolean, desc: String, note: String, preview: View, scale: Float): View = vbox().apply {
        setPadding(dp(Ui.MARGIN), dp(18), dp(Ui.MARGIN), dp(12))
        addView(hbox().apply {
            addView(text(name, 27f * scale, Ui.bold), lp(0, WRAP, 1f))
            if (on) addView(text(getString(R.string.onboarding_on), 22f * scale, color = Ui.GRAY))
        })
        addView(hbox().apply {
            gravity = Gravity.TOP
            addView(vbox().apply {
                addView(text(desc, 17f * scale, lines = 5).apply { setLineSpacing(0f, 1.2f) })
                addView(text(note, 15f * scale, color = Ui.GRAY, lines = 3).apply { setLineSpacing(0f, 1.15f) },
                    lp(MATCH, WRAP).apply { topMargin = dp(6) })
            }, lp(0, WRAP, 1f))
            addView(preview, lp(dp(PREVIEW_DP), WRAP).apply { marginStart = dp(22); topMargin = dp(4) })
        }, lp(MATCH, WRAP).apply { topMargin = dp(10) })
    }

    /** `켜는 방법`: 옅은 선 아래 회색 소제목, 줄마다 ①② · 설명 · 오른쪽 버튼(그 시스템 화면을 연다) */
    private fun steps(items: List<Pair<String, Step?>>, scale: Float): View = vbox().apply {
        addView(hline(1, Ui.DIVIDER))
        addView(text(getString(R.string.onboarding_steps), 14f * scale, Ui.bold, Ui.GRAY).apply {
            setPadding(dp(Ui.MARGIN), dp(10), dp(Ui.MARGIN), dp(2))
        })
        items.forEachIndexed { i, (desc, step) ->
            addView(hbox().apply {
                gravity = Gravity.TOP
                setPadding(dp(Ui.MARGIN), dp(6), dp(Ui.MARGIN), dp(4))
                addView(text(CIRCLED[i], 20f * scale), lp(dp(32), WRAP))
                addView(text(desc, 17f * scale, lines = 4).apply { setLineSpacing(0f, 1.1f) }, lp(0, WRAP, 1f))
                // 누르는 자리는 글자보다 넓게(왼쪽·아래 여백, 최소 44dp). 위 여백은 두지 않아 글자 윗줄이 설명 첫 줄과 맞는다.
                if (step != null) addView(text(getString(step.label), 18f * scale, Ui.bold).apply {
                    setPadding(dp(16), 0, 0, dp(8))
                    minHeight = dp(44)
                    setOnClickListener { step.action() }
                })
            })
        }
    }

    /** 자동 추가를 켜면 홈에 생기는 것: 맨 앞 책(작게). 예시 책 표지는 퍼블릭 도메인(`tools/sample_cover.py`). */
    private fun bookPreview(scale: Float): View = hbox().apply {
        gravity = Gravity.TOP
        val w = dp(SAMPLE_COVER_DP)
        addView(coverImage(cover), lp(w + dp(2), w * 3 / 2 + dp(2)))
        addView(vbox().apply {
            addView(text(getString(R.string.sample_title), 16f * scale, Ui.heavy, lines = 2))
            addView(text("${getString(R.string.sample_author)} · ${getString(R.string.progress_pct, 38)}", 12f * scale, color = Ui.GRAY),
                lp(WRAP, WRAP).apply { topMargin = dp(6) })
            addView(text("${getString(R.string.sample_app)}  ›", 12f * scale, Ui.bold), lp(WRAP, WRAP).apply { topMargin = dp(8) })
        }, lp(0, WRAP, 1f).apply { marginStart = dp(12) })
    }

    /** 오늘 읽은 시간을 켜면 홈 하단 줄 가운데에 생기는 것 */
    private fun footerPreview(scale: Float): View = vbox().apply {
        addView(hline(1, inset = false), lp(MATCH, dp(1)).apply { topMargin = dp(12) })
        addView(hbox().apply {
            addView(text(getString(R.string.all_apps_link), 13f * scale, Ui.bold))
            addView(View(context), lp(0, 1, 1f))
            addView(text(getString(R.string.reading_today, duration(42 * 60_000L)) + "  ·  " +
                getString(R.string.reading_streak, 5), 12f * scale, color = Ui.GRAY))
        }, lp(MATCH, dp(44)))
    }

    companion object {
        /** 오른쪽 미리 보기 칸 폭 */
        private const val PREVIEW_DP = 176
        private const val SAMPLE_COVER_DP = 52
        private const val KEY_PAGE = "page"
        private val CIRCLED = listOf("①", "②", "③", "④")
        /** [fitPage]: 넘칠 때 글자를 한 번에 줄이는 비율과 가장 작게 줄이는 비율 */
        private const val FIT_STEP = 0.05f
        private const val MAX_FIT_STEPS = 6

        /**
         * 홈에서 부른다. 아직 띄운 적이 없고(잠금이 풀린 뒤) 두 기능 중 하나라도 시스템에서 꺼져 있으면 한 번 띄운다.
         * 둘 다 이미 켜져 있으면 띄우지 않고 띄운 것으로 친다(나중에 꺼도 다시 나오지 않는다). 띄우는 순간 적어 두 번 뜨지 않게 하고,
         * 시스템 설정에 갔다가 홈 버튼으로 나가도 다시 띄우지 않는다(설정 › 독서에 같은 것이 있다).
         */
        fun showOnce(activity: Activity, prefs: HomePrefs) {
            if (prefs.onboarded || !Storage.isUnlocked(activity)) return
            prefs.onboarded = true
            if (ReaderWatchService.isEnabled(activity) && ReadingLog.hasAccess(activity)) return
            activity.startActivity(Intent(activity, OnboardingActivity::class.java))
        }

        /** 예시 책 표지. 직접 그리는 그림이라 크레마 화면 대비만큼 미리 밝힌다(홈 표지와 같다). */
        private fun sampleCover(context: Context): Bitmap =
            BitmapFactory.decodeResource(context.resources, R.drawable.sample_cover, BitmapFactory.Options().apply {
                inScaled = false; inMutable = true
            }).also { Crema.undoContrast(it, Crema.contrastTone(context)) }
    }
}
