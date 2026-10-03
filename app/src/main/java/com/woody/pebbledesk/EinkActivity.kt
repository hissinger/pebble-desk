package com.woody.pebbledesk

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import java.util.Locale
import kotlin.math.abs

/**
 * 전자잉크용 화면 공통: 화면 전환 애니메이션을 끄고, 좌우로 밀면 [onSwipe] 를 부른다.
 * 밀기로 끝난 터치는 아래 뷰에 취소로 전달해 눌림(클릭)으로 처리되지 않게 한다.
 */
abstract class EinkActivity : Activity() {
    protected lateinit var prefs: HomePrefs
    private var downX = 0f
    private var downY = 0f
    /** 이 화면을 만들 때 쓴 언어. 설정에서 바뀌었으면 돌아올 때 다시 만든다. */
    private var createdEnglish = false

    /** 설정의 언어(한국어/영어)로 화면 글자와 날짜 형식을 정한다. */
    override fun attachBaseContext(newBase: Context) {
        createdEnglish = HomePrefs.isEnglish(newBase)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(if (createdEnglish) Locale.ENGLISH else Locale.KOREAN)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = HomePrefs(this)
    }

    override fun onResume() {
        super.onResume()
        if (prefs.english != createdEnglish) recreate()
    }

    /** [toLeft] 가 true 면 왼쪽으로 민 것(다음 페이지). 처리했으면 true. */
    protected open fun onSwipe(toLeft: Boolean): Boolean = false

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

    /** 앱 길게 누르기 메뉴(④). 무엇이 바뀌면 [changed] 를 부른다. */
    protected fun showAppMenu(app: AppEntry, changed: () -> Unit) {
        // 읽고 있는 앱은 책에 딸린 설정이라 책 메뉴·책 목록에서만 바꾼다.
        val fav = app.key in prefs.favorites
        Sheet(this).header(app.label, app = app)
            .item(getString(if (fav) R.string.fav_remove else R.string.fav_add)) { prefs.toggleFavorite(app.key); changed() }
            .item(getString(R.string.hide)) { prefs.hide(app.key); changed() }
            .item(getString(R.string.app_info)) { AppStore.openAppInfo(this, app) }
            .show()
    }
}
