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
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

/** ① 홈: 맨 위 두 줄 · 지금 읽는 책과 기본 앱 · 자주 쓰는 앱 · 하단 줄 */
class HomeActivity : EinkActivity() {
    private lateinit var topBar: TopBarView
    private lateinit var hero: FrameLayout
    private lateinit var favRows: PagedRows
    private lateinit var dots: TextView

    private var apps: List<AppEntry> = emptyList()
    private var resumed = false
    private var pausedAt = 0L
    /** 홈이 보이는 채로 화면이 꺼졌으면 true. 다시 켜질 때 기본 앱 바로 열기에 쓴다. */
    private var sleptOnHome = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateStatus()
    }
    private val lightObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = updateStatus()
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // 화면이 꺼지면 홈도 곧 멈추므로, 방금 멈췄어도 홈이 보이고 있던 것으로 본다.
            if (resumed || SystemClock.elapsedRealtime() - pausedAt < 1500) sleptOnHome = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildViews()
        registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        // 기기를 켠 직후 처음 뜬 홈이면 기본 앱 바로 열기
        if (savedInstanceState == null && SystemClock.elapsedRealtime() < BOOT_WINDOW_MS && prefs.openDefaultOnWake) {
            sleptOnHome = true
        }
    }

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        hideStatusBar()
        if (sleptOnHome) {
            sleptOnHome = false
            if (prefs.openDefaultOnWake && openDefault()) return
        }
        updateStatus()
        refresh()
    }

    override fun onPause() {
        resumed = false
        pausedAt = SystemClock.elapsedRealtime()
        super.onPause()
    }

    /**
     * 상태 줄은 홈이 보이는 동안 계속 갱신한다. 빠른 설정 창이 위에 떠 있으면 홈은 멈춤(pause) 상태지만
     * 뒤에 보이므로, 거기서 와이파이·조명 등을 바꾸면 바로 반영되어야 한다.
     */
    override fun onStart() {
        super.onStart()
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
        favRows = PagedRows(this, dp(62 * prefs.textScale)).apply {
            gravity = Gravity.BOTTOM
            header = listOf(
                text("자주 쓰는 앱", 19f, Ui.bold).apply {
                    layoutParams = lp(MATCH, WRAP)
                    setPadding(dp(Ui.MARGIN), dp(30), dp(Ui.MARGIN), dp(12))
                },
                hline(2),
            )
        }
        root.addView(favRows, lp(MATCH, 0, 1f))

        root.addView(hline(1, inset = false))
        val footer = FrameLayout(this)
        footer.addView(text("모든 앱  ›", 24f, Ui.bold).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Ui.MARGIN), 0, dp(32), 0)
            setOnClickListener { startActivity(AppListActivity.intent(this@HomeActivity, AppListActivity.Mode.ALL)) }
        }, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.START))
        dots = text("", 15f).apply { gravity = Gravity.CENTER }
        footer.addView(dots, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.CENTER))
        footer.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_settings)
            contentDescription = "설정"
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
        AppIcons.clearIfPackagesChanged(this)
        apps = AppStore.loadAndPrune(this, prefs)
        val hidden = prefs.hidden
        val default = defaultApp()
        val favKeys = prefs.favorites
        var favs = apps.filter { it.key in favKeys && it.key !in hidden && it.key != default?.key }
        if (prefs.sortByCount) favs = favs.sortedByDescending { prefs.openCount(it.key) }

        buildHero(default)

        val icons = prefs.showIcons
        val scale = prefs.textScale
        favRows.rowHeightPx = dp(62 * scale)
        favRows.setRows(
            if (favs.isEmpty()) listOf {
                text("모든 앱에서 앱을 길게 눌러 자주 쓰는 앱에 넣으세요", 18f, color = Ui.LIGHT_GRAY, lines = 2).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(Ui.MARGIN), 0, dp(Ui.MARGIN), 0)
                }
            } else favs.map { app ->
                {
                    appRow(app, app.label, 28, 29f * scale, showIcon = icons).apply {
                        setOnClickListener { AppStore.launch(this@HomeActivity, app, prefs) }
                        setOnLongClickListener { showAppMenu(app) { refresh() }; true }
                    }
                }
            }
        )
    }

    private fun defaultApp(): AppEntry? = prefs.defaultApp?.let { key -> apps.find { it.key == key } }

    private fun openDefault(): Boolean {
        val app = defaultApp() ?: AppStore.load(this).find { it.key == prefs.defaultApp } ?: return false
        AppStore.launch(this, app, prefs)
        return true
    }

    private fun pickDefault() = startActivity(AppListActivity.intent(this, AppListActivity.Mode.PICK_DEFAULT))

    /** 지금 읽는 책(A안) 또는 기본 앱 크게 또는 기본 앱 정하기 */
    private fun buildHero(default: AppEntry?) {
        hero.removeAllViews()
        val coverW = dp(160)
        val book = if (prefs.showNowReading) NowReadingClient.read(this, coverW) else null
        val onTap = View.OnClickListener { if (default != null) AppStore.launch(this, default, prefs) else pickDefault() }
        val onLong = View.OnLongClickListener { if (default != null) showAppMenu(default) { refresh() } else pickDefault(); true }

        val view: View = when {
            book != null -> hbox().apply {
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
                    } else text("기본 앱 정하기  ›", 24f, Ui.bold, Ui.LIGHT_GRAY)
                )
                addView(info, lp(0, coverH + dp(2), 1f).apply { marginStart = dp(24) })
            }
            default != null -> hbox().apply {
                setPadding(dp(Ui.MARGIN), dp(18), dp(Ui.MARGIN), dp(8))
                if (prefs.showIcons) addView(ImageView(context).apply {
                    setImageBitmap(AppIcons.gray(context, default, dp(62)))
                }, lp(dp(62), dp(62)).apply { marginEnd = dp(20) })
                addView(text("${default.label}  ›", 72f, Ui.heavy).apply { letterSpacing = -0.03f })
            }
            else -> vbox().apply {
                setPadding(dp(Ui.MARGIN), dp(22), dp(Ui.MARGIN), dp(8))
                addView(text("기본 앱 정하기  ›", 42f, Ui.bold, Ui.LIGHT_GRAY))
                addView(text("책 읽을 때 쓰는 앱을 고르면 여기에 크게 보입니다", 19f, color = 0xFF777777.toInt()),
                    lp(WRAP, WRAP).apply { topMargin = dp(12) })
            }
        }
        view.setOnClickListener(onTap)
        view.setOnLongClickListener(onLong)
        hero.addView(view, FrameLayout.LayoutParams(MATCH, WRAP))
    }

    companion object {
        /** 부팅 뒤 이 시간 안에 처음 뜬 홈이면 '켤 때 기본 앱 바로 열기'를 적용한다. */
        private const val BOOT_WINDOW_MS = 3 * 60_000L
    }
}
