package com.woody.pebblehome

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** ③ 모든 앱 / ⑥ 기본 앱 고르기 / ⑦ 숨긴 앱. 모두 제목 + 페이지 목록 + 하단 줄 구조다. */
class AppListActivity : EinkActivity() {
    enum class Mode { ALL, PICK_DEFAULT, HIDDEN }

    private lateinit var mode: Mode
    private lateinit var titleCount: TextView
    private lateinit var rows: PagedRows
    private lateinit var footerLeft: TextView
    private lateinit var pageText: TextView
    private var indexCol: LinearLayout? = null
    private var shown: List<AppEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = Mode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: Mode.ALL.name)
        buildViews()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onSwipe(toLeft: Boolean): Boolean {
        if (rows.pageCount <= 1) return false
        if (toLeft) rows.next() else rows.prev()
        return true
    }

    private fun buildViews() {
        val root = vbox()
        val title = when (mode) {
            Mode.ALL -> "모든 앱"
            Mode.PICK_DEFAULT -> "기본 앱 고르기"
            Mode.HIDDEN -> "숨긴 앱"
        }
        root.addView(titleRow(title) { finish() }.apply {
            titleCount = text("", 21f, color = Ui.GRAY)
            addView(titleCount, lp(WRAP, WRAP).apply { marginStart = dp(14); topMargin = dp(6) })
        })
        root.addView(hline(2))
        if (mode == Mode.PICK_DEFAULT) {
            root.addView(text("홈 맨 위에 크게 보일 앱을 고르세요", 17f, color = Ui.GRAY).apply {
                setPadding(dp(Ui.MARGIN), dp(14), dp(Ui.MARGIN), dp(4))
            })
        }

        val body = LinearLayout(this)
        val rowHeight = when (mode) {
            Mode.PICK_DEFAULT -> 64
            else -> 58
        }
        rows = PagedRows(this, dp(rowHeight * prefs.textScale))
        body.addView(rows, lp(0, MATCH, 1f))
        if (mode == Mode.ALL) {
            indexCol = vbox().apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL }
            body.addView(indexCol, lp(dp(48), MATCH).apply { marginEnd = dp(Ui.MARGIN - 18) })
        }
        root.addView(body, lp(MATCH, 0, 1f))

        root.addView(hline(1, inset = false))
        val footer = FrameLayout(this)
        footerLeft = text("", if (mode == Mode.HIDDEN) 17f else 24f, if (mode == Mode.HIDDEN) Ui.regular else Ui.bold,
            if (mode == Mode.HIDDEN) Ui.GRAY else Ui.BLACK).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Ui.MARGIN), 0, dp(24), 0)
        }
        footer.addView(footerLeft, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.START))
        pageText = text("", 21f).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), 0, dp(Ui.MARGIN), 0)
            setOnClickListener { rows.next() }
        }
        footer.addView(pageText, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.END))
        root.addView(footer, lp(MATCH, dp(72)))

        when (mode) {
            Mode.ALL -> {
                footerLeft.setOnClickListener { startActivity(intent(this, Mode.HIDDEN)) }
            }
            Mode.HIDDEN -> footerLeft.text = "숨긴 앱은 홈과 모든 앱에 나오지 않습니다."
            Mode.PICK_DEFAULT -> Unit
        }
        rows.onPageChanged = {
            pageText.text = if (rows.pageCount <= 1) "" else "${rows.page + 1} / ${rows.pageCount}   ›"
            updateIndex()
        }
        setContentView(root)
    }

    private fun refresh() {
        AppIcons.clearIfPackagesChanged(this)
        val apps = AppStore.loadAndPrune(this, prefs)
        val hidden = prefs.hidden
        val favs = prefs.favorites
        val default = prefs.defaultApp
        val icons = prefs.showIcons
        val scale = prefs.textScale

        shown = when (mode) {
            Mode.ALL -> apps.filter { it.key !in hidden }
            Mode.HIDDEN -> apps.filter { it.key in hidden }
            // 이북 앱을 먼저, 그다음 나머지
            Mode.PICK_DEFAULT -> apps.filter { it.key !in hidden }.sortedBy { if (AppStore.isEbook(it)) 0 else 1 }
        }
        titleCount.text = if (mode == Mode.PICK_DEFAULT) "" else shown.size.toString()
        if (mode == Mode.ALL) footerLeft.text = "숨긴 앱 ${hidden.size}"

        rows.setRows(shown.map { app ->
            {
                when (mode) {
                    Mode.ALL -> {
                        val mark = when {
                            app.key == default -> text("기본", 17f)
                            app.key in favs -> text("★", 18f)
                            else -> null
                        }
                        appRow(app, app.label, 27, 27f * scale, showIcon = icons, right = mark).apply {
                            setOnClickListener { AppStore.launch(this@AppListActivity, app, prefs) }
                            setOnLongClickListener { showAppMenu(app) { refresh() }; true }
                        }
                    }
                    Mode.PICK_DEFAULT -> {
                        val current = app.key == default
                        appRow(app, app.label, 28, 28f * scale, if (current) Ui.heavy else Ui.regular,
                            showIcon = icons, right = if (current) text("✓", 24f, Ui.bold) else null).apply {
                            setOnClickListener { prefs.defaultApp = app.key; finish() }
                        }
                    }
                    Mode.HIDDEN -> {
                        val show = text("보이기", 20f).apply {
                            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
                            setPadding(dp(16), dp(14), 0, dp(14))
                            setOnClickListener { prefs.unhide(app.key); refresh() }
                        }
                        appRow(app, app.label, 27, 27f * scale, color = Ui.GRAY, showIcon = icons, right = show)
                    }
                }
            }
        })
    }

    /** 오른쪽 색인: 있는 첫 글자만, 지금 페이지의 글자는 진하게. 누르면 그 글자가 있는 페이지로. */
    private fun updateIndex() {
        val col = indexCol ?: return
        col.removeAllViews()
        if (shown.isEmpty() || rows.perPage <= 0) return
        val initials = shown.map { AppStore.initial(it.label) }
        val letters = initials.distinct()
        val from = rows.page * rows.perPage
        val onPage = initials.subList(from, minOf(initials.size, from + rows.perPage)).toSet()
        val cell = if (col.height > 0) minOf(dp(42), col.height / letters.size) else dp(42)
        letters.forEach { letter ->
            col.addView(text(letter, 18f, if (letter in onPage) Ui.heavy else Ui.regular,
                if (letter in onPage) Ui.BLACK else 0xFF999999.toInt()).apply {
                gravity = Gravity.CENTER
                setOnClickListener { rows.showPage(rows.pageOf(initials.indexOf(letter))) }
            }, lp(MATCH, cell))
        }
    }

    companion object {
        private const val EXTRA_MODE = "mode"

        fun intent(context: Context, mode: Mode): Intent =
            Intent(context, AppListActivity::class.java).putExtra(EXTRA_MODE, mode.name)
    }
}
