package com.woody.pebbledesk

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.wifi.WifiManager
import android.os.Build
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** ① 홈: 맨 위 두 줄 · 지금 읽는 책과 읽고 있는 앱 · 자주 쓰는 앱 · 하단 줄 */
class HomeActivity : EinkActivity() {
    private lateinit var topBar: TopBarView
    /** 읽고 있는 책 자리. 자주 쓰는 앱 높이가 고정이라 남는 높이를 모두 차지하고, 책이 한 권이면 표지가 그만큼 커진다. */
    private lateinit var hero: FrameLayout
    private var heroHeight = 0
    /** 자주 쓰는 앱 제목 줄: 왼쪽 제목, 오른쪽 끝에 페이지(여러 쪽일 때만) */
    private lateinit var favHeader: LinearLayout
    private lateinit var favPage: TextView
    private lateinit var favLine: View
    private lateinit var favRows: PagedRows
    /** 하단 가운데: 오늘 읽은 시간 · 연속 독서일(켜 두었고 권한이 있을 때만) */
    private lateinit var readingText: TextView

    private var apps: List<AppEntry> = emptyList()
    /** 지난번에 그린 홈의 내용. 같으면 다시 만들지 않는다(빠른 설정을 닫거나 앱에서 돌아올 때 불필요한 전자잉크 갱신을 막는다). */
    private var shownState: List<Any?>? = null

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateStatus()
            // 날이 바뀌면 오늘 읽은 시간을 0 부터. 부팅 직후 잠금이 풀리면 그때부터 읽은 시간을 셀 수 있다.
            if (intent.action == Intent.ACTION_DATE_CHANGED || intent.action == Intent.ACTION_USER_UNLOCKED) updateReading()
        }
    }
    private val lightObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = updateStatus()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildViews()
    }

    override fun onResume() {
        super.onResume()
        hideStatusBar()
        refresh()
        // 읽은 시간은 다른 앱에서 돌아올 때만 바뀐다(레이아웃 때의 refresh 에서는 다시 세지 않는다).
        updateReading()
    }

    /**
     * 상태 줄은 홈이 보이는 동안 계속 갱신한다. 빠른 설정 창이 위에 떠 있으면 홈은 멈춤(pause) 상태지만
     * 뒤에 보이므로, 거기서 와이파이·조명 등을 바꾸면 바로 반영되어야 한다.
     */
    override fun onStart() {
        super.onStart()
        topBar.use24Hour = prefs.clock24h
        registerReceiver(statusReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_USER_UNLOCKED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        })
        Device.lightKeys.forEach { contentResolver.registerContentObserver(Settings.System.getUriFor(it), false, lightObserver) }
        updateStatus()
    }

    override fun onStop() {
        runCatching { unregisterReceiver(statusReceiver) }
        contentResolver.unregisterContentObserver(lightObserver)
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }

    /** 홈 버튼을 다시 누르면 첫 페이지로 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (::favRows.isInitialized) favRows.showPage(0)
    }

    /** 홈에서 뒤로 가기는 아무것도 하지 않는다. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = Unit

    override fun onSwipe(toLeft: Boolean): Boolean {
        if (favRows.pageCount <= 1) return false
        if (toLeft) favRows.next() else favRows.prev()
        return true
    }

    /** 홈에서는 안드로이드 상단바를 숨긴다. 위에서 아래로 쓸면 잠깐 나온다. */
    private fun hideStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    /** 화면이 꺼져 있을 때는 그리지 않는다(슬립화면 위에 덧그리지 않도록). 켜지면 SCREEN_ON 으로 다시 부른다. */
    private fun updateStatus() {
        if (!::topBar.isInitialized || !getSystemService(PowerManager::class.java).isInteractive) return
        topBar.update(DeviceStatus.read(this))
    }

    private fun buildViews() {
        // 흰 바탕은 배경(background)으로 두지 않고 직접 칠한다. 크레마는 앱이 바뀔 때마다 배경이 있는 뷰 묶음(창의 맨 바깥 포함)에
        // 명암 필터를 다시 걸고 다시 그려, 홈으로 돌아올 때 전자잉크가 한 번 더 깜빡인다. 창 배경도 그래서 비운다.
        val root = object : LinearLayout(this) {
            override fun dispatchDraw(canvas: Canvas) {
                canvas.drawColor(Color.WHITE)
                super.dispatchDraw(canvas)
            }
        }.apply { orientation = LinearLayout.VERTICAL }
        topBar = TopBarView(this).apply { setOnClickListener { Device.openQuickSettings(this@HomeActivity) } }
        root.addView(topBar, lp(MATCH, WRAP))

        hero = FrameLayout(this)
        root.addView(hero, lp(MATCH, 0, 1f))
        // 높이가 정해지면(보통 처음 한 번) 이번 그리기를 건너뛰고 그 높이에 맞춰 책 자리를 채운 뒤 그린다.
        // (다음 차례로 미루면 빈 자리를 한 번 그린 뒤 다시 그리게 되어 전자잉크가 깜빡인다.)
        hero.viewTreeObserver.addOnPreDrawListener {
            if (hero.height == heroHeight) true else { shownState = null; refresh(); false }
        }

        // 자주 쓰는 앱: 책 수와 상관없이 늘 목록 세 줄(격자는 그 높이 안에 두 줄) 높이
        // 페이지 표시는 제목과 같은 글자·여백으로 오른쪽 끝에. 누르면 다음 쪽(마지막 쪽에서는 처음으로).
        favPage = text("", 13f, Ui.bold, Ui.GRAY).apply {
            setPadding(dp(24), dp(18), dp(Ui.MARGIN), dp(8))
            setOnClickListener { favRows.next() }
        }
        favHeader = hbox().apply {
            layoutParams = lp(MATCH, WRAP)
            gravity = Gravity.TOP
            addView(sectionTitle(getString(R.string.favorites), topDp = 18), lp(0, WRAP, 1f))
            addView(favPage, lp(WRAP, WRAP))
        }
        favLine = hline(1)
        favRows = PagedRows(this, dp(Ui.ROW_DP), fixedRows = FAV_ROWS).apply { header = listOf(favHeader, favLine) }
        root.addView(favRows, lp(MATCH, WRAP))

        root.addView(hline(1, inset = false))
        val footer = FrameLayout(this)
        footer.addView(text(getString(R.string.all_apps_link), 24f, Ui.bold).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Ui.MARGIN), 0, dp(32), 0)
            setOnClickListener { startActivity(AppListActivity.intent(this@HomeActivity, AppListActivity.Mode.ALL)) }
        }, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.START))
        readingText = text("", 15f, color = Ui.GRAY).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), 0)
            setOnClickListener { startActivity(ReadingActivity.intent(this@HomeActivity)) }
        }
        footer.addView(readingText, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.CENTER))
        footer.addView(PictureView(this).apply {
            drawable = getDrawable(R.drawable.ic_settings)
            contentDescription = getString(R.string.settings)
            setPadding(dp(24), dp((Ui.FOOTER_DP - 28) / 2), dp(Ui.MARGIN), dp((Ui.FOOTER_DP - 28) / 2))
            setOnClickListener { startActivity(Intent(this@HomeActivity, SettingsActivity::class.java)) }
        }, FrameLayout.LayoutParams(dp(24 + 32 + Ui.MARGIN), MATCH, Gravity.END))
        root.addView(footer, lp(MATCH, dp(Ui.FOOTER_DP)))

        favRows.onPageChanged = {
            val n = favRows.pageCount
            favPage.text = if (n <= 1) "" else "${favRows.page + 1} / $n   ›"
        }
        setContentView(root)
        window.setBackgroundDrawable(null)
    }

    /** 앱 목록과 설정을 다시 읽어 그린다. 홈으로 돌아올 때마다 부른다. */
    private fun refresh() {
        val iconsChanged = AppIcons.clearIfPackagesChanged(this)
        apps = AppStore.loadAndPrune(this, prefs)
        // 홈에는 앞 몇 권만 보인다(나머지는 읽고 있는 책 목록에만).
        val books = BookShelf.list(this).take(BookShelf.HOME)
        // 맨 앞 책의 앱이 곧 읽고 있는 앱이다.
        val reading = books.firstOrNull()?.app?.let(::findApp)
        // 자주 쓰는 앱은 직접 넣고 뺀 그대로 보인다(위의 책·앱과 상관없다).
        val favs = favoriteApps(apps, prefs)
        val icons = prefs.showIcons
        val grid = prefs.favGrid

        heroHeight = hero.height
        val state = listOf(books, reading, favs, icons, grid, heroHeight)
        if (!iconsChanged && state == shownState) return
        shownState = state

        buildHero(books, reading)

        favRows.setGrid(if (grid) GRID_ROWS else 0)
        favRows.setRows(
            if (favs.isEmpty()) listOf {
                text(getString(R.string.favorites_empty), 18f, color = Ui.LIGHT_GRAY, lines = 2).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(Ui.MARGIN), 0, dp(Ui.MARGIN), 0)
                }
            } else if (grid) favs.chunked(GRID_COLUMNS).map { line -> { gridRow(line) } }
            else favs.map { app ->
                {
                    appRow(app, app.label, Ui.ROW_ICON_DP, Ui.ROW_SP, showIcon = icons).apply {
                        setOnClickListener { AppStore.launch(this@HomeActivity, app) }
                        setOnLongClickListener { showAppMenu(app) { refresh() }; true }
                    }
                }
            }
        )
    }

    /**
     * 격자 한 줄: 흑백 아이콘 아래 이름 한 줄. 줄 높이(목록 높이를 두 줄로 나눈 값)에 맞춰 아이콘 크기를 정한다.
     * 격자에서는 '앱 아이콘' 설정과 상관없이 아이콘을 그린다.
     */
    private fun gridRow(line: List<AppEntry>): View = hbox().apply {
        // 함께 읽는 책 줄과 같은 칸(좌우 여백 + 칸 사이 간격)으로 나누고, 칸마다 아이콘과 이름을 가운데에 맞춘다.
        gravity = Gravity.TOP
        setPadding(dp(Ui.MARGIN), dp(14), dp(Ui.MARGIN), 0)
        val icon = (favRows.rowHeightPx - dp(GRID_LABEL_DP)).coerceIn(dp(32), dp(GRID_ICON_MAX_DP))
        line.forEachIndexed { i, app ->
            addView(vbox().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                addView(picture(AppIcons.gray(context, app, icon)), lp(icon, icon))
                addView(text(app.label, 13f, lines = 2).apply { gravity = Gravity.CENTER }, lp(MATCH, WRAP).apply { topMargin = dp(6) })
                setOnClickListener { AppStore.launch(this@HomeActivity, app) }
                setOnLongClickListener { showAppMenu(app) { refresh() }; true }
            }, lp(0, WRAP, 1f).apply { if (i > 0) marginStart = dp(GAP_DP) })
        }
        // 덜 찬 줄도 칸 폭이 같도록 빈 칸을 채운다.
        repeat(GRID_COLUMNS - line.size) { addView(View(context), lp(0, 1, 1f).apply { marginStart = dp(GAP_DP) }) }
    }

    /** 구역 제목(함께 읽는 책 · 자주 쓰는 앱): 작은 회색 굵은 글자, 아래에 가는 선 */
    private fun sectionTitle(title: String, topDp: Int) = text(title, 13f, Ui.bold, Ui.GRAY).apply {
        layoutParams = lp(MATCH, WRAP)
        setPadding(dp(Ui.MARGIN), dp(topDp), dp(Ui.MARGIN), dp(8))
    }

    private fun findApp(key: String): AppEntry? = apps.find { it.key == key }

    /** 하단 가운데의 `오늘 42분 · 5일째`. 꺼 두었거나 사용 기록 권한이 없으면 비운다. */
    private fun updateReading() {
        // 어느 책인지는 알 수 없어 하루 합계만 센다.
        val summary = if (prefs.readingTime) ReadingLog.summary(this, ReadingLog.readerPackages(this)) else null
        val line = summary?.let {
            val today = getString(R.string.reading_today, duration(it.todayMs))
            // 연속일은 이틀째부터
            if (it.streak >= 2) today + "  ·  " + getString(R.string.reading_streak, it.streak) else today
        } ?: ""
        if (readingText.text.toString() != line) readingText.text = line
    }

    /** 그 책의 앱을 연다. 앱을 아직 안 정했거나 지워졌으면 고르러 간다. */
    private fun openBook(book: ShelfBook) {
        val app = book.app?.let(::findApp)
        if (app != null) AppStore.launch(this, app)
        else startActivity(AppListActivity.intent(this, AppListActivity.Mode.PICK_READER, book.id))
    }

    /**
     * 읽고 있는 책 자리. 없으면 같은 모양의 스켈레톤, 한 권이면 남는 높이를 채우는 큰 표지,
     * 여러 권이면 남는 높이에 맞춘 맨 앞 책 + 아래 '함께 읽는 책' 줄.
     */
    private fun buildHero(books: List<ShelfBook>, reading: AppEntry?) {
        hero.removeAllViews()
        if (heroHeight <= 0) return
        val view: View = when (books.size) {
            0 -> skeleton(coverWidth())
            1 -> mainBook(books.first(), reading, coverWidth())
            else -> {
                val also = vbox().apply {
                    addView(sectionTitle(getString(R.string.also_reading), topDp = 28))
                    addView(hline(1))
                    addView(hbox().apply {
                        gravity = Gravity.TOP
                        setPadding(dp(Ui.MARGIN), dp(14), dp(Ui.MARGIN), 0)
                        books.drop(1).forEachIndexed { i, book ->
                            addView(smallBook(book), lp(0, WRAP, 1f).apply { if (i > 0) marginStart = dp(GAP_DP) })
                        }
                        // 두 권·세 권일 때도 표지 폭이 같도록 빈 칸을 채운다.
                        repeat(BookShelf.HOME - books.size) { addView(View(context), lp(0, 1, 1f).apply { marginStart = dp(GAP_DP) }) }
                    })
                }
                // 함께 읽는 책 줄 높이를 먼저 재고, 남는 높이를 맨 앞 책 표지에 준다.
                also.measure(
                    View.MeasureSpec.makeMeasureSpec(hero.width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                )
                vbox().apply {
                    addView(mainBook(books.first(), reading, coverWidth(reserved = also.measuredHeight, min = COVER_SMALLER_DP)))
                    addView(also)
                }
            }
        }
        hero.addView(view, FrameLayout.LayoutParams(MATCH, WRAP))
    }

    /**
     * 맨 앞 책 표지 폭: 책 자리 높이에서 위아래 여백과 그 아래에 놓일 높이([reserved], 함께 읽는 책 줄)를 뺀 만큼.
     * 너무 작거나 커지지 않게 [min]dp ~ COVER_MAX_DP.
     */
    private fun coverWidth(reserved: Int = 0, min: Int = COVER_DP): Int {
        val coverH = heroHeight - dp(HERO_PAD_TOP_DP) - dp(HERO_PAD_BOTTOM_DP) - reserved
        return (coverH * 2 / 3).coerceIn(dp(min), dp(COVER_MAX_DP))
    }

    /** 책이 없을 때: 표지 자리에 `+` 상자, 제목·저자·앱 자리에 회색 막대. 누르면 책 추가. */
    private fun skeleton(coverW: Int): View = hbox().apply {
        gravity = Gravity.TOP
        setPadding(dp(Ui.MARGIN), dp(HERO_PAD_TOP_DP), dp(Ui.MARGIN), 0)
        val coverH = coverW * 3 / 2
        addView(DashedBox(context), lp(coverW + dp(2), coverH + dp(2)))
        val info = vbox()
        // 책이 들어오면 채워질 자리: 제목 · 저자 · (아래) 읽고 있는 앱. 폭은 비율로(그리기 전에 정해지게 weight 로).
        fun bar(widthFrac: Float, heightDp: Int) = hbox().apply {
            addView(View(context).apply { setBackgroundColor(Ui.DIVIDER) }, lp(0, dp(heightDp), widthFrac))
            addView(View(context), lp(0, dp(heightDp), 1f - widthFrac))
        }
        info.addView(bar(0.7f, 26), lp(MATCH, WRAP).apply { topMargin = dp(14) })
        info.addView(bar(0.45f, 14), lp(MATCH, WRAP).apply { topMargin = dp(14) })
        info.addView(bar(0.4f, 18), lp(MATCH, WRAP).apply { topMargin = dp(30) })
        addView(info, lp(0, coverH + dp(2), 1f).apply { marginStart = dp(24) })
        setOnClickListener { startActivity(BookSearchActivity.intent(this@HomeActivity)) }
    }

    /** 표지 자리: 연한 회색으로 채우고 점선 테두리, 가운데에 + */
    private class DashedBox(context: Context) : View(context) {
        private val fill = Paint().apply { color = Ui.DIVIDER }
        private val plus = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Ui.GRAY
            strokeWidth = context.dpf(3)
            strokeCap = Paint.Cap.ROUND
        }
        private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Ui.LIGHT_GRAY
            strokeWidth = context.dpf(1.5f)
            pathEffect = DashPathEffect(floatArrayOf(context.dpf(6), context.dpf(5)), 0f)
        }
        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
            val inset = border.strokeWidth / 2
            canvas.drawRect(inset, inset, width - inset, height - inset, border)
            val cx = width / 2f
            val cy = height / 2f
            val arm = width * 0.11f
            canvas.drawLine(cx - arm, cy, cx + arm, cy, plus)
            canvas.drawLine(cx, cy - arm, cx, cy + arm, plus)
        }
    }

    /** 맨 앞 책: 왼쪽 표지, 오른쪽 제목·저자와 읽고 있는 앱 */
    private fun mainBook(book: ShelfBook, reading: AppEntry?, coverW: Int): View = hbox().apply {
        gravity = Gravity.TOP
        setPadding(dp(Ui.MARGIN), dp(HERO_PAD_TOP_DP), dp(Ui.MARGIN), 0)
        val coverH = coverW * 3 / 2
        addView(coverView(book, coverW), lp(coverW + dp(2), coverH + dp(2)))
        val info = vbox()
        info.addView(text(book.title, 34f, Ui.heavy, lines = 2), lp(MATCH, WRAP).apply { topMargin = dp(10) })
        if (book.author.isNotBlank()) {
            info.addView(text(book.author, 16f, color = Ui.GRAY), lp(MATCH, WRAP).apply { topMargin = dp(10) })
        }
        // 이북 앱 서재에서 읽어 온 진행률·반납일(읽는 책 자동 추가를 켰을 때), 한 줄씩
        bookStatus(book).forEachIndexed { i, s ->
            info.addView(text(s, 15f, color = Ui.GRAY), lp(MATCH, WRAP).apply { topMargin = dp(if (i == 0) 6 else 2) })
        }
        info.addView(
            if (reading != null) hbox().apply {
                if (prefs.showIcons) addView(picture(AppIcons.gray(context, reading, dp(26))), lp(dp(26), dp(26)).apply { marginEnd = dp(10) })
                addView(text("${reading.label}  ›", 20f, Ui.bold))
            } else text(getString(R.string.pick_reader_link), 20f, Ui.bold, Ui.LIGHT_GRAY),
            // 제목·저자 바로 아래에 붙여 한 덩어리로 읽히게 한다.
            lp(WRAP, WRAP).apply { topMargin = dp(26) }
        )
        addView(info, lp(0, coverH + dp(2), 1f).apply { marginStart = dp(24) })
        setOnClickListener { openBook(book) }
        setOnLongClickListener { showBookMenu(book); true }
    }

    /** 함께 읽는 책: 작은 표지 옆에 제목과 그 책의 앱. 누르면 맨 앞으로 올리고 그 앱을 연다. */
    private fun smallBook(book: ShelfBook): View = hbox().apply {
        gravity = Gravity.TOP
        val w = dp(SMALL_COVER_DP)
        addView(coverView(book, w), lp(w + dp(2), w * 3 / 2 + dp(2)))
        val info = vbox()
        info.addView(text(book.title, 16f, Ui.bold, lines = 2))
        book.app?.let(::findApp)?.let {
            info.addView(text(it.label, 13f, color = Ui.GRAY), lp(WRAP, WRAP).apply { topMargin = dp(4) })
        }
        bookStatus(book).forEach { info.addView(text(it, 12f, color = Ui.GRAY), lp(WRAP, WRAP).apply { topMargin = dp(2) }) }
        addView(info, lp(0, WRAP, 1f).apply { marginStart = dp(10) })
        setOnClickListener {
            BookShelf.moveToFront(this@HomeActivity, book.id)
            openBook(book)
        }
        setOnLongClickListener { showBookMenu(book); true }
    }

    private fun coverView(book: ShelfBook, widthPx: Int): View = coverImage(BookShelf.cover(this, book, widthPx))

    /** 책을 길게 누르면: 읽고 있는 앱 바꾸기 / 제목 고치기 / 저자 입력·고치기 / 표지 다시 찾기 / 다 읽음 / 책 빼기. 머리에 작은 표지. */
    private fun showBookMenu(book: ShelfBook) {
        // 이북 앱이 저자를 보여 주지 않아(읽는 화면 메뉴로 들어온 책 등) 비어 있으면 `저자 입력`, 있으면 `저자 고치기`.
        val authorLabel = if (book.author.isBlank()) R.string.add_author else R.string.edit_author
        Sheet(this).header(
            book.title, book.app?.let(::findApp)?.let { getString(R.string.reading_in, it.label) },
            image = BookShelf.cover(this, book, dp(SMALL_COVER_DP)),
        )
            .item(getString(R.string.change_reader)) {
                startActivity(AppListActivity.intent(this, AppListActivity.Mode.PICK_READER, book.id))
            }
            .item(getString(R.string.edit_title)) {
                Sheet(this).header(getString(R.string.edit_title)).input(book.title) { BookShelf.setTitle(this, book.id, it); refresh() }.show()
            }
            .item(getString(authorLabel)) {
                Sheet(this).header(getString(authorLabel)).input(book.author) { BookShelf.setAuthor(this, book.id, it); refresh() }.show()
            }
            .item(getString(R.string.find_cover)) { startActivity(BookSearchActivity.intent(this, replace = book)) }
            .item(getString(R.string.finish_book)) { BookShelf.finish(this, book.id); refresh() }
            .item(getString(R.string.remove_book)) {
                confirm(getString(R.string.remove_confirm), book.title, getString(R.string.remove_book)) { BookShelf.remove(this, book.id); refresh() }
            }
            .show()
    }

    companion object {
        /** 자주 쓰는 앱 고정 줄 수 */
        private const val FAV_ROWS = 3
        /** 맨 앞 책 표지는 남는 높이에 맞춰 COVER_DP~COVER_MAX_DP(여러 권이면 COVER_SMALLER_DP 부터) */
        private const val COVER_DP = 160
        private const val COVER_MAX_DP = 230
        private const val COVER_SMALLER_DP = 136
        private const val HERO_PAD_TOP_DP = 26
        private const val HERO_PAD_BOTTOM_DP = 22
        /** 함께 읽는 책 표지 폭 */
        private const val SMALL_COVER_DP = 50

        /** 자주 쓰는 앱 격자: 4칸 × 2줄(목록과 같은 높이 안에), 아이콘 최대 크기, 아이콘 밖에 필요한 높이(이름·여백) */
        private const val GRID_COLUMNS = 4
        /** 함께 읽는 책·격자 칸 사이 간격(두 줄의 칸을 맞춘다) */
        private const val GAP_DP = 12
        private const val GRID_ROWS = 2
        private const val GRID_ICON_MAX_DP = 56
        private const val GRID_LABEL_DP = 46

    }
}
