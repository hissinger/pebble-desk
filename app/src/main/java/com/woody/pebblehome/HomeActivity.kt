package com.woody.pebblehome

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.wifi.WifiManager
import android.os.Build
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
import android.widget.ImageView
import android.widget.TextView

/** ① 홈: 맨 위 두 줄 · 지금 읽는 책과 읽고 있는 앱 · 자주 쓰는 앱 · 하단 줄 */
class HomeActivity : EinkActivity() {
    private lateinit var topBar: TopBarView
    private lateinit var hero: FrameLayout
    /** 지금 읽는 책이 없을 때 읽고 있는 앱을 빈 공간 가운데에 두는 자리(자주 쓰는 앱 제목 위까지) */
    private lateinit var centerHero: FrameLayout
    private lateinit var favTitle: TextView
    private lateinit var favLine: View
    /** 가운데 자리의 최소 높이를 확보하려고 자주 쓰는 앱 머리 맨 위에 넣는 빈 칸 */
    private lateinit var centerSpacer: View
    private lateinit var favRows: PagedRows
    private lateinit var dots: TextView

    private var apps: List<AppEntry> = emptyList()
    /** 지난번에 그린 홈의 내용. 같으면 다시 만들지 않는다(빠른 설정을 닫거나 앱에서 돌아올 때 불필요한 전자잉크 갱신을 막는다). */
    private var shownState: List<Any?>? = null

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateStatus()
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
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        })
        contentResolver.registerContentObserver(Settings.System.getUriFor("screen_brightness"), false, lightObserver)
        contentResolver.registerContentObserver(Settings.System.getUriFor("warm_light"), false, lightObserver)
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
        val root = vbox()
        topBar = TopBarView(this).apply { setOnClickListener { Crema.openQuickSettings(this@HomeActivity) } }
        root.addView(topBar, lp(MATCH, WRAP))

        hero = FrameLayout(this)
        root.addView(hero, lp(MATCH, WRAP))

        // 자주 쓰는 앱이 적으면 제목과 목록을 하단 줄 바로 위에 붙이고, 남는 공간은 책과 목록 사이로 보낸다.
        favTitle = text(getString(R.string.favorites), 19f, Ui.bold).apply {
            layoutParams = lp(MATCH, WRAP)
            setPadding(dp(Ui.MARGIN), dp(30), dp(Ui.MARGIN), dp(12))
        }
        favLine = hline(2)
        centerSpacer = View(this).apply { layoutParams = lp(MATCH, dp(CENTER_MIN_DP)) }
        favRows = PagedRows(this, dp(Ui.ROW_DP)).apply { gravity = Gravity.BOTTOM }
        centerHero = FrameLayout(this)
        val middle = FrameLayout(this)
        middle.addView(favRows, FrameLayout.LayoutParams(MATCH, MATCH))
        middle.addView(centerHero, FrameLayout.LayoutParams(MATCH, 0))
        root.addView(middle, lp(MATCH, 0, 1f))
        // 가운데 자리는 자주 쓰는 앱 제목 바로 위까지. 목록 높이가 바뀔 때마다 맞춘다.
        favRows.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val top = if (favTitle.parent === favRows) favTitle.top else 0
            if (centerHero.layoutParams.height != top) centerHero.post {
                centerHero.layoutParams = centerHero.layoutParams.apply { height = top }
            }
        }

        root.addView(hline(1, inset = false))
        val footer = FrameLayout(this)
        footer.addView(text(getString(R.string.all_apps_link), 24f, Ui.bold).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Ui.MARGIN), 0, dp(32), 0)
            setOnClickListener { startActivity(AppListActivity.intent(this@HomeActivity, AppListActivity.Mode.ALL)) }
        }, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.START))
        dots = text("", 15f).apply { gravity = Gravity.CENTER }
        footer.addView(dots, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.CENTER))
        footer.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_settings)
            contentDescription = getString(R.string.settings)
            setPadding(dp(24), dp(22), dp(Ui.MARGIN), dp(22))
            setOnClickListener { startActivity(Intent(this@HomeActivity, SettingsActivity::class.java)) }
        }, FrameLayout.LayoutParams(dp(24 + 32 + Ui.MARGIN), MATCH, Gravity.END))
        root.addView(footer, lp(MATCH, dp(72)))

        favRows.onPageChanged = {
            val n = favRows.pageCount
            dots.text = if (n <= 1) "" else (0 until n).joinToString("  ") { if (it == favRows.page) "●" else "○" }
        }
        setContentView(root)
    }

    /** 앱 목록과 설정을 다시 읽어 그린다. 홈으로 돌아올 때마다 부른다. */
    private fun refresh() {
        val iconsChanged = AppIcons.clearIfPackagesChanged(this)
        apps = AppStore.loadAndPrune(this, prefs)
        val hidden = prefs.hidden
        val default = readingApp()
        val favKeys = prefs.favorites
        var favs = apps.filter { it.key in favKeys && it.key !in hidden && it.key != default?.key }
        if (prefs.sortByCount) favs = favs.sortedByDescending { prefs.openCount(it.key) }
        val book = NowReadingStore.read(this, dp(COVER_DP))
        val icons = prefs.showIcons

        val state = listOf(default, favs, book, icons)
        if (!iconsChanged && state == shownState) return
        shownState = state

        buildHero(default, book)

        favRows.setRows(
            if (favs.isEmpty()) listOf {
                text(getString(R.string.favorites_empty), 18f, color = Ui.LIGHT_GRAY, lines = 2).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(Ui.MARGIN), 0, dp(Ui.MARGIN), 0)
                }
            } else favs.map { app ->
                {
                    appRow(app, app.label, Ui.ROW_ICON_DP, Ui.ROW_SP, showIcon = icons).apply {
                        setOnClickListener { AppStore.launch(this@HomeActivity, app, prefs) }
                        setOnLongClickListener { showAppMenu(app) { refresh() }; true }
                    }
                }
            }
        )
    }

    private fun readingApp(): AppEntry? = prefs.readingApp?.let { key -> apps.find { it.key == key } }

    private fun pickReader() = startActivity(AppListActivity.intent(this, AppListActivity.Mode.PICK_READER))

    /** 지금 읽는 책(A안) 또는 읽고 있는 앱 크게 또는 읽는 책 정하기 */
    private fun buildHero(default: AppEntry?, book: NowReading?) {
        hero.removeAllViews()
        centerHero.removeAllViews()
        val coverW = dp(COVER_DP)
        val onTap = View.OnClickListener { if (default != null) AppStore.launch(this, default, prefs) else pickReader() }

        if (book == null) {
            showCenterHero(default, onTap)
            return
        }
        favRows.header = listOf(favTitle, favLine)
        val view = hbox().apply {
                gravity = Gravity.TOP
                setPadding(dp(Ui.MARGIN), dp(26), dp(Ui.MARGIN), 0)
                val coverH = coverW * book.cover.height / book.cover.width
                addView(ImageView(context).apply {
                    setImageBitmap(book.cover)
                    scaleType = ImageView.ScaleType.FIT_XY
                    setBackgroundColor(Ui.BLACK)
                    setPadding(dp(1), dp(1), dp(1), dp(1))
                }, lp(coverW + dp(2), coverH + dp(2)))
                val info = vbox()
                info.addView(text(book.title, 34f, Ui.heavy, lines = 2), lp(MATCH, WRAP).apply { topMargin = dp(10) })
                if (book.author.isNotBlank()) {
                    info.addView(text(book.author, 18f, color = Ui.GRAY), lp(MATCH, WRAP).apply { topMargin = dp(10) })
                }
                info.addView(View(context), lp(MATCH, 0, 1f))
                info.addView(
                    if (default != null) hbox().apply {
                        if (prefs.showIcons) addView(ImageView(context).apply {
                            setImageBitmap(AppIcons.gray(context, default, dp(42)))
                        }, lp(dp(42), dp(42)).apply { marginEnd = dp(14) })
                        addView(text("${default.label}  ›", 36f, Ui.heavy))
                    } else text(getString(R.string.pick_reader_link), 24f, Ui.bold, Ui.LIGHT_GRAY),
                    // 표지 아랫선보다 조금 올려 제목·저자와 한 덩어리로 보이게 한다.
                    lp(WRAP, WRAP).apply { bottomMargin = dp(28) }
                )
                addView(info, lp(0, coverH + dp(2), 1f).apply { marginStart = dp(24) })
        }
        view.setOnClickListener(onTap)
        view.setOnLongClickListener { showBookMenu(book, default); true }
        hero.addView(view, FrameLayout.LayoutParams(MATCH, WRAP))
    }

    /** 책을 길게 누르면: 책 바꾸기 / 읽는 앱 바꾸기 / 책 지우기 */
    private fun showBookMenu(book: NowReading, default: AppEntry?) {
        Sheet(this).header(book.title, default?.let { getString(R.string.reading_in, it.label) })
            .item(getString(R.string.change_book)) { startActivity(BookSearchActivity.intent(this)) }
            .item(getString(R.string.change_reader)) { startActivity(AppListActivity.intent(this, AppListActivity.Mode.PICK_READER)) }
            .item(getString(R.string.clear_book)) { NowReadingStore.clear(this); refresh() }
            .show()
    }

    /** 지금 읽는 책이 없을 때(B안): 읽고 있는 앱의 큰 아이콘과 이름을 상단과 자주 쓰는 앱 사이 가운데에 */
    private fun showCenterHero(default: AppEntry?, onTap: View.OnClickListener) {
        favRows.header = listOf(centerSpacer, favTitle, favLine)
        val block = vbox().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(12), dp(24), dp(12))
            if (default != null) {
                if (prefs.showIcons) addView(ImageView(context).apply {
                    setImageBitmap(AppIcons.gray(context, default, dp(100)))
                }, lp(dp(100), dp(100)).apply { bottomMargin = dp(14) })
                addView(text(default.label, 40f, Ui.heavy).apply { gravity = Gravity.CENTER }, lp(WRAP, WRAP))
                addView(text(getString(R.string.tap_to_read), 17f, color = Ui.LIGHT_GRAY), lp(WRAP, WRAP).apply { topMargin = dp(8) })
                setOnClickListener(onTap)
                setOnLongClickListener { showAppMenu(default) { refresh() }; true }
            } else {
                addView(text(getString(R.string.set_book_link), 30f, Ui.bold, Ui.LIGHT_GRAY), lp(WRAP, WRAP))
                addView(text(getString(R.string.set_book_hint), 18f, color = Ui.GRAY, lines = 2).apply {
                    gravity = Gravity.CENTER
                }, lp(WRAP, WRAP).apply { topMargin = dp(12) })
                setOnClickListener { startActivity(BookSearchActivity.intent(this@HomeActivity)) }
            }
        }
        // 읽고 있는 앱이 있으면 그 아래에 작은 '읽는 책 정하기'
        if (default != null) block.addView(text(getString(R.string.set_book_link), 18f, Ui.bold, Ui.LIGHT_GRAY).apply {
            setPadding(dp(16), dp(22), dp(16), dp(10))
            setOnClickListener { startActivity(BookSearchActivity.intent(this@HomeActivity)) }
        }, lp(WRAP, WRAP))
        centerHero.addView(block, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
    }

    companion object {
        /** 지금 읽는 책 표지 폭 */
        private const val COVER_DP = 160

        /** 가운데 자리 최소 높이: 아이콘 100 + 이름 + 안내 + 여백 */
        private const val CENTER_MIN_DP = 220
    }
}
