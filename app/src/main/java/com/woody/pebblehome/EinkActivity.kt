package com.woody.pebblehome

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import kotlin.math.abs

/**
 * 전자잉크용 화면 공통: 화면 전환 애니메이션을 끄고, 좌우로 밀면 [onSwipe] 를 부른다.
 * 밀기로 끝난 터치는 아래 뷰에 취소로 전달해 눌림(클릭)으로 처리되지 않게 한다.
 */
abstract class EinkActivity : Activity() {
    protected lateinit var prefs: HomePrefs
    private var downX = 0f
    private var downY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = HomePrefs(this)
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

    /** 앱 길게 누르기 메뉴(④) / 기본 앱 길게 누르기 메뉴(⑤). 무엇이 바뀌면 [changed] 를 부른다. */
    protected fun showAppMenu(app: AppEntry, changed: () -> Unit) {
        val count = prefs.openCount(app.key)
        val isDefault = prefs.defaultApp == app.key
        val sheet = Sheet(this)
        if (isDefault) {
            sheet.header(app.label, "기본 앱 · ${count}번 열었음", app)
                .item("기본 앱 바꾸기") { startActivity(AppListActivity.intent(this, AppListActivity.Mode.PICK_DEFAULT)) }
                .item("기본 앱 해제") { prefs.defaultApp = null; changed() }
        } else {
            val fav = app.key in prefs.favorites
            sheet.header(app.label, "${count}번 열었음", app)
                .item("기본 앱으로 설정", bold = true) { prefs.defaultApp = app.key; changed() }
                .item(if (fav) "자주 쓰는 앱에서 빼기" else "자주 쓰는 앱에 넣기") { prefs.toggleFavorite(app.key); changed() }
                .item("숨기기") { prefs.hide(app.key); changed() }
        }
        sheet.item("앱 정보") { AppStore.openAppInfo(this, app) }.show()
    }
}
