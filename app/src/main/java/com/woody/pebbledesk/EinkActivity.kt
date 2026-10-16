package com.woody.pebbledesk

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 전자잉크용 화면 공통: 화면 전환 애니메이션을 끄고, 좌우로 밀거나 페이지 버튼을 누르면 [onSwipe] 를 부른다.
 * 밀기로 끝난 터치는 아래 뷰에 취소로 전달해 눌림(클릭)으로 처리되지 않게 한다.
 */
abstract class EinkActivity : Activity() {
    protected lateinit var prefs: HomePrefs
    private var downX = 0f
    private var downY = 0f
    /** 이 화면을 만들 때 쓴 언어. 설정에서 바뀌었으면 돌아올 때 다시 만든다. */
    private var createdEnglish = false
    /** 이 화면을 만들 때 쓴 글자 크기·줄 간격(%). 설정에서 바뀌었으면 돌아올 때 다시 만든다. */
    private var createdTextScale = 100
    private var createdRowScale = 100
    /** 이 화면을 만들 때 쓴 색. 설정에서 바뀌었으면 돌아올 때 다시 만든다. */
    private var createdPalette = Palette.MONO
    /** 이 화면이 쓰는 배율([Ui.textScale]·[Ui.rowScale]). 앱 전체 값이라 이 화면으로 돌아올 때마다 다시 맞춘다. */
    private var textScale = 1f
    private var rowScale = 1f

    /**
     * true 면 글자 · 간격 설정과 기기 글꼴 크기를 따르지 않고 늘 기본(100%) 크기로 그린다.
     * 사진으로 찍어 보내는 진단 화면(기기 정보·자동 추가 기록)이 한 화면에 다 들어가게.
     */
    protected open val fixedTextSize: Boolean get() = false

    /**
     * 설정의 언어(한국어/영어)로 화면 글자와 날짜 형식을 정하고, 화면 폭이 [Ui.DESIGN_WIDTH_DP] 가 되도록 밀도를 다시 정한다.
     * 모든 dp·sp 값이 페블 기준이라, 픽셀 수는 거의 같은데 밀도만 다르게 선언한 기기(메이북 E6: 320dpi → 536dp)에서도
     * 같은 물리 크기로 그려진다. 페블(1072px, 300dpi)은 그대로 300 이 나온다.
     */
    override fun attachBaseContext(newBase: Context) {
        createdEnglish = HomePrefs.isEnglish(newBase)
        createdTextScale = HomePrefs.textScalePercent(newBase)
        createdRowScale = HomePrefs.rowScalePercent(newBase)
        createdPalette = HomePrefs.palette(newBase)
        Palette.current = createdPalette
        textScale = if (fixedTextSize) 1f else createdTextScale / 100f
        rowScale = if (fixedTextSize) 1f else createdRowScale / 100f
        Ui.textScale = textScale
        Ui.rowScale = rowScale
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(if (createdEnglish) Locale.ENGLISH else Locale.KOREAN)
        if (fixedTextSize) config.fontScale = 1f
        val dpi = Ui.designDensityDpi(newBase)
        // 화면 크기(dp)도 같은 비율로 바꾼다. 밀도만 바꾸면 안드로이드 10 은 dp 크기에서 픽셀을 거꾸로 셈해 화면을 실제보다 넓게 안다
        // (1264px 300dpi → 354dpi 면 1491px). 아래 메뉴가 그 폭으로 높이를 재서 글이 실제로는 한 줄 더 늘어 `닫기` 가 잘렸다(BOOX Leaf).
        val from = config.densityDpi
        if (from > 0 && from != dpi) {
            fun scale(dp: Int) = if (dp <= 0) dp else (dp * from / dpi.toFloat()).roundToInt() // 0 은 모름
            config.screenWidthDp = scale(config.screenWidthDp)
            config.screenHeightDp = scale(config.screenHeightDp)
            config.smallestScreenWidthDp = scale(config.smallestScreenWidthDp)
        }
        config.densityDpi = dpi
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = HomePrefs(this)
    }

    override fun onPostResume() {
        super.onPostResume()
        // 떠나 있는 사이(그리고 돌아오며 화면마다 onResume 에서 한 저장까지) 기록이 바뀌었으면 떠 있던 메뉴를 닫는다
        Sheet.dismissStale(this)
    }

    override fun onResume() {
        super.onResume()
        // 기본 크기로 그리는 화면(기기 정보)에서 돌아왔으면 이 화면의 배율로 되돌린다(새로 그리는 줄·메뉴가 이 값을 쓴다).
        Ui.textScale = textScale
        Ui.rowScale = rowScale
        Palette.current = createdPalette
        if (prefs.english != createdEnglish || prefs.textScalePercent != createdTextScale ||
            prefs.rowScalePercent != createdRowScale || prefs.palette != createdPalette) recreate()
    }

    override fun setContentView(view: View) {
        Crema.reserveHiddenBottom(view)
        super.setContentView(view)
    }

    /** [toLeft] 가 true 면 왼쪽으로 민 것(다음 페이지). 처리했으면 true. */
    protected open fun onSwipe(toLeft: Boolean): Boolean = false

    /**
     * 페이지 버튼: 메이북 전용 키(291 위·292 아래)와 표준 PAGE_UP·PAGE_DOWN. 아래(다음)는 왼쪽으로 민 것과 같다.
     * 누르고 있을 때 오는 반복은 넘기지 않는다(한 번 누르면 한 쪽).
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val next = pageKey(keyCode) ?: return super.onKeyDown(keyCode, event)
        if (event.repeatCount > 0) return true
        return onSwipe(next) || super.onKeyDown(keyCode, event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = ev.x; downY = ev.y }
            MotionEvent.ACTION_UP -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (abs(dx) > dp(70) && abs(dx) > 2 * abs(dy) && onSwipe(dx < 0)) {
                    val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun startActivity(intent: Intent?, options: Bundle?) {
        super.startActivity(intent, options)
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    /** 앱 길게 누르기 메뉴(④). 무엇이 바뀌면 [changed] 를 부른다. [rename] 이면(홈 자주 쓰는 앱) 맨 위에 `이름 바꾸기`. */
    protected fun showAppMenu(app: AppEntry, rename: Boolean = false, changed: () -> Unit) {
        // 읽고 있는 앱은 책에 딸린 설정이라 책 메뉴·책 목록에서만 바꾼다.
        val fav = app.key in prefs.favorites
        val sheet = Sheet(this).header(app.label, app = app)
        if (rename && fav) sheet.item(getString(R.string.rename)) { renameFavorite(app, changed) }
        sheet.item(getString(if (fav) R.string.fav_remove else R.string.fav_add)) { prefs.toggleFavorite(app.key); changed() }
            .item(getString(R.string.hide)) { prefs.hide(app.key); changed() }
            .item(getString(R.string.app_info)) { AppStore.openAppInfo(this, app) }
            .show()
    }

    /** 자주 쓰는 앱 이름 바꾸기. 원래 이름과 같게 쓰거나 `원래 이름으로` 를 누르면 붙인 이름을 지운다. */
    protected fun renameFavorite(app: AppEntry, changed: () -> Unit) {
        val original = AppStore.load(this).firstOrNull { it.key == app.key }?.label ?: app.label
        val custom = prefs.favoriteNames[app.key]
        val sheet = Sheet(this).header(getString(R.string.rename), app = app)
            .input(custom ?: original) { prefs.setFavoriteName(app.key, it.takeIf { name -> name != original }); changed() }
        if (custom != null) sheet.item(getString(R.string.rename_reset, original)) { prefs.setFavoriteName(app.key, null); changed() }
        sheet.show()
    }
}

/** 메이북(Haoqing) 펌웨어의 KeyEvent.KEYCODE_HAOQING_PAGE_UP·DOWN */
private const val MEEBOOK_PAGE_UP = 291
private const val MEEBOOK_PAGE_DOWN = 292

/** 페이지 버튼이면 다음 쪽(아래) true · 앞 쪽(위) false, 아니면 null */
fun pageKey(keyCode: Int): Boolean? = when (keyCode) {
    MEEBOOK_PAGE_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> true
    MEEBOOK_PAGE_UP, KeyEvent.KEYCODE_PAGE_UP -> false
    else -> null
}
