package com.woody.pebbledesk

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import java.time.DayOfWeek
import java.time.format.TextStyle
import kotlin.math.roundToInt

/** ⑧ 설정. 회색 소제목으로 묶고 값은 오른쪽에 같은 굵기로 쓴다. 켜짐은 검정, 꺼짐은 진회색, `›` 는 다음 화면. */
class SettingsActivity : EinkActivity() {
    private lateinit var list: LinearLayout
    /** 줄을 넣는 곳: 보통 [list], 가로 화면의 설정 첫 화면은 왼쪽·오른쪽 칸 중 하나 */
    private lateinit var col: LinearLayout
    /** 업데이트 줄 자리(상태가 바뀌면 이 줄만 다시 그린다) */
    private var updateSlot: LinearLayout? = null
    /** 최신 판 줄을 눌러 다시 확인하는 중(끝나서 여전히 최신이면 알린다) */
    private var announceLatest = false
    /** 받는 중 창(받기가 끝나거나 실패하면 닫는다) */
    private var progressDialog: Dialog? = null
    /** 자동 추가 안내의 쪽들(그 화면에서만) */
    private lateinit var guide: PagedBlocks
    /** 어느 화면인가: 설정(null), 독서([PAGE_READING]), 자동 추가 안내([PAGE_GUIDE]), 화면([PAGE_DISPLAY]), 글자 · 간격([PAGE_TEXT]) */
    private val page by lazy { intent.getStringExtra(EXTRA_PAGE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = vbox()
        val title = when (page) {
            PAGE_GUIDE -> R.string.auto_books_guide
            PAGE_READING -> R.string.reading_settings
            PAGE_DISPLAY -> R.string.section_display
            PAGE_TEXT -> R.string.text_spacing
            else -> R.string.settings
        }
        val titleRow = titleRow(getString(title)) { finish() }
        root.addView(titleRow)
        root.addView(hline(2))
        if (page == PAGE_GUIDE) {
            // 한 화면에 다 들어가지 않아 쪽으로 나눈다. 쪽 표시 `‹ 1 / 2 ›` 는 제목 줄 오른쪽 끝(아래 줄이 없는 화면).
            guide = PagedBlocks(this)
            val guidePage = text("", 21f).apply { setPadding(dp(24), dp(12), 0, dp(12)); pagesClick(guide) }
            titleRow.addView(View(this), lp(0, 1, 1f))
            titleRow.addView(guidePage)
            guide.onPageChanged = { guidePage.text = pageLabel(guide) }
            root.addView(guide, lp(MATCH, 0, 1f))
        } else {
            list = vbox()
            col = list
            root.addView(list, lp(MATCH, 0, 1f))
        }
        // 맨 아래 지금 판, 늘 보인다(업데이트 줄은 새 판이 있으면 새 판 번호를 보여 지금 판이 안 보인다)
        if (page == null) root.addView(text("Pebble Desk ${appVersion()}", 15f, color = Ui.LIGHT_GRAY).apply {
            gravity = Gravity.CENTER
        }, lp(MATCH, dp(44)))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        when (page) {
            PAGE_GUIDE -> renderGuide()
            PAGE_READING -> renderReading()
            PAGE_DISPLAY -> renderDisplay()
            PAGE_TEXT -> renderText()
            else -> {
                render()
                Update.onChange = { renderUpdate(); closeProgress(); showInstallFailure(); showLatest() }
                closeProgress()
                showInstallFailure()
                Update.checkIfStale(this)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (page == null) Update.onChange = null
    }

    override fun onDestroy() {
        progressDialog?.dismiss()
        super.onDestroy()
    }

    private fun render() {
        list.removeAllViews()
        AppStore.loadAndPrune(this, prefs)
        // 가로 화면이면 묶음째 두 칸으로: 왼쪽 독서·화면·언어와 정보, 오른쪽 앱 관리(묶음은 쪼개지 않는다)
        val right = splitColumns()
        val left = col

        // 맨 위 소제목 없이 독서(읽고 있는 책·자동 추가·오늘 읽은 시간·하루 목표·한 주 시작·독서 기록). 줄이 많아 한 화면에 모은다.
        row(getString(R.string.reading_settings), null, next = true) { open(PAGE_READING) }

        // 화면(글자 · 간격 · 색 · 앱 아이콘 · 시계)은 하위 화면에 모아 첫 화면을 짧게 둔다(줄 간격을 키워도 잘리지 않게).
        row(getString(R.string.section_display), null, next = true) { open(PAGE_DISPLAY) }
        // 언어는 첫 화면에 둔다(다른 언어 화면에서도 바로 찾게). 언어 이름은 바꾸지 않는다(어느 언어에서도 알아볼 수 있게). 바꾸면 이 화면부터 다시 만든다.
        val languages = listOf("한국어", "English")
        row(getString(R.string.language), languages[if (prefs.english) 1 else 0], next = true) {
            choose(getString(R.string.language), languages, if (prefs.english) 1 else 0) {
                if ((it == 1) != prefs.english) {
                    prefs.english = it == 1
                    recreate()
                }
            }
        }

        col = right
        section(getString(R.string.section_apps))
        row(getString(R.string.favorites), prefs.favoriteList.size.toString(), next = true) {
            startActivity(FavoritesActivity.intent(this))
        }
        row(getString(R.string.hidden_apps), prefs.hidden.size.toString(), next = true) {
            startActivity(AppListActivity.intent(this, AppListActivity.Mode.HIDDEN))
        }
        row(getString(R.string.set_default_home), null, next = true) { openHomeSettings() }
        row(getString(R.string.device_settings), null, next = true) {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }

        // 정보
        col = left
        section(getString(R.string.section_info))
        row(getString(R.string.device_info), Build.MODEL, next = true) { startActivity(DeviceInfoActivity.intent(this)) }
        updateSlot = vbox().also { col.addView(it) }
        renderUpdate()
    }

    /**
     * 업데이트 줄: 판 번호만. 최신이면 지금 판(회색), 새 판이 있으면 `1.5.1 ›`(받는 동안은 `›` 없이),
     * 모르면 `확인 중` · `인터넷 없음`(누르면 연결 확인 안내) · `서버 응답 없음`(회색, 누르면 잠시 뒤 다시 해 보라는 창). 새 판을 누르면 바뀐 점과 `설치` 창, 그 밖에는 누르면 다시 확인한다
     * (최신 판을 눌러 다시 확인해도 최신이면 `최신 버전이 설치되어 있습니다` 창).
     */
    private fun renderUpdate() {
        val slot = updateSlot ?: return
        slot.removeAllViews()
        val label = getString(R.string.update)
        when (val s = Update.state) {
            is Update.State.Available ->
                row(label, s.release.version, next = true, last = true, into = slot) { offerUpdate(s.release) }
            is Update.State.Downloading -> row(label, s.release.version, last = true, into = slot) {}
            // 설치 창을 띄운 뒤: 창을 그냥 벗어났을 수 있어 다시 누르면 그 창을 다시 띄운다.
            is Update.State.Installing -> row(label, s.release.version, next = true, last = true, into = slot) {
                if (!Update.resumeInstall(this, s.release.version)) offerUpdate(s.release)
            }
            Update.State.Latest -> row(label, appVersion(), on = false, last = true, into = slot) {
                announceLatest = true
                Update.check(this)
            }
            Update.State.Unknown, Update.State.Checking ->
                row(label, getString(R.string.update_checking), on = false, last = true, into = slot) {}
            // 누르면 아직 연결이 없으면 연결을 확인하라고 알리고, 연결됐으면 다시 확인한다.
            Update.State.Offline -> row(label, getString(R.string.update_offline_short), on = false, last = true, into = slot) {
                if (Update.online(this)) Update.check(this)
                else Sheet(this).header(getString(R.string.update_offline), getString(R.string.update_offline_desc)).show()
            }
            // 누르면 잠시 뒤 다시 해 보라고 알리고, 뒤에서 다시 확인해 둔다.
            Update.State.Failed -> row(label, getString(R.string.update_no_response), on = false, last = true, into = slot) {
                Sheet(this).header(getString(R.string.update_server_title), getString(R.string.update_server_desc)).show()
                Update.check(this)
            }
        }
    }

    /**
     * 받는 중 창: 머리 `Pebble Desk 1.5.1` · `받는 중`, 막대. 전자잉크라 막대는 25% 씩 네 번만 바꾸고, 금방 받아도
     * [Update.MIN_SHOW_MS](5초)에 걸쳐 채운다(받은 만큼보다 앞서 가지 않는다). 받기가 끝나면 닫히고 설치 창이 뜬다.
     */
    private fun showProgress(release: Update.Release) {
        val bar = ProgressLine(this)
        val dialog = Sheet(this).header("Pebble Desk ${release.version}", getString(R.string.update_downloading))
            .note(bar).locked().show()
        progressDialog = dialog
        val step = Update.MIN_SHOW_MS / 4
        var ticks = 0
        val tick = object : Runnable {
            override fun run() {
                if (!dialog.isShowing) return
                ticks++
                val shown = minOf(ticks * 25, Update.downloaded / 25 * 25)
                if (shown != bar.percent) bar.percent = shown
                bar.postDelayed(this, step)
            }
        }
        bar.postDelayed(tick, step)
    }

    /** 받기가 끝났으면(설치 창을 띄웠거나 실패) 받는 중 창을 닫는다 */
    private fun closeProgress() {
        if (Update.state is Update.State.Downloading) return
        progressDialog?.dismiss()
        progressDialog = null
    }

    /** 최신 판 줄을 눌러 다시 확인했는데 여전히 최신이면 알린다(새 판이 나왔으면 줄이 그 판으로 바뀐다) */
    private fun showLatest() {
        val s = Update.state
        if (!announceLatest || s == Update.State.Checking) return
        announceLatest = false
        if (s == Update.State.Latest) Sheet(this).header("Pebble Desk ${appVersion()}", getString(R.string.update_is_latest)).show()
    }

    /** `설치` 뒤 받기·확인·설치에 실패했으면 알린다(이 화면을 떠나 있었으면 돌아올 때) */
    private fun showInstallFailure() {
        if (!Update.takeInstallFailure()) return
        Sheet(this).header(getString(R.string.update_install_failed), getString(R.string.update_install_failed_desc)).show()
    }

    /** 새 판의 바뀐 점(릴리스 설명, 화면에서 [NOTES_LINES] 줄까지)과 `설치` */
    private fun offerUpdate(release: Update.Release) {
        Sheet(this).header("Pebble Desk ${release.version}", releaseNotes(release.notes), lines = NOTES_LINES)
            .item(getString(R.string.update_install), bold = true) { installUpdate(release) }
            .show()
    }

    /**
     * 릴리스 설명(마크다운)을 창에 넣을 글로: 절 제목(`## 새로 생긴 것`)은 굵은 검정, 항목(`- `)은 `· ` 로 시작하는 회색,
     * 두 번째 절부터는 위에 반 줄을 띄운다. `**`·`` ` `` 는 던다. 줄 수는 창이 [NOTES_LINES] 로 자른다(넘으면 끝에 줄임표).
     * 릴리스 설명은 이 줄 수에 맞춰 쓴다(CLAUDE.md 릴리스 절).
     */
    private fun releaseNotes(markdown: String): CharSequence? {
        val out = SpannableStringBuilder()
        for (raw in markdown.lines()) {
            val line = raw.replace("**", "").replace("`", "").trim()
            if (line.isEmpty()) continue
            val heading = line.startsWith("#")
            val text = when {
                heading -> line.trimStart('#', ' ')
                line.startsWith("- ") || line.startsWith("* ") -> "· " + line.drop(2)
                else -> line
            }
            if (out.isNotEmpty()) {
                out.append("\n")
                if (heading) out.append(" \n", RelativeSizeSpan(0.5f), 0)
            }
            if (heading) out.append(text, StyleSpan(Typeface.BOLD), 0).also {
                it.setSpan(ForegroundColorSpan(Ui.BLACK), it.length - text.length, it.length, 0)
            } else out.append(text)
            // 어차피 [NOTES_LINES] 줄만 보이므로 아주 긴 설명은 앞부분만 다룬다.
            if (out.length > NOTES_SCAN_CHARS) break
        }
        return out.takeIf { it.isNotEmpty() }
    }

    /** 이 앱에 '알 수 없는 앱 설치'가 허용돼 있으면 받아서 설치하고(이 판의 설치 창을 띄워 둔 적이 있으면 그 창을), 아니면 허용 화면 안내 */
    private fun installUpdate(release: Update.Release) {
        if (Update.resumeInstall(this, release.version)) return
        if (packageManager.canRequestPackageInstalls()) {
            Update.install(this, release)
            showProgress(release)
            return
        }
        Sheet(this).header(getString(R.string.update_allow_title), getString(R.string.update_allow_desc))
            .item(getString(R.string.update_allow_open), bold = true) {
                runCatching {
                    startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                }
            }.show()
    }

    private fun open(page: String) = startActivity(Intent(this, SettingsActivity::class.java).putExtra(EXTRA_PAGE, page))

    /** 화면: 글자 · 간격, 색, 앱 아이콘, 시계, 기기 상단 바 */
    private fun renderDisplay() {
        list.removeAllViews()
        // 글자 크기·줄 간격은 한 화면에서 슬라이더로 보며 고른다.
        row(getString(R.string.text_spacing), "${prefs.textScalePercent}% · ${prefs.rowScalePercent}%", next = true) { open(PAGE_TEXT) }
        // 색: 흑백 / 컬러. 바꾸면 이 화면부터 다시 만든다(색은 화면을 만들 때 정해진다).
        val palettes = listOf(Palette.MONO to R.string.palette_mono, Palette.COLOR to R.string.palette_color)
        val names = palettes.map { getString(it.second) }
        val now = palettes.indexOfFirst { it.first == prefs.palette }
        row(getString(R.string.color), names[now], next = true) {
            choose(getString(R.string.color), names, now) {
                if (palettes[it].first != prefs.palette) { prefs.palette = palettes[it].first; recreate() }
            }
        }
        toggle(getString(R.string.app_icons), prefs.showIcons, hint = getString(R.string.app_icons_hint), redraw = ::renderDisplay) { prefs.showIcons = it }
        val clocks = listOf(getString(R.string.clock24), getString(R.string.clock12))
        row(getString(R.string.clock), clocks[if (prefs.clock24h) 0 else 1], next = true) {
            choose(getString(R.string.clock), clocks, if (prefs.clock24h) 0 else 1) {
                prefs.clock24h = it == 0; renderDisplay()
            }
        }
        toggle(getString(R.string.status_bar), prefs.showStatusBar, hint = getString(R.string.status_bar_hint), redraw = ::renderDisplay) { prefs.showStatusBar = it }
    }

    /** 독서 화면: 읽고 있는 책, 읽는 책 자동 추가, 오늘 읽은 시간, 하루 목표, 한 주 시작, 독서 기록 */
    private fun renderReading() {
        list.removeAllViews()
        // 가로 화면이면 두 칸으로: 왼쪽 책·자동 추가, 오른쪽 읽은 시간·목표·독서 기록(일곱 줄이 낮은 화면을 넘지 않게)
        val right = splitColumns()
        // 읽고 있는 앱은 책을 정할 때 함께 고른다(책 없이 바꾸려면 앱을 길게 눌러 '읽고 있는 앱으로 설정').
        // 맨 앞 책 제목이 길면 줄임표로 자르고 `외 3권` 은 늘 보인다.
        val books = BookShelf.list(this)
        val booksText = books.firstOrNull()?.title ?: getString(R.string.none)
        val booksTail = if (books.size > 1) getString(R.string.books_more, books.size - 1) else null
        row(getString(R.string.now_reading), booksText, on = books.isNotEmpty(), next = true, tail = booksTail) {
            startActivity(BooksActivity.intent(this))
        }
        // 읽는 책 자동 추가 = 안드로이드 접근성에서 이 앱이 켜져 있음(앱이 대신 켜고 끌 수 없다).
        // 켜져 있든 꺼져 있든 무엇을 읽는지 안내 창(켜고 끄기는 그 창의 `접근성 설정 열기`).
        val watching = ReaderWatchService.isEnabled(this)
        row(
            getString(R.string.auto_books), getString(if (watching) R.string.on else R.string.off),
            on = watching, next = true,
        ) { autoBooksSheet(watching) }
        // 무엇을 어떻게 읽는지(서재 화면에 보이는 것만, 앱마다 되는 화면) 안내
        row(getString(R.string.auto_books_guide), null, next = true) {
            startActivity(Intent(this, SettingsActivity::class.java).putExtra(EXTRA_PAGE, PAGE_GUIDE))
        }
        // 오늘 읽은 시간 = 사용 기록 액세스 허용. 켜고 끄기 모두 그 시스템 화면에서 한다.
        col = right
        val access = ReadingLog.hasAccess(this)
        row(
            getString(R.string.reading_time), getString(if (access) R.string.on else R.string.off),
            on = access, next = true, hint = getString(R.string.reading_time_hint),
        ) { ReadingLog.openAccessSettings(this) }
        if (access) {
            // 정해 둔 값 다음에 `직접 입력`. 직접 넣은 값이면 그 줄에 값을 함께 보이고 ✓. 시간은 독서 기록과 같은 꼴(`1시간 30분`)
            val goals = listOf(0, 15, 20, 30, 45, 60)
            val goalText = { m: Int -> if (m == 0) getString(R.string.none) else duration(m * 60_000L) }
            val goal = prefs.readingGoalMin
            row(getString(R.string.daily_goal), goalText(goal), next = true) {
                val custom = goal !in goals
                val options = goals.map(goalText) + getString(R.string.custom_goal).let {
                    if (custom) "$it · ${goalText(goal)}" else it
                }
                choose(getString(R.string.daily_goal), options, if (custom) goals.size else goals.indexOf(goal)) {
                    if (it < goals.size) { prefs.readingGoalMin = goals[it]; renderReading() } else askGoal()
                }
            }
            // 독서 기록 달력과 '이번 주'의 시작 요일
            val days = listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY)
            val dayNames = days.map { it.getDisplayName(TextStyle.FULL, resources.configuration.locales[0]) }
            val first = days.indexOf(prefs.firstDayOfWeek)
            row(getString(R.string.week_starts), dayNames[first], next = true) {
                choose(getString(R.string.week_starts), dayNames, first) { prefs.firstDayOfWeek = days[it]; renderReading() }
            }
        }
        row(getString(R.string.reading_stats), null, next = true, last = true) { startActivity(ReadingActivity.intent(this)) }
    }

    /**
     * 글자 · 간격 화면: 글자 크기(70~150%, 기기 글꼴 크기에 곱한다)와 줄 간격(80~150%)을 10% 단위 슬라이더로 고르고, 아래 미리 보기(자주 쓰는 앱 목록 몇 줄)가
     * 손을 뗄 때 바로 바뀐다. 다른 화면은 돌아갈 때 새 값으로 다시 만든다(EinkActivity). 이 화면의 제목·슬라이더는 다시 들어올 때 바뀐다.
     */
    private fun renderText() {
        list.removeAllViews()
        // 가로 화면이면 왼쪽에 슬라이더, 오른쪽에 미리 보기(세로로 쌓으면 미리 보기가 화면 아래로 넘친다)
        val right = splitColumns()
        val preview = vbox()
        fun showPreview() {
            preview.removeAllViews()
            val apps = AppStore.load(this).let { all -> favoriteApps(all, prefs).ifEmpty { all } }.take(PREVIEW_ROWS)
            apps.forEachIndexed { i, app ->
                if (i > 0) preview.addView(hline(1, Ui.DIVIDER))
                preview.addView(appRow(app, app.label, Ui.ROW_ICON_DP, Ui.ROW_SP, showIcon = prefs.showIcons), lp(MATCH, rowDp(Ui.ROW_DP)))
            }
        }
        fun slider(label: Int, steps: List<Int>, value: Int, apply: (Int) -> Unit) {
            val valueText = text("$value%", 20f, Ui.medium)
            col.addView(hbox().apply {
                setPadding(dp(Ui.MARGIN), dp(18), dp(Ui.MARGIN), 0)
                addView(text(getString(label), ROW_LABEL_SP), lp(0, WRAP, 1f))
                addView(valueText)
            })
            col.addView(StepSlider(this, steps, value, onStep = { valueText.text = "$it%" }) { apply(it); showPreview() })
        }
        slider(R.string.text_size, HomePrefs.TEXT_SCALES, prefs.textScalePercent) {
            prefs.textScalePercent = it; Ui.textScale = it / 100f
        }
        // 기기 글꼴 크기를 바꿔 두었으면 그 위에 곱한다는 것을 알린다.
        val deviceFont = (resources.configuration.fontScale * 100).roundToInt()
        if (deviceFont != 100) col.addView(text(getString(R.string.text_size_device, deviceFont), 15f, color = Ui.GRAY).apply {
            setPadding(dp(Ui.MARGIN), 0, dp(Ui.MARGIN), 0)
        })
        slider(R.string.line_spacing, HomePrefs.ROW_SCALES, prefs.rowScalePercent) {
            prefs.rowScalePercent = it; Ui.rowScale = it / 100f
        }
        col = right
        section(getString(R.string.preview))
        col.addView(hline(1))
        col.addView(preview)
        showPreview()
    }

    /** 하루 목표 직접 입력(분). 범위 밖이면 가장 가까운 값으로 */
    private fun askGoal() {
        val initial = prefs.readingGoalMin.takeIf { it > 0 }?.toString().orEmpty()
        Sheet(this).header(getString(R.string.daily_goal), getString(R.string.custom_goal_hint, GOAL_MIN, GOAL_MAX))
            .input(initial, numeric = true) { v ->
                v.toLongOrNull()?.let { prefs.readingGoalMin = it.coerceIn(GOAL_MIN, GOAL_MAX).toInt(); renderReading() }
            }.show()
    }

    /**
     * 자동 추가 안내(두 쪽): 1쪽은 앱마다 공통인 안내(읽는 방식, 진행률, 표지·제목)와 지원하는 앱 이름,
     * 2쪽은 안 되는 게 있는 앱만 이름과 설명. 2쪽에 다 들어가지 않으면 다음 쪽으로 이어진다.
     */
    private fun renderGuide() {
        fun para(res: Int, top: Int, bottom: Int) = text(getString(res), 17f, color = Ui.BLACK, lines = 6).apply {
            setLineSpacing(0f, 1.2f)
            setPadding(dp(Ui.MARGIN), dp(top), dp(Ui.MARGIN), dp(bottom))
        }
        val apps = listOf(
            R.string.guide_kel to R.string.guide_kel_desc,
            R.string.guide_kyobo to R.string.guide_kyobo_desc,
            R.string.guide_millie to null,
            R.string.guide_millie_mobile to null,
            R.string.guide_aladin to R.string.guide_aladin_desc,
            R.string.guide_bookers to null,
            R.string.guide_ridi to R.string.guide_ridi_desc,
            R.string.guide_yes24lib to null,
            R.string.guide_myyes to R.string.guide_myyes_desc,
            R.string.guide_yes24ebook to null,
            R.string.guide_moon to R.string.guide_moon_desc,
            R.string.guide_kindle to R.string.guide_kindle_desc,
            R.string.guide_libby to R.string.guide_libby_desc,
            R.string.guide_neo to R.string.guide_neo_desc,
        )
        val blocks = mutableListOf(
            PagedBlocks.Block(listOf(para(R.string.guide_intro, 12, 4))),
            PagedBlocks.Block(listOf(para(R.string.guide_progress_note, 4, 4))),
            PagedBlocks.Block(listOf(para(R.string.guide_cover_note, 4, 12))),
            PagedBlocks.Block(listOf(
                sectionView(getString(R.string.guide_apps)),
                text(apps.joinToString("  ·  ") { getString(it.first) }, 18f, Ui.bold, lines = 6).apply {
                    setLineSpacing(0f, 1.2f)
                    setPadding(dp(Ui.MARGIN), dp(4), dp(Ui.MARGIN), dp(10))
                },
            )),
        )
        // 안 되는 게 있는 앱: 앱 하나가 한 묶음(위 선·이름·설명). 소제목은 첫 앱과 함께 새 쪽에서, 마지막 앱 아래에 선.
        val limited = apps.mapNotNull { (name, desc) -> desc?.let { name to it } }
        limited.forEachIndexed { i, (name, desc) ->
            val views = mutableListOf<View>()
            if (i == 0) views += sectionView(getString(R.string.guide_limits))
            views += hline(1, Ui.DIVIDER)
            views += text(getString(name), 19f, Ui.bold).apply { setPadding(dp(Ui.MARGIN), dp(8), dp(Ui.MARGIN), 0) }
            views += text(getString(desc), 17f, color = Ui.GRAY, lines = 5).apply {
                setPadding(dp(Ui.MARGIN), dp(2), dp(Ui.MARGIN), dp(8))
            }
            if (i == limited.lastIndex) views += hline(1, Ui.DIVIDER)
            blocks += PagedBlocks.Block(views, newPage = i == 0)
        }
        guide.setBlocks(blocks)
    }

    override fun onSwipe(toLeft: Boolean): Boolean {
        if (page != PAGE_GUIDE || guide.pageCount <= 1) return false
        if (toLeft) guide.next() else guide.prev()
        return true
    }

    /**
     * 자동 추가 창: 무엇을 읽는지 + `접근성 설정 열기`. 안드로이드 13+ 이면 `앱 정보 열기` 도 두고,
     * 꺼져 있으면([watching] 이 false) 허용이 안 보일 때 안내(BOOX Leaf2)와 13+ 의 제한된 설정 안내를 설명 아래 단락으로 붙인다.
     * 서비스가 한 번이라도 연결된 적이 있으면(이미 켜 본 것. 제한된 설정도 풀려 있다) 두 안내와 `앱 정보 열기` 는 두지 않는다.
     */
    private fun autoBooksSheet(watching: Boolean) {
        val turnedOnBefore = ReaderWatchService.lifeLog(this).first > 0
        val restricted = ReaderWatchService.mayBeRestricted && !turnedOnBefore
        val hints = if (watching || turnedOnBefore) emptyList() else listOfNotNull(R.string.allow_hint, R.string.restricted_hint.takeIf { restricted })
        val desc = (listOf(R.string.auto_books_desc) + hints).joinToString("\n\n") { getString(it) }
        val sheet = Sheet(this).header(getString(R.string.auto_books), desc)
            .item(getString(R.string.open_accessibility), bold = true) { ReaderWatchService.openSettings(this) }
        if (restricted) sheet.item(getString(R.string.open_app_info)) { ReaderWatchService.openAppInfo(this) }
        sheet.show()
    }

    /**
     * 가로 화면이면 [list] 를 두 칸(사이에 옅은 세로 선)으로 나눠 [col] 을 왼쪽 칸으로 두고 오른쪽 칸을 돌려준다.
     * 세로 화면이면 [col] 도 오른쪽도 [list] 그대로(한 칸).
     */
    private fun splitColumns(): LinearLayout {
        col = list
        if (!landscape()) return list
        list.orientation = LinearLayout.HORIZONTAL
        val left = vbox()
        val right = vbox()
        list.addView(left, lp(0, WRAP, 1f))
        list.addView(vline(12))
        list.addView(right, lp(0, WRAP, 1f))
        col = left
        return right
    }

    private fun section(title: String) = col.addView(sectionView(title))

    private fun sectionView(title: String) = text(title, 16f, color = Ui.LIGHT_GRAY).apply {
        setPadding(dp(Ui.MARGIN), dp(14), dp(Ui.MARGIN), dp(2))
    }

    /**
     * 설정 한 줄. [hint] 는 이름 뒤에 작은 회색 글자로 붙는 보조 설명. 값은 같은 굵기로, 꺼짐은 진회색.
     * 값은 이름 뒤 남는 폭만큼 오른쪽에 붙여 보이고 넘치면 줄임표로 자른다. [tail](`외 3권`)과 `›` 는 자르지 않는다.
     */
    private fun row(
        label: String, value: String?, on: Boolean = true, next: Boolean = false, last: Boolean = false,
        hint: String? = null, tail: String? = null, into: LinearLayout = col, action: () -> Unit,
    ) {
        val color = if (on) Ui.BLACK else Ui.GRAY
        val row = hbox().apply {
            setPadding(dp(Ui.MARGIN), 0, dp(Ui.MARGIN), 0)
            addView(text(label, ROW_LABEL_SP))
            if (hint != null) addView(text(hint, 15f, color = Ui.LIGHT_GRAY), lp(WRAP, WRAP).apply { marginStart = dp(10) })
            addView(text(value.orEmpty(), 20f, Ui.medium, color).apply { gravity = Gravity.END }, lp(0, WRAP, 1f).apply { marginStart = dp(16) })
            // 값과의 사이는 전과 같은 글자 간격(`외 3권` 앞 한 칸, `›` 앞 두 칸)
            val end = buildString {
                tail?.let { append(if (value.isNullOrEmpty()) it else " $it") }
                if (next) append(if (isEmpty() && value.isNullOrEmpty()) "›" else "  ›")
            }
            if (end.isNotEmpty()) addView(text(end, 20f, Ui.medium, color))
            setOnClickListener { action() }
        }
        into.addView(row, lp(MATCH, rowDp(Ui.SETTING_ROW_DP)))
        if (!last) into.addView(hline(1, Ui.DIVIDER))
    }

    /** 켜짐/꺼짐 줄. 누르면 바꾸고 [redraw] 로 그 화면을 다시 그린다. */
    private fun toggle(label: String, value: Boolean, hint: String? = null, redraw: () -> Unit, set: (Boolean) -> Unit) =
        row(label, getString(if (value) R.string.on else R.string.off), on = value, hint = hint) { set(!value); redraw() }

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

    companion object {
        private const val EXTRA_PAGE = "page"
        private const val PAGE_TEXT = "text"
        private const val PAGE_DISPLAY = "display"
        /** 설정 줄 이름 글자(sp) */
        private const val ROW_LABEL_SP = 22f
        private const val PAGE_READING = "reading"
        private const val PAGE_GUIDE = "guide"
        private const val GOAL_MIN = 1L
        private const val GOAL_MAX = 300L
        /** 바뀐 점 창에 보이는 릴리스 설명 줄 수(크레마 페블에서 한 줄 한국어 약 28자). 릴리스 설명은 이 안에 맞춰 쓴다. */
        const val NOTES_LINES = 18
        private const val NOTES_SCAN_CHARS = 2_000
        /** 글자 · 간격 미리 보기 줄 수 */
        private const val PREVIEW_ROWS = 4
    }
}

/**
 * 단계 슬라이더(글자 · 간격): 가로 선 위 단계마다 점, 고른 값까지 검정, 동그란 손잡이, 아래에 단계 값.
 * 누르거나 밀면 가장 가까운 단계로 옮기고([onStep]), 손을 떼면 [onCommit]. 단계가 바뀔 때만 다시 그린다(전자잉크).
 */
private class StepSlider(
    context: Context,
    private val steps: List<Int>,
    value: Int,
    private val onStep: (Int) -> Unit,
    private val onCommit: (Int) -> Unit,
) : View(context) {
    private var index = steps.indexOf(value).coerceAtLeast(0)
    private var committed = index
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = context.dpf(14); textAlign = Paint.Align.CENTER; typeface = Ui.regular
    }
    private val pad = context.dpf(Ui.MARGIN + 6)

    private fun x(i: Int) = pad + (width - 2 * pad) * i / (steps.size - 1)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), context.dp(HEIGHT_DP))

    override fun onDraw(canvas: Canvas) {
        val y = context.dpf(TRACK_Y_DP)
        paint.strokeWidth = context.dpf(2)
        paint.color = Ui.DIVIDER
        canvas.drawLine(x(0), y, x(steps.size - 1), y, paint)
        paint.color = Ui.BLACK
        paint.strokeWidth = context.dpf(3)
        canvas.drawLine(x(0), y, x(index), y, paint)
        steps.forEachIndexed { i, v ->
            paint.color = if (i <= index) Ui.BLACK else Ui.LIGHT_GRAY
            canvas.drawCircle(x(i), y, context.dpf(4), paint)
            labelPaint.color = if (i == index) Ui.BLACK else Ui.LIGHT_GRAY
            labelPaint.typeface = if (i == index) Ui.bold else Ui.regular
            canvas.drawText(v.toString(), x(i), y + context.dpf(38), labelPaint)
        }
        paint.color = Ui.BLACK
        canvas.drawCircle(x(index), y, context.dpf(12), paint)
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE -> {
                parent.requestDisallowInterceptTouchEvent(true)
                val i = ((event.x - pad) / ((width - 2 * pad) / (steps.size - 1))).let { Math.round(it) }.coerceIn(0, steps.size - 1)
                if (i != index) { index = i; invalidate(); onStep(steps[i]) }
            }
            // 손을 떼거나 터치가 취소되면 보이는 값으로 확정한다(손잡이와 저장 값이 어긋나지 않게).
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL ->
                if (index != committed) { committed = index; onCommit(steps[index]) }
        }
        return true
    }

    companion object {
        const val HEIGHT_DP = 72
        const val TRACK_Y_DP = 26
    }
}

/** 받는 중 막대: 연회색 줄 위에 받은 만큼 검정(박스 없이, 높이 [HEIGHT_DP]) */
private class ProgressLine(context: Context) : View(context) {
    var percent = 0
        set(v) { field = v; invalidate() }
    private val paint = Paint()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), context.dp(HEIGHT_DP))

    override fun onDraw(canvas: Canvas) {
        paint.color = Ui.DIVIDER
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.color = Ui.BLACK
        canvas.drawRect(0f, 0f, width * percent / 100f, height.toFloat(), paint)
    }

    companion object {
        const val HEIGHT_DP = 8
    }
}
